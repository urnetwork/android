import assert from "node:assert/strict";
import test from "node:test";
import { evaluateMemoryTeardown, memoryEvidencePrefix } from "./physical_memory_teardown.mjs";

// Synthetic process/session identities and controlled observed bytes only.
function evidence() {
  const policy = { memoryProfile: "ios-memory-audit-v2", goRuntimeBytes: 20000000,
    goMemoryLimitBytes: 33554432, goMemoryProfileRateBytes: 0, goMemStatsRuntimeBytes: 20000000,
    idleMemoryTrimCount: 0, lastIdleMemoryTrimBeforeBytes: 0, lastIdleMemoryTrimAfterBytes: 0 };
  const identity = { pid: 42, buildId: "fixture-build", sessionId: "fixture-session" };
  const memory = [{ ...policy, ...identity, type: "sample", phase: "quiet", elapsedMs: 300000, timeUnixMs: 998, samplerSchema: 13, samplerDropped: 0 }];
  const statusRuntime = [1, 2].map(sequence => ({ ...policy, ...identity, type: "status-runtime", sequence, timeUnixMs: 998 + sequence }));
  const samples = ["begin", "cancelled", "joined", "terminal"].map((stage, index) => ({
    sequence: index + 1, stage, timeUnixMs: 1000 + index, elapsedNanos: index,
    goRuntimeBytes: 20000000, goMemoryLimitBytes: 33554432, goMemoryProfileRateBytes: 0 }));
  const native = { type: "device-memory-teardown", schemaVersion: 1, observerId: "fixture-observer", deviceTargetBytes: 33554432,
    state: "complete", failure: "", intervalNanos: 15000000000, capacity: 16, produced: 4, drained: 4, dropped: 0,
    cancelSequence: 2, joinSequence: 3, terminalSequence: 4, observerJoined: true, samples };
  const teardown = { ...identity, type: "physical-memory-teardown", schemaVersion: 1, memoryProfile: policy.memoryProfile,
    native, observerId: native.observerId, finishCommandId: "fixture-finish", deviceDrainerJoined: true,
    deviceRingDrained: true, deviceJoined: true, referencesReleased: true, referencesReleasedTimeUnixMs: 1002,
    filesFlushed: true, statusRuntimeReadCount: 2, failureCount: 0, devicePrimitiveCount: 1, diagnosticBatchesProduced: 1,
    diagnosticBatchesFlushed: 1, exporterFailed: false, diagnosticCommandCount: 0 };
  const finish = { ...policy, ...identity, type: "status", state: "complete", phase: "finish", commandId: "fixture-finish",
    runtimeReadSequence: 2, teardownObserverId: native.observerId, teardownBeginTimeUnixMs: 1000 };
  const owner = { schema: 1, type: "instrumentation-session", state: "running", ownerId: "00000000-0000-0000-0000-000000000001", startedHostTimeUnixMs: 1,
    label: "fixture-label", serialHash: "b".repeat(64), nativeInputHash: "a".repeat(64), nativeBuildOwner: "fixture-native-owner",
    supervisorPid: 100, adbPid: 101, supervisorIdentity: "c".repeat(64), adbIdentity: "d".repeat(64),
    foreground: { inputTTY: true, outputTTY: true, processGroup: 100, foregroundGroup: 100 },
    targetPackage: "com.bringyour.network", className: "com.bringyour.network.acceptance.PhysicalLowbarSessionTest",
    component: "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner" };
  const nativeProof = { type: "physical-native-input-verification", schemaVersion: 1, eligible: true, classification: "NATIVE_INPUTS_VERIFIED",
    buildId: identity.buildId, buildOwner: owner.nativeBuildOwner, inputHash: owner.nativeInputHash, beforeSha256: "e".repeat(64), afterSha256: "f".repeat(64) };
  const ready = { schema: 1, type: "instrumentation-session-ready", ownerId: owner.ownerId, serialHash: owner.serialHash,
    targetPid: 42, elapsedMs: 0, hostTimeUnixMs: 2 };
  const terminal = { ...owner, state: "complete", exitCode: 0, signal: null, interrupted: false, completedHostTimeUnixMs: 2000 };
  const memoryContents = memory.map(JSON.stringify).join("\n") + "\n";
  const diagnostics = ["state", "memory", "memory_device_transport", "memory_device_transfer"].map(part => ({ ...identity,
    memoryProfile: policy.memoryProfile, part, diagnosticBatchSequence: 1, unix_millis: 998, go_total_bytes: 20000000,
    go_limit_bytes: 33554432, memory_profile_rate_bytes: 0, device_memory_target_bytes: 33554432 }));
  const statusContents = statusRuntime.map(JSON.stringify).join("\n") + "\n";
  const diagnosticContents = diagnostics.map(JSON.stringify).join("\n") + "\n";
  const liveGate = { schemaVersion: 4, eligible: true, evaluationMode: "live", ...identity, memoryPrefix: { ...memoryEvidencePrefix(memoryContents), eventCount: memory.length },
    statusPrefix: { ...memoryEvidencePrefix(statusContents), eventCount: 2 }, diagnosticPrefix: { ...memoryEvidencePrefix(diagnosticContents), eventCount: 1 } };
  const producerSummary = { ...identity, type: "physical-memory-producer-summary", schemaVersion: 1, memoryProfile: policy.memoryProfile,
    finishCommandId: finish.commandId, observerId: native.observerId, peakGoRuntimeBytes: 20000000,
    devicePrimitiveCount: 1, teardownPrimitiveCount: 4, statusRuntimeReadCount: 2, diagnosticRuntimeReadCount: 1,
    combinedRetainedRuntimeEventCount: 8, statusMemStatsSnapshotCount: 2, auxiliaryMaintenanceValueCount: 0,
    auxiliaryRuntimeBreachValueCount: 0, exporterFailed: false, failureCount: 0 };
  teardown.producerSummary = { ...producerSummary };
  return { teardown, memory, statusRuntime, diagnostics, finish, owner, ready, terminal, nativeProof, liveGate, memoryContents,
    statusContents, diagnosticContents, producerSummary, expectedFinishCommandId: "fixture-finish" };
}

test("exact current profile has separate counts and a fresh joined terminal", () => {
  const result = evaluateMemoryTeardown(evidence());
  assert.equal(result.eligible, true, JSON.stringify(result));
  assert.deepEqual(result.counts, { devicePrimitiveCount: 1, teardownPrimitiveCount: 4, statusRuntimeReadCount: 2, diagnosticRuntimeReadCount: 1 });
  assert.equal(result.combinedRuntimeReadCount, 8);
  assert.equal(result.statusMemStatsSnapshotCount, 2);
  assert.equal(result.producerPeakRepresentationCount, 2);
  assert.equal(result.sampleScope, "retained-runtime-events-not-all-internal-reads-or-continuous-peak");
});

test("failed writer producer maxima remain auxiliary known breaches even without their original row", () => {
  for (const scope of ["nested", "standalone", "wrong-identity"]) {
    const input = evidence();
    const summary = scope === "nested" ? input.teardown.producerSummary : input.producerSummary;
    summary.peakGoRuntimeBytes = 33554433;
    summary.exporterFailed = true;
    if (scope === "wrong-identity") summary.sessionId = "unqualified-prior-session";
    const result = evaluateMemoryTeardown(input);
    assert.equal(result.eligible, false, scope);
    assert.equal(result.peakGoRuntimeBytes, 33554433, scope);
    assert.equal(result.producerRuntimeBreachRepresentationCount, 1, scope);
    assert.equal(result.auxiliaryRuntimeBreachValueCount, 1, scope);
    assert.equal(result.combinedRetainedRuntimeEventCount, 8, scope);
    assert.equal(result.unqualifiedProducerPeakRepresentationCount, scope === "wrong-identity" ? 1 : 0, scope);
  }
});

test("emergency native receipts preserve one stream and conflicting high representations but never qualify", () => {
  const input = evidence();
  input.fallback = structuredClone(input.teardown);
  input.fallback.filesFlushed = false;
  input.fallback.failureCount = 1;
  let result = evaluateMemoryTeardown(input);
  assert.equal(result.eligible, false);
  assert.equal(result.counts.teardownPrimitiveCount, 4);
  assert.equal(result.nativeConflictRepresentationCount, 0);
  assert.equal(result.producerPeakRepresentationCount, 3);
  input.teardown.native.samples[1].goRuntimeBytes = 33554433;
  result = evaluateMemoryTeardown(input);
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.nativeConflictRepresentationCount, 1);
  assert.equal(result.combinedRetainedRuntimeEventCount, 8);
  input.teardown = null;
  input.fallback.native.samples[1].goRuntimeBytes = 33554433;
  result = evaluateMemoryTeardown(input);
  assert.equal(result.eligible, false);
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.counts.teardownPrimitiveCount, 4);
  input.fallback.sessionId = "stale-session";
  result = evaluateMemoryTeardown(input);
  assert.equal(result.fallbackState, "identity-mismatch");
  assert.equal(result.counts.teardownPrimitiveCount, 0);
});

test("unchanged quiet, missing native join, stale terminal and missing terminal fail", () => {
  for (const mutate of [
    input => { input.teardown = null; },
    input => { input.teardown.native.joinSequence = 0; },
    input => { input.teardown.native.terminalSequence = 2; },
    input => { input.teardown.native.samples.pop(); },
    input => { input.teardown.native.samples[3].stage = "joined"; },
    input => { input.teardown.native.samples[3].sequence = 3; },
    input => { input.teardown.native.samples[3].timeUnixMs = 999; },
  ]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateMemoryTeardown(input).eligible, false);
  }
});

test("withheld-close high observation survives a lower joined and terminal value", () => {
  const input = evidence();
  input.teardown.native.samples[1].goRuntimeBytes = 33554433;
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.classification, "FAILED_MEMORY_LIMIT");
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.goRuntimeBreachSampleCount, 1);
  assert.equal(result.combinedRuntimeReadCount, 8);
});

test("an earlier status overshoot survives a later low status", () => {
  const input = evidence();
  input.statusRuntime[0].goRuntimeBytes = 33554433;
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.classification, "FAILED_MEMORY_LIMIT");
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.goRuntimeBreachSampleCount, 1);
  assert.equal(result.counts.statusRuntimeReadCount, 2);
});

test("existing diagnostic before and after status reads are never filtered out", () => {
  const input = evidence();
  input.statusRuntime[0].readKind = "heap-profile-before";
  input.statusRuntime[0].goRuntimeBytes = 33554433;
  input.statusRuntime[1].readKind = "status";
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.classification, "FAILED_MEMORY_LIMIT");
  assert.equal(result.goRuntimeBreachSampleCount, 1);
  assert.equal(result.counts.statusRuntimeReadCount, 2);
  assert.equal(result.combinedRuntimeReadCount, 8);
});

test("cancel/join samples in one millisecond still require a newer terminal sequence", () => {
  const input = evidence();
  input.teardown.referencesReleasedTimeUnixMs = 1000;
  input.teardown.native.samples.forEach(row => { row.timeUnixMs = 1000; row.elapsedNanos = 0; });
  assert.equal(evaluateMemoryTeardown(input).eligible, true);
});

test("diagnostic and second status snapshots enter the cap without quiet padding", () => {
  for (const scope of ["diagnostic", "memstats", "trim"]) {
    const input = evidence();
    if (scope === "diagnostic") input.diagnostics[1].go_total_bytes = 33554433;
    if (scope === "memstats") input.statusRuntime[0].goMemStatsRuntimeBytes = 33554433;
    if (scope === "trim") Object.assign(input.statusRuntime[1], { idleMemoryTrimCount: 1,
      lastIdleMemoryTrimBeforeBytes: 33554433, lastIdleMemoryTrimAfterBytes: 20000000 });
    const result = evaluateMemoryTeardown(input);
    assert.equal(result.eligible, false, scope);
    assert.equal(result.peakGoRuntimeBytes, 33554433, scope);
    assert.equal(result.combinedRetainedRuntimeEventCount, 8);
  }
});

test("failed live proof and replaced retained prefixes cannot be requalified", () => {
  for (const mutate of [input => { input.liveGate.eligible = false; },
    input => { input.statusContents = "changed" + input.statusContents; },
    input => { input.diagnosticContents = "changed" + input.diagnosticContents; },
    input => { input.liveGate.memoryPrefix.eventCount = 2; },
    input => { input.liveGate.statusPrefix.eventCount = 1; },
    input => { input.liveGate.diagnosticPrefix = { ...memoryEvidencePrefix(input.diagnosticContents.trimEnd()), eventCount: 1 }; }]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateMemoryTeardown(input).eligible, false);
  }
});

test("a null retained row fails without erasing its high neighbor or raw row count", () => {
  for (const group of ["memory", "statusRuntime", "diagnostics"]) {
    const input = evidence();
    input.memory[0].goRuntimeBytes = 33554433;
    input[group].push(null);
    const result = evaluateMemoryTeardown(input);
    assert.equal(result.eligible, false, group);
    assert.equal(result.peakGoRuntimeBytes, 33554433, group);
    assert.equal(result.counts.devicePrimitiveCount, input.memory.length, group);
    assert.equal(result.counts.statusRuntimeReadCount, input.statusRuntime.length, group);
  }
});

test("a missing finish and malformed status object cannot erase a later high", () => {
  const input = evidence();
  input.finish = null;
  input.statusRuntime.unshift({});
  input.statusRuntime.at(-1).goRuntimeBytes = 33554433;
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.eligible, false);
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.counts.statusRuntimeReadCount, 3);
});

test("missing finish IDs and skeletal native owner receipts fail closed", () => {
  const missing = evidence();
  delete missing.expectedFinishCommandId; delete missing.finish.commandId; delete missing.teardown.finishCommandId;
  assert.equal(evaluateMemoryTeardown(missing).eligible, false);
  for (const field of ["ownerId", "nativeInputHash", "nativeBuildOwner", "supervisorPid", "adbPid", "className", "component", "foreground", "serialHash"]) {
    const input = evidence(); delete input.owner[field]; delete input.terminal[field];
    assert.equal(evaluateMemoryTeardown(input).eligible, false, field);
  }
  const stale = evidence(); stale.nativeProof.inputHash = "0".repeat(64);
  assert.equal(evaluateMemoryTeardown(stale).eligible, false);
  for (const field of ["ownerId", "serialHash", "nativeInputHash"]) {
    const input = evidence();
    input.owner[field] = [input.owner[field]];
    input.terminal[field] = input.owner[field];
    if (field === "ownerId" || field === "serialHash") input.ready[field] = input.owner[field];
    if (field === "nativeInputHash") input.nativeProof.inputHash = input.owner[field];
    assert.equal(evaluateMemoryTeardown(input).eligible, false, field);
  }
});

test("a malformed auxiliary neighbor cannot hide a known retained high value", () => {
  for (const mutate of [row => { delete row.idleMemoryTrimCount; }, row => { row.lastIdleMemoryTrimAfterBytes = "missing"; }]) {
    const input = evidence();
    Object.assign(input.statusRuntime[1], { idleMemoryTrimCount: 1, lastIdleMemoryTrimBeforeBytes: 33554433, lastIdleMemoryTrimAfterBytes: 20000000 });
    mutate(input.statusRuntime[1]);
    const result = evaluateMemoryTeardown(input);
    assert.equal(result.eligible, false);
    assert.equal(result.peakGoRuntimeBytes, 33554433);
    assert.equal(result.auxiliaryRuntimeBreachValueCount, 1);
  }
});

test("prior-profile maintenance values are explicitly unqualified and never independent samples", () => {
  const input = evidence();
  for (const row of [...input.memory, ...input.statusRuntime]) Object.assign(row, { idleMemoryTrimCount: 4,
    lastIdleMemoryTrimBeforeBytes: 21000000, lastIdleMemoryTrimAfterBytes: 20000000 });
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.eligible, false);
  assert.equal(result.auxiliaryUnqualifiedMaintenanceValueCount, 6);
  assert.equal(result.auxiliaryValuesAreIndependentSamples, false);
  assert.equal(result.combinedRetainedRuntimeEventCount, 8);
});

test("drainer timeout, aborted observer, unjoined host, lost rows and changed identity fail", () => {
  for (const mutate of [
    input => { input.teardown.deviceDrainerJoined = false; },
    input => { input.teardown.deviceRingDrained = false; },
    input => { input.teardown.referencesReleased = false; },
    input => { input.teardown.native.state = "failed"; },
    input => { input.teardown.native.observerJoined = false; },
    input => { input.teardown.native.dropped = 1; },
    input => { input.terminal.interrupted = true; },
    input => { input.finish.commandId = "stale-finish"; },
    input => { input.finish.teardownObserverId = "stale-observer"; },
    input => { input.teardown.pid++; },
    input => { input.teardown.buildId = "stale-build"; },
    input => { input.memory[0].sessionId = "stale-session"; },
    input => { input.teardown.native.samples[1].stage = "terminal"; },
    input => { input.teardown.native.samples[3].elapsedNanos = 150000000001; },
    input => { input.teardown.native.capacity = 64; },
    input => { input.teardown.native.intervalNanos = 30000000000; },
    input => { input.teardown.native.cancelSequence = "2"; },
    input => { input.teardown.native.joinSequence = "3"; },
    input => { input.statusRuntime.shift(); },
    input => { input.memory[0].samplerDropped = 1; input.memory[0].phase = "finish"; },
    input => { input.diagnostics.splice(0, 1); },
    input => { input.teardown.exporterFailed = true; },
    input => { input.memoryContents += "changed-prefix"; input.memoryContents = "x" + input.memoryContents; },
  ]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateMemoryTeardown(input).eligible, false);
  }
});

test("critical profile integers cannot be coerced or omitted in any observed scope", () => {
  for (const group of ["memory", "statusRuntime", "native"]) {
    for (const value of [undefined, null, "0", false, -1, 65536]) {
      const input = evidence();
      const rows = group === "native" ? input.teardown.native.samples : input[group];
      rows[0].goMemoryProfileRateBytes = value;
      assert.equal(evaluateMemoryTeardown(input).eligible, false, `${group}:${value}`);
    }
  }
});

test("foreign native highs remain unqualified auxiliary values beside a matching receipt", () => {
  for (const foreign of ["primary", "fallback"]) {
    const input = evidence();
    input.fallback = structuredClone(input.teardown);
    const receipt = foreign === "primary" ? input.teardown : input.fallback;
    receipt.sessionId = "foreign-session";
    receipt.native.samples[1].goRuntimeBytes = 33554433;
    delete receipt.producerSummary;
    const result = evaluateMemoryTeardown(input);
    assert.equal(result.eligible, false, foreign);
    assert.equal(result.peakGoRuntimeBytes, 33554433, foreign);
    assert.equal(result.counts.teardownPrimitiveCount, 4, foreign);
    assert.equal(result.nativeConflictRepresentationCount, 1, foreign);
    assert.equal(result.unqualifiedNativeConflictRepresentationCount, 1, foreign);
    assert.equal(result.teardownPeakGoRuntimeBytes, 33554433, foreign);
  }
});

test("foreign repeated and invalid native representations retain a finite deduplicated failed peak", () => {
  const input = evidence();
  input.fallback = structuredClone(input.teardown);
  input.fallback.sessionId = "foreign-session";
  delete input.fallback.producerSummary;
  const high = { ...input.teardown.native.samples[1], goRuntimeBytes: 33554433 };
  input.fallback.native.samples = [high, structuredClone(high), null, {}, { sequence: 99, goRuntimeBytes: "invalid" }];
  const result = evaluateMemoryTeardown(input);
  assert.equal(result.eligible, false);
  assert.equal(result.peakGoRuntimeBytes, 33554433);
  assert.equal(result.teardownPeakGoRuntimeBytes, 33554433);
  assert.equal(result.nativeConflictRepresentationCount, 3);
  assert.equal(result.unqualifiedNativeConflictRepresentationCount, 3);
  assert.equal(result.auxiliaryRuntimeBreachValueCount, 1);
  assert.equal(result.counts.teardownPrimitiveCount, 4);
  assert.equal(result.combinedRetainedRuntimeEventCount, 8);
  assert.ok(result.reasons.includes("native-conflict-runtime-value-invalid"));
});
