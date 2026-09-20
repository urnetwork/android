import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { DEVICE_MEMORY_TARGET_BYTES, evaluateMemoryProfile, GO_MEMORY_LIMIT_BYTES } from "./physical_memory_profile.mjs";

const ready = () => ({ type: "status", state: "ready", pid: 42,
  goMemoryLimitBytes: GO_MEMORY_LIMIT_BYTES, trackedMemory: { targetBytes: DEVICE_MEMORY_TARGET_BYTES } });

test("effective 20/32MiB inputs are required, independently of the 24MiB measured-runtime barrier", () => {
  assert.equal(DEVICE_MEMORY_TARGET_BYTES, 20 * 1024 * 1024);
  assert.equal(GO_MEMORY_LIMIT_BYTES, 32 * 1024 * 1024);
  assert.equal(evaluateMemoryProfile(ready()).eligible, true);
  assert.equal(evaluateMemoryProfile({ hostTimeUnixMs: 100, status: ready() }).eligible, true);
});

test("lY1fH2 root regression: omitted Gradle audit flag selects 28/40MiB and must fail before traffic", () => {
  const status = ready();
  status.trackedMemory.targetBytes = 29_360_128;
  status.goMemoryLimitBytes = 41_943_040;
  // Even a currently small runtime cannot make the wrong policy comparable.
  status.goRuntimeBytes = 13_271_056;
  const result = evaluateMemoryProfile(status);
  assert.equal(result.eligible, false);
  assert.equal(result.classification, "INVALID_MEMORY_PROFILE");
  assert.deepEqual(result.reasons, ["device-memory-target-not-20-mib", "go-memory-limit-not-32-mib"]);
});

test("missing, string-coerced, partial, stricter, or non-ready evidence never silently defaults", () => {
  for (const mutate of [
    (s) => { delete s.trackedMemory; },
    (s) => { delete s.goMemoryLimitBytes; },
    (s) => { s.goMemoryLimitBytes = `${GO_MEMORY_LIMIT_BYTES}`; },
    (s) => { s.trackedMemory.targetBytes -= 1; },
    (s) => { s.goMemoryLimitBytes -= 1; },
    (s) => { s.state = "error"; },
    (s) => { s.pid = 0; },
  ]) {
    const status = ready(); mutate(status);
    assert.equal(evaluateMemoryProfile(status).eligible, false);
  }
  assert.equal(evaluateMemoryProfile(null).eligible, false);
});

test("CLI is offline and emits only safe aggregate profile evidence", (t) => {
  const directory = mkdtempSync(join(tmpdir(), "physical-profile-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const path = join(directory, "private-status.json");
  const run = () => spawnSync(process.execPath, [new URL("./physical_memory_profile.mjs", import.meta.url).pathname,
    "--status", path], { encoding: "utf8", env: { ...process.env, PATH: "" } });
  writeFileSync(path, JSON.stringify({ ...ready(), secret: "private-identifier" }));
  let result = run();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(result.stdout).eligible, true);
  assert.equal(result.stdout.includes("private-identifier"), false);
  writeFileSync(path, JSON.stringify({ ...ready(), goMemoryLimitBytes: 40 * 1024 * 1024 }));
  result = run();
  assert.equal(result.status, 2);
  assert.equal(JSON.parse(result.stdout).classification, "INVALID_MEMORY_PROFILE");
  writeFileSync(path, "private-identifier-not-json");
  result = run();
  assert.equal(result.status, 2);
  assert.equal(result.stdout, "");
  assert.equal(result.stderr.includes("private-identifier"), false);
  assert.equal(result.stderr.includes(directory), false);
});
