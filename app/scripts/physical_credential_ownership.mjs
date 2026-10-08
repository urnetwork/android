#!/usr/bin/env node

// Private prospective setup ownership, never adoption of a pre-existing file.
// Assumes the runbook's single host/session and no concurrent same-UID mutator.
// A receipt alone (or a recycled inode) never authorizes credential removal.
import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, fstatSync, linkSync, lstatSync, openSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { requireVerifiedNativeInputs } from "./physical_native_provenance.mjs";

const PACKAGE = "com.bringyour.network";
const DESTINATION = "files/acceptance/credentials";
const hash = value => createHash("sha256").update(value).digest("hex");
const validHash = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
const validLabel = value => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(value);
const validToken = value => typeof value === "string" && /^[a-f0-9]{32}$/.test(value);
const validSession = value => typeof value === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value);
const quote = value => `'${value.replaceAll("'", "'\\''")}'`;
class OwnershipError extends Error {}
const fail = reason => { throw new OwnershipError(reason); };
export const credentialOwnershipReason = error => error instanceof OwnershipError ? error.message : "credential-ownership-unavailable";

function privateText(path, maxBytes = 16_384) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid() ||
      (stat.mode & 0o7777) !== 0o600 || stat.nlink !== 1 || stat.size > maxBytes) fail("private-credential-ownership-required");
  return readFileSync(path, "utf8");
}

const privateRead = path => JSON.parse(privateText(path));

function publish(path, value) {
  const binding = prepareArtifactDirectory(dirname(path));
  requireArtifactPaths(binding, [path]);
  const pending = `${path}.pending-${randomUUID()}`;
  writeFileSync(pending, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
  try { requireArtifactPaths(binding, [path]); linkSync(pending, path); }
  finally { unlinkSync(pending); }
}

const contextHash = value => hash(JSON.stringify([value.serialHash, value.label, value.buildId,
  value.nativeInputHash, value.nativeBuildOwner, value.token]));

export function prepareCredentialOwnership(options, dependencies = {}) {
  const keys = ["ownership", "native-inputs", "label", "build-id"];
  if (!keys.some(key => options[key] !== undefined)) return undefined;
  if (keys.some(key => !options[key]) || !options.serial || !validLabel(options.label) ||
      !validLabel(options["build-id"])) fail("explicit-credential-ownership-context-required");
  const binding = prepareArtifactDirectory(options["artifact-dir"] ?? dirname(options.output));
  requireArtifactPaths(binding, [options.output, options.ownership, options["native-inputs"]]);
  if (resolve(options.ownership) === resolve(options.output) ||
      ["", ".handoff.json", ".rollback.json", ".finish.json", ".failed-finish.json", ".operation"].some(suffix => existsSync(`${options.ownership}${suffix}`))) {
    fail("fresh-credential-ownership-required");
  }
  const native = (dependencies.verifyNativeInputs ?? requireVerifiedNativeInputs)(options["native-inputs"], options["build-id"]);
  if (native.buildId !== options["build-id"] || !validHash(native.inputHash) || !validLabel(native.buildOwner)) {
    fail("credential-native-context-invalid");
  }
  const token = (dependencies.ownershipToken ?? (() => randomUUID().replaceAll("-", "")))();
  if (!validToken(token)) fail("invalid-credential-ownership-token");
  const context = { type: "physical-credential-owner", schemaVersion: 1, state: "staged", token,
    serialHash: hash(options.serial), label: options.label, buildId: options["build-id"],
    nativeInputHash: native.inputHash, nativeBuildOwner: native.buildOwner,
    staging: resolve(options.output), ownership: resolve(options.ownership) };
  return { ...context, contextHash: contextHash(context) };
}

function fingerprint(context, requireCommand) {
  // Salt the content fingerprint with the independent random capability and
  // attested context. Neither raw content nor its unsalted digest is persisted.
  return `ownership_identity=$(stat -c '%d %i %u %a %h %s %y %z' ${DESTINATION}) || ${requireCommand} false
case "$ownership_identity" in ''|*%*) ${requireCommand} false;; esac
ownership_digest=$(sha256sum ${DESTINATION}) || ${requireCommand} false
ownership_digest=\${ownership_digest%% *}
ownership_proof=$(printf '%s\\n' '${context.token}' '${context.contextHash}' "$ownership_identity" "$ownership_digest" | sha256sum) || ${requireCommand} false
ownership_proof=\${ownership_proof%% *}
`;
}

// Called only after the destination passed the existing structural/hash checks,
// while the original exclusive publication descriptor is still open.
export function credentialOwnershipPublication(context) {
  if (!context) return "";
  if (!validToken(context.token) || !validHash(context.contextHash)) fail("invalid-credential-publication-context");
  const marker = `files/acceptance/.credential-owner-${context.token}`;
  return `${fingerprint(context, "publication_require")}
publication_require test ${DESTINATION} -ef /proc/self/fd/3
publication_require test ! -e ${marker}
publication_require test ! -L ${marker}
set -C
exec 4> ${marker}
publication_require test ${marker} -ef /proc/self/fd/4
printf '%s %s\\n' '${context.contextHash}' "$ownership_proof" >&4 || publication_finish "$?"
exec 4>&-
printf 'publication-ownership %s\\n' "$ownership_proof"
`;
}

export function recordCredentialOwnership(context, proof) {
  if (!context) return;
  if (!validHash(proof)) fail("credential-publication-proof-missing");
  const staging = privateRead(context.staging);
  if (staging.type !== "physical-credential-staging" || staging.eligible !== true ||
      staging.destinationOwned !== true || staging.steps?.publish?.exitCode !== 0) fail("joined-credential-staging-required");
  publish(context.ownership, { ...context, proof, stagingHash: hash(readFileSync(context.staging)), stagedHostTimeUnixMs: Date.now() });
}

export function requireCredentialOwnership(options, handedOff = false) {
  const binding = prepareArtifactDirectory(dirname(options.ownership));
  const owner = privateRead(options.ownership);
  requireArtifactPaths(binding, [options.ownership, owner.staging]);
  if (owner.type !== "physical-credential-owner" || owner.schemaVersion !== 1 || owner.state !== "staged" ||
      owner.ownership !== resolve(options.ownership) || owner.serialHash !== hash(options.serial ?? "") ||
      owner.label !== options.label || owner.buildId !== options["build-id"] || !validToken(owner.token) ||
      !validHash(owner.nativeInputHash) || !validLabel(owner.nativeBuildOwner) ||
      !validHash(owner.proof) || !validHash(owner.stagingHash) || owner.contextHash !== contextHash(owner) ||
      !Number.isFinite(owner.stagedHostTimeUnixMs)) fail("credential-ownership-context-mismatch");
  const staged = privateRead(owner.staging);
  if (hash(readFileSync(owner.staging)) !== owner.stagingHash || staged.eligible !== true ||
      staged.type !== "physical-credential-staging" || staged.destinationOwned !== true) fail("joined-credential-staging-required");
  if (!handedOff && existsSync(`${options.ownership}.handoff.json`)) fail("credential-already-handed-to-instrumentation");
  if (existsSync(`${options.ownership}.rollback.json`)) fail("credential-setup-already-terminal");
  if (existsSync(`${options.ownership}.finish.json`) || existsSync(`${options.ownership}.failed-finish.json`)) {
    fail("credential-session-cleanup-already-terminal");
  }
  return owner;
}

function withOwnerLock(options, action) {
  const path = `${options.ownership}.operation`;
  const binding = prepareArtifactDirectory(dirname(options.ownership));
  requireArtifactPaths(binding, [options.ownership, path]);
  let fd;
  try { fd = openSync(path, "wx", 0o600); }
  catch { fail("credential-ownership-operation-unavailable"); }
  try { return action(); }
  finally {
    const own = fstatSync(fd);
    try {
      const current = lstatSync(path);
      if (current.dev === own.dev && current.ino === own.ino && current.isFile()) unlinkSync(path);
    } finally { closeSync(fd); }
  }
}

// Exported for real-shell fake-ADB tests. Verification combines a marker created
// by the original exclusive transaction, attested host receipt, file contents,
// inode/device and nanosecond modification/change identity. Reopened FDs protect
// the final check from observed path replacement. This is not atomic unlink
// against a hostile concurrent same-UID writer; such concurrency is forbidden.
export function credentialOwnerScript(owner, operation) {
  if (!["check", "rollback", "handoff", "finish"].includes(operation) || !validToken(owner.token) ||
      !validHash(owner.contextHash) || !validHash(owner.proof)) fail("invalid-credential-owner-operation");
  const marker = `files/acceptance/.credential-owner-${owner.token}`;
  return `set -eu
umask 077
export LC_ALL=C
ownership_require() { "$@" || exit 71; }
ownership_require test ! -L files
ownership_require test ! -L files/acceptance
ownership_require test -f ${marker}
ownership_require test ! -L ${marker}
ownership_require test "$(stat -c '%a %h %u' ${marker})" = "600 1 $(id -u)"
exec 4< ${marker}
ownership_require test ${marker} -ef /proc/self/fd/4
ownership_marker=$(cat <&4)
ownership_require test "$ownership_marker" = '${owner.contextHash} ${owner.proof}'
ownership_require test -f ${DESTINATION}
ownership_require test ! -L ${DESTINATION}
ownership_require test "$(stat -c '%a %h %u' ${DESTINATION})" = "600 1 $(id -u)"
exec 3< ${DESTINATION}
ownership_require test ${DESTINATION} -ef /proc/self/fd/3
${fingerprint(owner, "ownership_require")}
ownership_require test "$ownership_proof" = '${owner.proof}'
ownership_require test ! -L ${marker}
ownership_require test ${marker} -ef /proc/self/fd/4
ownership_require test ! -L ${DESTINATION}
ownership_require test ${DESTINATION} -ef /proc/self/fd/3
${["rollback", "finish"].includes(operation) ? `rm ${DESTINATION}
ownership_require test ! -e ${DESTINATION}
ownership_require test ! -L ${DESTINATION}
` : ""}${["rollback", "finish"].includes(operation) ? `rm ${marker}
ownership_require test ! -e ${marker}
` : ""}printf 'credential-owner-${operation}-complete\\n'
`;
}

function adbInvocation(dependencies, maxBuffer = 4096) {
  return dependencies.ownershipAdb ?? ((args) => spawnSync("adb", args, { encoding: "utf8", timeout: 10_000, maxBuffer }));
}

// Share the same ordinary-shell proof at staging, handoff and cleanup. A
// package-replacement broadcast can start the normal app after installation;
// a missing credential destination does not imply a stopped app.
// Standalone callers use the same bounded ADB implementation as the ownership
// transactions; dependency injection remains available for their unit fixtures.
export function requireCredentialTargetStopped(serial, invoke = adbInvocation({})) {
  const result = invoke(["-s", serial, "shell", "pidof", PACKAGE]);
  // Exit 1 plus exactly empty output is the pidof no-match contract. Transport
  // errors, timeouts, arbitrary nonzero exits or a live normal app are not it.
  if (result?.status !== 1 || typeof result.stdout !== "string" || result.stdout.trim() !== "" ||
      result.stderr?.trim() || result.error || result.signal) fail("credential-target-not-proven-stopped");
}

function remote(owner, operation, serial, invoke) {
  const result = invoke(["-s", serial, "shell", "-T", "run-as", PACKAGE, "sh", "-c", quote(credentialOwnerScript(owner, operation))]);
  if (result?.status !== 0 || result.stdout !== `credential-owner-${operation}-complete\n` || result.error || result.signal) {
    fail("credential-remote-ownership-unproven");
  }
}

export function handoffCredentialOwnership(options, native, dependencies = {}) {
  return withOwnerLock(options, () => {
    const owner = requireCredentialOwnership(options);
    if (native.inputHash !== owner.nativeInputHash || native.buildOwner !== owner.nativeBuildOwner) fail("credential-native-context-mismatch");
    if (!validSession(options["session-id"]) || !options["instrumentation-owner"]) fail("prospective-credential-session-binding-required");
    const binding = prepareArtifactDirectory(dirname(options.ownership));
    requireArtifactPaths(binding, [options.ownership, options["instrumentation-owner"]]);
    if (existsSync(options["instrumentation-owner"])) fail("fresh-credential-instrumentation-owner-required");
    const invoke = adbInvocation(dependencies);
    requireCredentialTargetStopped(options.serial, invoke);
    remote(owner, "check", options.serial, invoke);
    requireCredentialTargetStopped(options.serial, invoke);
    // Irreversible before AM spawn. A crash or lost remote result after this
    // point must not authorize setup rollback of a possible consumer's input.
    publish(`${options.ownership}.handoff.json`, { type: "physical-credential-handoff", schemaVersion: 2,
      contextHash: owner.contextHash, sessionId: options["session-id"],
      instrumentationOwner: resolve(options["instrumentation-owner"]), hostTimeUnixMs: Date.now() });
    remote(owner, "handoff", options.serial, invoke);
    return true;
  });
}

function requireFinishedInstrumentation(options, owner, dependencies) {
  const binding = prepareArtifactDirectory(dirname(options.ownership));
  const path = options["instrumentation-owner"];
  if (!path || !validLabel(options["finish-command-id"])) fail("explicit-finished-credential-session-required");
  requireArtifactPaths(binding, [path, `${path}.ready.json`, `${path}.terminal.json`, `${options.ownership}.handoff.json`]);
  const handoff = privateRead(`${options.ownership}.handoff.json`);
  if (handoff.type !== "physical-credential-handoff" || handoff.schemaVersion !== 2 || handoff.contextHash !== owner.contextHash ||
      !validSession(handoff.sessionId) || handoff.instrumentationOwner !== resolve(path)) fail("prospective-finished-session-binding-required");
  const session = privateRead(path);
  const ready = privateRead(`${path}.ready.json`);
  const terminal = privateRead(`${path}.terminal.json`);
  if (session.schema !== 1 || session.type !== "instrumentation-session" || session.state !== "running" ||
      session.ownerId !== handoff.sessionId || session.serialHash !== owner.serialHash || session.label !== owner.label ||
      session.nativeInputHash !== owner.nativeInputHash || session.nativeBuildOwner !== owner.nativeBuildOwner ||
      session.targetPackage !== PACKAGE || session.className !== `${PACKAGE}.acceptance.PhysicalLowbarSessionTest` ||
      !Number.isSafeInteger(session.supervisorPid) || session.supervisorPid <= 0 || !Number.isSafeInteger(session.adbPid) ||
      session.adbPid <= 0 || session.supervisorPid === session.adbPid ||
      !validHash(session.supervisorIdentity) || !validHash(session.adbIdentity) ||
      !Number.isFinite(session.startedHostTimeUnixMs) || !Number.isFinite(handoff.hostTimeUnixMs) ||
      session.startedHostTimeUnixMs < handoff.hostTimeUnixMs ||
      ready.schema !== 1 || ready.type !== "instrumentation-session-ready" || ready.ownerId !== session.ownerId ||
      ready.serialHash !== session.serialHash || !Number.isSafeInteger(ready.targetPid) || ready.targetPid <= 0 ||
      !Number.isFinite(ready.elapsedMs) || ready.elapsedMs < 0 ||
      terminal.state !== "complete" || terminal.exitCode !== 0 || terminal.signal !== null || terminal.interrupted !== false ||
      !Number.isFinite(terminal.completedHostTimeUnixMs) || terminal.completedHostTimeUnixMs < session.startedHostTimeUnixMs ||
      Object.entries(session).some(([key, value]) => key !== "state" && JSON.stringify(terminal[key]) !== JSON.stringify(value))) {
    fail("normally-joined-matching-instrumentation-required");
  }
  const stopped = dependencies.hostProcessStopped ?? (pid => {
    try { process.kill(pid, 0); return false; } catch (error) { return error?.code === "ESRCH"; }
  });
  if (stopped(session.supervisorPid) !== true || stopped(session.adbPid) !== true) fail("instrumentation-host-owners-must-be-joined");
  return { ready, evidence: hash(JSON.stringify([owner, handoff, session, ready, terminal])) };
}

function requireFinishStatus(options, ready, invoke) {
  const result = invoke(["-s", options.serial, "shell", "run-as", PACKAGE, "cat", "files/acceptance/physical-status"]);
  if (result?.status !== 0 || result.error || result.signal || typeof result.stdout !== "string" ||
      result.stdout.length > 64 * 1024) fail("exact-finished-status-required");
  let status;
  try { status = JSON.parse(result.stdout); } catch { fail("exact-finished-status-required"); }
  // PhysicalLowbarSessionTest records finish after disconnecting both roles,
  // before finally/logout tears down the VPN service. tunnelStarted is a
  // snapshot from that earlier point, not proof of current target liveness.
  // Require its schema here; finishCredentialSession proves target exit too.
  if (status?.type !== "status" || status.pid !== ready.targetPid || status.state !== "complete" || status.phase !== "finish" ||
      status.commandId !== options["finish-command-id"] || !Number.isFinite(status.elapsedMs) || status.elapsedMs < ready.elapsedMs ||
      status.connected !== false || typeof status.tunnelStarted !== "boolean" || status.provideEnabled !== false) fail("exact-finished-status-required");
}

// Only a prospective v2 handoff with its original retained marker can authorize
// normal-finish cleanup. Legacy handoffs, interrupted owners and unknown files
// are intentionally not upgraded/adopted here, even if a finish file exists.
export function finishCredentialSession(options, dependencies = {}) {
  const report = { type: "physical-credential-session-cleanup", schemaVersion: 1, eligible: false,
    reason: "credential-session-cleanup-unavailable", ownershipVerified: false, destinationRemoved: false };
  let evidenceOwned = false;
  try {
    withOwnerLock(options, () => {
      const owner = requireCredentialOwnership(options, true);
      const finished = requireFinishedInstrumentation(options, owner, dependencies);
      evidenceOwned = true;
      const invoke = adbInvocation(dependencies, 64 * 1024);
      requireCredentialTargetStopped(options.serial, invoke);
      requireFinishStatus(options, finished.ready, invoke);
      remote(owner, "check", options.serial, invoke);
      report.ownershipVerified = true;
      requireFinishStatus(options, finished.ready, invoke);
      const current = requireCredentialOwnership(options, true);
      if (requireFinishedInstrumentation(options, current, dependencies).evidence !== finished.evidence) {
        fail("finished-credential-session-evidence-changed");
      }
      requireCredentialTargetStopped(options.serial, invoke);
      remote(owner, "finish", options.serial, invoke);
      report.destinationRemoved = true;
      report.eligible = true;
      report.reason = "owned-finished-session-credentials-removed";
      publish(`${options.ownership}.finish.json`, report);
    });
  } catch (error) { report.eligible = false; report.reason = credentialOwnershipReason(error); }
  if (!report.eligible && evidenceOwned) {
    try { if (!existsSync(`${options.ownership}.finish.json`)) publish(`${options.ownership}.finish.json`, report); }
    catch { /* no durable success receipt, no retry permission */ }
  }
  return report;
}

// This is cleanup authority for an already failed, prospectively owned arm,
// never successful finish evidence. A normal AM exit needs the original arm's
// observed startup failure; missing/ambiguous child exits stay owned.
function requireFailedInstrumentation(options, owner, dependencies) {
  const path = options["instrumentation-owner"];
  if (!path) fail("explicit-failed-credential-session-required");
  requireArtifactPaths(prepareArtifactDirectory(dirname(options.ownership)),
    [path, `${path}.terminal.json`, `${options.ownership}.handoff.json`]);
  const handoff = privateRead(`${options.ownership}.handoff.json`);
  const session = privateRead(path); const terminal = privateRead(`${path}.terminal.json`);
  const component = `${PACKAGE}.test/`;
  const exited = Number.isInteger(terminal.exitCode) && terminal.exitCode >= 0 && terminal.exitCode <= 255 && terminal.signal === null;
  const signalled = terminal.exitCode === null && ["SIGINT", "SIGTERM", "SIGHUP", "SIGKILL", "SIGABRT", "SIGSEGV", "SIGPIPE"].includes(terminal.signal);
  const failed = terminal.state === "failed" && (terminal.exitCode !== 0 || terminal.interrupted === true);
  const complete = terminal.state === "complete" && terminal.exitCode === 0 && terminal.signal === null && terminal.interrupted === false;
  if (handoff.type !== "physical-credential-handoff" || handoff.schemaVersion !== 2 || handoff.contextHash !== owner.contextHash ||
      !validSession(handoff.sessionId) || handoff.instrumentationOwner !== resolve(path) ||
      session.schema !== 1 || session.type !== "instrumentation-session" || session.state !== "running" ||
      session.ownerId !== handoff.sessionId || session.serialHash !== owner.serialHash || session.label !== owner.label ||
      session.nativeInputHash !== owner.nativeInputHash || session.nativeBuildOwner !== owner.nativeBuildOwner ||
      session.targetPackage !== PACKAGE || session.className !== `${PACKAGE}.acceptance.PhysicalLowbarSessionTest` ||
      ![`${component}androidx.test.runner.AndroidJUnitRunner`, `${component}${PACKAGE}.acceptance.PhysicalCredentialDiagnosticRunner`].includes(session.component) ||
      !Number.isSafeInteger(session.supervisorPid) || session.supervisorPid <= 0 || !Number.isSafeInteger(session.adbPid) || session.adbPid <= 0 ||
      session.supervisorPid === session.adbPid || !validHash(session.supervisorIdentity) || !validHash(session.adbIdentity) ||
      !Number.isFinite(handoff.hostTimeUnixMs) || !Number.isFinite(session.startedHostTimeUnixMs) ||
      session.startedHostTimeUnixMs < handoff.hostTimeUnixMs ||
      !(failed || complete) || typeof terminal.interrupted !== "boolean" || !(exited || signalled) ||
      !Number.isFinite(terminal.completedHostTimeUnixMs) || terminal.completedHostTimeUnixMs < session.startedHostTimeUnixMs ||
      Object.entries(session).some(([key, value]) => key !== "state" && JSON.stringify(terminal[key]) !== JSON.stringify(value))) {
    fail("joined-matching-failed-instrumentation-required");
  }
  const stopped = dependencies.hostProcessStopped ?? (pid => {
    try { process.kill(pid, 0); return false; } catch (error) { return error?.code === "ESRCH"; }
  });
  if (stopped(session.supervisorPid) !== true || stopped(session.adbPid) !== true) fail("instrumentation-host-owners-must-be-joined");
  let failedArm;
  if (complete || options["failed-arm"] !== undefined) {
    if (!options["failed-arm"]) fail("explicit-failed-arm-evidence-required");
    const manifestPath = resolve(options["failed-arm"]);
    const run = dirname(manifestPath); const artifacts = dirname(resolve(options.ownership));
    const directory = join(artifacts, owner.label); const statusPath = join(directory, "physical-finish-status.json");
    requireArtifactPaths(prepareArtifactDirectory(run), [manifestPath, join(run, "result.json")]);
    const arm = privateRead(manifestPath); const result = privateRead(join(run, "result.json"));
    if (manifestPath !== join(run, "arm.json") || arm.manifest !== manifestPath || arm.mode !== "run" || arm["run-dir"] !== run ||
        artifacts !== join(run, "private") || arm.artifacts !== artifacts || arm.directory !== directory || arm.finishStatus !== statusPath ||
        arm.owner !== resolve(path) || arm.owner !== join(artifacts, `${owner.label}.instrumentation-owner.json`) ||
        arm.credentialOwner !== resolve(options.ownership) || arm.credentialOwner !== join(artifacts, `${owner.label}.credential-owner.json`) ||
        arm.serial !== options.serial || arm.label !== owner.label || arm["build-id"] !== owner.buildId || arm.buildOwner !== owner.nativeBuildOwner ||
        !["qualification", "diagnostic"].includes(arm["measurement-mode"]) || result.measurementMode !== arm["measurement-mode"] ||
        result.type !== "scoped-h1-arm" || result.schemaVersion !== 2 || result.eligible !== false || result.qualificationEligible !== false ||
        result.classification !== `SCOPED_H1_${arm["measurement-mode"] === "diagnostic" ? "DIAGNOSTIC_" : ""}FAILED` ||
        result.memoryQualified !== false || result.cleanupComplete !== false || result.measurements !== null ||
        !Array.isArray(result.errors) || !result.errors.includes("instrumentation-ready-error") ||
        !Array.isArray(result.completedSteps) || !["credential-stage", "finish-status-final"].every(step => result.completedSteps.includes(step)) ||
        ["instrumentation-ready", "workload", "finish", "credential-finish"].some(step => result.completedSteps.includes(step)) ||
        existsSync(`${path}.ready.json`)) fail("matching-failed-startup-arm-required");
    // Reuse the manifest's original directory identities, not a replacement
    // tree assembled around copied private receipts after the arm finished.
    if (!Array.isArray(arm.directoryBindings) || arm.directoryBindings.length !== 3) fail("matching-failed-startup-arm-required");
    for (const expected of [run, artifacts, directory]) {
      const bindings = arm.directoryBindings.filter(binding => binding?.directory === expected);
      if (bindings.length !== 1) fail("matching-failed-startup-arm-required");
      requireArtifactPaths(bindings[0], [join(expected, "identity-check")]);
    }
    const status = privateRead(statusPath);
    const capture = privateRead(join(directory, "finish-status-final.outcome.json"));
    const observations = privateText(join(directory, "startup-status-observations.jsonl"), 256 * 1024)
      .trimEnd().split("\n").map(line => JSON.parse(line));
    const observed = observations.at(-1);
    // Device wall time is independent of the host. The host's live-PID
    // observation supplies the causal interval; fresh status checks bind the
    // retained app session ID without inventing a pre-ready owner receipt.
    if (status.type !== "status" || status.state !== "error" || status.phase !== "startup" || status.commandId !== "0" ||
        status.buildId !== owner.buildId || !validSession(status.sessionId) || !Number.isSafeInteger(status.pid) || status.pid <= 0 ||
        !Number.isFinite(status.elapsedMs) || status.elapsedMs < 0 ||
        capture.eligible !== true || capture.exitCode !== 0 || capture.signal !== null || capture.timedOut !== false ||
        observations.some((row, index) => row?.type !== "startup-status-observation" || row.schemaVersion !== 1 || row.sequence !== index + 1) ||
        observed.outcome !== "current-error" || observed.currentBuild !== true || observed.observedBuildId !== owner.buildId ||
        observed.observedPid !== status.pid || observed.observedState !== "error" || observed.targetPidMatches !== true ||
        observed.exitCode !== 0 || observed.signalled !== false || observed.transportFailed !== false || observed.stderrBytes !== 0 ||
        !Number.isSafeInteger(observed.stdoutBytes) || observed.stdoutBytes <= 0 ||
        !Number.isFinite(observed.hostTimeUnixMs) || observed.hostTimeUnixMs < session.startedHostTimeUnixMs ||
        observed.hostTimeUnixMs > terminal.completedHostTimeUnixMs) fail("matching-failed-startup-status-required");
    failedArm = { arm, result, status, capture, observations };
  }
  return { evidence: hash(JSON.stringify([owner, handoff, session, terminal, failedArm])), failedStatus: failedArm?.status };
}

// The supported package-filtered processes dump has a fixed prologue and tail.
// Keep raw process details in memory; unsupported/partial output is not absence.
export function requireCredentialInstrumentationStopped(serial, invoke) {
  const result = invoke(["-s", serial, "shell", "dumpsys", "activity", "processes", PACKAGE]);
  const text = result?.stdout;
  if (result?.status !== 0 || result.error || result.signal || result.stderr?.trim() || typeof text !== "string" ||
      Buffer.byteLength(text) > 64 * 1024 || text.includes("\0")) fail("credential-instrumentation-not-proven-stopped");
  const lines = text.replaceAll("\r\n", "\n").trimEnd().split("\n");
  const header = "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)";
  const tail = /^  mForceBackgroundCheck=(true|false)$/;
  const known = /^\s*(?:OOM levels:|-?[0-9]+: [A-Z_]+ \([ 0-9,K]+\)|Process OOM control \([0-9]+ total, non-act at [0-9]+, non-svc at [0-9]+\):|Process LRU list \(sorted by oom_adj, [0-9]+ total, non-act at [0-9]+, non-svc at [0-9]+\):|m(?:Home|Previous)Process: (?:null|ProcessRecord\{[^{}\r\n]+\})|All Active App Child Processes:|proc #[0-9]+: PhantomProcessRecord \{[^{}\r\n]+\}|user #[0-9]+ uid=[0-9]+ pid=[0-9]+ ppid=[0-9]+ knownSince=[+a-zA-Z0-9.-]+ killed=(?:true|false) *|lastCpuTime=[0-9]+ +(?:timeUsed=[0-9]+ )?oom adj=-?[0-9]+ seq=[0-9]+ *|mPreviousProcessVisibleTime: [+a-zA-Z0-9.-]+|mDeviceIdle(?:ExceptIdle|Temp)?Allowlist=\[[0-9, ]*\]|mFgs(?:BootCompleted)?StartTempAllowList:|mForceBackgroundCheck=(?:true|false))$/;
  if (lines[0] !== header || !tail.test(lines.at(-1)) || lines.filter(line => line === header).length !== 1 ||
      lines.filter(line => tail.test(line)).length !== 1 || !lines.includes("  OOM levels:") ||
      !lines.some(line => /^  Process LRU list \(/.test(line)) ||
      lines.slice(1).some(line => line.trim() && !known.test(line))) {
    fail("credential-instrumentation-not-proven-stopped");
  }
}

// Explicit failed-session cleanup removes only the original credential/marker
// after both host children and the target/instrumentation are proven absent.
export function finishFailedCredentialSession(options, dependencies = {}) {
  const report = { type: "physical-credential-failed-session-cleanup", schemaVersion: 1, eligible: false,
    qualificationEligible: false, reason: "credential-failed-session-cleanup-unavailable", ownershipVerified: false, destinationRemoved: false };
  let removalAttempted = false;
  try {
    withOwnerLock(options, () => {
      const owner = requireCredentialOwnership(options, true);
      const failed = requireFailedInstrumentation(options, owner, dependencies);
      const invoke = adbInvocation(dependencies, 64 * 1024);
      const requireFailedStatus = () => {
        if (!failed.failedStatus) return;
        const result = invoke(["-s", options.serial, "shell", "run-as", PACKAGE, "cat", "files/acceptance/physical-status"]);
        if (result?.status !== 0 || result.error || result.signal || result.stderr?.trim() || typeof result.stdout !== "string" ||
            Buffer.byteLength(result.stdout) > 64 * 1024) fail("exact-failed-arm-status-required");
        let status;
        try { status = JSON.parse(result.stdout); } catch { fail("exact-failed-arm-status-required"); }
        if (hash(JSON.stringify(status)) !== hash(JSON.stringify(failed.failedStatus))) fail("exact-failed-arm-status-required");
      };
      requireCredentialTargetStopped(options.serial, invoke);
      requireCredentialInstrumentationStopped(options.serial, invoke);
      requireFailedStatus();
      remote(owner, "check", options.serial, invoke);
      report.ownershipVerified = true;
      const current = requireCredentialOwnership(options, true);
      if (requireFailedInstrumentation(options, current, dependencies).evidence !== failed.evidence) fail("failed-credential-session-evidence-changed");
      requireFailedStatus();
      requireCredentialInstrumentationStopped(options.serial, invoke);
      requireCredentialTargetStopped(options.serial, invoke);
      removalAttempted = true;
      remote(owner, "finish", options.serial, invoke);
      report.destinationRemoved = true;
      report.eligible = true;
      report.reason = "owned-failed-session-credentials-removed";
      publish(`${options.ownership}.failed-finish.json`, report);
    });
  } catch (error) { report.eligible = false; report.reason = credentialOwnershipReason(error); }
  if (!report.eligible && removalAttempted) {
    try { if (!existsSync(`${options.ownership}.failed-finish.json`)) publish(`${options.ownership}.failed-finish.json`, report); }
    catch { /* retain ambiguous custody; no retry or success is manufactured */ }
  }
  return report;
}

export function rollbackCredentialSetup(options, dependencies = {}) {
  const report = { type: "physical-credential-rollback", schemaVersion: 1, eligible: false,
    reason: "credential-rollback-unavailable", ownershipVerified: false, destinationRemoved: false };
  let evidenceOwned = false;
  try {
    withOwnerLock(options, () => {
      const owner = requireCredentialOwnership(options);
      evidenceOwned = true;
      const invoke = adbInvocation(dependencies);
      requireCredentialTargetStopped(options.serial, invoke);
      remote(owner, "check", options.serial, invoke);
      report.ownershipVerified = true;
      remote(owner, "rollback", options.serial, invoke);
      report.destinationRemoved = true;
      report.eligible = true;
      report.reason = "owned-pre-instrumentation-credentials-removed";
      publish(`${options.ownership}.rollback.json`, report);
    });
  } catch (error) { report.eligible = false; report.reason = credentialOwnershipReason(error); }
  // Unknown ownership/crash/missing marker must not manufacture a cleanup
  // success or mutate its missing/untrusted artifact directory.
  if (!report.eligible && evidenceOwned) {
    try { if (!existsSync(`${options.ownership}.rollback.json`)) publish(`${options.ownership}.rollback.json`, report); }
    catch { /* No durable success proof: caller must keep this setup failed. */ }
  }
  return report;
}

export function parseArgs(argv) {
  if (!["rollback", "finish", "finish-failed"].includes(argv[0])) fail("explicit-credential-rollback-or-finish-required");
  const options = { mode: argv[0] };
  const requiredKeys = ["ownership", "serial", "label", "build-id", ...(argv[0] !== "rollback" ? ["instrumentation-owner"] : []),
    ...(argv[0] === "finish" ? ["finish-command-id"] : [])];
  const keys = [...requiredKeys, ...(argv[0] === "finish-failed" ? ["failed-arm"] : [])];
  for (let i = 1; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !keys.includes(key) ||
        !argv[i + 1] || argv[i + 1].startsWith("--") || options[key] !== undefined) fail("invalid-credential-rollback-arguments");
    options[key] = argv[i + 1];
  }
  if (requiredKeys.some(key => !options[key]) || !validLabel(options.label) || !validLabel(options["build-id"]) ||
      (options.mode === "finish" && !validLabel(options["finish-command-id"]))) {
    fail("explicit-credential-ownership-context-required");
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    const report = options.mode === "finish" ? finishCredentialSession(options) :
      options.mode === "finish-failed" ? finishFailedCredentialSession(options) : rollbackCredentialSetup(options);
    process.stdout.write(`${JSON.stringify(report)}\n`);
    if (!report.eligible) process.exitCode = 2;
  } catch (error) {
    process.stderr.write(`credential rollback failed: ${credentialOwnershipReason(error)}\n`);
    process.exitCode = 2;
  }
}
