import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";

for (const mode of ["close-before-navigation-reply", "navigation-error-before-load", "browser-close-before-open"])
test(`page CLI joins and diagnoses ${mode} without an unhandled event-wait rejection`, () => {
  // Exercise the real CLI and transport; only the browser is synthetic. A
  // delayed target cleanup makes the previously unobserved load rejection
  // deterministic, rather than depending on a race against process exit.
  const preload = `
    const mode = ${JSON.stringify(mode)};
    globalThis.fetch = async url => {
      if (url === 'http://127.0.0.1:9223/json/version') return { ok: true, json: async () => ({
        Browser: 'Chrome/152.0', 'Android-Package': 'com.android.chrome', webSocketDebuggerUrl: 'ws://fake/browser' }) };
      if (url === 'http://127.0.0.1:9223/json/list') return { ok: true, json: async () => [
        { id: 'owned', webSocketDebuggerUrl: 'ws://fake/page' }] };
      throw new Error('unexpected host request');
    };
    globalThis.WebSocket = class extends EventTarget {
      constructor(url) {
        super();
        if (!['ws://fake/browser', 'ws://fake/page'].includes(url)) throw new Error('unexpected websocket');
        setInterval(() => {}, 1000);
        queueMicrotask(() => {
          if (mode === 'browser-close-before-open') this.dispatchEvent(Object.assign(new Event('close'), {
            code: 1006, reason: 'secret-browser-close' }));
          else this.dispatchEvent(new Event('open'));
        });
      }
      send(data) {
        const { id, method } = JSON.parse(data);
        if (method === 'Page.navigate') {
          queueMicrotask(() => {
            if (mode === 'navigation-error-before-load') this.dispatchEvent(new MessageEvent('message', {
              data: JSON.stringify({ id, result: { errorText: 'secret-navigation-error' } }) }));
            else this.dispatchEvent(Object.assign(new Event('close'), { code: 1001, reason: 'secret-close-reason' }));
          });
          return;
        }
        const result = method === 'Target.createTarget' ? { targetId: 'owned' } : {};
        setTimeout(() => {
          if (method === 'Target.closeTarget') process.stdout.write('cleanup-observed\\n');
          this.dispatchEvent(new MessageEvent('message', { data: JSON.stringify({ id, result }) }));
        }, method === 'Target.closeTarget' ? 25 : 0);
      }
      close() {}
    };
  `;
  const child = spawnSync(process.execPath, ["--import", `data:text/javascript,${encodeURIComponent(preload)}`,
    fileURLToPath(new URL("./chrome_page_benchmark.mjs", import.meta.url)), "--port", "9223", "--runs", "1",
    "--timeout-ms", "2000", "https://example.invalid/"], { encoding: "utf8", timeout: 5000 });
  assert.equal(child.error, undefined);
  assert.equal(child.signal, null);
  assert.equal(child.status, 1);
  const lines = child.stdout.trim().split("\n");
  assert.equal(JSON.parse(lines[0]).type, "environment");
  if (mode !== "browser-close-before-open") assert.equal(lines[1], "cleanup-observed");
  else assert.equal(lines.length, 1);
  assert.doesNotMatch(child.stdout, /"type":"(?:sample|summary)"/);
  const error = JSON.parse(child.stderr);
  assert.equal(error.type, "page-error");
  assert.equal(error.reason, mode === "navigation-error-before-load" ? "benchmark-operation-failed" : "websocket-closed");
  if (mode === "close-before-navigation-reply") {
    assert.equal(error.operation, "Page.navigate");
    assert.equal(error.websocketCloseCode, 1001);
  }
  if (mode === "browser-close-before-open") assert.equal(error.websocketCloseCode, 1006);
  assert.doesNotMatch(child.stderr, /secret|Node\.js|Unhandled|node:|fake|invalid/);
});
