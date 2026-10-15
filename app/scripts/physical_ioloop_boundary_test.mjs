import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { chmodSync, mkdtempSync, mkdirSync, readFileSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { copyIoLoopTerminal, ioLoopTerminalCopyStep, validateIoLoopTerminalCopy, runIoLoopFastBoundary,
  requireIoLoopWorkloadJoined, parseTerminalArgs, parseWorkloadJoinArgs } from "./physical_ioloop_boundary.mjs";

function privateFixture(t) {
  const directory = mkdtempSync(join(realpathSync(tmpdir()), "ioloop-owner-fixture-"));
  chmodSync(directory, 0o700);
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const write = (path, value) => writeFileSync(path, JSON.stringify(value) + "\n", { mode: 0o600 });
  return { directory, write };
}

function fixture() {
  const serial = "synthetic-device";
  const owner = { ownerId: "22222222-2222-4222-8222-222222222222", launchId: "a".repeat(32), buildId: "fixture-build",
    serialHash: createHash("sha256").update(serial).digest("hex"), ioLoopDiagnostic: true, qualificationEligible: false };
  const ready = { ownerId: owner.ownerId, launchId: owner.launchId, buildId: owner.buildId, serialHash: owner.serialHash,
    ioLoopDiagnostic: true, qualificationEligible: false, targetSessionId: "11111111-1111-4111-8111-111111111111", targetPid: 1234 };
  const teardown = { type: "physical-memory-teardown", schemaVersion: 1, sessionId: ready.targetSessionId,
    buildId: owner.buildId, pid: ready.targetPid, ioLoopDiagnosticReceiptName: "physical-ioloop-" + ready.targetSessionId + ".json" };
  const value = { schema: 1, qualification_eligible: false, sessionId: ready.targetSessionId, buildId: owner.buildId,
    pid: ready.targetPid, capacity: 16, origin_unix_ns: "1792000000000000000", attempts: "1", refused: "0",
    complete: true, deviceJoined: true, constructionStopped: true, loops: [{ ordinal: 1 }] };
  return { serial, owner, ready, teardown, value, output: "/private-fixture/ioloop-final.json" };
}

test("terminal copy binds the Android session rather than the distinct host owner and preserves incomplete evidence", () => {
  const f = fixture();
  const step = ioLoopTerminalCopyStep(f);
  assert.equal(step.kind, "command");
  assert.equal(step.command, "adb");
  assert.deepEqual(step.args, ["-s", f.serial, "exec-out", "run-as", "com.bringyour.network", "cat",
    "files/acceptance/physical-ioloop-" + f.ready.targetSessionId + ".json"]);
  assert.equal(step.timeoutMs, 10000);
  assert.equal(step.qualificationEligible, false);
  assert.equal(step.stdout, f.output);
  assert.equal(Object.hasOwn(step, "joined"), false, "a copy binding cannot attest an executor join");
  for (const incomplete of ["complete", "deviceJoined", "constructionStopped", null]) {
    const value = { ...f.value };
    if (incomplete) value[incomplete] = false;
    const bytes = Buffer.from(JSON.stringify(value));
    const result = validateIoLoopTerminalCopy(bytes, step);
    assert.equal(result.producerComplete, value.complete);
    assert.equal(result.deviceJoined, value.deviceJoined);
    assert.equal(result.constructionStopped, value.constructionStopped);
    assert.equal(Object.hasOwn(result, "complete"), false, "copy identity is not a coverage verdict");
    assert.equal(result.qualificationEligible, false);
    assert.equal(result.sha256, createHash("sha256").update(bytes).digest("hex"));
    assert.equal(result.bytes, bytes.length);
  }
});

test("terminal copy refuses changed owner/session/build/path or malformed and oversized scalar artifacts", () => {
  for (const change of ["disabled", "eligible", "launch", "host-as-app-session", "pid", "build", "name", "relative-output"]) {
    const f = fixture();
    if (change === "disabled") f.owner.ioLoopDiagnostic = false;
    if (change === "eligible") f.owner.qualificationEligible = true;
    if (change === "launch") f.ready.launchId = "b".repeat(32);
    if (change === "host-as-app-session") f.teardown.sessionId = f.owner.ownerId;
    if (change === "pid") f.teardown.pid++;
    if (change === "build") f.teardown.buildId = "another";
    if (change === "name") f.teardown.ioLoopDiagnosticReceiptName = "../../other";
    if (change === "relative-output") f.output = "relative";
    assert.throws(() => ioLoopTerminalCopyStep(f), /ioloop-/);
  }
  const f = fixture(), step = ioLoopTerminalCopyStep(f);
  for (const change of ["sessionId", "buildId", "pid", "qualification_eligible", "capacity", "loops", "attempts"]) {
    const value = { ...f.value };
    value[change] = change === "loops" ? Array(17).fill({}) : change === "attempts" ? 9007199254740992 : "invalid";
    assert.throws(() => validateIoLoopTerminalCopy(Buffer.from(JSON.stringify(value)), step), /ioloop-/);
  }
  for (const bytes of [Buffer.alloc(0), Buffer.alloc(65537), Buffer.from("not-json")]) {
    assert.throws(() => validateIoLoopTerminalCopy(bytes, step));
  }
});

test("only explicit three Fast children and the unchanged bounded child deadline are dispatchable", async () => {
  const valid = { name: "fast-1", serial: "synthetic", instrumentationOwner: "/private-fixture/instrumentation",
    ownerPath: "/private-fixture/workload", command: ["synthetic-fast"], timeoutMs: 95000 };
  for (const delta of [{ name: "pages" }, { name: "fast-4" }, { command: [] }, { timeoutMs: 0 },
    { timeoutMs: 1.5 }, { timeoutMs: 2147483648 }, { instrumentationOwner: "relative" }, { ownerPath: null }]) {
    await assert.rejects(runIoLoopFastBoundary({ ...valid, ...delta }), /ioloop-exact-fast-child-arguments-required/);
  }
});

function joinedFixture(t, mode = "complete") {
  const f = privateFixture(t);
  const serial = "synthetic-device", ownerId = "33333333-3333-4333-8333-333333333333";
  const output = join(f.directory, "workloads.json"), ownerDirectory = output + ".owner-" + ownerId;
  mkdirSync(ownerDirectory, { mode: 0o700 });
  const owner = { schema: 1, type: "workload-owner", ownerId, label: "h1-ioloop-" + "a".repeat(32),
    serialHash: createHash("sha256").update(serial).digest("hex"), ownerPid: 1234,
    startedHostTimeUnixMs: 10, children: ["fast-1"], retainedOwner: { schema: 1, identity: "b".repeat(64),
      foreground: { inputTTY: true, outputTTY: true, processGroup: 1234, foregroundGroup: 1234 } } };
  f.write(join(ownerDirectory, "owner.json"), owner);
  const failure = name => ({ schema: 1, type: name ? "workload-child-failure" : "workload-owner-failure",
    ownerId, ...(name ? { name } : {}), state: "failed", observedHostTimeUnixMs: 40,
    result: { observed: true, closed: true, joined: true, joinTimedOut: false, interrupted: true,
      exitCode: null, signal: "SIGTERM", timedOut: false, launchError: null } });
  if (mode === "complete") f.write(output, { ...owner, state: "complete", exitCode: 0, signal: null,
    interrupted: false, processGroup: 2234, completedHostTimeUnixMs: 50 });
  else f.write(output + ".failed.json", failure());
  const prefix = join(ownerDirectory, "fast-1");
  if (mode === "before-anchor") f.write(prefix + ".ioloop.json", { schema: 1, type: "ioloop-fast-boundaries",
    qualificationEligible: false, workloadOwnerId: ownerId, name: "fast-1", complete: false, exitCode: 2, failureStage: "before-anchor" });
  else {
    f.write(prefix + ".started.json", { wrapperPid: 3234, startedHostTimeUnixMs: 20 });
    if (mode === "canceled") f.write(prefix + ".failed.json", failure("fast-1"));
    else f.write(prefix + ".complete.json", { schema: 1, type: "workload-child", ownerId, name: "fast-1",
      state: "complete", wrapperPid: 3234, processGroup: 4234, exitCode: mode === "invalid-fast" ? 2 : 0,
      signal: null, interrupted: false, startedHostTimeUnixMs: 20, completedHostTimeUnixMs: 30 });
  }
  return { ...f, output, ownerDirectory, owner, prefix,
    options: { serial, output, ownerPid: owner.ownerPid, label: owner.label } };
}

test("whole-session cleanup consumes actual nested join receipts without promoting failed Fast or canceled workloads", t => {
  for (const mode of ["complete", "invalid-fast", "canceled", "before-anchor"]) {
    const f = joinedFixture(t, mode), checked = [];
    const result = requireIoLoopWorkloadJoined(f.options, { isLive: pid => { checked.push(pid); return false; } });
    assert.equal(result.joined, true, mode);
    assert.equal(result.qualificationEligible, false);
    assert.equal(result.failed, mode !== "complete");
    assert.equal(result.childStarted, mode !== "before-anchor");
    assert.ok(checked.includes(1234) && checked.includes(-1234));
    if (mode === "complete") assert.ok(checked.includes(-2234) && checked.includes(-4234));
    if (mode === "invalid-fast") assert.equal(JSON.parse(readFileSync(f.prefix + ".complete.json")).exitCode, 2);
  }
});

test("outer process exit never substitutes for missing, live, foreign or ambiguous nested ownership", t => {
  for (const fault of ["owner-live", "body-live", "wrapper-live", "fast-live", "owner-id", "wrong-pid", "foreign-serial",
    "extra-child", "missing-terminal", "both-terminals", "missing-fast", "failed-unjoined", "public-file"]) {
    const f = joinedFixture(t, fault === "failed-unjoined" ? "canceled" : "complete");
    let live = 0;
    if (fault === "owner-live") live = 1234;
    if (fault === "body-live") live = -2234;
    if (fault === "wrapper-live") live = 3234;
    if (fault === "fast-live") live = -4234;
    if (fault === "owner-id") { f.owner.ownerId = "44444444-4444-4444-8444-444444444444"; f.write(join(f.ownerDirectory, "owner.json"), f.owner); }
    if (fault === "wrong-pid") f.options.ownerPid++;
    if (fault === "foreign-serial") f.options.serial = "different-synthetic-device";
    if (fault === "extra-child") { f.owner.children.push("fast-2"); f.write(join(f.ownerDirectory, "owner.json"), f.owner); }
    if (fault === "missing-terminal") rmSync(f.output);
    if (fault === "both-terminals") f.write(f.output + ".failed.json", {});
    if (fault === "missing-fast") rmSync(f.prefix + ".complete.json");
    if (fault === "failed-unjoined") { const value = JSON.parse(readFileSync(f.prefix + ".failed.json")); value.result.joined = false; f.write(f.prefix + ".failed.json", value); }
    if (fault === "public-file") chmodSync(f.prefix + ".complete.json", 0o644);
    assert.throws(() => requireIoLoopWorkloadJoined(f.options, { isLive: pid => pid === live }), undefined, fault);
  }
});

test("terminal copier uses the exact joined generation and retains incomplete producer values without qualification", t => {
  for (const complete of [true, false]) {
    const f = { ...privateFixture(t), ...fixture() };
    f.owner.startedHostTimeUnixMs = 1; f.ready.hostTimeUnixMs = 2;
    const ownerPath = join(f.directory, "instrumentation-owner.json"), teardownPath = join(f.directory, "teardown.json");
    f.write(ownerPath, f.owner); f.write(ownerPath + ".ready.json", f.ready);
    f.write(ownerPath + ".terminal.json", { ...f.owner, type: "instrumentation-session", state: "complete",
      completedHostTimeUnixMs: 3, exitCode: 0, signal: null, interrupted: false });
    f.write(teardownPath, f.teardown);
    f.value.complete = complete;
    const bytes = Buffer.from(JSON.stringify(f.value));
    const output = join(f.directory, "terminal.json");
    let calls = 0;
    const result = copyIoLoopTerminal({ serial: f.serial, owner: ownerPath, teardown: teardownPath, output }, {
      adb: (argv, limits) => { calls++; assert.deepEqual(argv, ioLoopTerminalCopyStep(f).args);
        assert.equal(limits.timeout, 10000); return { status: 0, stdout: bytes }; },
    });
    assert.equal(calls, 1); assert.deepEqual(readFileSync(output), bytes);
    assert.equal(result.producerComplete, complete); assert.equal(result.qualificationEligible, false);
    assert.equal(result.instrumentationComplete, true);
  }
});

test("terminal copier refuses owner substitution or malformed clocks before any adb call", t => {
  for (const fault of ["launch", "ready-time", "terminal-time", "exit", "signal", "state"]) {
    const f = { ...privateFixture(t), ...fixture() };
    f.owner.startedHostTimeUnixMs = 1; f.ready.hostTimeUnixMs = 2;
    const terminal = { ...f.owner, type: "instrumentation-session", state: "complete", completedHostTimeUnixMs: 3,
      exitCode: 0, signal: null, interrupted: false };
    if (fault === "launch") terminal.launchId = "b".repeat(32);
    if (fault === "ready-time") delete f.ready.hostTimeUnixMs;
    if (fault === "terminal-time") terminal.completedHostTimeUnixMs = 1;
    if (fault === "exit") terminal.exitCode = null;
    if (fault === "signal") terminal.signal = {};
    if (fault === "state") terminal.state = "running";
    const owner = join(f.directory, "owner.json"), teardown = join(f.directory, "teardown.json");
    f.write(owner, f.owner); f.write(owner + ".ready.json", f.ready); f.write(owner + ".terminal.json", terminal); f.write(teardown, f.teardown);
    assert.throws(() => copyIoLoopTerminal({ serial: f.serial, owner, teardown, output: join(f.directory, "output.json") }, {
      adb: () => assert.fail("invalid ownership reached adb"),
    }), /ioloop-terminal-owner-mismatch/, fault);
  }
  for (const parse of [parseTerminalArgs, parseWorkloadJoinArgs]) {
    assert.throws(() => parse([])); assert.throws(() => parse(["--command", "arbitrary"]));
    assert.throws(() => parse(["--serial", "one", "--serial", "two"]));
  }
});

