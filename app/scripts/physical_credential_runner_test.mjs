import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = (path) => readFileSync(new URL(`../app/${path}`, import.meta.url), "utf8");
const runner = "com.bringyour.network.acceptance.PhysicalCredentialDiagnosticRunner";

test("the diagnostic component is registered separately without replacing the default runner", () => {
  const manifest = source("src/androidTest/AndroidManifest.xml").replace(/<!--[\s\S]*?-->/g, "");
  const components = [...manifest.matchAll(/<instrumentation\b[^>]*>/g)].map(([element]) => ({
    name: /\bandroid:name="([^"]+)"/.exec(element)?.[1],
    target: /\bandroid:targetPackage="([^"]+)"/.exec(element)?.[1],
  }));
  // AGP's Instrumentation system-property override targets the first element.
  // A lone diagnostic declaration is silently renamed to the default runner.
  assert.deepEqual(components, [
    { name: "androidx.test.runner.AndroidJUnitRunner", target: "com.bringyour.network" },
    { name: runner, target: "com.bringyour.network" },
  ]);
  assert.match(source("build.gradle"), /testInstrumentationRunner\s+'androidx\.test\.runner\.AndroidJUnitRunner'/);
});

test("the earliest hook uses incoming arguments and delegates unchanged arguments to AndroidJUnitRunner", () => {
  const hook = source("src/androidTest/java/com/bringyour/network/acceptance/PhysicalCredentialDiagnosticRunner.kt");
  assert.match(hook, /class PhysicalCredentialDiagnosticRunner : AndroidJUnitRunner\(\)/);
  assert.match(hook, /override fun onCreate\(arguments: Bundle\?\)/);
  assert.match(hook, /withPhysicalCredentialRunnerCheckpoints\(/);
  assert.match(hook, /arguments\?\.getString\(PHYSICAL_CREDENTIAL_DIAGNOSTICS_ARGUMENT\)/);
  assert.match(hook, /createRunner = \{ super\.onCreate\(arguments\) \}/);
  assert.doesNotMatch(hook, /InstrumentationRegistry|readLines|readText|readBytes|logout\(|login\(/);
});

test("the shared sink is metadata-only private bounded and cannot block on a substituted FIFO", () => {
  const sink = source("src/androidTest/java/com/bringyour/network/acceptance/PhysicalCredentialDiagnosticRecorder.kt");
  assert.match(sink, /File\(context\.dataDir, "files\/acceptance\/credentials"\)/);
  assert.match(sink, /Os\.lstat\(credentials\.absolutePath\)/);
  assert.match(sink, /O_NOFOLLOW or OsConstants\.O_NONBLOCK/);
  assert.match(sink, /physicalCredentialCheckpointAppendAllowed\(prior\.st_size, bytes\.size\)/);
  assert.match(sink, /@Synchronized/);
  assert.doesNotMatch(sink, /context\.filesDir|readLines|readText|readBytes|FileInputStream|MessageDigest|\.delete\(|\.renameTo\(/);
  assert.doesNotMatch(sink, /Log\.[a-z]+\([^\n]*(?:error|exception|absolutePath|\.message|\.toString\()/);
});
