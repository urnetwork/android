import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const source = fileURLToPath(new URL("./physical_host_launch.sh", import.meta.url));
const quote = value => `'${value.replaceAll("'", "'\\''")}'`;

function fixture(t) {
  const directory = realpathSync(mkdtempSync(join(tmpdir(), "physical-root-scope-")));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const scripts = join(directory, "checkout with spaces/android/app/scripts");
  const bin = join(directory, "bin");
  mkdirSync(scripts, { recursive: true });
  mkdirSync(bin);
  const launcher = join(scripts, "physical_host_launch.sh");
  copyFileSync(source, launcher);
  for (const helper of ["physical_collector_session.mjs", "physical_workload_receipt.mjs"]) {
    writeFileSync(join(scripts, helper), "// Fixture: fake Node records argv without any device access.\n");
  }
  const recorder = join(directory, "record.mjs");
  writeFileSync(recorder, `process.stdout.write(JSON.stringify({ argv: process.argv.slice(2), pid: process.pid, root: process.env.ROOT ?? null }) + "\\n");\n`);
  writeFileSync(join(bin, "node"), `#!/bin/sh\nexec ${quote(process.execPath)} ${quote(recorder)} "$@"\n`);
  chmodSync(join(bin, "node"), 0o700);
  const env = { ...process.env, PATH: `${bin}:${process.env.PATH}` };
  delete env.ROOT;
  return { directory, scripts, launcher, env,
    run(mode, args = ["--label", "fixture"], extra = {}) {
      return spawnSync("bash", [launcher, mode, ...args], { cwd: directory, env: { ...env, ...extra }, encoding: "utf8", timeout: 5000 });
    } };
}

test("ROOT scoped only to mkdir cannot break the following collector or workload launch", t => {
  const f = fixture(t);
  for (const [mode, helper, command] of [["collector", "physical_collector_session.mjs", "run"],
    ["workload", "physical_workload_receipt.mjs", "owner-script"]]) {
    // Reproduce the actual failure: the inline assignment is not retained by
    // the shell. The old subsequent node "$ROOT/..." could not find its helper.
    const result = spawnSync("bash", ["-c", 'unset ROOT; ROOT="$1" mkdir "$2"; test -z "${ROOT+x}" || exit 81; exec bash "$3" "$4" --label fixture',
      "root-scope-regression", join(f.directory, "checkout with spaces"), join(f.directory, `${mode}-leaf`), f.launcher, mode],
    { cwd: f.directory, env: f.env, encoding: "utf8", timeout: 5000 });
    assert.equal(result.status, 0, result.stderr);
    const recorded = JSON.parse(result.stdout);
    assert.equal(recorded.root, null);
    assert.equal(recorded.pid, result.pid, "the launcher must exec, preserving the retained owner PID");
    assert.deepEqual(recorded.argv, [join(f.scripts, helper), command, "--label", "fixture"]);
  }
});

test("all modes resolve sibling helpers without ROOT, regardless of cwd or stale path variables", t => {
  const f = fixture(t);
  const values = ["--label", "literal ; $value `not-a-command`", "--", "--output", join(f.directory, "space and ' quote.json")];
  for (const [mode, helper, command] of [["collector", "physical_collector_session.mjs", "run"],
    ["collector-check", "physical_collector_session.mjs", "check"],
    ["workload", "physical_workload_receipt.mjs", "owner-script"],
    ["workload-preflight", "physical_workload_receipt.mjs", "script-preflight"]]) {
    const result = f.run(mode, values, { ROOT: "/wrong/root", SCRIPTS: "/wrong/scripts", RECEIPT: "/wrong/receipt" });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout).argv, [join(f.scripts, helper), command, ...values]);
  }
});

test("missing or unknown launch modes, empty arguments and absent or symlinked helpers fail before Node", t => {
  const f = fixture(t);
  for (const [mode, args, reason] of [["", [], "explicit-launch-mode-required"],
    ["run", ["--label", "fixture"], "explicit-launch-mode-required"],
    ["collector", [], "explicit-launch-arguments-required"]]) {
    const result = f.run(mode, args);
    assert.equal(result.status, 2);
    assert.equal(result.stdout, "");
    assert.equal(result.stderr, `physical launch failed: ${reason}-no-spawn\n`);
  }
  const helper = join(f.scripts, "physical_collector_session.mjs");
  rmSync(helper);
  for (const symlink of [false, true]) {
    if (symlink) symlinkSync(join(f.scripts, "physical_workload_receipt.mjs"), helper);
    const result = f.run("collector");
    assert.equal(result.status, 2);
    assert.equal(result.stdout, "");
    assert.equal(result.stderr, "physical launch failed: sibling-helper-unavailable-no-spawn\n");
  }
});

test("canonical launcher preserves real owner parsers and the no-PTY gate before device access", t => {
  const f = fixture(t);
  const adbMarker = join(f.directory, "adb-invoked");
  writeFileSync(join(f.directory, "bin/adb"), `#!/bin/sh\n: >${quote(adbMarker)}\nexit 91\n`);
  chmodSync(join(f.directory, "bin/adb"), 0o700);
  // Use real Node here; the fake adb would leave a marker if a host launch
  // unexpectedly bypassed the retained-terminal guard or touched a device.
  rmSync(join(f.directory, "bin/node"));
  const run = args => spawnSync("bash", [source, ...args], { cwd: f.directory, env: f.env, encoding: "utf8", timeout: 5000 });
  const incomplete = run(["collector", "--owner", join(f.directory, "owner.json")]);
  assert.equal(incomplete.status, 2);
  assert.match(incomplete.stderr, /explicit-session-artifacts-required/);
  const collector = run(["collector", "--owner", join(f.directory, "owner.json"),
    "--stdout", join(f.directory, "stdout"), "--stderr", join(f.directory, "stderr"), "--",
    "--serial", "fixture", "--label", "fixture", "--duration-seconds", "1800", "--interval-ms", "1000",
    "--stop-file", join(f.directory, "stop"), "--output", join(f.directory, "telemetry.ndjson")]);
  assert.equal(collector.status, 2);
  assert.match(collector.stderr, /foreground.*pty|required.*pty|retained.*pty/i);
  const workload = run(["workload", "--serial", "fixture", "--label", "fixture", "--collector-pid", "123",
    "--telemetry", join(f.directory, "telemetry.ndjson"), "--children", "wiki", "--output", join(f.directory, "fixture.workloads.json")]);
  assert.equal(workload.status, 2);
  assert.match(workload.stderr, /retained-workload-foreground-pty-required/);
  assert.equal(existsSync(adbMarker), false);
  assert.equal(existsSync(join(f.directory, "owner.json")), false);
  assert.equal(existsSync(join(f.directory, "stdout")), false);
  assert.equal(existsSync(join(f.directory, "fixture.workloads.json")), false);
});
