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
