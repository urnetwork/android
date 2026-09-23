import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  buildResult,
  classifyFailure,
  recommendedModelTier,
  summarizeLog,
} from "./acceptance-result.mjs";

test("summarizes a bounded, redacted failure window", () => {
  const summary = summarizeLog([
    "before",
    "java.lang.AssertionError: expected connected but was disconnected",
    "at example.Test.run(Test.kt:41)",
    "token=not-for-a-model",
  ].join("\n"));

  assert.match(summary.signature, /AssertionError/);
  assert.equal(summary.excerpt[1].text, "java.lang.AssertionError: expected connected but was disconnected");
  assert.equal(summary.excerpt.at(-1).text, "token=[REDACTED]");
});

test("classifies acceptance failures and reserves strong debugging for them", () => {
  assert.equal(classifyFailure("instrumentation", "FATAL EXCEPTION: main"), "crash");
  assert.equal(classifyFailure("instrumentation", "Unable to resolve host"), "network");
  assert.equal(classifyFailure("build", "anything"), "build");
  assert.equal(recommendedModelTier("failed", "instrumentation", "assertion"), "strong");
  assert.equal(recommendedModelTier("failed", "build", "build"), "focused");
  assert.equal(recommendedModelTier("passed", "instrumentation", null), "none");
});

test("builds a compact result with an artifact index", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "urnetwork-acceptance-result."));
  const outputPath = path.join(directory, "result.json");
  const logPath = path.join(directory, "instrumentation.log");
  fs.writeFileSync(logPath, "INSTRUMENTATION_FAILED: Process crashed\n");
  fs.writeFileSync(path.join(directory, "final.png"), "png");

  const result = buildResult({
    outputPath,
    environment: {
      UR_ACCEPT_RESULT_RUN_ID: "run-1",
      UR_ACCEPT_RESULT_PROFILE: "smoke",
      UR_ACCEPT_RESULT_TARGET: "github",
      UR_ACCEPT_RESULT_PHASE: "instrumentation",
      UR_ACCEPT_RESULT_STATUS: "failed",
      UR_ACCEPT_RESULT_EXIT_CODE: "1",
      UR_ACCEPT_RESULT_ARTIFACT_ROOT: directory,
      UR_ACCEPT_RESULT_LOG: logPath,
      UR_ACCEPT_RESULT_REPRO_COMMAND: "adb shell am instrument ...",
    },
  });

  assert.equal(result.failure.classification, "crash");
  assert.equal(result.recommendedModelTier, "strong");
  assert.deepEqual(result.artifacts.map((artifact) => artifact.path), ["final.png", "instrumentation.log"]);
  fs.rmSync(directory, { recursive: true, force: true });
});

function p2pFixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "urnetwork-p2p-result."));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const environment = {
    UR_ACCEPT_RESULT_TARGET: "play",
    UR_ACCEPT_RESULT_PHASE: "peer-to-peer",
    UR_ACCEPT_RESULT_STATUS: "failed",
    UR_ACCEPT_RESULT_EXIT_CODE: "1",
    UR_ACCEPT_RESULT_ARTIFACT_ROOT: directory,
    UR_ACCEPT_RESULT_LOG: path.join(directory, "client-instrumentation.log"),
  };
  return {
    directory,
    environment,
    write(name, contents) { fs.writeFileSync(path.join(directory, name), contents); },
    result() { return buildResult({ outputPath: path.join(directory, "result.json"), environment }); },
  };
}

test("retains ADB infrastructure cause instead of a later cleanup-induced client crash", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "provider", reason: "artifact-collection", classification: "infrastructure",
  }));
  fixture.write("client-instrumentation.log", "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n");
  fixture.write("provider-instrumentation.log", "INSTRUMENTATION_STATUS_CODE: 1\n");
  const result = fixture.result();
  assert.equal(result.status, "failed");
  assert.equal(result.failure.classification, "infrastructure");
  assert.equal(result.failure.signature, "P2P provider failed: artifact-collection");
  assert.equal(result.failure.originalCause.role, "provider");
  assert.equal(result.recommendedModelTier, "strong");
  assert.ok(result.artifacts.some((artifact) => artifact.path === "client-instrumentation.log"));
  assert.ok(result.artifacts.some((artifact) => artifact.path === "provider-instrumentation.log"));
});

test("lost instrumentation terminal receipt stays failed despite a recovered app finish", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "provider", reason: "instrumentation-stream-lost", classification: "infrastructure",
  }));
  fixture.write("provider-instrumentation.log", "INSTRUMENTATION_STATUS_CODE: 1\n");
  fixture.write("provider-status.json", JSON.stringify({ commandId: "provider-finish", state: "complete" }));
  const result = fixture.result();
  assert.equal(result.failure.classification, "infrastructure");
  assert.match(result.failure.signature, /instrumentation-stream-lost/);
});

test("genuine provider crash uses provider evidence instead of the client log", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "provider", reason: "instrumentation-failed", classification: "crash",
  }));
  fixture.write("client-instrumentation.log", "OK (1 test)\nINSTRUMENTATION_CODE: -1\n");
  fixture.write("provider-instrumentation.log", "FATAL EXCEPTION: main\ntoken=not-published\n");
  const result = fixture.result();
  assert.equal(result.failure.classification, "crash");
  assert.equal(result.failure.signature, "FATAL EXCEPTION: main");
  assert.equal(result.failure.excerpt[1].text, "token=[REDACTED]");
});

test("a deadline remains the first cause after bounded forced termination", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "client", reason: "natural-exit-timeout", classification: "timeout",
  }));
  fixture.write("client-instrumentation.log", "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n");
  const result = fixture.result();
  assert.equal(result.failure.classification, "timeout");
  assert.match(result.failure.signature, /natural-exit-timeout/);
});

test("failure provenance is bounded, finite and cannot publish extra raw fields", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "provider", reason: "artifact-collection", classification: "infrastructure",
    password: "never-publish", rawLog: "never-publish",
  }));
  assert.doesNotMatch(JSON.stringify(fixture.result()), /never-publish/);
  for (const invalid of [
    null,
    { schemaVersion: 2, role: "client", reason: "artifact-collection", classification: "infrastructure" },
    { schemaVersion: 1, role: "../foreign", reason: "instrumentation-failed", classification: "crash" },
    { schemaVersion: 1, role: "client", reason: "unbounded-server-message", classification: "infrastructure" },
    { schemaVersion: 1, role: "client", reason: "artifact-collection", classification: "crash" },
  ]) {
    fixture.write("p2p-first-failure.json", JSON.stringify(invalid));
    assert.throws(() => fixture.result(), /invalid P2P failure provenance/);
  }
  fixture.write("p2p-first-failure.json", " ".repeat(4097));
  assert.throws(() => fixture.result(), /invalid P2P failure provenance file/);
});

test("successful instrumentation plus failed package cleanup reports infrastructure cause", (t) => {
  const fixture = p2pFixture(t);
  fixture.environment.UR_ACCEPT_RESULT_PHASE = "instrumentation";
  fixture.environment.UR_ACCEPT_RESULT_LOG = path.join(fixture.directory, "instrumentation.log");
  fixture.write("instrumentation.log", "OK (2 tests)\nINSTRUMENTATION_CODE: -1\n");
  fixture.write("cleanup-failure.json", JSON.stringify({
    schemaVersion: 1, phase: "post-acceptance-cleanup", reason: "ownership-unavailable",
    classification: "infrastructure", precedingTestExitCode: 0,
  }));
  const result = fixture.result();
  assert.equal(result.status, "failed");
  assert.equal(result.failure.classification, "infrastructure");
  assert.equal(result.failure.signature, "Acceptance cleanup failed: ownership-unavailable");
  assert.equal(result.failure.originalCause.precedingTestExitCode, 0);
  assert.ok(result.artifacts.some((artifact) => artifact.path === "cleanup-failure.json"));
  assert.ok(result.artifacts.some((artifact) => artifact.path === "instrumentation.log"));
});

test("cleanup failure remains secondary to an already failed app test", (t) => {
  const fixture = p2pFixture(t);
  fixture.environment.UR_ACCEPT_RESULT_PHASE = "instrumentation";
  fixture.environment.UR_ACCEPT_RESULT_LOG = path.join(fixture.directory, "instrumentation.log");
  fixture.write("instrumentation.log", "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n");
  fixture.write("cleanup-failure.json", JSON.stringify({
    schemaVersion: 1, phase: "post-acceptance-cleanup", reason: "package-removal-unverified",
    classification: "infrastructure", precedingTestExitCode: 1,
  }));
  const result = fixture.result();
  assert.equal(result.failure.classification, "crash");
  assert.match(result.failure.signature, /Process crashed/);
  assert.equal(result.failure.originalCause, undefined);
  assert.equal(result.failure.cleanupFailure.reason, "package-removal-unverified");
});

test("early peer preparation failure has a finite cause and cell-local readiness artifact", (t) => {
  const fixture = p2pFixture(t);
  fixture.write("p2p-first-failure.json", JSON.stringify({
    schemaVersion: 1, role: "provider", reason: "peer-readiness-failed", classification: "infrastructure",
  }));
  fs.mkdirSync(path.join(fixture.directory, "provider-readiness"));
  fixture.write("provider-readiness/readiness.txt", "status=api-unavailable\n");
  const result = fixture.result();
  assert.equal(result.status, "failed");
  assert.equal(result.failure.classification, "infrastructure");
  assert.equal(result.failure.signature, "P2P provider failed: peer-readiness-failed");
  assert.ok(result.artifacts.some((artifact) => artifact.path === "provider-readiness/readiness.txt"));
});

test("cleanup provenance is bounded, finite and scoped to the failed instrumentation cell", (t) => {
  const fixture = p2pFixture(t);
  fixture.environment.UR_ACCEPT_RESULT_PHASE = "instrumentation";
  const valid = {
    schemaVersion: 1, phase: "post-acceptance-cleanup", reason: "ownership-unavailable",
    classification: "infrastructure", precedingTestExitCode: 0,
  };
  fixture.write("cleanup-failure.json", JSON.stringify({ ...valid, secret: "never-publish" }));
  assert.doesNotMatch(JSON.stringify(fixture.result()), /never-publish/);
  for (const invalid of [
    null, [], { ...valid, schemaVersion: 2 }, { ...valid, phase: "foreign" },
    { ...valid, reason: "unbounded-device-message" }, { ...valid, classification: "crash" },
    { ...valid, precedingTestExitCode: -1 }, { ...valid, precedingTestExitCode: 256 },
    { ...valid, precedingTestExitCode: "0" },
  ]) {
    fixture.write("cleanup-failure.json", JSON.stringify(invalid));
    assert.throws(() => fixture.result(), /invalid acceptance cleanup provenance/);
  }
  fixture.write("cleanup-failure.json", " ".repeat(4097));
  assert.throws(() => fixture.result(), /invalid acceptance cleanup provenance file/);
  fs.unlinkSync(path.join(fixture.directory, "cleanup-failure.json"));
  fixture.write("elsewhere.json", JSON.stringify(valid));
  fs.symlinkSync(path.join(fixture.directory, "elsewhere.json"), path.join(fixture.directory, "cleanup-failure.json"));
  assert.throws(() => fixture.result(), /invalid acceptance cleanup provenance file/);
  fs.unlinkSync(path.join(fixture.directory, "elsewhere.json"));
  assert.throws(() => fixture.result(), /invalid acceptance cleanup provenance file/);
  // A failed P2P event in a nested cell must not consume the parent's cleanup
  // cause, and a passing event never publishes stale failure details.
  fixture.environment.UR_ACCEPT_RESULT_PHASE = "peer-to-peer";
  assert.equal(fixture.result().failure.originalCause, undefined);
  fixture.environment.UR_ACCEPT_RESULT_PHASE = "instrumentation";
  fixture.environment.UR_ACCEPT_RESULT_STATUS = "passed";
  assert.equal(fixture.result().failure, null);
});
