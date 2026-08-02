#!/usr/bin/env node

// Measure a real Android Chrome navigation through an adb-forwarded DevTools
// socket without adding npm dependencies.
//
// Example:
//   adb -s SERIAL forward tcp:9222 localabstract:chrome_devtools_remote
//   node app/scripts/chrome_page_benchmark.mjs \
//     --port 9222 --runs 3 https://www.wikipedia.org/
//
// Network cache is disabled so repeated samples measure the tunnel rather than
// HTTP asset-cache hits. Restart Chrome before invoking the script when a cold
// browser DNS/connection sample is required.

import process from "node:process";

function parseArgs(argv) {
  const options = {
    port: 9222,
    runs: 1,
    timeoutMs: 60_000,
    settleMs: 100,
    freshContext: false,
    fetchMode: false,
    targetId: undefined,
    waterfall: false,
    urls: [],
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--port") {
      options.port = Number(argv[++i]);
    } else if (arg === "--runs") {
      options.runs = Number(argv[++i]);
    } else if (arg === "--timeout-ms") {
      options.timeoutMs = Number(argv[++i]);
    } else if (arg === "--settle-ms") {
      options.settleMs = Number(argv[++i]);
    } else if (arg === "--fresh-context") {
      options.freshContext = true;
    } else if (arg === "--fetch") {
      options.fetchMode = true;
    } else if (arg === "--target-id") {
      options.targetId = argv[++i];
    } else if (arg === "--waterfall") {
      options.waterfall = true;
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    } else if (arg.startsWith("--")) {
      throw new Error(`unknown option: ${arg}`);
    } else {
      options.urls.push(arg);
    }
  }
  if (
    !Number.isInteger(options.port) ||
    options.port <= 0 ||
    !Number.isInteger(options.runs) ||
    options.runs <= 0 ||
    !Number.isFinite(options.timeoutMs) ||
    options.timeoutMs <= 0 ||
    !Number.isFinite(options.settleMs) ||
    options.settleMs < 0
  ) {
    throw new Error("port, runs, and timeout must be positive numbers");
  }
  return options;
}

function usage() {
  return [
    "usage: chrome_page_benchmark.mjs [options] URL...",
    "",
    "options:",
    "  --port PORT          adb-forwarded DevTools port (default 9222)",
    "  --runs COUNT         repetitions per URL (default 1)",
    "  --timeout-ms MS      navigation timeout (default 60000)",
    "  --settle-ms MS       collect trailing events after load (default 100)",
    "  --fresh-context      isolated cache, cookies, DNS, and connections per run",
    "  --fetch              stream each URL with fetch instead of navigating",
    "  --target-id ID       navigate and close an existing target (one sample only)",
    "  --waterfall          emit every cross-origin request timing",
  ].join("\n");
}

class CdpSession {
  constructor(webSocketUrl) {
    this.nextId = 1;
    this.pending = new Map();
    this.waiters = new Map();
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
        this.handleMessage(event.data);
      });
      this.socket.addEventListener("close", () => {
        const error = new Error("DevTools websocket closed");
        for (const { reject: rejectPending } of this.pending.values()) {
          rejectPending(error);
        }
        this.pending.clear();
        for (const eventWaiters of this.waiters.values()) {
          for (const waiter of eventWaiters) {
            clearTimeout(waiter.timer);
            waiter.reject(error);
          }
        }
        this.waiters.clear();
      });
    });
  }

  handleMessage(data) {
    const message = JSON.parse(data);
    if (message.id !== undefined) {
      const pending = this.pending.get(message.id);
      if (!pending) {
        return;
      }
      this.pending.delete(message.id);
      if (message.error) {
        pending.reject(
          new Error(
            `${pending.method}: ${message.error.message ?? JSON.stringify(message.error)}`,
          ),
        );
      } else {
        pending.resolve(message.result ?? {});
      }
      return;
    }
    if (!message.method) {
      return;
    }
    const eventListeners = this.listeners.get(message.method);
    if (eventListeners) {
      for (const listener of [...eventListeners]) {
        listener(message.params ?? {});
      }
    }
    const eventWaiters = this.waiters.get(message.method);
    if (!eventWaiters) {
      return;
    }
    for (const waiter of [...eventWaiters]) {
      if (!waiter.predicate(message.params ?? {})) {
        continue;
      }
      clearTimeout(waiter.timer);
      eventWaiters.delete(waiter);
      waiter.resolve(message.params ?? {});
    }
    if (eventWaiters.size === 0) {
      this.waiters.delete(message.method);
    }
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { method, resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  waitFor(method, predicate, timeoutMs) {
    return new Promise((resolve, reject) => {
      const eventWaiters = this.waiters.get(method) ?? new Set();
      const waiter = {
        predicate,
        resolve,
        reject,
        timer: undefined,
      };
      waiter.timer = setTimeout(() => {
        eventWaiters.delete(waiter);
        if (eventWaiters.size === 0) {
          this.waiters.delete(method);
        }
        reject(new Error(`${method} timeout after ${timeoutMs} ms`));
      }, timeoutMs);
      eventWaiters.add(waiter);
      this.waiters.set(method, eventWaiters);
    });
  }

  on(method, listener) {
    const eventListeners = this.listeners.get(method) ?? new Set();
    eventListeners.add(listener);
    this.listeners.set(method, eventListeners);
    return () => {
      eventListeners.delete(listener);
      if (eventListeners.size === 0) {
        this.listeners.delete(method);
      }
    };
  }

  close() {
    this.socket.close();
  }
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`${options.method ?? "GET"} ${url}: HTTP ${response.status}`);
  }
  return response.json();
}

async function createTarget(
  baseUrl,
  browserWebSocketUrl,
  timeoutMs,
  freshContext,
  existingTargetId,
) {
  if (existingTargetId) {
    const targets = await fetchJson(`${baseUrl}/json/list`);
    const target = targets.find(
      (candidate) => candidate.id === existingTargetId,
    );
    if (!target?.webSocketDebuggerUrl) {
      throw new Error(`existing target ${existingTargetId} was not exposed`);
    }
    return { target, browserContextId: undefined };
  }
  const browser = new CdpSession(browserWebSocketUrl);
  try {
    await browser.open(timeoutMs);
    let browserContextId;
    if (freshContext) {
      ({ browserContextId } = await browser.send(
        "Target.createBrowserContext",
      ));
    }
    const createParams = {
      url: "about:blank",
    };
    if (browserContextId) {
      createParams.browserContextId = browserContextId;
    }
    const { targetId } = await browser.send(
      "Target.createTarget",
      createParams,
    );
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const targets = await fetchJson(`${baseUrl}/json/list`);
      const target = targets.find((candidate) => candidate.id === targetId);
      if (target?.webSocketDebuggerUrl) {
        return { target, browserContextId };
      }
      await new Promise((resolve) => setTimeout(resolve, 25));
    }
    throw new Error(`created target ${targetId} was not exposed by Chrome`);
  } finally {
    browser.close();
  }
}

async function closeTarget(
  browserWebSocketUrl,
  targetId,
  browserContextId,
  timeoutMs,
) {
  const browser = new CdpSession(browserWebSocketUrl);
  try {
    await browser.open(timeoutMs);
    await browser.send("Target.closeTarget", { targetId });
    if (browserContextId) {
      await browser.send("Target.disposeBrowserContext", {
        browserContextId,
      });
    }
  } finally {
    browser.close();
  }
}

function round(value) {
  return Number.isFinite(value) ? Math.round(value * 100) / 100 : null;
}

function median(values) {
  if (values.length === 0) {
    return null;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 1) {
    return sorted[middle];
  }
  return (sorted[middle - 1] + sorted[middle]) / 2;
}

function percentile(values, quantile) {
  if (values.length === 0) {
    return null;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil(quantile * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
}

function phaseMs(timing, startName, endName) {
  const start = timing?.[startName];
  const end = timing?.[endName];
  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start < 0 ||
    end < start
  ) {
    return null;
  }
  return end - start;
}

function maxOverlap(intervals) {
  const events = [];
  for (const [start, end] of intervals) {
    if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
      continue;
    }
    events.push([start, 1], [end, -1]);
  }
  events.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  let active = 0;
  let maximum = 0;
  for (const [, delta] of events) {
    active += delta;
    maximum = Math.max(maximum, active);
  }
  return maximum;
}

function safeUrlParts(value) {
  try {
    const parsed = new URL(value);
    return {
      host: parsed.hostname,
      origin: parsed.origin,
    };
  } catch {
    return {
      host: "",
      origin: "",
    };
  }
}

function summarizeRequests(requests) {
  if (requests.length === 0) {
    return {
      requestCount: 0,
      responseCount: 0,
      failedRequestCount: 0,
      distinctHostCount: 0,
      distinctOriginCount: 0,
      dnsLookupCount: 0,
      dnsLookupHostCount: 0,
      totalDnsMs: 0,
      medianDnsLookupMs: null,
      maxDnsLookupMs: null,
      maxParallelDns: 0,
      freshConnectionCount: 0,
      reusedConnectionCount: 0,
      maxParallelRequests: 0,
      medianRequestTtfbMs: null,
      p95RequestTtfbMs: null,
      maxRequestTtfbMs: null,
      dnsLookups: [],
      requestWaterfall: [],
    };
  }

  const firstTimestamp = Math.min(
    ...requests.map((request) => request.timestamp),
  );
  const hosts = new Set();
  const origins = new Set();
  const dnsHosts = new Set();
  const dnsDurations = [];
  const dnsIntervals = [];
  const dnsLookups = [];
  const requestIntervals = [];
  const requestTtfbDurations = [];
  let responseCount = 0;
  let failedRequestCount = 0;
  let freshConnectionCount = 0;
  let reusedConnectionCount = 0;

  const requestWaterfall = requests.map((request) => {
    const { host, origin } = safeUrlParts(request.url);
    if (host) {
      hosts.add(host);
    }
    if (origin && origin !== "null") {
      origins.add(origin);
    }
    const timing = request.response?.timing;
    const dnsMs = phaseMs(timing, "dnsStart", "dnsEnd");
    const tcpMs = phaseMs(timing, "connectStart", "connectEnd");
    const tlsMs = phaseMs(timing, "sslStart", "sslEnd");
    if (dnsMs !== null) {
      dnsDurations.push(dnsMs);
      if (host) {
        dnsHosts.add(host);
      }
      dnsIntervals.push([
        timing.requestTime * 1000 + timing.dnsStart,
        timing.requestTime * 1000 + timing.dnsEnd,
      ]);
      dnsLookups.push({
        host,
        offsetMs: round(
          (timing.requestTime - firstTimestamp) * 1000 + timing.dnsStart,
        ),
        finishMs: round(
          (timing.requestTime - firstTimestamp) * 1000 + timing.dnsEnd,
        ),
        dnsMs: round(dnsMs),
      });
    }
    if (request.response) {
      responseCount += 1;
      if (request.response.connectionReused) {
        reusedConnectionCount += 1;
      } else if (tcpMs !== null) {
        freshConnectionCount += 1;
      }
    }
    if (request.failed) {
      failedRequestCount += 1;
    }

    const requestTtfbMs =
      Number.isFinite(timing?.receiveHeadersStart) &&
      timing.receiveHeadersStart >= 0
        ? timing.receiveHeadersStart
        : Number.isFinite(request.responseTimestamp)
          ? (request.responseTimestamp - request.timestamp) * 1000
          : null;
    if (requestTtfbMs !== null) {
      requestTtfbDurations.push(requestTtfbMs);
    }
    const finishTimestamp =
      request.finishTimestamp ??
      request.responseTimestamp ??
      request.timestamp;
    requestIntervals.push([request.timestamp, finishTimestamp]);
    return {
      offsetMs: round((request.timestamp - firstTimestamp) * 1000),
      finishMs: round((finishTimestamp - firstTimestamp) * 1000),
      type: request.type,
      method: request.method,
      host,
      url: request.url,
      status: request.response?.status ?? null,
      protocol: request.response?.protocol ?? "",
      connectionReused: request.response?.connectionReused ?? null,
      dnsMs: round(dnsMs),
      tcpMs: round(tcpMs),
      tlsMs: round(tlsMs),
      requestTtfbMs: round(requestTtfbMs),
      encodedDataBytes:
        request.encodedDataLength ?? request.response?.encodedDataLength ?? 0,
      failed: request.failed?.errorText ?? null,
    };
  });

  requestWaterfall.sort((a, b) => a.offsetMs - b.offsetMs);
  return {
    requestCount: requests.length,
    responseCount,
    failedRequestCount,
    distinctHostCount: hosts.size,
    distinctOriginCount: origins.size,
    dnsLookupCount: dnsDurations.length,
    dnsLookupHostCount: dnsHosts.size,
    totalDnsMs: round(dnsDurations.reduce((sum, duration) => sum + duration, 0)),
    medianDnsLookupMs: round(median(dnsDurations)),
    maxDnsLookupMs: round(
      dnsDurations.length > 0 ? Math.max(...dnsDurations) : null,
    ),
    maxParallelDns: maxOverlap(dnsIntervals),
    freshConnectionCount,
    reusedConnectionCount,
    maxParallelRequests: maxOverlap(requestIntervals),
    medianRequestTtfbMs: round(median(requestTtfbDurations)),
    p95RequestTtfbMs: round(percentile(requestTtfbDurations, 0.95)),
    maxRequestTtfbMs: round(
      requestTtfbDurations.length > 0
        ? Math.max(...requestTtfbDurations)
        : null,
    ),
    dnsLookups: dnsLookups.sort((a, b) => a.offsetMs - b.offsetMs),
    requestWaterfall,
  };
}

async function measureNavigation(
  baseUrl,
  browserWebSocketUrl,
  url,
  run,
  timeoutMs,
  settleMs,
  freshContext,
  targetId,
  emitWaterfall,
) {
  const { target, browserContextId } = await createTarget(
    baseUrl,
    browserWebSocketUrl,
    timeoutMs,
    freshContext,
    targetId,
  );
  const session = new CdpSession(target.webSocketDebuggerUrl);
  const requests = [];
  const activeRequests = new Map();
  let closed = false;
  try {
    await session.open(timeoutMs);
    await Promise.all([
      session.send("Page.enable"),
      session.send("Network.enable"),
      session.send("Runtime.enable"),
    ]);
    await session.send("Network.setCacheDisabled", { cacheDisabled: true });
    await session.send("Network.clearBrowserCache");

    session.on("Network.requestWillBeSent", (event) => {
      const priorRequest = activeRequests.get(event.requestId);
      if (priorRequest && event.redirectResponse) {
        priorRequest.response = event.redirectResponse;
        priorRequest.responseTimestamp = event.timestamp;
        priorRequest.finishTimestamp = event.timestamp;
      }
      const request = {
        requestId: event.requestId,
        url: event.request.url,
        method: event.request.method,
        type: event.type,
        timestamp: event.timestamp,
      };
      requests.push(request);
      activeRequests.set(event.requestId, request);
    });
    session.on("Network.responseReceived", (event) => {
      const request = activeRequests.get(event.requestId);
      if (!request) {
        return;
      }
      request.response = event.response;
      request.responseTimestamp = event.timestamp;
    });
    session.on("Network.loadingFinished", (event) => {
      const request = activeRequests.get(event.requestId);
      if (!request) {
        return;
      }
      request.finishTimestamp = event.timestamp;
      request.encodedDataLength = event.encodedDataLength;
      activeRequests.delete(event.requestId);
    });
    session.on("Network.loadingFailed", (event) => {
      const request = activeRequests.get(event.requestId);
      if (!request) {
        return;
      }
      request.finishTimestamp = event.timestamp;
      request.failed = event;
      activeRequests.delete(event.requestId);
    });

    const load = session.waitFor("Page.loadEventFired", () => true, timeoutMs);
    const navigation = await session.send("Page.navigate", { url });
    if (navigation.errorText) {
      throw new Error(`navigation failed: ${navigation.errorText}`);
    }
    let loadError;
    try {
      await load;
    } catch (error) {
      loadError = error;
    }
    if (settleMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, settleMs));
    }

    const evaluated = await session.send("Runtime.evaluate", {
      awaitPromise: true,
      returnByValue: true,
      expression: `(() => {
        const navigation = performance.getEntriesByType("navigation")[0];
        if (!navigation) {
          throw new Error("navigation timing unavailable");
        }
        const resources = performance.getEntriesByType("resource");
        return {
          url: navigation.name,
          redirectCount: navigation.redirectCount,
          dnsMs: navigation.domainLookupEnd - navigation.domainLookupStart,
          tcpMs: navigation.connectEnd - navigation.connectStart,
          tlsMs: navigation.secureConnectionStart > 0
            ? navigation.connectEnd - navigation.secureConnectionStart
            : 0,
          requestToFirstByteMs: navigation.responseStart - navigation.requestStart,
          ttfbMs: navigation.responseStart - navigation.startTime,
          responseMs: navigation.responseEnd - navigation.responseStart,
          domContentLoadedMs: navigation.domContentLoadedEventEnd - navigation.startTime,
          loadMs: navigation.loadEventEnd > 0
            ? navigation.loadEventEnd - navigation.startTime
            : null,
          nextHopProtocol: navigation.nextHopProtocol,
          mainTransferBytes: navigation.transferSize,
          mainEncodedBodyBytes: navigation.encodedBodySize,
          resourceCount: resources.length,
          resourceTransferBytes: resources.reduce(
            (total, resource) => total + (resource.transferSize || 0),
            0,
          ),
          resourceEncodedBodyBytes: resources.reduce(
            (total, resource) => total + (resource.encodedBodySize || 0),
            0,
          ),
        };
      })()`,
    });
    if (evaluated.exceptionDetails) {
      throw new Error(
        evaluated.exceptionDetails.exception?.description ??
          evaluated.exceptionDetails.text,
      );
    }
    const timing = evaluated.result.value;
    const requestSummary = summarizeRequests(requests);
    const topSlowRequests = [...requestSummary.requestWaterfall]
      .filter((request) => Number.isFinite(request.requestTtfbMs))
      .sort((a, b) => b.requestTtfbMs - a.requestTtfbMs)
      .slice(0, 8);
    const result = {
      run,
      requestedUrl: url,
      finalUrl: timing.url,
      loadTimedOut: loadError !== undefined,
      loadError: loadError?.message ?? null,
      redirectCount: timing.redirectCount,
      dnsMs: round(timing.dnsMs),
      tcpMs: round(timing.tcpMs),
      tlsMs: round(timing.tlsMs),
      requestToFirstByteMs: round(timing.requestToFirstByteMs),
      ttfbMs: round(timing.ttfbMs),
      responseMs: round(timing.responseMs),
      domContentLoadedMs: round(timing.domContentLoadedMs),
      loadMs: round(timing.loadMs),
      nextHopProtocol: timing.nextHopProtocol,
      mainTransferBytes: timing.mainTransferBytes,
      mainEncodedBodyBytes: timing.mainEncodedBodyBytes,
      resourceCount: timing.resourceCount,
      totalTransferBytes:
        timing.mainTransferBytes + timing.resourceTransferBytes,
      totalEncodedBodyBytes:
        timing.mainEncodedBodyBytes + timing.resourceEncodedBodyBytes,
      requestCount: requestSummary.requestCount,
      responseCount: requestSummary.responseCount,
      failedRequestCount: requestSummary.failedRequestCount,
      distinctHostCount: requestSummary.distinctHostCount,
      distinctOriginCount: requestSummary.distinctOriginCount,
      dnsLookupCount: requestSummary.dnsLookupCount,
      dnsLookupHostCount: requestSummary.dnsLookupHostCount,
      totalDnsMs: requestSummary.totalDnsMs,
      medianDnsLookupMs: requestSummary.medianDnsLookupMs,
      maxDnsLookupMs: requestSummary.maxDnsLookupMs,
      maxParallelDns: requestSummary.maxParallelDns,
      freshConnectionCount: requestSummary.freshConnectionCount,
      reusedConnectionCount: requestSummary.reusedConnectionCount,
      maxParallelRequests: requestSummary.maxParallelRequests,
      medianRequestTtfbMs: requestSummary.medianRequestTtfbMs,
      p95RequestTtfbMs: requestSummary.p95RequestTtfbMs,
      maxRequestTtfbMs: requestSummary.maxRequestTtfbMs,
      dnsLookups: requestSummary.dnsLookups,
      topSlowRequests,
    };
    if (emitWaterfall) {
      result.requestWaterfall = requestSummary.requestWaterfall;
    }
    console.log(JSON.stringify({ type: "sample", ...result }));
    return result;
  } finally {
    if (!closed) {
      closed = true;
      session.close();
      try {
        await closeTarget(
          browserWebSocketUrl,
          target.id,
          browserContextId,
          Math.min(timeoutMs, 5_000),
        );
      } catch {
        // Chrome or the target may already have closed after a failed load.
      }
    }
  }
}

async function measureFetch(
  baseUrl,
  browserWebSocketUrl,
  url,
  run,
  timeoutMs,
  freshContext,
  targetId,
) {
  const { target, browserContextId } = await createTarget(
    baseUrl,
    browserWebSocketUrl,
    timeoutMs,
    freshContext,
    targetId,
  );
  const session = new CdpSession(target.webSocketDebuggerUrl);
  let response;
  let failure;
  let fetchRequestId;
  try {
    await session.open(timeoutMs);
    await Promise.all([
      session.send("Network.enable"),
      session.send("Runtime.enable"),
    ]);
    await session.send("Network.setCacheDisabled", { cacheDisabled: true });
    await session.send("Network.clearBrowserCache");
    session.on("Network.requestWillBeSent", (event) => {
      if (event.request.url === url) {
        fetchRequestId = event.requestId;
      }
    });
    session.on("Network.responseReceived", (event) => {
      if (event.requestId === fetchRequestId) {
        response = event.response;
      }
    });
    session.on("Network.loadingFailed", (event) => {
      if (event.requestId === fetchRequestId) {
        failure = event;
      }
    });

    const evaluated = await session.send("Runtime.evaluate", {
      awaitPromise: true,
      returnByValue: true,
      expression: `(async () => {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), ${timeoutMs});
        const start = performance.now();
        try {
          const response = await fetch(${JSON.stringify(url)}, {
            cache: "no-store",
            signal: controller.signal,
          });
          if (!response.ok) {
            throw new Error("HTTP " + response.status);
          }
          const reader = response.body.getReader();
          let byteCount = 0;
          for (;;) {
            const { done, value } = await reader.read();
            if (done) {
              break;
            }
            byteCount += value.byteLength;
          }
          const elapsedMs = performance.now() - start;
          return {
            status: response.status,
            byteCount,
            elapsedMs,
            throughputMbps: byteCount * 8 / elapsedMs / 1000,
          };
        } finally {
          clearTimeout(timeout);
        }
      })()`,
    });
    if (evaluated.exceptionDetails) {
      throw new Error(
        evaluated.exceptionDetails.exception?.description ??
          evaluated.exceptionDetails.text,
      );
    }
    const timing = response?.timing;
    const result = {
      run,
      requestedUrl: url,
      status: evaluated.result.value.status,
      byteCount: evaluated.result.value.byteCount,
      elapsedMs: round(evaluated.result.value.elapsedMs),
      throughputMbps: round(evaluated.result.value.throughputMbps),
      dnsMs: round(phaseMs(timing, "dnsStart", "dnsEnd")),
      tcpMs: round(phaseMs(timing, "connectStart", "connectEnd")),
      tlsMs: round(phaseMs(timing, "sslStart", "sslEnd")),
      requestToFirstByteMs: round(
        Number.isFinite(timing?.receiveHeadersStart)
          ? timing.receiveHeadersStart
          : null,
      ),
      nextHopProtocol: response?.protocol ?? "",
      networkFailure: failure?.errorText ?? null,
    };
    console.log(JSON.stringify({ type: "sample", ...result }));
    return result;
  } finally {
    session.close();
    try {
      await closeTarget(
        browserWebSocketUrl,
        target.id,
        browserContextId,
        Math.min(timeoutMs, 5_000),
      );
    } catch {
      // Chrome or the target may already have closed after a failed fetch.
    }
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help || options.urls.length === 0) {
    console.log(usage());
    process.exitCode = options.help ? 0 : 2;
    return;
  }
  if (
    options.targetId &&
    (options.urls.length !== 1 || options.runs !== 1)
  ) {
    throw new Error("--target-id requires exactly one URL and one run");
  }
  const baseUrl = `http://127.0.0.1:${options.port}`;
  const version = await fetchJson(`${baseUrl}/json/version`);
  console.log(
    JSON.stringify({
      type: "environment",
      browser: version.Browser,
      androidPackage: version["Android-Package"],
      port: options.port,
      cacheDisabled: true,
      fetchMode: options.fetchMode,
      freshContext: options.freshContext,
      targetId: options.targetId,
      waterfall: options.waterfall,
    }),
  );

  const samples = [];
  for (const url of options.urls) {
    for (let run = 1; run <= options.runs; run += 1) {
      samples.push(
        options.fetchMode
          ? await measureFetch(
              baseUrl,
              version.webSocketDebuggerUrl,
              url,
              run,
              options.timeoutMs,
              options.freshContext,
              options.targetId,
            )
          : await measureNavigation(
              baseUrl,
              version.webSocketDebuggerUrl,
              url,
              run,
              options.timeoutMs,
              options.settleMs,
              options.freshContext,
              options.targetId,
              options.waterfall,
            ),
      );
    }
  }

  for (const url of options.urls) {
    const urlSamples = samples.filter((sample) => sample.requestedUrl === url);
    const numericFields = [
      "dnsMs",
      "tcpMs",
      "tlsMs",
      "requestToFirstByteMs",
      "ttfbMs",
      "responseMs",
      "domContentLoadedMs",
      "loadMs",
      "totalTransferBytes",
      "requestCount",
      "distinctHostCount",
      "dnsLookupCount",
      "totalDnsMs",
      "maxDnsLookupMs",
      "maxParallelDns",
      "freshConnectionCount",
      "maxParallelRequests",
      "medianRequestTtfbMs",
      "p95RequestTtfbMs",
      "maxRequestTtfbMs",
      "byteCount",
      "elapsedMs",
      "throughputMbps",
    ];
    const summary = { type: "summary", requestedUrl: url, runs: urlSamples.length };
    for (const field of numericFields) {
      summary[`median${field[0].toUpperCase()}${field.slice(1)}`] = round(
        median(
          urlSamples
            .map((sample) => sample[field])
            .filter((value) => Number.isFinite(value)),
        ),
      );
    }
    console.log(JSON.stringify(summary));
  }
}

main().catch((error) => {
  console.error(error.stack ?? String(error));
  process.exitCode = 1;
});
