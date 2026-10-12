import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, realpathSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";
import { setTimeout as sleep } from "node:timers/promises";
import { collectNativeInputs, hashNativeInputFile, nativeWriterArguments, nativeWriterSourceHashes, parseArgs, parseJsonStream,
  prepareNativeConsumer, requireNativeBeforeWriter, requireNativeConsumerLock, requireNativeWriterReceipt, requireVerifiedNativeInputs,
  validateNativeManifest, verifyNativeInputs } from "./physical_native_provenance.mjs";

const SDK = "github.com/urnetwork/sdk";
const CONNECT = "github.com/urnetwork/connect";
const GLOG = "github.com/golang/glog";
const MOBILE = "golang.org/x/mobile";
const WARP = "github.com/urnetwork/warp";
const sha = value => createHash("sha256").update(value).digest("hex");
const write = (path, data) => { mkdirSync(dirname(path), { recursive: true, mode: 0o700 }); writeFileSync(path, data, { mode: 0o600 }); };
const json = (path, value) => write(path, `${JSON.stringify(value)}\n`);
const rehash = manifest => {
  const { root, buildOwner, buildId, profileRate, inputs } = manifest;
  manifest.inputHash = sha(JSON.stringify({ root, buildOwner, buildId, profileRate, inputs }));
  return manifest;
};

function fixture(t, prefix = "native-source-contract-") {
  const root = realpathSync(mkdtempSync(join(tmpdir(), prefix)));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const cwd = join(root, "sdk/build"); const privateDir = join(root, "private");
  mkdirSync(privateDir, { mode: 0o700 });
  const paths = { before: join(privateDir, "before.json"), after: join(privateDir, "after.json"), output: join(privateDir, "proof.json"),
    "writer-receipt": join(privateDir, "writer.json") };
  const main = { Path: `${SDK}/build`, Main: true, Dir: cwd, GoMod: join(cwd, "go.mod") };
  const local = (Path, name, target) => ({ Path, Version: "v0.0.1", Dir: join(root, name), GoMod: join(root, name, "go.mod"),
    Replace: { Path: target, Dir: join(root, name), GoMod: join(root, name, "go.mod") } });
  const modules = [main, local(SDK, "sdk", ".."), local(CONNECT, "connect", "../../connect"), local(GLOG, "glog", "../../glog"),
    { Path: MOBILE, Version: "v0.0.1", Dir: join(root, "mobile"), GoMod: join(root, "mobile/go.mod") }];
  for (const module of modules) {
    write(module.GoMod, `module ${module.Path}\n`);
    if (module.Path !== GLOG) write(join(module.Dir, "go.sum"), "fixture module checksum\n");
  }
  write(join(cwd, "Makefile"), "GOMOBILE_VERSION ?= v0.0.0-fixture\nCHECKSEC_VERSION ?= v3.1.0\n_build_android:\n export GOEXPERIMENT=greenteagc\n gomobile bind -target android/arm64,android/arm,android/amd64 -tags sdk_mobile_bind -androidapi 24\nbuild_ios:\n");
  for (const path of [join(cwd, "sdk-android-output-lock.sh"), join(cwd, "gomobile-bind-retry.sh"), join(root, "sdk/build-android.sh"),
    join(root, "android/app/app/build.gradle")]) write(path, "build contract fixture\n");
  const tools = join(root, "tools");
  for (const name of ["go", "gomobile", "gobind", "checksec", "compile", "link", "asm", "cgo"]) write(join(tools, name), `tool ${name}`);
  write(join(cwd, "main.go"), "package main\n");
  write(join(cwd, "cmd/mobileexports/main.go"), "package main\n");
  for (const [directory, name] of [["sdk", "sdk.go"], ["connect", "connect.go"], ["glog", "glog.go"], ["std", "std.go"]]) {
    write(join(root, directory, name), "package fixture\n");
  }
  write(join(root, "connect/res/policy.bin"), "fixture embedded bytes");
  write(join(root, "sdk/callback.h"), "/* fixture native header */");
  for (const name of ["bind/seq.go.support", "bind/java/Seq.java", "bind/java/seq_android.c.support", "bind/java/seq_android.go.support",
    "bind/java/seq_android.h", "bind/seq/ref.go", "bind/java/java.go"]) write(join(root, "mobile", name), "fixture bridge input\n");
  for (const abi of ["arm64", "arm", "amd64"]) write(join(root, `sdk/native_${abi}.go`), "package fixture\n");
  write(join(cwd, "android/.build-owner"), "fixture-owner\n");
  const hostMain = { Path: WARP, Main: true, Dir: join(root, "warp"), GoMod: join(root, "warp/go.mod") };
  const hostDependency = { Path: GLOG, Version: "v9.0.0", Dir: join(root, "warp-dep"), GoMod: join(root, "warp-dep/go.mod") };
  for (const module of [hostMain, hostDependency]) write(module.GoMod, "module " + module.Path + "\n");
  write(join(root, "warp/go.sum"), "fixture Warp checksum\n");
  write(join(root, "warp/warpctl/main.go"), "package main\n");
  write(join(root, "warp/warpctl/resource.txt"), "selected embedded Warp resource\n");
  write(join(root, "warp-dep/log.go"), "package glog\n");
  for (const hostOS of ["darwin", "linux"]) for (const hostArch of ["amd64", "arm64"]) {
    const path = join(root, "warp/warpctl/build", hostOS, hostArch, "warpctl");
    write(path, "fixture arm-local host executable"); chmodSync(path, 0o700);
  }
  const androidSdk = join(root, "android-sdk"); const ndk = join(androidSdk, "ndk/29.0.14206865");
  write(join(root, "android/app/app/build.gradle"), "android {\n ndkVersion = '29.0.14206865'\n}\n");
  write(join(ndk, "source.properties"), "Pkg.Revision = 29.0.14206865\n");
  const objcopy = join(ndk, "toolchains/llvm/prebuilt/fixture/bin/llvm-objcopy");
  write(objcopy, "fixture objcopy and strip"); chmodSync(objcopy, 0o700);
  symlinkSync("llvm-objcopy", join(dirname(objcopy), "llvm-strip"));
  const versionHome = join(root, "version-context");
  json(join(versionHome, "version.json"), { stagedVersion: "fixture-version", stagedVersionCode: 12340 });
  const f = { hostOS: "darwin", hostArch: "arm64", hostMain, hostDependency, ndk, androidSdk, versionHome,
    root, cwd, privateDir, paths, modules, commands: [], now: 1000, workspace: "", dirty: true,
    options: { root, phase: "before", "build-owner": "fixture-owner", "build-id": "fixture-build", "profile-rate": "65536" } };
  f.dependencies = { now: () => f.now++, env: { PATH: tools, HOME: root, ANDROID_HOME: androidSdk, ANDROID_SDK_ROOT: androidSdk,
      ANDROID_NDK_HOME: ndk, ANDROID_NDK_ROOT: ndk, WARP_VERSION_HOME: versionHome }, toolPath: name => join(tools, name),
    invoke(command, args, settings) {
      f.commands.push({ command, args, cwd: settings.cwd, env: settings.env });
      const result = value => ({ status: 0, stdout: typeof value === "string" ? value : JSON.stringify(value), stderr: "" });
      if (command === join(root, "std/bin/go") && args[0] === "version") {
        assert.deepEqual(args.slice(0, 3), ["version", "-m", "-json"]);
        const name = ["gomobile", "gobind", "checksec"].find(name => args[3] === join(tools, name));
        assert.ok(name, "metadata must describe the exact resolved executable");
        const module = name === "checksec" ? "github.com/slimm609/checksec/v3" : MOBILE;
        return result(f.mobileInfo?.(name) ?? { GoVersion: "go1.fixture",
          Path: name === "checksec" ? module : module + "/cmd/" + name,
          Main: { Path: module, Version: name === "checksec" ? "v3.1.0" : "v0.0.0-fixture", Sum: "h1:fixture" } });
      }
      if (command === "git") {
        const repo = settings.cwd.startsWith(join(root, "sdk")) ? join(root, "sdk") : settings.cwd;
        if (args[1] === "--show-toplevel") return result(repo);
        if (args[1] === "HEAD") return result("1".repeat(40));
        if (args[0] === "status") return result(f.dirty ? " M selected.go\0" : "");
        if (args[0] === "diff") return result(f.dirty ? "fixture selected diff" : "");
        assert.fail("unexpected git operation");
      }
      if (command === join(root, "std/bin/go")) {
        assert.equal(settings.cwd, join(root, "warp/warpctl"));
        command = "go";
      }
      assert.equal(command, "go");
      assert.equal(settings.env.GOPROXY, "off"); assert.equal(settings.env.GOSUMDB, "off");
      assert.equal(settings.env.GOEXPERIMENT, "greenteagc");
      if (settings.cwd === join(root, "warp/warpctl")) {
        assert.equal(settings.env.GOWORK, "off");
        assert.equal(settings.env.GOOS, f.hostOS); assert.equal(settings.env.GOARCH, f.hostArch);
        if (args[0] === "env") return result({ GOMOD: hostMain.GoMod, GOWORK: "off", GOFLAGS: "",
          GOVERSION: "go1.fixture", GOROOT: join(root, "std"), GOTOOLDIR: tools, GOEXPERIMENT: "greenteagc",
          GOOS: f.hostOS, GOARCH: f.hostArch, CGO_ENABLED: "1", CC: "clang", CXX: "clang++",
          CGO_CFLAGS: "-O2 -g", CGO_CPPFLAGS: "", CGO_CXXFLAGS: "-O2 -g", CGO_LDFLAGS: "-O2 -g" });
        if (args[0] === "mod") return result({ Module: { Path: WARP }, Replace: null });
        assert.equal(args[0], "list"); assert.equal(args[1], "-mod=readonly");
        if (args.includes("-m")) return result([hostMain, hostDependency].map(row => JSON.stringify(row)).join("\n"));
        assert.ok(args.includes("-deps")); assert.equal(args.at(-1), ".");
        return result([
          { ImportPath: "runtime", Standard: true, Dir: join(root, "std"), GoFiles: ["std.go"] },
          { ImportPath: WARP + "/warpctl", Dir: settings.cwd, Module: hostMain, GoFiles: ["main.go"], EmbedFiles: ["resource.txt"] },
          { ImportPath: GLOG, Dir: hostDependency.Dir, Module: hostDependency, GoFiles: ["log.go"] },
        ].map(row => JSON.stringify(row)).join("\n"));
      }
      assert.equal(settings.cwd, cwd);
      if (args[0] === "env") return result({ GOMOD: join(cwd, "go.mod"), GOWORK: f.workspace, GOFLAGS: "",
        GOVERSION: "go1.fixture", GOROOT: join(root, "std"), GOTOOLDIR: tools, GOEXPERIMENT: "greenteagc", GOHOSTOS: f.hostOS, GOHOSTARCH: f.hostArch });
      if (args[0] === "work") return result({ Go: "1.fixture", Use: [{ DiskPath: "sdk/build" }], Replace: [] });
      if (args[0] === "mod") return result({ Module: { Path: `${SDK}/build` }, Replace: modules.filter(module => module.Replace).map(module =>
        ({ Old: { Path: module.Path }, New: { Path: module.Replace.Path } })) });
      assert.equal(args[0], "list"); assert.equal(args[1], "-mod=readonly");
      if (args.includes("-m")) return args.includes("all") ? result(modules.map(module => JSON.stringify(module)).join("\n")) : result(main);
      const encode = rows => result(rows.map(row => JSON.stringify(row)).join("\n"));
      if (!args.includes("-deps")) return encode([
        { ImportPath: `${SDK}/build`, Dir: cwd, Module: main, GoFiles: ["main.go"] },
        { ImportPath: `${SDK}/build/cmd/mobileexports`, Dir: join(cwd, "cmd/mobileexports"), Module: main, GoFiles: ["main.go"] },
      ]);
      assert.equal(settings.env.GOOS, "android"); assert.equal(settings.env.CGO_ENABLED, "1");
      assert.ok(args.includes("-tags=sdk_mobile_bind"));
      return encode([
        { ImportPath: "runtime", Standard: true, Dir: join(root, "std"), GoFiles: ["std.go"] },
        { ImportPath: SDK, Dir: modules[1].Dir, Module: modules[1], GoFiles: ["sdk.go", `native_${settings.env.GOARCH}.go`], HFiles: ["callback.h"] },
        { ImportPath: CONNECT, Dir: modules[2].Dir, Module: modules[2], GoFiles: ["connect.go"], EmbedFiles: ["res/policy.bin"] },
        { ImportPath: GLOG, Dir: modules[3].Dir, Module: modules[3], GoFiles: ["glog.go"] },
        { ImportPath: `${MOBILE}/bind/java`, Dir: join(root, "mobile/bind/java"), Module: modules[4], GoFiles: ["java.go"] },
        { ImportPath: `${MOBILE}/bind/seq`, Dir: join(root, "mobile/bind/seq"), Module: modules[4], GoFiles: ["ref.go"] },
      ]);
    },
  };
  f.capture = phase => collectNativeInputs({ ...f.options, phase }, f.dependencies);
  f.pair = () => { json(paths.before, f.capture("before")); json(paths.after, f.capture("after")); };
  f.writerReceipt = (changes = {}) => {
    const before = JSON.parse(readFileSync(paths.before));
    const stdout = join(privateDir, "writer.stdout"); const stderr = join(privateDir, "writer.stderr");
    write(stdout, "fixture writer output\n"); write(stderr, "");
    const receipt = { type: "physical-native-writer", schemaVersion: 1, state: "terminal", eligible: true,
      childStarted: true, childExitCode: 0, signal: null, interrupted: false,
      root, buildId: before.buildId, buildOwner: before.buildOwner, profileRate: before.profileRate, inputHash: before.inputHash,
      before: paths.before, beforeSha256: hashNativeInputFile(paths.before).sha256,
      memoryProfile: "ios-memory-audit-v1", maxWorkers: 1, workingDirectory: join(root, "android/app"),
      arguments: nativeWriterArguments(before.buildId, before.profileRate, "ios-memory-audit-v1", 1),
      sourceHashes: nativeWriterSourceHashes(), startedAtUnixMs: before.capturedAtUnixMs + 1, completedAtUnixMs: before.capturedAtUnixMs + 2,
      stdout: hashNativeInputFile(stdout), stderr: hashNativeInputFile(stderr), ...changes };
    json(paths["writer-receipt"], receipt); return receipt;
  };
  return f;
}

test("native CLI requires complete explicit before/after/build context; metadata is bounded complete JSON", () => {
  for (const args of [[], ["capture"], ["capture", "--root", ""], ["check", "--proof", "proof"],
    ["verify", "--before", "b", "--after", "a", "--output", "o", "--unknown", "x"]]) assert.throws(() => parseArgs(args));
  assert.equal(parseArgs(["check", "--proof", "p", "--build-id", "fixture"]).mode, "check");
  assert.deepEqual(parseJsonStream('{"brace":"}\\\"{"}\n {"n":1}'), [{ brace: '}"{' }, { n: 1 }]);
  for (const raw of ["", "{} tail", "{", "[]", "not json"]) assert.throws(() => parseJsonStream(raw));
});

test("native writer allowlists Android explicitly without permitting numeric or aliased profile overrides", () => {
  for (const profile of ["android", "ios-memory-audit-v1", "ios-memory-audit-v2"]) {
    assert.deepEqual(nativeWriterArguments("fixture-build", 0, profile, 1), [":app:buildSdkAcceptance", "--max-workers", "1",
      "-PurnetworkAcceptanceBuildId=fixture-build", `-PurnetworkMemoryProfile=${profile}`, "-PurnetworkMemoryProfileRateBytes=0"]);
  }
  for (const profile of [undefined, null, "", "normalAndroid", "67108864", ["android"], "android -PurnetworkMemoryLimitBytes=1"]) {
    assert.throws(() => nativeWriterArguments("fixture-build", 0, profile, 1), /explicit-native-writer-profile-required/);
  }
});

test("Android writer receipt binds its exact argument vector, build owner and attested sources", t => {
  const f = fixture(t);
  f.options["profile-rate"] = "0";
  json(f.paths.before, f.capture("before"));
  const before = JSON.parse(readFileSync(f.paths.before));
  const receipt = f.writerReceipt({ memoryProfile: "android",
    arguments: nativeWriterArguments(before.buildId, before.profileRate, "android", 1) });
  assert.equal(requireNativeWriterReceipt(f.paths, before).memoryProfile, "android");
  for (const changes of [
    { memoryProfile: "ios-memory-audit-v2" },
    { arguments: nativeWriterArguments(before.buildId, before.profileRate, "ios-memory-audit-v2", 1) },
    { arguments: [...receipt.arguments, "-PurnetworkMemoryLimitBytes=67108864"] },
    { sourceHashes: receipt.sourceHashes.map(row => ({ ...row, sha256: "0".repeat(64) })) },
    { buildOwner: "foreign-owner" },
    { buildId: "foreign-build" },
    { memoryProfile: "normalAndroid" },
  ]) {
    json(f.paths["writer-receipt"], { ...receipt, ...changes });
    assert.throws(() => requireNativeWriterReceipt(f.paths, before), /native-writer-(?:receipt-context-mismatch|receipt-required)/);
  }
});

test("native closure records all ABI Go/embed/native inputs and separate module/replacement/dirty hashes", t => {
  const f = fixture(t); const manifest = f.capture("before");
  assert.equal(validateNativeManifest(manifest), true);
  assert.equal(manifest.inputs.workingDirectory, f.cwd);
  assert.deepEqual(manifest.inputs.targets, ["arm64", "arm", "amd64"]);
  assert.equal(manifest.inputs.resolvedModuleGraph.length, 3);
  assert.ok(manifest.inputs.codeInputs.some(file => file.kind === "embed" && file.module === CONNECT));
  assert.ok(manifest.inputs.codeInputs.some(file => file.kind === "native" && file.module === SDK));
  assert.equal(manifest.inputs.moduleInputs.filter(file => file.kind === "go.mod").length, 5);
  assert.equal(manifest.inputs.bridgeInputs.length, 5, "generated binding support is independent of SDK GoFiles");
  assert.equal(manifest.inputs.moduleInputs.find(file => file.module === GLOG && file.kind === "go.sum").present, false);
  assert.equal(manifest.inputs.modules.find(module => module.path === SDK).replacement.path, "..");
  assert.equal(manifest.inputs.modules.find(module => module.path === `${SDK}/build`).main, true);
  assert.equal(manifest.inputs.repositories.length, 4);
  assert.ok(manifest.inputs.repositories.every(repo => repo.dirty && /^[a-f0-9]{64}$/.test(repo.dirtyInputHash)));
  assert.equal(manifest.inputs.workspace.mode, "none");
  assert.equal(JSON.stringify(manifest).includes("fixture selected diff"), false, "only hashes, never diff content");
  assert.equal(f.capture("after").inputHash, manifest.inputHash, "time and phase are not inputs");
});

test("MjXnWF: recomputing summary SHA cannot hide omitted Go/module/replacement/tool fields", t => {
  const f = fixture(t); const original = f.capture("before");
  const corruptions = [
    m => delete m.inputs.codeInputs, m => delete m.inputs.moduleInputs, m => delete m.inputs.workspace,
    m => delete m.inputs.resolvedModuleGraph, m => { m.inputs.resolvedModuleGraph[0].modules.pop(); },
    m => delete m.inputs.mainResolution.Replace, m => delete m.inputs.repositories[0].dirtyInputHash,
    m => delete m.inputs.modules[0].replacement, m => delete m.inputs.goEnvironment.GOWORK,
    m => { m.inputs.tools.pop(); }, m => { m.inputs.buildInputs.pop(); }, m => { m.inputs.bridgeInputs.pop(); },
    m => { m.inputs.buildInputs = m.inputs.buildInputs.filter(file => !file.path.endsWith("/physical_artifact_directory.mjs")); },
    m => { m.inputs.repositories = m.inputs.repositories.slice(1); },
    m => { m.inputs.moduleInputs = m.inputs.moduleInputs.filter(file => file.kind !== "go.sum"); },
    m => { m.inputs.moduleInputs = m.inputs.moduleInputs.filter(file => file.module !== CONNECT); },
    m => { m.inputs.codeInputs = m.inputs.codeInputs.filter(file => file.kind !== "embed"); },
    m => { m.inputs.packageSelections.pop(); }, m => { m.inputs.buildPackages.pop(); },
  ];
  for (const mutate of corruptions) {
    const manifest = structuredClone(original); mutate(manifest); rehash(manifest);
    assert.throws(() => validateNativeManifest(manifest), undefined, String(mutate));
  }
});

test("active/off workspace and explicit absent go.work.sum are recorded; resolution drift changes fingerprint", t => {
  const f = fixture(t); const none = f.capture("before").inputHash;
  f.workspace = "off"; assert.notEqual(f.capture("after").inputHash, none);
  f.workspace = join(f.root, "go.work"); write(f.workspace, "go 1.fixture\nuse ./sdk/build\n");
  const active = f.capture("before"); assert.equal(active.inputs.workspace.sum.present, false);
  write(`${f.workspace}.sum`, "fixture sum\n"); assert.notEqual(f.capture("after").inputHash, active.inputHash);
  const broken = structuredClone(active); delete broken.inputs.workspace.sum; rehash(broken);
  assert.throws(() => validateNativeManifest(broken), /workspace-inputs-missing/);
});

test("each native source/module/local replacement/embed/host build input drift rejects the before-after pair", t => {
  for (const name of ["sdk/sdk.go", "connect/connect.go", "glog/glog.go", "sdk/build/go.mod", "sdk/build/go.sum", "sdk/go.sum",
    "connect/go.mod", "connect/res/policy.bin", "sdk/build/cmd/mobileexports/main.go", "sdk/native_arm.go",
    "mobile/bind/seq.go.support", "mobile/bind/seq/ref.go"]) {
    const f = fixture(t); json(f.paths.before, f.capture("before"));
    const path = join(f.root, name); write(path, `${readFileSync(path, "utf8")}changed\n`);
    json(f.paths.after, f.capture("after"));
    assert.throws(() => verifyNativeInputs(f.paths, f.dependencies), /native-inputs-changed-across-build/, name);
    assert.equal(existsSync(f.paths.output), false);
  }
});

test("unselected tests and generated binary output do not masquerade as native source drift", t => {
  const f = fixture(t); const before = f.capture("before");
  write(join(f.root, "connect/new_test.go"), "package fixture\n");
  write(join(f.cwd, "android/URnetworkSdk.aar"), "fake binary, separately attested");
  assert.equal(f.capture("after").inputHash, before.inputHash);
});

test("verification binds private manifests, source state, build ID and published native owner; rechecks before launch", t => {
  const f = fixture(t); f.pair();
  const result = verifyNativeInputs(f.paths, f.dependencies);
  assert.equal(result.eligible, true); assert.equal(statSync(f.paths.output).mode & 0o777, 0o600);
  assert.deepEqual(requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies), {
    buildId: "fixture-build", buildOwner: "fixture-owner", inputHash: result.inputHash,
  });
  assert.throws(() => requireVerifiedNativeInputs(f.paths.output, "different-build", f.dependencies), /verified-native-input-proof-required/);
  write(join(f.cwd, "android/.build-owner"), "different-owner\n");
  assert.throws(() => requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies), /native-build-owner-mismatch/);
  write(join(f.cwd, "android/.build-owner"), "fixture-owner\n");
  write(join(f.root, "connect/connect.go"), "package changed\n");
  assert.throws(() => requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies), /native-inputs-changed-after-build/);
});

test("public/tampered manifests, missing module input, failed metadata and unsupported flags fail closed without raw diagnostics", t => {
  const f = fixture(t); f.pair(); chmodSync(f.paths.before, 0o644);
  assert.throws(() => verifyNativeInputs(f.paths, f.dependencies), /private-native-manifest-required/);
  chmodSync(f.paths.before, 0o600); verifyNativeInputs(f.paths, f.dependencies);
  const after = JSON.parse(readFileSync(f.paths.after)); after.capturedAtUnixMs++; json(f.paths.after, after);
  assert.throws(() => requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies), /native-input-proof-replaced/);
  assert.throws(() => collectNativeInputs(f.options, { ...f.dependencies, env: { GOFLAGS: "-overlay=anything" } }), /unsupported-native-goflags/);
  assert.throws(() => collectNativeInputs(f.options, { ...f.dependencies, invoke: () => ({ status: 2, stdout: "", stderr: "private-value" }) }),
    /^Error: native-metadata-command-failed$/);
  rmSync(join(f.root, "connect/go.mod")); assert.throws(() => f.capture("before"));
});

test("real CLI rejects an incomplete private proof before any tool/device access and prints no private input", t => {
  const f = fixture(t); json(f.paths.output, { privateValue: "do-not-output", eligible: true });
  const result = spawnSync(process.execPath, [new URL("./physical_native_provenance.mjs", import.meta.url).pathname,
    "check", "--proof", f.paths.output, "--build-id", "fixture-build"], { encoding: "utf8", timeout: 3000 });
  assert.equal(result.status, 2); assert.equal(result.stdout, "");
  assert.equal(result.stderr, "native input provenance failed: verified-native-input-proof-required\n");
});

test("CXAusf: consumer preparation always captures after then verifies proof inside the held output lock", t => {
  const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
  const events = [];
  const result = prepareNativeConsumer({ ...f.paths, root: f.root }, { ...f.dependencies, consumerLock: root => {
    assert.equal(root, f.root);
    events.push({ after: existsSync(f.paths.after), proof: existsSync(f.paths.output) });
  } });
  assert.deepEqual(events, [{ after: false, proof: false }, { after: false, proof: false }, { after: true, proof: false }]);
  assert.equal(result.eligible, true); assert.equal(result.buildId, "fixture-build");
  const after = JSON.parse(readFileSync(f.paths.after)); const proof = JSON.parse(readFileSync(f.paths.output));
  assert.equal(after.phase, "after"); assert.equal(proof.afterSha256, sha(readFileSync(f.paths.after)));
  assert.equal(requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies).inputHash, result.inputHash);
  assert.equal(statSync(f.paths.after).mode & 0o777, 0o600); assert.equal(statSync(f.paths.output).mode & 0o777, 0o600);
});

test("consumer preparation rejects missing before, capture failure, source drift, stale owner and lost lock without a proof", t => {
  for (const kind of ["before", "capture", "source", "owner", "lock-start", "lock-before-after", "lock-before-proof"]) {
    const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
    if (kind === "before") rmSync(f.paths.before);
    if (kind === "source") write(join(f.root, "connect/connect.go"), "package changed\n");
    if (kind === "owner") write(join(f.cwd, "android/.build-owner"), "different-owner\n");
    let checks = 0;
    const dependencies = { ...f.dependencies, consumerLock: () => {
      checks++;
      if (checks === ({ "lock-start": 1, "lock-before-after": 2, "lock-before-proof": 3 })[kind]) throw Error("fixture-lock-lost");
    } };
    if (kind === "capture") dependencies.invoke = () => ({ status: 2, stdout: "", stderr: "private-input-must-not-leak" });
    assert.throws(() => prepareNativeConsumer({ ...f.paths, root: f.root }, dependencies), undefined, kind);
    assert.equal(existsSync(f.paths.output), false, kind);
    if (["before", "capture", "lock-start", "lock-before-after"].includes(kind)) assert.equal(existsSync(f.paths.after), false, kind);
  }
});

test("consumer preparation never adopts existing after/proof files or another root/artifact directory", t => {
  for (const kind of ["after", "output", "same", "root", "directory"]) {
    const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
    const options = { ...f.paths, root: f.root };
    if (["after", "output"].includes(kind)) write(f.paths[kind], "prior evidence\n");
    if (kind === "same") options.after = options.before;
    if (kind === "root") options.root = f.cwd;
    if (kind === "directory") options.after = join(f.root, "elsewhere.json");
    assert.throws(() => prepareNativeConsumer(options, { ...f.dependencies,
      consumerLock: () => assert.fail("invalid inputs reject before owner inspection") }));
    if (["after", "output"].includes(kind)) assert.equal(readFileSync(f.paths[kind], "utf8"), "prior evidence\n");
  }
});

test("native consumer cannot use inherited environment markers as a kernel-lock proof", t => {
  const f = fixture(t);
  for (const env of [{}, { URNETWORK_ANDROID_SDK_OUTPUT_LOCK_HELD: "1" }, {
    URNETWORK_ANDROID_SDK_OUTPUT_LOCK_HELD: "1", URNETWORK_ANDROID_SDK_OUTPUT_LOCK_ROLE: "physical-native-consumer",
    URNETWORK_ANDROID_SDK_OUTPUT_LOCK_FD: "10", URNETWORK_ANDROID_SDK_OUTPUT_LOCK_DIR: f.cwd,
    URNETWORK_ANDROID_SDK_OUTPUT_LOCK_PATH: join(f.cwd, ".android-output.lock"),
  }]) assert.throws(() => requireNativeConsumerLock(f.root, { lockEnv: env,
    lockInvoke: () => assert.fail("a missing/wrong descriptor must not query the lock") }), /^Error: native-consumer-lock-required$/);
});

test("real prepare-consumer CLI refuses a source-valid before manifest outside the kernel consumer lock", t => {
  const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (key.startsWith("URNETWORK_ANDROID_SDK_OUTPUT_LOCK_")) delete env[key];
  const result = spawnSync(process.execPath, [new URL("./physical_native_provenance.mjs", import.meta.url).pathname,
    "prepare-consumer", "--root", f.root, "--before", f.paths.before, "--after", f.paths.after, "--output", f.paths.output,
    "--writer-receipt", f.paths["writer-receipt"]],
  { env, encoding: "utf8", timeout: 3000 });
  assert.equal(result.status, 2); assert.equal(result.stdout, "");
  assert.equal(result.stderr, "native input provenance failed: native-consumer-lock-required-no-consumer-spawn\n");
  assert.equal(existsSync(f.paths.after), false); assert.equal(existsSync(f.paths.output), false);
});

function prepareCommandFixture(f) {
  write(join(f.cwd, "sdk-android-output-lock.sh"), readFileSync(new URL("../../../sdk/build/sdk-android-output-lock.sh", import.meta.url), "utf8"));
  const table = new Map();
  const invoke = f.dependencies.invoke;
  f.dependencies.invoke = (command, args, settings) => {
    const result = invoke(command, args, settings);
    table.set(JSON.stringify([command, args, settings.cwd, command === "go" && args.includes("-deps") ||
      command === "go" && args.includes("all") ? settings.env.GOARCH : null]), result.stdout);
    return result;
  };
  const fakeCommand = join(f.root, "metadata-command.mjs"); const tablePath = join(f.root, "metadata.json");
  write(fakeCommand, `import fs from 'node:fs';const [command,...args]=process.argv.slice(2);\n` +
    `const key=JSON.stringify([command,args,process.cwd(),command==='go'&&(args.includes('-deps')||args.includes('all'))?process.env.GOARCH:null]);\n` +
    `const table=JSON.parse(fs.readFileSync(${JSON.stringify(tablePath)},'utf8'));if(!(key in table))process.exit(3);process.stdout.write(table[key]);\n`);
  for (const command of ["go", "git"]) {
    write(join(f.root, "tools", command), `#!/bin/sh\nexec ${JSON.stringify(process.execPath)} ${JSON.stringify(fakeCommand)} ${command} "$@"\n`);
    chmodSync(join(f.root, "tools", command), 0o700);
  }
  const pinnedGo = join(f.root, "std/bin/go");
  write(pinnedGo, `#!/bin/sh\nexec ${JSON.stringify(process.execPath)} ${JSON.stringify(fakeCommand)} ${JSON.stringify(pinnedGo)} "$@"\n`);
  chmodSync(pinnedGo, 0o700);
  for (const command of ["gomobile", "gobind", "checksec"]) chmodSync(join(f.root, "tools", command), 0o700);
  json(f.paths.before, f.capture("before")); json(tablePath, Object.fromEntries(table));
  const env = { ...process.env, ...f.dependencies.env, PATH: `${join(f.root, "tools")}:${process.env.PATH}` };
  for (const key of Object.keys(env)) if (key.startsWith("URNETWORK_ANDROID_SDK_OUTPUT_LOCK_")) delete env[key];
  return env;
}

test("current wrapper and real preparation helper complete the full barrier under a real fixture lock before the consumer", t => {
  const f = fixture(t); const env = prepareCommandFixture(f); f.writerReceipt();
  const marker = join(f.privateDir, "consumer-started");
  const consumer = `const fs=require('node:fs');for(const file of process.argv.slice(1,3)){if(!fs.existsSync(file))process.exit(4)}fs.writeFileSync(process.argv[3],'assembled-after-proof',{mode:0o600});`;
  const result = spawnSync("bash", [new URL("./physical_native_consumer.sh", import.meta.url).pathname,
    "--root", f.root, "--before", f.paths.before, "--after", f.paths.after, "--proof", f.paths.output,
    "--writer-receipt", f.paths["writer-receipt"],
    "--", process.execPath, "-e", consumer, f.paths.after, f.paths.output, marker],
  { cwd: f.root, env, encoding: "utf8", timeout: 20000 });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(readFileSync(marker, "utf8"), "assembled-after-proof");
  assert.equal(requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies).buildOwner, "fixture-owner");
  assert.deepEqual(JSON.parse(result.stdout), { eligible: true, classification: "NATIVE_INPUTS_VERIFIED" });
});

function writerFixture(t, prefix) {
  const f = fixture(t, prefix); const env = prepareCommandFixture(f);
  const gradle = join(f.root, "android/app/gradlew");
  const fake = join(f.root, "fake-gradle.mjs");
  write(fake, `if(process.argv[2]!==':app:buildSdkAcceptance'||process.env.URNETWORK_ANDROID_SDK_BUILD_OWNER!=='fixture-owner'||process.env.GOMAXPROCS!=='1')process.exit(44);\n` +
    `process.stdout.write(process.argv.slice(2).join('\\n')+'\\n');process.stderr.write('fixture stderr\\n');\n` +
    `if(process.env.WRITER_FIXTURE==='nonzero')process.exit(7);if(process.env.WRITER_FIXTURE==='held')setInterval(()=>{},100);\n`);
  write(gradle, `#!/bin/bash\nexec ${JSON.stringify(process.execPath)} ${JSON.stringify(fake)} "$@"\n`); chmodSync(gradle, 0o700);
  const args = [new URL("./physical_native_writer.sh", import.meta.url).pathname, "--root", f.root, "--before", f.paths.before,
    "--build-id", "fixture-build", "--profile-rate", "65536", "--memory-profile", "ios-memory-audit-v1", "--max-workers", "1",
    "--receipt", f.paths["writer-receipt"], "--stdout", join(f.privateDir, "writer.stdout"), "--stderr", join(f.privateDir, "writer.stderr")];
  return { ...f, env, args, gradle,
    run(kind = "success") { return spawnSync("zsh", ["-fc", 'exec bash "$@"', "writer-boundary", ...args],
      { cwd: f.root, env: { ...env, WRITER_FIXTURE: kind }, encoding: "utf8", timeout: 15000 }); } };
}

test("terra_proof_arm: zsh status is read-only, but Bash-owned writer atomically retains a joined success receipt", t => {
  const old = spawnSync("zsh", ["-fc", "status=0"], { encoding: "utf8", timeout: 3000 });
  assert.notEqual(old.status, 0); assert.match(old.stderr, /read-only variable: status/);
  const f = writerFixture(t); const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), { eligible: true, classification: "NATIVE_WRITER_COMPLETE" });
  const receipt = requireNativeWriterReceipt(f.paths, JSON.parse(readFileSync(f.paths.before)));
  assert.equal(receipt.childStarted, true); assert.equal(receipt.childExitCode, 0); assert.equal(receipt.interrupted, false);
  assert.deepEqual(readFileSync(receipt.stdout.path, "utf8").trim().split("\n"), receipt.arguments);
  assert.equal(statSync(f.paths["writer-receipt"]).mode & 0o777, 0o600);
  assert.equal(readdirSync(f.privateDir).some(name => name.includes(".pending-")), false);
});

test("Bash-owned Android writer retains the selected profile through actual child arguments and receipt verification", t => {
  const f = writerFixture(t);
  f.args[f.args.indexOf("--memory-profile") + 1] = "android";
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  const receipt = requireNativeWriterReceipt(f.paths, JSON.parse(readFileSync(f.paths.before)));
  assert.equal(receipt.memoryProfile, "android");
  assert.ok(receipt.arguments.includes("-PurnetworkMemoryProfile=android"));
  assert.deepEqual(readFileSync(receipt.stdout.path, "utf8").trim().split("\n"), receipt.arguments);
  assert.equal(receipt.sourceHashes.some(row => row.name === "physical_native_provenance.mjs"), true);
});

test("writer nonzero and spawn failure publish failed terminal receipts that cannot authorize consumption", t => {
  for (const kind of ["nonzero", "spawn"]) {
    const f = writerFixture(t); if (kind === "spawn") rmSync(f.gradle);
    const result = f.run(kind); assert.equal(result.status, 2, result.stderr);
    const receipt = JSON.parse(readFileSync(f.paths["writer-receipt"]));
    assert.equal(receipt.eligible, false); assert.equal(receipt.state, "terminal");
    assert.equal(receipt.childExitCode, kind === "nonzero" ? 7 : null);
    assert.equal(receipt.childStarted, kind !== "spawn");
    assert.throws(() => prepareNativeConsumer({ ...f.paths, root: f.root }, { ...f.dependencies,
      consumerLock: () => assert.fail("failed writer must not reach consumption") }), /successful-native-writer-receipt-required/);
  }
});

test("writer interruption cannot publish success or expose a terminal receipt before the child has joined", async t => {
  const f = writerFixture(t);
  const child = spawn("zsh", ["-fc", 'exec bash "$@"', "writer-boundary", ...f.args],
    { cwd: f.root, env: { ...f.env, WRITER_FIXTURE: "held" }, stdio: ["ignore", "pipe", "pipe"] });
  const closed = new Promise(resolve => child.once("close", resolve));
  t.after(async () => { if (child.exitCode === null) child.kill("SIGTERM"); await closed; });
  const output = join(f.privateDir, "writer.stdout");
  for (let i = 0; i < 400 && (!existsSync(output) || statSync(output).size === 0); i++) await sleep(10);
  assert.ok(existsSync(output) && statSync(output).size > 0);
  assert.equal(existsSync(f.paths["writer-receipt"]), false);
  child.kill("SIGTERM"); assert.equal(await closed, 2);
  const receipt = JSON.parse(readFileSync(f.paths["writer-receipt"]));
  assert.equal(receipt.interrupted, true); assert.equal(receipt.signal, "SIGTERM"); assert.equal(receipt.eligible, false);
  assert.equal(readdirSync(f.privateDir).some(name => name.includes(".pending-")), false);
});

test("actual consumer entry point refuses missing, nonzero and forged receipts before any assembly marker", t => {
  const f = writerFixture(t); const result = f.run(); assert.equal(result.status, 0, result.stderr);
  const receipt = JSON.parse(readFileSync(f.paths["writer-receipt"])); const marker = join(f.privateDir, "assembly-marker");
  const args = [new URL("./physical_native_consumer.sh", import.meta.url).pathname, "--root", f.root,
    "--before", f.paths.before, "--after", f.paths.after, "--proof", f.paths.output, "--writer-receipt", f.paths["writer-receipt"],
    "--", process.execPath, "-e", "require('node:fs').writeFileSync(process.argv[1],'consumer')", marker];
  for (const kind of ["missing", "nonzero", "forged", "arguments", "source", "artifact-helper", "before"]) {
    json(f.paths["writer-receipt"], receipt);
    if (kind === "missing") rmSync(f.paths["writer-receipt"]);
    else if (kind === "nonzero") json(f.paths["writer-receipt"], { ...receipt, childExitCode: 7 });
    else if (kind === "forged") json(f.paths["writer-receipt"], { eligible: true, childExitCode: 0 });
    else if (kind === "arguments") json(f.paths["writer-receipt"], { ...receipt, arguments: [":app:assembleGithubDebug"] });
    else if (kind === "source") json(f.paths["writer-receipt"], { ...receipt, sourceHashes: [] });
    else if (kind === "artifact-helper") json(f.paths["writer-receipt"], { ...receipt,
      sourceHashes: receipt.sourceHashes.filter(source => source.name !== "physical_artifact_directory.mjs") });
    else json(f.paths["writer-receipt"], { ...receipt, beforeSha256: "0".repeat(64) });
    const rejected = spawnSync("bash", args, { cwd: f.root, env: f.env, encoding: "utf8", timeout: 10000 });
    assert.equal(rejected.status, 2, kind); assert.equal(existsSync(marker), false, kind);
    assert.equal(existsSync(f.paths.after), false, kind); assert.equal(existsSync(f.paths.output), false, kind);
  }
  json(f.paths["writer-receipt"], receipt);
  const accepted = spawnSync("bash", args, { cwd: f.root, env: f.env, encoding: "utf8", timeout: 10000 });
  assert.equal(accepted.status, 0, accepted.stderr); assert.equal(readFileSync(marker, "utf8"), "consumer");
});

test("writer rejects missing explicit profile, stale before inputs and existing receipt before fake Gradle spawn", t => {
  const f = writerFixture(t);
  const missing = spawnSync("bash", f.args.filter((_, index) => index !== 9 && index !== 10),
    { cwd: f.root, env: f.env, encoding: "utf8", timeout: 3000 });
  assert.equal(missing.status, 2); assert.equal(existsSync(join(f.privateDir, "writer.stdout")), false);
  write(join(f.root, "connect/connect.go"), "package changed\n");
  const changed = f.run(); assert.equal(changed.status, 2); assert.match(changed.stderr, /native-inputs-changed-before-writer/);
  assert.equal(existsSync(join(f.privateDir, "writer.stdout")), false);
  json(f.paths["writer-receipt"], { prior: true }); const existing = f.run(); assert.equal(existing.status, 2);
  assert.deepEqual(JSON.parse(readFileSync(f.paths["writer-receipt"])), { prior: true });
});

// Execute the actual documented commands, not a test-only approximation. The
// fake native build and real kernel consumer lock remain inside this fixture.
function documentedNativeBlock(marker) {
  const doc = readFileSync(new URL("../../../tests/TEST-PERF.md", import.meta.url), "utf8");
  const blocks = [...doc.matchAll(/```sh\n([\s\S]*?)```/g)].map(match => match[1]);
  const selected = blocks.filter(block => block.startsWith(marker));
  assert.equal(selected.length, 1, `exactly one documented ${marker} block`);
  return selected[0];
}

test("physical documentation links the current PERF contract and existing cleanup anchors", () => {
  const doc = readFileSync(new URL("./PHYSICAL_LOWBAR.md", import.meta.url), "utf8");
  const contract = readFileSync(new URL("../../../tests/TEST-PERF.md", import.meta.url), "utf8");
  assert.doesNotMatch(doc, /RUN-PERF(?:\.md|'s|;)/);
  for (const [anchor, title] of [
    ["owned-credentials-after-normal-session-completion", "Owned credentials after normal session completion"],
    ["prospective-credential-setup-rollback", "Prospective credential setup rollback"],
  ]) {
    assert.ok(doc.includes(`../../../tests/TEST-PERF.md#${anchor}`), `current ${anchor} link`);
    assert.ok(contract.includes(`#### ${title}\n`), `current ${anchor} heading`);
  }
  for (const marker of ["# NATIVE-CONTEXT:", "# WRITER executor call:", "# CONSUMER executor call:"]) {
    assert.ok(documentedNativeBlock(marker).startsWith(marker));
  }
});

function documentedWriterFixture(t) {
  const f = writerFixture(t, "native zsh contract-");
  const originalBefore = f.paths.before;
  const label = "fixture-arm";
  for (const [key, suffix] of Object.entries({ before: "native-inputs-before", after: "native-inputs-after",
    output: "native-inputs-verified", "writer-receipt": "native-writer" })) {
    f.paths[key] = join(f.privateDir, `${label}.${suffix}.json`);
  }
  renameSync(originalBefore, f.paths.before);
  symlinkSync(new URL(".", import.meta.url).pathname, join(f.root, "android/app/scripts"));
  write(join(f.root, "arm-identifiers"), `${label}\nfixture-build\n`);
  const env = { ...f.env, ROOT: f.root, RUN_DIR: f.root, GOMAXPROCS: "1", NATIVE_PROFILE_RATE: "65536" };
  const restore = documentedNativeBlock("# NATIVE-CONTEXT:");
  const writer = documentedNativeBlock("# WRITER executor call:");
  const consumer = documentedNativeBlock("# CONSUMER executor call:");
  return { ...f, env, label, restore, writer, consumer,
    runBlock(block, extra = {}) { return spawnSync("zsh", ["-fc", `set -eu\n${restore}\n${block}`],
      { cwd: f.root, env: { ...env, ...extra }, encoding: "utf8", timeout: 20000 }); } };
}

test("xRWdea: outer zsh readarray fails before Bash writer exec, with no native child or receipt", t => {
  const f = documentedWriterFixture(t);
  const old = spawnSync("zsh", ["-fc", `set -eu\nreadarray -t arm < "$RUN_DIR/arm-identifiers"\n${f.writer}`],
    { cwd: f.root, env: f.env, encoding: "utf8", timeout: 3000 });
  assert.notEqual(old.status, 0); assert.match(old.stderr, /command not found: readarray/);
  assert.equal(existsSync(f.paths["writer-receipt"]), false);
  assert.equal(existsSync(join(f.privateDir, `${f.label}.native-writer.stdout`)), false);
});

test("exact documented scalar restore, writer and consumer run from separate default-zsh calls including spaced paths", t => {
  const f = documentedWriterFixture(t);
  assert.doesNotMatch(`${f.restore}\n${f.writer}\n${f.consumer}`, /\b(?:readarray|mapfile|eval)\b|bash\s+-lc/);
  const built = f.runBlock(f.writer); assert.equal(built.status, 0, built.stderr);
  const before = JSON.parse(readFileSync(f.paths.before));
  const receipt = requireNativeWriterReceipt(f.paths, before);
  assert.equal(receipt.buildId, "fixture-build"); assert.equal(receipt.profileRate, 65536);
  const marker = join(f.privateDir, "consumer-marker");
  const consumerScript = join(f.root, "consumer fixture.sh");
  write(consumerScript, `#!/bin/bash\nset -Eeuo pipefail\n` +
    `test -s ${JSON.stringify(f.paths.after)}\ntest -s ${JSON.stringify(f.paths.output)}\n` +
    `printf '%s' assembled-after-proof > ${JSON.stringify(marker)}\n`);
  // No variables from the first zsh invocation survive this separate process.
  const consumed = f.runBlock(f.consumer, { NATIVE_CONSUMER_SCRIPT: consumerScript });
  assert.equal(consumed.status, 0, consumed.stderr);
  assert.equal(readFileSync(marker, "utf8"), "assembled-after-proof");
  assert.equal(requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies).buildOwner, "fixture-owner");
});

test("WhH7v8: documented writer and consumer accept intact canonical log bindings through a tmp-style ancestor alias", t => {
  const f = documentedWriterFixture(t); const alias = join(f.root, "tmp-alias"); symlinkSync(f.root, alias);
  const built = f.runBlock(f.writer, { RUN_DIR: alias }); assert.equal(built.status, 0, built.stderr);
  const receipt = JSON.parse(readFileSync(f.paths["writer-receipt"]));
  assert.equal(dirname(receipt.stdout.path), f.privateDir, "writer hashes retain canonical log paths");
  assert.notEqual(dirname(receipt.stdout.path), join(alias, "private"));
  const options = { before: join(alias, "private", `${f.label}.native-inputs-before.json`),
    "writer-receipt": join(alias, "private", `${f.label}.native-writer.json`) };
  const before = JSON.parse(readFileSync(f.paths.before)); const output = readFileSync(receipt.stdout.path);
  write(receipt.stdout.path, Buffer.concat([output, Buffer.from("changed\n")]));
  assert.throws(() => requireNativeWriterReceipt(options, before), /native-writer-log-binding-mismatch/);
  write(receipt.stdout.path, output); chmodSync(receipt.stdout.path, 0o644);
  assert.throws(() => requireNativeWriterReceipt(options, before), /native-writer-log-binding-mismatch/);
  chmodSync(receipt.stdout.path, 0o600);
  const preserved = `${receipt.stdout.path}.preserved`; renameSync(receipt.stdout.path, preserved); symlinkSync(preserved, receipt.stdout.path);
  assert.throws(() => requireNativeWriterReceipt(options, before), /native-writer-log-binding-mismatch/);
  rmSync(receipt.stdout.path); renameSync(preserved, receipt.stdout.path);
  const marker = join(f.privateDir, "consumer-marker"); const consumerScript = join(f.root, "alias-consumer.sh");
  write(consumerScript, `#!/bin/bash\nset -Eeuo pipefail\n` +
    `test -s ${JSON.stringify(f.paths.after)}\ntest -s ${JSON.stringify(f.paths.output)}\n` +
    `printf '%s' assembled-after-proof > ${JSON.stringify(marker)}\n`);
  const consumed = f.runBlock(f.consumer, { RUN_DIR: alias, NATIVE_CONSUMER_SCRIPT: consumerScript });
  assert.equal(consumed.status, 0, consumed.stderr);
  assert.equal(readFileSync(marker, "utf8"), "assembled-after-proof");
  assert.equal(requireVerifiedNativeInputs(f.paths.output, "fixture-build", f.dependencies).buildOwner, "fixture-owner");
});

test("documented scalar restore rejects missing, blank, incomplete and extra-line arm records before writer spawn", t => {
  const f = documentedWriterFixture(t); const identifiers = join(f.root, "arm-identifiers");
  for (const record of [null, "", "fixture-arm\n", "fixture-arm\nfixture-build", "\nfixture-build\n", "fixture-arm\n\n",
    "fixture-arm\nfixture-build\nthird\n", "fixture-arm\nfixture-build\nthird", "fixture-arm\nfixture-build\n\n"]) {
    if (record === null) rmSync(identifiers); else write(identifiers, record);
    const rejected = f.runBlock(f.writer);
    assert.equal(rejected.status, 2);
    assert.equal(rejected.stderr, "native launch failed: invalid-arm-context-no-spawn\n");
    assert.equal(existsSync(f.paths["writer-receipt"]), false);
    assert.equal(existsSync(join(f.privateDir, `${f.label}.native-writer.stdout`)), false);
    assert.equal(existsSync(f.paths.after), false); assert.equal(existsSync(f.paths.output), false);
  }
});


test("physical frozen arm capture refuses the missing arm-local Warp executable", t => {
  const f = fixture(t);
  const path = join(f.root, "warp/warpctl/build", f.hostOS, f.hostArch, "warpctl");
  rmSync(path);
  // A candidate/PATH tool is not an acceptable substitute for the control arm.
  write(join(f.root, "tools/warpctl"), "unattested candidate executable");
  assert.throws(() => f.capture("before"), /native-build-tool-unavailable/);
});

test("Warp host closure preserves each supported platform and an independent module graph", t => {
  for (const hostOS of ["darwin", "linux"]) for (const hostArch of ["amd64", "arm64"]) {
    const f = fixture(t); f.hostOS = hostOS; f.hostArch = hostArch;
    const value = f.capture("before");
    const host = value.inputs.hostTool;
    assert.equal(host.binary.path, join(f.root, "warp/warpctl/build", hostOS, hostArch, "warpctl"));
    assert.equal(host.goEnvironment.GOMOD, join(f.root, "warp/go.mod"));
    assert.equal(host.goEnvironment.GOOS, hostOS); assert.equal(host.goEnvironment.GOARCH, hostArch);
    assert.equal(host.modules.find(module => module.path === GLOG).version, "v9.0.0");
    assert.equal(value.inputs.modules.find(module => module.path === GLOG).version, "v0.0.1");
    assert.ok(host.codeInputs.some(file => file.kind === "embed"));
    assert.ok(host.codeInputs.some(file => file.module === "std"));
    assert.equal(host.repositories[0].directory, join(f.root, "warp"));
    assert.equal(validateNativeManifest(value), true);
  }
  const f = fixture(t); f.hostOS = "windows";
  assert.throws(() => f.capture("before"), /unsupported-native-host-platform/);
});

test("Warp and adjacent native inputs are rechecked before writer and consumer", t => {
  for (const relative of ["warp/warpctl/main.go", "warp/warpctl/resource.txt", "warp/go.mod", "warp/go.sum",
    "warp-dep/log.go", "warp-dep/go.mod", "warp/warpctl/build/darwin/arm64/warpctl",
    "sdk/build/gomobile-bind-retry.sh", "tools/checksec",
    "android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/fixture/bin/llvm-objcopy"]) {
    const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
    const path = join(f.root, relative); write(path, readFileSync(path, "utf8") + "changed\n");
    assert.throws(() => requireNativeBeforeWriter({ ...f.options, before: f.paths.before }, f.dependencies),
      /native-inputs-changed-before-writer/, relative);
    assert.throws(() => prepareNativeConsumer({ ...f.paths, root: f.root },
      { ...f.dependencies, consumerLock: () => true }), /native-inputs-changed-across-build/, relative);
    assert.equal(existsSync(f.paths.output), false, "a mutated tool/source cannot publish consumer proof");
  }
});

test("version context binds actual optional files and refuses drift before either native pass", t => {
  const f = fixture(t); json(f.paths.before, f.capture("before")); f.writerReceipt();
  const original = JSON.parse(readFileSync(f.paths.before)).inputs.versionContext;
  assert.equal(original.versionHome, f.versionHome);
  assert.equal(original.versionMode, "version-file"); assert.equal(original.versionCodeMode, "version-file");
  assert.equal(original.files[1].present, false);
  assert.equal(JSON.stringify(original).includes("fixture-version"), false, "version values are not retained in evidence");
  json(join(f.versionHome, "version.json"), { stagedVersion: "changed", stagedVersionCode: 56780 });
  assert.throws(() => requireNativeBeforeWriter({ ...f.options, before: f.paths.before }, f.dependencies),
    /native-inputs-changed-before-writer/);
  assert.throws(() => prepareNativeConsumer({ ...f.paths, root: f.root }, { ...f.dependencies, consumerLock: () => true }),
    /native-inputs-changed-across-build/);
  assert.equal(existsSync(f.paths.output), false);
});

test("properties override and explicit clock fallback remain distinct version contexts", t => {
  const f = fixture(t);
  delete f.dependencies.env.WARP_VERSION_HOME;
  const absent = f.capture("before").inputs.versionContext;
  assert.equal(absent.versionHome, f.root); assert.equal(absent.versionMode, "clock-host-fallback");
  assert.equal(absent.versionCodeMode, "clock-fallback"); assert.equal(absent.files[2].present, false);
  write(join(f.root, "android/app/local.properties"),
    "warp.version = fixture\\\n  -property\nwarp.version_code: 12340\nsdk.dir=" + f.androidSdk + "\n");
  const override = f.capture("after").inputs.versionContext;
  assert.equal(override.versionMode, "property"); assert.equal(override.versionCodeMode, "property");
  assert.equal(override.usesWarp, false); assert.equal(override.files.length, 1);
  json(join(f.root, "warp.json"), { ignored: "must not be emitted" });
  assert.deepEqual(f.capture("after").inputs.versionContext, override);
  rmSync(join(f.root, "android/app/local.properties"));
  for (const [stagedVersion, expected] of [[undefined, "clock-host-fallback"], [null, "clock-host-fallback"],
    ["local", "clock-host-fallback"], ["", "version-file"], ["fixed", "version-file"]]) {
    json(join(f.root, "version.json"), { stagedVersion });
    const context = f.capture("after").inputs.versionContext;
    assert.equal(context.versionMode, expected, "match Warp nil/local/empty/nonempty semantics");
    assert.equal(context.versionCodeMode, "clock-fallback");
  }
});

test("NDK writer selection and consumer strip override have independent tool identities", t => {
  const f = fixture(t);
  write(join(f.root, "android/app/local.properties"), "sdk.dir=" + f.androidSdk + "\n");
  f.dependencies.env.ANDROID_HOME = join(f.root, "unselected-sdk");
  const other = join(f.root, "consumer-ndk");
  const strip = join(other, "toolchains/llvm/prebuilt/fixture/bin/llvm-strip");
  write(strip, "fixture separate strip"); chmodSync(strip, 0o700);
  f.options["strip-ndk"] = other;
  const value = f.capture("before");
  assert.equal(value.inputs.ndk.writerNdk, f.ndk); assert.equal(value.inputs.ndk.stripNdk, other);
  assert.equal(value.inputs.ndk.stripOverride, other);
  assert.equal(value.inputs.ndk.tools.find(tool => tool.name === "llvm-strip").invocationPath, strip);
  json(f.paths.before, value);
  assert.equal(requireNativeBeforeWriter({ ...f.options, before: f.paths.before }, f.dependencies).inputHash, value.inputHash);
  write(strip, "changed strip");
  assert.throws(() => requireNativeBeforeWriter({ ...f.options, before: f.paths.before }, f.dependencies),
    /native-inputs-changed-before-writer/);
});

test("NDK missing, ambiguous, or substituted postprocessing tools cannot be attested", t => {
  for (const kind of ["missing", "ambiguous", "version", "strip-symlink"]) {
    const f = fixture(t);
    const path = join(f.ndk, "toolchains/llvm/prebuilt/fixture/bin/llvm-objcopy");
    if (kind === "missing") rmSync(path);
    if (kind === "ambiguous") write(join(f.ndk, "another/llvm-objcopy"), "ambiguous first find result");
    if (kind === "version") write(join(f.ndk, "source.properties"), "Pkg.Revision=0.0.0\n");
    if (kind === "strip-symlink") {
      json(f.paths.before, f.capture("before"));
      const strip = join(dirname(path), "llvm-strip");
      rmSync(strip); const other = join(dirname(path), "other-tool");
      write(other, "changed strip target"); chmodSync(other, 0o700); symlinkSync("other-tool", strip);
      assert.throws(() => requireNativeBeforeWriter({ ...f.options, before: f.paths.before }, f.dependencies),
        /native-inputs-changed-before-writer/);
    } else assert.throws(() => f.capture("before"), /(?:one-selected-native-objcopy-and-strip-required|native-writer-ndk-version-mismatch)/);
  }
});

test("selected mobile tools require actual embedded frozen compiler and exact package/module pins", t => {
  const f = fixture(t); const original = f.capture("before");
  assert.equal(original.inputs.mobileTools.length, 3);
  for (const row of original.inputs.mobileTools) {
    assert.equal(row.sha256, original.inputs.tools.find(tool => tool.name === row.name).sha256);
    assert.equal(row.buildInfo.GoVersion, "go1.fixture");
  }
  for (const kind of ["stale", "missing", "package", "module", "version", "replacement"]) {
    f.mobileInfo = name => {
      const info = structuredClone(original.inputs.mobileTools.find(row => row.name === name).buildInfo);
      if (kind === "stale") info.GoVersion = "go1.old";
      if (kind === "missing") return {};
      if (kind === "package") info.Path = "not-the-executed-package";
      if (kind === "module") info.Main.Path = "another-module";
      if (kind === "version") info.Main.Version = "v0.0.0-wrong";
      if (kind === "replacement") info.Main.Replace = { Path: "/unattested/source" };
      return info;
    };
    assert.throws(() => f.capture("before"), /native-mobile-tool-build-metadata-mismatch/, kind);
  }
  delete f.mobileInfo;
  assert.equal(f.capture("after").inputHash, original.inputHash);
  const queries = f.commands.filter(call => call.args[0] === "version");
  assert.ok(queries.length > 3);
  for (const call of queries) {
    assert.equal(call.command, join(f.root, "std/bin/go"));
    assert.deepEqual(call.args.slice(0, 3), ["version", "-m", "-json"]);
    assert.ok(original.inputs.tools.some(tool => tool.path === call.args[3] &&
      ["gomobile", "gobind", "checksec"].includes(tool.name)));
  }
});


test("selected Java properties honor escaped keys, Unicode, comments, duplicates and continuations", t => {
  const f = fixture(t);
  const selectedSdk = join(f.root, "selected-sdk#suffix");
  mkdirSync(selectedSdk, { mode: 0o700 });
  const escapedSdk = join(f.root, "selected-sdk").replaceAll("/", "\\u002f");
  write(join(f.root, "android/app/local.properties"),
    " # a comment ending in backslash does not continue\\\r\n" +
    "sdk.dir=" + escapedSdk + "\\\r\n#suffix\r\n" +
    "ndk.dir : " + f.ndk + "\r\n" +
    "warp\\.version=old\r\nwarp.version: selected\\\r\n#-property\r\n" +
    "warp.version_code=12340\r\nignored.private=value\\u0020not-retained\r\n");
  f.dependencies.env.ANDROID_HOME = join(f.root, "must-not-select-ambient-sdk");
  const value = f.capture("before");
  assert.equal(value.inputs.ndk.sdkRoot, selectedSdk, "a continued # is part of the selected SDK path");
  assert.equal(value.inputs.ndk.writerNdk, f.ndk);
  assert.equal(value.inputs.versionContext.versionMode, "property");
  assert.equal(value.inputs.versionContext.versionCodeMode, "property");
  assert.equal(JSON.stringify(value).includes("not-retained"), false);
  write(join(f.root, "android/app/local.properties"), "sdk.dir=\\uZZZZ\n");
  assert.throws(() => f.capture("before"), /native-properties-invalid/);
});

test("summary rehash cannot hide omitted host closure, runtime, tool identity or native helpers", t => {
  const f = fixture(t); const original = f.capture("before");
  for (const mutate of [
    m => delete m.inputs.hostTool, m => delete m.inputs.hostTool.binary,
    m => { m.inputs.hostTool.binary.path = join(f.root, "candidate/warpctl"); },
    m => { m.inputs.hostTool.modules.pop(); }, m => { m.inputs.hostTool.moduleInputs.pop(); },
    m => { m.inputs.hostTool.codeInputs.pop(); }, m => { m.inputs.hostTool.packageSelections.pop(); },
    m => { m.inputs.hostTool.resolvedModuleGraph.pop(); }, m => { m.inputs.hostTool.repositories = []; },
    m => { delete m.inputs.hostTool.goEnvironment.GOWORK; }, m => delete m.inputs.versionContext,
    m => { m.inputs.versionContext.files.pop(); }, m => delete m.inputs.ndk,
    m => { m.inputs.ndk.tools.pop(); }, m => delete m.inputs.mobileTools,
    m => { m.inputs.mobileTools[0].path = join(f.root, "unexecuted-tool"); },
    m => { m.inputs.mobileTools[0].sha256 = "0".repeat(64); },
    m => { m.inputs.tools = m.inputs.tools.filter(tool => tool.name !== "checksec"); },
    m => { m.inputs.buildInputs = m.inputs.buildInputs.filter(file => !file.path.endsWith("/gomobile-bind-retry.sh")); },
  ]) {
    const changed = structuredClone(original); mutate(changed); rehash(changed);
    assert.throws(() => validateNativeManifest(changed), undefined, String(mutate));
  }
});
