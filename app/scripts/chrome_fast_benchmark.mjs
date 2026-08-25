#!/usr/bin/env node

// Run Fast.com's real browser workload through an adb-forwarded Android Chrome
// DevTools socket. Output intentionally contains aggregate timings and byte
// counts only: Fast.com's ephemeral download URLs may contain signed tokens and
// must not be copied into benchmark logs.

import process from "node:process";

function parseArgs(argv) {
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

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(usage());
    return;
  }
  const baseUrl = `http://127.0.0.1:${options.port}`;
  const version = await fetchJson(`${baseUrl}/json/version`);
  const browser = new CdpSession(version.webSocketDebuggerUrl);
  let target;
  let session;
  try {
    await browser.open(options.timeoutMs);
    const { targetId } = await browser.send("Target.createTarget", {
      url: "about:blank",
    });
    const targetDeadline = Date.now() + 5_000;
    while (Date.now() < targetDeadline) {
      const targets = await fetchJson(`${baseUrl}/json/list`);
      target = targets.find((candidate) => candidate.id === targetId);
      if (target?.webSocketDebuggerUrl) break;
      await new Promise((resolve) => setTimeout(resolve, 25));
    }
    if (!target?.webSocketDebuggerUrl) throw new Error("Chrome target unavailable");

    session = new CdpSession(target.webSocketDebuggerUrl);
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
    let pageFailedRequestCount = 0;
    let activeRequests = 0;
    let maxParallelRequests = 0;
    session.on("Network.requestWillBeSent", () => {
      pageRequestCount += 1;
      activeRequests += 1;
      maxParallelRequests = Math.max(maxParallelRequests, activeRequests);
    });
    session.on("Network.loadingFinished", (event) => {
      pageEncodedBytes += Math.max(0, event.encodedDataLength ?? 0);
      activeRequests = Math.max(0, activeRequests - 1);
    });
    session.on("Network.loadingFailed", () => {
      pageFailedRequestCount += 1;
      activeRequests = Math.max(0, activeRequests - 1);
    });

    const startedAt = performance.now();
    const navigation = await session.send("Page.navigate", {
      url: "https://fast.com/",
    });
    if (navigation.errorText) throw new Error(navigation.errorText);

    let lastDisplay = "";
    let displayChangedAt = performance.now();
    let observedResult = false;
    let result;
    while (performance.now() - startedAt < options.timeoutMs) {
      await new Promise((resolve) => setTimeout(resolve, 1_000));
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
      const display = `${result.value}|${result.units}`;
      if (display !== lastDisplay) {
        lastDisplay = display;
        displayChangedAt = performance.now();
      }
      observedResult ||= /\d/.test(result.value) && Number(result.value) > 0;
      const spinnerStopped = /succeeded|stopped|hidden/i.test(result.progress);
      if (
        observedResult &&
        (spinnerStopped || performance.now() - displayChangedAt >= options.stableMs)
      ) {
        break;
      }
    }

    const elapsedMs = performance.now() - startedAt;
    console.log(
      JSON.stringify({
        type: "fast-result",
        displayValue: result?.value ?? "",
        displayUnits: result?.units ?? "",
        elapsedMs: Math.round(elapsedMs),
        pageEncodedBytes: Math.round(pageEncodedBytes),
        pageObservedMbps:
          Math.round((pageEncodedBytes * 8 * 100) / elapsedMs / 1_000) / 100,
        pageRequestCount,
        pageFailedRequestCount,
        pageMaxParallelRequests: maxParallelRequests,
        completed: observedResult,
      }),
    );
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

main().catch((error) => {
  console.error(error.stack ?? String(error));
  process.exitCode = 1;
});
