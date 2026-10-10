import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { DEVICE_MEMORY_TARGET_BYTES, DIAGNOSTIC_PROFILE_RATE_BYTES, evaluateMemoryProfile,
  GO_MEMORY_LIMIT_BYTES, matchesMemoryProfileProof, memoryAuditPolicy, memoryProfileRequirements,
  MEMORY_AUDIT_PROFILES, parseMemoryProfileArgs } from "./physical_memory_profile.mjs";

const ready = () => ({ type: "status", state: "ready", pid: 42,
  memoryProfile: "ios-memory-audit-v2",
  goMemoryProfileRateBytes: 0,
  goMemoryLimitBytes: GO_MEMORY_LIMIT_BYTES, trackedMemory: { targetBytes: DEVICE_MEMORY_TARGET_BYTES } });

test("selected iOS profile must be observed, not inferred from 32/32 inputs or echoed request", () => {
  for (const memoryProfile of [undefined, null, "", "android", "ios-memory-audit-v1", "private-canary", true]) {
    const result = evaluateMemoryProfile({ ...ready(), memoryProfile, requiredProfile: "ios-memory-audit-v2" });
    assert.equal(result.eligible, false);
    assert.equal(result.classification, "INVALID_MEMORY_PROFILE");
    assert.ok(result.reasons.includes("selected-memory-profile-not-ios"));
    assert.equal(JSON.stringify(result).includes("private-canary"), false);
  }
  const result = evaluateMemoryProfile(ready());
  assert.equal(result.observedProfile, "ios-memory-audit-v2");
  assert.equal(result.requiredGoRuntimeLimitBytes, 33_554_432);
  assert.equal(result.requiredDeviceTargetBytes, 32 * 1024 * 1024);
  assert.equal(result.requiredGoMemoryLimitBytes, 32 * 1024 * 1024);
});

test("effective 32/32MiB inputs are required, independently of the 32MiB measured-runtime barrier", () => {
  assert.equal(DEVICE_MEMORY_TARGET_BYTES, 32 * 1024 * 1024);
  assert.equal(GO_MEMORY_LIMIT_BYTES, 32 * 1024 * 1024);
  assert.equal(evaluateMemoryProfile(ready()).eligible, true);
  assert.equal(evaluateMemoryProfile(ready()).qualificationEligible, true);
  assert.equal(evaluateMemoryProfile({ hostTimeUnixMs: 100, status: ready() }).eligible, true);
});

test("v1 remains 20/32/28 and requires an explicit historical profile selection", () => {
  assert.deepEqual(MEMORY_AUDIT_PROFILES["ios-memory-audit-v1"], { deviceTargetBytes: 20 * 1024 * 1024,
    goMemoryLimitBytes: 32 * 1024 * 1024, goRuntimeLimitBytes: 28 * 1024 * 1024 });
  const legacy = { ...ready(), memoryProfile: "ios-memory-audit-v1", trackedMemory: { targetBytes: 20 * 1024 * 1024 } };
  assert.equal(evaluateMemoryProfile(legacy).eligible, false);
  const explicit = evaluateMemoryProfile(legacy, { profile: "ios-memory-audit-v1" });
  assert.equal(explicit.eligible, true);
  assert.equal(explicit.requiredGoRuntimeLimitBytes, 28 * 1024 * 1024);
  assert.equal(evaluateMemoryProfile(ready(), { profile: "ios-memory-audit-v1" }).eligible, false);
  assert.equal(evaluateMemoryProfile(ready(), { profile: "private-canary" }).eligible, false);
});

test("explicit Android policy requires exact 64 MiB target and soft limit in both measurement modes", () => {
  const limit = 64 * 1024 * 1024;
  const status = { ...ready(), memoryProfile: "android", goMemoryLimitBytes: limit, trackedMemory: { targetBytes: limit } };
  assert.deepEqual(memoryAuditPolicy("android"), { deviceTargetBytes: limit, goMemoryLimitBytes: limit, goRuntimeLimitBytes: limit });
  for (const mode of ["qualification", "diagnostic"]) {
    status.goMemoryProfileRateBytes = mode === "qualification" ? 0 : 65_536;
    const result = evaluateMemoryProfile(status, { profile: "android", mode });
    assert.equal(result.eligible, true, mode);
    assert.equal(result.qualificationEligible, mode === "qualification");
    assert.equal(result.requiredProfile, "android");
    assert.equal(result.requiredDeviceTargetBytes, limit);
    assert.equal(result.requiredGoMemoryLimitBytes, limit);
    assert.equal(result.requiredGoRuntimeLimitBytes, limit);
    for (const bytes of [32 * 1024 * 1024, limit - 1, limit + 1, String(limit), null]) {
      assert.equal(evaluateMemoryProfile({ ...status, goMemoryLimitBytes: bytes }, { profile: "android", mode }).eligible, false);
      assert.equal(evaluateMemoryProfile({ ...status, trackedMemory: { targetBytes: bytes } }, { profile: "android", mode }).eligible, false);
    }
  }
  status.goMemoryProfileRateBytes = 0;
  assert.equal(evaluateMemoryProfile(status).eligible, false, "rows cannot select their own larger ceiling");
  assert.equal(evaluateMemoryProfile(ready(), { profile: "android" }).eligible, false);
  assert.equal(evaluateMemoryProfile({ ...status, memoryProfile: "ios-memory-audit-v2" }, { profile: "android" }).eligible, false);
});

test("profile policies are immutable and proof binding rejects identity and numeric borrowing", () => {
  assert.equal(Object.isFrozen(MEMORY_AUDIT_PROFILES), true);
  for (const profile of ["ios-memory-audit-v1", "ios-memory-audit-v2", "android"]) {
    assert.equal(Object.isFrozen(memoryAuditPolicy(profile)), true);
    assert.throws(() => { memoryAuditPolicy(profile).goRuntimeLimitBytes = 1; }, TypeError);
    const proof = memoryProfileRequirements(profile);
    assert.equal(matchesMemoryProfileProof(proof, profile), true);
    for (const key of Object.keys(proof)) {
      const changed = { ...proof, [key]: key === "requiredProfile" ? "foreign-profile" : proof[key] + 1 };
      assert.equal(matchesMemoryProfileProof(changed, profile), false, `${profile}:${key}`);
      delete changed[key];
      assert.equal(matchesMemoryProfileProof(changed, profile), false, `${profile}:${key}:missing`);
    }
    for (const other of ["ios-memory-audit-v1", "ios-memory-audit-v2", "android"]) {
      if (other !== profile) assert.equal(matchesMemoryProfileProof(proof, other), false, `${profile}:${other}`);
    }
  }
  for (const profile of [null, "", "toString", "__proto__", ["android"], { profile: "android" }, "normalAndroid"]) {
    assert.equal(memoryAuditPolicy(profile), null);
    assert.equal(matchesMemoryProfileProof(memoryProfileRequirements("android"), profile), false);
  }
  assert.equal(memoryAuditPolicy().goRuntimeLimitBytes, 32 * 1024 * 1024);
});

test("paZ8U8 root regression: a 32/32-MiB diagnostic runtime cannot start a rate-zero qualification", () => {
  const status = { ...ready(), goMemoryProfileRateBytes: 65_536, goRuntimeBytes: 15_030_536 };
  const result = evaluateMemoryProfile(status);
  assert.equal(result.eligible, false);
  assert.equal(result.qualificationEligible, false);
  assert.equal(result.classification, "INVALID_RATE_ZERO");
  assert.equal(result.requiredGoMemoryProfileRateBytes, 0);
  assert.equal(result.observedGoMemoryProfileRateBytes, 65_536);
  assert.deepEqual(result.reasons, ["go-memory-profile-rate-not-zero"]);
});

test("diagnostic opt-in requires its exact live rate and can never claim qualification", () => {
  const status = { ...ready(), goMemoryProfileRateBytes: DIAGNOSTIC_PROFILE_RATE_BYTES };
  const result = evaluateMemoryProfile(status, { mode: "diagnostic" });
  assert.equal(result.eligible, true);
  assert.equal(result.qualificationEligible, false);
  assert.equal(result.classification, "DIAGNOSTIC_PROFILE_READY");
  assert.equal(result.measurementMode, "diagnostic");
  assert.equal(result.requiredGoMemoryProfileRateBytes, 65_536);
  for (const rate of [undefined, null, "65536", 0, 65_535, 524_288]) {
    assert.equal(evaluateMemoryProfile({ ...status, goMemoryProfileRateBytes: rate }, { mode: "diagnostic" }).eligible, false);
  }
  assert.equal(evaluateMemoryProfile(ready(), { mode: "arbitrary" }).eligible, false);
});

test("a missing or coerced profile rate is not evidence that profiling is disabled", () => {
  for (const rate of [undefined, null, "0", false, -1, 1, NaN]) {
    const result = evaluateMemoryProfile({ ...ready(), goMemoryProfileRateBytes: rate });
    assert.equal(result.classification, "INVALID_RATE_ZERO");
    assert.equal(result.eligible, false);
  }
});

test("qualification is the CLI default and diagnostic intent cannot be inferred from a status", () => {
  assert.deepEqual(parseMemoryProfileArgs(["--status", "private.json"]), { status: "private.json", mode: "qualification", profile: "ios-memory-audit-v2" });
  assert.deepEqual(parseMemoryProfileArgs(["--mode", "diagnostic", "--status", "private.json"]),
    { status: "private.json", mode: "diagnostic", profile: "ios-memory-audit-v2" });
  assert.equal(parseMemoryProfileArgs(["--status", "private.json", "--profile", "ios-memory-audit-v1"]).profile, "ios-memory-audit-v1");
  assert.equal(parseMemoryProfileArgs(["--status", "private.json", "--profile", "android"]).profile, "android");
  for (const args of [[], ["--mode", "diagnostic"], ["--status", "x", "--mode", "other"],
    ["--status", "x", "--mode", "diagnostic", "--mode", "qualification"], ["--status", "x", "--status", "y"],
    ["--status", "x", "--rate", "0"], ["--status", "x", "--profile", "normalAndroid"],
    ["--status", "x", "--go-runtime-limit-bytes", "67108864"]]) assert.throws(() => parseMemoryProfileArgs(args));
});

test("lY1fH2 root regression: omitted Gradle audit flag selects 28/40MiB and must fail before traffic", () => {
  const status = ready();
  status.trackedMemory.targetBytes = 28 * 1024 * 1024;
  status.goMemoryLimitBytes = 41_943_040;
  // Even a currently small runtime cannot make the wrong policy comparable.
  status.goRuntimeBytes = 13_271_056;
  const result = evaluateMemoryProfile(status);
  assert.equal(result.eligible, false);
  assert.equal(result.classification, "INVALID_MEMORY_PROFILE");
  assert.deepEqual(result.reasons, ["device-memory-target-mismatch", "go-memory-limit-not-32-mib"]);
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
  const run = (extra = []) => spawnSync(process.execPath, [new URL("./physical_memory_profile.mjs", import.meta.url).pathname,
    "--status", path, ...extra], { encoding: "utf8", env: { ...process.env, PATH: "" } });
  writeFileSync(path, JSON.stringify({ ...ready(), secret: "private-identifier" }));
  let result = run();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(result.stdout).eligible, true);
  assert.equal(result.stdout.includes("private-identifier"), false);
  writeFileSync(path, JSON.stringify({ ...ready(), goMemoryProfileRateBytes: 65_536 }));
  result = run();
  assert.equal(result.status, 2);
  assert.equal(JSON.parse(result.stdout).classification, "INVALID_RATE_ZERO");
  result = run(["--mode", "diagnostic"]);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(result.stdout).qualificationEligible, false);
  writeFileSync(path, JSON.stringify({ ...ready(), memoryProfile: "android", goMemoryLimitBytes: 64 * 1024 * 1024,
    trackedMemory: { targetBytes: 64 * 1024 * 1024 } }));
  result = run(["--profile", "android"]);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(result.stdout).requiredGoRuntimeLimitBytes, 64 * 1024 * 1024);
  assert.equal(run().status, 2, "default standalone calls retain the iOS policy");
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
