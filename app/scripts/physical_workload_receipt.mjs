#!/usr/bin/env node

// Host process ownership only: no traffic schedule, network policy, or memory
// thresholds. Child commands stay caller-owned; cleanup is explicit, never
// automatic. A completed receipt cannot be produced by an executor's yield.
import { spawn, spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, linkSync, lstatSync, mkdirSync, readFileSync, realpathSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { captureWorkloadScriptPreflight, requireWorkloadScript, scriptedWorkload,
  workloadScriptReason, workloadScriptSummary } from "./physical_workload_script.mjs";

const SCHEMA = 1;
const OWNER_ENV = "URNETWORK_PERF_WORKLOAD_OWNER";
const NAME = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
class ReceiptError extends Error {}
const fail = (reason) => { throw new ReceiptError(reason); };
const read = (path) => JSON.parse(readFileSync(path, "utf8"));
const pidValid = (pid) => Number.isInteger(pid) && pid > 0;
const timeValid = (time) => Number.isFinite(time) && time >= 0;
const serialHash = (serial) => createHash("sha256").update(serial).digest("hex");
const exitedNormally = (code) => Number.isInteger(code) && code >= 0 && code < 128;
const MAX_COLLECTOR_GAP_MS = 5_000;
const digest = (value) => createHash("sha256").update(value).digest("hex");
const failureReason = error => error instanceof ReceiptError ? error.message :
  error?.code === "ENOENT" ? "required-evidence-missing" : error?.code === "EEXIST" ? "evidence-already-exists" : "evidence-unavailable";

function resultEvidence(result, isLive) {
  let joined = false;
  try { joined = result?.closed === true && pidValid(result.pid) && !isLive(-result.pid); } catch { /* unknown is not joined */ }
  return { observed: result !== undefined, closed: result?.closed === true, joined,
    exitCode: Number.isInteger(result?.exitCode) && result.exitCode >= 0 && result.exitCode <= 255 ? result.exitCode : null,
    signal: ["SIGINT", "SIGTERM", "SIGHUP", "SIGKILL", "SIGABRT", "SIGSEGV", "SIGPIPE"].includes(result?.signal)
      ? result.signal : result?.signal ? "other" : null,
    interrupted: typeof result?.interrupted === "boolean" ? result.interrupted : null,
    launchError: ["missing-executable", "permission-denied", "invalid-launch", "other"].includes(result?.launchError) ? result.launchError : null };
}

// Failure evidence is not a completion receipt. Never repair/retry an existing
// failure or turn a lost owner/process group into joined success.
function retainFailure(path, owner, error, details, binding, dependencies) {
  try {
    requireArtifactPaths(binding, [path]);
    publish(path, { schema: SCHEMA, type: details.name ? "workload-child-failure" : "workload-owner-failure",
      ownerId: owner.ownerId, state: "failed", classification: "INVALID_WORKLOAD_OWNER",
      reason: failureReason(error), stage: details.stage, ...(details.name ? { name: details.name } : {}),
      observedHostTimeUnixMs: (dependencies.wallNow ?? Date.now)(),
      result: resultEvidence(details.result, dependencies.isLive ?? processIsLive) });
    return true;
  } catch { return false; } // Preserve the original fixed failure; never clobber another artifact.
}

export function retainRejectedWorkloadInvocation(argv, error, dependencies = {}) {
  // A cleanup/child parser rejection occurs before recordChild. Preserve it
  // only inside the inherited, live, privately owned workload attempt.
  try {
    if (!["cleanup", "child"].includes(argv[0])) return false;
    const ownerPath = dependencies.ownerPath ?? process.env[OWNER_ENV];
    if (!ownerPath) return false;
    const binding = prepareArtifactDirectory(dirname(ownerPath));
    requireArtifactPaths(binding, [ownerPath]);
    const file = lstatSync(ownerPath);
    if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600) return false;
    const owner = read(ownerPath);
    if (!ownerValid(owner)) return false;
    requireLiveWorkloadOwner(owner, dependencies);
    if (argv[0] === "child" && argv.indexOf("--name") < 0) return false;
    const name = argv[0] === "cleanup" ? "cleanup" : argv[argv.indexOf("--name") + 1];
    if (name !== "cleanup" && !owner.children.includes(name)) return false;
    return retainFailure(join(binding.directory, `${name}.failed.json`), owner, error,
      { name, stage: "arguments" }, binding, dependencies);
  } catch { return false; }
}

export function requireRetainedWorkloadForeground(state) {
  if (state?.inputTTY !== true || state.outputTTY !== true || !pidValid(state.processGroup) ||
      state.processGroup !== state.foregroundGroup) fail("retained-workload-foreground-pty-required");
}

// No command arguments are queried: only the owner's lifetime, controlling
// terminal and foreground process group. Raw ps/terminal names stay in memory.
export function workloadOwnerProcess(result, pid) {
  if (result?.status !== 0 || result.error || result.signal || typeof result.stdout !== "string" || result.stdout.length > 2048) {
    fail("workload-owner-not-live");
  }
  const fields = result.stdout.match(/^\s*(\d+)\s+(\S+)\s+(-?\d+)\s+(-?\d+)\s+(\S+)\s+(\S+\s+\S+\s+\d+\s+\d{2}:\d{2}:\d{2}\s+\d{4})\s*$/);
  if (!fields || Number(fields[1]) !== pid || /[TXZ]/i.test(fields[2])) fail("workload-owner-not-live");
  const processGroup = Number(fields[3]); const foregroundGroup = Number(fields[4]);
  if (!pidValid(processGroup) || processGroup !== foregroundGroup || ["?", "??", "-"].includes(fields[5])) {
    fail("workload-owner-not-foreground");
  }
  return { processGroup, foregroundGroup, identity: digest(`${pid}\n${processGroup}\n${fields[5]}\n${fields[6]}`) };
}

function currentWorkloadOwner(pid, dependencies) {
  return workloadOwnerProcess((dependencies.hostProcess ?? ((value) => spawnSync("ps",
    ["-p", String(value), "-o", "pid=,stat=,pgid=,tpgid=,tty=,lstart="],
    { encoding: "utf8", timeout: 2000, maxBuffer: 2048 })))(pid), pid);
}

function retainedOwnerValid(proof) {
  try { requireRetainedWorkloadForeground(proof?.foreground); } catch { return false; }
  return proof.schema === 1 && /^[a-f0-9]{64}$/.test(proof.identity ?? "");
}

function requireLiveWorkloadOwner(owner, dependencies) {
  if (!retainedOwnerValid(owner.retainedOwner)) fail("retained-workload-owner-proof-required");
  const current = currentWorkloadOwner(owner.ownerPid, dependencies);
  if (current.identity !== owner.retainedOwner.identity || current.processGroup !== owner.retainedOwner.foreground.processGroup ||
      current.foregroundGroup !== owner.retainedOwner.foreground.foregroundGroup) fail("workload-owner-replaced");
}

export function evaluateWorkloadCoverage(records, start, end, label) {
  const reasons = [];
  const environments = records.filter((r) => r.type === "environment");
  if (environments.length !== 1 || environments[0].label !== label) reasons.push("collector-label-or-owner-changed");
  const samples = records.filter((r) => r.type === "sample");
  const first = samples.findLastIndex((r) => r.startTimeUnixMs <= start);
  const last = samples.findIndex((r) => r.endTimeUnixMs >= end);
  const covered = first >= 0 && last >= first ? samples.slice(first, last + 1) : [];
  if (!timeValid(start) || !timeValid(end) || end < start || !covered.length) reasons.push("collector-does-not-cover-workloads");
  for (let i = 0; i < covered.length; i += 1) {
    const r = covered[i];
    if (!timeValid(r.startTimeUnixMs) || !timeValid(r.endTimeUnixMs) || r.endTimeUnixMs < r.startTimeUnixMs ||
        r.endTimeUnixMs - r.startTimeUnixMs > MAX_COLLECTOR_GAP_MS ||
        (i && (r.startTimeUnixMs <= covered[i - 1].startTimeUnixMs ||
          r.startTimeUnixMs - covered[i - 1].endTimeUnixMs > MAX_COLLECTOR_GAP_MS))) reasons.push("workload-telemetry-gap-or-clock-invalid");
    if (r.eligibility?.eligible !== true || !Array.isArray(r.telemetryErrors) || r.telemetryErrors.length) reasons.push("workload-telemetry-ineligible");
  }
  return { eligible: reasons.length === 0, sampleCount: covered.length, reasons: [...new Set(reasons)] };
}

// The explicit retained collector PID is supplied by its launcher ($!). A
// sample already on disk is not enough: require that owner live, a fresh tail,
// and the same file/first sample so late starts/restarts cannot replace it.
export function requireLiveCollector(collector, label, coverageStart, dependencies = {}) {
  const isLive = dependencies.isLive ?? processIsLive;
  const now = (dependencies.wallNow ?? Date.now)();
  if (!pidValid(collector?.pid) || !isLive(collector.pid)) fail("collector-owner-not-live");
  const path = realpathSync(collector.path);
  const stat = statSync(path);
  if (collector.device !== undefined && (collector.device !== stat.dev || collector.inode !== stat.ino)) fail("collector-file-replaced");
  const raw = readFileSync(path, "utf8");
  // A concurrently appended last line is not complete evidence yet.
  const records = raw.slice(0, raw.lastIndexOf("\n") + 1).split("\n").filter(Boolean).map(JSON.parse);
  if (records.some((r) => r.type === "summary")) fail("collector-already-finished");
  const samples = records.filter((r) => r.type === "sample");
  const first = samples[0]?.startTimeUnixMs;
  const last = samples.at(-1)?.endTimeUnixMs;
  if (!timeValid(first) || !timeValid(last) || last > now || now - last > MAX_COLLECTOR_GAP_MS) fail("collector-fresh-sample-required");
  if (collector.firstSampleHostTimeUnixMs !== undefined && collector.firstSampleHostTimeUnixMs !== first) fail("collector-restarted-or-truncated");
  const start = coverageStart ?? now;
  if (!evaluateWorkloadCoverage(records, Math.min(start, last), last, label).eligible) fail("collector-workload-coverage-incomplete");
  if (!isLive(collector.pid)) fail("collector-owner-not-live");
  return { pid: collector.pid, path, device: stat.dev, inode: stat.ino, firstSampleHostTimeUnixMs: first };
}

// Negative IDs check the entire POSIX process group, including an abandoned
// background child whose shell already exited. EPERM is conservatively live.
export function processIsLive(pid) {
  try { process.kill(pid, 0); return true; } catch (error) {
    if (error.code === "ESRCH") return false;
    if (error.code === "EPERM") return true;
    throw error;
  }
}

function publish(path, value) {
  const temporary = `${path}.pending-${randomUUID()}`;
  writeFileSync(temporary, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
  try { linkSync(temporary, path); } finally { unlinkSync(temporary); }
}

function ownerValid(owner) {
  return owner?.schema === SCHEMA && owner.type === "workload-owner" && NAME.test(owner.label ?? "") &&
    NAME.test(owner.ownerId ?? "") && pidValid(owner.ownerPid) && timeValid(owner.startedHostTimeUnixMs) &&
    /^[a-f0-9]{64}$/.test(owner.serialHash ?? "") &&
    Array.isArray(owner.children) && owner.children.length > 0 && owner.children.length <= 32 &&
    owner.children.every((name) => NAME.test(name) && name !== "cleanup") &&
    new Set(owner.children).size === owner.children.length && retainedOwnerValid(owner.retainedOwner);
}

function validateChild(child, owner, name, previousEnd, isLive) {
  if (child?.schema !== SCHEMA || child.type !== "workload-child" || child.ownerId !== owner.ownerId ||
      child.name !== name || child.state !== "complete" || !exitedNormally(child.exitCode) || child.signal !== null ||
      child.interrupted !== false || !pidValid(child.wrapperPid) || !timeValid(child.startedHostTimeUnixMs) ||
      !timeValid(child.completedHostTimeUnixMs) || child.startedHostTimeUnixMs < previousEnd ||
      child.completedHostTimeUnixMs < child.startedHostTimeUnixMs) fail("child-completion-required");
  if (isLive(child.wrapperPid)) fail("child-wrapper-still-live");
  if (name === "cleanup") {
    if (child.exitCode !== 0 || child.browserStopped !== true || child.browserPackage !== "com.android.chrome") fail("browser-cleanup-required");
  } else if (!pidValid(child.processGroup) || isLive(-child.processGroup)) fail("child-process-group-still-live");
  return child.completedHostTimeUnixMs;
}

function childrenFromDisk(owner, ownerPath, names, isLive) {
  let previousEnd = owner.startedHostTimeUnixMs;
  return names.map((name) => {
    if (existsSync(join(dirname(ownerPath), `${name}.failed.json`))) fail("child-failure-already-recorded");
    const path = join(dirname(ownerPath), `${name}.complete.json`);
    if (!existsSync(path)) fail("child-completion-required");
    const child = read(path);
    previousEnd = validateChild(child, owner, name, previousEnd, isLive);
    return child;
  });
}

export function requireCompletedWorkloads(path, label, serial, dependencies = {}) {
  if (existsSync(`${path}.failed.json`)) fail("owner-failure-already-recorded");
  const isLive = dependencies.isLive ?? processIsLive;
  const receipt = read(path);
  if (!serial || !ownerValid(receipt) || receipt.label !== label || receipt.serialHash !== serialHash(serial) || receipt.state !== "complete" ||
      receipt.exitCode !== 0 || receipt.signal !== null || receipt.interrupted !== false ||
      !pidValid(receipt.processGroup) || !timeValid(receipt.completedHostTimeUnixMs) ||
      receipt.completedHostTimeUnixMs < receipt.startedHostTimeUnixMs ||
      receipt.completedHostTimeUnixMs > (dependencies.wallNow ?? Date.now)() ||
      !Array.isArray(receipt.childReceipts) || receipt.childReceipts.length !== receipt.children.length + 1) {
    fail("terminal-owner-receipt-required");
  }
  if (isLive(receipt.ownerPid) || isLive(-receipt.processGroup)) fail("owner-still-live");
  let previousEnd = receipt.startedHostTimeUnixMs;
  [...receipt.children, "cleanup"].forEach((name, index) => {
    previousEnd = validateChild(receipt.childReceipts[index], receipt, name, previousEnd, isLive);
  });
  if (receipt.completedHostTimeUnixMs < previousEnd) fail("owner-completed-before-child");
  if (receipt.failedChildCount !== receipt.childReceipts.filter((child) => child.exitCode !== 0).length) fail("child-failures-not-preserved");
  requireLiveCollector(receipt.collector, label, receipt.startedHostTimeUnixMs, dependencies);
  return receipt;
}

export async function runCommand(command, env = process.env) {
  if (!Array.isArray(command) || !command.length || command.some((value) => typeof value !== "string" || !value)) {
    fail("foreground-command-required");
  }
  // A missing script after `node --` is an interactive process, which cannot be
  // a bounded workload owner/child. Normal probes always pass a script or -e.
  if ((command[0] === "node" || command[0].endsWith("/node")) && command.length === 1) fail("foreground-command-required");
  let interrupted = false;
  let launchError = null;
  const child = spawn(command[0], command.slice(1), { env, stdio: "inherit", detached: true });
  const handlers = new Map(["SIGINT", "SIGTERM", "SIGHUP"].map((signal) => [signal, () => {
    interrupted = true;
    if (child.pid) {
      try { process.kill(-child.pid, signal); } catch (error) { if (error.code !== "ESRCH") throw error; }
    }
  }]));
  for (const [signal, handler] of handlers) process.on(signal, handler);
  try {
    return await new Promise((resolve_) => {
      child.once("error", error => {
        launchError = error?.code === "ENOENT" ? "missing-executable" : ["EACCES", "EPERM"].includes(error?.code)
          ? "permission-denied" : error?.code === "EINVAL" ? "invalid-launch" : "other";
      });
      child.once("close", (exitCode, signal) => resolve_({ closed: true, exitCode, signal, interrupted, pid: child.pid, launchError }));
    });
  } finally {
    for (const [signal, handler] of handlers) process.off(signal, handler);
  }
}

function completeResult(result, isLive, requireSuccess = true) {
  return result?.closed === true && (requireSuccess ? result.exitCode === 0 : exitedNormally(result.exitCode)) && result.signal === null &&
    result.interrupted === false && !result.launchError && pidValid(result.pid) && !isLive(-result.pid);
}

// Read-only post-mortem inspection also covers SIGKILL/executor loss, when no
// process can promise to write a terminal file. Counts are artifact presence,
// not proof of completion, and this is never a replacement for the quiet gate.
export function inspectWorkloadAttempt(options, dependencies = {}) {
  const binding = prepareArtifactDirectory(dirname(resolve(options.owner)));
  requireArtifactPaths(binding, [options.owner]);
  const file = lstatSync(options.owner);
  if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600) fail("private-workload-owner-required");
  const owner = read(options.owner);
  if (!ownerValid(owner) || binding.canonical !== realpathSync(`${resolve(options.output)}.owner-${owner.ownerId}`)) {
    fail("matching-workload-attempt-required");
  }
  const outputBinding = prepareArtifactDirectory(dirname(resolve(options.output)));
  requireArtifactPaths(outputBinding, [options.output, `${options.output}.failed.json`]);
  let live = false;
  try { requireLiveWorkloadOwner(owner, dependencies); live = true; } catch { /* not verified live, not proof of process-group exit */ }
  const failed = existsSync(`${options.output}.failed.json`);
  const complete = existsSync(options.output);
  const names = [...owner.children, "cleanup"];
  return { schema: SCHEMA, type: "workload-attempt-inspection", eligible: false,
    classification: failed ? "INVALID_WORKLOAD_OWNER" : complete ? "WORKLOAD_TERMINAL_PRESENT" : live ? "WORKLOAD_STILL_RUNNING" : "INVALID_WORKLOAD_OWNER",
    reason: failed ? "failed-owner-receipt-present" : complete ? "terminal-requires-completion-validation" :
      live ? "terminal-not-yet-published" : "missing-required-terminal-receipt",
    ownerVerifiedLive: live, terminalSuccessFile: complete, terminalFailureFile: failed,
    declaredChildCount: owner.children.length,
    childStartedFiles: names.filter(name => existsSync(join(binding.directory, `${name}.started.json`))).length,
    childCompleteFiles: names.filter(name => existsSync(join(binding.directory, `${name}.complete.json`))).length,
    childFailureFiles: names.filter(name => existsSync(join(binding.directory, `${name}.failed.json`))).length };
}

export async function ownWorkloads(options, dependencies = {}) {
  // Pipes, redirected/background owners and one-shot executors fail before
  // collector inspection, owner publication or the first workload process.
  const terminal = (dependencies.terminal ?? (() => ({ inputTTY: process.stdin.isTTY === true,
    outputTTY: process.stdout.isTTY === true })))();
  if (terminal.inputTTY !== true || terminal.outputTTY !== true) fail("retained-workload-foreground-pty-required");
  const launch = currentWorkloadOwner(process.pid, dependencies);
  const foreground = { ...terminal, processGroup: launch.processGroup, foregroundGroup: launch.foregroundGroup };
  requireRetainedWorkloadForeground(foreground);
  let script;
  if (scriptedWorkload(options)) {
    try { script = requireWorkloadScript(options); }
    catch (error) { fail(workloadScriptReason(error)); }
  }
  const wallNow = dependencies.wallNow ?? Date.now;
  const isLive = dependencies.isLive ?? processIsLive;
  if (!options.serial) fail("explicit-owner-serial-required");
  const owner = { schema: SCHEMA, type: "workload-owner", ownerId: randomUUID(), label: options.label,
    serialHash: serialHash(options.serial), ownerPid: process.pid, startedHostTimeUnixMs: wallNow(), children: options.children,
    retainedOwner: { schema: 1, foreground, identity: launch.identity } };
  if (!ownerValid(owner)) fail("unique-predeclared-children-and-label-required");
  owner.collector = requireLiveCollector({ pid: Number(options["collector-pid"]), path: options.telemetry },
    owner.label, owner.startedHostTimeUnixMs, dependencies);
  requireLiveWorkloadOwner(owner, dependencies);
  if (existsSync(options.output)) fail("output-already-exists");
  if (existsSync(`${options.output}.failed.json`)) fail("owner-failure-already-recorded");
  const outputBinding = prepareArtifactDirectory(dirname(resolve(options.output)));
  requireArtifactPaths(outputBinding, [options.output, `${options.output}.failed.json`]);
  const directory = `${resolve(options.output)}.owner-${owner.ownerId}`;
  mkdirSync(directory, { mode: 0o700 });
  const ownerPath = join(directory, "owner.json");
  publish(ownerPath, owner);
  let result;
  let stage = "before-launch";
  try {
    requireLiveWorkloadOwner(owner, dependencies);
    if (script) {
      stage = "script-recheck";
      let current;
      try { current = requireWorkloadScript(options); }
      catch (error) { fail(workloadScriptReason(error)); }
      if (JSON.stringify(current.proof) !== JSON.stringify(script.proof)) fail("workload-script-binding-changed");
    }
    stage = "owner-command";
    result = await (dependencies.run ?? runCommand)(script?.command ?? options.command,
      { ...process.env, ...(script ? { PRIVATE_DIR: script.privateDirectory, SERIAL: options.serial,
        RECEIPT: fileURLToPath(import.meta.url), SCRIPTS: dirname(fileURLToPath(import.meta.url)) } : {}), [OWNER_ENV]: ownerPath });
    stage = "owner-join";
    if (!completeResult(result, isLive)) {
      if (completeResult(result, isLive, false)) fail("owner-command-failed");
      fail("owner-not-joined-or-interrupted");
    }
    requireLiveWorkloadOwner(owner, dependencies);
    stage = "child-validation";
    const childReceipts = childrenFromDisk(owner, ownerPath, [...owner.children, "cleanup"], isLive);
    stage = "collector-validation";
    requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
    const receipt = { ...owner, state: "complete", exitCode: 0, signal: null, interrupted: false,
      processGroup: result.pid, completedHostTimeUnixMs: wallNow(), childReceipts,
      failedChildCount: childReceipts.filter((child) => child.exitCode !== 0).length };
    if (receipt.completedHostTimeUnixMs < childReceipts.at(-1).completedHostTimeUnixMs) fail("host-clock-regressed");
    requireLiveWorkloadOwner(owner, dependencies);
    stage = "owner-publication";
    requireArtifactPaths(outputBinding, [options.output]);
    publish(options.output, receipt);
    return receipt;
  } catch (error) {
    retainFailure(`${options.output}.failed.json`, owner, error, { stage, result }, outputBinding, dependencies);
    throw error;
  }
}

export async function recordChild(options, dependencies = {}) {
  const ownerPath = options.ownerPath ?? process.env[OWNER_ENV];
  if (!ownerPath) fail("workload-owner-required");
  const owner = read(ownerPath);
  if (!ownerValid(owner)) fail("valid-workload-owner-required");
  const binding = prepareArtifactDirectory(dirname(ownerPath));
  requireArtifactPaths(binding, [ownerPath]);
  const name = options.cleanup ? "cleanup" : options.name;
  let stage = "child-preconditions";
  let commandResult;
  try {
    requireLiveWorkloadOwner(owner, dependencies);
    requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
    const index = [...owner.children, "cleanup"].indexOf(name);
    if (index < 0 || (!options.cleanup && name === "cleanup")) fail("predeclared-child-required");
    if (existsSync(join(binding.directory, `${name}.failed.json`))) fail("child-failure-already-recorded");
    const wallNow = dependencies.wallNow ?? Date.now;
    const isLive = dependencies.isLive ?? processIsLive;
    const prior = childrenFromDisk(owner, ownerPath, owner.children.slice(0, index), isLive);
    const started = wallNow();
    if (started < (prior.at(-1)?.completedHostTimeUnixMs ?? owner.startedHostTimeUnixMs)) fail("host-clock-regressed");
    // Exclusive claim stays as failure/running evidence. A second invocation can
    // neither overwrite it nor retry a failed measurement under the same owner.
    publish(join(dirname(ownerPath), `${name}.started.json`), { wrapperPid: process.pid, startedHostTimeUnixMs: started });
    let result;
    requireLiveWorkloadOwner(owner, dependencies);
    stage = options.cleanup ? "browser-cleanup" : "child-command";
    if (options.cleanup) {
      if (!options.serial || serialHash(options.serial) !== owner.serialHash) fail("matching-browser-cleanup-serial-required");
      const adb = dependencies.adb ?? ((args) => spawnSync("adb", args,
        { encoding: "utf8", timeout: 10_000, maxBuffer: 1024 * 1024 }));
      // Closing only the foreground tab is insufficient: background video/fast
      // targets may still run. This explicit step stops Chrome, not the VPN.
      const stopped = adb(["-s", options.serial, "shell", "am", "force-stop", "com.android.chrome"]);
      const checked = stopped.status === 0 && !stopped.signal && !stopped.error
        ? adb(["-s", options.serial, "shell", "pidof", "com.android.chrome"]) : undefined;
      if (!checked || checked.status !== 1 || checked.signal || checked.error || checked.stdout?.trim() !== "" ||
          checked.stderr?.trim()) fail("browser-cleanup-not-verified");
      result = { exitCode: 0, browserStopped: true, browserPackage: "com.android.chrome" };
    } else {
      const closed = await (dependencies.run ?? runCommand)(options.command);
      commandResult = closed;
      stage = "child-join";
      if (!completeResult(closed, isLive, false)) fail("child-not-joined-or-interrupted");
      result = { exitCode: closed.exitCode, processGroup: closed.pid };
    }
    const completed = wallNow();
    stage = "child-postconditions";
    if (completed < started) fail("host-clock-regressed");
    requireLiveWorkloadOwner(owner, dependencies);
    requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
    const receipt = { schema: SCHEMA, type: "workload-child", ownerId: owner.ownerId, name,
      wrapperPid: process.pid, state: "complete", exitCode: 0, signal: null, interrupted: false,
      startedHostTimeUnixMs: started, completedHostTimeUnixMs: completed, ...result };
    publish(join(dirname(ownerPath), `${name}.complete.json`), receipt);
    return receipt;
  } catch (error) {
    if (name === "cleanup" || owner.children.includes(name)) retainFailure(join(binding.directory, `${name}.failed.json`),
      owner, error, { name, stage, result: commandResult }, binding, dependencies);
    throw error;
  }
}

export function parseArgs(argv) {
  const [mode, ...rest] = argv;
  const separator = rest.indexOf("--");
  const pairs = separator < 0 ? rest : rest.slice(0, separator);
  const options = { mode, command: separator < 0 ? [] : rest.slice(separator + 1) };
  const ownerOptions = ["serial", "label", "output", "children", "collector-pid", "telemetry"];
  const allowed = { owner: ownerOptions, "owner-script": ownerOptions, "script-preflight": ["label", "output"],
    child: ["name"], cleanup: ["serial"], inspect: ["owner", "output"] }[mode];
  if (!allowed) fail("owner-child-or-cleanup-required");
  for (let i = 0; i < pairs.length; i += 2) {
    const key = pairs[i]?.slice(2);
    if (!pairs[i]?.startsWith("--") || !allowed.includes(key) || !pairs[i + 1] ||
        pairs[i + 1].startsWith("--") || options[key] !== undefined) fail("invalid-arguments");
    options[key] = pairs[i + 1];
  }
  if (allowed.some((key) => !options[key]) || (["cleanup", "inspect", "owner-script", "script-preflight"].includes(mode)
    ? options.command.length : !options.command.length)) {
    fail("explicit-owner-child-command-or-cleanup-required");
  }
  if (["owner", "owner-script"].includes(mode)) options.children = options.children.split(",");
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const argv = process.argv.slice(2);
    if (argv[0] === "validate") {
      if (argv[1] !== "--") fail("validate-requires-argument-separator");
      const options = parseArgs(argv.slice(2));
      process.stdout.write(`${JSON.stringify({ type: "workload-arguments-preflight", valid: true, mode: options.mode })}\n`);
    } else {
      let options;
      try { options = parseArgs(argv); }
      catch (error) { retainRejectedWorkloadInvocation(argv, error); throw error; }
      if (["owner", "owner-script"].includes(options.mode)) await ownWorkloads(options);
      else if (options.mode === "script-preflight") {
        let report;
        try { report = captureWorkloadScriptPreflight(options); }
        catch (error) { fail(workloadScriptReason(error)); }
        process.stdout.write(`${JSON.stringify(workloadScriptSummary(report))}\n`);
        if (!report.eligible) process.exitCode = 2;
      }
      else if (options.mode === "inspect") process.stdout.write(`${JSON.stringify(inspectWorkloadAttempt(options))}\n`);
      else {
        const receipt = await recordChild({ ...options, cleanup: options.mode === "cleanup" });
        // Completed video/probe failures remain failures, but can still be joined
        // before diagnostic quiet memory collection. They never become success.
        process.exitCode = receipt.exitCode;
      }
    }
  } catch (error) {
    // Never echo command arguments, serials, private paths, or device output.
    process.stderr.write(`workload receipt failed: ${error instanceof ReceiptError ? error.message : "evidence-unavailable"}\n`);
    process.exitCode = 2;
  }
}
