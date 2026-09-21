import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";
import { captureWorkloadScriptPreflight, requireWorkloadScript, workloadScriptSummary } from "./physical_workload_script.mjs";

const body = 'set -u\n# Caller-owned wiki/Fast body goes here.\nnode "$RECEIPT" cleanup --serial "$SERIAL"\n';
function fixture(t, outsideOutput = false) {
  const root = mkdtempSync(join(tmpdir(), "physical-workload-script-test-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const label = "fresh-label";
  const directory = join(root, label);
  mkdirSync(directory, { mode: 0o700 });
  const script = join(directory, "traffic-workload.sh");
  writeFileSync(script, body, { mode: 0o600 });
  const options = { mode: "owner-script", label, output: outsideOutput ? join(root, `${label}.workloads.json`) : join(directory, "workloads.json"), command: [] };
  return { root, directory, script, options, report: `${options.output}.script-preflight.json` };
}

test("both runbook output layouts derive exactly the same label-local script and fixed cleanup", t => {
  for (const outsideOutput of [false, true]) {
    const f = fixture(t, outsideOutput);
    const report = captureWorkloadScriptPreflight(f.options);
    assert.equal(report.eligible, true);
    const result = requireWorkloadScript(f.options);
    assert.deepEqual(result.command, ["sh", realpathSync(f.script)]);
    assert.equal(result.privateDirectory, realpathSync(f.directory));
    assert.equal(result.proof.cleanup, "built-in-explicit-serial");
    assert.equal(statSync(f.report).mode & 0o7777, 0o600);
    assert.equal(workloadScriptSummary(report).classification, "WORKLOAD_SCRIPT_READY");
    assert.doesNotMatch(JSON.stringify(workloadScriptSummary(report)), /fresh-label|traffic-workload|sha256|private/);
  }
});

test("1eGmda wrong-label script rejects before reading the foreign body and retains classified evidence", t => {
  const f = fixture(t);
  const wrong = join(f.root, "previous-label");
  mkdirSync(wrong, { mode: 0o700 });
  const script = join(wrong, "traffic-workload.sh");
  writeFileSync(script, "private-foreign-body", { mode: 0o600 });
  f.options.command = ["sh", script];
  const report = captureWorkloadScriptPreflight(f.options);
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "workload-script-path-mismatch");
  assert.equal(report.classification, "INVALID_WORKLOAD_SCRIPT");
  assert.doesNotMatch(readFileSync(f.report, "utf8"), /private-foreign-body|previous-label/);
  assert.throws(() => requireWorkloadScript(f.options), /passing-script-preflight-required/);
  assert.equal(existsSync(`${f.options.output}.script-preflight.failed.json`), true);
});

test("missing, wrong-name, relative, parent-label and symlink scripts cannot qualify", t => {
  for (const kind of ["missing", "wrong-name", "relative", "parent-label", "symlink", "symlink-directory", "writable", "output-label"]) {
    const f = fixture(t, kind === "symlink-directory");
    if (kind === "missing") rmSync(f.script);
    if (kind === "wrong-name") f.options.command = ["sh", join(f.directory, "another.sh")];
    if (kind === "relative") f.options.command = ["sh", "traffic-workload.sh"];
    if (kind === "parent-label") f.options.command = ["sh", join(dirname(f.directory), "traffic-workload.sh")];
    if (kind === "symlink") { renameSync(f.script, `${f.script}.saved`); symlinkSync(`${f.script}.saved`, f.script); }
    if (kind === "symlink-directory") { renameSync(f.directory, `${f.directory}.saved`); symlinkSync(`${f.directory}.saved`, f.directory); }
    if (kind === "writable") chmodSync(f.script, 0o666);
    if (kind === "output-label") f.options.label = "wrong-label";
    if (kind === "output-label") {
      assert.throws(() => captureWorkloadScriptPreflight(f.options), /workload-output-parent-label-mismatch/);
      assert.equal(existsSync(f.report), false);
      continue;
    }
    if (kind === "symlink-directory") {
      assert.throws(() => captureWorkloadScriptPreflight(f.options), /artifact-directory-untrusted-type/);
      const failedPath = join(f.root, `${f.options.label}.workload-script-launch.failed.json`);
      assert.equal(JSON.parse(readFileSync(failedPath)).eligible, false);
      assert.equal(statSync(failedPath).mode & 0o7777, 0o600);
      assert.equal(existsSync(f.report), false);
      continue;
    }
    const report = captureWorkloadScriptPreflight(f.options);
    assert.equal(report.eligible, false, kind);
    assert.equal(report.classification, "INVALID_WORKLOAD_SCRIPT");
    assert.equal(statSync(f.report).mode & 0o7777, 0o600);
  }
});

test("exact cleanup checks actual final body line, not a separate successful parser invocation", t => {
  for (const cleanup of [
    'node "$RECEIPT" cleanup -- adb -s "$SERIAL" shell am force-stop com.android.chrome',
    'node "$RECEIPT" cleanup --serial "$SERIAL" || true',
    '# node "$RECEIPT" cleanup --serial "$SERIAL"',
    'node "$RECEIPT" cleanup --serial "$SERIAL"\necho after-cleanup',
    'node "$RECEIPT" cleanup --serial "$SERIAL"\nnode "$RECEIPT" cleanup --serial "$SERIAL"',
  ]) {
    const f = fixture(t);
    writeFileSync(f.script, `set -u\n${cleanup}\n`);
    const report = captureWorkloadScriptPreflight(f.options);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "exact-final-cleanup-serial-required");
  }
  const f = fixture(t);
  writeFileSync(f.script, 'set -u\nnode "$RECEIPT" cleanup --serial "$SERIAL" >"$PRIVATE_DIR/chrome-cleanup.stdout" 2>"$PRIVATE_DIR/chrome-cleanup.stderr"\n');
  assert.equal(captureWorkloadScriptPreflight(f.options).eligible, true);
});

test("passed preflight cannot authorize later script, inode, directory, shell or candidate-path drift", t => {
  for (const kind of ["body", "inode", "directory", "shell", "candidate"]) {
    const f = fixture(t);
    captureWorkloadScriptPreflight(f.options);
    if (kind === "body") writeFileSync(f.script, `# changed\n${body}`);
    if (kind === "inode") { renameSync(f.script, `${f.script}.saved`); writeFileSync(f.script, body, { mode: 0o600 }); }
    if (kind === "directory") {
      renameSync(f.directory, `${f.directory}.saved`);
      mkdirSync(f.directory, { mode: 0o700 });
      writeFileSync(f.script, body, { mode: 0o600 });
      writeFileSync(f.report, readFileSync(join(`${f.directory}.saved`, "workloads.json.script-preflight.json")), { mode: 0o600 });
    }
    if (kind === "shell") f.options.command = ["bash", f.script];
    if (kind === "candidate") f.options.command = ["sh", join(f.root, "old-label", "traffic-workload.sh")];
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-binding-changed|workload-script-path-mismatch/);
    const failed = JSON.parse(readFileSync(`${f.options.output}.script-preflight.failed.json`));
    assert.equal(failed.eligible, false);
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-recheck-already-failed/);
  }
});

test("existing or nonprivate preflight is never rewritten or accepted", t => {
  const f = fixture(t);
  captureWorkloadScriptPreflight(f.options);
  const original = readFileSync(f.report, "utf8");
  assert.throws(() => captureWorkloadScriptPreflight(f.options), /fresh-script-preflight-required/);
  assert.equal(readFileSync(f.report, "utf8"), original);
  chmodSync(f.report, 0o644);
  assert.throws(() => requireWorkloadScript(f.options), /private-script-preflight-required/);
});

test("only the full successful producer receipt can authorize the owner, never stdout or a tampered envelope", t => {
  const changes = {
    "stdout-summary": report => workloadScriptSummary(report),
    "schema-missing": report => { delete report.schema; return report; },
    "schema-string": report => ({ ...report, schema: "1" }),
    "wrong-type": report => ({ ...report, type: "workload-arguments-preflight" }),
    "negative-result": report => ({ ...report, eligible: false }),
    "coerced-result": report => ({ ...report, eligible: "true" }),
    "wrong-classification": report => ({ ...report, classification: "INVALID_WORKLOAD_SCRIPT" }),
    "missing-classification": report => { delete report.classification; return report; },
    "wrong-reason": report => ({ ...report, reason: "exact-final-cleanup-serial-required" }),
    "missing-reason": report => { delete report.reason; return report; },
  };
  for (const [kind, change] of Object.entries(changes)) {
    const f = fixture(t);
    const report = captureWorkloadScriptPreflight(f.options);
    writeFileSync(f.report, JSON.stringify(change(report)), { mode: 0o600 });
    const original = readFileSync(f.report, "utf8");
    assert.throws(() => requireWorkloadScript(f.options), /passing-script-preflight-required/, kind);
    assert.equal(readFileSync(f.report, "utf8"), original, kind);
    const failed = JSON.parse(readFileSync(`${f.options.output}.script-preflight.failed.json`));
    assert.equal(failed.reason, "passing-script-preflight-required", kind);
    assert.equal(statSync(`${f.options.output}.script-preflight.failed.json`).mode & 0o7777, 0o600, kind);
  }
});

test("successful receipt still requires the exact label, cleanup, script hash and directory/file identities", t => {
  const changes = {
    "missing-proof": report => { delete report.proof; },
    "wrong-label": report => { report.proof.labelHash = "0".repeat(64); },
    "wrong-interpreter": report => { report.proof.interpreter = "bash"; },
    "wrong-cleanup": report => { report.proof.cleanup = "unverified"; },
    "wrong-directory-path": report => { report.proof.directory.canonical += "/another-label"; },
    "wrong-directory-device": report => { report.proof.directory.device++; },
    "wrong-directory-inode": report => { report.proof.directory.inode++; },
    "wrong-script-path": report => { report.proof.script.path += ".other"; },
    "wrong-script-device": report => { report.proof.script.device++; },
    "wrong-script-inode": report => { report.proof.script.inode++; },
    "wrong-script-size": report => { report.proof.script.size++; },
    "wrong-script-mtime": report => { report.proof.script.modifiedMs++; },
    "wrong-script-hash": report => { report.proof.script.sha256 = "0".repeat(64); },
  };
  for (const [kind, change] of Object.entries(changes)) {
    const f = fixture(t);
    const report = captureWorkloadScriptPreflight(f.options);
    change(report);
    writeFileSync(f.report, JSON.stringify(report), { mode: 0o600 });
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-binding-changed/, kind);
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-recheck-already-failed/, kind);
  }
});

test("FNr3Q9 private root retains a classified launch failure when the label directory cannot hold a receipt", t => {
  for (const outsideOutput of [false, true]) for (const kind of ["mode", "missing", "symlink", "file"]) {
    const f = fixture(t, outsideOutput);
    const failurePath = join(f.root, `${f.options.label}.workload-script-launch.failed.json`);
    if (kind === "mode") chmodSync(f.directory, 0o755);
    if (kind === "missing") rmSync(f.directory, { recursive: true });
    if (kind === "symlink") { renameSync(f.directory, `${f.directory}.saved`); symlinkSync(`${f.directory}.saved`, f.directory); }
    if (kind === "file") { rmSync(f.directory, { recursive: true }); writeFileSync(f.directory, "fixture", { mode: 0o600 }); }
    const reason = kind === "mode" ? "artifact-directory-mode-not-0700" : kind === "missing" ?
      "artifact-directory-missing" : "artifact-directory-untrusted-type";
    const cli = spawnSync(process.execPath, [new URL("./physical_workload_receipt.mjs", import.meta.url).pathname,
      "script-preflight", "--label", f.options.label, "--output", f.options.output], { encoding: "utf8", timeout: 3000 });
    assert.equal(cli.status, 2, kind);
    assert.equal(cli.stdout, "", kind);
    assert.equal(cli.stderr, `workload receipt failed: ${reason}\n`, kind);
    assert.equal(existsSync(f.report), false, kind);
    const original = readFileSync(failurePath, "utf8");
    assert.deepEqual(JSON.parse(original), { schema: 1, type: "workload-script-launch", eligible: false,
      classification: "INVALID_WORKLOAD_SCRIPT", reason }, kind);
    assert.equal(statSync(failurePath).mode & 0o7777, 0o600, kind);
    assert.doesNotMatch(original, /fresh-label|traffic-workload|physical-workload-script-test/);
    assert.throws(() => captureWorkloadScriptPreflight(f.options), /workload-script-launch-already-failed/, kind);
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-launch-already-failed/, kind);
    assert.equal(readFileSync(failurePath, "utf8"), original, kind);
    if (kind === "mode") {
      assert.equal(statSync(f.directory).mode & 0o7777, 0o755, "no permission repair");
      chmodSync(f.directory, 0o700);
      assert.throws(() => requireWorkloadScript(f.options), /workload-script-launch-already-failed/,
        "a later operator repair cannot make the failed arm eligible");
    }
  }
});

test("untrusted root retains only fixed CLI failure and never writes a fallback outside a verified private directory", t => {
  const f = fixture(t);
  chmodSync(f.root, 0o755);
  const cli = spawnSync(process.execPath, [new URL("./physical_workload_receipt.mjs", import.meta.url).pathname,
    "script-preflight", "--label", f.options.label, "--output", f.options.output], { encoding: "utf8", timeout: 3000 });
  assert.equal(cli.status, 2);
  assert.equal(cli.stdout, "");
  assert.equal(cli.stderr, "workload receipt failed: artifact-directory-mode-not-0700\n");
  assert.equal(existsSync(f.report), false);
  assert.equal(existsSync(join(f.root, `${f.options.label}.workload-script-launch.failed.json`)), false);
  assert.equal(statSync(f.root).mode & 0o7777, 0o755);
});

test("launch failure entries, including dangling symlinks, cannot be overwritten or reused", t => {
  for (const kind of ["file", "dangling-symlink"]) {
    const f = fixture(t);
    const path = join(f.root, `${f.options.label}.workload-script-launch.failed.json`);
    if (kind === "file") writeFileSync(path, "existing-private-evidence", { mode: 0o600 });
    else symlinkSync(join(f.root, "absent"), path);
    assert.throws(() => captureWorkloadScriptPreflight(f.options), /workload-script-launch-already-failed/);
    assert.throws(() => requireWorkloadScript(f.options), /workload-script-launch-already-failed/);
    assert.equal(existsSync(f.report), false);
    if (kind === "file") assert.equal(readFileSync(path, "utf8"), "existing-private-evidence");
    else assert.equal(existsSync(join(f.root, "absent")), false);
  }
});

test("runbook creates a fresh 0700 label directory before any redirected output and stops on an existing 0755 directory", t => {
  for (const fresh of [true, false]) {
    const f = fixture(t);
    if (fresh) rmSync(f.directory, { recursive: true });
    else chmodSync(f.directory, 0o755);
    const cli = spawnSync("sh", ["-c", 'umask 077\n' +
      'node "$DIRECTORY_HELPER" create --directory "$PRIVATE_DIR" || exit 2\n' +
      'printf ready >"$PRIVATE_DIR/script-preflight.stdout.json"\n'], { encoding: "utf8", timeout: 3000,
      env: { ...process.env, PRIVATE_DIR: f.directory,
        DIRECTORY_HELPER: new URL("./physical_artifact_directory.mjs", import.meta.url).pathname } });
    assert.equal(cli.status, fresh ? 0 : 2);
    assert.equal(JSON.parse(cli.stdout).eligible, fresh);
    assert.equal(existsSync(join(f.directory, "script-preflight.stdout.json")), fresh);
    assert.equal(statSync(f.directory).mode & 0o7777, fresh ? 0o700 : 0o755);
    if (fresh) assert.equal(statSync(join(f.directory, "script-preflight.stdout.json")).mode & 0o7777, 0o600);
  }
});
