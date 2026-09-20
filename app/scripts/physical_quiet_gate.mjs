#!/usr/bin/env node

// Host-only evidence gate. Never starts traffic, changes a device, or finishes
// instrumentation. Status capture is a read-only adb call; all files are private.
import { spawnSync } from "node:child_process";
import { readFileSync, realpathSync, writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { evaluateEligibility } from "./physical_lowbar_capture.mjs";
import { evaluateMemoryProfile, GO_MEMORY_LIMIT_BYTES } from "./physical_memory_profile.mjs";
import { evaluateWorkloadCoverage, requireLiveCollector } from "./physical_workload_receipt.mjs";

export const REQUIRED_QUIET_MS = 300_000;
export const GO_RUNTIME_LIMIT_BYTES = 24 * 1024 * 1024;
const MAX_MEMORY_GAP_MS = 30_000;
const MAX_TELEMETRY_GAP_MS = 5_000;
const ROLES = ["direct", "client", "provider"];

function validTime(value) {
  return Number.isFinite(value) && value >= 0;
}

function roleMatches(status, role) {
  if (role === "client") {
    return status.connected === true && status.tunnelStarted === true;
  }
  return status.connected === false && status.tunnelStarted === false &&
    status.provideEnabled === (role === "provider");
}

// Boundary envelopes come from --capture-status, not host/device clock
// subtraction. Device elapsed time brackets memory; host time brackets dumpsys.
export function evaluateQuietWindow({ start, end, memory, telemetry, phase, role, underlay }) {
  const reasons = new Set();
  const fail = (reason) => reasons.add(reason);
  if (!/^quiet-[A-Za-z0-9._-]+$/.test(phase ?? "")) fail("explicit-quiet-phase-required");
  if (!ROLES.includes(role)) fail("explicit-role-required");
  if (!["wifi", "cellular"].includes(underlay)) fail("explicit-underlay-required");
  for (const boundary of [start, end]) {
    const status = boundary?.status;
    if (!status || !validTime(boundary.hostTimeUnixMs) || !validTime(status.elapsedMs) ||
        status.type !== "status" || status.state !== "complete" || status.phase !== phase ||
        !Number.isInteger(status.pid) || status.pid <= 0 || !status.commandId) {
      fail("completed-quiet-boundaries-required");
    }
    if (!status || !roleMatches(status, role)) fail("quiet-role-not-preserved");
    if (!evaluateMemoryProfile(status).eligible) fail("ios-memory-audit-profile-mismatch");
  }
  const firstStatus = start?.status;
  const lastStatus = end?.status;
  if (firstStatus?.pid !== lastStatus?.pid) fail("quiet-process-changed");
  if (firstStatus?.commandId === lastStatus?.commandId) fail("fresh-end-status-required");
  const boundaryDurationMs = (lastStatus?.elapsedMs ?? NaN) - (firstStatus?.elapsedMs ?? NaN);
  const hostDurationMs = (end?.hostTimeUnixMs ?? NaN) - (start?.hostTimeUnixMs ?? NaN);
  if (!(boundaryDurationMs >= REQUIRED_QUIET_MS) || !(hostDurationMs >= REQUIRED_QUIET_MS)) {
    fail("quiet-boundaries-shorter-than-300-seconds");
  }

  const allSamples = memory.filter((record) => record.type === "sample");
  let peakGoRuntimeBytes = 0;
  for (const record of allSamples) {
    if (record.goMemoryLimitBytes !== GO_MEMORY_LIMIT_BYTES) fail("sampler-go-memory-limit-not-32-mib");
    if (!Number.isFinite(record.goRuntimeBytes) || record.goRuntimeBytes <= 0) {
      fail("memory-measurement-missing");
    } else {
      peakGoRuntimeBytes = Math.max(peakGoRuntimeBytes, record.goRuntimeBytes);
    }
  }
  if (peakGoRuntimeBytes > GO_RUNTIME_LIMIT_BYTES) fail("go-runtime-above-24-mib");
  const workloads = start?.workloads;
  let workloadCoverage = { eligible: false, sampleCount: 0 };
  if (!workloads?.ownerId || workloads.ownerId !== end?.workloads?.ownerId ||
      workloads.label !== phase?.slice("quiet-".length) || !validTime(workloads.startedHostTimeUnixMs) ||
      !validTime(workloads.completedHostTimeUnixMs) || workloads.completedHostTimeUnixMs < workloads.startedHostTimeUnixMs ||
      workloads.completedHostTimeUnixMs > start?.hostTimeUnixMs || !workloads.collector?.pid) {
    fail("workload-collector-evidence-required");
  } else {
    workloadCoverage = evaluateWorkloadCoverage(telemetry, workloads.startedHostTimeUnixMs, end?.hostTimeUnixMs, workloads.label);
    if (!workloadCoverage.eligible) fail("workload-collector-coverage-incomplete");
  }
  const inWindow = memory.filter((record) => validTime(record.elapsedMs) &&
    firstStatus?.elapsedMs <= record.elapsedMs && record.elapsedMs <= lastStatus?.elapsedMs);
  if (inWindow.some((record) => record.type !== "sample")) fail("quiet-sampler-error");
  const samples = inWindow.filter((record) => record.type === "sample");
  // Keep attribution separate from the absolute whole-run gate above. Offline
  // teardown re-evaluates the same boundaries; it is not a second quiet arm.
  const quietPeakGoRuntimeBytes = samples.reduce((peak, record) =>
    Number.isFinite(record.goRuntimeBytes) ? Math.max(peak, record.goRuntimeBytes) : peak, 0);
  const goRuntimeBreachSampleCount = allSamples.filter((record) =>
    record.goRuntimeBytes > GO_RUNTIME_LIMIT_BYTES).length;
  const quietGoRuntimeBreachSampleCount = samples.filter((record) =>
    record.goRuntimeBytes > GO_RUNTIME_LIMIT_BYTES).length;
  if (samples.some((record) => record.phase !== phase)) fail("quiet-phase-interrupted");
  if (samples.some((record) => record.samplerDropped !== 0)) fail("quiet-samples-dropped-or-unknown");
  const sampleDurationMs = samples.length > 1
    ? samples.at(-1).elapsedMs - samples[0].elapsedMs : 0;
  if (samples.length < 21 || sampleDurationMs < REQUIRED_QUIET_MS) {
    fail("quiet-samples-shorter-than-300-seconds");
  }
  for (let i = 1; i < samples.length; i += 1) {
    const gap = samples[i].elapsedMs - samples[i - 1].elapsedMs;
    if (!(gap > 0 && gap <= MAX_MEMORY_GAP_MS)) fail("quiet-memory-timestamps-or-gap-invalid");
  }
  // phase is assigned while the primitive ring is drained: older records can
  // inherit the new label. The explicit status time excludes these old samples.
  if (!samples.length || samples[0].elapsedMs - firstStatus?.elapsedMs > MAX_MEMORY_GAP_MS ||
      lastStatus?.elapsedMs - samples.at(-1)?.elapsedMs > MAX_MEMORY_GAP_MS) {
    fail("quiet-memory-does-not-cover-boundaries");
  }

  const hostSamples = telemetry.filter((record) => record.type === "sample");
  // Include the samples on either side, so an absent collector tail cannot
  // masquerade as continuous connected coverage.
  const before = hostSamples.findLastIndex((record) => record.startTimeUnixMs <= start?.hostTimeUnixMs);
  const after = hostSamples.findIndex((record) => record.endTimeUnixMs >= end?.hostTimeUnixMs);
  const covered = before >= 0 && after >= before ? hostSamples.slice(before, after + 1) : [];
  if (!covered.length) fail("quiet-telemetry-does-not-cover-boundaries");
  const requirements = {
    requireWifi: underlay === "wifi",
    requireCellular: underlay === "cellular",
    requireVpn: role === "client",
    requireNoVpn: role !== "client",
  };
  for (let i = 0; i < covered.length; i += 1) {
    const record = covered[i];
    if (record.eligibility?.eligible !== true || !evaluateEligibility(record, requirements).eligible) {
      fail("quiet-network-ineligible");
    }
    if (!validTime(record.startTimeUnixMs) || !validTime(record.endTimeUnixMs) ||
        record.endTimeUnixMs < record.startTimeUnixMs ||
        record.endTimeUnixMs - record.startTimeUnixMs > MAX_TELEMETRY_GAP_MS ||
        (i > 0 && (!(record.startTimeUnixMs > covered[i - 1].startTimeUnixMs) ||
          record.startTimeUnixMs - covered[i - 1].endTimeUnixMs > MAX_TELEMETRY_GAP_MS))) {
      fail("quiet-telemetry-timestamps-or-gap-invalid");
    }
  }
  return {
    type: "quiet-window-gate",
    schemaVersion: 2,
    eligible: reasons.size === 0,
    classification: reasons.has("go-runtime-above-24-mib") ? "FAILED_MEMORY_LIMIT" :
      reasons.has("ios-memory-audit-profile-mismatch") || reasons.has("sampler-go-memory-limit-not-32-mib") ?
        "INVALID_MEMORY_PROFILE" :
      reasons.has("workload-collector-evidence-required") || reasons.has("workload-collector-coverage-incomplete") ?
        "INCOMPLETE_ACTIVE_COVERAGE" :
      reasons.size ? "INCOMPLETE_QUIET_WINDOW" : "QUIET_WINDOW_COMPLETE",
    scope: "workload-telemetry-and-quiet-window",
    role: ROLES.includes(role) ? role : "unknown",
    connectedClientEvidence: reasons.size === 0 && role === "client",
    requiredDurationMs: REQUIRED_QUIET_MS,
    boundaryDurationMs: Number.isFinite(boundaryDurationMs) ? boundaryDurationMs : null,
    sampleDurationMs,
    sampleCount: samples.length,
    telemetrySampleCount: covered.length,
    workloadTelemetrySampleCount: workloadCoverage.sampleCount,
    peakGoRuntimeBytes,
    quietPeakGoRuntimeBytes,
    goRuntimeBreachSampleCount,
    quietGoRuntimeBreachSampleCount,
    goRuntimeLimitBytes: GO_RUNTIME_LIMIT_BYTES,
    reasons: [...reasons],
  };
}

function readNdjson(path, liveAppend = false) {
  let contents = readFileSync(path, "utf8");
  if (liveAppend) contents = contents.slice(0, contents.lastIndexOf("\n") + 1);
  return contents.split(/\r?\n/).filter((line) => line.trim()).map(JSON.parse);
}

export function parseArgs(argv) {
  const options = {};
  const names = new Set(["capture-status", "serial", "start", "end", "memory", "telemetry", "phase", "role", "underlay", "live-gate"]);
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.replace(/^--/, "");
    if (!argv[i]?.startsWith("--") || !names.has(name) || !argv[i + 1] || argv[i + 1].startsWith("--")) {
      throw new Error("invalid quiet-gate arguments");
    }
    if (options[name] !== undefined) throw new Error("duplicate quiet-gate argument");
    options[name] = argv[i + 1];
  }
  const required = options["capture-status"] ? ["capture-status", "serial"] :
    ["start", "end", "memory", "telemetry", "phase", "role", "underlay"];
  const allowed = options["capture-status"] ? required : [...required, "live-gate"];
  if (required.some((name) => !options[name]) || Object.keys(options).some((name) => !allowed.includes(name))) {
    throw new Error("missing or incompatible quiet-gate arguments");
  }
  return options;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options["capture-status"]) {
    const result = spawnSync("adb", ["-s", options.serial, "shell", "run-as",
      "com.bringyour.network", "cat", "files/acceptance/physical-status"],
    { encoding: "utf8", timeout: 15_000, maxBuffer: 1024 * 1024 });
    if (result.status !== 0) throw new Error("quiet status capture failed");
    const record = { hostTimeUnixMs: Date.now(), status: JSON.parse(result.stdout) };
    writeFileSync(options["capture-status"], `${JSON.stringify(record)}\n`, { mode: 0o600, flag: "wx" });
    return;
  }
  const start = JSON.parse(readFileSync(options.start, "utf8"));
  const end = JSON.parse(readFileSync(options.end, "utf8"));
  const result = evaluateQuietWindow({
    start,
    end,
    memory: readNdjson(options.memory),
    telemetry: readNdjson(options.telemetry, true),
    phase: options.phase, role: options.role, underlay: options.underlay,
  });
  result.workloadOwnerId = start.workloads?.ownerId ?? null;
  result.hostTimeUnixMs = Date.now();
  result.evaluationMode = options["live-gate"] ? "offline-teardown" : "live";
  result.collectorLiveAtGate = false;
  // Normal completion must gate while its retained collector is still alive,
  // before setting the stop marker. Pure evaluation remains usable offline.
  try {
    const workloads = start.workloads;
    if (realpathSync(options.telemetry) !== realpathSync(workloads.collector.path)) throw new Error();
    if (options["live-gate"]) {
      const proof = JSON.parse(readFileSync(options["live-gate"], "utf8"));
      if (proof.type !== "quiet-window-gate" || proof.schemaVersion !== 2 || proof.evaluationMode !== "live" ||
          proof.collectorLiveAtGate !== true || proof.workloadOwnerId !== workloads.ownerId ||
          !validTime(proof.hostTimeUnixMs) || proof.hostTimeUnixMs < end.hostTimeUnixMs) throw new Error();
      result.liveGateHostTimeUnixMs = proof.hostTimeUnixMs;
    } else {
      requireLiveCollector(workloads.collector, workloads.label, workloads.startedHostTimeUnixMs);
    }
    result.collectorLiveAtGate = true;
  } catch {
    result.eligible = false;
    result.connectedClientEvidence = false;
    result.reasons.push("collector-not-live-at-final-gate");
    if (!["FAILED_MEMORY_LIMIT", "INVALID_MEMORY_PROFILE"].includes(result.classification)) {
      result.classification = "INCOMPLETE_ACTIVE_COVERAGE";
    }
  }
  process.stdout.write(`${JSON.stringify(result)}\n`);
  if (!result.eligible) process.exitCode = 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main();
  } catch {
    // Do not echo private filenames, serials, raw JSON or adb diagnostics.
    process.stderr.write("quiet-gate evidence unavailable, malformed, or arguments invalid\n");
    process.exitCode = 2;
  }
}
