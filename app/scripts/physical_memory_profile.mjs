#!/usr/bin/env node

// Offline preflight of the effective iOS-surrogate admission/GC/profile inputs.
// Build flags alone cannot prove the installed runtime policy. Diagnostic
// sampling retains runtime metadata and cannot qualify the release memory cap.
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export const MEMORY_AUDIT_PROFILE = "ios-memory-audit-v1";
export const DEVICE_MEMORY_TARGET_BYTES = 20 * 1024 * 1024;
export const GO_MEMORY_LIMIT_BYTES = 32 * 1024 * 1024;
export const QUALIFICATION_PROFILE_RATE_BYTES = 0;
export const DIAGNOSTIC_PROFILE_RATE_BYTES = 65_536;

export function evaluateMemoryProfile(record, { mode = "qualification" } = {}) {
  const status = record?.status ?? record;
  const reasons = [];
  const validMode = ["qualification", "diagnostic"].includes(mode);
  if (!validMode) reasons.push("memory-profile-mode-invalid");
  const requiredRate = mode === "diagnostic" ? DIAGNOSTIC_PROFILE_RATE_BYTES : QUALIFICATION_PROFILE_RATE_BYTES;
  if (status?.type !== "status" || !["ready", "complete"].includes(status.state) ||
      !Number.isInteger(status.pid) || status.pid <= 0) reasons.push("live-status-required");
  if (status?.trackedMemory?.targetBytes !== DEVICE_MEMORY_TARGET_BYTES) {
    reasons.push("device-memory-target-not-20-mib");
  }
  if (status?.goMemoryLimitBytes !== GO_MEMORY_LIMIT_BYTES) reasons.push("go-memory-limit-not-32-mib");
  const rateMismatch = status?.goMemoryProfileRateBytes !== requiredRate;
  if (rateMismatch) reasons.push(mode === "diagnostic" ?
    "go-memory-profile-rate-not-65536" : "go-memory-profile-rate-not-zero");
  const eligible = reasons.length === 0;
  return {
    type: "memory-profile-gate", schemaVersion: 2,
    eligible,
    classification: eligible ? (mode === "diagnostic" ? "DIAGNOSTIC_PROFILE_READY" : "MEMORY_PROFILE_READY") :
      rateMismatch && mode === "qualification" ? "INVALID_RATE_ZERO" : "INVALID_MEMORY_PROFILE",
    measurementMode: validMode ? mode : null,
    qualificationEligible: eligible && mode === "qualification",
    requiredProfile: MEMORY_AUDIT_PROFILE,
    requiredDeviceTargetBytes: DEVICE_MEMORY_TARGET_BYTES,
    requiredGoMemoryLimitBytes: GO_MEMORY_LIMIT_BYTES,
    requiredGoMemoryProfileRateBytes: requiredRate,
    observedDeviceTargetBytes: Number.isFinite(status?.trackedMemory?.targetBytes) ? status.trackedMemory.targetBytes : null,
    observedGoMemoryLimitBytes: Number.isFinite(status?.goMemoryLimitBytes) ? status.goMemoryLimitBytes : null,
    observedGoMemoryProfileRateBytes: Number.isFinite(status?.goMemoryProfileRateBytes) ? status.goMemoryProfileRateBytes : null,
    reasons,
  };
}

export function parseMemoryProfileArgs(argv) {
  const options = { mode: "qualification" };
  const seen = new Set();
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    const value = argv[i + 1];
    if (!["--status", "--mode"].includes(key) || seen.has(key) || !value || value.startsWith("--")) throw new Error();
    seen.add(key);
    options[key.slice(2)] = value;
  }
  if (!options.status || !["qualification", "diagnostic"].includes(options.mode)) throw new Error();
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseMemoryProfileArgs(process.argv.slice(2));
    const result = evaluateMemoryProfile(JSON.parse(readFileSync(options.status, "utf8")), { mode: options.mode });
    process.stdout.write(`${JSON.stringify(result)}\n`);
    if (!result.eligible) process.exitCode = 2;
  } catch {
    // No adb calls and no serial, credential, raw status, or path in output.
    process.stderr.write("memory-profile evidence unavailable, malformed, or arguments invalid\n");
    process.exitCode = 2;
  }
}
