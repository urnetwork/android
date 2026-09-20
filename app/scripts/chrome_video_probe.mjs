#!/usr/bin/env node

// Verify that media really advances in an Android Chrome target. Navigation
// without --target-id owns a fresh blank target and closes it on every exit.
// The output is deliberately limited to page/video state: it never records
// request URLs, response headers, cookies, media manifests, or signed tokens.

import process from "node:process";
import { pathToFileURL } from "node:url";

export function parseArgs(argv) {
  const options = {
    port: 9222,
    targetId: "",
    timeoutMs: 45_000,
    intervalMs: 1_000,
    reload: false,
    navigateUrl: "",
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--port") {
      options.port = Number(argv[++i]);
    } else if (arg === "--target-id") {
      options.targetId = argv[++i] ?? "";
    } else if (arg === "--timeout-ms") {
      options.timeoutMs = Number(argv[++i]);
    } else if (arg === "--interval-ms") {
      options.intervalMs = Number(argv[++i]);
    } else if (arg === "--reload") {
      options.reload = true;
    } else if (arg === "--navigate") {
      options.navigateUrl = argv[++i] ?? "";
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    } else {
      throw new Error(`unknown option: ${arg}`);
    }
  }
  if (
    !Number.isInteger(options.port) || options.port <= 0 ||
    !Number.isFinite(options.timeoutMs) || options.timeoutMs <= 0 ||
    !Number.isFinite(options.intervalMs) || options.intervalMs <= 0
  ) {
    throw new Error("port, timeout, and interval must be positive numbers");
  }
  if (options.reload && options.navigateUrl) {
    throw new Error("--reload and --navigate are mutually exclusive");
  }
  return options;
}

function usage() {
  return [
    "usage: chrome_video_probe.mjs (--target-id ID | --navigate URL) [options]",
    "",
    "options:",
    "  --port PORT          adb-forwarded DevTools port (default 9222)",
    "  --target-id ID       existing Chrome page target (otherwise --navigate owns a new target)",
    "  --timeout-ms MS      playback deadline (default 45000)",
    "  --interval-ms MS     sampling interval (default 1000)",
    "  --reload             reload once with Chrome's cache disabled",
    "  --navigate URL       navigate once after instrumentation is active",
  ].join("\n");
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.json();
}

class CdpSession {
  constructor(url) {
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.socket = new WebSocket(url);
    this.socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.id === undefined) {
        const listeners = this.listeners.get(message.method);
        if (listeners) {
          for (const listener of listeners) listener(message.params ?? {});
        }
        return;
      }
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) {
        pending.reject(new Error(`${pending.method}: ${message.error.message}`));
      } else {
        pending.resolve(message.result ?? {});
      }
    });
  }

  async open(timeoutMs) {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(
        () => reject(new Error("DevTools websocket open timeout")),
        timeoutMs,
      );
      this.socket.addEventListener("open", () => {
        clearTimeout(timer);
        resolve();
      }, { once: true });
      this.socket.addEventListener("error", () => {
        clearTimeout(timer);
        reject(new Error("DevTools websocket open failed"));
      }, { once: true });
    });
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { method, resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? new Set();
    listeners.add(listener);
    this.listeners.set(method, listeners);
    return () => listeners.delete(listener);
  }

  close() {
    this.socket.close();
  }
}

const videoStateExpression = `(() => {
  const videos = [...document.querySelectorAll("video")];
  const video = videos[0];
  let bufferedSeconds = 0;
  if (video && video.buffered.length > 0) {
    bufferedSeconds = Math.max(
      0,
      video.buffered.end(video.buffered.length - 1) - video.currentTime,
    );
  }
  return {
    title: document.title,
    documentReadyState: document.readyState,
    robotChallenge:
      /are you a robot/i.test(document.title) ||
      /are you a robot/i.test(document.body?.innerText ?? ""),
    videoCount: videos.length,
    video: video ? {
      currentTime: video.currentTime,
      duration: Number.isFinite(video.duration) ? video.duration : null,
      readyState: video.readyState,
      networkState: video.networkState,
      paused: video.paused,
      ended: video.ended,
      bufferedSeconds,
      errorCode: video.error?.code ?? null,
    } : null,
  };
})()`;

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

export function safeHost(value) {
  try {
    return new URL(value).hostname;
  } catch {
    return "invalid";
  }
}

export function summarizeRejectedConnections(responses) {
  const rejected = responses.filter((response) => response.status === 403);
  return rejected.map((response) => {
    const later = responses.filter(
      (candidate) =>
        candidate.host === response.host &&
        candidate.timestamp > response.timestamp,
    );
    return {
      host: response.host,
      resourceType: response.resourceType,
      status: response.status,
      protocol: response.protocol,
      connectionId: response.connectionId,
      connectionReused: response.connectionReused,
      laterResponseCount: later.length,
      retryOnSameConnection: later.some(
        (candidate) => candidate.connectionId === response.connectionId,
      ),
      retryOnNewConnection: later.some(
        (candidate) =>
          candidate.connectionId &&
          candidate.connectionId !== response.connectionId,
      ),
    };
  });
}

// A caller-owned target remains caller-owned. With --navigate only, acquire
// an explicit blank target, never an arbitrary existing tab. Opening the site
// happens later, after the Network/Runtime observers have been installed.
export async function acquireVideoTarget(options, dependencies = {}) {
  if (!options.targetId && !options.navigateUrl) throw new Error("--target-id or --navigate is required");
  const readJson = dependencies.fetchJson ?? fetchJson;
  const createSession = dependencies.createSession ?? ((url) => new CdpSession(url));
  const now = dependencies.now ?? Date.now;
  const pause = dependencies.sleep ?? delay;
  const baseUrl = `http://127.0.0.1:${options.port}`;
  let browser;
  let ownedTargetId;
  let closed = false;
  const close = async () => {
    if (closed) return;
    closed = true;
    try {
      if (ownedTargetId) {
        const result = await browser.send("Target.closeTarget", { targetId: ownedTargetId });
        if (result.success !== true) throw new Error("Chrome owned target cleanup failed");
      }
    } finally { browser?.close(); }
  };
  try {
    if (!options.targetId) {
      const version = await readJson(`${baseUrl}/json/version`);
      browser = createSession(version.webSocketDebuggerUrl);
      await browser.open(Math.min(options.timeoutMs, 10_000));
      const created = await browser.send("Target.createTarget", { url: "about:blank" });
      if (typeof created.targetId !== "string" || !created.targetId) throw new Error("Chrome target unavailable");
      ownedTargetId = created.targetId;
    }
    const deadline = now() + Math.min(options.timeoutMs, 5_000);
    do {
      const targets = await readJson(`${baseUrl}/json/list`);
      const target = targets.find((candidate) => candidate.id === (ownedTargetId ?? options.targetId));
      if (target?.webSocketDebuggerUrl && target.type === "page") return { target, close };
      if (!ownedTargetId) break;
      await pause(25);
    } while (now() < deadline);
    throw new Error("Chrome target unavailable");
  } catch (error) {
    await close();
    throw error;
  }
}

export async function runVideoProbe(options, dependencies = {}) {
  const lease = await acquireVideoTarget(options, dependencies);
  const createSession = dependencies.createSession ?? ((url) => new CdpSession(url));
  const now = dependencies.now ?? Date.now;
  const pause = dependencies.sleep ?? delay;
  let session;
  try {
    session = createSession(lease.target.webSocketDebuggerUrl);
    await session.open(Math.min(options.timeoutMs, 10_000));
    await session.send("Runtime.enable");
    const requests = new Map();
    const responses = [];
    session.on("Network.requestWillBeSent", (event) => {
      if (requests.size >= 4_096) return;
      requests.set(event.requestId, {
        host: safeHost(event.request?.url),
        resourceType: event.type ?? "Other",
        timestamp: event.timestamp ?? 0,
      });
    });
    session.on("Network.responseReceived", (event) => {
      if (responses.length >= 4_096) return;
      const request = requests.get(event.requestId) ?? {};
      responses.push({
        host: request.host ?? safeHost(event.response?.url),
        resourceType: event.type ?? request.resourceType ?? "Other",
        timestamp: event.timestamp ?? request.timestamp ?? 0,
        status: Math.trunc(event.response?.status ?? 0),
        protocol: event.response?.protocol ?? "",
        connectionId: String(event.response?.connectionId ?? ""),
        connectionReused: Boolean(event.response?.connectionReused),
      });
    });

    const deadline = now() + options.timeoutMs;
    let navigationCommitted = !(options.reload || options.navigateUrl);
    if (options.reload || options.navigateUrl) {
      await session.send("Network.enable");
      await session.send("Network.setCacheDisabled", { cacheDisabled: true });
      await session.send("Runtime.evaluate", {
        expression: "window.__urnetworkVideoProbeReloadMarker = true",
      });
      if (options.navigateUrl) {
        await session.send("Page.navigate", { url: options.navigateUrl });
      } else {
        await session.send("Page.reload", { ignoreCache: true });
      }
      while (now() < deadline) {
        const result = await session.send("Runtime.evaluate", {
          // A video may already play while an unrelated ad/telemetry resource
          // holds document.readyState at interactive. Wait only for the new
          // document, then observe media throughout the remaining deadline.
          expression: "typeof window.__urnetworkVideoProbeReloadMarker === 'undefined'",
          returnByValue: true,
        });
        if (result.result?.value === true) {
          navigationCommitted = true;
          break;
        }
        await pause(Math.min(options.intervalMs, 250));
      }
    }

    let first = null;
    let last = null;
    let maximumReadyState = 0;
    let playbackProgressed = false;
    let sampleCount = 0;
    while (now() < deadline) {
      const result = await session.send("Runtime.evaluate", {
        expression: videoStateExpression,
        returnByValue: true,
      });
      last = result.result?.value ?? null;
      sampleCount += 1;
      if (last?.video) {
        first ??= last;
        maximumReadyState = Math.max(maximumReadyState, last.video.readyState);
        playbackProgressed =
          last.video.currentTime >= first.video.currentTime + 1 &&
          last.video.readyState >= 2;
      }
      if (playbackProgressed || last?.robotChallenge || last?.video?.errorCode) break;
      await pause(options.intervalMs);
    }

    const output = {
      targetId: lease.target.id,
      reload: options.reload,
      navigated: Boolean(options.navigateUrl),
      navigationCommitted,
      observationStatus: !navigationCommitted ? "navigation-timeout" : sampleCount ? "observed" : "observation-timeout",
      playbackProgressed,
      sampleCount,
      maximumReadyState,
      rejectedConnections: summarizeRejectedConnections(responses),
      first,
      last,
    };
    return output;
  } finally {
    try { session?.close(); } finally { await lease.close(); }
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(usage());
    return;
  }
  const output = await runVideoProbe(options);
  console.log(JSON.stringify(output, null, 2));
  if (!output.playbackProgressed) process.exitCode = 2;
}

if (
  process.argv[1] &&
  pathToFileURL(process.argv[1]).href === import.meta.url
) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
