import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { evaluateWorkloadCoverage, ownWorkloads, parseArgs, recordChild, requireCompletedWorkloads, requireLiveCollector,
  inspectWorkloadAttempt, retainRejectedWorkloadInvocation, requireRetainedWorkloadForeground, runCommand, workloadOwnerProcess } from "./physical_workload_receipt.mjs";

const script = new URL("./physical_workload_receipt.mjs", import.meta.url).pathname;
const closed = (pid = 500, exitCode = 0) => ({ closed: true, pid, exitCode, signal: null, interrupted: false });
const ownerProcessRow = (pid, change = {}) => ({ status: 0,
  stdout: `${pid} ${change.status ?? "S"} 700 ${change.foreground ?? 700} ${change.tty ?? "ttys-fixture"} Mon Sep 21 10:11:${change.seconds ?? "12"} 2026\n` });
const quoted = (value) => `'${value.replaceAll("'", "'\\''")}'`;
function retainedPty(args) {
  return process.platform === "darwin" ? ["/usr/bin/script", ["-q", "/dev/null", ...args]]
    : ["script", ["-q", "-e", "-c", args.map(quoted).join(" "), "/dev/null"]];
}
function telemetry(path, label, end = 10_000, first = end - 2000) {
  const records = [{ type: "environment", label }];
  for (let start = first; start < end; start += 1000) records.push({ type: "sample",
    startTimeUnixMs: start, endTimeUnixMs: Math.min(start + 1000, end),
    eligibility: { eligible: true }, telemetryErrors: [] });
  writeFileSync(path, records.map(JSON.stringify).join("\n") + "\n", { mode: 0o600 });
  return records;
}

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "physical-workload-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const options = { serial: "fake-device", label: "cell-01", output: join(directory, "workloads.json"),
    children: ["pages", "fast-1"], "collector-pid": 900, telemetry: join(directory, "telemetry.ndjson"), command: ["fake-owner"] };
  telemetry(options.telemetry, options.label);
  let now = 10_000;
  let ownerPath;
  const calls = [];
  const f = { directory, options, calls, collectorLive: true, live: new Set(),
    hostProcess: (pid) => ownerProcessRow(pid),
    childRun: async () => closed(), ownerResult: closed(501),
    deps: { terminal: () => ({ inputTTY: true, outputTTY: true }), hostProcess: (pid) => f.hostProcess(pid),
      wallNow: () => ++now, isLive: (pid) => pid === 900 ? f.collectorLive : f.live.has(pid),
      adb: (args) => { calls.push(args); return { status: args.includes("pidof") ? 1 : 0, stdout: "", stderr: "" }; } },
    get ownerPath() { return ownerPath; },
    async child(name) { return recordChild({ ownerPath, name, command: ["fake-probe"] }, { ...f.deps, run: f.childRun }); },
    async cleanup(serial = options.serial) { return recordChild({ ownerPath, cleanup: true, serial }, f.deps); },
    async run(body = async () => { for (const name of options.children) await f.child(name); await f.cleanup(); }) {
      return ownWorkloads(options, { ...f.deps, run: async (_command, env) => {
        ownerPath = env.URNETWORK_PERF_WORKLOAD_OWNER; await body(); return f.ownerResult;
      } });
    },
    check() { return requireCompletedWorkloads(options.output, options.label, options.serial, f.deps); },
  };
  return f;
}

test("owner predeclares unique ordered children and requires explicit collector ownership", () => {
  const parsed = parseArgs(["owner", "--serial", "fake", "--label", "cell", "--output", "out", "--children", "pages,fast-1",
    "--collector-pid", "42", "--telemetry", "samples", "--", "sh", "workload.sh"]);
  assert.deepEqual(parsed.children, ["pages", "fast-1"]);
  assert.deepEqual(parsed.command, ["sh", "workload.sh"]);
  for (const args of [[], ["child", "--name", "pages"], ["cleanup", "--serial", "fake", "--", "true"],
    ["owner", "--label", "cell", "--output", "out", "--children", "pages", "--", "true"]]) assert.throws(() => parseArgs(args));
});

test("invalid TTY, redirected, background or missing owner rejects before collector/artifacts/child spawn", async (t) => {
  for (const change of ["stdin", "stdout", "background", "no-terminal", "dead", "stopped"]) {
    const f = fixture(t);
    f.deps.terminal = () => ({ inputTTY: change !== "stdin", outputTTY: change !== "stdout" });
    f.deps.hostProcess = (pid) => change === "dead" ? { status: 1, stdout: "private-ps-diagnostic" } : ownerProcessRow(pid,
      change === "background" ? { foreground: 701 } : change === "no-terminal" ? { tty: "??" } :
        change === "stopped" ? { status: "T" } : {});
    f.deps.isLive = () => assert.fail("invalid owner precedes collector access");
    await assert.rejects(f.run(() => assert.fail("invalid owner must not launch any child")),
      /retained-workload-foreground-pty-required|workload-owner-not-(?:foreground|live)/);
    assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson"]);
    assert.equal(f.calls.length, 0);
  }
});

test("owner process proof is bounded and retains no raw terminal name, date or command", () => {
  const proof = workloadOwnerProcess(ownerProcessRow(123), 123);
  assert.deepEqual(Object.keys(proof).sort(), ["foregroundGroup", "identity", "processGroup"]);
  assert.equal(/^[a-f0-9]{64}$/.test(proof.identity), true);
  assert.equal(JSON.stringify(proof).includes("ttys-fixture"), false);
  assert.notEqual(workloadOwnerProcess(ownerProcessRow(123, { seconds: "13" }), 123).identity, proof.identity);
  assert.notEqual(workloadOwnerProcess(ownerProcessRow(123, { tty: "ttys-other" }), 123).identity, proof.identity);
  for (const result of [{ status: 0, stdout: "x".repeat(2049) }, { status: 0, stdout: "partial" },
    { ...ownerProcessRow(123), error: { code: "ETIMEDOUT" } }, { ...ownerProcessRow(123), signal: "SIGTERM" }]) {
    assert.throws(() => workloadOwnerProcess(result, 123), /workload-owner-not-live/);
  }
  assert.throws(() => workloadOwnerProcess(ownerProcessRow(123), 124), /workload-owner-not-live/);
  assert.throws(() => requireRetainedWorkloadForeground({ inputTTY: true, outputTTY: true, processGroup: 700, foregroundGroup: 701 }),
    /retained-workload-foreground-pty-required/);
});

test("dead, replaced or background owner cannot start another child or browser cleanup", async (t) => {
  for (const change of ["dead", "reused-pid", "background"]) {
    const f = fixture(t); let ran = 0;
    await assert.rejects(f.run(async () => {
      f.hostProcess = (pid) => change === "dead" ? { status: 1 } :
        ownerProcessRow(pid, change === "reused-pid" ? { seconds: "13" } : { foreground: 701 });
      f.childRun = () => { ran++; return closed(); };
      await f.child("pages");
    }), /workload-owner-(?:not-live|replaced|not-foreground)/);
    assert.equal(ran, 0); assert.equal(f.calls.length, 0);
    assert.equal(existsSync(f.options.output), false);
    await assert.rejects(f.cleanup(), /workload-owner-(?:not-live|replaced|not-foreground)/);
  }
});

test("owner loss during a child cannot publish child or terminal success", async (t) => {
  const f = fixture(t);
  f.childRun = async () => { f.hostProcess = () => ({ status: 1 }); return closed(); };
  await assert.rejects(f.run(), /workload-owner-not-live/);
  assert.equal(existsSync(f.options.output), false);
  assert.equal(readdirSync(f.directory).some(name => name.includes(".owner-")), true, "partial ownership evidence is retained");
  assert.equal(readdirSync(join(f.ownerPath, "..")).includes("pages.complete.json"), false);
});

test("empty or bare-Node foreground commands are rejected before an interactive process can start", async () => {
  await assert.rejects(runCommand(["node"]), /foreground-command-required/);
  await assert.rejects(runCommand(["", "workload.sh"]), /foreground-command-required/);
});

test("3HECGG root: a missing receipt helper fails in the shell before Node can enter its REPL", () => {
  const result = spawnSync("sh", ["-c", ': "${RECEIPT:?workload receipt helper required}"; node "$RECEIPT"'],
    { encoding: "utf8", env: { ...process.env, RECEIPT: "" } });
  assert.notEqual(result.status, 0);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /Welcome to Node/);
});

test("owner receipt is exclusive, private, device-bound and published only after children plus cleanup", async (t) => {
  const f = fixture(t);
  const receipt = await f.run();
  assert.deepEqual(receipt.childReceipts.map((r) => r.name), ["pages", "fast-1", "cleanup"]);
  assert.equal(receipt.failedChildCount, 0);
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.deepEqual(f.check(), receipt);
  assert.equal(f.calls.length, 2);
  assert.deepEqual(f.calls[0], ["-s", "fake-device", "shell", "am", "force-stop", "com.android.chrome"]);
  assert.deepEqual(f.calls[1], ["-s", "fake-device", "shell", "pidof", "com.android.chrome"]);
  assert.throws(() => requireCompletedWorkloads(f.options.output, "different-cell", "fake-device", f.deps));
  assert.throws(() => requireCompletedWorkloads(f.options.output, f.options.label, "different-device", f.deps));
  await assert.rejects(f.run(), /output-already-exists/);
});

test("terminal receipts without a retained owner proof cannot authorize quiet", async (t) => {
  const f = fixture(t); const receipt = await f.run();
  for (const proof of [undefined, { ...receipt.retainedOwner, identity: "invalid" },
    { ...receipt.retainedOwner, foreground: { ...receipt.retainedOwner.foreground, outputTTY: false } }]) {
    writeFileSync(f.options.output, JSON.stringify({ ...receipt, retainedOwner: proof }), { mode: 0o600 });
    assert.throws(() => f.check(), /terminal-owner-receipt-required/);
  }
});

test("gShVxc: executor yield while child is held never creates a completion receipt", async (t) => {
  const f = fixture(t);
  let release;
  let entered;
  const active = new Promise((resolve) => { entered = resolve; });
  let calls = 0;
  f.childRun = async () => {
    if (calls++ === 0) { entered(); return new Promise((resolve) => { release = () => resolve(closed()); }); }
    return closed();
  };
  const result = f.run();
  await active;
  assert.equal(existsSync(f.options.output), false);
  assert.throws(() => f.check());
  assert.equal(f.calls.length, 0, "browser cleanup cannot race an unjoined child");
  release();
  await result;
  assert.equal(f.check().state, "complete");
});

test("missing child, skipped order, duplicate child and missing cleanup cannot qualify", async (t) => {
  for (const body of [async (f) => {}, async (f) => f.child("fast-1"),
    async (f) => { await f.child("pages"); await f.child("pages"); },
    async (f) => { await f.child("pages"); await f.child("fast-1"); }]) {
    const f = fixture(t);
    await assert.rejects(f.run(() => body(f)));
    assert.equal(existsSync(f.options.output), false);
  }
});

test("yielded, signalled, interrupted and shell-masked interruption results are rejected", async (t) => {
  for (const result of [{ sessionId: 17 }, { ...closed(), closed: false }, { ...closed(), signal: "SIGTERM" },
    { ...closed(), interrupted: true }, closed(500, 130)]) {
    const f = fixture(t); f.childRun = async () => result;
    await assert.rejects(f.run(), /child-not-joined-or-interrupted/);
    assert.equal(existsSync(f.options.output), false);
  }
  const f = fixture(t); f.ownerResult = { ...closed(501), interrupted: true };
  await assert.rejects(f.run(), /owner-not-joined-or-interrupted/);
});

test("a shell exit cannot conceal a live background process group", async (t) => {
  const f = fixture(t); f.live.add(-500);
  await assert.rejects(f.run(), /child-not-joined-or-interrupted/);
  assert.equal(existsSync(f.options.output), false);
});

test("normal probe failure is preserved while allowing joined diagnostic memory collection", async (t) => {
  const f = fixture(t); let count = 0;
  f.childRun = async () => closed(500, count++ === 0 ? 2 : 0);
  await f.run();
  assert.equal(f.check().failedChildCount, 1);
  assert.equal(f.check().childReceipts[0].exitCode, 2);
});

test("8vdtZ8 root: malformed cleanup preserves four successful children and explicit private parser/owner failure", async (t) => {
  const f = fixture(t);
  f.options.children = ["wiki", "fast-1", "fast-2", "fast-3"];
  f.ownerResult = closed(501, 2);
  await assert.rejects(f.run(async () => {
    for (const name of f.options.children) await f.child(name);
    const args = ["cleanup", "--", "adb", "-s", "fake-device", "shell", "am", "force-stop", "com.android.chrome"];
    let rejected;
    try { parseArgs(args); } catch (error) { rejected = error; }
    assert.ok(rejected);
    assert.equal(retainRejectedWorkloadInvocation(args, rejected, { ...f.deps, ownerPath: f.ownerPath }), true);
  }), /owner-command-failed/);
  const ownerFailure = JSON.parse(readFileSync(`${f.options.output}.failed.json`, "utf8"));
  const cleanupFailure = JSON.parse(readFileSync(join(f.ownerPath, "..", "cleanup.failed.json"), "utf8"));
  assert.equal(ownerFailure.reason, "owner-command-failed");
  assert.equal(ownerFailure.result.exitCode, 2);
  assert.equal(ownerFailure.result.joined, true);
  assert.equal(cleanupFailure.reason, "explicit-owner-child-command-or-cleanup-required");
  assert.equal(cleanupFailure.stage, "arguments");
  assert.equal(existsSync(join(f.ownerPath, "..", "cleanup.started.json")), false);
  assert.equal(f.calls.length, 0, "bad cleanup cannot execute its arbitrary adb command");
  for (const name of f.options.children) assert.equal(JSON.parse(readFileSync(join(f.ownerPath, "..", `${name}.complete.json`))).exitCode, 0);
  for (const path of [`${f.options.output}.failed.json`, join(f.ownerPath, "..", "cleanup.failed.json")]) assert.equal(statSync(path).mode & 0o777, 0o600);
  assert.equal(existsSync(f.options.output), false);
  assert.throws(() => f.check(), /owner-failure-already-recorded/);
  await assert.rejects(f.run(), /owner-failure-already-recorded/);
  await assert.rejects(f.cleanup(), /child-failure-already-recorded/);
});

test("outer nonzero, signal, shell-masked signal, unjoined group and thrown errors always retain failure, never success", async (t) => {
  const cases = [closed(501, 7), closed(501, 130), { ...closed(501), exitCode: null, signal: "SIGTERM", interrupted: true },
    { ...closed(501), closed: false }, { ...closed(501), launchError: "missing-executable", pid: undefined }];
  for (const result of cases) {
    const f = fixture(t); f.ownerResult = result;
    await assert.rejects(f.run(async () => {}), /owner-command-failed|owner-not-joined-or-interrupted/);
    const failed = JSON.parse(readFileSync(`${f.options.output}.failed.json`));
    assert.equal(failed.state, "failed");
    assert.equal(failed.result.exitCode, result.exitCode);
    assert.equal(failed.result.signal, result.signal);
    assert.equal(failed.result.interrupted, result.interrupted);
    assert.equal(existsSync(f.options.output), false);
  }
  const f = fixture(t);
  await assert.rejects(f.run(() => { throw new Error("private-command-and-credentials"); }), /private-command/);
  const failure = readFileSync(`${f.options.output}.failed.json`, "utf8");
  assert.equal(JSON.parse(failure).reason, "evidence-unavailable");
  assert.doesNotMatch(failure, /private-command|credentials/);
});

test("child signal/spawn/owner-loss failures retain private per-child results and cannot be retried", async (t) => {
  for (const result of [{ ...closed(), exitCode: null, signal: "SIGKILL" }, closed(500, 130),
    { ...closed(), pid: undefined, exitCode: null, launchError: "missing-executable" }]) {
    const f = fixture(t); f.childRun = async () => result;
    await assert.rejects(f.run(), /child-not-joined-or-interrupted/);
    const failed = JSON.parse(readFileSync(join(f.ownerPath, "..", "pages.failed.json")));
    assert.equal(failed.state, "failed");
    assert.equal(failed.result.signal, result.signal);
    assert.equal(failed.result.exitCode, result.exitCode);
    assert.equal(failed.result.launchError, result.launchError ?? null);
    assert.equal(existsSync(join(f.ownerPath, "..", "pages.complete.json")), false);
    await assert.rejects(f.child("pages"), /child-failure-already-recorded/);
  }
});

test("lost terminal after an uncatchable owner exit is inspectable but never qualified or given an invented exit", async (t) => {
  const f = fixture(t);
  let release;
  let entered;
  const active = new Promise(resolve => { entered = resolve; });
  const run = f.run(async () => { entered(); await new Promise(resolve => { release = resolve; }); });
  await active;
  f.hostProcess = () => ({ status: 1 });
  const inspection = inspectWorkloadAttempt({ owner: f.ownerPath, output: f.options.output }, f.deps);
  assert.equal(inspection.classification, "INVALID_WORKLOAD_OWNER");
  assert.equal(inspection.reason, "missing-required-terminal-receipt");
  assert.equal(inspection.eligible, false);
  assert.equal(Object.hasOwn(inspection, "exitCode"), false);
  assert.equal(existsSync(f.options.output), false);
  assert.equal(existsSync(`${f.options.output}.failed.json`), false, "inspection must not synthesize a terminal receipt");
  release();
  await assert.rejects(run, /workload-owner-not-live/);
});

test("missing executable waits for close and preserves only a fixed launch error", async () => {
  const result = await runCommand(["/nonexistent-physical-workload-fixture/command"]);
  assert.equal(result.closed, true);
  assert.equal(result.launchError, "missing-executable");
  assert.equal(result.pid, undefined);
  assert.doesNotMatch(JSON.stringify(result), /nonexistent-physical/);
});

test("offline cleanup argument preflight accepts only explicit serial, never touches adb or an owner", () => {
  for (const valid of [true, false]) {
    const args = valid ? ["cleanup", "--serial", "fake-device"] :
      ["cleanup", "--", "adb", "-s", "fake-device", "shell", "am", "force-stop", "com.android.chrome"];
    const result = spawnSync(process.execPath, [script, "validate", "--", ...args], { encoding: "utf8", timeout: 3000 });
    assert.equal(result.status, valid ? 0 : 2);
    if (valid) assert.deepEqual(JSON.parse(result.stdout), { type: "workload-arguments-preflight", valid: true, mode: "cleanup" });
    else assert.equal(result.stderr, "workload receipt failed: explicit-owner-child-command-or-cleanup-required\n");
    assert.doesNotMatch(result.stdout + result.stderr, /fake-device|com.android.chrome/);
  }
});

test("scripted owners reject wrong label/missing body before collector access or workload launch", async t => {
  for (const wrongLabel of [true, false]) {
    const f = fixture(t);
    const directory = join(f.directory, f.options.label);
    mkdirSync(directory, { mode: 0o700 });
    f.options.output = join(directory, "workloads.json");
    f.options.mode = wrongLabel ? "owner" : "owner-script";
    f.options.command = wrongLabel ? ["sh", join(f.directory, "previous-label", "traffic-workload.sh")] : [];
    f.deps.isLive = () => assert.fail("bad body precedes collector access");
    await assert.rejects(f.run(() => assert.fail("bad body cannot launch")), /passing-script-preflight-required/);
    const report = JSON.parse(readFileSync(`${f.options.output}.script-preflight.json`));
    assert.equal(report.classification, "INVALID_WORKLOAD_SCRIPT");
    assert.equal(report.reason, wrongLabel ? "workload-script-path-mismatch" : "workload-script-missing");
    assert.equal(f.ownerPath, undefined);
    assert.equal(existsSync(f.options.output), false);
  }
});

test("script preflight is rechecked after collector/owner setup before the body can spawn", async t => {
  const f = fixture(t);
  const directory = join(f.directory, f.options.label);
  mkdirSync(directory, { mode: 0o700 });
  f.options.output = join(directory, "workloads.json");
  f.options.mode = "owner-script";
  f.options.command = [];
  const path = join(directory, "traffic-workload.sh");
  const body = 'set -u\nnode "$RECEIPT" cleanup --serial "$SERIAL"\n';
  writeFileSync(path, body, { mode: 0o600 });
  const live = f.deps.isLive;
  f.deps.isLive = pid => { writeFileSync(path, `# changed after preflight\n${body}`); return live(pid); };
  await assert.rejects(f.run(() => assert.fail("changed script cannot spawn")), /workload-script-binding-changed/);
  assert.equal(JSON.parse(readFileSync(`${f.options.output}.failed.json`)).stage, "script-recheck");
  assert.equal(existsSync(f.options.output), false);
});

test("real redirected preflight producer round-trips to retained owner-script with the exact cleanup body", t => {
  for (const outsideOutput of [false, true]) {
  const f = fixture(t);
  const directory = join(f.directory, f.options.label);
  mkdirSync(directory, { mode: 0o700 });
  f.options.output = outsideOutput ? join(f.directory, `${f.options.label}.workloads.json`) : join(directory, "workloads.json");
  telemetry(f.options.telemetry, f.options.label, Date.now());
  writeFileSync(join(f.directory, "adb"), `#!${process.execPath}\n` +
    `const a=process.argv.slice(2);if(a[3]==='pidof')process.exitCode=1;\n`, { mode: 0o700 });
  writeFileSync(join(directory, "traffic-workload.sh"), 'set -u\n' +
    'printf derived >"$PRIVATE_DIR/derived.marker"\n' +
    'node "$RECEIPT" child --name pages -- node -e "process.exit(0)"\n' +
    'node "$RECEIPT" child --name fast-1 -- node -e "process.exit(0)"\n' +
    'node "$RECEIPT" cleanup --serial "$SERIAL" >"$PRIVATE_DIR/chrome-cleanup.stdout" 2>"$PRIVATE_DIR/chrome-cleanup.stderr"\n', { mode: 0o600 });
  const summaryPath = join(directory, "script-preflight.stdout.json");
  const preflight = spawnSync("sh", ["-c", 'umask 077\nnode "$RECEIPT" script-preflight --label "$LABEL" --output "$OUTPUT" ' +
    '>"$PRIVATE_DIR/script-preflight.stdout.json" 2>"$PRIVATE_DIR/script-preflight.stderr" || exit 2\n'],
    { encoding: "utf8", timeout: 3000, env: { ...process.env, RECEIPT: script, LABEL: f.options.label,
      OUTPUT: f.options.output, PRIVATE_DIR: directory } });
  assert.equal(preflight.status, 0, preflight.stderr);
  assert.equal(preflight.stdout, "");
  const summary = JSON.parse(readFileSync(summaryPath));
  assert.equal(summary.classification, "WORKLOAD_SCRIPT_READY");
  assert.equal(summary.eligible, true);
  assert.equal(Object.hasOwn(summary, "schema"), false);
  assert.equal(Object.hasOwn(summary, "proof"), false);
  assert.equal(statSync(summaryPath).mode & 0o7777, 0o600);
  const reportPath = `${f.options.output}.script-preflight.json`;
  const privateReceipt = readFileSync(reportPath, "utf8");
  const report = JSON.parse(privateReceipt);
  assert.equal(report.schema, 1);
  assert.equal(report.type, "workload-script-preflight");
  assert.equal(report.eligible, true);
  assert.equal(report.classification, "WORKLOAD_SCRIPT_READY");
  assert.equal(report.reason, "exact-label-script-and-cleanup-verified");
  assert.equal(typeof report.proof.script.sha256, "string");
  assert.equal(statSync(reportPath).mode & 0o7777, 0o600);
  const [pty, args] = retainedPty([process.execPath, script, "owner-script", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", "pages,fast-1", "--collector-pid", `${process.pid}`, "--telemetry", f.options.telemetry]);
  const result = spawnSync(pty, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], timeout: 10000,
    env: { ...process.env, PATH: `${f.directory}:${process.env.PATH}`, PRIVATE_DIR: "/wrong-label", RECEIPT: "/wrong-helper", SERIAL: "wrong-serial" } });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.equal(readFileSync(join(directory, "derived.marker"), "utf8"), "derived");
  assert.equal(requireCompletedWorkloads(f.options.output, f.options.label, f.options.serial).childReceipts.length, 3);
  assert.equal(readFileSync(reportPath, "utf8"), privateReceipt, "owner consumes the original receipt without rewriting it");
  }
});

test("7V4Dk3 negative preflight publishes a receipt but must stop before owner launch", async t => {
  for (const guarded of [false, true]) {
    const f = fixture(t);
    const directory = join(f.directory, f.options.label);
    mkdirSync(directory, { mode: 0o700 });
    f.options.output = join(directory, "workloads.json");
    f.options.mode = "owner-script";
    f.options.command = [];
    // Correct built-in cleanup argv does not make arbitrary shell redirection
    // part of the fixed body contract. A receipt exists for failure as well.
    writeFileSync(join(directory, "traffic-workload.sh"), 'set -u\n' +
      'node "$RECEIPT" cleanup --serial "$SERIAL" >"$PRIVATE_DIR/cleanup.stdout" 2>"$PRIVATE_DIR/cleanup.stderr"\n', { mode: 0o600 });
    const preflight = spawnSync("sh", ["-c", 'umask 077\nnode "$RECEIPT" script-preflight --label "$LABEL" --output "$OUTPUT" ' +
      '>"$PRIVATE_DIR/script-preflight.stdout.json" 2>"$PRIVATE_DIR/script-preflight.stderr"' +
      (guarded ? ' || exit 2\n' : '\n') + 'printf attempted >"$PRIVATE_DIR/owner-attempt.marker"\n'],
      { encoding: "utf8", timeout: 3000, env: { ...process.env, RECEIPT: script, LABEL: f.options.label,
        OUTPUT: f.options.output, PRIVATE_DIR: directory } });
    assert.equal(preflight.status, guarded ? 2 : 0);
    assert.equal(existsSync(join(directory, "owner-attempt.marker")), !guarded);
    const reportPath = `${f.options.output}.script-preflight.json`;
    const original = readFileSync(reportPath, "utf8");
    const report = JSON.parse(original);
    assert.equal(report.schema, 1);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "exact-final-cleanup-serial-required");
    assert.equal(Object.hasOwn(report, "proof"), false);
    assert.equal(JSON.parse(readFileSync(join(directory, "script-preflight.stdout.json"))).eligible, false);
    f.deps.isLive = () => assert.fail("failed producer cannot reach collector validation");
    await assert.rejects(f.run(() => assert.fail("failed producer cannot launch workload")), /passing-script-preflight-required/);
    assert.equal(readFileSync(reportPath, "utf8"), original);
    assert.equal(f.ownerPath, undefined);
    assert.equal(existsSync(f.options.output), false);
  }
});

test("cleanup must stop the correct browser and prove its process absent", async (t) => {
  for (const result of [{ status: 0, stdout: "123", stderr: "" }, { status: 1, stdout: "", stderr: "adb error" },
    { status: null, stdout: "", stderr: "", signal: "SIGTERM" }]) {
    const f = fixture(t);
    f.deps.adb = (args) => args.includes("pidof") ? result : { status: 0 };
    await assert.rejects(f.run(), /browser-cleanup-not-verified/);
    assert.equal(existsSync(f.options.output), false);
  }
  const f = fixture(t);
  await assert.rejects(f.run(async () => { await f.child("pages"); await f.child("fast-1"); await f.cleanup("wrong-device"); }),
    /matching-browser-cleanup-serial-required/);
});

test("gy8htp: no workload may start before a live collector has a fresh eligible sample", async (t) => {
  for (const mutate of [
    (f) => { f.collectorLive = false; },
    (f) => { writeFileSync(f.options.telemetry, ""); },
    (f) => { telemetry(f.options.telemetry, f.options.label, 4000); },
    (f) => { telemetry(f.options.telemetry, "wrong-label"); },
    (f) => { const r = telemetry(f.options.telemetry, f.options.label); r.at(-1).eligibility.eligible = false;
      writeFileSync(f.options.telemetry, r.map(JSON.stringify).join("\n") + "\n"); },
  ]) {
    const f = fixture(t); mutate(f); let ran = false;
    await assert.rejects(f.run(async () => { ran = true; }));
    assert.equal(ran, false);
    assert.equal(existsSync(f.options.output), false);
  }
});

test("collector death or restart during a child invalidates the owner", async (t) => {
  for (const change of [
    (f) => { f.collectorLive = false; },
    (f) => { telemetry(f.options.telemetry, f.options.label, 10_000, 9000); },
  ]) {
    const f = fixture(t); f.childRun = async () => { change(f); return closed(); };
    await assert.rejects(f.run());
    assert.equal(existsSync(f.options.output), false);
  }
});

test("active coverage rejects a late collector even when its five quiet minutes are perfect", (t) => {
  const f = fixture(t);
  const rows = telemetry(f.options.telemetry, f.options.label, 350_000, 50_000);
  assert.equal(evaluateWorkloadCoverage(rows, 50_000, 350_000, f.options.label).eligible, true);
  assert.equal(evaluateWorkloadCoverage(rows, 1000, 350_000, f.options.label).eligible, false);
  const gap = rows.filter((r) => r.type !== "sample" || r.startTimeUnixMs < 60_000 || r.startTimeUnixMs > 70_000);
  assert.equal(evaluateWorkloadCoverage(gap, 50_000, 350_000, f.options.label).eligible, false);
});

test("terminated collector cannot pass the final live check", async (t) => {
  const f = fixture(t); await f.run();
  f.collectorLive = false;
  assert.throws(() => f.check(), /collector-owner-not-live/);
});

test("real nonretained CLI rejects before command/collector access or artifact creation", (t) => {
  const f = fixture(t); const marker = join(f.directory, "must-not-start");
  const result = spawnSync(process.execPath, [script, "owner", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", "pages", "--collector-pid", "1", "--telemetry", "missing-telemetry",
    "--", process.execPath, "-e", `require('node:fs').writeFileSync(${JSON.stringify(marker)},'started')`],
  { encoding: "utf8", timeout: 3000 });
  assert.equal(result.status, 2); assert.equal(result.stdout, "");
  assert.equal(result.stderr, "workload receipt failed: retained-workload-foreground-pty-required\n");
  assert.equal(existsSync(marker), false);
  assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson"]);
});

for (const invalidCleanup of [false, true]) test(invalidCleanup
  ? "8vdtZ8 real retained PTY reproduces bad cleanup after four successful children and preserves failed terminals"
  : "real retained PTY CLI joins actual children and fake browser cleanup without adb/devices", (t) => {
  const f = fixture(t);
  if (invalidCleanup) f.options.children = ["wiki", "fast-1", "fast-2", "fast-3"];
  telemetry(f.options.telemetry, f.options.label, Date.now());
  writeFileSync(join(f.directory, "adb"), `#!${process.execPath}\n` +
    `const assert=require('node:assert/strict'),a=process.argv.slice(2);assert.deepEqual(a.slice(0,3),['-s','fake-device','shell']);` +
    `assert.equal(a.at(-1),'com.android.chrome');if(a[3]==='pidof')process.exitCode=1;else assert.equal(a[3],'am');\n`, { mode: 0o700 });
  const ownerScript = join(f.directory, "owner.mjs");
  const cleanupArgs = invalidCleanup ? ["cleanup", "--", "adb", "-s", "fake-device", "shell", "am", "force-stop", "com.android.chrome"]
    : ["cleanup", "--serial", "fake-device"];
  writeFileSync(ownerScript, `import {spawnSync} from 'node:child_process';\n` +
    `for(const name of ${JSON.stringify(f.options.children)}){const r=spawnSync(${JSON.stringify(process.execPath)},[${JSON.stringify(script)},'child','--name',name,'--',${JSON.stringify(process.execPath)},'-e','process.exit(0)'],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??2);}\n` +
    `const r=spawnSync(${JSON.stringify(process.execPath)},[${JSON.stringify(script)},...${JSON.stringify(cleanupArgs)}],{stdio:'inherit'});process.exitCode=r.status??2;\n`);
  const [pty, args] = retainedPty([process.execPath, script, "owner", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", f.options.children.join(","), "--collector-pid", `${process.pid}`,
    "--telemetry", f.options.telemetry, "--", process.execPath, ownerScript]);
  const result = spawnSync(pty, args,
  { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], timeout: 10_000,
    env: { ...process.env, PATH: `${f.directory}:${process.env.PATH}` } });
  assert.equal(result.status, invalidCleanup ? 2 : 0, `${result.stdout}${result.stderr}`);
  if (invalidCleanup) {
    const failed = JSON.parse(readFileSync(`${f.options.output}.failed.json`));
    assert.equal(failed.reason, "owner-command-failed");
    assert.equal(failed.result.exitCode, 2);
    assert.equal(failed.result.joined, true);
    const ownerDirectory = `${f.options.output}.owner-${failed.ownerId}`;
    assert.equal(JSON.parse(readFileSync(join(ownerDirectory, "cleanup.failed.json"))).reason, "explicit-owner-child-command-or-cleanup-required");
    assert.equal(existsSync(join(ownerDirectory, "cleanup.started.json")), false);
    for (const name of f.options.children) assert.equal(JSON.parse(readFileSync(join(ownerDirectory, `${name}.complete.json`))).exitCode, 0);
    assert.equal(existsSync(f.options.output), false);
    return;
  }
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /workload receipt failed/);
  const receipt = requireCompletedWorkloads(f.options.output, f.options.label, f.options.serial);
  assert.equal(receipt.childReceipts.length, 3);
  assert.equal(receipt.retainedOwner.foreground.inputTTY, true);
  assert.equal(receipt.retainedOwner.foreground.outputTTY, true);
  assert.equal(receipt.retainedOwner.foreground.processGroup, receipt.retainedOwner.foreground.foregroundGroup);
  assert.ok(receipt.childReceipts.every((r) => r.wrapperPid !== process.pid));
});

test("real retained owner interruption never publishes a terminal success receipt", { timeout: 10_000 }, async (t) => {
  const f = fixture(t); telemetry(f.options.telemetry, f.options.label, Date.now());
  const [pty, args] = retainedPty([process.execPath, script, "owner", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", "pages", "--collector-pid", `${process.pid}`,
    "--telemetry", f.options.telemetry, "--", process.execPath, "-e", "process.stdout.write('probe-ready\\n');setInterval(()=>{},1000)"]);
  const child = spawn(pty, args, { stdio: ["ignore", "pipe", "pipe"] });
  t.after(() => { if (child.exitCode === null && child.signalCode === null) child.kill("SIGTERM"); });
  const terminal = new Promise((resolve) => child.once("close", (code) => resolve(code)));
  let output = "";
  await Promise.race([terminal.then(() => assert.fail(`PTY owner exited before probe readiness: ${output}`)),
    new Promise((resolve) => child.stdout.on("data", (chunk) => { output += chunk; if (output.includes("probe-ready")) resolve(); }))]);
  const ownerDirectory = readdirSync(f.directory).find(name => name.startsWith("workloads.json.owner-"));
  const owner = JSON.parse(readFileSync(join(f.directory, ownerDirectory, "owner.json"), "utf8"));
  process.kill(owner.ownerPid, "SIGTERM");
  assert.equal(await terminal, 2);
  assert.equal(existsSync(f.options.output), false);
  assert.match(output, /owner-not-joined-or-interrupted/);
  const failed = JSON.parse(readFileSync(`${f.options.output}.failed.json`));
  assert.equal(failed.state, "failed");
  assert.equal(failed.result.signal, "SIGTERM");
  assert.equal(failed.result.interrupted, true);
});
