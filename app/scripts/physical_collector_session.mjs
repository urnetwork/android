#!/usr/bin/env node

// Foreground retained PTY owners for the fixed physical instrumentation and
// its read-only collector. Run each alone in a retained executor session;
// bind/check from another executor. No daemonization, restart or gate bypass.
import { spawn, spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, lstatSync, openSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseArgs as parseCaptureArgs } from "./physical_lowbar_capture.mjs";
import { processIsLive, requireLiveCollector } from "./physical_workload_receipt.mjs";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { nativeProvenanceReason, requireVerifiedNativeInputs } from "./physical_native_provenance.mjs";
import { handoffCredentialOwnership, rollbackCredentialSetup } from "./physical_credential_ownership.mjs";

class SessionError extends Error {
  constructor(reason, statusRead) { super(reason); this.statusRead = statusRead; }
}
const fail = (reason, statusRead) => { throw new SessionError(reason, statusRead); };
export const statusReadFailureMetadata = error => error instanceof SessionError ? error.statusRead : undefined;
export function formatSessionFailure(error) {
  const metadata = statusReadFailureMetadata(error);
  return `collector session failed: ${error instanceof SessionError ? error.message : "evidence-unavailable"}\n` +
    (metadata ? `${JSON.stringify(metadata)}\n` : "");
}
const pidValid = (pid) => Number.isInteger(pid) && pid > 0;
const captureScript = fileURLToPath(new URL("./physical_lowbar_capture.mjs", import.meta.url));
const packageName = "com.bringyour.network";
const statusPath = "files/acceptance/physical-status";
// The app's connect wait is 120 seconds; allow its command-poll interval too.
// This precedes collector startup and does not relax any telemetry deadline.
const roleTimeoutMs = 150_000;
const commandIdValid = (id) => typeof id === "string" && /^[A-Za-z0-9._-]+$/.test(id);
const digest = (value) => createHash("sha256").update(value).digest("hex");
const hexDigest = (value) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
const physicalClass = `${packageName}.acceptance.PhysicalLowbarSessionTest`;
const runnerComponents = [`${packageName}.test/androidx.test.runner.AndroidJUnitRunner`,
  `${packageName}.test/${packageName}.acceptance.PhysicalCredentialDiagnosticRunner`];

function hostProcessDetails(result, pid) {
  if (result?.status !== 0 || result.error || result.signal || typeof result.stdout !== "string") {
    fail("retained-instrumentation-owner-not-live");
  }
  const fields = result.stdout.match(/^\s*(\d+)\s+(\S+)\s+(\S+\s+\S+\s+\d+\s+\d{2}:\d{2}:\d{2}\s+\d{4})\s+([^\r\n]+)\s*$/);
  if (!fields || Number(fields[1]) !== pid || /[ZX]/i.test(fields[2])) fail("retained-instrumentation-owner-not-live");
  return { command: fields[4].trim(), identity: digest(`${pid}\n${fields[3]}\n${fields[4].trim()}`) };
}

// The retained host adb client is the AM instrumentation owner. The test APK
// is loaded into the target app; it does not need a separate .test process.
// Inspect command/start identity only in memory; never publish raw ps output.
export function instrumentationProcessIdentity(result, serial, pid) {
  const details = hostProcessDetails(result, pid);
  const args = details.command.split(/\s+/);
  if (args[0]?.split("/").at(-1) !== "adb" || args[1] !== "-s" || args[2] !== serial ||
      args[3] !== "shell" || args[4] !== "am" || args[5] !== "instrument" ||
      !args.slice(6).includes("-w") || !runnerComponents.includes(args.at(-1)) ||
      !args.some((arg, index) => arg === "-e" && args[index + 1] === "class" && args[index + 2] === physicalClass)) {
    fail("retained-adb-instrumentation-owner-required");
  }
  return details.identity;
}

function liveHostOwner(pid, serial, dependencies, supervisor = false) {
  const result = (dependencies.hostProcess ?? ((value) => spawnSync("ps",
    ["-ww", "-p", String(value), "-o", "pid=,stat=,lstart=,args="],
    { encoding: "utf8", timeout: 2000, maxBuffer: 64 * 1024 })))(pid);
  return supervisor ? hostProcessDetails(result, pid).identity : instrumentationProcessIdentity(result, serial, pid);
}

function deviceInvoker(serial, dependencies) {
  const invoke = dependencies.adb ?? ((args) => spawnSync("adb", args,
    { encoding: "utf8", timeout: 2000, maxBuffer: 1024 * 1024 }));
  return (args) => invoke(["-s", serial, "shell", ...args]);
}

function readTargetStatus(adb, expectedPid, minimumElapsedMs = 0) {
  // pidof runs as the adb shell, not run-as: app-domain signal permission is
  // neither needed nor used as a proxy for target-process liveness.
  const targetPids = () => {
    const result = adb(["pidof", packageName]);
    if (result?.status !== 0 || result.error || result.signal ||
        !/^\s*[1-9][0-9]*(?:\s+[1-9][0-9]*)*\s*$/.test(result.stdout ?? "")) {
      fail("target-app-process-not-live");
    }
    const pids = result.stdout.trim().split(/\s+/).map(Number);
    if (!pids.every(Number.isSafeInteger)) fail("target-app-process-not-live");
    return new Set(pids);
  };
  const before = targetPids();
  const result = adb(["run-as", packageName, "cat", statusPath]);
  let status;
  const stdout = typeof result?.stdout === "string" ? result.stdout : "";
  const stderr = typeof result?.stderr === "string" ? result.stderr : "";
  const transportCodes = { ETIMEDOUT: "timeout", ENOBUFS: "output-limit", ENOENT: "spawn-unavailable", EPIPE: "pipe-closed" };
  const transportError = result?.error ? (Object.hasOwn(transportCodes, result.error.code)
    ? transportCodes[result.error.code] : "other") : null;
  let outcome;
  if (result?.status !== 0 || result.error || result.signal) {
    // Classify only the known cat target's diagnostics; never publish the raw
    // error, serial, private paths, status fields or process identifiers.
    outcome = transportError ? "adb-transport-error" : result?.signal ? "adb-signalled" :
      /(?:^|\n)(?:\/system\/bin\/)?cat: files\/acceptance\/physical-status: No such file or directory\r?(?:\n|$)/.test(stderr)
        ? "status-path-missing" : /(?:^|\n)(?:\/system\/bin\/)?cat: files\/acceptance\/physical-status: Permission denied\r?(?:\n|$)/.test(stderr)
          ? "status-read-denied" : "adb-nonzero-exit";
  } else if (!stdout.trim()) outcome = "empty-status-output";
  else {
    try { status = JSON.parse(stdout); } catch { outcome = "malformed-status-json"; }
    if (!outcome) outcome = status?.type !== "status" ? "invalid-status-type" : !pidValid(status.pid) ? "invalid-status-pid" :
      !commandIdValid(status.commandId) ? "invalid-status-command-id" :
      !Number.isFinite(status.elapsedMs) || status.elapsedMs < 0 ? "invalid-status-elapsed" :
      status.elapsedMs < minimumElapsedMs ? "status-elapsed-regressed" :
      !["ready", "running", "complete", "error"].includes(status.state) ? "invalid-status-state" :
      status.phase === "finish" ? "finished-status" : undefined;
  }
  if (outcome) {
    let after;
    try { after = targetPids(); } catch { /* record unavailable continuity, never retry the failed status read */ }
    const hasExpected = pidValid(expectedPid);
    fail("live-instrumentation-status-required", { type: "physical-status-read-failure", schema: 1,
      classification: "INVALID_SETUP", outcome, transportError,
      exitCode: Number.isInteger(result?.status) && result.status >= 0 && result.status <= 255 ? result.status : null,
      signalled: Boolean(result?.signal), stdoutBytes: Buffer.byteLength(stdout), stderrBytes: Buffer.byteLength(stderr),
      readyTargetBound: hasExpected, targetPresentBefore: hasExpected ? before.has(expectedPid) : null,
      targetPresentAfter: hasExpected && after ? after.has(expectedPid) : null,
      targetSetUnchanged: after ? before.size === after.size && [...before].every(pid => after.has(pid)) : null });
  }
  if (!before.has(status.pid) || !targetPids().has(status.pid)) fail("target-app-process-changed");
  return status;
}

function readInstrumentationOwner(path, serial, requireReady = true) {
  if (!path || !existsSync(path)) fail("retained-instrumentation-owner-receipt-required");
  const file = lstatSync(path);
  if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o777) !== 0o600 || file.size > 4096) {
    fail("private-instrumentation-owner-receipt-required");
  }
  const owner = JSON.parse(readFileSync(path, "utf8"));
  if (owner.schema !== 1 || owner.type !== "instrumentation-session" || owner.state !== "running" ||
      !/^[a-f0-9-]{36}$/.test(owner.ownerId ?? "") || !pidValid(owner.supervisorPid) || !pidValid(owner.adbPid) ||
      owner.supervisorPid === owner.adbPid || !hexDigest(owner.supervisorIdentity) || !hexDigest(owner.adbIdentity) ||
      owner.serialHash !== digest(serial) || !commandIdValid(owner.label) || owner.className !== physicalClass ||
      owner.targetPackage !== packageName || !runnerComponents.includes(owner.component) ||
      !Number.isFinite(owner.startedHostTimeUnixMs) || owner.startedHostTimeUnixMs < 0) {
    fail("invalid-instrumentation-owner-receipt");
  }
  requireRetainedForeground(owner.foreground ?? {});
  if (existsSync(`${path}.terminal.json`)) fail("instrumentation-session-already-finished");
  if (requireReady) {
    const readyPath = `${path}.ready.json`;
    if (!existsSync(readyPath)) fail("bound-ready-instrumentation-required");
    const readyFile = lstatSync(readyPath);
    if (!readyFile.isFile() || readyFile.uid !== process.getuid() || (readyFile.mode & 0o777) !== 0o600 || readyFile.size > 4096) {
      fail("private-instrumentation-ready-receipt-required");
    }
    const ready = JSON.parse(readFileSync(readyPath, "utf8"));
    if (ready.schema !== 1 || ready.type !== "instrumentation-session-ready" || ready.ownerId !== owner.ownerId ||
        ready.serialHash !== owner.serialHash || !pidValid(ready.targetPid) ||
        !Number.isFinite(ready.elapsedMs) || ready.elapsedMs < 0) fail("bound-ready-instrumentation-required");
    return { ...owner, targetPid: ready.targetPid, readyElapsedMs: ready.elapsedMs };
  }
  return owner;
}

function requireLiveInstrumentation(owner, serial, dependencies) {
  const identity = (pid, supervisor) => {
    try { return liveHostOwner(pid, serial, dependencies, supervisor); } catch (error) {
      if (error instanceof SessionError) fail(`retained-instrumentation-${supervisor ? "supervisor" : "adb"}-not-live`);
      throw error;
    }
  };
  if (identity(owner.supervisorPid, true) !== owner.supervisorIdentity) {
    fail("retained-instrumentation-supervisor-changed");
  }
  if (identity(owner.adbPid, false) !== owner.adbIdentity) fail("retained-instrumentation-adb-changed");
}

// Read-only ready binding. The supervisor owns the adb child; this records the
// target-app PID once, before connect, without rewriting its running receipt.
export function bindInstrumentationReady(options, dependencies = {}) {
  if (!options.owner || !options.serial) fail("instrumentation-owner-and-serial-required");
  if (existsSync(`${options.owner}.ready.json`)) fail("instrumentation-ready-already-bound");
  const owner = readInstrumentationOwner(options.owner, options.serial, false);
  requireLiveInstrumentation(owner, options.serial, dependencies);
  const status = readTargetStatus(deviceInvoker(options.serial, dependencies), owner.targetPid);
  if (status.state !== "ready" || status.phase !== "ready") fail("ready-session-before-owner-binding-required");
  requireLiveInstrumentation(owner, options.serial, dependencies);
  const current = readInstrumentationOwner(options.owner, options.serial, false);
  if (JSON.stringify(current) !== JSON.stringify(owner)) fail("instrumentation-owner-receipt-replaced");
  publish(`${options.owner}.ready.json`, { schema: 1, type: "instrumentation-session-ready", ownerId: owner.ownerId,
    serialHash: owner.serialHash, targetPid: status.pid, elapsedMs: status.elapsedMs, hostTimeUnixMs: Date.now() });
  return { retainedOwnerLive: true, adbChildLive: true, targetProcessMatchesStatus: true, readyStatusReadable: true };
}

// Diagnostic commands change the phase without changing the VPN role. Reuse
// the existing retained owner / ready-bound target checks without demanding
// that the most recent command still be connect-h1. No command is issued here.
export function checkInstrumentationCommandSession(options, dependencies = {}) {
  if (!options.owner || !options.serial) fail("instrumentation-owner-and-serial-required");
  const owner = readInstrumentationOwner(options.owner, options.serial);
  requireLiveInstrumentation(owner, options.serial, dependencies);
  const status = readTargetStatus(deviceInvoker(options.serial, dependencies), owner.targetPid);
  if (status.pid !== owner.targetPid || status.elapsedMs < owner.readyElapsedMs) fail("instrumentation-target-process-changed");
  if (["connected", "tunnelStarted", "provideEnabled"].some(key => typeof status[key] !== "boolean") ||
      typeof status.transportMode !== "string" || !status.transportMode) fail("live-instrumentation-status-required");
  requireLiveInstrumentation(owner, options.serial, dependencies);
  if (JSON.stringify(readInstrumentationOwner(options.owner, options.serial)) !== JSON.stringify(owner)) {
    fail("instrumentation-owner-receipt-replaced");
  }
  return { sessionId: owner.ownerId, pid: status.pid, commandId: status.commandId, state: status.state, phase: status.phase,
    elapsedMs: status.elapsedMs, connected: status.connected, tunnelStarted: status.tunnelStarted,
    provideEnabled: status.provideEnabled, transportMode: status.transportMode };
}

function validateRoleOptions(options, capture) {
  const mode = options["session-mode"];
  if (mode === undefined && options["connect-command-id"] === undefined && options["instrumentation-owner"] === undefined) return;
  if (!["h1", "direct"].includes(mode) ||
      (mode === "h1" ? !commandIdValid(options["connect-command-id"]) : options["connect-command-id"] !== undefined)) {
    fail("explicit-h1-command-or-direct-role-required");
  }
  if (capture && (mode === "h1" ? !capture.requireVpn : !capture.requireNoVpn)) {
    fail("collector-vpn-requirement-does-not-match-session-role");
  }
  if (!options["instrumentation-owner"]) fail("retained-instrumentation-owner-receipt-required");
}

// Read only: this never issues connect/disconnect or changes Android state.
// Re-read the live app status immediately before spawning a guarded collector;
// a saved connected snapshot alone cannot satisfy this prerequisite.
export async function checkSessionRole(options, dependencies = {}, wait = true) {
  validateRoleOptions(options);
  if (!options.serial || !options["session-mode"]) fail("serial-and-session-role-required");
  const adb = deviceInvoker(options.serial, dependencies);
  const retained = readInstrumentationOwner(options["instrumentation-owner"], options.serial);
  const checkOwner = () => {
    const current = readInstrumentationOwner(options["instrumentation-owner"], options.serial);
    if (JSON.stringify(current) !== JSON.stringify(retained)) fail("instrumentation-owner-receipt-replaced");
    requireLiveInstrumentation(retained, options.serial, dependencies);
  };
  const now = dependencies.now ?? performance.now.bind(performance);
  const deadline = now() + roleTimeoutMs;
  let initial;
  let previousElapsedMs = -1;
  for (;;) {
    checkOwner();
    const status = readTargetStatus(adb, retained.targetPid, Math.max(previousElapsedMs, retained.readyElapsedMs));
    if (status.pid !== retained.targetPid) fail("instrumentation-target-process-changed");
    checkOwner();
    initial ??= status;
    if (initial.pid !== status.pid) fail("instrumentation-process-changed");
    previousElapsedMs = status.elapsedMs;
    let ready = false;
    if (options["session-mode"] === "h1") {
      if (![initial.commandId, options["connect-command-id"]].includes(status.commandId)) fail("concurrent-command-observed");
      if (status.commandId === options["connect-command-id"]) {
        if (status.phase !== "connect-h1" || status.state === "error") fail("h1-connect-command-rejected");
        if (status.state === "complete") {
          if (status.transportMode !== "h1" || status.connected !== true || status.tunnelStarted !== true ||
              status.provideEnabled !== false) fail("completed-h1-tunnel-required");
          ready = true;
        }
      } else if (!["ready", "complete"].includes(status.state)) fail("idle-session-before-h1-required");
    } else {
      if (!["ready", "complete"].includes(status.state) || status.connected !== false ||
          status.tunnelStarted !== false || status.provideEnabled !== false) fail("disconnected-direct-session-required");
      ready = true;
    }
    if (ready) return { sessionMode: options["session-mode"], pid: status.pid, commandId: status.commandId,
      elapsedMs: status.elapsedMs, state: status.state, phase: status.phase, connected: status.connected,
      tunnelStarted: status.tunnelStarted, provideEnabled: status.provideEnabled,
      retainedOwnerLive: true, targetProcessMatchesStatus: true, targetStatusReadable: true,
      ...(options["session-mode"] === "h1" ? { transportMode: status.transportMode } : {}) };
    if (!wait) fail("h1-connect-must-complete-before-collector");
    if (now() >= deadline) fail("h1-connect-prerequisite-deadline");
    await (dependencies.sleep ?? sleep)(Math.min(250, deadline - now()));
  }
}

function publish(path, value) {
  const temporary = `${path}.pending-${randomUUID()}`;
  writeFileSync(temporary, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
  try { linkSync(temporary, path); } finally { unlinkSync(temporary); }
}

export function requireRetainedForeground(state) {
  if (!state.inputTTY || !state.outputTTY || !pidValid(state.processGroup) ||
      state.processGroup !== state.foregroundGroup) fail("retained-foreground-pty-required");
}

function foregroundState() {
  const result = spawnSync("ps", ["-o", "pgid=,tpgid=", "-p", String(process.pid)],
    { encoding: "utf8", timeout: 2000, maxBuffer: 1024 });
  const fields = result.status === 0 && !result.signal && !result.error
    ? result.stdout.trim().split(/\s+/).map(Number) : [];
  return { inputTTY: process.stdin.isTTY === true, outputTTY: process.stdout.isTTY === true,
    processGroup: fields.length === 2 ? fields[0] : 0, foregroundGroup: fields[1] };
}

function readOwner(path, isLive) {
  if (!existsSync(path)) fail("collector-session-owner-required");
  const owner = JSON.parse(readFileSync(path, "utf8"));
  if (owner.schema !== 1 || owner.type !== "collector-session" || owner.state !== "running" ||
      typeof owner.ownerId !== "string" || !/^[a-f0-9-]{36}$/.test(owner.ownerId) ||
      !pidValid(owner.ownerPid) || !pidValid(owner.collectorPid) || owner.ownerPid === owner.collectorPid ||
      typeof owner.telemetry !== "string" || typeof owner.label !== "string") fail("invalid-collector-session-owner");
  if (existsSync(`${path}.terminal.json`)) fail("collector-session-already-finished");
  if (!isLive(owner.ownerPid) || !isLive(owner.collectorPid)) fail("collector-session-owner-not-live");
  return owner;
}

// Dedicated retained AM supervisor. Only this mode launches instrumentation;
// bind/check-role are read-only. Normal exit/handled signals join the child;
// liveness checks reject an abruptly lost supervisor even before terminal JSON.
export async function runInstrumentationSession(options, dependencies = {}) {
  try { return await runInstrumentationSessionOwned(options, dependencies); }
  catch (error) {
    if (options["credential-ownership"]) {
      // This rollback is prospective and pre-handoff only. Missing proof,
      // crashes, live targets and any AM handoff leave credentials untouched.
      const result = rollbackCredentialSetup({ ...options, ownership: options["credential-ownership"] }, dependencies);
      dependencies.onCredentialRollback?.(result);
    }
    throw error;
  }
}

async function runInstrumentationSessionOwned(options, dependencies = {}) {
  const component = options.component ?? runnerComponents[0];
  if (!options.serial || !commandIdValid(options.label) || !commandIdValid(options["build-id"]) ||
      !runnerComponents.includes(component) || !options.owner || !options.stdout || !options.stderr) {
    fail("explicit-instrumentation-session-required");
  }
  const paths = [options.owner, options.stdout, options.stderr, `${options.owner}.ready.json`, `${options.owner}.terminal.json`]
    .map((path) => resolve(path));
  let directoryBinding;
  const checkDirectory = () => {
    try {
      directoryBinding ??= prepareArtifactDirectory(options["artifact-dir"] ?? dirname(options.owner), false, dependencies.directory);
      requireArtifactPaths(directoryBinding, paths, dependencies.directory);
    } catch (error) { fail(`${artifactDirectoryReason(error)}-no-spawn`); }
  };
  // This check deliberately precedes foreground/ps/open/spawn. A missing leaf
  // is failed setup, never an invitation to recreate or silently redirect it.
  checkDirectory();
  if (options["credential-ownership"]) {
    try { requireArtifactPaths(directoryBinding, [options["credential-ownership"]], dependencies.directory); }
    catch (error) { fail(`${artifactDirectoryReason(error)}-no-spawn`); }
  }
  if (!options["native-inputs"]) fail("verified-native-input-proof-required-no-spawn");
  try { requireArtifactPaths(directoryBinding, [options["native-inputs"]], dependencies.directory); }
  catch (error) { fail(`${artifactDirectoryReason(error)}-no-spawn`); }
  if (new Set(paths).size !== paths.length || paths.some(existsSync)) fail("fresh-instrumentation-artifacts-required");
  const foreground = (dependencies.foreground ?? foregroundState)();
  requireRetainedForeground(foreground);
  const supervisorIdentity = liveHostOwner(process.pid, options.serial, dependencies, true);
  let nativeInputs;
  try { nativeInputs = (dependencies.verifyNativeInputs ?? requireVerifiedNativeInputs)(options["native-inputs"], options["build-id"]); }
  catch (error) { fail(`${nativeProvenanceReason(error)}-no-spawn`); }
  let output;
  let errors;
  let child;
  let spawnAttempted = false;
  let closed;
  let interrupted = false;
  const instrumentationOwnerId = randomUUID();
  const signals = dependencies.signals ?? process;
  const handlers = new Map(["SIGINT", "SIGTERM", "SIGHUP"].map((signal) => [signal, () => {
    interrupted = true;
    child?.kill(signal);
  }]));
  try {
    checkDirectory();
    output = openSync(options.stdout, "wx", 0o600);
    errors = openSync(options.stderr, "wx", 0o600);
    checkDirectory();
    if (options["credential-ownership"]) {
      handoffCredentialOwnership({ ...options, ownership: options["credential-ownership"],
        "instrumentation-owner": options.owner, "session-id": instrumentationOwnerId }, nativeInputs, dependencies);
    }
    spawnAttempted = true;
    child = (dependencies.spawn ?? spawn)("adb", ["-s", options.serial, "shell", "am", "instrument", "-w", "-r",
      "-e", "class", physicalClass, "-e", "acceptanceBuildId", options["build-id"], component],
    { stdio: ["ignore", output, errors] });
    for (const [signal, handler] of handlers) signals.on(signal, handler);
    closed = new Promise((finish) => {
      child.once("error", () => finish({ exitCode: null, signal: null }));
      child.once("close", (exitCode, signal) => finish({ exitCode, signal }));
    });
    const spawned = await new Promise((finish) => {
      child.once("spawn", () => finish(true));
      child.once("error", () => finish(false));
    });
    if (!spawned || !pidValid(child.pid)) fail("instrumentation-adb-not-started");
    const owner = { schema: 1, type: "instrumentation-session", state: "running", ownerId: instrumentationOwnerId,
      label: options.label, serialHash: digest(options.serial), component, className: physicalClass, targetPackage: packageName,
      nativeInputHash: nativeInputs.inputHash, nativeBuildOwner: nativeInputs.buildOwner,
      supervisorPid: process.pid, supervisorIdentity, adbPid: child.pid,
      adbIdentity: liveHostOwner(child.pid, options.serial, dependencies), foreground, startedHostTimeUnixMs: Date.now() };
    requireLiveInstrumentation(owner, options.serial, dependencies);
    publish(options.owner, owner);
    const result = await closed;
    const complete = result.exitCode === 0 && result.signal === null && !interrupted;
    publish(`${options.owner}.terminal.json`, { ...owner, ...result, interrupted,
      state: complete ? "complete" : "failed", completedHostTimeUnixMs: Date.now() });
    return complete ? 0 : 2;
  } catch (error) {
    child?.kill("SIGTERM");
    if (closed) await closed;
    if (!child) {
      checkDirectory();
      if (!spawnAttempted && !(error instanceof SessionError)) fail("instrumentation-artifact-open-failed-no-spawn");
    }
    throw error;
  } finally {
    for (const [signal, handler] of handlers) signals.off(signal, handler);
    if (output !== undefined) closeSync(output);
    if (errors !== undefined) closeSync(errors);
  }
}

// Run only in the foreground of a retained PTY. Redirection belongs to the
// child, so the owner itself keeps its terminal and the executor stays live.
export async function runCollectorSession(options, dependencies = {}) {
  const foreground = (dependencies.foreground ?? foregroundState)();
  requireRetainedForeground(foreground);
  const capture = parseCaptureArgs(options.captureArgs);
  if (!capture.serial || !capture.label || !capture.output || !capture.stopFile || !capture.durationSeconds || capture.help) {
    fail("explicit-bounded-collector-required");
  }
  validateRoleOptions(options, capture);
  const paths = [options.owner, options.stdout, options.stderr, capture.output, capture.stopFile,
    `${options.owner}.terminal.json`].map((path) => resolve(path));
  if (new Set(paths).size !== paths.length || paths.some(existsSync)) fail("fresh-collector-artifacts-required");
  const sessionRole = options["session-mode"] === undefined ? undefined : await checkSessionRole(
    { ...options, serial: capture.serial }, dependencies, false);
  const output = openSync(options.stdout, "wx", 0o600);
  let errors;
  let child;
  let closed;
  let interrupted = false;
  const signals = dependencies.signals ?? process;
  const handlers = new Map(["SIGINT", "SIGTERM", "SIGHUP"].map((signal) => [signal, () => {
    interrupted = true;
    child?.kill(signal);
  }]));
  try {
    errors = openSync(options.stderr, "wx", 0o600);
    child = (dependencies.spawn ?? spawn)(process.execPath, [captureScript, ...options.captureArgs],
      { stdio: ["ignore", output, errors] });
    for (const [signal, handler] of handlers) signals.on(signal, handler);
    closed = new Promise((finish) => {
      child.once("error", () => finish({ exitCode: null, signal: null }));
      child.once("close", (exitCode, signal) => finish({ exitCode, signal }));
    });
    if (!pidValid(child.pid)) fail("collector-process-not-started");
    const owner = { schema: 1, type: "collector-session", state: "running", ownerId: randomUUID(), ownerPid: process.pid,
      collectorPid: child.pid, processGroup: foreground.processGroup, startedHostTimeUnixMs: Date.now(),
      label: capture.label, telemetry: resolve(capture.output), ...(sessionRole ? { sessionRole } : {}) };
    publish(options.owner, owner);
    const result = await closed;
    const complete = result.exitCode === 0 && result.signal === null && !interrupted;
    publish(`${options.owner}.terminal.json`, { ...owner, ...result, interrupted,
      state: complete ? "complete" : "failed", completedHostTimeUnixMs: Date.now() });
    return complete ? 0 : 2;
  } catch (error) {
    child?.kill("SIGTERM");
    if (closed) await closed;
    throw error;
  } finally {
    for (const [signal, handler] of handlers) signals.off(signal, handler);
    closeSync(output);
    if (errors !== undefined) closeSync(errors);
  }
}

// A fresh eligible prefix is not proof that its launcher survived. Require
// both recorded processes live before and after the existing collector check.
// Poll only startup telemetry; a dead/finished owner is never retried/replaced.
export async function checkCollectorSession(options, dependencies = {}) {
  const isLive = dependencies.isLive ?? processIsLive;
  const now = dependencies.now ?? performance.now.bind(performance);
  const deadline = now() + options.timeoutMs;
  const initial = readOwner(options.owner, isLive);
  const sameOwner = () => {
    const current = readOwner(options.owner, isLive);
    for (const key of ["ownerId", "ownerPid", "collectorPid", "telemetry", "label"]) {
      if (current[key] !== initial[key]) fail("collector-session-owner-replaced");
    }
    return current;
  };
  for (;;) {
    const owner = sameOwner();
    try {
      requireLiveCollector({ pid: owner.collectorPid, path: owner.telemetry }, owner.label, undefined,
        { isLive, wallNow: dependencies.wallNow });
      sameOwner();
      return owner.collectorPid;
    } catch (error) {
      // Recheck ownership even when a partial/missing first sample caused the
      // telemetry check to fail. Never wait out a terminated launcher.
      sameOwner();
      if (now() >= deadline) fail("collector-session-ready-deadline");
      await (dependencies.sleep ?? sleep)(100);
    }
  }
}

export function parseArgs(argv) {
  const [mode, ...rest] = argv;
  const separator = rest.indexOf("--");
  const pairs = separator < 0 ? rest : rest.slice(0, separator);
  const roleArguments = ["session-mode", "connect-command-id", "instrumentation-owner"];
  const allowed = mode === "run" ? ["owner", "stdout", "stderr", ...roleArguments] :
    mode === "check" ? ["owner", "timeout-ms"] : mode === "check-role" ? ["serial", ...roleArguments] :
      mode === "run-instrumentation" ? ["owner", "stdout", "stderr", "serial", "label", "build-id", "component", "artifact-dir", "native-inputs", "credential-ownership"] :
        mode === "bind-instrumentation-ready" ? ["owner", "serial"] : [];
  if (!allowed.length) fail("run-check-or-check-role-required");
  const options = { mode, captureArgs: separator < 0 ? [] : rest.slice(separator + 1) };
  for (let i = 0; i < pairs.length; i += 2) {
    const key = pairs[i]?.slice(2);
    if (!pairs[i]?.startsWith("--") || !allowed.includes(key) || !pairs[i + 1] || pairs[i + 1].startsWith("--") ||
        options[key] !== undefined) fail("invalid-session-arguments");
    options[key] = pairs[i + 1];
  }
  if (["run-instrumentation", "bind-instrumentation-ready"].includes(mode)) {
    if (!options.serial || !options.owner || options.captureArgs.length ||
        (mode === "run-instrumentation" && (!options.stdout || !options.stderr || !options["native-inputs"] || !commandIdValid(options.label) ||
          !commandIdValid(options["build-id"]) || !runnerComponents.includes(options.component ?? runnerComponents[0])))) {
      fail("explicit-instrumentation-session-required");
    }
    return options;
  }
  if (mode === "check-role") {
    if (!options.serial || !options["session-mode"] || options.captureArgs.length) fail("serial-and-session-role-required");
    validateRoleOptions(options);
    return options;
  }
  if (!options.owner || (mode === "run" ? !options.stdout || !options.stderr || !options.captureArgs.length : options.captureArgs.length)) {
    fail("explicit-session-artifacts-required");
  }
  if (mode === "run") validateRoleOptions(options);
  options.timeoutMs = Number(options["timeout-ms"] ?? 15_000);
  if (!Number.isFinite(options.timeoutMs) || options.timeoutMs <= 0 || options.timeoutMs > 30_000) fail("invalid-session-deadline");
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    if (options.mode === "run") process.exitCode = await runCollectorSession(options);
    else if (options.mode === "run-instrumentation") process.exitCode = await runInstrumentationSession(options);
    else if (options.mode === "bind-instrumentation-ready") process.stdout.write(`${JSON.stringify(bindInstrumentationReady(options))}\n`);
    else if (options.mode === "check-role") process.stdout.write(`${JSON.stringify(await checkSessionRole(options))}\n`);
    else process.stdout.write(`${await checkCollectorSession(options)}\n`);
  } catch (error) {
    // No serials, command arguments, paths or raw device diagnostics in errors.
    process.stderr.write(formatSessionFailure(error));
    process.exitCode = 2;
  }
}
