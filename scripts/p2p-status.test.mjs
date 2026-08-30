import test from "node:test";
import assert from "node:assert/strict";

import { evaluateStatus } from "./p2p-status.mjs";

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
