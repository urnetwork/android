#!/usr/bin/env node

// Private prospective setup ownership, never adoption of a pre-existing file.
// Assumes the runbook's single host/session and no concurrent same-UID mutator.
// A receipt alone (or a recycled inode) never authorizes credential removal.
import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, fstatSync, linkSync, lstatSync, openSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
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

function privateRead(path) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid() ||
      (stat.mode & 0o7777) !== 0o600 || stat.nlink !== 1 || stat.size > 16_384) fail("private-credential-ownership-required");
  return JSON.parse(readFileSync(path, "utf8"));
}

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
      ["", ".handoff.json", ".rollback.json", ".finish.json", ".operation"].some(suffix => existsSync(`${options.ownership}${suffix}`))) {
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
  if (existsSync(`${options.ownership}.finish.json`)) fail("credential-session-cleanup-already-terminal");
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

function stoppedTarget(serial, invoke) {
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
    stoppedTarget(options.serial, invoke);
    remote(owner, "check", options.serial, invoke);
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
      stoppedTarget(options.serial, invoke);
      requireFinishStatus(options, finished.ready, invoke);
      remote(owner, "check", options.serial, invoke);
      report.ownershipVerified = true;
      requireFinishStatus(options, finished.ready, invoke);
      const current = requireCredentialOwnership(options, true);
      if (requireFinishedInstrumentation(options, current, dependencies).evidence !== finished.evidence) {
        fail("finished-credential-session-evidence-changed");
      }
      stoppedTarget(options.serial, invoke);
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

export function rollbackCredentialSetup(options, dependencies = {}) {
  const report = { type: "physical-credential-rollback", schemaVersion: 1, eligible: false,
    reason: "credential-rollback-unavailable", ownershipVerified: false, destinationRemoved: false };
  let evidenceOwned = false;
  try {
    withOwnerLock(options, () => {
      const owner = requireCredentialOwnership(options);
      evidenceOwned = true;
      const invoke = adbInvocation(dependencies);
      stoppedTarget(options.serial, invoke);
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
  if (!["rollback", "finish"].includes(argv[0])) fail("explicit-credential-rollback-or-finish-required");
  const options = { mode: argv[0] };
  const keys = ["ownership", "serial", "label", "build-id", ...(argv[0] === "finish" ? ["instrumentation-owner", "finish-command-id"] : [])];
  for (let i = 1; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !keys.includes(key) ||
        !argv[i + 1] || argv[i + 1].startsWith("--") || options[key] !== undefined) fail("invalid-credential-rollback-arguments");
    options[key] = argv[i + 1];
  }
  if (keys.some(key => !options[key]) || !validLabel(options.label) || !validLabel(options["build-id"]) ||
      (options.mode === "finish" && !validLabel(options["finish-command-id"]))) {
    fail("explicit-credential-ownership-context-required");
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    const report = options.mode === "finish" ? finishCredentialSession(options) : rollbackCredentialSetup(options);
    process.stdout.write(`${JSON.stringify(report)}\n`);
    if (!report.eligible) process.exitCode = 2;
  } catch (error) {
    process.stderr.write(`credential rollback failed: ${credentialOwnershipReason(error)}\n`);
    process.exitCode = 2;
  }
}
