#!/usr/bin/env node

// Private diagnostic composition only. Existing owners still own traffic,
// collectors, deadlines and joins. These six cold commands never qualify PERF.
import { createHash } from "node:crypto";
import { lstatSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join } from "node:path";
import { pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { runDiagnosticCommand } from "./physical_diagnostic_command.mjs";
import { copyDiagnosticArtifact } from "./physical_diagnostic_copy.mjs";
import { diagnosticIdentity, diagnosticSessionValid } from "./physical_diagnostic_identity.mjs";
import { recordChild } from "./physical_workload_receipt.mjs";

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
  try { process.exitCode = (await runIoLoopFastBoundary(parseArgs(process.argv.slice(2)))).exitCode; }
  catch { process.stderr.write("native-loop diagnostic boundary unavailable\n"); process.exitCode = 2; }
}

