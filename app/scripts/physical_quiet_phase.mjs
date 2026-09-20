#!/usr/bin/env node

// One host-owned phase-command/acknowledgment/envelope transaction. This does
// not connect, disconnect, generate traffic, wait five minutes, or finish a run.
import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, openSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { setTimeout as sleep } from "node:timers/promises";
import { pathToFileURL } from "node:url";
import { requireCompletedWorkloads, requireLiveCollector } from "./physical_workload_receipt.mjs";

const PACKAGE = "com.bringyour.network";
const COMMAND_PATH = "files/acceptance/physical-command";
const STATUS_PATH = "files/acceptance/physical-status";
const TIMEOUT_MS = 30_000;
const QUIET_PHASE = /^quiet-[A-Za-z0-9._-]+$/;

class BoundaryError extends Error {}
const fail = (reason) => { throw new BoundaryError(reason); };
const validStatus = (status) => status?.type === "status" &&
  Number.isInteger(status.pid) && status.pid > 0 && Number.isFinite(status.elapsedMs) &&
  status.elapsedMs >= 0 && typeof status.commandId === "string" && status.commandId.length > 0;

export function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !["serial", "output", "label", "start", "workloads"].includes(name) ||
        !argv[i + 1] || argv[i + 1].startsWith("--") || options[name] !== undefined) {
      fail("invalid-arguments");
    }
    options[name] = argv[i + 1];
  }
  if (!options.serial || !options.output || Boolean(options.label) === Boolean(options.start)) {
    fail("serial-output-and-exactly-one-of-label-or-start-required");
  }
  if (options.label ? !options.workloads : options.workloads) fail("workloads-required-for-start-only");
  return options;
}

// Dependency injection is only for deterministic fake-ADB/fake-clock tests;
// production callers always use the fixed, bounded timeout and real adb.
export async function captureQuietPhase(options, dependencies = {}) {
  const now = dependencies.now ?? (() => performance.now());
  const wallNow = dependencies.wallNow ?? Date.now;
  const pause = dependencies.sleep ?? sleep;
  const uuid = (dependencies.uuid ?? randomUUID)();
  if (!/^[A-Za-z0-9-]+$/.test(uuid)) fail("invalid-command-token");
  const deadline = now() + TIMEOUT_MS;
  const commandId = `quiet-${uuid}`;
  let phase;
  let start;
  let workloads;
  if (options.start) {
    start = JSON.parse(readFileSync(options.start, "utf8"));
    if (!Number.isFinite(start.hostTimeUnixMs) || start.hostTimeUnixMs < 0 ||
        !validStatus(start.status) || start.status.state !== "complete" || !QUIET_PHASE.test(start.status.phase)) {
      fail("validated-start-envelope-required");
    }
    phase = start.status.phase;
    workloads = start.workloads;
    if (!workloads?.ownerId || workloads.label !== phase.slice("quiet-".length) ||
        !Number.isFinite(workloads.completedHostTimeUnixMs) || workloads.completedHostTimeUnixMs > start.hostTimeUnixMs ||
        !Number.isInteger(workloads.failedChildCount) || workloads.failedChildCount < 0) fail("start-workload-evidence-required");
    try { requireLiveCollector(workloads.collector, workloads.label, workloads.startedHostTimeUnixMs,
      { wallNow, isLive: dependencies.workloadIsLive }); } catch { fail("live-collector-coverage-required"); }
  } else {
    if (!/^[A-Za-z0-9._-]+$/.test(options.label ?? "")) fail("safe-nonempty-label-required");
    phase = `quiet-${options.label}`;
    if (!options.workloads) fail("completed-workload-receipt-required");
    try {
      const receipt = requireCompletedWorkloads(options.workloads, options.label, options.serial,
        { wallNow, isLive: dependencies.workloadIsLive });
      workloads = { ownerId: receipt.ownerId, label: receipt.label,
        startedHostTimeUnixMs: receipt.startedHostTimeUnixMs,
        completedHostTimeUnixMs: receipt.completedHostTimeUnixMs, failedChildCount: receipt.failedChildCount,
        collector: receipt.collector };
    } catch { fail("completed-workload-receipt-required"); }
  }
  if (existsSync(options.output)) fail("output-already-exists");
  const temporaryOutput = `${options.output}.pending-${uuid}`;
  const temporaryCommand = `${COMMAND_PATH}.${uuid}`;
  // Reserve a private host file before issuing any device command. Final
  // publication is exclusive and atomic; no raw status is ever published.
  let descriptor = openSync(temporaryOutput, "wx", 0o600);
  let temporaryCommandMayExist = false;
  const invoke = dependencies.adb ?? ((arguments_, input_, timeout) => spawnSync("adb", arguments_, {
    input: input_, timeout, encoding: "utf8", maxBuffer: 1024 * 1024,
  }));
  const adb = (args, input) => {
    const remaining = deadline - now();
    if (remaining <= 0) fail("acknowledgment-timeout");
    return invoke(["-s", options.serial, "shell", "run-as", PACKAGE, ...args],
      input, Math.max(1, Math.floor(Math.min(2_000, remaining))));
  };
  const readStatus = () => {
    const result = adb(["cat", STATUS_PATH]);
    if (result.status !== 0) return undefined;
    try { return JSON.parse(result.stdout); } catch { return undefined; }
  };
  try {
    const initial = readStatus();
    if (!validStatus(initial) || !["ready", "complete"].includes(initial.state) || initial.phase === "finish") {
      fail("live-idle-session-required");
    }
    if (start && (initial.pid !== start.status.pid || initial.phase !== phase ||
        initial.elapsedMs < start.status.elapsedMs || wallNow() < start.hostTimeUnixMs)) {
      fail("start-session-or-phase-changed");
    }
    if (initial.commandId === commandId || start?.status.commandId === commandId) fail("fresh-command-id-required");
    // adb shell forwards stdin here; exec-out did not reliably forward the
    // command on the physical harness. tee's echo stays private in memory.
    temporaryCommandMayExist = true;
    const command = `${commandId}|phase|${phase}\n`;
    const written = adb(["tee", temporaryCommand], command);
    if (written.status !== 0) {
      fail("phase-command-write-failed");
    }
    if (written.stdout?.replaceAll("\r\n", "\n") !== command) fail("phase-command-echo-mismatch");
    if (adb(["mv", temporaryCommand, COMMAND_PATH]).status !== 0) fail("phase-command-publish-failed");
    temporaryCommandMayExist = false;
    for (;;) {
      const status = readStatus();
      if (validStatus(status)) {
        if (status.pid !== initial.pid) fail("instrumentation-process-changed");
        if (![initial.commandId, commandId].includes(status.commandId)) fail("concurrent-command-observed");
        if (status.commandId === commandId) {
          if (status.phase !== phase) fail("acknowledged-phase-mismatch");
          if (status.state === "error") fail("phase-command-rejected");
          if (status.state === "complete") {
            if (status.elapsedMs < initial.elapsedMs || ["connected", "tunnelStarted", "provideEnabled"]
              .some((key) => status[key] !== initial[key])) fail("session-role-or-clock-changed");
            const envelope = { hostTimeUnixMs: wallNow(), workloads, status };
            writeFileSync(descriptor, `${JSON.stringify(envelope)}\n`);
            closeSync(descriptor);
            descriptor = undefined;
            linkSync(temporaryOutput, options.output);
            return envelope;
          }
        }
      }
      if (now() >= deadline) fail("acknowledgment-timeout");
      await pause(Math.min(250, deadline - now()));
    }
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
    unlinkSync(temporaryOutput);
    if (temporaryCommandMayExist) {
      // Only our unique staging file; never touch a published/other command.
      try {
        invoke(["-s", options.serial, "shell", "run-as", PACKAGE, "rm", "-f", temporaryCommand], undefined, 1_000);
      } catch { /* preserve the original failure if a disconnected device cannot clean up */ }
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await captureQuietPhase(parseArgs(process.argv.slice(2)));
  } catch (error) {
    // Never echo a serial, private path, raw status, or remote command output.
    process.stderr.write(`quiet phase boundary failed: ${error instanceof BoundaryError ? error.message : "evidence-unavailable"}\n`);
    process.exitCode = 2;
  }
}
