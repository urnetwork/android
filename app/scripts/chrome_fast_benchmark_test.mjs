// Deterministic browser fixtures pin result, deadline, page-only diagnostics and privacy.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { main, parseArgs, runFastBenchmark, validFastDisplay } from "./chrome_fast_benchmark.mjs";

// Synthetic page/browser sessions use an explicit clock; no device or network is contacted.
function fixture() {
  let elapsed = 0;
  const calls = [];
  const listeners = new Map();
  const output = [];
  const f = {
    calls, output,
    samples: [{ value: "40", units: "Mbps", progress: "succeeded", loaded: "complete" }],
    requests: 1, finished: 1, encodedBytes: 768, failed: 0, failedEvents: [],
    evaluateDelay: 0,
    options: parseArgs(["--port", "9223", "--timeout-ms", "90000"]),
    deps: {
      now: () => elapsed,
      sleep: async (ms) => { assert.ok(ms > 0); elapsed += ms; },
      emit: (value) => output.push(JSON.parse(value)),
      fetchJson: async (url) => url.endsWith("/json/version") ?
        { webSocketDebuggerUrl: "ws://browser" } : [{ id: "owned", webSocketDebuggerUrl: "ws://page" }],
      createSession: (url) => url === "ws://browser" ? browser : page,
    },
  };
  const browser = {
    open: async () => calls.push("browser-open"),
    send: async (method, params) => {
      calls.push([method, params]);
      return method === "Target.createTarget" ? { targetId: "owned" } : {};
    },
    close: () => calls.push("browser-close"),
  };
  const page = {
    open: async () => calls.push("page-open"),
    on: (method, listener) => listeners.set(method, listener),
    send: async (method, params) => {
      calls.push([method, params]);
      if (method === "Page.navigate") {
        for (let i = 0; i < f.requests; i += 1) listeners.get("Network.requestWillBeSent")({});
        for (let i = 0; i < f.finished; i += 1) listeners.get("Network.loadingFinished")({ encodedDataLength: f.encodedBytes });
        for (let i = 0; i < f.failed; i += 1) listeners.get("Network.loadingFailed")({});
        for (const event of f.failedEvents) listeners.get("Network.loadingFailed")(event);
        if (f.navigationError) return { errorText: "synthetic navigation failure" };
      }
      if (method === "Runtime.evaluate") {
        elapsed += f.evaluateDelay;
        return { result: { value: f.samples.length > 1 ? f.samples.shift() : f.samples[0] } };
      }
      return {};
    },
    close: () => calls.push("page-close"),
  };
  f.browser = browser;
  f.page = page;
  f.advance = ms => { elapsed += ms; };
  return f;
}

function assertClosed(f) {
  assert.equal(f.calls.filter((call) => Array.isArray(call) && call[0] === "Target.closeTarget").length, 1);
  assert.deepEqual(f.calls.at(-2), ["Target.closeTarget", { targetId: "owned" }]);
  assert.equal(f.calls.at(-1), "browser-close");
  assert.ok(f.calls.includes("page-close"));
}

test("lBOYKS root: one pending navigation and no display retains diagnostics but exits nonzero", async () => {
  const f = fixture(); f.samples = [{ value: "", units: "", progress: "", loaded: "loading" }];
  f.finished = 0;
  assert.equal(await main(["--port", "9223", "--timeout-ms", "90000"], f.deps), 2);
  assert.equal(f.output.length, 1);
  const result = f.output[0];
  assert.equal(result.type, "fast-result");
  assert.equal(result.elapsedMs, 90_000);
  assert.equal(result.pageRequestCount, 1);
  assert.equal(result.pageCompletedRequestCount, 0);
  assert.equal(result.pageEncodedBytes, 0);
  assert.equal(result.completed, false);
  assert.equal(result.valid, false);
  assert.equal(result.failureReason, "deadline-exceeded");
  assert.equal(result.timeoutPhase, "display-poll");
  assertClosed(f);
});

test("a transient number cannot make a later empty display complete", async () => {
  const f = fixture(); f.options.timeoutMs = 7000;
  f.samples = [{ value: "40", units: "Mbps", progress: "running" }, { value: "", units: "", progress: "hidden" }];
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.completed, false);
  assert.equal(result.valid, false);
  assert.equal(result.failureReason, "deadline-exceeded");
  assertClosed(f);
});

test("a number observed before timeout is not a completed measurement without stop or stability", async () => {
  const f = fixture(); f.options.timeoutMs = 2000;
  f.samples = [{ value: "40", units: "Mbps", progress: "running" }, { value: "41", units: "Mbps", progress: "running" }];
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.displayValue, "40");
  assert.equal(result.completed, false);
  assert.equal(result.failureReason, "deadline-exceeded");
});

test("positive terminal display without received page workload is invalid, not zero or valid speed", async () => {
  for (const missing of ["response", "bytes"]) {
    const f = fixture();
    if (missing === "response") f.finished = 0;
    else f.encodedBytes = 0;
    assert.equal(await main([], f.deps), 2);
    assert.equal(f.output[0].completed, true);
    assert.equal(f.output[0].valid, false);
    assert.equal(f.output[0].failureReason, "insufficient-workload");
    assert.equal(f.output[0].displayValue, "40", "retain the observed aggregate for diagnosis");
    assertClosed(f);
  }
});

test("canonical worker-owned result needs no invented page-byte or minimum-speed threshold", async () => {
  for (const [value, units] of [["0.79", "Mbps"], ["62", "Mbps"], ["400", "Kbps"], ["1.2", "Gbps"]]) {
    const f = fixture(); f.samples[0] = { value, units, progress: "circle succeeded", loaded: "complete" };
    assert.equal(await main([], f.deps), 0);
    assert.equal(f.output[0].displayValue, value);
    assert.equal(f.output[0].displayUnits, units);
    assert.equal(f.output[0].pageEncodedBytes, 768);
    assert.equal(f.output[0].pageCompletedRequestCount, 1);
    assert.equal(f.output[0].completed, true);
    assert.equal(f.output[0].valid, true);
    assert.equal(f.output[0].completionReason, "spinner-stopped");
    assert.equal(f.output[0].failureReason, null);
    assertClosed(f);
  }
});

test("existing stable-display fallback requires a currently valid display for the full interval", async () => {
  const f = fixture(); f.samples[0].progress = "running";
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.elapsedMs, 6000);
  assert.equal(result.completionReason, "stable-display");
  assert.equal(result.valid, true);
});

test("malformed values, zero, nonfinite values and missing or unknown units are not measurements", () => {
  for (const [value, units] of [["", "Mbps"], ["0", "Mbps"], ["-1", "Mbps"], ["Infinity", "Mbps"],
    ["NaN", "Mbps"], ["40 garbage", "Mbps"], ["1e9", "Mbps"], [40, "Mbps"], ["40", ""], ["40", "MBps"], ["40", "unknown"]]) {
    assert.equal(validFastDisplay(value, units), false, `${value}|${units}`);
  }
});

test("nonterminal class substrings and a response arriving after deadline cannot make success", async () => {
  for (const progress of ["not-hidden", "unstopped", "succeeded-looking"]) {
    const f = fixture(); f.options.timeoutMs = 2000; f.samples[0].progress = progress;
    assert.equal((await runFastBenchmark(f.options, f.deps)).failureReason, "deadline-exceeded");
  }
  const f = fixture(); f.options.timeoutMs = 1000; f.evaluateDelay = 1;
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.completed, false);
  assert.equal(result.failureReason, "deadline-exceeded");
});

test("network subresource failures remain diagnostic and do not invalidate a completed canonical result", async () => {
  const f = fixture(); f.requests = 3; f.failed = 2;
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.valid, true);
  assert.equal(result.pageFailedRequestCount, 2);
});

// The missing evidence is observable through the real deadline/result path.
test("page failure diagnostics retain fixed categories when the display deadline fails", async () => {
  const f = fixture();
  f.requests = 50; f.finished = 21;
  f.samples = [{ value: "0", units: "", progress: "", loaded: "complete" }];
  f.failedEvents = Array.from({ length: 26 }, (_, index) => ({
    errorText: index < 21 ? "net::ERR_NAME_NOT_RESOLVED" : "net::ERR_CONNECTION_TIMED_OUT",
    requestId: `synthetic-request-${index}`,
    url: "https://download.example/file?token=synthetic-private",
  }));
  assert.equal(await main(["--timeout-ms", "90000"], f.deps), 2);
  const result = f.output[0];
  assert.ok(result.pageFailureCategoryCounts, "missing bounded page failure categories");
  assert.equal(result.pageFailureScope, "page-target-only");
  assert.equal(result.pageFailureCategoryCounts.dns, 21);
  assert.equal(result.pageFailureCategoryCounts.timeout, 5);
  assert.equal(Object.values(result.pageFailureCategoryCounts).reduce((sum, count) => sum + count, 0), 26);
  assert.equal(result.pageFailedRequestCount, 26);
  assert.equal(result.totalElapsedMs, 90_000);
  assert.equal(result.timeoutPhase, "display-poll");
  assert.equal(result.completed, false);
  assert.equal(result.valid, false);
  assert.equal(result.failureReason, "deadline-exceeded");
  assert.doesNotMatch(JSON.stringify(result), /synthetic|download\.example|requestId|token=/);
  assertClosed(f);
});

// Homogeneous protocol cases share one real listener, not a parallel classifier.
test("page failure diagnostics map exact supported codes without changing a completed result", async () => {
  const cases = [
    ["ERR_ABORTED", "canceled"],
    ["ERR_NAME_NOT_RESOLVED", "dns"], ["ERR_NAME_RESOLUTION_FAILED", "dns"], ["ERR_DNS_TIMED_OUT", "dns"],
    ["ERR_TIMED_OUT", "timeout"], ["ERR_CONNECTION_TIMED_OUT", "timeout"],
    ["ERR_CONNECTION_CLOSED", "connection_closed"], ["ERR_CONNECTION_RESET", "connection_reset"],
    ["ERR_CONNECTION_REFUSED", "connection_refused"], ["ERR_CONNECTION_FAILED", "connection_failed"],
    ["ERR_CONNECTION_ABORTED", "connection_failed"], ["ERR_ADDRESS_UNREACHABLE", "unreachable"],
    ["ERR_INTERNET_DISCONNECTED", "offline"], ["ERR_NETWORK_CHANGED", "network_changed"],
    ["ERR_SSL_PROTOCOL_ERROR", "tls"], ["ERR_SSL_VERSION_OR_CIPHER_MISMATCH", "tls"],
    ["ERR_CERT_COMMON_NAME_INVALID", "tls"], ["ERR_CERT_DATE_INVALID", "tls"], ["ERR_CERT_AUTHORITY_INVALID", "tls"],
    ["ERR_BLOCKED_BY_CLIENT", "blocked"], ["ERR_BLOCKED_BY_ADMINISTRATOR", "blocked"],
    ["ERR_BLOCKED_BY_RESPONSE", "blocked"], ["ERR_BLOCKED_BY_CSP", "blocked"],
    ["ERR_INSUFFICIENT_RESOURCES", "resource"], ["ERR_OUT_OF_MEMORY", "resource"], ["ERR_FAILED", "other"],
  ];
  for (const [code, category] of cases) {
    const f = fixture(); f.failedEvents = [{ errorText: `net::${code}` }];
    const result = await runFastBenchmark(f.options, f.deps);
    assert.equal(result.pageFailureCategoryCounts[category], 1, code);
    assert.equal(Object.values(result.pageFailureCategoryCounts).reduce((sum, count) => sum + count, 0), 1, code);
    assert.equal(result.pageFailedRequestCount, 1);
    assert.equal(result.pageFailureScope, "page-target-only");
    assert.equal(result.displayValue, "40");
    assert.equal(result.completed, true);
    assert.equal(result.valid, true, "subresource diagnostics must not become a new qualification gate");
    assert.equal(result.elapsedMs, 1000);
    assert.equal(result.pageEncodedBytes, 768, "page accounting remains separate from worker bulk bytes");
    assertClosed(f);
  }
});

// A strict protocol cancellation flag outranks an incidental transport code.
test("page failure diagnostics distinguish cancellation from truthy lookalikes", async () => {
  const f = fixture();
  f.failedEvents = [
    { canceled: true, errorText: "net::ERR_CONNECTION_RESET" },
    { canceled: true },
    { canceled: "true", errorText: "net::ERR_CONNECTION_REFUSED" },
    { canceled: 1, errorText: "net::ERR_NAME_NOT_RESOLVED" },
    { canceled: false, errorText: "net::ERR_ABORTED" },
  ];
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.pageFailureCategoryCounts.canceled, 3);
  assert.equal(result.pageFailureCategoryCounts.connection_reset, 0);
  assert.equal(result.pageFailureCategoryCounts.connection_refused, 1);
  assert.equal(result.pageFailureCategoryCounts.dns, 1);
  assert.equal(Object.values(result.pageFailureCategoryCounts).reduce((sum, count) => sum + count, 0), 5);
});

// Unknown/malformed data must stay one finite bucket without coercion or echo.
test("page failure diagnostics discard private text and malformed code lookalikes", async () => {
  const f = fixture();
  const cannotStringify = { toString() { throw new Error("diagnostics coerced an opaque value"); } };
  f.failedEvents = [
    { errorText: "net::ERR_CONNECTION_RESET synthetic-private-token" },
    { errorText: "prefix net::ERR_NAME_NOT_RESOLVED" },
    { errorText: "net::err_connection_refused" },
    { errorText: "net::ERR_TIMED_OUT\nsynthetic-private-token" },
    { errorText: "net::ERR_CERT_DATE_INVALID\u0000" },
    { errorText: "__proto__" }, { errorText: "constructor" }, { errorText: "toString" },
    { errorText: cannotStringify }, { errorText: 42 }, { errorText: null },
    { errorText: "synthetic-private".repeat(8192) },
    null, undefined, {}, "net::ERR_CONNECTION_RESET",
  ];
  const guarded = { errorText: "net::ERR_CONNECTION_CLOSED" };
  Object.defineProperty(guarded, "url", { get() { throw new Error("diagnostics inspected a URL"); } });
  Object.defineProperty(guarded, "requestId", { get() { throw new Error("diagnostics inspected an identity"); } });
  f.failedEvents.push(guarded);
  const result = await runFastBenchmark(f.options, f.deps);
  const counts = result.pageFailureCategoryCounts;
  assert.deepEqual(Object.keys(counts).sort(), ["canceled", "dns", "timeout", "connection_closed", "connection_reset",
    "connection_refused", "connection_failed", "unreachable", "offline", "network_changed", "tls", "blocked", "resource", "other"].sort());
  assert.equal(counts.other, f.failedEvents.length - 1);
  assert.equal(counts.connection_closed, 1);
  assert.equal(Object.values(counts).reduce((sum, count) => sum + count, 0), f.failedEvents.length);
  assert.equal(result.pageFailedRequestCount, f.failedEvents.length);
  assert.equal(Object.getPrototypeOf(counts), Object.prototype);
  assert.ok(JSON.stringify(counts).length < 512);
  assert.doesNotMatch(JSON.stringify(result), /synthetic|token|net::|__proto__|requestId|https|ERR_/);
  assert.equal(result.valid, true);
});

// Counter ownership is one page invocation, never the shared browser or workers.
test("page failure diagnostics are fresh per page invocation and do not attach worker targets", async () => {
  const first = fixture(); first.failedEvents = [{ errorText: "net::ERR_CONNECTION_RESET" }];
  const before = await runFastBenchmark(first.options, first.deps);
  const next = fixture();
  const after = await runFastBenchmark(next.options, next.deps);
  assert.notEqual(before.pageFailureCategoryCounts, after.pageFailureCategoryCounts);
  assert.equal(before.pageFailureCategoryCounts.connection_reset, 1);
  assert.ok(Object.values(after.pageFailureCategoryCounts).every(count => count === 0));
  assert.equal(after.pageFailureScope, "page-target-only");
  const allowed = new Set(["Target.createTarget", "Target.closeTarget", "Page.enable", "Network.enable", "Runtime.enable",
    "Network.setCacheDisabled", "Network.clearBrowserCache", "Page.navigate", "Runtime.evaluate"]);
  for (const call of [...first.calls, ...next.calls].filter(Array.isArray)) assert.ok(allowed.has(call[0]));
  assertClosed(first); assertClosed(next);
});

test("navigation failure still closes only the owned target and propagates failure", async () => {
  const f = fixture(); f.navigationError = true;
  await assert.rejects(runFastBenchmark(f.options, f.deps), /synthetic navigation failure/);
  assertClosed(f);
});

test("whole-run deadline bounds discovery, CDP operations and owned-target cleanup", async (t) => {
  for (const phase of ["browser-discovery", "browser-open", "target-create", "target-discovery", "page-open",
    "page-enable", "cache-disable", "cache-clear", "navigation", "display-evaluate", "target-cleanup"]) {
    await t.test(phase, async () => {
      const f = fixture();
      f.options.timeoutMs = 100;
      f.deps.sleep = async () => f.advance(1);
      // Completed microtasks cancel these deterministic timers. Only the
      // intentionally unresolved host/CDP operation consumes the budget.
      f.deps.setTimer = (callback, ms) => setImmediate(() => { f.advance(ms); callback(); });
      f.deps.clearTimer = clearImmediate;
      const held = () => new Promise(() => {});
      const fetch = f.deps.fetchJson;
      f.deps.fetchJson = (url, signal) => {
        assert.equal(signal.aborted, false);
        return (phase === "browser-discovery" && url.endsWith("/version")) ||
          (phase === "target-discovery" && url.endsWith("/list")) ? held() : fetch(url);
      };
      if (phase === "browser-open") f.browser.open = held;
      if (phase === "page-open") f.page.open = held;
      const method = { "target-create": "Target.createTarget", "page-enable": "Network.enable",
        "cache-disable": "Network.setCacheDisabled", "cache-clear": "Network.clearBrowserCache",
        navigation: "Page.navigate", "display-evaluate": "Runtime.evaluate", "target-cleanup": "Target.closeTarget" }[phase];
      for (const session of [f.browser, f.page]) {
        const send = session.send;
        session.send = (name, params) => name === method ? held() : send(name, params);
      }
      const result = await runFastBenchmark(f.options, f.deps);
      assert.equal(result.valid, false);
      assert.equal(result.timedOut, true);
      assert.equal(result.failureReason, "deadline-exceeded");
      assert.equal(result.timeoutPhase, phase);
      assert.ok(result.totalElapsedMs <= 100);
      if (phase !== "browser-discovery") assert.ok(f.calls.includes("browser-close"));
      if (phase === "target-cleanup") {
        assert.equal(result.completed, true, "a completed display cannot hide failed cleanup");
        assert.equal(result.cleanupComplete, false);
      }
    });
  }
});

test("setup spends the display budget and a result at the deadline cannot pass", async () => {
  const f = fixture();
  f.options.timeoutMs = 1500;
  f.browser.open = async () => { f.advance(600); };
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.timedOut, true);
  assert.equal(result.totalElapsedMs, 1500);
  assert.equal(result.elapsedMs, 900);
  assert.equal(result.displayValue, "");
});

for (const mode of ["incomplete", "evaluate-hang", "cleanup-hang", "close-handshake"])
test(`actual CLI exits with the correct aggregate despite ${mode} and a retained socket handle`, () => {
  // Preload substitutes only host APIs; the production CLI and polling path run
  // unchanged. Any unexpected target/URL fails rather than contacting a device.
  const preload = `
    const mode = ${JSON.stringify(mode)};
    let now = 0;
    const realTimeout = globalThis.setTimeout;
    globalThis.performance = { now: () => now };
    globalThis.setTimeout = (callback, ms, ...args) => ms <= 1000
      ? realTimeout(() => { now += ms; callback(...args); }, 0)
      : realTimeout(callback, ms, ...args);
    globalThis.fetch = async (url) => {
      if (url === "http://127.0.0.1:9223/json/version") return { ok: true, json: async () => ({ webSocketDebuggerUrl: "ws://fake/browser" }) };
      if (url === "http://127.0.0.1:9223/json/list") return { ok: true, json: async () => [{ id: "owned", webSocketDebuggerUrl: "ws://fake/page" }] };
      throw new Error("unexpected fake URL");
    };
    globalThis.WebSocket = class extends EventTarget {
      constructor(url) {
        super();
        if (url !== "ws://fake/browser" && url !== "ws://fake/page") throw new Error("unexpected fake socket");
        setInterval(() => {}, 1000); // A close handshake that never releases its host handle.
        queueMicrotask(() => this.dispatchEvent(new Event("open")));
      }
      send(data) {
        const { id, method } = JSON.parse(data);
        if (mode === "evaluate-hang" && method === "Runtime.evaluate") return;
        if (mode === "cleanup-hang" && method === "Target.closeTarget") return;
        let result = {};
        if (method === "Target.createTarget") result = { targetId: "owned" };
        if (method === "Runtime.evaluate") result = { result: { value: mode === "incomplete"
          ? { value: "", units: "", progress: "", loaded: "loading" }
          : { value: "40", units: "Mbps", progress: "succeeded", loaded: "complete" } } };
        queueMicrotask(() => {
          if (method === "Page.navigate") this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ method: "Network.requestWillBeSent", params: {} }) }));
          if (method === "Page.navigate") for (const errorText of ["net::ERR_CONNECTION_RESET", "net::ERR_TIMED_OUT synthetic-private-token"]) {
            this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ method: "Network.requestWillBeSent", params: {} }) }));
            this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ method: "Network.loadingFailed",
              params: { errorText, requestId: "synthetic-private-request", url: "https://download.example/?token=synthetic-private" } }) }));
          }
          if (method === "Page.navigate" && mode !== "incomplete") this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ method: "Network.loadingFinished", params: { encodedDataLength: 768 } }) }));
          this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ id, result }) }));
        });
      }
      close() {}
    };
  `;
  const child = spawnSync(process.execPath, ["--import", `data:text/javascript,${encodeURIComponent(preload)}`,
    fileURLToPath(new URL("./chrome_fast_benchmark.mjs", import.meta.url)), "--port", "9223", "--timeout-ms", "2000"],
  { encoding: "utf8", timeout: 5000 });
  assert.equal(child.error, undefined);
  assert.equal(child.signal, null);
  const valid = mode === "close-handshake";
  assert.equal(child.status, valid ? 0 : 2, child.stderr);
  assert.equal(child.stderr, "");
  const result = JSON.parse(child.stdout);
  assert.equal(result.completed, ["cleanup-hang", "close-handshake"].includes(mode));
  assert.equal(result.valid, valid);
  assert.equal(result.pageRequestCount, 3);
  assert.equal(result.pageFailureScope, "page-target-only");
  assert.equal(result.pageFailedRequestCount, 2);
  assert.equal(result.pageFailureCategoryCounts.connection_reset, 1);
  assert.equal(result.pageFailureCategoryCounts.other, 1);
  assert.equal(Object.values(result.pageFailureCategoryCounts).reduce((sum, count) => sum + count, 0), 2);
  assert.doesNotMatch(child.stdout, /synthetic-private|download\.example|requestId|net::/);
  assert.equal(result.pageEncodedBytes, mode === "incomplete" ? 0 : 768);
  assert.equal(result.totalElapsedMs, valid ? 1000 : 2000);
  assert.equal(result.timeoutPhase, { incomplete: "display-poll", "evaluate-hang": "display-evaluate", "cleanup-hang": "target-cleanup" }[mode] ?? null);
});

for (const mode of ["browser-open", "target-create", "navigation"])
test(`actual CLI preserves sanitized ${mode} websocket failure without inventing a speed sample`, () => {
  const preload = `
    const mode = ${JSON.stringify(mode)};
    globalThis.fetch = async url => {
      if (url === 'http://127.0.0.1:9223/json/version') return { ok: true, json: async () => ({ webSocketDebuggerUrl: 'ws://fake/browser' }) };
      if (url === 'http://127.0.0.1:9223/json/list') return { ok: true, json: async () => [{ id: 'owned', webSocketDebuggerUrl: 'ws://fake/page' }] };
      throw new Error('unexpected request');
    };
    globalThis.WebSocket = class extends EventTarget {
      constructor(url) {
        super();
        if (!['ws://fake/browser', 'ws://fake/page'].includes(url)) throw new Error('unexpected socket');
        setInterval(() => {}, 1000);
        queueMicrotask(() => mode === 'browser-open' ? this.fail() : this.dispatchEvent(new Event('open')));
      }
      fail() { this.dispatchEvent(Object.assign(new Event('close'), { code: 1006, reason: 'private-close-reason' })); }
      send(data) {
        const { id, method } = JSON.parse(data);
        if ((mode === 'target-create' && method === 'Target.createTarget') || (mode === 'navigation' && method === 'Page.navigate')) {
          queueMicrotask(() => this.fail());
          return;
        }
        const response = method === 'Target.closeTarget'
          ? { id, error: { code: -32000, message: 'private-cleanup-error' } }
          : { id, result: method === 'Target.createTarget' ? { targetId: 'owned' } : {} };
        queueMicrotask(() => this.dispatchEvent(new MessageEvent('message', { data: JSON.stringify(response) })));
      }
      close() {}
    };
  `;
  const child = spawnSync(process.execPath, ["--import", `data:text/javascript,${encodeURIComponent(preload)}`,
    fileURLToPath(new URL("./chrome_fast_benchmark.mjs", import.meta.url)), "--port", "9223", "--timeout-ms", "2000"],
  { encoding: "utf8", timeout: 5000 });
  assert.equal(child.error, undefined);
  assert.equal(child.signal, null);
  assert.equal(child.status, 1);
  assert.equal(child.stdout, "", "control failure must never become a fast-result");
  assert.deepEqual(JSON.parse(child.stderr), {
    type: "fast-error", reason: "websocket-closed",
    ...(mode === "target-create" ? { operation: "Target.createTarget" } : {}),
    ...(mode === "navigation" ? { operation: "Page.navigate" } : {}),
    websocketCloseCode: 1006, phase: mode,
  });
  assert.doesNotMatch(child.stderr, /private|fake|Node\.js|stack|url|expression/);
});
