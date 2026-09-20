import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { main, parseArgs, runFastBenchmark, validFastDisplay } from "./chrome_fast_benchmark.mjs";

function fixture() {
  let elapsed = 0;
  const calls = [];
  const listeners = new Map();
  const output = [];
  const f = {
    calls, output,
    samples: [{ value: "40", units: "Mbps", progress: "succeeded", loaded: "complete" }],
    requests: 1, finished: 1, encodedBytes: 768, failed: 0,
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
  assert.equal(result.failureReason, "invalid-display");
  assertClosed(f);
});

test("a transient number cannot make a later empty display complete", async () => {
  const f = fixture(); f.options.timeoutMs = 7000;
  f.samples = [{ value: "40", units: "Mbps", progress: "running" }, { value: "", units: "", progress: "hidden" }];
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.completed, false);
  assert.equal(result.valid, false);
  assert.equal(result.failureReason, "invalid-display");
  assertClosed(f);
});

test("a number observed before timeout is not a completed measurement without stop or stability", async () => {
  const f = fixture(); f.options.timeoutMs = 2000;
  f.samples = [{ value: "40", units: "Mbps", progress: "running" }, { value: "41", units: "Mbps", progress: "running" }];
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.displayValue, "41");
  assert.equal(result.completed, false);
  assert.equal(result.failureReason, "incomplete-result");
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
    assert.equal((await runFastBenchmark(f.options, f.deps)).failureReason, "incomplete-result");
  }
  const f = fixture(); f.options.timeoutMs = 1000; f.evaluateDelay = 1;
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.completed, false);
  assert.equal(result.failureReason, "incomplete-result");
});

test("network subresource failures remain diagnostic and do not invalidate a completed canonical result", async () => {
  const f = fixture(); f.requests = 3; f.failed = 2;
  const result = await runFastBenchmark(f.options, f.deps);
  assert.equal(result.valid, true);
  assert.equal(result.pageFailedRequestCount, 2);
});

test("navigation failure still closes only the owned target and propagates failure", async () => {
  const f = fixture(); f.navigationError = true;
  await assert.rejects(runFastBenchmark(f.options, f.deps), /synthetic navigation failure/);
  assertClosed(f);
});

test("actual CLI publishes the incomplete aggregate and exits 2 with fake CDP and no network", () => {
  // Preload substitutes only host APIs; the production CLI and polling path run
  // unchanged. Any unexpected target/URL fails rather than contacting a device.
  const preload = `
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
        queueMicrotask(() => this.dispatchEvent(new Event("open")));
      }
      send(data) {
        const { id, method } = JSON.parse(data);
        let result = {};
        if (method === "Target.createTarget") result = { targetId: "owned" };
        if (method === "Runtime.evaluate") result = { result: { value: { value: "", units: "", progress: "", loaded: "loading" } } };
        queueMicrotask(() => {
          if (method === "Page.navigate") this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify({ method: "Network.requestWillBeSent", params: {} }) }));
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
  assert.equal(child.status, 2, child.stderr);
  assert.equal(child.stderr, "");
  const result = JSON.parse(child.stdout);
  assert.equal(result.completed, false);
  assert.equal(result.valid, false);
  assert.equal(result.pageRequestCount, 1);
  assert.equal(result.pageEncodedBytes, 0);
  assert.equal(result.elapsedMs, 2000);
});
