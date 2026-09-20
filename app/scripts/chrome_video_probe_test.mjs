import assert from "node:assert/strict";
import test from "node:test";
import * as videoProbe from "./chrome_video_probe.mjs";

import {
  parseArgs,
  safeHost,
  summarizeRejectedConnections,
} from "./chrome_video_probe.mjs";

test("argument parser keeps reload and navigation mutually exclusive", () => {
  assert.deepEqual(
    parseArgs([
      "--port", "9223",
      "--target-id", "target",
      "--timeout-ms", "5000",
      "--interval-ms", "250",
      "--reload",
    ]),
    {
      port: 9223,
      targetId: "target",
      timeoutMs: 5000,
      intervalMs: 250,
      reload: true,
      navigateUrl: "",
    },
  );
  assert.throws(
    () => parseArgs(["--reload", "--navigate", "https://example.test/video"]),
    /mutually exclusive/,
  );
});

test("403 retry summary distinguishes same and new TLS connections", () => {
  const responses = [
    {
      host: "media.example.test",
      resourceType: "Fetch",
      timestamp: 1,
      status: 403,
      protocol: "h2",
      connectionId: "8895",
      connectionReused: true,
    },
    {
      host: "unrelated.example.test",
      resourceType: "Fetch",
      timestamp: 2,
      status: 200,
      protocol: "h2",
      connectionId: "other",
      connectionReused: true,
    },
    {
      host: "media.example.test",
      resourceType: "Fetch",
      timestamp: 3,
      status: 403,
      protocol: "h2",
      connectionId: "8895",
      connectionReused: true,
    },
    {
      host: "media.example.test",
      resourceType: "Document",
      timestamp: 4,
      status: 403,
      protocol: "h2",
      connectionId: "9547",
      connectionReused: false,
    },
  ];

  const rejected = summarizeRejectedConnections(responses);
  assert.equal(rejected.length, 3);
  assert.equal(rejected[0].laterResponseCount, 2);
  assert.equal(rejected[0].retryOnSameConnection, true);
  assert.equal(rejected[0].retryOnNewConnection, true);
  assert.equal(rejected[1].retryOnSameConnection, false);
  assert.equal(rejected[1].retryOnNewConnection, true);
  assert.equal(rejected[2].retryOnSameConnection, false);
  assert.equal(rejected[2].retryOnNewConnection, false);
});

test("host sanitizer never retains a request path or signed query", () => {
  assert.equal(
    safeHost("https://media.example.test/private/manifest.mpd?token=secret"),
    "media.example.test",
  );
  assert.equal(safeHost("not a URL"), "invalid");
});

function targetFixture() {
  const calls = [];
  let elapsed = 0;
  const f = {
    calls,
    targets: [{ id: "unrelated", type: "page", webSocketDebuggerUrl: "ws://unrelated" },
      { id: "owned", type: "page", webSocketDebuggerUrl: "ws://owned" }],
    browser: {
      open: async () => calls.push(["open"]),
      send: async (method, params) => {
        calls.push([method, params]);
        return method === "Target.createTarget" ? { targetId: "owned" } : { success: true };
      },
      close: () => calls.push(["browser-close"]),
    },
    deps: {
      fetchJson: async (url) => {
        calls.push(["fetch", url]);
        return url.endsWith("/json/version") ? { webSocketDebuggerUrl: "ws://browser" } : f.targets;
      },
      createSession: (url) => { assert.equal(url, "ws://browser"); return f.browser; },
      now: () => elapsed,
      sleep: async (ms) => { elapsed += ms; },
    },
  };
  return f;
}

test("6Vkcuq root: documented --navigate without target owns a blank target before any site navigation", async () => {
  assert.equal(typeof videoProbe.acquireVideoTarget, "function");
  const f = targetFixture();
  const options = parseArgs(["--port", "9223", "--navigate", "https://example.test/video"]);
  const lease = await videoProbe.acquireVideoTarget(options, f.deps);
  assert.equal(lease.target.id, "owned");
  assert.deepEqual(f.calls.find(([method]) => method === "Target.createTarget"),
    ["Target.createTarget", { url: "about:blank" }]);
  assert.equal(JSON.stringify(f.calls).includes("example.test"), false, "attach probe before loading site");
  await lease.close();
  await lease.close();
  assert.equal(f.calls.filter(([method]) => method === "Target.closeTarget").length, 1);
  assert.deepEqual(f.calls.at(-2), ["Target.closeTarget", { targetId: "owned" }]);
  assert.deepEqual(f.calls.at(-1), ["browser-close"]);
});

test("explicit existing target is never replaced or closed by video probe", async () => {
  const f = targetFixture();
  const lease = await videoProbe.acquireVideoTarget(parseArgs(["--target-id", "unrelated"]), f.deps);
  assert.equal(lease.target.id, "unrelated");
  await lease.close();
  assert.equal(f.calls.length, 1);
  assert.ok(f.calls[0][1].endsWith("/json/list"));
  await assert.rejects(videoProbe.acquireVideoTarget(parseArgs([]), f.deps), /target-id.*navigate/);
});

test("created target discovery failure closes only the owned target and browser session", async () => {
  const f = targetFixture(); f.targets = [f.targets[0]];
  await assert.rejects(videoProbe.acquireVideoTarget(parseArgs(["--navigate", "https://example.test/video"]), f.deps),
    /target.*unavailable/i);
  assert.deepEqual(f.calls.at(-2), ["Target.closeTarget", { targetId: "owned" }]);
  assert.deepEqual(f.calls.at(-1), ["browser-close"]);
});

test("target cleanup failure is visible but still closes the browser session", async () => {
  const f = targetFixture();
  const lease = await videoProbe.acquireVideoTarget(parseArgs(["--navigate", "https://example.test/video"]), f.deps);
  f.browser.send = async () => { throw new Error("close refused"); };
  await assert.rejects(lease.close(), /close refused/);
  assert.deepEqual(f.calls.at(-1), ["browser-close"]);
});

test("actual navigation path instruments the owned blank target, reports playback, then closes it", async () => {
  const f = targetFixture(); let samples = 0;
  const page = {
    open: async () => f.calls.push(["page-open"]),
    on: (method) => f.calls.push(["observe", method]),
    send: async (method, params) => {
      f.calls.push([method, params]);
      if (method === "Runtime.evaluate" && params.returnByValue) {
        if (params.expression.includes("document.readyState === 'complete'")) return { result: { value: true } };
        return { result: { value: { video: { currentTime: samples++ * 2, readyState: 4 } } } };
      }
      return {};
    },
    close: () => f.calls.push(["page-close"]),
  };
  f.deps.createSession = (url) => url === "ws://browser" ? f.browser : page;
  const result = await videoProbe.runVideoProbe(parseArgs(["--navigate", "https://example.test/video"]), f.deps);
  assert.equal(result.playbackProgressed, true);
  assert.equal(result.targetId, "owned");
  assert.equal(result.sampleCount, 2);
  const navigate = f.calls.findIndex(([method]) => method === "Page.navigate");
  assert.ok(navigate > f.calls.findIndex(([method]) => method === "Network.enable"));
  assert.ok(navigate > f.calls.findIndex(([method]) => method === "observe"));
  assert.deepEqual(f.calls.slice(-3), [["page-close"], ["Target.closeTarget", { targetId: "owned" }], ["browser-close"]]);
});

test("page websocket open failure still releases the created target", async () => {
  const f = targetFixture();
  f.deps.createSession = (url) => url === "ws://browser" ? f.browser : {
    open: async () => { throw new Error("page open failed"); },
    close: () => f.calls.push(["page-close"]),
  };
  await assert.rejects(videoProbe.runVideoProbe(parseArgs(["--navigate", "https://example.test/video"]), f.deps),
    /page open failed/);
  assert.deepEqual(f.calls.slice(-3), [["page-close"], ["Target.closeTarget", { targetId: "owned" }], ["browser-close"]]);
});
