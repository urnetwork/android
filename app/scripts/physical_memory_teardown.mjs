// Existing physical evidence consumers share this narrow, read-only validator.
// Device primitives, process-only teardown, status and diagnostic reads keep
// separate denominators. No device counters are invented for process samples.
import { createHash } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { requireRetainedForeground } from "./physical_collector_session.mjs";
import { GO_MEMORY_LIMIT_BYTES, GO_RUNTIME_LIMIT_BYTES, MEMORY_AUDIT_PROFILE } from "./physical_memory_profile.mjs";

const integer = value => Number.isSafeInteger(value) && value >= 0;
const digest = value => createHash("sha256").update(value).digest("hex");

export function memoryEvidencePrefix(contents) {
  return { bytes: Buffer.byteLength(contents), sha256: digest(contents) };
}

// Does not coerce absent/null/string values into an apparent rate-zero profile.
export function evaluateRuntimeReads(groups, requiredRate = 0) {
  const reasons = new Set();
  const counts = {};
  let peakGoRuntimeBytes = 0;
  let goRuntimeBreachSampleCount = 0;
  for (const [scope, rows] of Object.entries(groups)) {
    counts[scope] = rows.length;
    for (const row of rows) {
      if (!integer(row?.goRuntimeBytes) || row.goRuntimeBytes <= 0) reasons.add("memory-measurement-missing");
      else {
        peakGoRuntimeBytes = Math.max(peakGoRuntimeBytes, row.goRuntimeBytes);
        if (row.goRuntimeBytes > GO_RUNTIME_LIMIT_BYTES) goRuntimeBreachSampleCount++;
      }
      if (row?.goMemoryLimitBytes !== GO_MEMORY_LIMIT_BYTES || row?.memoryProfile !== MEMORY_AUDIT_PROFILE) reasons.add("runtime-profile-mismatch");
      if (row?.goMemoryProfileRateBytes !== requiredRate) reasons.add("runtime-profile-rate-mismatch");
    }
  }
  if (goRuntimeBreachSampleCount) reasons.add("go-runtime-above-32-mib");
  return { counts, combinedRuntimeReadCount: Object.values(counts).reduce((sum, value) => sum + value, 0),
    peakGoRuntimeBytes, goRuntimeBreachSampleCount, reasons: [...reasons] };
}

// The producer already performed these reads; recording them adds no sampling.
export function evaluateStatusRuntimeReads(rows, boundaries) {
  const reasons = new Set();
  const identity = boundaries[0];
  let sequence = 0;
  let previousTime = 0;
  for (const row of rows) {
    if (row?.type !== "status-runtime" || row.sequence !== ++sequence || !integer(row.timeUnixMs) || row.timeUnixMs < previousTime ||
        row.pid !== identity?.pid || row.sessionId !== identity?.sessionId || row.buildId !== identity?.buildId) reasons.add("status-runtime-evidence-invalid");
    previousTime = row?.timeUnixMs;
  }
  if (!rows.length) reasons.add("status-runtime-evidence-missing");
  for (const boundary of boundaries) {
    if (!integer(boundary?.runtimeReadSequence) || boundary.runtimeReadSequence <= 0) {
      reasons.add("status-runtime-boundary-not-retained"); continue;
    }
    const row = rows.find(sample => sample?.sequence === boundary?.runtimeReadSequence);
    if (!row || ["goRuntimeBytes", "goMemoryLimitBytes", "goMemoryProfileRateBytes", "memoryProfile", "pid", "sessionId", "buildId"]
      .some(key => row[key] !== boundary?.[key])) reasons.add("status-runtime-boundary-not-retained");
  }
  return [...reasons];
}

// One runtime read per already-exported atomic diagnostic batch. Reuse the
// existing file, never make a second copy into the status writer.
export function evaluateDiagnosticRuntimeReads(rows, identity) {
  const reasons = new Set();
  const batches = new Map();
  const samples = [];
  for (const row of rows) {
    if (!integer(row?.diagnosticBatchSequence) || row.diagnosticBatchSequence <= 0 || !integer(row.unix_millis) || row.unix_millis <= 0 ||
        ["pid", "sessionId", "buildId"].some(key => row?.[key] !== identity?.[key])) reasons.add("diagnostic-runtime-identity-invalid");
    const sequence = row?.diagnosticBatchSequence;
    if (!batches.has(sequence)) batches.set(sequence, { time: row?.unix_millis, parts: new Set() });
    const batch = batches.get(sequence);
    if (batch.time !== row?.unix_millis) reasons.add("diagnostic-runtime-batch-not-atomic");
    if (["state", "memory", "memory_device_transport", "memory_device_transfer"].includes(row?.part)) {
      if (batch.parts.has(row.part)) reasons.add("diagnostic-runtime-batch-duplicate");
      batch.parts.add(row.part);
    }
    if (row?.part === "memory") {
      if (row.device_memory_target_bytes !== 33554432) reasons.add("diagnostic-runtime-target-mismatch");
      samples.push({ ...row, goRuntimeBytes: row.go_total_bytes, goMemoryLimitBytes: row.go_limit_bytes,
        goMemoryProfileRateBytes: row.memory_profile_rate_bytes, timeUnixMs: row.unix_millis });
    }
  }
  let sequence = 0;
  let previousTime = -1;
  for (const [id, batch] of batches) {
    if (id !== ++sequence || batch.time <= previousTime || batch.parts.size !== 4) reasons.add("diagnostic-runtime-batch-incomplete");
    previousTime = batch.time;
  }
  if (!samples.length || samples.length !== batches.size) reasons.add("diagnostic-runtime-evidence-missing");
  return { samples, batchCount: batches.size, reasons: [...reasons] };
}

// These are retained representations, not invented independent samples.
// A status has two actual runtime snapshots. Repeated last-trim values remain
// auxiliary; a first nonzero maintenance counter has unknown prior provenance.
export function evaluateAuxiliaryRuntimeValues(memory, statusRuntime, ownerCensuses = [], strict = true) {
  const reasons = new Set();
  let peak = 0, breaches = 0, statusSnapshots = 0, maintenanceValues = 0, censusValues = 0, unqualifiedMaintenanceValues = 0;
  const value = (bytes, kind) => {
    if (!integer(bytes) || bytes <= 0) { reasons.add(`${kind}-runtime-value-invalid`); return; }
    peak = Math.max(peak, bytes);
    if (bytes > GO_RUNTIME_LIMIT_BYTES) breaches++;
  };
  for (const row of statusRuntime) {
    if (row?.goMemStatsRuntimeBytes !== undefined || strict) {
      statusSnapshots++;
      value(row?.goMemStatsRuntimeBytes, "status-memstats");
    }
  }
  const maintenance = [...memory, ...statusRuntime].sort((a, b) => (a?.timeUnixMs ?? 0) - (b?.timeUnixMs ?? 0));
  let initialCounter;
  for (const row of maintenance) {
    const count = row?.idleMemoryTrimCount, before = row?.lastIdleMemoryTrimBeforeBytes, after = row?.lastIdleMemoryTrimAfterBytes;
    if (count === undefined && before === undefined && after === undefined && !strict) continue;
    if (!integer(count) || !integer(before) || !integer(after)) reasons.add("maintenance-runtime-value-invalid");
    if (integer(count)) initialCounter ??= count;
    if (count === 0 && (before !== 0 || after !== 0)) reasons.add("maintenance-runtime-counter-invalid");
    for (const bytes of [before, after]) {
      // An invalid counter or neighboring value cannot erase known bytes.
      if (integer(count) && count > 0 || integer(bytes) && bytes > 0) {
        maintenanceValues++;
        value(bytes, "maintenance");
        if (initialCounter > 0 || !integer(count)) unqualifiedMaintenanceValues++;
      }
    }
  }
  if (unqualifiedMaintenanceValues) reasons.add("maintenance-runtime-prior-profile-unqualified");
  for (const census of ownerCensuses) {
    censusValues += 2;
    value(census?.before?.runtime_bytes, "owner-census"); value(census?.after?.runtime_bytes, "owner-census");
  }
  if (breaches) reasons.add("retained-auxiliary-runtime-above-32-mib");
  return { auxiliaryPeakGoRuntimeBytes: peak, auxiliaryRuntimeBreachValueCount: breaches,
    statusMemStatsSnapshotCount: statusSnapshots, auxiliaryMaintenanceValueCount: maintenanceValues,
    auxiliaryCensusValueCount: censusValues, auxiliaryUnqualifiedMaintenanceValueCount: unqualifiedMaintenanceValues,
    auxiliaryValuesAreIndependentSamples: false, reasons: [...reasons] };
}

// Match the existing retained instrumentation schema and its already-verified
// native proof. Offline evaluation does not re-run native tooling or liveness.
export function evaluateJoinedMemoryOwner(owner, ready, terminal, nativeProof, identity) {
  const reasons = new Set();
  const hash = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
  const label = value => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(value);
  const pid = value => integer(value) && value > 0;
  const component = owner?.component;
  if (owner?.schema !== 1 || owner.type !== "instrumentation-session" || owner.state !== "running" ||
      typeof owner.ownerId !== "string" || !/^[a-f0-9-]{36}$/.test(owner.ownerId) || !label(owner.label) || !hash(owner.serialHash) ||
      !pid(owner.supervisorPid) || !pid(owner.adbPid) || owner.supervisorPid === owner.adbPid ||
      !hash(owner.supervisorIdentity) || !hash(owner.adbIdentity) || !hash(owner.nativeInputHash) || !label(owner.nativeBuildOwner) ||
      owner.targetPackage !== "com.bringyour.network" || owner.className !== "com.bringyour.network.acceptance.PhysicalLowbarSessionTest" ||
      !["com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner",
        "com.bringyour.network.test/com.bringyour.network.acceptance.PhysicalCredentialDiagnosticRunner"].includes(component) ||
      !integer(owner.startedHostTimeUnixMs) || owner.foreground?.inputTTY !== true || owner.foreground?.outputTTY !== true) reasons.add("instrumentation-owner-schema-incomplete");
  try { requireRetainedForeground(owner?.foreground ?? {}); } catch { reasons.add("instrumentation-owner-not-retained"); }
  if (nativeProof?.type !== "physical-native-input-verification" || nativeProof.schemaVersion !== 1 || nativeProof.eligible !== true ||
      nativeProof.classification !== "NATIVE_INPUTS_VERIFIED" || nativeProof.buildId !== identity?.buildId ||
      !hash(nativeProof.beforeSha256) || !hash(nativeProof.afterSha256) || !hash(nativeProof.inputHash) ||
      nativeProof.inputHash !== owner?.nativeInputHash || nativeProof.buildOwner !== owner?.nativeBuildOwner) reasons.add("teardown-native-proof-mismatch");
  if (ready?.schema !== 1 || ready.type !== "instrumentation-session-ready" || ready.ownerId !== owner?.ownerId ||
      ready.serialHash !== owner?.serialHash || ready.targetPid !== identity?.pid || !integer(ready.elapsedMs) ||
      !integer(ready.hostTimeUnixMs) || ready.hostTimeUnixMs < owner?.startedHostTimeUnixMs ||
      terminal?.state !== "complete" || terminal.exitCode !== 0 || terminal.signal !== null || terminal.interrupted !== false ||
      !integer(terminal.completedHostTimeUnixMs) || terminal.completedHostTimeUnixMs < ready?.hostTimeUnixMs ||
      Object.entries(owner ?? {}).some(([key, value]) => key !== "state" && JSON.stringify(terminal?.[key]) !== JSON.stringify(value))) reasons.add("instrumentation-join-incomplete");
  return [...reasons];
}

// Emergency receipts never qualify. Select one identity-bound native stream;
// conflicting readable primary rows remain auxiliary, not duplicate events.
export function selectMemoryTeardownReceipt(primary, fallback, finish, identity, finishCommandId) {
  const bound = receipt => receipt?.type === "physical-memory-teardown" && receipt.schemaVersion === 1 &&
    receipt.memoryProfile === MEMORY_AUDIT_PROFILE && typeof identity?.sessionId === "string" && identity.sessionId &&
    typeof identity?.buildId === "string" && identity.buildId && integer(identity?.pid) && identity.pid > 0 &&
    ["sessionId", "buildId", "pid"].every(key => receipt[key] === identity[key] && finish?.[key] === identity[key]) &&
    typeof finishCommandId === "string" && finishCommandId && receipt.finishCommandId === finishCommandId && finish?.commandId === finishCommandId &&
    typeof receipt.observerId === "string" && receipt.observerId && receipt.observerId === finish?.teardownObserverId &&
    receipt.native?.observerId === receipt.observerId;
  if (!fallback) return { teardown: primary, reasons: [], conflictingPrimaryRows: [], unqualifiedNativeConflictRepresentationCount: 0, fallbackState: "absent" };
  const matchingFallback = bound(fallback);
  const selected = matchingFallback ? fallback : primary;
  const other = matchingFallback ? primary : fallback;
  // Match the Go receipt decoder's invalid-row view for equality only. Raw
  // artifacts and selected primary event counts retain their original slots.
  const objectRow = row => row && typeof row === "object" && !Array.isArray(row) ? row : {};
  const selectedRows = Array.isArray(selected?.native?.samples) ? selected.native.samples.map(objectRow) : [];
  const otherRows = Array.isArray(other?.native?.samples) ? other.native.samples.map(objectRow) : [];
  const conflictingPrimaryRows = [];
  for (const row of otherRows) {
    if (![...selectedRows, ...conflictingPrimaryRows].some(retained => isDeepStrictEqual(row, retained))) {
      conflictingPrimaryRows.push(row);
    }
  }
  return { teardown: selected, conflictingPrimaryRows,
    unqualifiedNativeConflictRepresentationCount: bound(other) ? 0 : conflictingPrimaryRows.length,
    fallbackState: matchingFallback ? "selected-ineligible" : "identity-mismatch",
    reasons: [matchingFallback ? "native-teardown-fallback-ineligible" : "native-teardown-fallback-identity-mismatch",
      ...(conflictingPrimaryRows.length ? ["native-teardown-conflicting-representations"] : [])] };
}

// Producer maxima can be the only surviving values after append/flush failure.
// These are explicitly duplicated representations, never primary/quiet events.
export function evaluateProducerMemorySummaries(summaries, identity, finishCommandId, observerId, counts) {
  const reasons = new Set();
  let peak = 0, representationCount = 0, breaches = 0, unqualified = 0;
  const reportedCounts = [];
  const countFields = ["devicePrimitiveCount", "teardownPrimitiveCount", "statusRuntimeReadCount", "diagnosticRuntimeReadCount"];
  for (const summary of summaries) {
    if (summary == null) { reasons.add("producer-memory-summary-missing"); continue; }
    representationCount++;
    if (summary.type === "malformed-retained-evidence") reasons.add("producer-memory-summary-malformed");
    if (!integer(summary.peakGoRuntimeBytes) || summary.peakGoRuntimeBytes <= 0) reasons.add("producer-memory-peak-invalid");
    else {
      peak = Math.max(peak, summary.peakGoRuntimeBytes);
      if (summary.peakGoRuntimeBytes > GO_RUNTIME_LIMIT_BYTES) breaches++;
    }
    const qualified = summary.type === "physical-memory-producer-summary" && summary.schemaVersion === 1 &&
      summary.memoryProfile === MEMORY_AUDIT_PROFILE && typeof identity?.sessionId === "string" && identity.sessionId &&
      typeof identity?.buildId === "string" && identity.buildId && integer(identity?.pid) && identity.pid > 0 &&
      ["pid", "sessionId", "buildId"].every(key => summary[key] === identity[key]) &&
      typeof finishCommandId === "string" && finishCommandId && summary.finishCommandId === finishCommandId &&
      typeof observerId === "string" && observerId && summary.observerId === observerId;
    if (!qualified) { unqualified++; reasons.add("producer-memory-summary-unqualified"); }
    const reported = Object.fromEntries(countFields.map(key => [key, summary[key]]));
    for (const key of ["statusMemStatsSnapshotCount", "auxiliaryMaintenanceValueCount", "auxiliaryRuntimeBreachValueCount"]) {
      reported[key] = summary[key];
      if (!integer(summary[key])) reasons.add("producer-memory-auxiliary-count-invalid");
    }
    reported.combinedRetainedRuntimeEventCount = summary.combinedRetainedRuntimeEventCount;
    reportedCounts.push(reported);
    if (countFields.some(key => summary[key] !== counts[key]) ||
        summary.combinedRetainedRuntimeEventCount !== Object.values(counts).reduce((sum, count) => sum + count, 0)) reasons.add("producer-memory-count-mismatch");
    if (summary.exporterFailed !== false || summary.failureCount !== 0) reasons.add("producer-memory-export-incomplete");
  }
  if (breaches) reasons.add("producer-memory-runtime-above-32-mib");
  return { producerPeakGoRuntimeBytes: peak, producerPeakRepresentationCount: representationCount,
    producerRuntimeBreachRepresentationCount: breaches, unqualifiedProducerPeakRepresentationCount: unqualified,
    producerReportedCounts: reportedCounts, reasons: [...reasons] };
}

// A normal host AM join, copied native lifecycle channels and a fresh terminal
// read are three distinct requirements. An old quiet file proves none of them.
export function evaluateMemoryTeardown({ teardown, fallback, producerSummary, memory, statusRuntime, diagnostics = [], finish, owner, ready, terminal, nativeProof,
  liveGate, memoryContents, statusContents, diagnosticContents, expectedFinishCommandId, requiredRate = 0 }) {
  const reasons = new Set();
  const fail = reason => reasons.add(reason);
  const producerSummaries = [teardown?.producerSummary, ...(fallback ? [fallback.producerSummary] : []), producerSummary];
  const selection = selectMemoryTeardownReceipt(teardown, fallback, finish,
    { sessionId: liveGate?.sessionId, buildId: nativeProof?.buildId, pid: ready?.targetPid }, expectedFinishCommandId);
  teardown = selection.teardown;
  selection.reasons.forEach(fail);
  const native = teardown?.native;
  const nativeSamples = Array.isArray(native?.samples) ? native.samples : [];
  const processSamples = nativeSamples.map(row => ({ ...row, memoryProfile: teardown?.memoryProfile }));
  const diagnostic = evaluateDiagnosticRuntimeReads(diagnostics, teardown);
  diagnostic.reasons.forEach(fail);
  const runtime = evaluateRuntimeReads({ devicePrimitiveCount: memory, teardownPrimitiveCount: processSamples,
    statusRuntimeReadCount: statusRuntime, diagnosticRuntimeReadCount: diagnostic.samples }, requiredRate);
  const auxiliary = evaluateAuxiliaryRuntimeValues(memory, statusRuntime);
  const producer = evaluateProducerMemorySummaries(producerSummaries,
    { sessionId: liveGate?.sessionId, buildId: nativeProof?.buildId, pid: ready?.targetPid }, expectedFinishCommandId, finish?.teardownObserverId, runtime.counts);
  producer.reasons.forEach(fail);
  auxiliary.auxiliaryPeakGoRuntimeBytes = Math.max(auxiliary.auxiliaryPeakGoRuntimeBytes, producer.producerPeakGoRuntimeBytes);
  auxiliary.auxiliaryRuntimeBreachValueCount += producer.producerRuntimeBreachRepresentationCount;
  auxiliary.nativeConflictRepresentationCount = selection.conflictingPrimaryRows.length;
  auxiliary.unqualifiedNativeConflictRepresentationCount = selection.unqualifiedNativeConflictRepresentationCount;
  for (const row of selection.conflictingPrimaryRows) {
    if (!integer(row?.goRuntimeBytes) || row.goRuntimeBytes <= 0) {
      fail("native-conflict-runtime-value-invalid"); continue;
    }
    auxiliary.auxiliaryPeakGoRuntimeBytes = Math.max(auxiliary.auxiliaryPeakGoRuntimeBytes, row.goRuntimeBytes);
    if (row.goRuntimeBytes > GO_RUNTIME_LIMIT_BYTES) auxiliary.auxiliaryRuntimeBreachValueCount++;
  }
  auxiliary.reasons.forEach(fail);
  runtime.reasons.forEach(fail);
  evaluateStatusRuntimeReads(statusRuntime, [finish]).forEach(fail);
  const observerId = native?.observerId;
  if (teardown?.type !== "physical-memory-teardown" || teardown.schemaVersion !== 1 || teardown.memoryProfile !== MEMORY_AUDIT_PROFILE ||
      typeof teardown.sessionId !== "string" || !teardown.sessionId || typeof teardown.buildId !== "string" || !teardown.buildId || !integer(teardown.pid) || teardown.pid <= 0 ||
      finish?.type !== "status" || finish.state !== "complete" || finish.phase !== "finish" ||
      typeof expectedFinishCommandId !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(expectedFinishCommandId) ||
      finish.commandId !== expectedFinishCommandId || teardown.finishCommandId !== expectedFinishCommandId ||
      finish.pid !== teardown.pid || finish.sessionId !== teardown.sessionId || finish.buildId !== teardown.buildId ||
      typeof observerId !== "string" || !observerId || finish.teardownObserverId !== observerId || teardown.observerId !== observerId) fail("teardown-identity-mismatch");
  if (["deviceDrainerJoined", "deviceRingDrained", "deviceJoined", "referencesReleased", "filesFlushed"]
    .some(key => teardown?.[key] !== true) || teardown?.failureCount !== 0) fail("teardown-adapter-incomplete");
  if (teardown?.statusRuntimeReadCount !== statusRuntime.length) fail("status-runtime-count-mismatch");
  if (teardown?.devicePrimitiveCount !== memory.length || teardown?.diagnosticBatchesProduced !== diagnostic.batchCount ||
      teardown?.diagnosticBatchesFlushed !== diagnostic.batchCount || teardown?.exporterFailed !== false ||
      requiredRate === 0 && teardown?.diagnosticCommandCount !== 0) fail("retained-runtime-stream-incomplete-or-diagnostic");
  if (memory.some(row => row?.type !== "sample" || row.samplerSchema !== 13 || row.samplerDropped !== 0)) fail("device-primitive-evidence-invalid");
  if (memory.some(row => ["pid", "sessionId", "buildId"].some(key => row?.[key] !== teardown?.[key]))) fail("device-memory-identity-mismatch");
  if (native?.type !== "device-memory-teardown" || native.schemaVersion !== 1 || native.state !== "complete" || native.failure !== "" ||
      native.deviceTargetBytes !== 33554432 || native.observerJoined !== true || native.dropped !== 0 ||
      native.produced !== nativeSamples.length || native.drained !== nativeSamples.length || nativeSamples.length < 4 ||
      native.capacity !== 16 || native.capacity < nativeSamples.length || native.intervalNanos !== 15000000000 ||
      ![native.cancelSequence, native.joinSequence, native.terminalSequence].every(integer) ||
      !(native.cancelSequence > 1 && native.joinSequence > native.cancelSequence && native.terminalSequence > native.joinSequence &&
        native.terminalSequence === native.produced)) fail("native-teardown-incomplete");
  let previousElapsed = -1;
  let previousUnix = -1;
  for (let i = 0; i < nativeSamples.length; i++) {
    const row = nativeSamples[i];
    if (row?.sequence !== i + 1 || !integer(row.elapsedNanos) || row.elapsedNanos < previousElapsed || !integer(row.timeUnixMs) || row.timeUnixMs <= 0 ||
        row.timeUnixMs < previousUnix || (i > 0 && row.elapsedNanos - previousElapsed > 30000000000)) fail("native-teardown-sequence-invalid");
    previousElapsed = row?.elapsedNanos;
    previousUnix = row?.timeUnixMs;
    const expectedStage = i === 0 ? "begin" : i + 1 === native.cancelSequence ? "cancelled" :
      i + 1 === native.joinSequence ? "joined" : i + 1 === native.terminalSequence ? "terminal" : "periodic";
    if (row?.stage !== expectedStage || row?.elapsedNanos > 150000000000) fail("native-teardown-lifecycle-order-invalid");
  }
  for (const [sequence, stage] of [[1, "begin"], [native?.cancelSequence, "cancelled"], [native?.joinSequence, "joined"], [native?.terminalSequence, "terminal"]]) {
    if (nativeSamples[sequence - 1]?.stage !== stage) fail("native-teardown-boundary-missing");
  }
  if (!integer(teardown?.referencesReleasedTimeUnixMs) || nativeSamples.at(-1)?.timeUnixMs < teardown.referencesReleasedTimeUnixMs ||
      nativeSamples[0]?.timeUnixMs < finish?.teardownBeginTimeUnixMs ||
      !integer(finish?.teardownBeginTimeUnixMs)) fail("native-terminal-not-fresh");
  evaluateJoinedMemoryOwner(owner, ready, terminal, nativeProof, teardown).forEach(fail);
  const prefix = liveGate?.memoryPrefix;
  const bytes = Buffer.from(memoryContents ?? "", "utf8");
  if (liveGate?.schemaVersion !== 4 || liveGate.eligible !== true || liveGate.evaluationMode !== "live" || liveGate.sessionId !== teardown?.sessionId ||
      liveGate.buildId !== teardown?.buildId || liveGate.pid !== teardown?.pid ||
      !integer(prefix?.bytes) || prefix.bytes <= 0 || prefix.bytes > bytes.length || digest(bytes.subarray(0, prefix.bytes)) !== prefix.sha256) fail("live-memory-prefix-mismatch");
  for (const [name, contents, count] of [["memory", memoryContents, memory.length], ["status", statusContents, statusRuntime.length], ["diagnostic", diagnosticContents, diagnostic.batchCount]]) {
    const proof = liveGate?.[`${name}Prefix`];
    const final = Buffer.from(contents ?? "", "utf8");
    if (!integer(proof?.bytes) || proof.bytes <= 0 || proof.bytes > final.length || digest(final.subarray(0, proof.bytes)) !== proof.sha256 ||
        !integer(proof?.eventCount) || proof.eventCount <= 0 || proof.eventCount > count) fail(`live-${name}-prefix-mismatch`);
    else {
      try {
        const prefix = final.subarray(0, proof.bytes).toString("utf8");
        if (!prefix.endsWith("\n")) throw new Error("incomplete-prefix");
        const rows = prefix.trim().split("\n").map(line => JSON.parse(line));
        const events = name === "memory" ? rows.length : name === "status" ? rows.filter(row => row?.type === "status-runtime").length : rows.filter(row => row?.part === "memory").length;
        if (events !== proof.eventCount || name === "status" && events !== rows.length) fail(`live-${name}-prefix-mismatch`);
      } catch { fail(`live-${name}-prefix-mismatch`); }
    }
  }
  return { type: "physical-memory-teardown-gate", schemaVersion: 1, eligible: reasons.size === 0,
    classification: reasons.has("go-runtime-above-32-mib") || auxiliary.auxiliaryRuntimeBreachValueCount || producer.producerRuntimeBreachRepresentationCount ? "FAILED_MEMORY_LIMIT" : reasons.size ? "INCOMPLETE_TEARDOWN" : "TEARDOWN_COMPLETE",
    sampleScope: "retained-runtime-events-not-all-internal-reads-or-continuous-peak", ...runtime, ...auxiliary, ...producer,
    peakGoRuntimeBytes: Math.max(runtime.peakGoRuntimeBytes, auxiliary.auxiliaryPeakGoRuntimeBytes),
    combinedRetainedRuntimeEventCount: runtime.combinedRuntimeReadCount,
    fallbackState: selection.fallbackState,
    teardownPeakGoRuntimeBytes: [...processSamples, ...selection.conflictingPrimaryRows].reduce((peak, row) => Number.isSafeInteger(row?.goRuntimeBytes) ? Math.max(peak, row.goRuntimeBytes) : peak, 0),
    reasons: [...reasons] };
}
