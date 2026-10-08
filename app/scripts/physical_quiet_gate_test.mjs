import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { evaluateQuietWindow, GO_RUNTIME_LIMIT_BYTES, parseArgs, REQUIRED_QUIET_MS } from "./physical_quiet_gate.mjs";

function evidence(role = "client", durationMs = REQUIRED_QUIET_MS) {
  const base = 1_000_000;
  const phase = "quiet-post-traffic";
  const workloads = { ownerId: "owner-01", label: "post-traffic", startedHostTimeUnixMs: base,
    completedHostTimeUnixMs: base, failedChildCount: 0, collector: { pid: process.pid, path: "fixture-telemetry" } };
  const status = (elapsedMs, commandId) => ({
    hostTimeUnixMs: base + elapsedMs,
    workloads,
    status: { type: "status", state: "complete", phase, pid: 42, commandId,
      sessionId: "fixture-session", buildId: "fixture-build", goRuntimeBytes: 20 * 1024 * 1024,
      goMemStatsRuntimeBytes: 20 * 1024 * 1024, idleMemoryTrimCount: 0,
      lastIdleMemoryTrimBeforeBytes: 0, lastIdleMemoryTrimAfterBytes: 0,
      runtimeReadSequence: commandId === "quiet-start" ? 1 : 2, timeUnixMs: base + elapsedMs,
      memoryProfile: "ios-memory-audit-v2",
      goMemoryProfileRateBytes: 0,
      goMemoryLimitBytes: 32 * 1024 * 1024, trackedMemory: { targetBytes: 32 * 1024 * 1024 },
      elapsedMs, connected: role === "client", tunnelStarted: role !== "direct",
      provideEnabled: role === "provider" },
  });
  return {
    role, phase, underlay: "wifi", start: status(0, "quiet-start"), end: status(durationMs, "quiet-end"),
    memory: Array.from({ length: 21 }, (_, i) => ({ type: "sample", phase,
      pid: 42, sessionId: "fixture-session", buildId: "fixture-build",
      samplerSchema: 13, timeUnixMs: base + durationMs * i / 20,
      idleMemoryTrimCount: 0, lastIdleMemoryTrimBeforeBytes: 0, lastIdleMemoryTrimAfterBytes: 0,
      memoryProfile: "ios-memory-audit-v2",
      goMemoryProfileRateBytes: 0,
      goMemoryLimitBytes: 32 * 1024 * 1024,
      elapsedMs: durationMs * i / 20, samplerDropped: 0, goRuntimeBytes: 20 * 1024 * 1024 })),
    telemetry: [{ type: "environment", label: "post-traffic" }, ...Array.from({ length: Math.ceil(durationMs / 1000) + 1 }, (_, i) => ({
      type: "sample", startTimeUnixMs: base + i * 1000, endTimeUnixMs: base + i * 1000 + 100,
      network: { activeNetwork: { transports: [role === "client" ? "VPN" : "WIFI"] },
        underlayNetworks: [{ transports: ["WIFI"] }] },
      eligibility: { eligible: true, reasons: [] }, telemetryErrors: [],
    }))],
  };
}

function rejects(input, reason) {
  const result = evaluateQuietWindow(input);
  assert.equal(result.eligible, false);
  assert.ok(result.reasons.includes(reason), JSON.stringify(result));
  return result;
}

test("32 MiB is the exact iOS absolute cap in every phase, independently of steady percentiles", () => {
  assert.equal(GO_RUNTIME_LIMIT_BYTES, 33_554_432);
  for (const phase of ["baseline", "active", "drain", "transition", "quiet", "finish"]) {
    const input = evidence();
    const row = phase === "quiet" ? input.memory[10] :
      { ...input.memory[0], phase, elapsedMs: phase === "finish" ? REQUIRED_QUIET_MS + 1 : -1 };
    if (phase !== "quiet") input.memory.push(row);
    row.goRuntimeBytes = 33_554_432;
    const exact = evaluateQuietWindow(input);
    assert.equal(exact.eligible, true, `${phase}: ${JSON.stringify(exact)}`);
    assert.equal(exact.goRuntimeLimitBytes, 33_554_432);
    row.goRuntimeBytes += 1;
    const breached = rejects(input, "go-runtime-above-32-mib");
    assert.equal(breached.classification, "FAILED_MEMORY_LIMIT");
    assert.equal(breached.goRuntimeBreachSampleCount, 1);
    assert.equal(breached.quietGoRuntimeBreachSampleCount, phase === "quiet" ? 1 : 0);
  }
});

test("explicit selected iOS profile is required at boundaries and every primitive sample", () => {
  for (const memoryProfile of [undefined, null, "android", "private-canary"]) {
    for (const phase of ["start", "end", "baseline", "active", "drain", "transition", "quiet", "finish"]) {
      const input = evidence();
      if (phase === "start" || phase === "end") input[phase].status.memoryProfile = memoryProfile;
      else if (phase === "quiet") input.memory[10].memoryProfile = memoryProfile;
      else input.memory.push({ ...input.memory[0], phase, memoryProfile,
        elapsedMs: phase === "finish" ? REQUIRED_QUIET_MS + 1 : -1 });
      const result = evaluateQuietWindow(input);
      assert.equal(result.classification, "INVALID_MEMORY_PROFILE", `${phase}: ${JSON.stringify(result)}`);
      assert.equal(result.eligible, false);
      assert.equal(JSON.stringify(result).includes("private-canary"), false);
    }
  }
});

test("five minutes is a fixed floor, not sample count or rounded elapsed time", () => {
  assert.equal(REQUIRED_QUIET_MS, 300_000);
  rejects(evidence("client", 299_999), "quiet-samples-shorter-than-300-seconds");
  assert.equal(evaluateQuietWindow(evidence()).eligible, true);
  assert.throws(() => parseArgs(["--duration-seconds", "240"]), /invalid/);
});

test("paZ8U8: profiling overhead cannot pass as release memory, even with valid iOS budgets", () => {
  const input = evidence();
  for (const boundary of [input.start, input.end]) boundary.status.goMemoryProfileRateBytes = 65_536;
  for (const sample of input.memory) sample.goMemoryProfileRateBytes = 65_536;
  const result = rejects(input, "ios-memory-profile-rate-not-zero");
  assert.equal(result.classification, "INVALID_RATE_ZERO");
  assert.ok(result.reasons.includes("sampler-memory-profile-rate-not-zero"));
  // Bucket accounting is explanatory only. Never subtract it from the gate.
  input.memory[10].goRuntimeBytes = 25_442_584;
  input.memory[10].goProfilingBucketBytes = 1_850_363;
  assert.equal(evaluateQuietWindow(input).classification, "INVALID_RATE_ZERO",
    "the historical 24-MiB breach is below today's cap but remains a diagnostic-profile rejection");
  input.memory[10].goRuntimeBytes = 33_554_433;
  const breached = rejects(input, "go-runtime-above-32-mib");
  assert.equal(breached.classification, "FAILED_MEMORY_LIMIT");
  assert.equal(breached.goRuntimeBreachSampleCount, 1);
  assert.ok(breached.reasons.includes("sampler-memory-profile-rate-not-zero"));
});

test("rate zero must be proved at both boundaries and every active, quiet, and teardown sample", () => {
  for (const rate of [65_536, undefined, null, "0", false, -1]) {
    for (const phase of ["start", "end", "traffic", "quiet", "finish"]) {
      const input = evidence();
      if (["start", "end"].includes(phase)) {
        input[phase].status.goMemoryProfileRateBytes = rate;
      } else if (phase === "quiet") {
        input.memory[10].goMemoryProfileRateBytes = rate;
      } else {
        input.memory.push({ ...input.memory[0], phase, elapsedMs: phase === "traffic" ? -1 : REQUIRED_QUIET_MS + 1,
          goMemoryProfileRateBytes: rate });
      }
      const result = evaluateQuietWindow(input);
      assert.equal(result.classification, "INVALID_RATE_ZERO", `${phase}, ${rate}`);
      assert.equal(result.eligible, false);
    }
  }
});

test("lY1fH2 regression: correct quiet duration/network never qualifies the larger Android profile", () => {
  const input = evidence();
  for (const boundary of [input.start, input.end]) {
    boundary.status.trackedMemory.targetBytes = 28 * 1024 * 1024;
    boundary.status.goMemoryLimitBytes = 40 * 1024 * 1024;
  }
  input.memory.forEach((sample) => { sample.goMemoryLimitBytes = 40 * 1024 * 1024; });
  const result = rejects(input, "ios-memory-audit-profile-mismatch");
  assert.equal(result.classification, "INVALID_MEMORY_PROFILE");
  input.memory[10].goRuntimeBytes = 26_492_960;
  assert.equal(evaluateQuietWindow(input).classification, "INVALID_MEMORY_PROFILE");
  input.memory[10].goRuntimeBytes = 33_554_433;
  assert.equal(rejects(input, "ios-memory-audit-profile-mismatch").classification, "FAILED_MEMORY_LIMIT",
    "profile invalidation must not hide the real absolute memory violation");
});

test("profile must match both boundaries and every primitive sample, including active phases", () => {
  for (const mutate of [
    (input) => { delete input.start.status.trackedMemory; },
    (input) => { input.end.status.goMemoryLimitBytes = 40 * 1024 * 1024; },
    (input) => { input.memory[10].goMemoryLimitBytes = 40 * 1024 * 1024; },
    (input) => { delete input.memory[10].goMemoryLimitBytes; },
    (input) => { input.memory.unshift({ type: "sample", elapsedMs: -1, phase: "traffic", goRuntimeBytes: 20 * 1024 * 1024,
      goMemoryLimitBytes: 40 * 1024 * 1024, goMemoryProfileRateBytes: 0 }); },
  ]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateQuietWindow(input).classification, "INVALID_MEMORY_PROFILE");
  }
});

test("root regression: a 254692ms session with 17 samples and no quiet phase cannot qualify", () => {
  const input = evidence("direct", 254_692);
  input.start.status.phase = "ready";
  input.end.status.phase = "finish";
  input.memory = Array.from({ length: 17 }, (_, i) => ({
    type: "sample", phase: "ready", elapsedMs: i * 15_000,
    samplerDropped: 0, goRuntimeBytes: 17_293_328,
  }));
  const result = rejects(input, "completed-quiet-boundaries-required");
  assert.ok(result.reasons.includes("quiet-phase-interrupted"));
  assert.ok(result.reasons.includes("quiet-samples-shorter-than-300-seconds"));
  assert.equal(result.connectedClientEvidence, false);
});

test("a long total session without an explicit uninterrupted quiet phase is insufficient", () => {
  const input = evidence();
  input.memory.forEach((record) => { record.phase = "traffic"; });
  rejects(input, "quiet-phase-interrupted");
  input.memory = [];
  rejects(input, "quiet-samples-shorter-than-300-seconds");
  rejects({ ...evidence(), phase: "ready" }, "explicit-quiet-phase-required");
});

test("quiet boundaries cannot substitute for 300 seconds of primitive samples", () => {
  const input = evidence();
  input.memory.pop();
  rejects(input, "quiet-samples-shorter-than-300-seconds");
  input.memory = [evidence().memory[0], evidence().memory.at(-1)];
  rejects(input, "quiet-memory-timestamps-or-gap-invalid");
});

test("delayed ring-drain labels cannot count pre-phase active samples", () => {
  const input = evidence();
  input.start.status.elapsedMs = 15_000;
  input.start.hostTimeUnixMs += 15_000;
  // The first record has the new label but was sampled before the boundary.
  const result = rejects(input, "quiet-samples-shorter-than-300-seconds");
  assert.equal(result.sampleCount, 20);
  assert.equal(result.sampleDurationMs, 285_000);
});

test("missing, stale, disconnected or different-process status fails", () => {
  for (const mutate of [
    (input) => { input.end = undefined; },
    (input) => { input.end.status.commandId = input.start.status.commandId; },
    (input) => { input.end.status.connected = false; },
    (input) => { input.end.status.pid += 1; },
  ]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateQuietWindow(input).eligible, false);
  }
});

test("Direct and provider quiet gates never claim connected client qualification", () => {
  for (const role of ["direct", "provider"]) {
    const result = evaluateQuietWindow(evidence(role));
    assert.equal(result.eligible, true, JSON.stringify(result));
    assert.equal(result.connectedClientEvidence, false);
  }
  const client = evaluateQuietWindow(evidence());
  assert.equal(client.connectedClientEvidence, true);
  const input = evidence("direct"); input.role = "client";
  rejects(input, "quiet-role-not-preserved");
});

test("provider quiet preserves its running service without claiming a client VPN", () => {
  assert.equal(evaluateQuietWindow(evidence("provider")).eligible, true);
  for (const role of ["client", "provider", "direct"]) {
    const input = evidence(role);
    if (role === "client") input.end.status.provideEnabled = true;
    else if (role === "provider") input.end.status.tunnelStarted = false;
    else input.end.status.tunnelStarted = true;
    rejects(input, "quiet-role-not-preserved");
  }
  const provider = evidence("provider");
  provider.telemetry[150].network.activeNetwork.transports = ["VPN"];
  rejects(provider, "quiet-network-ineligible");
});

test("disconnection or underlay change anywhere in the quiet telemetry fails", () => {
  for (const mutate of [
    (sample) => { sample.network.activeNetwork.transports = ["WIFI"]; },
    (sample) => { sample.network.underlayNetworks = [{ transports: ["CELLULAR"] }]; },
    (sample) => { sample.eligibility.eligible = false; },
    (sample) => { sample.telemetryErrors = ["network-unavailable"]; },
  ]) {
    const input = evidence(); mutate(input.telemetry[150]);
    rejects(input, "quiet-network-ineligible");
  }
});

test("collector start, tail or gap loss fails instead of reusing active traffic coverage", () => {
  for (const mutate of [
    (input) => { input.telemetry.shift(); },
    (input) => { input.telemetry.pop(); },
    (input) => { input.telemetry.splice(100, 10); },
  ]) {
    const input = evidence(); mutate(input);
    assert.equal(evaluateQuietWindow(input).eligible, false);
  }
});

test("gy8htp: late collector is rejected despite a complete 300-second quiet window", () => {
  const input = evidence();
  input.start.workloads.startedHostTimeUnixMs -= 120_000;
  const result = rejects(input, "workload-collector-coverage-incomplete");
  assert.equal(result.classification, "INCOMPLETE_ACTIVE_COVERAGE");
  assert.equal(result.telemetrySampleCount, 301, "quiet coverage alone was valid");
});

test("a telemetry hole during active traffic cannot be hidden by perfect quiet telemetry", () => {
  const input = evidence();
  input.start.workloads.startedHostTimeUnixMs -= 20_000;
  input.telemetry.splice(1, 0, { ...input.telemetry[1],
    startTimeUnixMs: input.start.hostTimeUnixMs - 20_000, endTimeUnixMs: input.start.hostTimeUnixMs - 19_000 });
  rejects(input, "workload-collector-coverage-incomplete");
});

test("dropped, duplicate, erroneous and interrupted primitive samples fail", () => {
  for (const mutate of [
    (sample) => { sample.samplerDropped = 1; },
    (sample) => { delete sample.samplerDropped; },
    (sample) => { sample.elapsedMs = 0; },
    (sample) => { sample.type = "sample-error"; },
    (sample) => { sample.phase = "connect-h1"; },
  ]) {
    const input = evidence(); mutate(input.memory[10]);
    assert.equal(evaluateQuietWindow(input).eligible, false);
  }
});

test("32MiB is absolute, including an active sample outside the quiet window", () => {
  const input = evidence();
  input.memory[10].goRuntimeBytes = GO_RUNTIME_LIMIT_BYTES;
  assert.equal(evaluateQuietWindow(input).eligible, true);
  input.memory.unshift({ type: "sample", elapsedMs: -1, phase: "traffic",
    goRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1 });
  assert.equal(rejects(input, "go-runtime-above-32-mib").classification, "FAILED_MEMORY_LIMIT");
});

test("historical burst values remain visible; a new global breach is not a quiet-window breach", () => {
  const input = evidence();
  input.memory.forEach((sample) => { sample.goRuntimeBytes = 22_904_864; });
  input.memory[1].goRuntimeBytes = 24_723_488;
  for (const [index, bytes] of [25_509_920, 26_050_592, 26_353_696].entries()) {
    input.memory.unshift({ ...input.memory[0], type: "sample", elapsedMs: -15_000 * (index + 1), phase: "traffic",
      memoryProfile: "ios-memory-audit-v2",
      goMemoryLimitBytes: 32 * 1024 * 1024, goMemoryProfileRateBytes: 0, goRuntimeBytes: bytes });
  }
  // These former 24-MiB test values are below the newly authorized cap.
  // This fixture includes current profile proof; it does not requalify old artifacts.
  const historical = evaluateQuietWindow(input);
  assert.equal(historical.eligible, true);
  assert.equal(historical.peakGoRuntimeBytes, 26_353_696);
  assert.equal(historical.goRuntimeBreachSampleCount, 0);
  input.memory.unshift({ ...input.memory[0], elapsedMs: -60_000, goRuntimeBytes: 33_554_433 });
  const first = evaluateQuietWindow(input);
  assert.equal(first.classification, "FAILED_MEMORY_LIMIT");
  assert.equal(first.eligible, false);
  assert.deepEqual(first.reasons, ["go-runtime-above-32-mib"]);
  assert.equal(first.peakGoRuntimeBytes, 33_554_433);
  assert.equal(first.quietPeakGoRuntimeBytes, 24_723_488);
  assert.equal(first.goRuntimeBreachSampleCount, 1);
  assert.equal(first.quietGoRuntimeBreachSampleCount, 0);
  // A later offline/teardown evaluation remains the same interval and global
  // failure, even if all appended samples have settled below the cap.
  input.memory.push({ ...input.memory.at(-1), elapsedMs: REQUIRED_QUIET_MS + 15_000, goRuntimeBytes: 23_019_552 });
  const later = evaluateQuietWindow(input);
  for (const key of ["classification", "eligible", "sampleCount", "sampleDurationMs", "peakGoRuntimeBytes",
    "quietPeakGoRuntimeBytes", "goRuntimeBreachSampleCount", "quietGoRuntimeBreachSampleCount"]) {
    assert.equal(later[key], first[key]);
  }
  input.memory[10].goRuntimeBytes = GO_RUNTIME_LIMIT_BYTES + 1;
  assert.equal(evaluateQuietWindow(input).quietGoRuntimeBreachSampleCount, 1);
});

test("status capture is read-only, private, fresh and never overwrites prior evidence", () => {
  const directory = mkdtempSync(join(tmpdir(), "physical-quiet-capture-test-"));
  try {
    const output = join(directory, "boundary.json");
    const log = join(directory, "arguments.json");
    const status = evidence().start.status;
    writeFileSync(join(directory, "adb"), `#!${process.execPath}\n` +
      `const fs=require('node:fs');fs.writeFileSync(${JSON.stringify(log)},JSON.stringify(process.argv.slice(2)));` +
      `process.stdout.write(${JSON.stringify(JSON.stringify(status))});\n`, { mode: 0o700 });
    const run = () => spawnSync(process.execPath, [new URL("./physical_quiet_gate.mjs", import.meta.url).pathname,
      "--capture-status", output, "--serial", "fake-device"], {
      encoding: "utf8", env: { ...process.env, PATH: directory },
    });
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(readFileSync(log, "utf8")), ["-s", "fake-device", "shell", "run-as",
      "com.bringyour.network", "cat", "files/acceptance/physical-status"]);
    assert.deepEqual(JSON.parse(readFileSync(output, "utf8")).status, status);
    assert.equal(statSync(output).mode & 0o777, 0o600);
    assert.equal(run().status, 2);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("CLI requires fresh native teardown in addition to valid live quiet evidence without adb", () => {
  const directory = mkdtempSync(join(tmpdir(), "physical-quiet-gate-test-"));
  try {
    const input = evidence();
    // A fake retained collector is this test process; no adb or live device.
    const shift = Date.now() - input.end.hostTimeUnixMs - 1000;
    for (const b of [input.start, input.end]) b.hostTimeUnixMs += shift;
    input.start.workloads.startedHostTimeUnixMs += shift;
    input.start.workloads.completedHostTimeUnixMs += shift;
    for (const record of input.telemetry.filter((r) => r.type === "sample")) {
      record.startTimeUnixMs += shift; record.endTimeUnixMs += shift;
    }
    input.start.workloads.collector.path = join(directory, "telemetry.json");
    const args = [];
    for (const name of ["start", "end", "memory", "telemetry"]) {
      const file = join(directory, `${name}.json`);
      const value = input[name];
      writeFileSync(file, Array.isArray(value) ? value.map(JSON.stringify).join("\n") + "\n" : JSON.stringify(value));
      args.push(`--${name}`, file);
    }
    const statusRuntimePath = join(directory, "status-runtime.ndjson");
    writeFileSync(statusRuntimePath, [input.start.status, input.end.status].map(row => JSON.stringify({ ...row,
      type: "status-runtime", sequence: row.runtimeReadSequence })).join("\n") + "\n");
    const diagnosticsPath = join(directory, "diagnostics.ndjson");
    writeFileSync(diagnosticsPath, ["state", "memory", "memory_device_transport", "memory_device_transfer"].map(part => JSON.stringify({
      ...input.start.status, part, diagnosticBatchSequence: 1, unix_millis: input.start.status.timeUnixMs,
      go_total_bytes: 20 * 1024 * 1024, go_limit_bytes: 33554432, memory_profile_rate_bytes: 0,
      device_memory_target_bytes: 33554432 })).join("\n") + "\n");
    args.push("--status-runtime", statusRuntimePath, "--diagnostics", diagnosticsPath,
      "--phase", input.phase, "--role", input.role, "--underlay", input.underlay);
    const run = () => spawnSync(process.execPath, [new URL("./physical_quiet_gate.mjs", import.meta.url).pathname, ...args], { encoding: "utf8" });
    let result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(result.stdout).eligible, true);
    const liveGate = join(directory, "live-gate.json");
    writeFileSync(liveGate, result.stdout);
    // Collector has now finished. Its old proof still needs a fresh native
    // lifecycle receipt and the host's normal instrumentation join.
    writeFileSync(join(directory, "telemetry.json"), readFileSync(join(directory, "telemetry.json"), "utf8") +
      '\n{"type":"summary"}\n');
    result = run();
    assert.equal(result.status, 2);
    assert.ok(JSON.parse(result.stdout).reasons.includes("collector-not-live-at-final-gate"));
    const teardownArgs = [];
    const offline = () => spawnSync(process.execPath, [new URL("./physical_quiet_gate.mjs", import.meta.url).pathname,
      ...args, "--live-gate", liveGate, ...teardownArgs], { encoding: "utf8" });
    result = offline();
    assert.equal(result.status, 2, "unchanged quiet file cannot prove native teardown");
    assert.equal(result.stdout, "");
    assert.match(result.stderr, /quiet-gate evidence unavailable/);
    const beginTime = input.end.status.timeUnixMs + 1;
    const identity = { pid: 42, sessionId: "fixture-session", buildId: "fixture-build" };
    const native = { type: "device-memory-teardown", schemaVersion: 1, observerId: "fixture-observer", deviceTargetBytes: 33554432,
      state: "complete", failure: "", intervalNanos: 15000000000, capacity: 16, produced: 4, drained: 4, dropped: 0,
      cancelSequence: 2, joinSequence: 3, terminalSequence: 4, observerJoined: true,
      samples: ["begin", "cancelled", "joined", "terminal"].map((stage, index) => ({ sequence: index + 1, stage,
        timeUnixMs: beginTime + index, elapsedNanos: index, goRuntimeBytes: 20 * 1024 * 1024,
        goMemoryLimitBytes: 33554432, goMemoryProfileRateBytes: 0 })) };
    const finish = { ...input.end.status, phase: "finish", commandId: "fixture-finish", timeUnixMs: beginTime,
      runtimeReadSequence: 3, teardownObserverId: native.observerId, teardownBeginTimeUnixMs: beginTime };
    writeFileSync(statusRuntimePath, readFileSync(statusRuntimePath, "utf8") +
      JSON.stringify({ ...finish, type: "status-runtime", sequence: 3 }) + "\n");
    const owner = { schema: 1, type: "instrumentation-session", state: "running", ownerId: "00000000-0000-0000-0000-000000000001",
      startedHostTimeUnixMs: input.start.hostTimeUnixMs, label: "fixture-label", serialHash: "b".repeat(64),
      nativeInputHash: "a".repeat(64), nativeBuildOwner: "fixture-native-owner", supervisorPid: 100, adbPid: 101,
      supervisorIdentity: "c".repeat(64), adbIdentity: "d".repeat(64),
      foreground: { inputTTY: true, outputTTY: true, processGroup: 100, foregroundGroup: 100 },
      targetPackage: "com.bringyour.network", className: "com.bringyour.network.acceptance.PhysicalLowbarSessionTest",
      component: "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner" };
    const ownerPath = join(directory, "owner.json");
    writeFileSync(ownerPath, JSON.stringify(owner));
    writeFileSync(`${ownerPath}.ready.json`, JSON.stringify({ schema: 1, type: "instrumentation-session-ready",
      ownerId: owner.ownerId, serialHash: owner.serialHash, targetPid: 42, elapsedMs: 0, hostTimeUnixMs: input.start.hostTimeUnixMs }));
    writeFileSync(`${ownerPath}.terminal.json`, JSON.stringify({ ...owner, state: "complete", exitCode: 0,
      signal: null, interrupted: false, completedHostTimeUnixMs: Date.now() }));
    const teardownPath = join(directory, "teardown.json");
    const producerSummary = { ...identity, type: "physical-memory-producer-summary", schemaVersion: 1, memoryProfile: "ios-memory-audit-v2",
      finishCommandId: finish.commandId, observerId: native.observerId, peakGoRuntimeBytes: 20 * 1024 * 1024,
      devicePrimitiveCount: input.memory.length, teardownPrimitiveCount: 4, statusRuntimeReadCount: 3, diagnosticRuntimeReadCount: 1,
      combinedRetainedRuntimeEventCount: input.memory.length + 8, statusMemStatsSnapshotCount: 3,
      auxiliaryMaintenanceValueCount: 0, auxiliaryRuntimeBreachValueCount: 0, exporterFailed: false, failureCount: 0 };
    const producerSummaryPath = join(directory, "producer-summary.json");
    writeFileSync(producerSummaryPath, JSON.stringify(producerSummary));
    writeFileSync(teardownPath, JSON.stringify({ ...identity, type: "physical-memory-teardown", schemaVersion: 1,
      memoryProfile: "ios-memory-audit-v2", native, observerId: native.observerId, finishCommandId: finish.commandId,
      deviceDrainerJoined: true, deviceRingDrained: true, deviceJoined: true, referencesReleased: true,
      referencesReleasedTimeUnixMs: beginTime + 2, filesFlushed: true, statusRuntimeReadCount: 3, failureCount: 0,
      devicePrimitiveCount: input.memory.length, diagnosticBatchesProduced: 1, diagnosticBatchesFlushed: 1,
      exporterFailed: false, diagnosticCommandCount: 0, producerSummary }));
    const finishPath = join(directory, "finish.json");
    writeFileSync(finishPath, JSON.stringify(finish));
    const nativeProofPath = join(directory, "native-proof.json");
    writeFileSync(nativeProofPath, JSON.stringify({ type: "physical-native-input-verification", schemaVersion: 1, eligible: true,
      classification: "NATIVE_INPUTS_VERIFIED", buildId: identity.buildId, buildOwner: owner.nativeBuildOwner,
      inputHash: owner.nativeInputHash, beforeSha256: "e".repeat(64), afterSha256: "f".repeat(64) }));
    teardownArgs.push("--teardown", teardownPath, "--finish-status", finishPath,
      "--instrumentation-owner", ownerPath, "--finish-command-id", finish.commandId, "--native-inputs", nativeProofPath,
      "--producer-summary", producerSummaryPath);
    result = offline();
    assert.equal(result.status, 0, result.stderr || result.stdout);
    const completeTeardown = JSON.parse(result.stdout);
    assert.equal(completeTeardown.teardown.eligible, true);
    assert.equal(completeTeardown.combinedRuntimeReadCount, input.memory.length + 3 + 1 + 4);
    assert.equal(completeTeardown.combinedRetainedRuntimeEventCount, input.memory.length + 3 + 1 + 4);
    assert.equal(completeTeardown.teardownPrimitiveCount, 4);
    assert.equal(completeTeardown.memoryPrefix.eventCount, input.memory.length);
    assert.equal(completeTeardown.teardown.producerPeakRepresentationCount, 2);
    const originalTeardown = readFileSync(teardownPath, "utf8");
    const fallbackPath = join(directory, "teardown-fallback.json");
    const fallback = JSON.parse(originalTeardown);
    fallback.filesFlushed = false; fallback.failureCount = 1;
    fallback.native.samples[1].goRuntimeBytes = GO_RUNTIME_LIMIT_BYTES + 1;
    writeFileSync(fallbackPath, JSON.stringify(fallback));
    writeFileSync(teardownPath, "incomplete primary write");
    teardownArgs.push("--teardown-fallback", fallbackPath);
    result = offline();
    assert.equal(result.status, 2);
    assert.equal(JSON.parse(result.stdout).peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 1);
    assert.equal(JSON.parse(result.stdout).teardown.counts.teardownPrimitiveCount, 4);
    assert.equal(JSON.parse(result.stdout).teardown.fallbackState, "selected-ineligible");
    teardownArgs.splice(-2);
    writeFileSync(teardownPath, originalTeardown);
    writeFileSync(producerSummaryPath, JSON.stringify({ ...producerSummary, peakGoRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1, exporterFailed: true }));
    result = offline();
    assert.equal(result.status, 2);
    assert.equal(JSON.parse(result.stdout).peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 1);
    assert.equal(JSON.parse(result.stdout).teardown.producerRuntimeBreachRepresentationCount, 1);
    const nativeWithProducerHigh = JSON.parse(originalTeardown);
    nativeWithProducerHigh.producerSummary.peakGoRuntimeBytes = GO_RUNTIME_LIMIT_BYTES + 3;
    writeFileSync(teardownPath, JSON.stringify(nativeWithProducerHigh));
    writeFileSync(producerSummaryPath, '{"private-malformed-summary":');
    result = offline();
    assert.equal(result.status, 2);
    const malformedProducer = JSON.parse(result.stdout);
    assert.equal(malformedProducer.teardown.producerPeakRepresentationCount, 2,
      "nonempty invalid summary is retained as one invalid representation beside the native summary");
    assert.equal(malformedProducer.teardown.unqualifiedProducerPeakRepresentationCount, 1);
    assert.equal(malformedProducer.peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 3);
    assert.ok(malformedProducer.teardown.reasons.includes("producer-memory-summary-malformed"));
    assert.equal(result.stdout.includes("private-malformed-summary"), false);
    writeFileSync(teardownPath, originalTeardown);
    writeFileSync(producerSummaryPath, JSON.stringify(producerSummary));
    const currentProof = JSON.parse(readFileSync(liveGate));
    for (const mutation of [{ schemaVersion: 2 }, { goRuntimeLimitBytes: 25_165_824 }, { eligible: false }]) {
      writeFileSync(liveGate, JSON.stringify({ ...currentProof, ...mutation }));
      assert.equal(offline().status, 2, "old or mismatched gate proof must not be requalified");
    }
    writeFileSync(liveGate, JSON.stringify(currentProof));
    const memoryPath = join(directory, "memory.json");
    const originalMemory = readFileSync(memoryPath, "utf8");
    writeFileSync(memoryPath, originalMemory + JSON.stringify({ type: "sample", phase: "finish", elapsedMs: 400_000,
      goMemoryLimitBytes: 32 * 1024 * 1024, goRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1 }) + "\n");
    result = offline();
    assert.equal(result.status, 2);
    assert.equal(JSON.parse(result.stdout).classification, "FAILED_MEMORY_LIMIT");
    writeFileSync(memoryPath, readFileSync(memoryPath, "utf8") + "null\n");
    const originalStatusRuntime = readFileSync(statusRuntimePath, "utf8");
    writeFileSync(statusRuntimePath, originalStatusRuntime + "null\n");
    result = offline();
    assert.equal(result.status, 2);
    const malformed = JSON.parse(result.stdout);
    assert.equal(malformed.peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 1);
    assert.equal(malformed.combinedRetainedRuntimeEventCount, input.memory.length + 2 + 4 + 1 + 4);
    assert.equal(malformed.teardown.counts.devicePrimitiveCount, input.memory.length + 2);
    writeFileSync(statusRuntimePath, originalStatusRuntime);
    writeFileSync(memoryPath, originalMemory);
    // A partial exporter append is still one retained invalid row. Parsing it
    // must not erase a readable later high or the producer's known peak.
    writeFileSync(memoryPath, originalMemory + '{"private-truncated-value":\n' +
      JSON.stringify({ ...input.memory.at(-1), phase: "finish", elapsedMs: 400_000,
        goRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1 }) + "\n");
    result = offline();
    assert.equal(result.status, 2);
    const truncatedWithHigh = JSON.parse(result.stdout);
    assert.equal(truncatedWithHigh.classification, "FAILED_MEMORY_LIMIT");
    assert.equal(truncatedWithHigh.peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 1);
    assert.equal(truncatedWithHigh.teardown.counts.devicePrimitiveCount, input.memory.length + 2);
    assert.equal(truncatedWithHigh.combinedRetainedRuntimeEventCount, input.memory.length + 10);
    assert.equal(result.stdout.includes("private-truncated-value"), false);
    writeFileSync(memoryPath, originalMemory + '{"private-truncated-tail":');
    writeFileSync(producerSummaryPath, JSON.stringify({ ...producerSummary,
      peakGoRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 2, exporterFailed: true, failureCount: 1 }));
    result = offline();
    assert.equal(result.status, 2);
    const truncatedWithProducer = JSON.parse(result.stdout);
    assert.equal(truncatedWithProducer.classification, "FAILED_MEMORY_LIMIT");
    assert.equal(truncatedWithProducer.peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 2);
    assert.equal(truncatedWithProducer.teardown.producerRuntimeBreachRepresentationCount, 1);
    assert.equal(truncatedWithProducer.teardown.counts.devicePrimitiveCount, input.memory.length + 1);
    assert.equal(truncatedWithProducer.combinedRetainedRuntimeEventCount, input.memory.length + 9);
    assert.equal(result.stdout.includes("private-truncated-tail"), false);
    // Failed acquisition leaves no primitive file at all, but other retained
    // native/summary evidence must still publish a failed, non-quiet aggregate.
    const memoryArgument = args.indexOf("--memory") + 1;
    args[memoryArgument] = join(directory, "missing-memory.ndjson");
    result = offline();
    assert.equal(result.status, 2);
    const missingWithProducer = JSON.parse(result.stdout);
    assert.equal(missingWithProducer.classification, "FAILED_MEMORY_LIMIT");
    assert.equal(missingWithProducer.peakGoRuntimeBytes, GO_RUNTIME_LIMIT_BYTES + 2);
    assert.equal(missingWithProducer.teardown.counts.devicePrimitiveCount, 0);
    assert.equal(missingWithProducer.combinedRetainedRuntimeEventCount, 8);
    assert.ok(missingWithProducer.reasons.includes("retained-runtime-evidence-unavailable"));
    assert.equal(missingWithProducer.sampleCount, 0, "failed acquisition must not manufacture quiet samples");
    args[memoryArgument] = memoryPath;
    writeFileSync(memoryPath, originalMemory);
    writeFileSync(producerSummaryPath, JSON.stringify(producerSummary));
    const proof = JSON.parse(readFileSync(liveGate)); proof.workloadOwnerId = "different-owner";
    writeFileSync(liveGate, JSON.stringify(proof));
    assert.equal(offline().status, 2);
    writeFileSync(join(directory, "memory.json"), "private-identifier-not-json");
    result = run();
    assert.equal(result.status, 2);
    assert.equal(result.stdout.includes("private-identifier"), false);
    assert.equal(result.stderr.includes("private-identifier"), false);
    assert.equal(result.stderr.includes(directory), false);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
