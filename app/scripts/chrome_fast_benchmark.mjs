#!/usr/bin/env node

// Run Fast.com's real browser workload through an adb-forwarded Android Chrome
// DevTools socket. Output intentionally contains aggregate timings and byte
// counts only: Fast.com's ephemeral download URLs may contain signed tokens and
// must not be copied into benchmark logs.

import process from "node:process";
import { pathToFileURL } from "node:url";

export function parseArgs(argv) {
  const options = { port: 9222, timeoutMs: 90_000, stableMs: 5_000 };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--port") options.port = Number(argv[++i]);
    else if (arg === "--timeout-ms") options.timeoutMs = Number(argv[++i]);
    else if (arg === "--stable-ms") options.stableMs = Number(argv[++i]);
    else if (arg === "--help" || arg === "-h") options.help = true;
    else throw new Error(`unknown option: ${arg}`);
  }
  for (const [name, value] of Object.entries(options)) {
    if (name !== "help" && (!Number.isSafeInteger(value) || value <= 0 || value > 2_147_483_647)) {
      throw new Error(`${name} must be a positive number`);
    }
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
    "  --stable-ms MS       unchanged result interval (default 5000)",
  ].join("\n");
}

class DeadlineError extends Error {
  constructor(phase) { super("Fast.com lifecycle deadline exceeded"); this.phase = phase; }
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

class CdpSession {
  constructor(webSocketUrl) {
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.socket = new WebSocket(webSocketUrl);
    this.failure = null;
    this.socket.addEventListener("close", () => this.fail(new Error("DevTools websocket closed")));
    this.socket.addEventListener("error", () => this.fail(new Error("DevTools websocket failed")));
  }

  fail(error) {
    this.failure ??= error;
    for (const pending of this.pending.values()) pending.reject(this.failure);
    this.pending.clear();
  }

  async open(timeoutMs) {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(
        () => reject(new Error("DevTools websocket open timeout")),
        timeoutMs,
      );
      this.socket.addEventListener(
        "open",
        () => {
          clearTimeout(timer);
          resolve();
        },
        { once: true },
      );
      this.socket.addEventListener(
        "error",
        () => {
          clearTimeout(timer);
          reject(new Error("DevTools websocket open failed"));
        },
        { once: true },
      );
      this.socket.addEventListener("message", (event) => {
        let message;
        try { message = JSON.parse(event.data); }
        catch { this.fail(new Error("Invalid DevTools response")); return; }
        if (message.id !== undefined) {
          const pending = this.pending.get(message.id);
          if (!pending) return;
          this.pending.delete(message.id);
          if (message.error) pending.reject(new Error(message.error.message));
          else pending.resolve(message.result ?? {});
          return;
        }
        const listeners = this.listeners.get(message.method);
        if (listeners) {
          for (const listener of listeners) listener(message.params ?? {});
        }
      });
    });
  }

  send(method, params = {}) {
    if (this.failure) return Promise.reject(this.failure);
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      try { this.socket.send(JSON.stringify({ id, method, params })); }
      catch (error) { this.pending.delete(id); reject(error); }
    });
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? new Set();
    listeners.add(listener);
    this.listeners.set(method, listeners);
  }

  close() {
    this.fail(new Error("DevTools session closed"));
    this.listeners.clear();
    this.socket.close();
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
  if (result.completed !== true) return "incomplete-result";
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
  const baseUrl = `http://127.0.0.1:${options.port}`;
  let browser;
  let targetId;
  let target;
  let session;
  let startedAt;
  let measuredAt;
  let result;
  let completionReason = null;
  let failure;
  let cleanupComplete = true;
  let pageEncodedBytes = 0;
  let pageRequestCount = 0;
  let pageCompletedRequestCount = 0;
  let pageFailedRequestCount = 0;
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
    session.on("Network.loadingFailed", () => {
      pageFailedRequestCount += 1;
      activeRequests = Math.max(0, activeRequests - 1);
    });

    startedAt = now();
    const navigation = await deadline.wait("navigation", () => session.send("Page.navigate", {
      url: "https://fast.com/",
    }));
    if (navigation.errorText) throw new Error(navigation.errorText);

    let lastDisplay = "";
    let displayChangedAt = now();
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
      const display = `${result?.value ?? ""}|${result?.units ?? ""}`;
      if (display !== lastDisplay) {
        lastDisplay = display;
        displayChangedAt = now();
      }
      const spinnerStopped = /(?:^|\s)(?:succeeded|stopped|hidden)(?:\s|$)/i.test(result?.progress ?? "");
      if (
        deadline.remaining() > 0 &&
        validFastDisplay(result?.value, result?.units) &&
        (spinnerStopped || now() - displayChangedAt >= options.stableMs)
      ) {
        completionReason = spinnerStopped ? "spinner-stopped" : "stable-display";
        break;
      }
    }

    if (completionReason === null) throw new DeadlineError("display-poll");
  } catch (error) {
    failure = error;
  } finally {
    measuredAt = now();
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
        failure ??= error;
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
    pageMaxParallelRequests: maxParallelRequests,
    completed: completionReason !== null,
    completionReason,
    timedOut: failure instanceof DeadlineError,
    timeoutPhase: failure instanceof DeadlineError ? failure.phase : null,
    cleanupComplete,
  };
  measurement.failureReason = validateFastMeasurement(measurement);
  measurement.valid = measurement.failureReason === null;
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
    .then(code => process.exit(code)).catch(() => {
      process.stderr.write("Fast.com benchmark failed\n", () => process.exit(1));
    });
}
