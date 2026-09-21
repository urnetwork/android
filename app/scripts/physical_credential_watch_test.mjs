import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { credentialMetadataScript, parseArgs, parseCredentialMetadata, preflightCredentialDestination,
  watchCredentialMetadata } from "./physical_credential_watch.mjs";

test("external credential witness accepts only bounded primitive metadata and never raw errors", () => {
  assert.deepEqual(parseCredentialMetadata({ status: 0, stdout: "1 regular 1 600 44\n" }),
    { exists: true, fileType: "regular", ownerMatchesApp: true, mode: "600", byteCount: 44 });
  assert.deepEqual(parseCredentialMetadata({ status: 0, stdout: "0 missing - - -\n" }),
    { exists: false, fileType: "missing", ownerMatchesApp: null, mode: null, byteCount: null });
  for (const raw of ["secret-value", "1 regular 1 600 -1", "1 regular 1 600 9007199254740992", "1 private-path 1 600 3", "x".repeat(257)]) {
    const parsed = parseCredentialMetadata({ status: 0, stdout: raw, stderr: "secret-value" });
    assert.equal(parsed.fileType, "unavailable");
    assert.equal(JSON.stringify(parsed).includes("secret-value"), false);
  }
  assert.equal(parseCredentialMetadata({ status: 1, stdout: "1 regular 1 600 44" }).fileType, "unavailable");
  for (const result of [{ error: { code: "ETIMEDOUT" } }, { signal: "SIGTERM" }]) {
    assert.equal(parseCredentialMetadata({ status: 0, stdout: "0 missing - - -\n", ...result }).fileType, "unavailable");
  }
  assert.match(credentialMetadataScript, /stat -c '%u %a %s'/);
  assert.doesNotMatch(credentialMetadataScript, /\b(cat|tee|rm|chmod|sha256sum|head|tail|sed|awk)\b/);
});

test("metadata witness requires a bounded deadline and explicit private paths", () => {
  assert.deepEqual(parseArgs(["--serial", "fixture", "--output", "/private/out", "--stop-file", "/private/stop"]),
    { serial: "fixture", output: "/private/out", stopFile: "/private/stop", timeoutMs: 120000 });
  for (const args of [[], ["--serial", "fixture"], ["--password", "secret"],
    ["--serial", "fixture", "--output", "relative", "--stop-file", "/private/stop"],
    ["--serial", "fixture", "--output", "/private/out", "--stop-file", "/private/stop", "--timeout-ms", "180001"]]) {
    assert.throws(() => parseArgs(args));
  }
});

test("destination preflight accepts one read-only probe with no watch deadline or stop file", () => {
  assert.deepEqual(parseArgs(["--preflight", "--serial", "fixture", "--output", "/private/out"]),
    { serial: "fixture", output: "/private/out", preflight: true });
  for (const tail of [["--preflight"], ["--stop-file", "/private/stop"], ["--timeout-ms", "1000"]]) {
    assert.throws(() => parseArgs(["--preflight", "--serial", "fixture", "--output", "/private/out", ...tail]));
  }
});

function shellFixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "credential-namespace-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const app = join(directory, "app"); const shell = join(directory, "shell"); const bin = join(directory, "bin");
  for (const path of [app, shell, bin]) mkdirSync(path, { mode: 0o700 });
  mkdirSync(join(app, "files/acceptance"), { recursive: true, mode: 0o700 });
  writeFileSync(join(bin, "run-as"), '#!/bin/sh\nshift\ncd "$FIXTURE_APP" || exit 1\nexec "$@"\n', { mode: 0o700 });
  // Android stat's metadata format; no fixture contents are read by this tool.
  writeFileSync(join(bin, "stat"), `#!${process.execPath}
const fs = require('node:fs');
const stat = fs.lstatSync(process.argv.at(-1));
process.stdout.write(stat.uid + ' ' + (stat.mode & 511).toString(8) + ' ' + stat.size + '\\n');
`, { mode: 0o700 });
  const invoke = command => spawnSync("/bin/sh", ["-c", command], { cwd: shell, encoding: "utf8", timeout: 3000,
    env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_APP: app } });
  const adb = args => {
    assert.deepEqual(args.slice(0, 4), ["-s", "fixture", "shell", "-T"]);
    // Model ADB's device-shell join, not exec of the host's original argv.
    return invoke(args.slice(4).join(" "));
  };
  const options = { serial: "fixture", preflight: true, output: join(directory, "preflight.json") };
  return { directory, app, options, adb, invoke, destination: join(app, "files/acceptance/credentials") };
}

test("ADB argument joining falsely reports an existing credential absent unless the remote script is quoted", t => {
  const f = shellFixture(t);
  writeFileSync(f.destination, "synthetic-do-not-read", { mode: 0o600 });
  // This is the observed physical-run command after the host shell removes its
  // quotes. The inner `sh -c test` receives no test operands and returns false.
  const malformed = ["run-as", "com.bringyour.network", "sh", "-c",
    "test -e files/acceptance/credentials && echo credentials-present || echo credentials-absent; " +
    "test -e files/acceptance/physical-status && echo status-present || echo status-absent; " +
    "test -e files/acceptance/physical-expected-peer-id && echo peer-pin-present || echo peer-pin-absent"];
  const misleading = f.invoke(malformed.join(" "));
  assert.equal(misleading.status, 0);
  assert.equal(misleading.stdout, "credentials-absent\nstatus-absent\npeer-pin-absent\n");
  assert.equal(existsSync(f.destination), true);

  const report = preflightCredentialDestination(f.options, { adb: f.adb });
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "credential-destination-present");
  assert.equal(report.fileType, "regular");
  assert.equal(report.mode, "600");
  assert.equal(readFileSync(f.destination, "utf8"), "synthetic-do-not-read");
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.doesNotMatch(readFileSync(f.options.output, "utf8"), /synthetic-do-not-read|fixture|files\/|sha256|digest/);
  assert.throws(() => preflightCredentialDestination(f.options, { adb: () => assert.fail("output collision must not probe") }));
});

test("destination preflight proves absence and rejects symlinks or inaccessible metadata without mutations", t => {
  for (const kind of ["absent", "missing-ancestor", "existing-symlink", "ancestor-symlink", "unavailable"]) {
    const f = shellFixture(t);
    if (kind === "missing-ancestor") rmSync(join(f.app, "files/acceptance"), { recursive: true });
    if (kind === "existing-symlink") symlinkSync(join(f.directory, "missing"), f.destination);
    if (kind === "ancestor-symlink") {
      rmSync(join(f.app, "files/acceptance"), { recursive: true });
      symlinkSync(f.directory, join(f.app, "files/acceptance"));
    }
    const result = preflightCredentialDestination(f.options, { adb: kind === "unavailable"
      ? () => ({ status: 1, stdout: "0 missing - - -\n", stderr: "private-transport-detail" }) : f.adb });
    assert.equal(result.eligible, ["absent", "missing-ancestor"].includes(kind), kind);
    assert.equal(result.reason, ["absent", "missing-ancestor"].includes(kind) ? "credential-destination-absent"
      : kind === "existing-symlink" ? "credential-destination-present" : "credential-destination-unavailable", kind);
    assert.equal(JSON.stringify(result).includes("private-transport-detail"), false);
    if (kind === "absent") assert.equal(existsSync(f.destination), false);
  }
});

test("retained metadata witness records disappearance without reading or restoring credentials", async (t) => {
  const directory = mkdtempSync(join(tmpdir(), "credential-watch-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const options = { serial: "fixture-device", output: join(directory, "watch.ndjson"), stopFile: join(directory, "stop"), timeoutMs: 1000 };
  let clock = 100; let probes = 0;
  const summary = await watchCredentialMetadata(options, {
    now: () => clock, monotonic: () => clock,
    sleep: async (ms) => { clock += ms; },
    probe: () => {
      probes++;
      if (probes === 3) writeFileSync(options.stopFile, "", { mode: 0o600 });
      return { status: 0, stdout: probes === 1 ? "1 regular 1 600 44\n" : "0 missing - - -\n" };
    },
  });
  assert.deepEqual(summary, { type: "physical-credential-watch-summary", samples: 3, reason: "stop-file" });
  const text = readFileSync(options.output, "utf8");
  const rows = text.trim().split("\n").map(JSON.parse);
  assert.deepEqual(rows.slice(0, 3).map((row) => row.exists), [true, false, false]);
  assert.equal(statSync(options.output).mode & 0o777, 0o600);
  assert.equal(text.includes(options.serial), false);
  assert.equal(text.includes(directory), false);
  await assert.rejects(watchCredentialMetadata(options, { probe: () => assert.fail("existing output must not probe") }));
});
