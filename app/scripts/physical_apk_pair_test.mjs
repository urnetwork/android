import assert from "node:assert/strict";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { checkPair, observeDevices, parseArgs, parseInstalledVersions, PERFORMANCE_SERIALS,
  selectAndRetainPair, selectCandidates } from "./physical_apk_pair.mjs";

const APP = "com.bringyour.network";
const BASE = 1034210530;
const split = (abi, suffix) => ({ type: "ONE_OF_MANY", filters: [{ filterType: "ABI", value: abi }],
  outputFile: `${abi}.apk`, versionCode: BASE + suffix });
const appMetadata = () => ({ version: 3, artifactType: { type: "APK", kind: "Directory" }, elementType: "File",
  applicationId: APP, variantName: "githubDebug", elements: [
    { type: "UNIVERSAL", filters: [], outputFile: "universal.apk", versionCode: BASE },
    split("armeabi-v7a", 2), split("x86_64", 1), split("arm64-v8a", 3),
  ] });
const testMetadata = () => ({ version: 3, artifactType: { type: "APK", kind: "Directory" }, elementType: "File",
  applicationId: `${APP}.test`, variantName: "githubDebugAndroidTest",
  elements: [{ type: "SINGLE", filters: [], outputFile: "test.apk", versionCode: 0 }] });
const observed = (appCode = BASE + 3, testCode = 0) => ({ type: "physical-apk-devices", schemaVersion: 1, observedAtUnixMs: 123,
  devices: PERFORMANCE_SERIALS.map(serial => ({ serial, abis: ["arm64-v8a", "armeabi-v7a"], versions: { app: appCode, test: testCode } })) });
const writeJson = (path, data) => writeFileSync(path, JSON.stringify(data), { mode: 0o600 });

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "apk-pair-test-"));
  t.after(() => rmSync(root, { force: true, recursive: true }));
  const generated = join(root, "generated"); const artifacts = join(root, "private");
  mkdirSync(generated); mkdirSync(artifacts, { mode: 0o700 });
  const options = { observed: join(artifacts, "devices.json"), "app-metadata": join(generated, "app.json"),
    "test-metadata": join(generated, "test.json"), "build-id": "fixture-id", aapt: "/fixture/aapt",
    "app-output": join(artifacts, "app.apk"), "test-output": join(artifacts, "test.apk"), output: join(artifacts, "pair.json") };
  writeJson(options.observed, observed()); writeJson(options["app-metadata"], appMetadata()); writeJson(options["test-metadata"], testMetadata());
  for (const [metadata, name] of [[appMetadata(), APP], [testMetadata(), `${APP}.test`]]) {
    for (const element of metadata.elements) writeJson(join(generated, element.outputFile), { name, versionCode: element.versionCode,
      abi: name === APP ? element.filters[0]?.value ?? "arm64-v8a" : null });
  }
  const f = { root, generated, artifacts, options, appVersion: BASE + 3, testVersion: 0, commands: [] };
  f.dependencies = { now: () => 456, invoke(command, args) {
    f.commands.push({ command, args });
    const output = stdout => ({ status: 0, stdout, stderr: "" });
    if (command === "/fixture/aapt") {
      assert.deepEqual(args.slice(0, 2), ["dump", "badging"]);
      const data = JSON.parse(readFileSync(args[2], "utf8"));
      return output(`package: name='${data.name}' versionCode='${data.versionCode}' versionName='fixture'\n` +
        (data.abi ? `native-code: '${data.abi}'\n` : ""));
    }
    assert.equal(command, "adb"); assert.equal(args[0], "-s"); assert.ok(PERFORMANCE_SERIALS.includes(args[1]));
    if (args[2] === "get-state") { assert.equal(args.length, 3); return output("device\n"); }
    if (args[3] === "getprop") { assert.deepEqual(args.slice(2), ["shell", "getprop", "ro.product.cpu.abilist"]); return output("arm64-v8a,armeabi-v7a\n"); }
    assert.deepEqual(args.slice(2), ["shell", "cmd", "package", "list", "packages", "--show-versioncode", APP]);
    return output(`package:${APP} versionCode:${f.appVersion}\npackage:${APP}.test versionCode:${f.testVersion}\n`);
  } };
  return f;
}

test("root cause: universal .530 would downgrade installed arm64 .533; compatible split replaces at equal code", () => {
  const app = appMetadata();
  assert.ok(app.elements[0].versionCode < observed().devices[0].versions.app);
  const selected = selectCandidates(app, testMetadata(), observed());
  assert.equal(selected.app.outputFile, "arm64-v8a.apk");
  assert.equal(selected.app.versionCode, BASE + 3);
  assert.equal(selected.test.versionCode, 0, "instrumentation code is independent of application code");
});

test("selection uses the higher installed floor across the pair and rejects a genuinely newer app or test", () => {
  const devices = observed(BASE); devices.devices[1].versions.app = BASE + 3;
  assert.equal(selectCandidates(appMetadata(), testMetadata(), devices).floors.app, BASE + 3);
  for (const [appCode, testCode, message] of [[BASE + 4, 0, /app-apk-would-downgrade/], [BASE + 3, 1, /test-apk-would-downgrade/]]) {
    assert.throws(() => selectCandidates(appMetadata(), testMetadata(), observed(appCode, testCode)), message);
  }
});

test("absent/equal versions permit normal install; compatible universal is fallback only", () => {
  const app = appMetadata();
  assert.equal(selectCandidates(app, testMetadata(), observed(null, null)).app.outputFile, "arm64-v8a.apk");
  app.elements = app.elements.slice(0, 1);
  assert.equal(selectCandidates(app, testMetadata(), observed(BASE)).app.outputFile, "universal.apk");
  assert.throws(() => selectCandidates(app, testMetadata(), observed()), /app-apk-would-downgrade/);
});

test("metadata rejects mismatched pair, traversal, duplicate candidates, wrong ABI and unobserved devices", () => {
  for (const mutation of [
    (app, apkTest) => { apkTest.variantName = "playDebugAndroidTest"; },
    app => { app.applicationId = "wrong.package"; },
    app => { app.elements[3].outputFile = "../external.apk"; },
    app => { app.elements[3].outputFile = "bad\\name.apk"; },
    app => { app.elements.push({ ...app.elements[3] }); },
    app => { app.elements = [app.elements[2]]; },
    (_app, _test, devices) => { devices.devices[1].serial = "unrelated"; },
    (_app, _test, devices) => { devices.devices[1].abis = ["armeabi-v7a"]; },
    app => { app.elements[0].versionCode = 2147483648; },
  ]) {
    const app = appMetadata(); const apkTest = testMetadata(); const devices = observed();
    mutation(app, apkTest, devices);
    assert.throws(() => selectCandidates(app, apkTest, devices));
  }
});

test("installed-version parser handles exact names and absence; errors never become zero", () => {
  assert.deepEqual(parseInstalledVersions(""), { app: null, test: null });
  assert.deepEqual(parseInstalledVersions(`package:${APP} versionCode:${BASE + 3}\npackage:${APP}.test versionCode:0\npackage:${APP}.extra versionCode:7\n`),
    { app: BASE + 3, test: 0 });
  for (const raw of ["Error: package service unavailable", `package:${APP}`, `package:${APP} versionCode:-1`,
    `package:${APP} versionCode:2147483648`, `package:${APP} versionCode:1\npackage:${APP} versionCode:1`]) {
    assert.throws(() => parseInstalledVersions(raw));
  }
});

test("observation only reads the two allowlisted devices and fails on unavailable ADB", t => {
  const f = fixture(t); const result = observeDevices(f.dependencies);
  assert.equal(result.devices.length, 2); assert.equal(f.commands.length, 6);
  assert.deepEqual(new Set(f.commands.map(command => command.args[1])), new Set(PERFORMANCE_SERIALS));
  assert.throws(() => observeDevices({ invoke: () => ({ status: 1, stdout: "", stderr: "private failure" }) }), /apk-pair-command-failed/);
  assert.throws(() => observeDevices({ invoke: () => ({ status: 0, stdout: "unauthorized\n" }) }), /performance-device-offline/);
});

test("selector retains exact compatible APKs mode 0600 and checks manifest codes without any device command", t => {
  const f = fixture(t); const pair = selectAndRetainPair(f.options, f.dependencies);
  assert.equal(pair.app.versionCode, BASE + 3); assert.equal(pair.test.versionCode, 0);
  assert.equal(pair.buildId, "fixture-id");
  for (const key of ["app", "test"]) {
    assert.equal(statSync(pair[key].path).mode & 0o777, 0o600);
    assert.match(pair[key].sha256, /^[a-f0-9]{64}$/);
  }
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.ok(f.commands.every(command => command.command === f.options.aapt));
  assert.throws(() => selectAndRetainPair(f.options, f.dependencies), /fresh-apk-evidence-required/);
});

test("binary metadata mismatch or wrong packaged ABI fails before selection publication", t => {
  const f = fixture(t); const source = join(f.generated, "arm64-v8a.apk");
  writeJson(source, { name: APP, versionCode: BASE, abi: "arm64-v8a" });
  assert.throws(() => selectAndRetainPair(f.options, f.dependencies), /apk-manifest-version-mismatch/);
  assert.equal(existsSync(f.options.output), false);
  writeJson(source, { name: APP, versionCode: BASE + 3, abi: "armeabi-v7a" });
  assert.throws(() => selectAndRetainPair(f.options, f.dependencies), /apk-native-abi-mismatch/);
  assert.equal(existsSync(f.options.output), false);
});

test("selector rejects symlink APKs, nonprivate evidence, and output collisions without touching destinations", t => {
  const f = fixture(t); const source = join(f.generated, "arm64-v8a.apk");
  rmSync(source); symlinkSync(join(f.generated, "universal.apk"), source);
  assert.throws(() => selectAndRetainPair(f.options, f.dependencies));
  assert.equal(f.commands.length, 0); assert.equal(existsSync(f.options["app-output"]), false);
  rmSync(source); writeJson(source, { name: APP, versionCode: BASE + 3, abi: "arm64-v8a" });
  chmodSync(f.options.observed, 0o644);
  assert.throws(() => selectAndRetainPair(f.options, f.dependencies), /private-apk-evidence-required/);
  chmodSync(f.options.observed, 0o600);
  assert.throws(() => selectAndRetainPair({ ...f.options, "test-output": f.options["app-output"] }, f.dependencies), /fresh-apk-evidence-required/);
});

test("preinstall check catches an intervening package upgrade independently on app and instrumentation", t => {
  const f = fixture(t); selectAndRetainPair(f.options, f.dependencies);
  assert.equal(checkPair(f.options.output, f.dependencies).eligible, true);
  f.appVersion = BASE + 4;
  assert.throws(() => checkPair(f.options.output, f.dependencies), /app-apk-would-downgrade/);
  f.appVersion = BASE + 3; f.testVersion = 1;
  assert.throws(() => checkPair(f.options.output, f.dependencies), /test-apk-would-downgrade/);
});

test("preinstall check catches replaced retained APKs before reading any phone", t => {
  const f = fixture(t); selectAndRetainPair(f.options, f.dependencies); f.commands = [];
  writeFileSync(f.options["app-output"], "changed artifact");
  assert.throws(() => checkPair(f.options.output, f.dependencies), /retained-apk-pair-changed/);
  assert.equal(f.commands.length, 0);
});

test("preinstall check refuses a retained APK whose private mode was lost", t => {
  const f = fixture(t); selectAndRetainPair(f.options, f.dependencies); f.commands = [];
  chmodSync(f.options["app-output"], 0o644);
  assert.throws(() => checkPair(f.options.output, f.dependencies), /private-apk-evidence-required/);
  assert.equal(f.commands.length, 0);
});

test("CLI requires explicit absolute context and does not accept install/downgrade options", () => {
  assert.deepEqual(parseArgs(["observe", "--output", "/private/versions.json"]), { mode: "observe", output: "/private/versions.json" });
  for (const args of [[], ["observe", "--output", "relative"], ["observe", "--serial", "extra-device"],
    ["check", "--selection", "/pair", "--selection", "/again"], ["install", "-d"]]) {
    assert.throws(() => parseArgs(args));
  }
});

