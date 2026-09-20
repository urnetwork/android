#!/usr/bin/env node

// Host process ownership only: no traffic schedule, network policy, or memory
// thresholds. Child commands stay caller-owned; cleanup is explicit, never
// automatic. A completed receipt cannot be produced by an executor's yield.
import { spawn, spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, linkSync, mkdirSync, readFileSync, realpathSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

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
    new Set(owner.children).size === owner.children.length;
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
    const path = join(dirname(ownerPath), `${name}.complete.json`);
    if (!existsSync(path)) fail("child-completion-required");
    const child = read(path);
    previousEnd = validateChild(child, owner, name, previousEnd, isLive);
    return child;
  });
}

export function requireCompletedWorkloads(path, label, serial, dependencies = {}) {
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
  if (!Array.isArray(command) || !command.length) fail("foreground-command-required");
  let interrupted = false;
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
      child.once("error", () => resolve_({ closed: true, exitCode: null, signal: null, interrupted, pid: child.pid }));
      child.once("close", (exitCode, signal) => resolve_({ closed: true, exitCode, signal, interrupted, pid: child.pid }));
    });
  } finally {
    for (const [signal, handler] of handlers) process.off(signal, handler);
  }
}

function completeResult(result, isLive, requireSuccess = true) {
  return result?.closed === true && (requireSuccess ? result.exitCode === 0 : exitedNormally(result.exitCode)) && result.signal === null &&
    result.interrupted === false && pidValid(result.pid) && !isLive(-result.pid);
}

export async function ownWorkloads(options, dependencies = {}) {
  const wallNow = dependencies.wallNow ?? Date.now;
  const isLive = dependencies.isLive ?? processIsLive;
  if (!options.serial) fail("explicit-owner-serial-required");
  const owner = { schema: SCHEMA, type: "workload-owner", ownerId: randomUUID(), label: options.label,
    serialHash: serialHash(options.serial), ownerPid: process.pid, startedHostTimeUnixMs: wallNow(), children: options.children };
  if (!ownerValid(owner)) fail("unique-predeclared-children-and-label-required");
  owner.collector = requireLiveCollector({ pid: Number(options["collector-pid"]), path: options.telemetry },
    owner.label, owner.startedHostTimeUnixMs, dependencies);
  if (existsSync(options.output)) fail("output-already-exists");
  const directory = `${resolve(options.output)}.owner-${owner.ownerId}`;
  mkdirSync(directory, { mode: 0o700 });
  const ownerPath = join(directory, "owner.json");
  publish(ownerPath, owner);
  const result = await (dependencies.run ?? runCommand)(options.command,
    { ...process.env, [OWNER_ENV]: ownerPath });
  if (!completeResult(result, isLive)) fail("owner-not-joined-or-interrupted");
  const childReceipts = childrenFromDisk(owner, ownerPath, [...owner.children, "cleanup"], isLive);
  requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
  const receipt = { ...owner, state: "complete", exitCode: 0, signal: null, interrupted: false,
    processGroup: result.pid, completedHostTimeUnixMs: wallNow(), childReceipts,
    failedChildCount: childReceipts.filter((child) => child.exitCode !== 0).length };
  if (receipt.completedHostTimeUnixMs < childReceipts.at(-1).completedHostTimeUnixMs) fail("host-clock-regressed");
  publish(options.output, receipt);
  return receipt;
}

export async function recordChild(options, dependencies = {}) {
  const ownerPath = options.ownerPath ?? process.env[OWNER_ENV];
  if (!ownerPath) fail("workload-owner-required");
  const owner = read(ownerPath);
  if (!ownerValid(owner)) fail("valid-workload-owner-required");
  requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
  const name = options.cleanup ? "cleanup" : options.name;
  const index = [...owner.children, "cleanup"].indexOf(name);
  if (index < 0 || (!options.cleanup && name === "cleanup")) fail("predeclared-child-required");
  const wallNow = dependencies.wallNow ?? Date.now;
  const isLive = dependencies.isLive ?? processIsLive;
  const prior = childrenFromDisk(owner, ownerPath, owner.children.slice(0, index), isLive);
  const started = wallNow();
  if (started < (prior.at(-1)?.completedHostTimeUnixMs ?? owner.startedHostTimeUnixMs)) fail("host-clock-regressed");
  // Exclusive claim stays as failure/running evidence. A second invocation can
  // neither overwrite it nor retry a failed measurement under the same owner.
  publish(join(dirname(ownerPath), `${name}.started.json`), { wrapperPid: process.pid, startedHostTimeUnixMs: started });
  let result;
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
    if (!completeResult(closed, isLive, false)) fail("child-not-joined-or-interrupted");
    result = { exitCode: closed.exitCode, processGroup: closed.pid };
  }
  const completed = wallNow();
  if (completed < started) fail("host-clock-regressed");
  requireLiveCollector(owner.collector, owner.label, owner.startedHostTimeUnixMs, dependencies);
  const receipt = { schema: SCHEMA, type: "workload-child", ownerId: owner.ownerId, name,
    wrapperPid: process.pid, state: "complete", exitCode: 0, signal: null, interrupted: false,
    startedHostTimeUnixMs: started, completedHostTimeUnixMs: completed, ...result };
  publish(join(dirname(ownerPath), `${name}.complete.json`), receipt);
  return receipt;
}

export function parseArgs(argv) {
  const [mode, ...rest] = argv;
  const separator = rest.indexOf("--");
  const pairs = separator < 0 ? rest : rest.slice(0, separator);
  const options = { mode, command: separator < 0 ? [] : rest.slice(separator + 1) };
  const allowed = { owner: ["serial", "label", "output", "children", "collector-pid", "telemetry"], child: ["name"], cleanup: ["serial"] }[mode];
  if (!allowed) fail("owner-child-or-cleanup-required");
  for (let i = 0; i < pairs.length; i += 2) {
    const key = pairs[i]?.slice(2);
    if (!pairs[i]?.startsWith("--") || !allowed.includes(key) || !pairs[i + 1] ||
        pairs[i + 1].startsWith("--") || options[key] !== undefined) fail("invalid-arguments");
    options[key] = pairs[i + 1];
  }
  if (allowed.some((key) => !options[key]) || (mode === "cleanup" ? options.command.length : !options.command.length)) {
    fail("explicit-owner-child-command-or-cleanup-required");
  }
  if (mode === "owner") options.children = options.children.split(",");
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    if (options.mode === "owner") await ownWorkloads(options);
    else {
      const receipt = await recordChild({ ...options, cleanup: options.mode === "cleanup" });
      // Completed video/probe failures remain failures, but can still be joined
      // before diagnostic quiet memory collection. They never become success.
      process.exitCode = receipt.exitCode;
    }
  } catch (error) {
    // Never echo command arguments, serials, private paths, or device output.
    process.stderr.write(`workload receipt failed: ${error instanceof ReceiptError ? error.message : "evidence-unavailable"}\n`);
    process.exitCode = 2;
  }
}
