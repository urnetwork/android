import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

// Pure JVM behavior tests are insufficient if instrumentation bypasses the owner.
test("physical typed failure is held until exact finish and rethrown after owned teardown", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const owned = source.slice(source.indexOf("    private fun runOwnedDeviceSession("), source.indexOf("    private class MemoryTeardownState"));
  assert.match(owned, /val commandOwner = PhysicalCommandOwner\(\)/);
  assert.match(owned, /finished = commandOwner\.run\(/);
  assert.match(owned, /commandResult\(text, application, activeDevice,/);
  assert.match(owned, /failureStatus\(id, application, startElapsedMs, captureFailure\(error\), cleanupOnly = true\)/);
  assert.match(owned, /val deadline = startElapsedMs \+ MAX_SESSION_MILLIS/);
  assert.match(owned, /while \(!finished && SystemClock\.elapsedRealtime\(\) < deadline\)/);
  assert.match(owned, /SystemClock\.sleep\(COMMAND_POLL_MILLIS\)/);
  assert.match(owned, /assertTrue\("physical session reached its safety timeout", finished\)\s+return commandOwner\.failure/);
  assert.match(owned, /finally \{\s+closeOwnedDeviceScope\(/);
  const outer = source.slice(source.indexOf("    fun physicalLowbarSession()"), source.indexOf("    private fun runOwnedDeviceSession("));
  assert.match(outer, /sessionFailure = runOwnedDeviceSession\(/);
  const completed = outer.indexOf("finishMemoryTeardown(summary)");
  const rethrow = outer.indexOf("sessionFailure?.let { throw it }");
  assert.ok(completed > 0 && rethrow > completed, "failed command must remain failed after native teardown");
  assert.doesNotMatch(outer.slice(completed), /failureStatus\(/);
  assert.match(source, /cleanupOnly: Boolean = false/);
  assert.match(source, /if \(cleanupOnly\) extra\.put\("cleanupOnly", true\)/);
});
