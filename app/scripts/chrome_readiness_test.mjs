import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { EventEmitter } from "node:events";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { captureChromeReadiness, parseArgs, parseVersionResponse, readDevtoolsVersion,
  READINESS_TIMEOUT_MS, STABLE_RESPONSE_GAP_MS, waitForChromeReady } from "./chrome_readiness.mjs";

const PORT = 9223;
const version = (token = "private-browser-A") => JSON.stringify({ "Android-Package": "com.android.chrome",
  Browser: "Chrome/152.0.7977.82", "Protocol-Version": "1.3", "User-Agent": "private-user-agent",
  webSocketDebuggerUrl: `ws://localhost:${PORT}/devtools/browser${token === null ? "" : `/${token}`}` });
const active = () => ({ status: 0, stdout: `fake-device tcp:${PORT} localabstract:chrome_devtools_remote\n` });

function fixture() {
  let time = 0;
  const probeTimes = [];
  const waits = [];
  const f = { options: { serial: "fake-device", port: PORT, label: "cell-01" }, probeTimes, waits,
    responses: [version()], forward: active, processIdentity: () => ({ status: 0, stdout: "4321\n" }),
    get time() { return time; }, advance(ms) { time += ms; },
    deps: { now: () => time, wallNow: () => 1_000_000 + time,
      sleep: async (ms) => { assert.ok(ms > 0); waits.push(ms); time += ms; },
      forward: async (timeout) => { assert.ok(timeout > 0 && timeout <= 2000); return f.forward(); },
      processIdentity: async (timeout) => { assert.ok(timeout > 0 && timeout <= 2000); return f.processIdentity(); },
      probe: async (port, timeout) => { assert.equal(port, PORT); assert.ok(timeout > 0 && timeout <= 2000);
        probeTimes.push(time); return f.responses.length > 1 ? f.responses.shift() : f.responses[0]; },
    },
  };
  return f;
}

test("only explicit serial, bounded loopback port, label and output are accepted", () => {
  assert.deepEqual(parseArgs(["--serial", "fake", "--port", "9223", "--label", "cell-1", "--output", "out"]),
    { serial: "fake", port: 9223, label: "cell-1", output: "out" });
  for (const args of [[], ["--port", "65536"], ["--host", "remote.example"],
    ["--serial", "fake", "--port", "9223", "--label", "bad label", "--output", "out"],
    ["--serial", "fake", "--port", "9223", "--label", "cell", "--output", "out", "--timeout-ms", "1"]]) {
    assert.throws(() => parseArgs(args));
  }
  assert.equal(READINESS_TIMEOUT_MS, 30_000);
  assert.equal(STABLE_RESPONSE_GAP_MS, 5_000);
});

test("cbI1op root: initial zero-byte/connection-reset response is not readiness or terminal startup failure", async () => {
  const f = fixture(); f.responses = [undefined, "", version(), version()];
  const result = await waitForChromeReady(f.options, f.deps);
  assert.equal(result.eligible, true);
  assert.equal(result.stableGapMs, 5000);
  assert.deepEqual(f.probeTimes, [0, 250, 500, 5500]);
  assert.equal(result.rejectedResponses, 2);
  assert.equal(result.sameBrowserInstance, true);
  assert.equal(result.elapsedMs, 5500);
});

test("an invalid second response discards the first and requires a new five-second pair", async () => {
  for (const invalid of [undefined, "", "{", "[]", "null", '{"Browser":"Chrome/152.0"}']) {
    const f = fixture(); f.responses = [version(), invalid, version(), version()];
    const result = await waitForChromeReady(f.options, f.deps);
    assert.equal(result.eligible, true);
    assert.equal(result.firstStableResponseHostTimeUnixMs, 1_005_250);
    assert.equal(result.stableGapMs, 5000);
    assert.equal(result.elapsedMs, 10_250);
  }
});

test("Chrome replacement cannot pair old/new instances, even when versions match", async () => {
  const f = fixture(); f.responses = [version("instance-A"), version("instance-B"), version("instance-B")];
  const result = await waitForChromeReady(f.options, f.deps);
  assert.equal(result.eligible, true);
  assert.equal(result.browserReplacements, 1);
  assert.equal(result.elapsedMs, 10_000);
  assert.equal(result.firstStableResponseHostTimeUnixMs, 1_005_000);
});

test("cbI1op real Android shape: tokenless /devtools/browser is valid only with stable Chrome PID evidence", async () => {
  const f = fixture(); f.responses = [version(null)];
  assert.ok(parseVersionResponse(version(null), PORT));
  const result = await waitForChromeReady(f.options, f.deps);
  assert.equal(result.eligible, true);
  assert.equal(result.stableGapMs, 5000);
  assert.equal(result.browserReplacements, 0);
  assert.equal(JSON.stringify(result).includes("4321"), false);
});

test("a tokenless browser replacement between or within responses requires a new stable pair", async () => {
  for (const boundary of [1, 2]) {
    const f = fixture(); f.responses = [version(null)]; let count = 0;
    f.processIdentity = () => ({ status: 0, stdout: count++ < boundary ? "100\n" : "200\n" });
    const result = await waitForChromeReady(f.options, f.deps);
    assert.equal(result.eligible, true);
    assert.equal(result.browserReplacements, 1);
    assert.equal(result.stableGapMs, 5000);
    assert.equal(result.elapsedMs, boundary === 1 ? 5250 : 10_000);
  }
});

test("absent, multiple or unreadable Chrome PIDs never qualify a tokenless response", async () => {
  for (const identity of [{ status: 1, stdout: "" }, { status: 0, stdout: "100 200\n" }, { status: 0, stdout: "private error" }]) {
    const f = fixture(); f.responses = [version(null)]; f.processIdentity = () => identity;
    const result = await waitForChromeReady(f.options, f.deps);
    assert.equal(result.eligible, false);
    assert.equal(result.elapsedMs, 30_000);
    assert.equal(f.probeTimes.length, 0);
  }
});

test("valid-looking non-Chrome, nonlocal, wrong-port and malformed documents are rejected", () => {
  for (const mutate of [
    (v) => { v.Browser = [v.Browser]; }, (v) => { v["Protocol-Version"] = 1.3; },
    (v) => { v["Android-Package"] = "other.browser"; },
    (v) => { v.webSocketDebuggerUrl = "ws://remote.example:9223/devtools/browser/A"; },
    (v) => { v.webSocketDebuggerUrl = "ws://localhost:9222/devtools/browser/A"; },
    (v) => { v.webSocketDebuggerUrl = "ws://localhost:9223/devtools/page/A"; },
    (v) => { v.webSocketDebuggerUrl += "?private=token"; },
    (v) => { v.webSocketDebuggerUrl = "ws://user:pass@localhost:9223/devtools/browser/A"; },
  ]) {
    const value = JSON.parse(version()); mutate(value);
    assert.equal(parseVersionResponse(JSON.stringify(value), PORT), undefined);
  }
  assert.ok(parseVersionResponse(version(), PORT));
  assert.equal(parseVersionResponse(version() + "trailing", PORT), undefined);
});

test("the same existing forward is checked before and after each version response", async () => {
  for (const row of [`other-device tcp:${PORT} localabstract:chrome_devtools_remote\n`,
    `fake-device tcp:${PORT} localabstract:different_socket\n`, active().stdout.repeat(2)]) {
    const f = fixture(); let count = 0;
    f.forward = () => count++ < 2 ? active() : { status: 0, stdout: row };
    const result = await waitForChromeReady(f.options, f.deps);
    assert.equal(result.eligible, false);
    assert.equal(result.reason, "forward-target-mismatch");
    assert.equal(f.probeTimes.length, 1, "never send a request through the wrong forward");
  }
  const f = fixture(); let checks = 0;
  f.forward = () => ++checks === 2 ? { status: 0, stdout: "" } : active();
  const result = await waitForChromeReady(f.options, f.deps);
  assert.equal(result.eligible, true);
  assert.equal(result.firstStableResponseHostTimeUnixMs, 1_000_250);
  assert.equal(result.unavailableForwardChecks, 1);
});

test("persistent empty/malformed/failed forward responses stop at the fixed deadline", async () => {
  for (const kind of ["empty", "malformed", "forward"]) {
    const f = fixture();
    if (kind === "forward") f.forward = () => ({ status: 1, stdout: "private adb failure" });
    else f.responses = [kind === "empty" ? "" : "private malformed json"];
    const result = await waitForChromeReady(f.options, f.deps);
    assert.equal(result.eligible, false);
    assert.equal(result.classification, "FAILED_CHROME_READINESS");
    assert.equal(result.elapsedMs, 30_000);
    assert.equal(JSON.stringify(result).includes("private"), false);
  }
});

test("one valid response near deadline cannot extend the budget or count as stable readiness", async () => {
  const f = fixture(); let count = 0;
  f.deps.probe = async () => { if (count++ === 0) f.advance(29_000); return version(); };
  const result = await waitForChromeReady(f.options, f.deps);
  assert.equal(result.eligible, false);
  assert.equal(result.validResponses, 1);
  assert.equal(result.elapsedMs, 30_000);
  assert.equal(result.stableGapMs, null);
});

test("HTTP adapter accepts complete split JSON and rejects truncated, oversized, non-200 and hung responses", async () => {
  for (const mode of ["valid", "truncated", "oversized", "http-error", "aborted", "socket-error", "timeout"]) {
    const request = new EventEmitter();
    request.destroy = () => request.emit("error", new Error("private socket error"));
    let timeout;
    const body = await readDevtoolsVersion(PORT, 2000, {
      setTimer: (callback, ms) => { assert.equal(ms, 2000); timeout = callback; return 1; }, clearTimer: () => {},
      get: (url, options, receive) => {
        assert.equal(url, "http://127.0.0.1:9223/json/version");
        assert.equal(options.agent, false);
        queueMicrotask(() => {
          if (mode === "timeout") { timeout(); return; }
          if (mode === "socket-error") { request.emit("error", new Error("private")); return; }
          const response = new EventEmitter(); response.statusCode = mode === "http-error" ? 503 : 200; response.complete = false;
          receive(response);
          const payload = mode === "oversized" ? Buffer.alloc(65537) : Buffer.from(version());
          response.emit("data", payload.subarray(0, 10)); response.emit("data", payload.subarray(10));
          if (mode === "aborted") response.emit("aborted");
          else if (mode === "truncated") response.emit("close");
          else { response.complete = true; response.emit("end"); response.emit("close"); }
        });
        return request;
      },
    });
    assert.equal(body, mode === "valid" ? version() : undefined, mode);
  }
});

test("success/failure artifacts are private and sanitized; an existing artifact is never replaced", async (t) => {
  const directory = mkdtempSync(join(tmpdir(), "chrome-ready-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  for (const succeeds of [true, false]) {
    const f = fixture(); f.options.output = join(directory, succeeds ? "ready.json" : "failed.json");
    if (!succeeds) f.responses = [""];
    const result = await captureChromeReadiness(f.options, f.deps);
    assert.equal(result.eligible, succeeds);
    assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
    const text = readFileSync(f.options.output, "utf8");
    assert.equal(text.includes("fake-device"), false);
    assert.equal(text.includes("private-browser"), false);
    assert.equal(text.includes("private-user-agent"), false);
    assert.equal(text.includes("webSocket"), false);
    await assert.rejects(captureChromeReadiness(f.options, { forward: () => assert.fail("must not probe") }), /output-already-exists/);
  }
  assert.deepEqual(readdirSync(directory), ["failed.json", "ready.json"]);
});

test("CLI invalid arguments fail safely without adb, HTTP, raw IDs or output", () => {
  const result = spawnSync(process.execPath, [new URL("./chrome_readiness.mjs", import.meta.url).pathname,
    "--serial", "private-device", "--port", "0", "--label", "cell", "--output", "/private/missing/output"],
  { encoding: "utf8", env: { ...process.env, PATH: "" } });
  assert.equal(result.status, 2);
  assert.equal(result.stdout, "");
  assert.equal(result.stderr.includes("private-device"), false);
  assert.equal(result.stderr.includes("/private/missing"), false);
});
