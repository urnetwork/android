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
    if (name !== "help" && (!Number.isFinite(value) || value <= 0)) {
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
    "  --timeout-ms MS      maximum test duration (default 90000)",
    "  --stable-ms MS       unchanged result interval (default 5000)",
  ].join("\n");
}

class CdpSession {
  constructor(webSocketUrl) {
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.socket = new WebSocket(webSocketUrl);
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
        const message = JSON.parse(event.data);
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
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? new Set();
    listeners.add(listener);
    this.listeners.set(method, listeners);
  }

  close() {
    this.socket.close();
  }
}

async function fetchJson(url) {
  const response = await fetch(url);
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
  const baseUrl = `http://127.0.0.1:${options.port}`;
  const version = await readJson(`${baseUrl}/json/version`);
  const browser = createSession(version.webSocketDebuggerUrl);
  let target;
  let session;
  try {
    await browser.open(options.timeoutMs);
    const { targetId } = await browser.send("Target.createTarget", {
      url: "about:blank",
    });
    const targetDeadline = now() + 5_000;
    while (now() < targetDeadline) {
      const targets = await readJson(`${baseUrl}/json/list`);
      target = targets.find((candidate) => candidate.id === targetId);
      if (target?.webSocketDebuggerUrl) break;
      await sleep(25);
    }
    if (!target?.webSocketDebuggerUrl) throw new Error("Chrome target unavailable");

    session = createSession(target.webSocketDebuggerUrl);
    await session.open(options.timeoutMs);
    await Promise.all([
      session.send("Page.enable"),
      session.send("Network.enable"),
      session.send("Runtime.enable"),
    ]);
    await session.send("Network.setCacheDisabled", { cacheDisabled: true });
    await session.send("Network.clearBrowserCache");

    // CDP reports only requests owned by this page target. Fast.com may move
    // downloads into a worker/child target, so these are diagnostic page
    // counters, not the authoritative speed-test byte total. The displayed
    // result remains Fast.com's canonical aggregate measurement.
    let pageEncodedBytes = 0;
    let pageRequestCount = 0;
    let pageCompletedRequestCount = 0;
    let pageFailedRequestCount = 0;
    let activeRequests = 0;
    let maxParallelRequests = 0;
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

    const startedAt = now();
    const navigation = await session.send("Page.navigate", {
      url: "https://fast.com/",
    });
    if (navigation.errorText) throw new Error(navigation.errorText);

    let lastDisplay = "";
    let displayChangedAt = now();
    let completionReason = null;
    let result;
    while (now() - startedAt < options.timeoutMs) {
      await sleep(Math.min(1_000, options.timeoutMs - (now() - startedAt)));
      result = await evaluate(
        session,
        `(() => {
          const value = document.querySelector("#speed-value")?.textContent?.trim() ?? "";
          const units = document.querySelector("#speed-units")?.textContent?.trim() ?? "";
          const progress = document.querySelector("#speed-progress-indicator")?.getAttribute("class") ?? "";
          const loaded = document.readyState;
          return { value, units, progress, loaded };
        })()`,
      );
      const display = `${result?.value ?? ""}|${result?.units ?? ""}`;
      if (display !== lastDisplay) {
        lastDisplay = display;
        displayChangedAt = now();
      }
      const spinnerStopped = /(?:^|\s)(?:succeeded|stopped|hidden)(?:\s|$)/i.test(result?.progress ?? "");
      if (
        now() - startedAt <= options.timeoutMs &&
        validFastDisplay(result?.value, result?.units) &&
        (spinnerStopped || now() - displayChangedAt >= options.stableMs)
      ) {
        completionReason = spinnerStopped ? "spinner-stopped" : "stable-display";
        break;
      }
    }

    const elapsedMs = now() - startedAt;
    const measurement = {
      type: "fast-result",
      displayValue: result?.value ?? "",
      displayUnits: result?.units ?? "",
      elapsedMs: Math.round(elapsedMs),
      pageEncodedBytes: Math.round(pageEncodedBytes),
      pageObservedMbps:
        Math.round((pageEncodedBytes * 8 * 100) / elapsedMs / 1_000) / 100,
      pageRequestCount,
      pageCompletedRequestCount,
      pageFailedRequestCount,
      pageMaxParallelRequests: maxParallelRequests,
      completed: completionReason !== null,
      completionReason,
    };
    measurement.failureReason = validateFastMeasurement(measurement);
    measurement.valid = measurement.failureReason === null;
    return measurement;
  } finally {
    session?.close();
    if (target?.id) {
      try {
        await browser.send("Target.closeTarget", { targetId: target.id });
      } catch {
        // Chrome may have already closed the page.
      }
    }
    browser.close();
  }
}

export async function main(argv = process.argv.slice(2), dependencies = {}) {
  const options = parseArgs(argv);
  const emit = dependencies.emit ?? console.log;
  if (options.help) {
    emit(usage());
    return 0;
  }
  const measurement = await runFastBenchmark(options, dependencies);
  emit(JSON.stringify(measurement));
  return measurement.valid ? 0 : 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().then((code) => { process.exitCode = code; }).catch((error) => {
    console.error(error.stack ?? String(error));
    process.exitCode = 1;
  });
}
