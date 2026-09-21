#!/usr/bin/env node

// Host-only, read-only startup gate. Never starts/stops Chrome, changes an adb
// forward, creates a browser target, or requests a public website.
import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync, linkSync, unlinkSync, writeFileSync } from "node:fs";
import { get as httpGet } from "node:http";
import { setTimeout as sleep } from "node:timers/promises";
import { pathToFileURL } from "node:url";
import { CdpSession, cdpFailureDetails } from "./chrome_cdp.mjs";

export const READINESS_TIMEOUT_MS = 30_000;
export const STABLE_RESPONSE_GAP_MS = 5_000;
const PROBE_TIMEOUT_MS = 2_000;
const RETRY_MS = 250;
const MAX_RESPONSE_BYTES = 64 * 1024;
const REMOTE = "localabstract:chrome_devtools_remote";

export function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !["serial", "port", "label", "output"].includes(name) ||
        !argv[i + 1] || argv[i + 1].startsWith("--") || options[name] !== undefined) throw new Error("invalid-arguments");
    options[name] = argv[i + 1];
  }
  options.port = Number(options.port);
  if (!options.serial || !options.output || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(options.label ?? "") ||
      !Number.isInteger(options.port) || options.port < 1 || options.port > 65535) throw new Error("invalid-arguments");
  return options;
}

// Only complete bounded HTTP bodies qualify. Socket-close/aborted responses
// cannot masquerade as a successful empty or partially written JSON document.
export function readDevtoolsVersion(port, timeoutMs, dependencies = {}) {
  const get = dependencies.get ?? httpGet;
  const setTimer = dependencies.setTimer ?? setTimeout;
  const clearTimer = dependencies.clearTimer ?? clearTimeout;
  return new Promise((resolve) => {
    let request;
    let timer;
    let settled = false;
    const finish = (body) => {
      if (settled) return;
      settled = true;
      clearTimer(timer);
      resolve(body);
    };
    try {
      request = get(`http://127.0.0.1:${port}/json/version`, { agent: false }, (response) => {
        const chunks = [];
        let size = 0;
        response.on("data", (chunk) => {
          size += chunk.length;
          if (size > MAX_RESPONSE_BYTES) { finish(undefined); request.destroy(); }
          else chunks.push(chunk);
        });
        response.once("end", () => finish(response.complete && response.statusCode === 200
          ? Buffer.concat(chunks).toString("utf8") : undefined));
        response.once("aborted", () => finish(undefined));
        response.once("error", () => finish(undefined));
        response.once("close", () => { if (!response.complete) finish(undefined); });
      });
      request.once("error", () => finish(undefined));
      timer = setTimer(() => { finish(undefined); request.destroy(); }, timeoutMs);
    } catch { finish(undefined); }
  });
}

export function parseVersionResponse(body, port) {
  try {
    if (typeof body !== "string" || !body.trim() || Buffer.byteLength(body) > MAX_RESPONSE_BYTES) return undefined;
    const version = JSON.parse(body);
    if (typeof version?.Browser !== "string" || typeof version?.["Protocol-Version"] !== "string" ||
        typeof version?.webSocketDebuggerUrl !== "string" ||
        !/^Chrome\/[0-9]+(?:\.[0-9]+){1,4}$/.test(version?.Browser ?? "") ||
        !/^[0-9]+\.[0-9]+$/.test(version?.["Protocol-Version"] ?? "") ||
        version?.["Android-Package"] !== "com.android.chrome") return undefined;
    const socket = new URL(version.webSocketDebuggerUrl);
    if (socket.protocol !== "ws:" || !["127.0.0.1", "localhost", "[::1]"].includes(socket.hostname) ||
        Number(socket.port) !== port || socket.username || socket.password || socket.search || socket.hash ||
        !/^\/devtools\/browser(?:\/[A-Za-z0-9_-]{1,128})?$/.test(socket.pathname)) return undefined;
    return { browser: version.Browser, protocol: version["Protocol-Version"], webSocketUrl: socket.href,
      // Android may expose the tokenless /devtools/browser endpoint. The
      // caller also brackets each response with Chrome process identity.
      // Optional browser target tokens are compared only in memory.
      identity: `${version.Browser}|${version["Protocol-Version"]}|${socket.pathname}` };
  } catch { return undefined; }
}

// A healthy HTTP version endpoint does not prove that Chrome's control socket
// can accept the workload. Ask one read-only command through that exact local
// endpoint; never create a target or navigate during readiness. One deadline
// covers the handshake and command, including a close without an error event.
export async function readDevtoolsControl(version, timeoutMs, dependencies = {}) {
  const createSession = dependencies.createSession ?? (url => new CdpSession(url));
  const setTimer = dependencies.setTimer ?? setTimeout;
  const clearTimer = dependencies.clearTimer ?? clearTimeout;
  let session;
  let timer;
  let finished = false;
  try {
    return await Promise.race([
      new Promise(resolve => { timer = setTimer(() => resolve({ eligible: false, reason: "control-probe-timeout" }), timeoutMs); }),
      (async () => {
        session = createSession(version.webSocketUrl);
        await session.open(timeoutMs);
        if (finished) return { eligible: false, reason: "control-probe-timeout" };
        const result = await session.send("Browser.getVersion");
        return result?.product === version.browser && result?.protocolVersion === version.protocol
          ? { eligible: true }
          : { eligible: false, reason: "control-version-mismatch" };
      })(),
    ]);
  } catch (error) {
    return { eligible: false, ...cdpFailureDetails(error) };
  } finally {
    finished = true;
    clearTimer(timer);
    try { session?.close(); } catch { /* The probe result remains authoritative. */ }
  }
}

function forwardState(result, serial, port) {
  if (result?.status !== 0 || typeof result.stdout !== "string") return "unavailable";
  const rows = result.stdout.split(/\r?\n/).filter((line) => line.trim()).map((line) => line.trim().split(/\s+/));
  const selected = rows.filter((row) => row[1] === `tcp:${port}`);
  if (!selected.length) return "unavailable";
  return selected.length === 1 && selected[0].length === 3 && selected[0][0] === serial && selected[0][2] === REMOTE
    ? "active" : "mismatch";
}

export async function waitForChromeReady(options, dependencies = {}) {
  const now = dependencies.now ?? (() => performance.now());
  const wallNow = dependencies.wallNow ?? Date.now;
  const pause = dependencies.sleep ?? sleep;
  const forward = dependencies.forward ?? ((timeout) => spawnSync("adb", ["forward", "--list"],
    { encoding: "utf8", timeout, maxBuffer: 1024 * 1024 }));
  const processIdentity = dependencies.processIdentity ?? ((timeout) => spawnSync("adb",
    ["-s", options.serial, "shell", "pidof", "com.android.chrome"],
    { encoding: "utf8", timeout, maxBuffer: 4096 }));
  const probe = dependencies.probe ?? readDevtoolsVersion;
  const control = dependencies.control ?? readDevtoolsControl;
  const start = now();
  const deadline = start + READINESS_TIMEOUT_MS;
  const counters = { attempts: 0, validResponses: 0, rejectedResponses: 0, browserReplacements: 0, unavailableForwardChecks: 0,
    controlAttempts: 0, controlFailures: 0 };
  let first;
  let lastReason = "deadline-expired";
  let lastControlFailure;
  const result = (eligible, reason, second) => ({
    type: "chrome-readiness", schemaVersion: 1, label: options.label,
    eligible, classification: eligible ? "CHROME_READY" : "FAILED_CHROME_READINESS",
    reason, elapsedMs: Math.max(0, now() - start), requiredStableGapMs: STABLE_RESPONSE_GAP_MS,
    stableGapMs: eligible ? second.time - first.time : null,
    firstStableResponseHostTimeUnixMs: eligible ? first.wallTime : null,
    readyHostTimeUnixMs: eligible ? second.wallTime : null,
    browser: eligible ? second.browser : null, protocol: eligible ? second.protocol : null,
    sameForward: eligible, sameBrowserInstance: eligible, controlReady: eligible,
    ...(lastControlFailure ? { lastControlFailure } : {}), ...counters,
  });
  const checkForward = async () => {
    if (now() >= deadline) return "deadline";
    let value;
    try { value = await forward(Math.max(1, Math.min(PROBE_TIMEOUT_MS, Math.floor(deadline - now())))); }
    catch { value = undefined; }
    const state = forwardState(value, options.serial, options.port);
    if (state === "unavailable") counters.unavailableForwardChecks += 1;
    return state;
  };
  const chromePid = async () => {
    if (now() >= deadline) return undefined;
    try {
      const value = await processIdentity(Math.max(1, Math.min(PROBE_TIMEOUT_MS, Math.floor(deadline - now()))));
      const pid = value?.stdout?.trim();
      // Empty/multiple processes do not identify the one browser behind this
      // forward. Never retain the PID or raw shell response in the artifact.
      return value?.status === 0 && /^[1-9][0-9]*$/.test(pid ?? "") ? pid : undefined;
    } catch { return undefined; }
  };
  while (now() < deadline) {
    counters.attempts += 1;
    const before = await checkForward();
    if (before === "mismatch") return result(false, "forward-target-mismatch");
    let parsed;
    if (before === "active" && now() < deadline) {
      const pidBefore = await chromePid();
      let body;
      let candidate;
      let controlFailed = false;
      if (pidBefore && now() < deadline) {
        try { body = await probe(options.port, Math.max(1, Math.min(PROBE_TIMEOUT_MS, Math.floor(deadline - now())))); }
        catch { body = undefined; }
        candidate = parseVersionResponse(body, options.port);
        if (candidate && now() < deadline) {
          counters.controlAttempts += 1;
          let response;
          try { response = await control(candidate, Math.max(1, Math.min(PROBE_TIMEOUT_MS, Math.floor(deadline - now())))); }
          catch { response = { eligible: false, reason: "benchmark-operation-failed" }; }
          if (response?.eligible !== true) {
            counters.controlFailures += 1;
            controlFailed = true;
            candidate = undefined;
            // The real adapter only returns fixed fields; explicitly select
            // them so a future adapter cannot accidentally publish Chrome data.
            lastControlFailure = {
              reason: ["control-probe-timeout", "control-version-mismatch", "websocket-closed", "websocket-error",
                "websocket-open-timeout", "invalid-cdp-response", "command-failed", "session-closed",
                "websocket-send-failed", "websocket-not-open"].includes(response?.reason)
                ? response.reason : "control-probe-failed",
              ...(response?.operation === "Browser.getVersion" ? { operation: response.operation } : {}),
              ...(Number.isInteger(response?.websocketCloseCode) && response.websocketCloseCode >= 1000 && response.websocketCloseCode <= 4999
                ? { websocketCloseCode: response.websocketCloseCode } : {}),
              ...(Number.isSafeInteger(response?.protocolCode) ? { protocolCode: response.protocolCode } : {}),
            };
          }
        } else candidate = undefined;
      }
      const pidAfter = await chromePid();
      const after = await checkForward();
      if (after === "mismatch") return result(false, "forward-target-mismatch");
      const replaced = pidBefore && pidAfter && pidBefore !== pidAfter;
      if (after === "active" && pidBefore && pidBefore === pidAfter) {
        parsed = candidate;
        if (parsed) parsed.identity = `${pidBefore}|${parsed.identity}`;
      } else if (replaced) counters.browserReplacements += 1;
      // Exhausting the budget prevented this check; it did not observe a
      // missing forward. Preserve completed evidence, including an actual
      // process replacement if its PID read consumed the remaining budget.
      if (after === "deadline") {
        if (replaced) lastReason = "chrome-process-unavailable-or-replaced";
        else if (controlFailed) lastReason = "devtools-control-unavailable";
        break;
      }
      lastReason = after !== "active" ? "forward-unavailable" : !pidBefore || pidBefore !== pidAfter
        ? "chrome-process-unavailable-or-replaced" : controlFailed
          ? "devtools-control-unavailable" : "invalid-or-incomplete-version-response";
    } else if (before === "unavailable") lastReason = "forward-unavailable";
    if (now() >= deadline) break;
    if (!parsed) {
      counters.rejectedResponses += 1;
      first = undefined;
    } else {
      counters.validResponses += 1;
      const current = { ...parsed, time: now(), wallTime: wallNow() };
      if (first && first.identity !== current.identity) {
        counters.browserReplacements += 1;
        first = undefined;
      }
      if (first && current.time - first.time >= STABLE_RESPONSE_GAP_MS) return result(true, "stable-version-pair", current);
      first ??= current;
    }
    const waitMs = first ? Math.max(1, first.time + STABLE_RESPONSE_GAP_MS - now()) : RETRY_MS;
    await pause(Math.min(waitMs, Math.max(0, deadline - now())));
  }
  return result(false, `deadline-expired:${lastReason}`);
}

function publish(path, result) {
  const temporary = `${path}.pending-${randomUUID()}`;
  writeFileSync(temporary, `${JSON.stringify(result)}\n`, { flag: "wx", mode: 0o600 });
  try { linkSync(temporary, path); } finally { unlinkSync(temporary); }
}

export async function captureChromeReadiness(options, dependencies = {}) {
  if (existsSync(options.output)) throw new Error("output-already-exists");
  const result = await waitForChromeReady(options, dependencies);
  publish(options.output, result);
  return result;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const result = await captureChromeReadiness(parseArgs(process.argv.slice(2)));
    // The receipt is already written synchronously. End any lingering native
    // WebSocket close handshake rather than extending the readiness deadline.
    process.exit(result.eligible ? 0 : 2);
  } catch {
    process.stderr.write("Chrome readiness evidence unavailable or arguments invalid\n", () => process.exit(2));
  }
}
