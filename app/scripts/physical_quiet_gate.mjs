#!/usr/bin/env node

// Host-only evidence gate. Never starts traffic, changes a device, or finishes
// instrumentation. Status capture is a read-only adb call; all files are private.
import { spawnSync } from "node:child_process";
import { evaluateMemoryTeardown, evaluateStatusRuntimeReads, evaluateDiagnosticRuntimeReads,
  evaluateAuxiliaryRuntimeValues, memoryEvidencePrefix } from "./physical_memory_teardown.mjs";
import { readFileSync, realpathSync, writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { evaluateEligibility } from "./physical_lowbar_capture.mjs";
import { evaluateMemoryProfile, matchesMemoryProfileProof, memoryAuditPolicy, memoryProfileRequirements,
  MEMORY_AUDIT_PROFILE, QUALIFICATION_PROFILE_RATE_BYTES } from "./physical_memory_profile.mjs";
import { evaluateWorkloadCoverage, requireLiveCollector } from "./physical_workload_receipt.mjs";

export const REQUIRED_QUIET_MS = 300_000;
export { GO_RUNTIME_LIMIT_BYTES } from "./physical_memory_profile.mjs";
const MAX_MEMORY_GAP_MS = 30_000;
const MAX_TELEMETRY_GAP_MS = 5_000;
const ROLES = ["direct", "client", "provider"];

function validTime(value) {
  return Number.isFinite(value) && value >= 0;
}

function roleMatches(status, role) {
  if (role === "client") {
    return status.connected === true && status.tunnelStarted === true && status.provideEnabled === false;
  }
  // Android keeps the provider service running (tunnelStarted) without a
  // client VPN. The independent network evidence below still requires no VPN.
  if (role === "provider") {
    return status.connected === false && status.tunnelStarted === true && status.provideEnabled === true;
  }
  return role === "direct" && status.connected === false && status.tunnelStarted === false &&
    status.provideEnabled === false;
}

// Shared by the live end-boundary runner and the final evidence gate. With no
// end yet, only the existing quiet samples are evaluated; the acknowledged end
// supplies the upper bound and trailing-coverage check before publication.
export function evaluateQuietSamples(memory, phase, startElapsedMs, endElapsedMs = Infinity) {
  const reasons = new Set();
  if (memory.some(record => !record || typeof record !== "object" || Array.isArray(record))) reasons.add("quiet-memory-row-invalid");
  const inWindow = memory.filter((record) => validTime(record?.elapsedMs) &&
    startElapsedMs <= record.elapsedMs && record.elapsedMs <= endElapsedMs);
  if (inWindow.some((record) => record.type !== "sample")) reasons.add("quiet-sampler-error");
  const samples = inWindow.filter((record) => record.type === "sample");
  if (samples.some((record) => record.phase !== phase)) reasons.add("quiet-phase-interrupted");
  if (samples.some((record) => record.samplerDropped !== 0)) reasons.add("quiet-samples-dropped-or-unknown");
  const sampleDurationMs = samples.length > 1 ? samples.at(-1).elapsedMs - samples[0].elapsedMs : 0;
  if (samples.length < 21 || sampleDurationMs < REQUIRED_QUIET_MS) {
    reasons.add("quiet-samples-shorter-than-300-seconds");
  }
  for (let i = 1; i < samples.length; i += 1) {
    const gap = samples[i].elapsedMs - samples[i - 1].elapsedMs;
    if (!(gap > 0 && gap <= MAX_MEMORY_GAP_MS)) reasons.add("quiet-memory-timestamps-or-gap-invalid");
  }
  // The drain can assign the quiet label to pre-boundary ring records. The
  // device timestamp filter above prevents them from supplying quiet duration.
  if (!samples.length || samples[0].elapsedMs - startElapsedMs > MAX_MEMORY_GAP_MS ||
      (Number.isFinite(endElapsedMs) && endElapsedMs - samples.at(-1)?.elapsedMs > MAX_MEMORY_GAP_MS)) {
    reasons.add("quiet-memory-does-not-cover-boundaries");
  }
  return { samples, sampleDurationMs, reasons: [...reasons] };
}

// Boundary envelopes come from --capture-status, not host/device clock
// subtraction. Device elapsed time brackets memory; host time brackets dumpsys.
export function evaluateQuietWindow({ start, end, memory, telemetry, phase, role, underlay, statusRuntime, diagnostics,
  strictRetainedEvidence = false, profile = MEMORY_AUDIT_PROFILE }) {
  const reasons = new Set();
  const fail = (reason) => reasons.add(reason);
  const policy = memoryAuditPolicy(profile);
  const profileMismatchReason = profile === "android" ? "memory-audit-profile-mismatch" : "ios-memory-audit-profile-mismatch";
  const rateMismatchReason = profile === "android" ? "memory-profile-rate-not-zero" : "ios-memory-profile-rate-not-zero";
  const samplerProfileReason = profile === "android" ? "sampler-memory-profile-mismatch" : "sampler-memory-profile-not-ios";
  const samplerLimitReason = policy ? `sampler-go-memory-limit-not-${policy.goMemoryLimitBytes / (1024 * 1024)}-mib` : "sampler-go-memory-limit-profile-invalid";
  if (!policy) fail("memory-profile-version-invalid");
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
    const preflight = evaluateMemoryProfile(status, { profile });
    if (preflight.reasons.some((reason) => reason !== "go-memory-profile-rate-not-zero")) {
      fail(profileMismatchReason);
    }
    if (status?.goMemoryProfileRateBytes !== QUALIFICATION_PROFILE_RATE_BYTES) {
      fail(rateMismatchReason);
    }
  }
  const firstStatus = start?.status;
  const lastStatus = end?.status;
  if (firstStatus?.pid !== lastStatus?.pid) fail("quiet-process-changed");
  if (["sessionId", "buildId"].some(key => typeof firstStatus?.[key] !== "string" || !firstStatus[key] ||
      firstStatus[key] !== lastStatus?.[key])) fail("quiet-session-or-build-changed");
  if (firstStatus?.commandId === lastStatus?.commandId) fail("fresh-end-status-required");
  const boundaryDurationMs = (lastStatus?.elapsedMs ?? NaN) - (firstStatus?.elapsedMs ?? NaN);
  const hostDurationMs = (end?.hostTimeUnixMs ?? NaN) - (start?.hostTimeUnixMs ?? NaN);
  if (!(boundaryDurationMs >= REQUIRED_QUIET_MS) || !(hostDurationMs >= REQUIRED_QUIET_MS)) {
    fail("quiet-boundaries-shorter-than-300-seconds");
  }

  const primitiveSamples = memory.filter((record) => record?.type === "sample");
  if (primitiveSamples.length !== memory.length) fail("primitive-memory-row-invalid");
  if (primitiveSamples.some(record => record.samplerDropped !== 0 || strictRetainedEvidence && record.samplerSchema !== 13)) fail("primitive-memory-dropped-or-schema-invalid");
  const statusSamples = statusRuntime ?? [firstStatus, lastStatus].filter(Boolean);
  if (statusRuntime) evaluateStatusRuntimeReads(statusRuntime, [firstStatus, lastStatus], profile).forEach(fail);
  const diagnostic = diagnostics ? evaluateDiagnosticRuntimeReads(diagnostics, firstStatus, profile) : { samples: [], reasons: [] };
  diagnostic.reasons.forEach(fail);
  const auxiliary = evaluateAuxiliaryRuntimeValues(memory, statusSamples, [], strictRetainedEvidence, profile);
  auxiliary.reasons.forEach(fail);
  const allSamples = [...memory, ...statusSamples, ...diagnostic.samples];
  let peakGoRuntimeBytes = 0;
  for (const record of allSamples) {
    if (["pid", "sessionId", "buildId"].some(key => record?.[key] !== firstStatus?.[key])) fail("memory-runtime-identity-mismatch");
    if (!policy || record?.memoryProfile !== profile) fail(samplerProfileReason);
    if (!policy || record?.goMemoryLimitBytes !== policy.goMemoryLimitBytes) fail(samplerLimitReason);
    if (record?.goMemoryProfileRateBytes !== QUALIFICATION_PROFILE_RATE_BYTES) {
      fail("sampler-memory-profile-rate-not-zero");
    }
    if (!Number.isSafeInteger(record?.goRuntimeBytes) || record.goRuntimeBytes <= 0) {
      fail("memory-measurement-missing");
    } else {
      peakGoRuntimeBytes = Math.max(peakGoRuntimeBytes, record.goRuntimeBytes);
    }
  }
  peakGoRuntimeBytes = Math.max(peakGoRuntimeBytes, auxiliary.auxiliaryPeakGoRuntimeBytes);
  const memoryLimitBreached = peakGoRuntimeBytes > policy?.goRuntimeLimitBytes;
  if (memoryLimitBreached) fail(`go-runtime-above-${policy.goRuntimeLimitBytes / (1024 * 1024)}-mib`);
  const workloads = start?.workloads;
  const validTelemetry = telemetry.filter(record => record && typeof record === "object" && !Array.isArray(record));
  if (validTelemetry.length !== telemetry.length) fail("telemetry-row-invalid");
  let workloadCoverage = { eligible: false, sampleCount: 0 };
  if (!workloads?.ownerId || workloads.ownerId !== end?.workloads?.ownerId ||
      workloads.label !== phase?.slice("quiet-".length) || !validTime(workloads.startedHostTimeUnixMs) ||
      !validTime(workloads.completedHostTimeUnixMs) || workloads.completedHostTimeUnixMs < workloads.startedHostTimeUnixMs ||
      workloads.completedHostTimeUnixMs > start?.hostTimeUnixMs || !workloads.collector?.pid) {
    fail("workload-collector-evidence-required");
  } else {
    workloadCoverage = evaluateWorkloadCoverage(validTelemetry, workloads.startedHostTimeUnixMs, end?.hostTimeUnixMs, workloads.label);
    if (!workloadCoverage.eligible) fail("workload-collector-coverage-incomplete");
  }
  const { samples, sampleDurationMs, reasons: sampleReasons } = evaluateQuietSamples(
    memory, phase, firstStatus?.elapsedMs, lastStatus?.elapsedMs ?? NaN);
  sampleReasons.forEach(fail);
  // Keep attribution separate from the absolute whole-run gate above. Offline
  // teardown re-evaluates the same boundaries; it is not a second quiet arm.
  const quietPeakGoRuntimeBytes = samples.reduce((peak, record) =>
    Number.isFinite(record.goRuntimeBytes) ? Math.max(peak, record.goRuntimeBytes) : peak, 0);
  const goRuntimeBreachSampleCount = allSamples.filter((record) =>
    Number.isSafeInteger(record?.goRuntimeBytes) && record.goRuntimeBytes > policy?.goRuntimeLimitBytes).length;
  const quietGoRuntimeBreachSampleCount = samples.filter((record) =>
    record.goRuntimeBytes > policy?.goRuntimeLimitBytes).length;

  const hostSamples = validTelemetry.filter((record) => record.type === "sample");
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
    schemaVersion: 4,
    eligible: reasons.size === 0,
    classification: memoryLimitBreached ? "FAILED_MEMORY_LIMIT" :
      reasons.has(rateMismatchReason) || reasons.has("sampler-memory-profile-rate-not-zero") ?
        "INVALID_RATE_ZERO" :
      !policy || reasons.has(profileMismatchReason) || reasons.has(samplerProfileReason) || reasons.has(samplerLimitReason) ||
        reasons.has("diagnostic-runtime-target-mismatch") || reasons.has("diagnostic-runtime-profile-mismatch") ?
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
    devicePrimitiveCount: memory.length,
    statusRuntimeReadCount: statusSamples.length,
    diagnosticRuntimeReadCount: diagnostic.samples.length,
    combinedRuntimeReadCount: allSamples.length,
    combinedRetainedRuntimeEventCount: allSamples.length,
    auxiliary: { ...auxiliary, reasons: auxiliary.reasons },
    telemetrySampleCount: covered.length,
    workloadTelemetrySampleCount: workloadCoverage.sampleCount,
    peakGoRuntimeBytes,
    quietPeakGoRuntimeBytes,
    goRuntimeBreachSampleCount,
    quietGoRuntimeBreachSampleCount,
    ...memoryProfileRequirements(profile),
    goRuntimeLimitBytes: policy?.goRuntimeLimitBytes ?? null,
    memoryProfile: policy ? profile : null,
    requiredGoMemoryProfileRateBytes: QUALIFICATION_PROFILE_RATE_BYTES,
    reasons: [...reasons],
  };
}

function parseNdjson(contents, liveAppend = false) {
  if (liveAppend) contents = contents.slice(0, contents.lastIndexOf("\n") + 1);
  return contents.split(/\r?\n/).filter((line) => line.trim()).map(line => {
    try { return JSON.parse(line); } catch { return { type: "malformed-retained-row" }; }
  });
}

export function parseArgs(argv) {
  const options = {};
  const names = new Set(["capture-status", "serial", "start", "end", "memory", "telemetry", "phase", "role", "underlay", "profile", "live-gate", "status-runtime", "diagnostics", "teardown", "teardown-fallback", "producer-summary", "finish-status", "instrumentation-owner", "finish-command-id", "native-inputs"]);
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.replace(/^--/, "");
    if (!argv[i]?.startsWith("--") || !names.has(name) || !argv[i + 1] || argv[i + 1].startsWith("--")) {
      throw new Error("invalid quiet-gate arguments");
    }
    if (options[name] !== undefined) throw new Error("duplicate quiet-gate argument");
    options[name] = argv[i + 1];
  }
  const required = options["capture-status"] ? ["capture-status", "serial"] :
    ["start", "end", "memory", "status-runtime", "diagnostics", "telemetry", "phase", "role", "underlay",
      ...(options["live-gate"] ? ["teardown", "producer-summary", "finish-status", "instrumentation-owner", "finish-command-id", "native-inputs"] : [])];
  const allowed = options["capture-status"] ? required : [...required, "profile", "live-gate", ...(options["live-gate"] ? ["teardown-fallback"] : [])];
  if (required.some((name) => !options[name]) || Object.keys(options).some((name) => !allowed.includes(name))) {
    throw new Error("missing or incompatible quiet-gate arguments");
  }
  if (!options["capture-status"]) {
    options.profile ??= MEMORY_AUDIT_PROFILE;
    if (!memoryAuditPolicy(options.profile)) throw new Error("invalid quiet-gate profile");
  }
  return options;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const policy = memoryAuditPolicy(options.profile);
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
  let retainedReadFailed = false;
  const readContents = path => {
    try { return readFileSync(path, "utf8"); } catch { retainedReadFailed = true; return ""; }
  };
  const memoryContents = readContents(options.memory);
  const memory = parseNdjson(memoryContents);
  const statusContents = readContents(options["status-runtime"]);
  const statusRuntime = parseNdjson(statusContents);
  const diagnosticContents = readContents(options.diagnostics);
  const diagnostics = parseNdjson(diagnosticContents);
  const result = evaluateQuietWindow({
    start,
    end,
    memory,
    statusRuntime,
    diagnostics, strictRetainedEvidence: true,
    telemetry: parseNdjson(readContents(options.telemetry), true),
    phase: options.phase, role: options.role, underlay: options.underlay, profile: options.profile,
  });
  if (retainedReadFailed) {
    result.eligible = result.connectedClientEvidence = false;
    result.reasons.push("retained-runtime-evidence-unavailable");
    if (result.classification === "QUIET_WINDOW_COMPLETE") result.classification = "INCOMPLETE_QUIET_WINDOW";
  }
  result.workloadOwnerId = start?.workloads?.ownerId ?? null;
  result.memoryPrefix = { ...memoryEvidencePrefix(memoryContents), eventCount: memory.length };
  result.statusPrefix = { ...memoryEvidencePrefix(statusContents), eventCount: statusRuntime.length };
  result.diagnosticPrefix = { ...memoryEvidencePrefix(diagnosticContents), eventCount: diagnostics.filter(row => row?.part === "memory").length };
  result.sessionId = start?.status?.sessionId ?? null;
  result.buildId = start?.status?.buildId ?? null;
  result.pid = start?.status?.pid ?? null;
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
      if (proof.type !== "quiet-window-gate" || proof.schemaVersion !== 4 || proof.eligible !== true || proof.evaluationMode !== "live" ||
          !matchesMemoryProfileProof(proof, options.profile) || proof.memoryProfile !== options.profile ||
          proof.goRuntimeLimitBytes !== policy.goRuntimeLimitBytes || proof.requiredGoMemoryProfileRateBytes !== QUALIFICATION_PROFILE_RATE_BYTES ||
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
    if (!["FAILED_MEMORY_LIMIT", "INVALID_MEMORY_PROFILE", "INVALID_RATE_ZERO"].includes(result.classification)) {
      result.classification = "INCOMPLETE_ACTIVE_COVERAGE";
    }
  }
  if (options["live-gate"]) {
    const readOptional = path => {
      let contents;
      try { contents = readFileSync(path, "utf8"); } catch { return null; }
      if (!contents.trim()) return null;
      try { return JSON.parse(contents); } catch { return { type: "malformed-retained-evidence" }; }
    };
    const ownerPath = options["instrumentation-owner"];
    const finishEnvelope = readOptional(options["finish-status"]);
    const teardown = evaluateMemoryTeardown({ teardown: readOptional(options.teardown), fallback: readOptional(options["teardown-fallback"]),
      producerSummary: readOptional(options["producer-summary"]), memory, statusRuntime, diagnostics,
      finish: finishEnvelope?.status ?? finishEnvelope, owner: readOptional(ownerPath),
      ready: readOptional(`${ownerPath}.ready.json`), terminal: readOptional(`${ownerPath}.terminal.json`),
      nativeProof: readOptional(options["native-inputs"]), liveGate: readOptional(options["live-gate"]), memoryContents,
      statusContents, diagnosticContents, expectedFinishCommandId: options["finish-command-id"], profile: options.profile });
    result.teardown = teardown;
    result.peakGoRuntimeBytes = Math.max(result.peakGoRuntimeBytes, teardown.peakGoRuntimeBytes);
    result.goRuntimeBreachSampleCount = teardown.goRuntimeBreachSampleCount;
    result.combinedRuntimeReadCount = teardown.combinedRuntimeReadCount;
    result.combinedRetainedRuntimeEventCount = teardown.combinedRetainedRuntimeEventCount;
    result.teardownPrimitiveCount = teardown.counts.teardownPrimitiveCount;
    if (!teardown.eligible) {
      result.eligible = result.connectedClientEvidence = false;
      result.reasons.push(...teardown.reasons);
      result.classification = result.peakGoRuntimeBytes > policy.goRuntimeLimitBytes ? "FAILED_MEMORY_LIMIT" : "INCOMPLETE_TEARDOWN";
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
