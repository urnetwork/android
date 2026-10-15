#!/usr/bin/env node

// Private diagnostic composition only. Existing owners still own traffic,
// collectors, deadlines and joins. These six cold commands never qualify PERF.
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { existsSync, lstatSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { basename, dirname, isAbsolute, join } from "node:path";
import { pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { runDiagnosticCommand } from "./physical_diagnostic_command.mjs";
import { copyDiagnosticArtifact } from "./physical_diagnostic_copy.mjs";
import { diagnosticIdentity, diagnosticSessionValid } from "./physical_diagnostic_identity.mjs";
import { recordChild, processIsLive, requireRetainedWorkloadForeground } from "./physical_workload_receipt.mjs";

const digest = bytes => createHash("sha256").update(bytes).digest("hex");
const fail = reason => { throw new Error(reason); };
const decimal = value => {
  if (typeof value !== "string" || !/^(0|[1-9][0-9]{0,39})$/.test(value)) fail("ioloop-integer-text-required");
  return BigInt(value);
};
function retained(path, bound = 65536) {
  const file = lstatSync(path);
  if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600 || file.size > bound) {
    fail("ioloop-private-bounded-input-required");
  }
  const bytes = readFileSync(path);
  return { value: JSON.parse(bytes), sha256: digest(bytes) };
}
function publish(path, value) {
  writeFileSync(path, JSON.stringify(value) + "\n", { flag: "wx", mode: 0o600 });
}
function identity(owner, ready, serial) {
  if (owner?.ioLoopDiagnostic !== true || owner.qualificationEligible !== false ||
      owner.serialHash !== digest(serial) || !diagnosticSessionValid(owner.ownerId) ||
      typeof owner.launchId !== "string" || !/^[a-f0-9]{32}$/.test(owner.launchId) || typeof owner.buildId !== "string" || !owner.buildId ||
      ready?.ownerId !== owner.ownerId || ready.launchId !== owner.launchId || ready.buildId !== owner.buildId ||
      ready.serialHash !== owner.serialHash || ready.ioLoopDiagnostic !== true || ready.qualificationEligible !== false ||
      !diagnosticSessionValid(ready.targetSessionId) || !Number.isSafeInteger(ready.targetPid) || ready.targetPid <= 0) {
    fail("ioloop-explicit-owner-ready-binding-required");
  }
}
function anchorValue(value, receipt, owner, ready) {
  if (value?.schema !== 1 || value.qualificationEligible !== false ||
      value.sessionId !== ready.targetSessionId || value.buildId !== owner.buildId || value.pid !== ready.targetPid ||
      receipt.initial?.sessionId !== owner.ownerId || receipt.status?.sessionId !== owner.ownerId ||
      receipt.initial?.pid !== ready.targetPid || receipt.status?.pid !== ready.targetPid ||
      receipt.qualificationEligible !== false || receipt.clockBracket?.source !== "host-hrtime-v1") {
    fail("ioloop-clock-identity-mismatch");
  }
  decimal(value.origin_unix_ns); decimal(value.elapsed_ns); decimal(value.wall_unix_ns);
  if (decimal(receipt.clockBracket.beforeNs) > decimal(receipt.clockBracket.afterNs)) fail("ioloop-host-clock-regressed");
}

// All clocks below execute in this one process. Device elapsed values are NOT
// subtracted from host hrtime or from browser wall time. The two request/reply
// brackets only bound where each device anchor occurred.
export async function runIoLoopFastBoundary(options, dependencies = {}) {
  if (!["fast-1", "fast-2", "fast-3"].includes(options.name) || !options.serial ||
      !isAbsolute(options.instrumentationOwner ?? "") || !isAbsolute(options.ownerPath ?? "") ||
      !Array.isArray(options.command) || !options.command.length ||
      !Number.isSafeInteger(options.timeoutMs) || options.timeoutMs <= 0 || options.timeoutMs > 2147483647) {
    fail("ioloop-exact-fast-child-arguments-required");
  }
  const instrumentationBinding = prepareArtifactDirectory(dirname(options.instrumentationOwner));
  const readyPath = options.instrumentationOwner + ".ready.json";
  requireArtifactPaths(instrumentationBinding, [options.instrumentationOwner, readyPath]);
  const ownerInput = retained(options.instrumentationOwner), readyInput = retained(readyPath);
  const owner = ownerInput.value, ready = readyInput.value;
  identity(owner, ready, options.serial);
  const workloadBinding = prepareArtifactDirectory(dirname(options.ownerPath));
  requireArtifactPaths(workloadBinding, [options.ownerPath]);
  const workload = retained(options.ownerPath).value;
  if (!diagnosticSessionValid(workload.ownerId) || workload.serialHash !== owner.serialHash ||
      !Array.isArray(workload.children) || !workload.children.includes(options.name)) fail("ioloop-workload-owner-mismatch");
  const output = join(workloadBinding.directory, options.name + ".ioloop.json");
  const attempt = join(workloadBinding.directory, options.name + ".ioloop.attempt.json");
  requireArtifactPaths(workloadBinding, [attempt, output]);
  publish(attempt, { schema: 1, type: "ioloop-fast-attempt", qualificationEligible: false });
  const monotonicNanos = dependencies.monotonicNanos ?? (() => process.hrtime.bigint());
  const report = { schema: 1, type: "ioloop-fast-boundaries", qualificationEligible: false, complete: false,
    name: options.name, workloadOwnerId: workload.ownerId, instrumentationOwnerId: owner.ownerId,
    ownerSha256: ownerInput.sha256, readySha256: readyInput.sha256, clockSource: "same-process-host-hrtime-v1" };
  let stage = "before-anchor";
  const anchor = async edge => {
    const label = workload.ownerId + "-" + options.name + "-" + edge;
    const receiptPath = join(instrumentationBinding.directory, label + ".command.json");
    const artifactPath = join(instrumentationBinding.directory, label + ".clock.json");
    requireArtifactPaths(instrumentationBinding, [receiptPath, artifactPath]);
    const receipt = await (dependencies.command ?? runDiagnosticCommand)({
      serial: options.serial, owner: options.instrumentationOwner, verb: "ioloop-clock",
      label, "command-id": label, output: receiptPath,
    }, { ...dependencies.commandDependencies, monotonicNanos });
    (dependencies.copy ?? copyDiagnosticArtifact)({ serial: options.serial, receipt: receiptPath, output: artifactPath },
      dependencies.copyDependencies);
    const receiptInput = retained(receiptPath, 4096), artifact = retained(artifactPath, 4096);
    if (JSON.stringify(receiptInput.value) !== JSON.stringify(receipt)) fail("ioloop-clock-receipt-changed");
    const wire = diagnosticIdentity(owner.ownerId, "ioloop-clock", label, label);
    if (JSON.stringify(receipt.wire) !== JSON.stringify(wire)) fail("ioloop-clock-wire-mismatch");
    anchorValue(artifact.value, receipt, owner, ready);
    return { commandSha256: receiptInput.sha256, artifactSha256: artifact.sha256,
      host: receipt.clockBracket, device: artifact.value };
  };
  try {
    report.before = await anchor("before");
    stage = "owned-child";
    // The existing function enforces ordered child, collector continuity and
    // real child closure. A throw is UNKNOWN; do not issue a post command over
    // a possibly unjoined traffic child.
    const child = await (dependencies.child ?? recordChild)({
      ownerPath: options.ownerPath, name: options.name, command: options.command, "timeout-ms": options.timeoutMs,
    }, { ...dependencies.childDependencies, monotonicNanos });
    report.child = child;
    stage = "after-anchor";
    report.after = await anchor("after");
    if (report.before.device.origin_unix_ns !== report.after.device.origin_unix_ns ||
        decimal(report.before.device.elapsed_ns) > decimal(report.after.device.elapsed_ns) ||
        decimal(report.before.host.afterNs) > decimal(child.startedHostMonotonicNs) ||
        decimal(child.startedHostMonotonicNs) > decimal(child.completedHostMonotonicNs) ||
        decimal(child.completedHostMonotonicNs) > decimal(report.after.host.beforeNs)) fail("ioloop-boundary-order-mismatch");
    report.childSha256 = retained(join(workloadBinding.directory, options.name + ".complete.json")).sha256;
    report.complete = true;
    report.exitCode = child.exitCode;
  } catch {
    report.failureStage = stage;
    report.exitCode = 2;
  }
  requireArtifactPaths(workloadBinding, [output]);
  publish(output, report);
  return report;
}

// Pure step binding for the EXISTING afterJoin executor. This does not assert
// owner join, execute adb, or grant authority. The enclosing owner must retain
// the actual AM/host terminal and group-absence evidence first, even on failure.
// It can then copy an incomplete native receipt without turning it into PASS.
export function ioLoopTerminalCopyStep({ serial, owner, ready, teardown, output }) {
  identity(owner, ready, serial);
  const name = "physical-ioloop-" + ready.targetSessionId + ".json";
  if (teardown?.type !== "physical-memory-teardown" || teardown.schemaVersion !== 1 ||
      teardown.sessionId !== ready.targetSessionId || teardown.buildId !== owner.buildId ||
      teardown.pid !== ready.targetPid || teardown.ioLoopDiagnosticReceiptName !== name ||
      !isAbsolute(output ?? "")) fail("ioloop-terminal-artifact-binding-required");
  return { id: "ioloop-final", kind: "command", command: "adb", qualificationEligible: false, timeoutMs: 10000,
    args: ["-s", serial, "exec-out", "run-as", "com.bringyour.network", "cat", "files/acceptance/" + name],
    stdout: output, expected: { schema: 1, sessionId: ready.targetSessionId, buildId: owner.buildId,
      pid: ready.targetPid, qualification_eligible: false } };
}

export function validateIoLoopTerminalCopy(bytes, step) {
  if (!Buffer.isBuffer(bytes) || bytes.length === 0 || bytes.length > 65536) fail("ioloop-terminal-size-invalid");
  const value = JSON.parse(bytes.toString("utf8"));
  if (Object.entries(step.expected).some(([key, expected]) => value[key] !== expected) ||
      value.capacity !== 16 || !Array.isArray(value.loops) || value.loops.length > 16 ||
      typeof value.complete !== "boolean") fail("ioloop-terminal-identity-invalid");
  decimal(value.origin_unix_ns); decimal(value.attempts); decimal(value.refused);
  // Copy binding is not a coverage validator. Preserve the producer's claim
  // and both fences separately; none of these fields grants qualification.
  return { schema: 1, type: "ioloop-terminal-copy", qualificationEligible: false,
    sha256: digest(bytes), bytes: bytes.length, producerComplete: value.complete,
    deviceJoined: value.deviceJoined === true, constructionStopped: value.constructionStopped === true };
}

// Called only after the Go session owner has joined the actual instrumentation
// group. This validates the retained terminal identity, not process absence.
export function copyIoLoopTerminal(options, dependencies = {}) {
  if (!options.serial || !isAbsolute(options.owner ?? "") || !isAbsolute(options.teardown ?? "") ||
      !isAbsolute(options.output ?? "")) fail("ioloop-terminal-arguments-required");
  const ownerBinding = prepareArtifactDirectory(dirname(options.owner));
  requireArtifactPaths(ownerBinding, [options.owner, options.owner + ".ready.json", options.owner + ".terminal.json"]);
  const owner = retained(options.owner).value;
  const ready = retained(options.owner + ".ready.json").value;
  const terminal = retained(options.owner + ".terminal.json").value;
  identity(owner, ready, options.serial);
  if (terminal.type !== "instrumentation-session" || !["complete", "failed"].includes(terminal.state) ||
      Object.entries(owner).some(([key, value]) => key !== "state" && JSON.stringify(terminal[key]) !== JSON.stringify(value)) ||
      !Number.isSafeInteger(owner.startedHostTimeUnixMs) || !Number.isSafeInteger(ready.hostTimeUnixMs) ||
      ready.hostTimeUnixMs < owner.startedHostTimeUnixMs ||
      !Number.isSafeInteger(terminal.completedHostTimeUnixMs) || terminal.completedHostTimeUnixMs < ready.hostTimeUnixMs ||
      !Number.isSafeInteger(terminal.exitCode) || terminal.exitCode < 0 || terminal.exitCode > 255 ||
      typeof terminal.interrupted !== "boolean" || !(terminal.signal === null || typeof terminal.signal === "string")) {
    fail("ioloop-terminal-owner-mismatch");
  }
  const outputBinding = prepareArtifactDirectory(dirname(options.output));
  requireArtifactPaths(outputBinding, [options.output]);
  const step = ioLoopTerminalCopyStep({ ...options, owner, ready, teardown: retained(options.teardown).value });
  const result = (dependencies.adb ?? ((args, limits) => spawnSync("adb", args, limits)))(
    step.args, { timeout: step.timeoutMs, maxBuffer: 65536 });
  if (result?.status !== 0 || result.signal || result.error || !Buffer.isBuffer(result.stdout)) {
    fail("ioloop-terminal-copy-failed");
  }
  const report = validateIoLoopTerminalCopy(result.stdout, step);
  requireArtifactPaths(outputBinding, [options.output]);
  writeFileSync(options.output, result.stdout, { flag: "wx", mode: 0o600 });
  return { ...report, instrumentationComplete: terminal.state === "complete" && terminal.exitCode === 0 &&
    terminal.signal === null && terminal.interrupted === false };
}

// The terminal operation has fixed paths and no arbitrary command argument.
export function parseTerminalArgs(argv) {
  const options = {};
  const keys = { "--serial": "serial", "--instrumentation-owner": "owner", "--teardown": "teardown", "--output": "output" };
  for (let index = 0; index < argv.length; index += 2) {
    const key = keys[argv[index]];
    if (!key || options[key] !== undefined || !argv[index + 1] || argv[index + 1].startsWith("--")) fail("ioloop-terminal-arguments-required");
    options[key] = argv[index + 1];
  }
  if (Object.keys(options).length !== 4) fail("ioloop-terminal-arguments-required");
  return options;
}

// Joining the outer pty is insufficient: the existing workload owner and Fast
// wrapper each create a separate child group. Consume their actual immutable
// join evidence even for a failed diagnostic, never manufacture a completion.
export function requireIoLoopWorkloadJoined(options, dependencies = {}) {
  if (!options.serial || !isAbsolute(options.output ?? "") || !Number.isSafeInteger(options.ownerPid) ||
      options.ownerPid <= 0 || !/^h1-ioloop-[a-f0-9]{32}$/.test(options.label ?? "")) fail("ioloop-workload-arguments-required");
  const binding = prepareArtifactDirectory(dirname(options.output));
  requireArtifactPaths(binding, [options.output, options.output + ".failed.json"]);
  const names = readdirSync(binding.directory).filter(name => name.startsWith(basename(options.output) + ".owner-"));
  if (names.length !== 1) fail("ioloop-one-workload-owner-required");
  const ownerBinding = prepareArtifactDirectory(join(binding.directory, names[0]));
  const ownerPath = join(ownerBinding.directory, "owner.json");
  requireArtifactPaths(ownerBinding, [ownerPath]);
  const owner = retained(ownerPath).value;
  const isLive = dependencies.isLive ?? processIsLive;
  if (owner.schema !== 1 || owner.type !== "workload-owner" || !diagnosticSessionValid(owner.ownerId) ||
      owner.ownerPid !== options.ownerPid || owner.label !== options.label || owner.serialHash !== digest(options.serial) ||
      names[0] !== basename(options.output) + ".owner-" + owner.ownerId ||
      JSON.stringify(owner.children) !== '["fast-1"]' || !Number.isSafeInteger(owner.startedHostTimeUnixMs) ||
      owner.retainedOwner?.schema !== 1 || !/^[a-f0-9]{64}$/.test(owner.retainedOwner.identity ?? "")) {
    fail("ioloop-workload-owner-mismatch");
  }
  requireRetainedWorkloadForeground(owner.retainedOwner.foreground);
  if (owner.retainedOwner.foreground.processGroup !== owner.ownerPid || isLive(owner.ownerPid) || isLive(-owner.ownerPid)) {
    fail("ioloop-workload-owner-not-joined");
  }
  const failedJoin = (value, name) => {
    if (value.schema !== 1 || value.type !== (name ? "workload-child-failure" : "workload-owner-failure") ||
        value.ownerId !== owner.ownerId || value.state !== "failed" || (name && value.name !== name) ||
        !Number.isSafeInteger(value.observedHostTimeUnixMs) || value.observedHostTimeUnixMs < owner.startedHostTimeUnixMs ||
        value.result?.observed !== true || value.result.closed !== true || value.result.joined !== true ||
        value.result.joinTimedOut !== false) fail("ioloop-failed-workload-join-unproved");
  };
  const complete = existsSync(options.output), failed = existsSync(options.output + ".failed.json");
  if (complete === failed) fail("ioloop-one-workload-terminal-required");
  if (failed) failedJoin(retained(options.output + ".failed.json").value);
  else {
    const terminal = retained(options.output).value;
    if (Object.entries(owner).some(([key, value]) => JSON.stringify(terminal[key]) !== JSON.stringify(value)) ||
        terminal.state !== "complete" || terminal.exitCode !== 0 || terminal.signal !== null || terminal.interrupted !== false ||
        !Number.isSafeInteger(terminal.completedHostTimeUnixMs) || terminal.completedHostTimeUnixMs < owner.startedHostTimeUnixMs ||
        !Number.isSafeInteger(terminal.processGroup) || terminal.processGroup <= 0 || isLive(-terminal.processGroup)) {
      fail("ioloop-workload-body-not-joined");
    }
  }
  const prefix = join(ownerBinding.directory, "fast-1");
  requireArtifactPaths(ownerBinding, [prefix + ".started.json", prefix + ".complete.json", prefix + ".failed.json", prefix + ".ioloop.json"]);
  const childComplete = existsSync(prefix + ".complete.json"), childFailed = existsSync(prefix + ".failed.json");
  const childStarted = existsSync(prefix + ".started.json");
  if (!childStarted && !childComplete && !childFailed) {
    // Before-anchor refusal runs no child. Only the bound wrapper's terminal
    // report plus the independently joined body can establish that boundary.
    const boundary = retained(prefix + ".ioloop.json").value;
    if (!failed || boundary.type !== "ioloop-fast-boundaries" || boundary.schema !== 1 || boundary.qualificationEligible !== false ||
        boundary.workloadOwnerId !== owner.ownerId || boundary.name !== "fast-1" || boundary.complete !== false ||
        boundary.exitCode !== 2 || boundary.failureStage !== "before-anchor") fail("ioloop-unstarted-fast-unproved");
  } else {
    if (!childStarted || childComplete === childFailed) fail("ioloop-one-fast-terminal-required");
    const started = retained(prefix + ".started.json").value;
    if (!Number.isSafeInteger(started.wrapperPid) || started.wrapperPid <= 0 || isLive(started.wrapperPid) ||
        !Number.isSafeInteger(started.startedHostTimeUnixMs) || started.startedHostTimeUnixMs < owner.startedHostTimeUnixMs) {
      fail("ioloop-fast-wrapper-not-joined");
    }
    if (childFailed) failedJoin(retained(prefix + ".failed.json").value, "fast-1");
    else {
      const child = retained(prefix + ".complete.json").value;
      if (child.schema !== 1 || child.type !== "workload-child" || child.ownerId !== owner.ownerId || child.name !== "fast-1" ||
          child.state !== "complete" || !Number.isSafeInteger(child.exitCode) || child.exitCode < 0 || child.exitCode >= 128 ||
          child.signal !== null || child.interrupted !== false || child.wrapperPid !== started.wrapperPid ||
          child.startedHostTimeUnixMs !== started.startedHostTimeUnixMs || !Number.isSafeInteger(child.completedHostTimeUnixMs) ||
          child.completedHostTimeUnixMs < child.startedHostTimeUnixMs || !Number.isSafeInteger(child.processGroup) ||
          child.processGroup <= 0 || isLive(-child.processGroup)) fail("ioloop-fast-process-group-not-joined");
    }
  }
  return { type: "ioloop-workload-join", schema: 1, qualificationEligible: false,
    ownerId: owner.ownerId, ownerPid: owner.ownerPid, joined: true, failed, childStarted };
}

// Only the Go owner's one fixed diagnostic workload is selectable here.
export function parseWorkloadJoinArgs(argv) {
  const options = {};
  const keys = { "--serial": "serial", "--output": "output", "--owner-pid": "ownerPid", "--label": "label" };
  for (let index = 0; index < argv.length; index += 2) {
    const key = keys[argv[index]];
    if (!key || options[key] !== undefined || !argv[index + 1] || argv[index + 1].startsWith("--")) fail("ioloop-workload-arguments-required");
    options[key] = key === "ownerPid" ? Number(argv[index + 1]) : argv[index + 1];
  }
  if (Object.keys(options).length !== 4) fail("ioloop-workload-arguments-required");
  return options;
}

export function parseArgs(argv) {
  const split = argv.indexOf("--"), prefix = split < 0 ? argv : argv.slice(0, split);
  const value = { command: split < 0 ? [] : argv.slice(split + 1), ownerPath: process.env.URNETWORK_PERF_WORKLOAD_OWNER };
  const keys = { "--serial": "serial", "--instrumentation-owner": "instrumentationOwner", "--name": "name", "--timeout-ms": "timeoutMs" };
  for (let i = 0; i < prefix.length; i += 2) {
    const key = keys[prefix[i]];
    if (!key || value[key] !== undefined || !prefix[i + 1] || prefix[i + 1].startsWith("--")) fail("ioloop-explicit-arguments-required");
    value[key] = key === "timeoutMs" ? Number(prefix[i + 1]) : prefix[i + 1];
  }
  return value;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv[2] === "terminal-copy") {
      process.stdout.write(JSON.stringify(copyIoLoopTerminal(parseTerminalArgs(process.argv.slice(3)))) + "\n");
    } else if (process.argv[2] === "workload-join") {
      process.stdout.write(JSON.stringify(requireIoLoopWorkloadJoined(parseWorkloadJoinArgs(process.argv.slice(3)))) + "\n");
    } else process.exitCode = (await runIoLoopFastBoundary(parseArgs(process.argv.slice(2)))).exitCode;
  }
  catch { process.stderr.write("native-loop diagnostic boundary unavailable\n"); process.exitCode = 2; }
}

