import test from "node:test";
import assert from "node:assert/strict";

import { classifyStatus, evaluateStatus } from "./p2p-status.mjs";

const bidirectional = {
  remoteEgressPackets: 2,
  remoteEgressBytes: 120,
  remoteIngressPackets: 1,
  remoteIngressBytes: 80,
};

test("accepts exact command completion with bidirectional client proof", () => {
  assert.equal(evaluateStatus({
    commandId: "client-probe",
    state: "complete",
    packets: bidirectional,
    extra: { address: "2001:db8::1" },
  }, "client-probe", "complete", "client"), true);
});

test("rejects stale, one-way, and missing client proofs", () => {
  assert.equal(evaluateStatus({
    commandId: "old",
    state: "complete",
    packets: bidirectional,
    extra: { address: "192.0.2.1" },
  }, "client-probe", "complete", "client"), false);
  assert.equal(evaluateStatus({
    commandId: "client-probe",
    state: "complete",
    packets: { ...bidirectional, remoteIngressBytes: 0 },
    extra: { address: "192.0.2.1" },
  }, "client-probe", "complete", "client"), false);
  assert.equal(evaluateStatus({
    commandId: "client-probe",
    state: "complete",
    packets: bidirectional,
    extra: {},
  }, "client-probe", "complete", "client"), false);
});

test("provider proof independently requires both directions", () => {
  assert.equal(evaluateStatus({
    commandId: "provider-proof",
    state: "complete",
    providerPackets: bidirectional,
  }, "provider-proof", "complete", "provider"), true);
  assert.equal(evaluateStatus({
    commandId: "provider-proof",
    state: "complete",
    providerPackets: { ...bidirectional, remoteEgressPackets: 0 },
  }, "provider-proof", "complete", "provider"), false);
});

test("classifies exact bounded login failure as terminal", () => {
  assert.equal(classifyStatus({
    commandId: "0",
    state: "error",
    extra: {
      stage: "device-local-initialization",
      failure: "device-local-creation-failed",
    },
  }, "0", "ready"), "terminal-error");
});

test("classifies finite pre-password UI failures without inventing logout or success", () => {
  const cases = [
    ["auth-user-form", "ui-action-failed"],
    ["auth-discovery", "ui-action-failed"],
    ["auth-discovery", "auth-discovery-failed"],
    ["auth-discovery", "auth-discovery-timeout"],
    ["auth-discovery", "auth-discovery-observation-failed"],
    ["auth-password-submit", "ui-action-failed"],
  ];
  for (const [stage, failure] of cases) {
    const status = {
      commandId: "0",
      state: "error",
      extra: {
        stage,
        failure,
        loginUiBeforeTeardown: {
          userFormVisible: true,
          passwordFormVisible: false,
          discoveryErrorVisible: failure === "auth-discovery-failed",
        },
      },
    };
    assert.equal(classifyStatus(status, "0", "ready"), "terminal-error", `${stage}/${failure}`);
    assert.equal(evaluateStatus(status, "0", "ready"), false);
    assert.equal(classifyStatus(status, "new", "ready"), "pending");
  }
});

test("rejects invalid UI stage/failure combinations and raw discovery errors", () => {
  for (const [stage, failure] of [
    ["logout", "auth-discovery-failed"],
    ["auth-password-submit", "auth-discovery-timeout"],
    ["auth-discovery", "auth-logout"],
    ["auth-discovery", "server said private account text"],
    ["auth-discovery", undefined],
  ]) {
    assert.equal(classifyStatus({ commandId: "0", state: "error", extra: { stage, failure } },
      "0", "ready"), "invalid-terminal", `${stage}/${failure}`);
  }
});

test("does not confuse stale or unbounded errors with the requested command", () => {
  assert.equal(classifyStatus({
    commandId: "old",
    state: "error",
    extra: { stage: "auth-client", failure: "auth-client-request-failed" },
  }, "0", "ready"), "pending");
  assert.equal(classifyStatus({
    commandId: "0",
    state: "error",
    extra: { stage: "secret-stage", failure: "server said everything" },
  }, "0", "ready"), "invalid-terminal");
});

test("classifies only the exact ledger persistence terminal as cleanup-unsafe", () => {
  assert.equal(classifyStatus({
    commandId: "0",
    state: "error",
    extra: {
      stage: "client-allocation",
      failure: "cleanup-ledger-persistence-failed",
    },
  }, "0", "ready"), "cleanup-error");
  assert.equal(classifyStatus({
    commandId: "0",
    state: "error",
    extra: {
      stage: "auth-client",
      failure: "cleanup-ledger-persistence-failed",
    },
  }, "0", "ready"), "invalid-terminal");
});
