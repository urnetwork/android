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
  process.exit(evaluateStatus(status, commandId, state, proof) ? 0 : 1);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(2);
  });
}
