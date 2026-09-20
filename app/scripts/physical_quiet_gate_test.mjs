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
      goMemoryLimitBytes: 32 * 1024 * 1024, trackedMemory: { targetBytes: 20 * 1024 * 1024 },
      elapsedMs, connected: role === "client", tunnelStarted: role === "client",
      provideEnabled: role === "provider" },
  });
  return {
    role, phase, underlay: "wifi", start: status(0, "quiet-start"), end: status(durationMs, "quiet-end"),
    memory: Array.from({ length: 21 }, (_, i) => ({ type: "sample", phase,
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

test("five minutes is a fixed floor, not sample count or rounded elapsed time", () => {
  assert.equal(REQUIRED_QUIET_MS, 300_000);
  rejects(evidence("client", 299_999), "quiet-samples-shorter-than-300-seconds");
  assert.equal(evaluateQuietWindow(evidence()).eligible, true);
  assert.throws(() => parseArgs(["--duration-seconds", "240"]), /invalid/);
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
      goMemoryLimitBytes: 40 * 1024 * 1024 }); },
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

test("24MiB is absolute, including an active sample outside the quiet window", () => {
  const input = evidence();
  input.memory[10].goRuntimeBytes = GO_RUNTIME_LIMIT_BYTES;
  assert.equal(evaluateQuietWindow(input).eligible, true);
  input.memory.unshift({ type: "sample", elapsedMs: -1, phase: "traffic",
    goRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1 });
  assert.equal(rejects(input, "go-runtime-above-24-mib").classification, "FAILED_MEMORY_LIMIT");
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

test("CLI rejects missing evidence safely and accepts valid offline evidence without adb", () => {
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
    args.push("--phase", input.phase, "--role", input.role, "--underlay", input.underlay);
    const run = () => spawnSync(process.execPath, [new URL("./physical_quiet_gate.mjs", import.meta.url).pathname, ...args], { encoding: "utf8" });
    let result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(result.stdout).eligible, true);
    const liveGate = join(directory, "live-gate.json");
    writeFileSync(liveGate, result.stdout);
    // Collector has now finished. It must have been live at the original gate;
    // only its retained proof permits a teardown-memory offline recheck.
    writeFileSync(join(directory, "telemetry.json"), readFileSync(join(directory, "telemetry.json"), "utf8") +
      '\n{"type":"summary"}\n');
    result = run();
    assert.equal(result.status, 2);
    assert.ok(JSON.parse(result.stdout).reasons.includes("collector-not-live-at-final-gate"));
    const offline = () => spawnSync(process.execPath, [new URL("./physical_quiet_gate.mjs", import.meta.url).pathname,
      ...args, "--live-gate", liveGate], { encoding: "utf8" });
    result = offline();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(result.stdout).evaluationMode, "offline-teardown");
    const memoryPath = join(directory, "memory.json");
    const originalMemory = readFileSync(memoryPath, "utf8");
    writeFileSync(memoryPath, originalMemory + JSON.stringify({ type: "sample", phase: "finish", elapsedMs: 400_000,
      goMemoryLimitBytes: 32 * 1024 * 1024, goRuntimeBytes: GO_RUNTIME_LIMIT_BYTES + 1 }) + "\n");
    result = offline();
    assert.equal(result.status, 2);
    assert.equal(JSON.parse(result.stdout).classification, "FAILED_MEMORY_LIMIT");
    writeFileSync(memoryPath, originalMemory);
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
