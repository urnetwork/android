#!/usr/bin/env node

// Select and retain a compatible APK pair without downgrading either package.
// Device operations are read-only and restricted to the performance pair.
// Native source/ABI linkage and the runtime acceptance build ID remain separate
// mandatory gates; Android versionCode is an installation floor, not identity.
import { spawnSync } from "node:child_process";
import { closeSync, constants, existsSync, lstatSync, openSync, readFileSync, readSync, writeFileSync, writeSync } from "node:fs";
import { basename, dirname, isAbsolute, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { hashNativeInputFile } from "./physical_native_provenance.mjs";

export const PERFORMANCE_SERIALS = ["3B161FDJG001KT", "R5CX21FY6ND"];
const APP = "com.bringyour.network";
const TEST = `${APP}.test`;
const ABI = "arm64-v8a";
const MAX_VERSION_CODE = 2147483647;
class PairError extends Error {}
const fail = reason => { throw new PairError(reason); };
const reason = error => error instanceof PairError ? error.message : "apk-pair-evidence-unavailable";
const code = value => Number.isSafeInteger(value) && value >= 0 && value <= MAX_VERSION_CODE;

function invoke(command, args, dependencies) {
  let result;
  try { result = (dependencies.invoke ?? spawnSync)(command, args,
    { encoding: "utf8", timeout: 15_000, maxBuffer: 1024 * 1024 }); } catch { fail("apk-pair-command-failed"); }
  if (result?.status !== 0 || result.error || result.signal || typeof result.stdout !== "string") fail("apk-pair-command-failed");
  return result.stdout;
}

// `cmd package list packages --show-versioncode` also matches the test package.
// Empty successful output means neither package is installed; failed/garbled
// output cannot be treated as an absent (zero-version) package.
export function parseInstalledVersions(raw) {
  const versions = { app: null, test: null };
  for (const line of raw.trim().split(/\r?\n/).filter(Boolean)) {
    const match = /^package:([A-Za-z0-9_.]+) versionCode:([0-9]+)$/.exec(line.trim());
    if (!match || !code(Number(match[2]))) fail("installed-package-version-unavailable");
    const key = match[1] === APP ? "app" : match[1] === TEST ? "test" : null;
    if (!key) continue;
    if (versions[key] !== null) fail("installed-package-version-ambiguous");
    versions[key] = Number(match[2]);
  }
  return versions;
}

export function observeDevices(dependencies = {}) {
  const devices = PERFORMANCE_SERIALS.map(serial => {
    if (invoke("adb", ["-s", serial, "get-state"], dependencies).trim() !== "device") fail("performance-device-offline");
    const abis = invoke("adb", ["-s", serial, "shell", "getprop", "ro.product.cpu.abilist"], dependencies).trim().split(",");
    if (!abis.includes(ABI) || abis.some(value => !/^[a-z0-9_-]+$/.test(value))) fail("performance-device-abi-unavailable");
    const versions = parseInstalledVersions(invoke("adb", ["-s", serial, "shell", "cmd", "package", "list", "packages",
      "--show-versioncode", APP], dependencies));
    return { serial, abis, versions };
  });
  return { type: "physical-apk-devices", schemaVersion: 1, observedAtUnixMs: (dependencies.now ?? Date.now)(), devices };
}

function validateDevices(observed) {
  if (observed?.type !== "physical-apk-devices" || observed.schemaVersion !== 1 ||
      !Number.isFinite(observed.observedAtUnixMs) || !Array.isArray(observed.devices) || observed.devices.length !== 2 ||
      PERFORMANCE_SERIALS.some(serial => observed.devices.filter(device => device.serial === serial).length !== 1) ||
      observed.devices.some(device => !Array.isArray(device.abis) || !device.abis.includes(ABI) || !device.versions ||
        ["app", "test"].some(key => device.versions[key] !== null && !code(device.versions[key])))) fail("performance-pair-observation-required");
  return observed;
}

export function selectCandidates(appMetadata, testMetadata, observed) {
  validateDevices(observed);
  if (appMetadata?.applicationId !== APP || testMetadata?.applicationId !== TEST ||
      typeof appMetadata.variantName !== "string" || !/^[A-Za-z][A-Za-z0-9_]*Debug$/.test(appMetadata.variantName) ||
      testMetadata.variantName !== `${appMetadata.variantName}AndroidTest`) fail("matching-debug-apk-pair-required");
  for (const metadata of [appMetadata, testMetadata]) {
    if (metadata.version !== 3 || metadata.artifactType?.type !== "APK" || metadata.elementType !== "File" ||
        !Array.isArray(metadata.elements) || !metadata.elements.length || metadata.elements.some(element =>
          !code(element.versionCode) || !Array.isArray(element.filters) || typeof element.outputFile !== "string" ||
          !element.outputFile.endsWith(".apk") || basename(element.outputFile) !== element.outputFile ||
          /[\\\0\r\n]/.test(element.outputFile))) fail("complete-apk-metadata-required");
  }
  const floor = key => Math.max(0, ...observed.devices.map(device => device.versions[key] ?? 0));
  // Prefer the 64-bit split for both pinned phones. The universal APK is lower
  // by the ABI suffix (currently +3), so first-by-name/universal-first is unsafe.
  const appCandidates = appMetadata.elements.map(element => ({ element,
    rank: element.type === "ONE_OF_MANY" && element.filters.length === 1 &&
      element.filters[0].filterType === "ABI" && element.filters[0].value === ABI ? 0 :
      ["UNIVERSAL", "SINGLE"].includes(element.type) && element.filters.length === 0 ? 1 : 2 }))
    .filter(candidate => candidate.rank < 2 && candidate.element.versionCode >= floor("app"))
    .sort((a, b) => a.rank - b.rank);
  if (!appCandidates.length) fail("app-apk-would-downgrade-or-abi-mismatch");
  if (appCandidates.length > 1 && appCandidates[0].rank === appCandidates[1].rank) fail("app-apk-selection-ambiguous");
  const tests = testMetadata.elements.filter(element => ["SINGLE", "UNIVERSAL"].includes(element.type) && element.filters.length === 0);
  if (tests.length !== 1) fail("test-apk-selection-ambiguous");
  if (tests[0].versionCode < floor("test")) fail("test-apk-would-downgrade");
  return { app: appCandidates[0].element, test: tests[0], floors: { app: floor("app"), test: floor("test") } };
}

function readJson(path, privateFile = false) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 1024 * 1024) fail("apk-metadata-file-untrusted");
  if (privateFile) {
    prepareArtifactDirectory(dirname(path));
    if (stat.uid !== process.getuid() || (stat.mode & 0o7777) !== 0o600) fail("private-apk-evidence-required");
  }
  return JSON.parse(readFileSync(path, "utf8"));
}

function freshOutput(path) {
  const binding = prepareArtifactDirectory(dirname(path));
  requireArtifactPaths(binding, [path]);
  if (existsSync(path)) fail("fresh-apk-evidence-required");
  return binding;
}

function publish(path, value, binding) {
  requireArtifactPaths(binding, [path]);
  writeFileSync(path, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
}

function verifyManifest(aapt, path, expected, packageName, dependencies) {
  const raw = invoke(aapt, ["dump", "badging", path], dependencies);
  const match = /^package: name='([^']+)' versionCode='([0-9]*)'/m.exec(raw);
  if (!match || match[1] !== packageName || Number(match[2]) !== expected.versionCode) fail("apk-manifest-version-mismatch");
  // The test APK is Java-only; the application must actually package ARM64.
  if (packageName === APP && !/^native-code:.*'arm64-v8a'/m.test(raw)) fail("apk-native-abi-mismatch");
}

function retainApk(source, destination) {
  const before = hashNativeInputFile(source);
  let input; let output;
  try {
    input = openSync(source, constants.O_RDONLY | constants.O_NOFOLLOW);
    output = openSync(destination, "wx", 0o600);
    const buffer = Buffer.alloc(64 * 1024);
    for (;;) {
      const count = readSync(input, buffer, 0, buffer.length, null);
      if (!count) break;
      for (let offset = 0; offset < count;) offset += writeSync(output, buffer, offset, count - offset);
    }
  } finally {
    if (output !== undefined) closeSync(output);
    if (input !== undefined) closeSync(input);
  }
  const retained = hashNativeInputFile(destination);
  if (retained.sha256 !== before.sha256 || hashNativeInputFile(source).sha256 !== before.sha256) fail("apk-changed-during-retention");
  return retained;
}

export function selectAndRetainPair(options, dependencies = {}) {
  const binding = freshOutput(options.output);
  const destinations = [options["app-output"], options["test-output"], options.output];
  requireArtifactPaths(binding, destinations);
  if (new Set(destinations.map(path => resolve(path))).size !== 3 || destinations.some(existsSync)) fail("fresh-apk-evidence-required");
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(options["build-id"])) fail("explicit-acceptance-build-id-required");
  const observed = validateDevices(readJson(options.observed, true));
  const appMetadata = readJson(options["app-metadata"]); const testMetadata = readJson(options["test-metadata"]);
  const selected = selectCandidates(appMetadata, testMetadata, observed);
  const artifacts = {};
  for (const key of ["app", "test"]) {
    const source = join(dirname(options[`${key}-metadata`]), selected[key].outputFile);
    hashNativeInputFile(source); // Reject links/nonfiles before the manifest tool reads it.
    verifyManifest(options.aapt, source, selected[key], key === "app" ? APP : TEST, dependencies);
    requireArtifactPaths(binding, destinations);
    artifacts[key] = { ...retainApk(source, options[`${key}-output`]), versionCode: selected[key].versionCode,
      packageName: key === "app" ? APP : TEST, outputType: selected[key].type };
    verifyManifest(options.aapt, artifacts[key].path, selected[key], artifacts[key].packageName, dependencies);
  }
  const report = { type: "physical-apk-pair", schemaVersion: 1, buildId: options["build-id"], abi: ABI,
    variantName: appMetadata.variantName, observed, ...artifacts };
  publish(options.output, report, binding);
  return report;
}

export function checkPair(selectionPath, dependencies = {}) {
  const pair = readJson(selectionPath, true);
  const binding = prepareArtifactDirectory(dirname(selectionPath));
  if (pair?.type !== "physical-apk-pair" || pair.schemaVersion !== 1 || pair.abi !== ABI) fail("retained-apk-pair-required");
  for (const key of ["app", "test"]) {
    const artifact = pair[key];
    if (!artifact || artifact.packageName !== (key === "app" ? APP : TEST) || !code(artifact.versionCode) ||
        hashNativeInputFile(artifact.path).sha256 !== artifact.sha256) fail("retained-apk-pair-changed");
    requireArtifactPaths(binding, [artifact.path]);
    const stat = lstatSync(artifact.path);
    if (stat.uid !== process.getuid() || (stat.mode & 0o7777) !== 0o600) fail("private-apk-evidence-required");
  }
  const current = observeDevices(dependencies);
  for (const key of ["app", "test"]) {
    if (current.devices.some(device => (device.versions[key] ?? 0) > pair[key].versionCode)) fail(`${key}-apk-would-downgrade`);
  }
  return { type: "physical-apk-install-preflight", schemaVersion: 1, eligible: true,
    buildId: pair.buildId, appVersionCode: pair.app.versionCode, testVersionCode: pair.test.versionCode, observed: current };
}

export function parseArgs(argv) {
  const mode = argv[0];
  const keys = mode === "observe" ? ["output"] : mode === "select" ? ["observed", "app-metadata", "test-metadata", "build-id",
    "aapt", "app-output", "test-output", "output"] : mode === "check" ? ["selection", "output"] : [];
  if (!keys.length || argv.length !== keys.length * 2 + 1) fail("explicit-apk-pair-arguments-required");
  const options = { mode };
  for (let index = 1; index < argv.length; index += 2) {
    const key = argv[index].slice(2); const value = argv[index + 1];
    if (!argv[index].startsWith("--") || !keys.includes(key) || options[key] !== undefined || !value || value.startsWith("--") ||
        key !== "build-id" && !isAbsolute(value)) fail("explicit-apk-pair-arguments-required");
    options[key] = value;
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2)); const binding = freshOutput(options.output);
    if (options.mode === "observe") publish(options.output, observeDevices(), binding);
    else if (options.mode === "select") selectAndRetainPair(options);
    else publish(options.output, checkPair(options.selection), binding);
    process.stdout.write(`${JSON.stringify({ eligible: true, classification: "APK_PAIR_VERSION_CHECKED" })}\n`);
  } catch (error) { process.stderr.write(`APK pair failed: ${reason(error)}\n`); process.exitCode = 2; }
}

