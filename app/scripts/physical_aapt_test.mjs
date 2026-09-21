import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { resolveAapt } from "./physical_aapt.mjs";

const script = fileURLToPath(new URL("./physical_aapt.mjs", import.meta.url));
const workspace = fileURLToPath(new URL("../../../", import.meta.url));

function fixture(t) {
  const root = realpathSync(mkdtempSync(join(tmpdir(), "physical aapt-")));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const home = join(root, "user home"); const cwd = join(home, "urnetwork", "tests");
  mkdirSync(cwd, { recursive: true });
  const sdkRoot = join(home, "Library", "Android", "sdk");
  const env = { ...process.env, HOME: home };
  delete env.ANDROID_HOME; delete env.ANDROID_SDK_ROOT;
  function install(version, sdk = sdkRoot, mode = 0o700) {
    const path = join(sdk, "build-tools", version, "aapt");
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, "#!/bin/sh\nexit 0\n", { mode });
    return path;
  }
  return { root, home, cwd, sdkRoot, env, install };
}

test("IBNQ9U: nested checkout never becomes HOME when locating the macOS SDK", t => {
  const f = fixture(t); const expected = f.install("37.0.0");
  assert.equal(resolveAapt({ env: {}, platform: "darwin", home: f.home }), expected);
  const oldWrongRoot = join(dirname(f.cwd), "Library", "Android", "sdk");
  assert.throws(() => resolveAapt({ env: { ANDROID_SDK_ROOT: oldWrongRoot }, home: f.home }), /android-build-tools-unavailable/);
  if (process.platform === "darwin") {
    const run = spawnSync(process.execPath, [script], { cwd: f.cwd, env: f.env, encoding: "utf8" });
    assert.equal(run.status, 0, run.stderr);
    assert.equal(run.stdout, `${expected}\n`);
  }
});

test("SDK environment paths take precedence without silently falling back on invalid explicit configuration", t => {
  const f = fixture(t); f.install("37.0.0");
  const homeSdk = join(f.root, "custom SDK home"); const rootSdk = join(f.root, "custom SDK root");
  const homeTool = f.install("35.0.0", homeSdk); const rootTool = f.install("36.0.0", rootSdk);
  assert.equal(resolveAapt({ env: { ANDROID_HOME: homeSdk }, home: f.home }), homeTool);
  assert.equal(resolveAapt({ env: { ANDROID_HOME: homeSdk, ANDROID_SDK_ROOT: rootSdk }, home: f.home }), rootTool);
  assert.throws(() => resolveAapt({ env: { ANDROID_SDK_ROOT: "relative/sdk", ANDROID_HOME: homeSdk } }),
    /absolute-android-sdk-root-required/);
  assert.throws(() => resolveAapt({ env: { ANDROID_SDK_ROOT: join(f.root, "missing"), ANDROID_HOME: homeSdk } }),
    /android-build-tools-unavailable/);
});

test("highest executable stable build-tools version is chosen numerically, excluding previews and nonexecutables", t => {
  const f = fixture(t);
  f.install("9.0.0"); f.install("36.0.9"); f.install("37.0.0-rc1");
  const expected = f.install("36.1.0"); const newer = f.install("37.0.0", f.sdkRoot, 0o600);
  const options = { env: { ANDROID_SDK_ROOT: f.sdkRoot } };
  assert.equal(resolveAapt(options), expected);
  chmodSync(newer, 0o700);
  assert.equal(resolveAapt(options), newer);
});

test("Linux home fallback and unavailable tool cases have explicit failures", t => {
  const f = fixture(t); const sdk = join(f.home, "Android", "Sdk"); const expected = f.install("36.0.0", sdk);
  assert.equal(resolveAapt({ env: {}, platform: "linux", home: f.home }), expected);
  assert.throws(() => resolveAapt({ env: {}, platform: "unsupported", home: f.home }), /explicit-android-sdk-root-required/);
  f.install("37.0.0-rc1");
  assert.throws(() => resolveAapt({ env: {}, platform: "darwin", home: f.home }), /executable-stable-aapt-unavailable/);
  const run = spawnSync(process.execPath, [script, "unexpected"], { cwd: f.cwd, env: f.env, encoding: "utf8" });
  assert.equal(run.status, 2);
  assert.equal(run.stdout, "");
  assert.equal(run.stderr, "AAPT lookup failed: aapt-resolver-takes-no-arguments\n");
});

test("exact documented AAPT context is shell-safe and stops before subsequent work when no tool exists", t => {
  const f = fixture(t); const expected = f.install("37.0.0");
  const doc = readFileSync(new URL("./PHYSICAL_LOWBAR.md", import.meta.url), "utf8");
  const blocks = [...doc.matchAll(/```sh\n([\s\S]*?)```/g)].map(match => match[1]);
  const block = blocks.filter(value => value.startsWith("# AAPT-CONTEXT:"));
  assert.equal(block.length, 1);
  for (const shell of ["bash", "zsh"]) {
    const command = `${block[0]}\nprintf '%s\\n' "$AAPT"`;
    const env = { ...f.env, ROOT: workspace, ANDROID_SDK_ROOT: f.sdkRoot };
    const run = spawnSync(shell, ["-fc", command], { cwd: f.cwd, env, encoding: "utf8" });
    assert.equal(run.status, 0, run.stderr); assert.equal(run.stdout, `${expected}\n`);
    const rejected = spawnSync(shell, ["-fc", command], { cwd: f.cwd,
      env: { ...env, ANDROID_SDK_ROOT: join(f.root, "missing") }, encoding: "utf8" });
    assert.equal(rejected.status, 2); assert.equal(rejected.stdout, "");
    assert.equal(rejected.stderr, "AAPT lookup failed: android-build-tools-unavailable\n");
  }
});
