// Shared Chrome DevTools transport. Failure evidence is deliberately limited
// to protocol operation names and numeric close/error codes: Chrome messages,
// websocket URLs, close reasons and page expressions can contain private data.

const OPERATIONS = new Set([
  "Browser.getVersion",
  "Target.createBrowserContext", "Target.disposeBrowserContext", "Target.createTarget", "Target.closeTarget",
  "Page.enable", "Page.navigate", "Page.loadEventFired", "Network.enable", "Network.setCacheDisabled",
  "Network.clearBrowserCache", "Runtime.enable", "Runtime.evaluate",
]);

export class CdpError extends Error {
  constructor(reason, operation, closeCode, protocolCode) {
    super(`DevTools ${reason}`);
    this.name = "CdpError";
    this.reason = reason;
    this.operation = operation;
    this.closeCode = closeCode;
    this.protocolCode = protocolCode;
  }
}

export function cdpFailureDetails(error) {
  if (!(error instanceof CdpError)) return { reason: "benchmark-operation-failed" };
  return {
    reason: error.reason,
    ...(OPERATIONS.has(error.operation) ? { operation: error.operation } : {}),
    ...(Number.isInteger(error.closeCode) && error.closeCode >= 1000 && error.closeCode <= 4999
      ? { websocketCloseCode: error.closeCode } : {}),
    ...(Number.isSafeInteger(error.protocolCode) ? { protocolCode: error.protocolCode } : {}),
  };
}

export class CdpSession {
  constructor(url, dependencies = {}) {
    this.nextId = 1;
    this.pending = new Map();
    this.waiters = new Map();
    this.listeners = new Map();
    this.openWaiters = new Set();
    this.opened = false;
    this.failure = null;
    this.socket = (dependencies.createSocket ?? (value => new WebSocket(value)))(url);
    this.socket.addEventListener("open", () => {
      if (this.failure) return;
      this.opened = true;
      for (const waiter of this.openWaiters) {
        clearTimeout(waiter.timer);
        waiter.resolve();
      }
      this.openWaiters.clear();
    });
    this.socket.addEventListener("close", event => this.fail(new CdpError("websocket-closed", undefined, event.code)));
    this.socket.addEventListener("error", () => this.fail(new CdpError("websocket-error")));
    this.socket.addEventListener("message", event => this.handleMessage(event.data));
  }

  fail(error) {
    this.failure ??= error;
    const failedOperation = operation => new CdpError(this.failure.reason, operation,
      this.failure.closeCode, this.failure.protocolCode);
    for (const waiter of this.openWaiters) {
      clearTimeout(waiter.timer);
      waiter.reject(this.failure);
    }
    this.openWaiters.clear();
    for (const pending of this.pending.values()) pending.reject(failedOperation(pending.method));
    this.pending.clear();
    for (const [method, waiters] of this.waiters) {
      for (const waiter of waiters) {
        clearTimeout(waiter.timer);
        waiter.reject(failedOperation(method));
      }
    }
    this.waiters.clear();
  }

  open(timeoutMs) {
    if (this.failure) return Promise.reject(this.failure);
    if (this.opened) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const waiter = { resolve, reject };
      waiter.timer = setTimeout(() => {
        this.openWaiters.delete(waiter);
        reject(new CdpError("websocket-open-timeout"));
      }, timeoutMs);
      this.openWaiters.add(waiter);
    });
  }

  handleMessage(data) {
    if (this.failure) return;
    let message;
    try { message = JSON.parse(data); }
    catch { this.fail(new CdpError("invalid-cdp-response")); return; }
    if (!message || typeof message !== "object" || Array.isArray(message)) {
      this.fail(new CdpError("invalid-cdp-response"));
      return;
    }
    if (message.id !== undefined) {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new CdpError("command-failed", pending.method, undefined, message.error.code));
      else pending.resolve(message.result ?? {});
      return;
    }
    if (typeof message.method !== "string") return;
    const params = message.params ?? {};
    for (const listener of [...(this.listeners.get(message.method) ?? [])]) listener(params);
    const waiters = this.waiters.get(message.method);
    if (!waiters) return;
    for (const waiter of [...waiters]) {
      if (!waiter.predicate(params)) continue;
      clearTimeout(waiter.timer);
      waiters.delete(waiter);
      waiter.resolve(params);
    }
    if (waiters.size === 0) this.waiters.delete(message.method);
  }

  send(method, params = {}) {
    if (this.failure) return Promise.reject(new CdpError(this.failure.reason, method,
      this.failure.closeCode, this.failure.protocolCode));
    if (!this.opened) return Promise.reject(new CdpError("websocket-not-open", method));
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { method, resolve, reject });
      try { this.socket.send(JSON.stringify({ id, method, params })); }
      catch {
        this.pending.delete(id);
        reject(new CdpError("websocket-send-failed", method));
      }
    });
  }

  waitFor(method, predicate, timeoutMs) {
    if (this.failure) return Promise.reject(new CdpError(this.failure.reason, method, this.failure.closeCode));
    return new Promise((resolve, reject) => {
      const waiters = this.waiters.get(method) ?? new Set();
      const waiter = { predicate, resolve, reject };
      waiter.timer = setTimeout(() => {
        waiters.delete(waiter);
        if (waiters.size === 0) this.waiters.delete(method);
        reject(new CdpError("event-timeout", method));
      }, timeoutMs);
      waiters.add(waiter);
      this.waiters.set(method, waiters);
    });
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? new Set();
    listeners.add(listener);
    this.listeners.set(method, listeners);
    return () => {
      listeners.delete(listener);
      if (listeners.size === 0) this.listeners.delete(method);
    };
  }

  close() {
    this.fail(new CdpError("session-closed"));
    this.listeners.clear();
    this.socket.close();
  }
}
