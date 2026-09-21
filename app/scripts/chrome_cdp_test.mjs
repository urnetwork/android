import assert from "node:assert/strict";
import test from "node:test";
import { CdpError, CdpSession, cdpFailureDetails } from "./chrome_cdp.mjs";

function fixture() {
  class Socket extends EventTarget {
    sent = [];
    send(value) { this.sent.push(JSON.parse(value)); }
    close() { this.closed = true; }
    emit(type, props = {}) { this.dispatchEvent(Object.assign(new Event(type), props)); }
    reply(value) { this.emit("message", { data: JSON.stringify(value) }); }
  }
  const socket = new Socket();
  const session = new CdpSession("ws://unused/private", { createSocket: () => socket });
  return { socket, session };
}

test("closing before open rejects immediately and remembers failure for later operations", async () => {
  const { socket, session } = fixture();
  const opening = session.open(60_000);
  socket.emit("close", { code: 1006, reason: "private token" });
  await assert.rejects(opening, error => {
    assert.deepEqual(cdpFailureDetails(error), { reason: "websocket-closed", websocketCloseCode: 1006 });
    return true;
  });
  await assert.rejects(session.open(60_000), /websocket-closed/);
  await assert.rejects(session.send("Page.navigate", { url: "https://private.invalid/secret" }), error => {
    assert.deepEqual(cdpFailureDetails(error), { reason: "websocket-closed", operation: "Page.navigate", websocketCloseCode: 1006 });
    return true;
  });
  assert.equal(socket.sent.length, 0);
  assert.equal(session.openWaiters.size, 0);
});

test("one close rejects concurrent page commands and load wait with their own operation", async () => {
  const { socket, session } = fixture();
  socket.emit("open");
  await session.open(60_000);
  const navigation = session.send("Page.navigate", { url: "https://example.invalid/" }).catch(error => error);
  const loaded = session.waitFor("Page.loadEventFired", () => true, 60_000).catch(error => error);
  socket.emit("close", { code: 1001, reason: "private-page-state" });
  assert.deepEqual(cdpFailureDetails(await navigation), {
    reason: "websocket-closed", operation: "Page.navigate", websocketCloseCode: 1001,
  });
  assert.deepEqual(cdpFailureDetails(await loaded), {
    reason: "websocket-closed", operation: "Page.loadEventFired", websocketCloseCode: 1001,
  });
  assert.equal(session.pending.size, 0);
  assert.equal(session.waiters.size, 0);
  // A cleanup close must not replace the primary unexpected-close evidence.
  session.close();
  assert.equal(session.failure.reason, "websocket-closed");
});

test("successful commands, listeners and event waits retain existing behavior", async () => {
  const { socket, session } = fixture();
  const opened = session.open(60_000);
  socket.emit("open");
  await opened;
  const created = session.send("Target.createTarget", { url: "about:blank" });
  socket.reply({ id: socket.sent[0].id, result: { targetId: "owned" } });
  assert.deepEqual(await created, { targetId: "owned" });
  const events = [];
  const remove = session.on("Page.loadEventFired", params => events.push(params));
  const loaded = session.waitFor("Page.loadEventFired", params => params.timestamp > 1, 60_000);
  socket.reply({ method: "Page.loadEventFired", params: { timestamp: 1 } });
  socket.reply({ method: "Page.loadEventFired", params: { timestamp: 2 } });
  assert.deepEqual(await loaded, { timestamp: 2 });
  assert.equal(events.length, 2);
  remove();
  assert.equal(session.listeners.size, 0);
  assert.equal(session.waiters.size, 0);
  session.close();
});

test("protocol failures keep numeric diagnostics without remote messages or arbitrary operations", async () => {
  const { socket, session } = fixture();
  socket.emit("open");
  const pending = session.send("Runtime.evaluate", { expression: "private expression" });
  socket.reply({ id: 1, error: { code: -32000, message: "https://private.invalid/token?secret=yes" } });
  await assert.rejects(pending, error => {
    const summary = cdpFailureDetails(error);
    assert.deepEqual(summary, { reason: "command-failed", operation: "Runtime.evaluate", protocolCode: -32000 });
    assert.doesNotMatch(JSON.stringify(summary) + error.message, /private|token|secret|expression/);
    return true;
  });
  assert.deepEqual(cdpFailureDetails(new Error("private URL")), { reason: "benchmark-operation-failed" });
  assert.deepEqual(cdpFailureDetails(new CdpError("websocket-closed", "private-operation", "private-code")),
    { reason: "websocket-closed" });
  session.close();
});

test("malformed responses fail every pending operation without leaking response bodies", async () => {
  for (const data of ["private invalid JSON", "null", "[]"]) {
    const { socket, session } = fixture();
    socket.emit("open");
    const pending = session.send("Runtime.enable");
    socket.emit("message", { data });
    await assert.rejects(pending, error => {
      assert.deepEqual(cdpFailureDetails(error), { reason: "invalid-cdp-response", operation: "Runtime.enable" });
      return true;
    });
    assert.equal(session.pending.size, 0);
    session.close();
  }
});

test("socket errors and send exceptions are observed without a delayed timeout", async () => {
  const f = fixture();
  const opening = f.session.open(60_000);
  f.socket.emit("error", { message: "private network error" });
  await assert.rejects(opening, /websocket-error/);
  f.session.close();
  const g = fixture();
  g.socket.emit("open");
  g.socket.send = () => { throw new Error("private payload"); };
  await assert.rejects(g.session.send("Page.navigate"), /websocket-send-failed/);
  assert.equal(g.session.pending.size, 0);
  g.session.close();
});
