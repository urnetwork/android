#!/usr/bin/env node

// Offline preflight of the effective iOS-surrogate admission/GC inputs. Build
// flags alone are not evidence that the installed process selected that policy.
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export const MEMORY_AUDIT_PROFILE = "ios-memory-audit-v1";
export const DEVICE_MEMORY_TARGET_BYTES = 20 * 1024 * 1024;
export const GO_MEMORY_LIMIT_BYTES = 32 * 1024 * 1024;

export function evaluateMemoryProfile(record) {
  const status = record?.status ?? record;
  const reasons = [];
  if (status?.type !== "status" || !["ready", "complete"].includes(status.state) ||
      !Number.isInteger(status.pid) || status.pid <= 0) reasons.push("live-status-required");
  if (status?.trackedMemory?.targetBytes !== DEVICE_MEMORY_TARGET_BYTES) {
    reasons.push("device-memory-target-not-20-mib");
  }
  if (status?.goMemoryLimitBytes !== GO_MEMORY_LIMIT_BYTES) reasons.push("go-memory-limit-not-32-mib");
  return {
    type: "memory-profile-gate", schemaVersion: 1,
    eligible: reasons.length === 0,
    classification: reasons.length ? "INVALID_MEMORY_PROFILE" : "MEMORY_PROFILE_READY",
    requiredProfile: MEMORY_AUDIT_PROFILE,
    requiredDeviceTargetBytes: DEVICE_MEMORY_TARGET_BYTES,
    requiredGoMemoryLimitBytes: GO_MEMORY_LIMIT_BYTES,
    observedDeviceTargetBytes: Number.isFinite(status?.trackedMemory?.targetBytes) ? status.trackedMemory.targetBytes : null,
    observedGoMemoryLimitBytes: Number.isFinite(status?.goMemoryLimitBytes) ? status.goMemoryLimitBytes : null,
    reasons,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4 || process.argv[2] !== "--status") throw new Error();
    const result = evaluateMemoryProfile(JSON.parse(readFileSync(process.argv[3], "utf8")));
    process.stdout.write(`${JSON.stringify(result)}\n`);
    if (!result.eligible) process.exitCode = 2;
  } catch {
    // No adb calls and no serial, credential, raw status, or path in output.
    process.stderr.write("memory-profile evidence unavailable, malformed, or arguments invalid\n");
    process.exitCode = 2;
  }
}
