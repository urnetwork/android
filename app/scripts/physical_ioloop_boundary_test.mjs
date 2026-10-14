import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";
import { ioLoopTerminalCopyStep, validateIoLoopTerminalCopy, runIoLoopFastBoundary } from "./physical_ioloop_boundary.mjs";

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

