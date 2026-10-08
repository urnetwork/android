import assert from "node:assert/strict";
import test from "node:test";
import { evaluateQuietWindow } from "./physical_quiet_gate.mjs";

// Existing pure entry point: these changes run against old production code.
function evidence() {
  const base = 1_000_000;
  const phase = "quiet-post-traffic";
  const workloads = { ownerId: "fixture-owner", label: "post-traffic", startedHostTimeUnixMs: base,
    completedHostTimeUnixMs: base, failedChildCount: 0, collector: { pid: 42, path: "fixture" } };
  const status = (elapsedMs, commandId) => ({ hostTimeUnixMs: base + elapsedMs, workloads,
    status: { type: "status", state: "complete", phase, pid: 42, commandId, elapsedMs,
      memoryProfile: "ios-memory-audit-v2", goMemoryLimitBytes: 33554432, goMemoryProfileRateBytes: 0,
      trackedMemory: { targetBytes: 33554432 }, goRuntimeBytes: 33554433,
      connected: true, tunnelStarted: true, provideEnabled: false } });
  return { role: "client", phase, underlay: "wifi", start: status(0, "start"), end: status(300000, "end"),
    memory: Array.from({ length: 21 }, (_, i) => ({ type: "sample", phase, elapsedMs: i * 15000,
      memoryProfile: "ios-memory-audit-v2", goRuntimeBytes: 20000000, goMemoryLimitBytes: 33554432,
      goMemoryProfileRateBytes: 0, samplerDropped: 0 })),
    telemetry: [{ type: "environment", label: "post-traffic" }, ...Array.from({ length: 301 }, (_, i) => ({
      type: "sample", startTimeUnixMs: base + i * 1000, endTimeUnixMs: base + i * 1000 + 100,
      network: { activeNetwork: { transports: ["VPN"] }, underlayNetworks: [{ transports: ["WIFI"] }] },
      eligibility: { eligible: true, reasons: [] }, telemetryErrors: [] }))] };
}

test("retained status runtime overshoot cannot disappear behind valid quiet samples", () => {
  const result = evaluateQuietWindow(evidence());
  assert.equal(result.eligible, false, "existing boundary runtime read was excluded");
  assert.equal(result.classification, "FAILED_MEMORY_LIMIT");
});

test("retained diagnostic runtime overshoot cannot disappear behind valid quiet samples", () => {
  const input = evidence();
  input.start.status.goRuntimeBytes = input.end.status.goRuntimeBytes = 20000000;
  input.diagnostics = ["state", "memory", "memory_device_transport", "memory_device_transfer"].map(part => ({
    part, diagnosticBatchSequence: 1, unix_millis: 1, pid: 42, memoryProfile: "ios-memory-audit-v2",
    go_total_bytes: 33554433, go_limit_bytes: 33554432, memory_profile_rate_bytes: 0, device_memory_target_bytes: 33554432,
  }));
  const result = evaluateQuietWindow(input);
  assert.equal(result.eligible, false, "existing diagnostic runtime read was excluded");
  assert.equal(result.classification, "FAILED_MEMORY_LIMIT");
});

test("dropped active or final primitives cannot hide behind an intact quiet window", () => {
  for (const phase of ["active", "finish"]) {
    const input = evidence();
    input.start.status.goRuntimeBytes = input.end.status.goRuntimeBytes = 20000000;
    input.memory.push({ ...input.memory[0], elapsedMs: 400000, phase, samplerDropped: 1 });
    const result = evaluateQuietWindow(input);
    assert.equal(result.eligible, false, phase);
  }
});
