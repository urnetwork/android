#!/usr/bin/env node

// Run Fast.com's real browser workload through an adb-forwarded Android Chrome
// DevTools socket. Output intentionally contains aggregate timings and byte
// counts and fixed failure categories only: Fast.com's ephemeral download URLs
// may contain signed tokens and must not be copied into benchmark logs.

import process from "node:process";
import { existsSync, lstatSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { CdpSession, cdpFailureDetails } from "./chrome_cdp.mjs";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";

export function parseArgs(argv) {
  const options = { port: 9222, timeoutMs: 90_000 };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--port") options.port = Number(argv[++i]);
    else if (arg === "--timeout-ms") options.timeoutMs = Number(argv[++i]);
    else if (arg === "--diagnostic-last-state") options.diagnosticLastState = true;
    else if (arg === "--diagnostic-activity") options.diagnosticActivity = argv[++i];
    else if (arg === "--help" || arg === "-h") options.help = true;
    else throw new Error(`unknown option: ${arg}`);
  }
  for (const [name, value] of Object.entries(options)) {
    if (!["help", "diagnosticLastState", "diagnosticActivity"].includes(name) && (!Number.isSafeInteger(value) || value <= 0 || value > 2_147_483_647)) {
      throw new Error(`${name} must be a positive number`);
    }
  }
  if (Object.hasOwn(options, "diagnosticActivity") &&
      (typeof options.diagnosticActivity !== "string" || !isAbsolute(options.diagnosticActivity) ||
       resolve(options.diagnosticActivity) !== options.diagnosticActivity || /[\0\r\n]/.test(options.diagnosticActivity))) {
    throw new Error("absolute diagnostic activity path required");
  }
  return options;
}

function usage() {
  return [
    "usage: chrome_fast_benchmark.mjs [options]",
    "",
    "options:",
    "  --port PORT          adb-forwarded DevTools port (default 9222)",
    "  --timeout-ms MS      whole-run deadline, including setup/cleanup (default 90000)",
    "  --diagnostic-last-state  retain bounded last DOM state (unqualified, default off)",
    "  --diagnostic-activity FILE  private latest CDP state for the Go owner (unqualified, default off)",
  ].join("\n");
}

class DeadlineError extends Error {
  constructor(phase) { super("Fast.com lifecycle deadline exceeded"); this.phase = phase; }
}

// Navigation replies can contain private text. Retain only exact, known codes
// as fixed categories; do not coerce, store or echo the supplied value.
class NavigationError extends Error {
  constructor(errorText) {
    super("Fast.com navigation failed");
    switch (errorText) {
      case "net::ERR_TIMED_OUT":
      case "net::ERR_CONNECTION_TIMED_OUT": this.category = "timeout"; break;
      case "net::ERR_NAME_NOT_RESOLVED":
      case "net::ERR_NAME_RESOLUTION_FAILED":
      case "net::ERR_DNS_TIMED_OUT": this.category = "dns"; break;
      case "net::ERR_CONNECTION_RESET": this.category = "reset"; break;
      case "net::ERR_SSL_PROTOCOL_ERROR":
      case "net::ERR_SSL_VERSION_OR_CIPHER_MISMATCH":
      case "net::ERR_CERT_COMMON_NAME_INVALID":
      case "net::ERR_CERT_DATE_INVALID":
      case "net::ERR_CERT_AUTHORITY_INVALID": this.category = "tls"; break;
      case "net::ERR_BLOCKED_BY_CLIENT":
      case "net::ERR_BLOCKED_BY_ADMINISTRATOR":
      case "net::ERR_BLOCKED_BY_RESPONSE":
      case "net::ERR_BLOCKED_BY_CSP": this.category = "blocked"; break;
      case "net::ERR_ABORTED": this.category = "aborted"; break;
    }
  }
}

// Platform-binding exception only: export the existing owned CDP poll, not a
// second browser/controller or app-command publisher. Go owns every decision.
// One atomic latest record (at most4096 bytes), at most96 updates, no history,
// URLs, request ids, arbitrary DOM text or changes to the benchmark deadline.
export function createFastDiagnosticActivity(path) {
  if (path === undefined) return undefined;
  const ownerPath = process.env.URNETWORK_PERF_WORKLOAD_OWNER;
  if (!isAbsolute(path) || resolve(path) !== path || typeof ownerPath !== "string" ||
      !isAbsolute(ownerPath) || resolve(ownerPath) !== ownerPath) throw new Error("diagnostic activity bindings required");
  const binding = prepareArtifactDirectory(dirname(path));
  const temporary = path + ".next", claim = path + ".claim";
  requireArtifactPaths(binding, [path, temporary, claim]);
  const ownerFile = lstatSync(ownerPath);
  if (!ownerFile.isFile() || ownerFile.uid !== process.getuid() || (ownerFile.mode & 0o7777) !== 0o600 ||
      ownerFile.size > 16384) throw new Error("private bounded workload owner required");
  const owner = JSON.parse(readFileSync(ownerPath, "utf8"));
  if (owner.type !== "workload-owner" || owner.schema !== 1 ||
      !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(owner.ownerId) ||
      !Number.isSafeInteger(owner.ownerPid) || owner.ownerPid <= 0 || JSON.stringify(owner.children) !== '["fast-1"]') {
    throw new Error("one owned Fast child required");
  }
  if (existsSync(path) || existsSync(temporary)) throw new Error("fresh diagnostic activity required");
  writeFileSync(claim, JSON.stringify({ type: "fast-activity-claim", pid: process.pid }) + "\n", { flag: "wx", mode: 0o600 });
  let sequence = 0, previous;
  return (phase, value) => {
    if (!["active", "ended"].includes(phase) || ++sequence > 96) throw new Error("bounded diagnostic activity required");
    requireArtifactPaths(binding, [path, temporary, claim]);
    if (previous) {
      const current = lstatSync(path);
      if (!current.isFile() || current.dev !== previous.dev || current.ino !== previous.ino ||
          current.uid !== process.getuid() || (current.mode & 0o7777) !== 0o600) throw new Error("diagnostic activity replaced");
    }
    const bounded = (text, pattern, maximum) => typeof text === "string" && text.length <= maximum && pattern.test(text) ? text : "";
    const record = { type: "fast-active-state", schema: 1, qualificationEligible: false,
      sequence, phase, pid: process.pid, wrapperPid: process.ppid, workloadOwnerPath: ownerPath,
      workloadOwnerId: owner.ownerId, workloadOwnerPid: owner.ownerPid, observedUnixMs: Date.now(),
      elapsedMs: Math.max(0, Math.round(value.elapsedMs)), remainingMs: Math.max(0, Math.floor(value.remainingMs)),
      pageRequestCount: value.pageRequestCount,
      dom: { progressClass: bounded(value.result?.progress, /^[A-Za-z0-9_ -]*$/, 256),
        value: bounded(value.result?.value, /^[0-9., ]*$/, 64),
        units: bounded(value.result?.units, /^[KkMmGg]bps$/, 4),
        ready: ["loading", "interactive", "complete"].includes(value.result?.loaded) ? value.result.loaded : "unknown" } };
    const bytes = JSON.stringify(record) + "\n";
    if (Buffer.byteLength(bytes) > 4096) throw new Error("diagnostic activity too large");
    writeFileSync(temporary, bytes, { flag: "wx", mode: 0o600 });
    renameSync(temporary, path);
    previous = lstatSync(path);
  };
}

// Every external await spends the same budget. Checking only the display loop
// cannot bound a stalled discovery request, CDP reply or target-close reply.
class Deadline {
  constructor(timeoutMs, now, dependencies) {
    this.now = now;
    this.end = now() + timeoutMs;
    this.setTimer = dependencies.setTimer ?? setTimeout;
    this.clearTimer = dependencies.clearTimer ?? clearTimeout;
    this.controller = new AbortController();
  }

  remaining() { return Math.max(0, this.end - this.now()); }

  async wait(phase, operation, maximumMs = Infinity) {
    this.phase = phase;
    const remaining = Math.min(this.remaining(), maximumMs);
    if (remaining <= 0) throw new DeadlineError(phase);
    return new Promise((resolve, reject) => {
      let settled = false;
      const finish = (error, value) => {
        if (settled) return;
        settled = true;
        this.clearTimer(timer);
        if (error) reject(error); else resolve(value);
      };
      const timer = this.setTimer(() => {
        finish(new DeadlineError(phase));
        this.controller.abort();
      }, remaining);
      try {
        Promise.resolve(operation(this.controller.signal)).then(
          value => finish(this.now() >= this.end ? new DeadlineError(phase) : null, value),
          error => finish(error),
        );
      } catch (error) { finish(error); }
    });
  }
}

async function fetchJson(url, signal) {
  const response = await fetch(url, { signal });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.json();
}

async function evaluate(session, expression) {
  const result = await session.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    throw new Error(
      result.exceptionDetails.exception?.description ??
        result.exceptionDetails.text,
    );
  }
  return result.result.value;
}

export function validFastDisplay(value, units) {
  return typeof value === "string" && /^\d+(?:\.\d+)?$/.test(value.trim()) &&
    Number.isFinite(Number(value)) && Number(value) > 0 &&
    typeof units === "string" && /^[KkMmGg]bps$/.test(units.trim());
}

// Page counters cannot measure Fast.com's worker-owned bulk downloads. They
// can still prove that the observed target received a nonempty response, rather
// than treating one pending navigation and a timed-out poll as a speed test.
export function validateFastMeasurement(result) {
  if (result.timedOut === true) return "deadline-exceeded";
  if (result.cleanupComplete === false) return "cleanup-failed";
  if (!validFastDisplay(result.displayValue, result.displayUnits)) return "invalid-display";
  if (result.completed !== true || result.completionReason !== "spinner-stopped") return "incomplete-result";
  if (!(Number.isSafeInteger(result.pageCompletedRequestCount) && result.pageCompletedRequestCount > 0 &&
    Number.isSafeInteger(result.pageEncodedBytes) && result.pageEncodedBytes > 0)) return "insufficient-workload";
  return null;
}

export async function runFastBenchmark(options, dependencies = {}) {
  const readJson = dependencies.fetchJson ?? fetchJson;
  const createSession = dependencies.createSession ?? ((url) => new CdpSession(url));
  const now = dependencies.now ?? (() => performance.now());
  const sleep = dependencies.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const lifecycleStartedAt = now();
  const deadline = new Deadline(options.timeoutMs, now, dependencies);
  const activity = createFastDiagnosticActivity(options.diagnosticActivity);
  const baseUrl = `http://127.0.0.1:${options.port}`;
  let browser;
  let targetId;
  let target;
  let session;
  let startedAt;
  let measuredAt;
  let lastStateAt;
  let result;
  let completionReason = null;
  let failure;
  let cleanupComplete = true;
  let pageEncodedBytes = 0;
  let pageRequestCount = 0;
  let pageCompletedRequestCount = 0;
  let pageFailedRequestCount = 0;
  const pageFailureCategoryCounts = { canceled: 0, dns: 0, timeout: 0,
    connection_closed: 0, connection_reset: 0, connection_refused: 0, connection_failed: 0,
    unreachable: 0, offline: 0, network_changed: 0, tls: 0, blocked: 0, resource: 0, other: 0 };
  let activeRequests = 0;
  let maxParallelRequests = 0;
  try {
    const version = await deadline.wait("browser-discovery", signal => readJson(`${baseUrl}/json/version`, signal));
    browser = createSession(version.webSocketDebuggerUrl);
    await deadline.wait("browser-open", () => browser.open(deadline.remaining()));
    ({ targetId } = await deadline.wait("target-create", () => browser.send("Target.createTarget", {
      url: "about:blank",
    })));
    const targetDeadline = now() + Math.min(5_000, deadline.remaining());
    while (now() < targetDeadline) {
      const targets = await deadline.wait("target-discovery", signal => readJson(`${baseUrl}/json/list`, signal));
      target = targets.find((candidate) => candidate.id === targetId);
      if (target?.webSocketDebuggerUrl) break;
      await deadline.wait("target-discovery", () => sleep(Math.min(25, deadline.remaining())));
    }
    if (!target?.webSocketDebuggerUrl) throw new Error("Chrome target unavailable");

    session = createSession(target.webSocketDebuggerUrl);
    await deadline.wait("page-open", () => session.open(deadline.remaining()));
    await deadline.wait("page-enable", () => Promise.all([
      session.send("Page.enable"),
      session.send("Network.enable"),
      session.send("Runtime.enable"),
    ]));
    await deadline.wait("cache-disable", () => session.send("Network.setCacheDisabled", { cacheDisabled: true }));
    await deadline.wait("cache-clear", () => session.send("Network.clearBrowserCache"));

    // CDP reports only requests owned by this page target. Fast.com may move
    // downloads into a worker/child target, so these are diagnostic page
    // counters, not the authoritative speed-test byte total. The displayed
    // result remains Fast.com's canonical aggregate measurement.
    session.on("Network.requestWillBeSent", () => {
      pageRequestCount += 1;
      activeRequests += 1;
      maxParallelRequests = Math.max(maxParallelRequests, activeRequests);
    });
    session.on("Network.loadingFinished", (event) => {
      pageCompletedRequestCount += 1;
      pageEncodedBytes += Math.max(0, event.encodedDataLength ?? 0);
      activeRequests = Math.max(0, activeRequests - 1);
    });
    session.on("Network.loadingFailed", (event) => {
      // Exact protocol codes only. Unknown text, URLs and request identities
      // never become keys or retained values; cancellation takes precedence.
      let category = "other";
      if (event?.canceled === true) category = "canceled";
      else switch (event?.errorText) {
        case "net::ERR_ABORTED": category = "canceled"; break;
        case "net::ERR_NAME_NOT_RESOLVED":
        case "net::ERR_NAME_RESOLUTION_FAILED":
        case "net::ERR_DNS_TIMED_OUT": category = "dns"; break;
        case "net::ERR_TIMED_OUT":
        case "net::ERR_CONNECTION_TIMED_OUT": category = "timeout"; break;
        case "net::ERR_CONNECTION_CLOSED": category = "connection_closed"; break;
        case "net::ERR_CONNECTION_RESET": category = "connection_reset"; break;
        case "net::ERR_CONNECTION_REFUSED": category = "connection_refused"; break;
        case "net::ERR_CONNECTION_FAILED":
        case "net::ERR_CONNECTION_ABORTED": category = "connection_failed"; break;
        case "net::ERR_ADDRESS_UNREACHABLE": category = "unreachable"; break;
        case "net::ERR_INTERNET_DISCONNECTED": category = "offline"; break;
        case "net::ERR_NETWORK_CHANGED": category = "network_changed"; break;
        case "net::ERR_SSL_PROTOCOL_ERROR":
        case "net::ERR_SSL_VERSION_OR_CIPHER_MISMATCH":
        case "net::ERR_CERT_COMMON_NAME_INVALID":
        case "net::ERR_CERT_DATE_INVALID":
        case "net::ERR_CERT_AUTHORITY_INVALID": category = "tls"; break;
        case "net::ERR_BLOCKED_BY_CLIENT":
        case "net::ERR_BLOCKED_BY_ADMINISTRATOR":
        case "net::ERR_BLOCKED_BY_RESPONSE":
        case "net::ERR_BLOCKED_BY_CSP": category = "blocked"; break;
        case "net::ERR_INSUFFICIENT_RESOURCES":
        case "net::ERR_OUT_OF_MEMORY": category = "resource"; break;
      }
      pageFailureCategoryCounts[category] += 1;
      pageFailedRequestCount += 1;
      activeRequests = Math.max(0, activeRequests - 1);
    });

    startedAt = now();
    const navigation = await deadline.wait("navigation", () => session.send("Page.navigate", {
      url: "https://fast.com/",
    }));
    if (navigation.errorText) throw new NavigationError(navigation.errorText);

    while (deadline.remaining() > 0) {
      await deadline.wait("display-poll", () => sleep(Math.min(1_000, deadline.remaining())));
      result = await deadline.wait("display-evaluate", () => evaluate(
        session,
        `(() => {
          const value = document.querySelector("#speed-value")?.textContent?.trim() ?? "";
          const units = document.querySelector("#speed-units")?.textContent?.trim() ?? "";
          const progress = document.querySelector("#speed-progress-indicator")?.getAttribute("class") ?? "";
          const loaded = document.readyState;
          return { value, units, progress, loaded };
        })()`,
      ));
      if (options.diagnosticLastState === true) lastStateAt = now();
      const spinnerStopped = /(?:^|\s)(?:succeeded|stopped|hidden)(?:\s|$)/i.test(result?.progress ?? "");
      // A running or missing indicator can hold one number while traffic stalls.
      // Only the observed terminal indicator completes the aggregate measurement.
      if (
        deadline.remaining() > 0 &&
        validFastDisplay(result?.value, result?.units) &&
        spinnerStopped
      ) {
        completionReason = "spinner-stopped";
        break;
      }
      activity?.("active", { elapsedMs: now() - startedAt, remainingMs: deadline.remaining(), pageRequestCount, result });
    }

    if (completionReason === null) throw new DeadlineError("display-poll");
  } catch (error) {
    failure = error;
    if (failure instanceof Error && failure.phase === undefined) failure.phase = deadline.phase;
  } finally {
    measuredAt = now();
    try {
      activity?.("ended", { elapsedMs: startedAt === undefined ? 0 : measuredAt - startedAt,
        remainingMs: deadline.remaining(), pageRequestCount, result });
    } catch (error) {
      cleanupComplete = false;
      if (!failure) failure = error;
    }
    try { session?.close(); } catch { cleanupComplete = false; }
    if (targetId) {
      try {
        // Send even when the deadline has just expired, so Chrome can release
        // this owned page. Never wait past the shared budget for its reply.
        const closing = browser.send("Target.closeTarget", { targetId });
        closing.catch(() => {});
        await deadline.wait("target-cleanup", () => closing, 1_000);
      } catch (error) {
        cleanupComplete = false;
        if (!failure) {
          failure = error;
          if (failure instanceof Error && failure.phase === undefined) failure.phase = "target-cleanup";
        }
      }
    }
    try { browser?.close(); } catch { cleanupComplete = false; }
    deadline.controller.abort();
  }
  if (failure && !(failure instanceof DeadlineError)) throw failure;
  const elapsedMs = startedAt === undefined ? 0 : measuredAt - startedAt;
  const measurement = {
    type: "fast-result",
    displayValue: result?.value ?? "",
    displayUnits: result?.units ?? "",
    elapsedMs: Math.round(elapsedMs),
    totalElapsedMs: Math.round(now() - lifecycleStartedAt),
    pageEncodedBytes: Math.round(pageEncodedBytes),
    pageObservedMbps:
      elapsedMs > 0 ? Math.round((pageEncodedBytes * 8 * 100) / elapsedMs / 1_000) / 100 : 0,
    pageRequestCount,
    pageCompletedRequestCount,
    pageFailedRequestCount,
    pageFailureScope: "page-target-only",
    pageFailureCategoryCounts,
    pageMaxParallelRequests: maxParallelRequests,
    completed: completionReason !== null,
    completionReason,
    timedOut: failure instanceof DeadlineError,
    timeoutPhase: failure instanceof DeadlineError ? failure.phase : null,
    cleanupComplete,
  };
  measurement.failureReason = validateFastMeasurement(measurement);
  measurement.valid = measurement.failureReason === null;
  if (options.diagnosticLastState === true) {
    // One finite state, never arbitrary page text, classes, URLs or history.
    const classes = typeof result?.progress === "string" ? result.progress.split(/\s+/).map(value => value.toLowerCase()) : [];
    const terminal = ["succeeded", "stopped", "hidden"].find(value => classes.includes(value));
    measurement.diagnosticLastState = { qualificationEligible: false,
      observed: lastStateAt !== undefined,
      elapsedMs: lastStateAt === undefined || startedAt === undefined ? null : Math.round(lastStateAt - startedAt),
      indicator: lastStateAt === undefined ? "unobserved" : terminal ?? (classes.some(Boolean) ? "nonterminal" : "missing"),
      documentReadyState: ["loading", "interactive", "complete"].includes(result?.loaded) ? result.loaded : "unknown" };
  }
  return measurement;
}

export async function main(argv = process.argv.slice(2), dependencies = {}) {
  const options = parseArgs(argv);
  const emit = dependencies.emit ?? console.log;
  if (options.help) {
    emit(usage());
    return 0;
  }
  const measurement = await runFastBenchmark(options, dependencies);
  await emit(JSON.stringify(measurement));
  return measurement.valid ? 0 : 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  // Undici's WebSocket close handshake may itself retain a host handle after
  // close(). The owned target has already had its bounded cleanup attempt;
  // flush the aggregate before ending the CLI, even if Chrome never replies.
  main(process.argv.slice(2), { emit: value => new Promise(resolve => process.stdout.write(`${value}\n`, resolve)) })
    .then(code => process.exit(code)).catch(error => {
      const phases = new Set(["browser-discovery", "browser-open", "target-create", "target-discovery", "page-open",
        "page-enable", "cache-disable", "cache-clear", "navigation", "display-poll", "display-evaluate", "target-cleanup"]);
      process.stderr.write(`${JSON.stringify({ type: "fast-error", ...cdpFailureDetails(error),
        ...(error instanceof NavigationError && error.category
          ? { reason: "navigation-failed", navigationFailureCategory: error.category } : {}),
        ...(phases.has(error?.phase) ? { phase: error.phase } : {}) })}\n`, () => process.exit(1));
    });
}
