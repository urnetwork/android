#!/usr/bin/env node

// Offline preflight of the effective iOS-surrogate admission/GC/profile inputs.
// Build flags alone cannot prove the installed runtime policy. Diagnostic
// sampling retains runtime metadata and cannot qualify the release memory cap.
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

// Versioned policies must never requalify an old capture under a new ceiling.
// The Android surrogate qualifies Go accounting only, never iOS phys_footprint.
export const MEMORY_AUDIT_PROFILES = Object.freeze({
  "ios-memory-audit-v1": Object.freeze({ deviceTargetBytes: 20 * 1024 * 1024,
    goMemoryLimitBytes: 32 * 1024 * 1024, goRuntimeLimitBytes: 28 * 1024 * 1024 }),
  "ios-memory-audit-v2": Object.freeze({ deviceTargetBytes: 32 * 1024 * 1024,
    goMemoryLimitBytes: 32 * 1024 * 1024, goRuntimeLimitBytes: 32 * 1024 * 1024 }),
});
export const MEMORY_AUDIT_PROFILE = "ios-memory-audit-v2";
export const DEVICE_MEMORY_TARGET_BYTES = MEMORY_AUDIT_PROFILES[MEMORY_AUDIT_PROFILE].deviceTargetBytes;
export const GO_MEMORY_LIMIT_BYTES = MEMORY_AUDIT_PROFILES[MEMORY_AUDIT_PROFILE].goMemoryLimitBytes;
export const GO_RUNTIME_LIMIT_BYTES = MEMORY_AUDIT_PROFILES[MEMORY_AUDIT_PROFILE].goRuntimeLimitBytes;
export const QUALIFICATION_PROFILE_RATE_BYTES = 0;
export const DIAGNOSTIC_PROFILE_RATE_BYTES = 65_536;

export function evaluateMemoryProfile(record, { mode = "qualification", profile = MEMORY_AUDIT_PROFILE } = {}) {
  const status = record?.status ?? record;
  const reasons = [];
  const validMode = ["qualification", "diagnostic"].includes(mode);
  if (!validMode) reasons.push("memory-profile-mode-invalid");
  const policy = Object.hasOwn(MEMORY_AUDIT_PROFILES, profile) ? MEMORY_AUDIT_PROFILES[profile] : null;
  if (!policy) reasons.push("memory-profile-version-invalid");
  const requiredRate = mode === "diagnostic" ? DIAGNOSTIC_PROFILE_RATE_BYTES : QUALIFICATION_PROFILE_RATE_BYTES;
  if (status?.type !== "status" || !["ready", "complete"].includes(status.state) ||
      !Number.isInteger(status.pid) || status.pid <= 0) reasons.push("live-status-required");
  if (!policy || status?.memoryProfile !== profile) reasons.push("selected-memory-profile-not-ios");
  if (!policy || status?.trackedMemory?.targetBytes !== policy.deviceTargetBytes) {
    reasons.push("device-memory-target-mismatch");
  }
  if (!policy || status?.goMemoryLimitBytes !== policy.goMemoryLimitBytes) reasons.push("go-memory-limit-not-32-mib");
  const rateMismatch = status?.goMemoryProfileRateBytes !== requiredRate;
  if (rateMismatch) reasons.push(mode === "diagnostic" ?
    "go-memory-profile-rate-not-65536" : "go-memory-profile-rate-not-zero");
  const eligible = reasons.length === 0;
  return {
    type: "memory-profile-gate", schemaVersion: 4,
    eligible,
    classification: eligible ? (mode === "diagnostic" ? "DIAGNOSTIC_PROFILE_READY" : "MEMORY_PROFILE_READY") :
      rateMismatch && mode === "qualification" ? "INVALID_RATE_ZERO" : "INVALID_MEMORY_PROFILE",
    measurementMode: validMode ? mode : null,
    qualificationEligible: eligible && mode === "qualification",
    requiredProfile: policy ? profile : null,
    observedProfile: ["android", ...Object.keys(MEMORY_AUDIT_PROFILES)].includes(status?.memoryProfile) ? status.memoryProfile : null,
    requiredDeviceTargetBytes: policy?.deviceTargetBytes ?? null,
    requiredGoMemoryLimitBytes: policy?.goMemoryLimitBytes ?? null,
    requiredGoRuntimeLimitBytes: policy?.goRuntimeLimitBytes ?? null,
    requiredGoMemoryProfileRateBytes: requiredRate,
    observedDeviceTargetBytes: Number.isFinite(status?.trackedMemory?.targetBytes) ? status.trackedMemory.targetBytes : null,
    observedGoMemoryLimitBytes: Number.isFinite(status?.goMemoryLimitBytes) ? status.goMemoryLimitBytes : null,
    observedGoMemoryProfileRateBytes: Number.isFinite(status?.goMemoryProfileRateBytes) ? status.goMemoryProfileRateBytes : null,
    reasons,
  };
}

export function parseMemoryProfileArgs(argv) {
  const options = { mode: "qualification", profile: MEMORY_AUDIT_PROFILE };
  const seen = new Set();
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    const value = argv[i + 1];
    if (!["--status", "--mode", "--profile"].includes(key) || seen.has(key) || !value || value.startsWith("--")) throw new Error();
    seen.add(key);
    options[key.slice(2)] = value;
  }
  if (!options.status || !["qualification", "diagnostic"].includes(options.mode) ||
      !Object.hasOwn(MEMORY_AUDIT_PROFILES, options.profile)) throw new Error();
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseMemoryProfileArgs(process.argv.slice(2));
    const result = evaluateMemoryProfile(JSON.parse(readFileSync(options.status, "utf8")), options);
    process.stdout.write(`${JSON.stringify(result)}\n`);
    if (!result.eligible) process.exitCode = 2;
  } catch {
    // No adb calls and no serial, credential, raw status, or path in output.
    process.stderr.write("memory-profile evidence unavailable, malformed, or arguments invalid\n");
    process.exitCode = 2;
  }
}
