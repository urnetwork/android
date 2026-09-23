#!/usr/bin/env node

import fs from "node:fs";
import { pathToFileURL } from "node:url";

function positiveTraffic(stats) {
  return stats !== null && typeof stats === "object" &&
    Number(stats.remoteEgressPackets) > 0 &&
    Number(stats.remoteEgressBytes) > 0 &&
    Number(stats.remoteIngressPackets) > 0 &&
    Number(stats.remoteIngressBytes) > 0;
}

export function evaluateStatus(status, commandId, state, proof = "none") {
  if (status === null || typeof status !== "object") return false;
  if (status.commandId !== commandId || status.state !== state) return false;
  if (proof === "none") return true;
  if (proof === "client") {
    return positiveTraffic(status.packets) &&
      typeof status.extra?.address === "string" &&
      /^[0-9a-f:.]+$/i.test(status.extra.address);
  }
  if (proof === "provider") return positiveTraffic(status.providerPackets);
  throw new Error(`unsupported proof ${proof}`);
}

const loginStages = new Set([
  "password-auth",
  "network-session-persistence",
  "auth-client",
  "client-allocation",
  "client-jwt-validation",
  "client-jwt-persistence",
  "device-local-initialization",
  "ready",
  "logout",
]);

const loginFailures = new Set([
  "password-auth-request-failed",
  "password-auth-rejected",
  "password-auth-result-invalid",
  "network-session-persistence-failed",
  "auth-client-unavailable",
  "auth-client-request-failed",
  "auth-client-rejected",
  "auth-client-limit-exceeded",
  "auth-client-upgrade-required",
  "auth-client-result-missing",
  "auth-client-id-missing",
  "auth-client-id-invalid",
  "client-jwt-missing",
  "client-jwt-invalid",
  "client-jwt-id-mismatch",
  "client-jwt-persistence-failed",
  "device-network-space-missing",
  "device-local-state-missing",
  "device-instance-id-missing",
  "device-local-creation-failed",
  "device-local-configuration-failed",
  "auth-logout",
  "stale-attempt",
  "startup-timeout",
  "cleanup-ledger-persistence-failed",
]);

// Initial-form discovery happens before LoginStartupState leaves LoggedOut.
// Keep its vocabulary finite and disallow cross-stage misclassification. UI
// evidence is presence-only; no server text or credential values are printed.
const loginUiFailuresByStage = new Map([
  ["auth-user-form", new Set(["ui-action-failed"])],
  ["auth-discovery", new Set([
    "ui-action-failed",
    "auth-discovery-failed",
    "auth-discovery-timeout",
    "auth-discovery-observation-failed",
  ])],
  ["auth-password-submit", new Set(["ui-action-failed"])],
]);
const loginUiFailures = new Set([...loginUiFailuresByStage.values()].flatMap((failures) => [...failures]));

export function classifyStatus(status, commandId, state, proof = "none") {
  if (evaluateStatus(status, commandId, state, proof)) return "expected";
  if (status === null || typeof status !== "object" || status.commandId !== commandId) {
    return "pending";
  }
  if (status.state !== "error") return "pending";
  const stage = status.extra?.stage;
  const failure = status.extra?.failure;
  if (loginUiFailuresByStage.has(stage) || loginUiFailures.has(failure)) {
    return loginUiFailuresByStage.get(stage)?.has(failure) ? "terminal-error" : "invalid-terminal";
  }
  if ((stage !== undefined && !loginStages.has(stage)) ||
      (failure !== undefined && !loginFailures.has(failure))) {
    return "invalid-terminal";
  }
  if (failure === "cleanup-ledger-persistence-failed") {
    return stage === "client-allocation" ? "cleanup-error" : "invalid-terminal";
  }
  return "terminal-error";
}

async function main() {
  const [, , commandId, state, proof = "none"] = process.argv;
  if (!commandId || !state) {
    throw new Error("usage: p2p-status.mjs COMMAND_ID STATE [none|client|provider]");
  }
  const input = fs.readFileSync(0, "utf8").trim();
  if (!input) process.exit(1);
  let status;
  try {
    status = JSON.parse(input);
  } catch {
    process.exit(1);
  }
  const classification = classifyStatus(status, commandId, state, proof);
  if (classification === "expected") process.exit(0);
  if (classification === "terminal-error") {
    const stage = status.extra?.stage ?? "runtime";
    const failure = status.extra?.failure ?? status.extra?.errorType ?? "unknown";
    console.error(`terminal status: stage=${stage} failure=${failure}`);
    process.exit(3);
  }
  if (classification === "cleanup-error") {
    console.error("terminal status: stage=client-allocation failure=cleanup-ledger-persistence-failed");
    process.exit(4);
  }
  if (classification === "invalid-terminal") process.exit(2);
  process.exit(1);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(2);
  });
}
