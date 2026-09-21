// Host-only binding of the fixed private physical-workload body. No device
// access, subprocess launch, script evaluation, path repair or artifact reuse.
import { createHash, randomUUID } from "node:crypto";
import { existsSync, linkSync, lstatSync, readFileSync, realpathSync, unlinkSync, writeFileSync } from "node:fs";
import { basename, dirname, isAbsolute, join, resolve } from "node:path";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";

const LABEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
const SHELLS = new Set(["sh", "bash", "zsh"]);
class ScriptError extends Error {}
const fail = reason => { throw new ScriptError(reason); };
export const workloadScriptReason = error => error instanceof ScriptError ? error.message :
  artifactDirectoryReason(error) !== "artifact-directory-unavailable" ? artifactDirectoryReason(error) : "workload-script-evidence-unavailable";
const hash = bytes => createHash("sha256").update(bytes).digest("hex");

export const scriptedWorkload = options => options.mode === "owner-script" || options.mode === "script-preflight" ||
  (Array.isArray(options.command) && SHELLS.has(basename(options.command[0] ?? "")));

function workloadDirectory(options) {
  if (!LABEL.test(options.label ?? "") || !isAbsolute(options.output ?? "")) fail("workload-label-and-absolute-output-required");
  const output = resolve(options.output);
  const parent = dirname(output);
  // LOWBAR keeps output within LABEL/, RUN-PERF keeps LABEL.workloads.json
  // beside LABEL/. Both derive the same unique direct-child script location.
  if (basename(output) === "workloads.json") {
    if (basename(parent) !== options.label) fail("workload-output-parent-label-mismatch");
    return parent;
  }
  if (basename(output) === `${options.label}.workloads.json`) return join(parent, options.label);
  fail("workload-output-label-mismatch");
}

function inspectScript(options) {
  const directory = workloadDirectory(options);
  const parent = prepareArtifactDirectory(dirname(directory));
  requireArtifactPaths(parent, [directory]);
  const binding = prepareArtifactDirectory(directory);
  const expected = join(directory, "traffic-workload.sh");
  if (options.command?.length) {
    if (options.command.length !== 2 || !SHELLS.has(basename(options.command[0])) || !isAbsolute(options.command[1])) {
      fail("fixed-workload-script-command-required");
    }
    if (![expected, join(binding.canonical, "traffic-workload.sh")].includes(resolve(options.command[1]))) {
      fail("workload-script-path-mismatch");
    }
    requireArtifactPaths(binding, [options.command[1]]);
  }
  requireArtifactPaths(binding, [expected]);
  let file;
  try { file = lstatSync(expected); } catch (error) { fail(error?.code === "ENOENT" ? "workload-script-missing" : "workload-script-unreadable"); }
  if (!file.isFile() || file.isSymbolicLink()) fail("workload-script-untrusted-type");
  if (file.uid !== process.getuid() || (file.mode & 0o022) !== 0 || file.size <= 0 || file.size > 65536) {
    fail("workload-script-untrusted-metadata");
  }
  const bytes = readFileSync(expected);
  const lines = bytes.toString("utf8").split(/\r?\n/).map(line => line.trim()).filter(line => line && !line.startsWith("#"));
  // This is the runbook's deliberately narrow literal cleanup form, not a
  // general shell parser. In particular, -- adb ..., trailing || true, and a
  // comment containing the right command cannot satisfy the contract.
  const cleanup = lines.filter(line => /\bcleanup\b/.test(line));
  const cleanupLine = /^node\s+"\$(?:RECEIPT|\{RECEIPT\})"\s+cleanup\s+--serial\s+"\$(?:SERIAL|\{SERIAL\})"(?:\s+>"\$PRIVATE_DIR\/chrome-cleanup\.stdout"\s+2>"\$PRIVATE_DIR\/chrome-cleanup\.stderr")?$/;
  if (cleanup.length !== 1 || cleanup[0] !== lines.at(-1) || !cleanupLine.test(cleanup[0])) fail("exact-final-cleanup-serial-required");
  const after = lstatSync(expected);
  requireArtifactPaths(parent, [directory]);
  requireArtifactPaths(binding, [expected]);
  if (after.dev !== file.dev || after.ino !== file.ino || after.size !== file.size || after.mtimeMs !== file.mtimeMs ||
      !after.isFile() || (after.mode & 0o022) !== 0) fail("workload-script-changed-during-read");
  return { labelHash: hash(options.label), interpreter: options.command?.[0] ?? "sh",
    directory: { canonical: binding.canonical, device: binding.device, inode: binding.inode }, script: { path: realpathSync(expected),
    device: file.dev, inode: file.ino, size: file.size, modifiedMs: file.mtimeMs, sha256: hash(bytes) },
    cleanup: "built-in-explicit-serial" };
}

function publish(path, value, binding) {
  requireArtifactPaths(binding, [path]);
  const temporary = `${path}.pending-${randomUUID()}`;
  writeFileSync(temporary, `${JSON.stringify(value)}\n`, { mode: 0o600, flag: "wx" });
  try { requireArtifactPaths(binding, [path, temporary]); linkSync(temporary, path); } finally { unlinkSync(temporary); }
}

function privateReport(path) {
  const file = lstatSync(path);
  if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600 || file.size > 4096) fail("private-script-preflight-required");
  return JSON.parse(readFileSync(path, "utf8"));
}

function workloadLaunch(options) {
  const directory = workloadDirectory(options);
  // The label directory may itself be missing/untrusted. Only its already
  // private parent may retain that failure; never create/chmod the bad target.
  const binding = prepareArtifactDirectory(dirname(directory));
  const failurePath = join(binding.directory, `${options.label}.workload-script-launch.failed.json`);
  requireArtifactPaths(binding, [directory, failurePath]);
  let failure;
  try { failure = lstatSync(failurePath); } catch (error) { if (error?.code !== "ENOENT") throw error; }
  if (failure) fail("workload-script-launch-already-failed");
  return { directory, binding, failurePath };
}

function requireOutputDirectory(options, launch) {
  try {
    requireArtifactPaths(launch.binding, [launch.directory]);
    prepareArtifactDirectory(launch.directory);
    return prepareArtifactDirectory(dirname(options.output));
  } catch (error) {
    const reason = workloadScriptReason(error);
    // A negative launch receipt is not a script proof and can never authorize
    // the owner. Publication may also fail; the original fixed reason survives.
    try { publish(launch.failurePath, { schema: 1, type: "workload-script-launch", eligible: false,
      classification: "INVALID_WORKLOAD_SCRIPT", reason }, launch.binding); } catch { /* no unsafe fallback */ }
    fail(reason);
  }
}

export function captureWorkloadScriptPreflight(options) {
  if (!isAbsolute(options.output ?? "")) fail("workload-label-and-absolute-output-required");
  const launch = workloadLaunch(options);
  const binding = requireOutputDirectory(options, launch);
  const path = `${options.output}.script-preflight.json`;
  if (existsSync(path)) fail("fresh-script-preflight-required");
  let report;
  try { report = { schema: 1, type: "workload-script-preflight", eligible: true, classification: "WORKLOAD_SCRIPT_READY",
    reason: "exact-label-script-and-cleanup-verified", proof: inspectScript(options) }; }
  catch (error) { report = { schema: 1, type: "workload-script-preflight", eligible: false,
    classification: "INVALID_WORKLOAD_SCRIPT", reason: workloadScriptReason(error) }; }
  publish(path, report, binding);
  return report;
}

export function requireWorkloadScript(options) {
  const launch = workloadLaunch(options);
  const path = `${options.output}.script-preflight.json`;
  const failedPath = `${options.output}.script-preflight.failed.json`;
  const outputBinding = requireOutputDirectory(options, launch);
  requireArtifactPaths(outputBinding, [path, failedPath]);
  if (existsSync(failedPath)) fail("workload-script-recheck-already-failed");
  try {
    const report = existsSync(path) ? privateReport(path) : captureWorkloadScriptPreflight(options);
    if (report?.schema !== 1 || report.type !== "workload-script-preflight" || report.eligible !== true ||
        report.classification !== "WORKLOAD_SCRIPT_READY" || report.reason !== "exact-label-script-and-cleanup-verified") {
      fail("passing-script-preflight-required");
    }
    const current = inspectScript(options);
    if (JSON.stringify(current) !== JSON.stringify(report.proof)) fail("workload-script-binding-changed");
    return { command: [current.interpreter, current.script.path], privateDirectory: current.directory.canonical, proof: current };
  } catch (error) {
    const reason = workloadScriptReason(error);
    try { publish(failedPath, { schema: 1, type: "workload-script-preflight", eligible: false,
      classification: "INVALID_WORKLOAD_SCRIPT", reason }, outputBinding); } catch { /* original reason remains fail closed */ }
    fail(reason);
  }
}

export const workloadScriptSummary = report => ({ type: report.type, eligible: report.eligible,
  classification: report.classification, reason: report.reason });
