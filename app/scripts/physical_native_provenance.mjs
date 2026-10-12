#!/usr/bin/env node

// Source linkage only: this never builds, installs, emits credential values or
// changes module files. Binary/AAR/APK/strip/consumer-lock attestation remains separate.
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { accessSync, closeSync, constants, existsSync, fstatSync, lstatSync, openSync, readFileSync,
  readSync, readdirSync, realpathSync, writeFileSync } from "node:fs";
import { delimiter, dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { availableParallelism } from "node:os";
import { fileURLToPath, pathToFileURL } from "node:url";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";

const LIMIT = 32 * 1024 * 1024;
const ABIS = ["arm64", "arm", "amd64"];
const TAG = "sdk_mobile_bind";
const SDK = "github.com/urnetwork/sdk";
const CONNECT = "github.com/urnetwork/connect";
const MOBILE = "golang.org/x/mobile";
const BRIDGE_PACKAGES = [`${MOBILE}/bind/java`, `${MOBILE}/bind/seq`];
// Copied verbatim by the supported gobind Android generator, not GoFiles of
// the SDK package. Binary tool hashes alone do not cover these disk inputs.
const BRIDGE_FILES = ["bind/seq.go.support", "bind/java/Seq.java", "bind/java/seq_android.c.support",
  "bind/java/seq_android.go.support", "bind/java/seq_android.h"];
const FILE_FIELDS = ["GoFiles", "CgoFiles", "CFiles", "CXXFiles", "MFiles", "HFiles", "FFiles", "SFiles",
  "SwigFiles", "SwigCXXFiles", "SysoFiles", "EmbedFiles"];
const TOOL_NAMES = ["go", "gomobile", "gobind", "checksec", "compile", "link", "asm", "cgo"];
// Keep writer selection inside the source-hashed provenance module.
const WRITER_MEMORY_PROFILES = Object.freeze(["android", "ios-memory-audit-v1", "ios-memory-audit-v2"]);
const SELF = fileURLToPath(import.meta.url);
const validHash = (value) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
const validLabel = (value) => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$/.test(value);
const sha = (value) => createHash("sha256").update(value).digest("hex");
class NativeInputError extends Error {}
const fail = (reason) => { throw new NativeInputError(reason); };
const reason = (error) => error instanceof NativeInputError ? error.message : "native-input-evidence-unavailable";
export const nativeProvenanceReason = reason;
const sorted = (values) => values.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b), "en"));

export function parseJsonStream(raw) {
  if (typeof raw !== "string" || raw.length > LIMIT) fail("bounded-native-metadata-required");
  const values = []; let begin = -1; let depth = 0; let quoted = false; let escaped = false;
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i];
    if (begin < 0) { if (/\s/.test(c)) continue; if (c !== "{") fail("native-metadata-invalid"); begin = i; depth = 1; continue; }
    if (quoted) { if (escaped) escaped = false; else if (c === "\\") escaped = true; else if (c === '"') quoted = false; continue; }
    if (c === '"') quoted = true;
    else if (c === "{") depth++;
    else if (c === "}" && --depth === 0) { try { values.push(JSON.parse(raw.slice(begin, i + 1))); } catch { fail("native-metadata-invalid"); } begin = -1; }
  }
  if (begin >= 0 || !values.length) fail("native-metadata-incomplete");
  return values;
}

function inputFile(path, optional = false) {
  let stat;
  try { stat = lstatSync(path); } catch (error) {
    if (optional && error.code === "ENOENT") return { path: resolve(path), present: false, sha256: null, bytes: 0 };
    fail("native-input-file-missing");
  }
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 512 * 1024 * 1024) fail("native-input-file-untrusted");
  const fd = openSync(path, "r"); const buffer = Buffer.alloc(64 * 1024); const hash = createHash("sha256");
  try { for (;;) { const n = readSync(fd, buffer, 0, buffer.length, null); if (!n) break; hash.update(buffer.subarray(0, n)); } }
  finally { closeSync(fd); }
  const after = lstatSync(path);
  if (after.dev !== stat.dev || after.ino !== stat.ino || after.size !== stat.size || after.mtimeMs !== stat.mtimeMs) fail("native-input-changed-during-read");
  return { path: realpathSync(path), present: true, sha256: hash.digest("hex"), bytes: stat.size };
}
export const hashNativeInputFile = inputFile;

function toolPath(name, env) {
  for (const directory of (env.PATH ?? "").split(delimiter)) {
    const path = resolve(directory || ".", name);
    try { accessSync(path, constants.X_OK); return realpathSync(path); } catch { /* next PATH entry */ }
  }
  fail("native-build-tool-unavailable");
}

function privateRead(path) {
  try { prepareArtifactDirectory(dirname(path)); } catch { fail("private-native-manifest-required"); }
  let stat;
  try { stat = lstatSync(path); } catch { fail("private-native-manifest-required"); }
  if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid() || (stat.mode & 0o777) !== 0o600 || stat.size > LIMIT) {
    fail("private-native-manifest-required");
  }
  try { return JSON.parse(readFileSync(path, "utf8")); } catch { fail("native-manifest-invalid-json"); }
}

function outputBinding(path) {
  try { const binding = prepareArtifactDirectory(dirname(path)); requireArtifactPaths(binding, [path]); return binding; }
  catch (error) { fail(artifactDirectoryReason(error)); }
}

function writePrivate(path, value, binding) {
  try { requireArtifactPaths(binding, [path]); } catch (error) { fail(artifactDirectoryReason(error)); }
  const data = `${JSON.stringify(value)}\n`; if (data.length > LIMIT) fail("native-manifest-too-large");
  writeFileSync(path, data, { mode: 0o600, flag: "wx" });
}

function invokeFor(dependencies, cwd, env) {
  return (command, args) => {
    let result;
    try { result = (dependencies.invoke ?? spawnSync)(command, args, { cwd, env, encoding: "utf8", timeout: 60_000, maxBuffer: LIMIT }); }
    catch { fail("native-metadata-command-failed"); }
    if (result?.status !== 0 || result.error || result.signal || typeof result.stdout !== "string") fail("native-metadata-command-failed");
    return result.stdout;
  };
}

function moduleRecord(module) {
  if (!module || typeof module.Path !== "string" || !module.Dir || !module.GoMod || module.Error) fail("resolved-native-module-required");
  const replacement = module.Replace ? { path: module.Replace.Path, version: module.Replace.Version ?? null,
    directory: module.Replace.Dir ? realpathSync(module.Replace.Dir) : null, goMod: module.Replace.GoMod ?? null,
    sum: module.Replace.Sum ?? null, goModSum: module.Replace.GoModSum ?? null } : null;
  return { path: module.Path, version: module.Version ?? null, main: module.Main === true,
    directory: realpathSync(module.Dir), goMod: realpathSync(module.GoMod), sum: module.Sum ?? null,
    goModSum: module.GoModSum ?? null, replacement };
}

function fingerprint(manifest) {
  const { root, buildOwner, buildId, profileRate, inputs } = manifest;
  return sha(JSON.stringify({ root, buildOwner, buildId, profileRate, inputs }));
}


const WARP = "github.com/urnetwork/warp";

// Java Properties.load(InputStream) uses Latin-1, escapes and continuations.
// Keep the file hash, never emit unrelated properties or Warp credentials.
function nativeProperties(file) {
  if (!file.present) return {};
  const data = readFileSync(file.path);
  if (data.length > 1024 * 1024 || sha(data) !== file.sha256) fail("native-runtime-input-changed");
  const unescape = value => {
    let decoded = "";
    for (let i = 0; i < value.length; i++) {
      if (value[i] !== "\\") { decoded += value[i]; continue; }
      const escaped = value[++i];
      if (escaped === "u") {
        const digits = value.slice(i + 1, i + 5);
        if (!/^[0-9a-fA-F]{4}$/.test(digits)) fail("native-properties-invalid");
        decoded += String.fromCharCode(parseInt(digits, 16)); i += 4;
      } else {
        if (escaped === undefined) fail("native-properties-incomplete");
        decoded += ({ t: "\t", r: "\r", n: "\n", f: "\f" })[escaped] ?? escaped;
      }
    }
    return decoded;
  };
  const result = {}; let continued = ""; let pending = false;
  for (let line of data.toString("latin1").split(/\r\n|\r|\n/)) {
    if (!pending && /^[ \t\f]*[#!]/.test(line)) continue;
    if (pending) line = continued + line.replace(/^[ \t\f]+/, "");
    let slash = 0; for (let i = line.length - 1; i >= 0 && line[i] === "\\"; i--) slash++;
    if (slash % 2) { continued = line.slice(0, -1); pending = true; continue; }
    continued = ""; pending = false; line = line.replace(/^[ \t\f]+/, "");
    if (!line || /^[#!]/.test(line)) continue;
    let end = 0; let escaped = false;
    for (; end < line.length; end++) {
      if (!escaped && /[ \t\f=:]/.test(line[end])) break;
      if (!escaped && line[end] === "\\") escaped = true; else escaped = false;
    }
    const key = unescape(line.slice(0, end));
    let rest = line.slice(end).replace(/^[ \t\f]+/, "");
    if (/^[=:]/.test(rest)) rest = rest.slice(1);
    const value = unescape(rest.replace(/^[ \t\f]+/, ""));
    if (["sdk.dir", "ndk.dir", "Pkg.Revision", "warp.version", "warp.version_code"].includes(key)) result[key] = value;
  }
  if (pending) fail("native-properties-incomplete");
  return result;
}

function nativeExecutable(path, name, allowLink = true) {
  try { accessSync(path, constants.X_OK); } catch { fail("native-build-tool-unavailable"); }
  const resolved = realpathSync(path);
  if (!allowLink && resolved !== resolve(path)) fail("arm-local-native-tool-required");
  return { name, invocationPath: resolve(path), ...inputFile(resolved) };
}

function nativeVersionContext(root, env, propertiesFile, properties) {
  const versionHome = env.WARP_VERSION_HOME || root;
  if (!isAbsolute(versionHome) || /[\r\n\0]/.test(versionHome)) fail("absolute-native-version-context-required");
  const needsWarp = !properties["warp.version"] || !properties["warp.version_code"];
  const files = [propertiesFile]; let settings = {};
  if (needsWarp) {
    const warp = inputFile(join(root, "warp.json"), true);
    const version = inputFile(join(versionHome, "version.json"), true);
    files.push(warp, version);
    for (const [file, selected] of [[warp, false], [version, true]]) if (file.present) {
      const data = readFileSync(file.path);
      if (data.length > 1024 * 1024 || sha(data) !== file.sha256) fail("native-runtime-input-changed");
      let value; try { value = JSON.parse(data); } catch { fail("native-version-context-invalid"); }
      if (!value || typeof value !== "object" || Array.isArray(value)) fail("native-version-context-invalid");
      if (selected) settings = value;
    }
  }
  if (properties["warp.version_code"] && !/^-?[0-9]+$/.test(properties["warp.version_code"])) fail("native-version-context-invalid");
  if (settings.stagedVersion != null && typeof settings.stagedVersion !== "string" ||
      settings.stagedVersionCode != null && !Number.isSafeInteger(settings.stagedVersionCode)) fail("native-version-context-invalid");
  return { versionHome: resolve(versionHome), warpHome: root, usesWarp: needsWarp, files,
    versionMode: properties["warp.version"] ? "property" :
      settings.stagedVersion != null && settings.stagedVersion !== "local" ? "version-file" : "clock-host-fallback",
    versionCodeMode: properties["warp.version_code"] ? "property" :
      settings.stagedVersionCode != null ? "version-file" : "clock-fallback" };
}

function nativeNdkInputs(root, env, properties, stripOverride) {
  const gradle = readFileSync(join(root, "android/app/app/build.gradle"), "utf8");
  const versions = [...gradle.matchAll(/^\s*ndkVersion\s*=?\s*['"]([^'"]+)['"]\s*$/gm)];
  if (versions.length !== 1 || /^\s*ndkPath\b/m.test(gradle)) fail("explicit-native-ndk-resolution-required");
  const sdk = properties["sdk.dir"] || env.ANDROID_HOME || env.ANDROID_SDK_ROOT;
  if (!isAbsolute(sdk ?? "") || /[\r\n\0]/.test(sdk)) fail("absolute-native-sdk-context-required");
  if (!properties["sdk.dir"] && env.ANDROID_HOME && env.ANDROID_SDK_ROOT &&
      realpathSync(env.ANDROID_HOME) !== realpathSync(env.ANDROID_SDK_ROOT)) fail("ambiguous-native-sdk-context");
  const writerSelection = properties["ndk.dir"] || join(sdk, "ndk", versions[0][1]);
  if (!isAbsolute(writerSelection)) fail("absolute-native-writer-ndk-required");
  const writerNdk = realpathSync(writerSelection);
  const writerProperties = inputFile(join(writerNdk, "source.properties"));
  if (nativeProperties(writerProperties)["Pkg.Revision"] !== versions[0][1]) fail("native-writer-ndk-version-mismatch");
  const stripSelection = stripOverride || env.ANDROID_NDK_HOME || env.ANDROID_NDK_ROOT || writerNdk;
  if (!isAbsolute(stripSelection)) fail("absolute-native-strip-context-required");
  const stripNdk = realpathSync(stripSelection);
  const objcopy = []; let entries = 0;
  function walk(directory, depth) {
    if (depth > 40) fail("bounded-native-ndk-inventory-required");
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (++entries > 100000) fail("bounded-native-ndk-inventory-required");
      const path = join(directory, entry.name);
      if (entry.isDirectory()) walk(path, depth + 1);
      else if (entry.isFile() && entry.name.toLowerCase() === "llvm-objcopy") objcopy.push(path);
    }
  }
  // Match Makefile's non-following find -type f -iname llvm-objcopy exactly.
  // Refuse ambiguity instead of attesting a different tool from its first match.
  walk(writerNdk, 0);
  const prebuilt = join(stripNdk, "toolchains/llvm/prebuilt");
  const strip = readdirSync(prebuilt).map(name => join(prebuilt, name, "bin/llvm-strip")).filter(existsSync);
  if (objcopy.length !== 1 || strip.length !== 1) fail("one-selected-native-objcopy-and-strip-required");
  return { sdkRoot: realpathSync(sdk), writerNdk, writerVersion: versions[0][1], writerProperties,
    stripNdk, stripOverride: stripOverride || null,
    tools: [nativeExecutable(objcopy[0], "llvm-objcopy"), nativeExecutable(strip[0], "llvm-strip")] };
}

function nativeWarpInputs(root, env, goEnv, dependencies) {
  const hostOS = goEnv.GOHOSTOS; const hostArch = goEnv.GOHOSTARCH;
  if (!["darwin", "linux"].includes(hostOS) || !["amd64", "arm64"].includes(hostArch)) fail("unsupported-native-host-platform");
  const directory = join(root, "warp"); const cwd = join(directory, "warpctl");
  const binary = nativeExecutable(join(cwd, "build", hostOS, hostArch, "warpctl"), "warpctl", false);
  const hostEnv = { ...env, GOOS: hostOS, GOARCH: hostArch, GOWORK: "off" };
  const hostInvoke = invokeFor(dependencies, cwd, hostEnv);
  const invoke = (command, args) => hostInvoke(command === "go" ? join(goEnv.GOROOT, "bin/go") : command, args);
  const effective = JSON.parse(invoke("go", ["env", "-json", "GOMOD", "GOWORK", "GOFLAGS", "GOVERSION",
    "GOROOT", "GOTOOLDIR", "GOEXPERIMENT", "GOOS", "GOARCH", "CGO_ENABLED", "CC", "CXX", "CGO_CFLAGS", "CGO_CPPFLAGS", "CGO_CXXFLAGS", "CGO_LDFLAGS"]));
  if (effective.GOMOD !== join(directory, "go.mod") || effective.GOWORK !== "off" ||
      effective.GOOS !== hostOS || effective.GOARCH !== hostArch ||
      !["GOVERSION", "GOROOT", "GOTOOLDIR", "GOEXPERIMENT", "GOFLAGS"].every(key => effective[key] === goEnv[key])) {
    fail("native-warp-toolchain-context-mismatch");
  }
  const resolution = JSON.parse(invoke("go", ["mod", "edit", "-json"]));
  if (resolution.Module?.Path !== WARP) fail("native-warp-module-context-mismatch");
  const graph = parseJsonStream(invoke("go", ["list", "-mod=readonly", "-m", "-json", "all"])).map(module => {
    if (!module.Path || module.Error) fail("native-warp-module-graph-incomplete");
    return { path: module.Path, version: module.Version ?? null, main: module.Main === true,
      directory: module.Dir ? realpathSync(module.Dir) : null, replacement: module.Replace ? { path: module.Replace.Path,
        version: module.Replace.Version ?? null, directory: module.Replace.Dir ? realpathSync(module.Replace.Dir) : null } : null };
  });
  const rows = parseJsonStream(invoke("go", ["list", "-mod=readonly", "-deps",
    "-json=ImportPath,Dir,Standard,Module," + FILE_FIELDS.join(",") + ",Error,Incomplete", "."]));
  if (!rows.some(row => row.ImportPath === WARP + "/warpctl" && row.Dir === cwd)) fail("native-warp-package-selection-incomplete");
  const modules = new Map(); const code = new Map(); const selections = [];
  for (const row of rows) {
    if (!row.ImportPath || !row.Dir || row.Error || row.Incomplete) fail("native-warp-package-selection-incomplete");
    let module = "std";
    if (!row.Standard) {
      const record = moduleRecord(row.Module); module = record.path;
      if (modules.has(module) && JSON.stringify(modules.get(module)) !== JSON.stringify(record)) fail("native-warp-module-resolution-conflict");
      modules.set(module, record);
    }
    const files = [];
    for (const field of FILE_FIELDS) for (const name of row[field] ?? []) {
      if (typeof name !== "string" || isAbsolute(name) || name.split(/[\\/]/).includes("..")) fail("native-code-path-invalid");
      const path = resolve(row.Dir, name); files.push(path);
      code.set(module + "\0" + path, { module, kind: extname(path) === ".go" ? "go" : field === "EmbedFiles" ? "embed" : "native", ...inputFile(path) });
    }
    selections.push({ importPath: row.ImportPath, module, files: [...new Set(files)].sort() });
  }
  if (modules.get(WARP)?.directory !== directory || !modules.get(WARP)?.main) fail("arm-local-native-warp-source-required");
  const moduleInputs = [];
  for (const module of modules.values()) {
    moduleInputs.push({ module: module.path, kind: "go.mod", ...inputFile(module.goMod) });
    if (module.main || module.replacement && module.replacement.version === null) {
      moduleInputs.push({ module: module.path, kind: "go.sum", ...inputFile(join(module.directory, "go.sum"), true) });
    }
  }
  const repositories = new Map();
  for (const module of modules.values()) if (module.main || module.replacement && module.replacement.version === null) {
    const git = invokeFor(dependencies, module.directory, hostEnv);
    const repo = realpathSync(git("git", ["rev-parse", "--show-toplevel"]).trim());
    if (module.path === WARP && repo !== directory) fail("arm-local-native-warp-source-required");
    const files = [...code.values(), ...moduleInputs].filter(file => file.path.startsWith(repo + sep));
    const names = [...new Set(files.map(file => relative(repo, file.path)))].sort();
    const revision = git("git", ["rev-parse", "HEAD"]).trim();
    const status = git("git", ["status", "--porcelain=v1", "-z", "--untracked-files=all", "--", ...names]);
    const diff = git("git", ["diff", "--binary", "HEAD", "--", ...names]);
    repositories.set(repo, { directory: repo, revision, dirty: Boolean(status), inputCount: names.length,
      dirtyInputHash: sha(status + "\0" + diff + "\0" + (status ? JSON.stringify(sorted(files)) : "")) });
  }
  return { workingDirectory: cwd, binary, goEnvironment: effective, mainResolution: resolution,
    modules: sorted([...modules.values()]), resolvedModuleGraph: sorted(graph), moduleInputs: sorted(moduleInputs),
    codeInputs: sorted([...code.values()]), packageSelections: sorted(selections), repositories: sorted([...repositories.values()]) };
}

function validateNativeHostInputs(root, input) {
  const warp = input.hostTool; const version = input.versionContext; const ndk = input.ndk;
  const hostOS = input.goEnvironment.GOHOSTOS; const hostArch = input.goEnvironment.GOHOSTARCH;
  const regular = file => isAbsolute(file?.path ?? "") && file.present === true &&
    validHash(file.sha256) && Number.isInteger(file.bytes) && file.bytes >= 0;
  const optional = file => regular(file) || isAbsolute(file?.path ?? "") && file.present === false && file.sha256 === null && file.bytes === 0;
  const executable = file => regular(file) && isAbsolute(file.invocationPath ?? "") && typeof file.name === "string";
  if (!warp || !["darwin", "linux"].includes(hostOS) || !["amd64", "arm64"].includes(hostArch) ||
      warp.workingDirectory !== join(root, "warp/warpctl") || !executable(warp.binary) ||
      warp.binary.path !== join(warp.workingDirectory, "build", hostOS, hostArch, "warpctl") ||
      warp.binary.invocationPath !== warp.binary.path || warp.binary.name !== "warpctl" ||
      warp.goEnvironment?.GOMOD !== join(root, "warp/go.mod") || warp.goEnvironment.GOWORK !== "off" ||
      warp.goEnvironment.GOOS !== hostOS || warp.goEnvironment.GOARCH !== hostArch ||
      !["GOVERSION", "GOROOT", "GOTOOLDIR", "GOEXPERIMENT", "GOFLAGS"].every(key => warp.goEnvironment[key] === input.goEnvironment[key]) ||
      warp.mainResolution?.Module?.Path !== WARP) fail("complete-native-warp-context-required");
  for (const key of ["modules", "resolvedModuleGraph", "moduleInputs", "codeInputs", "packageSelections", "repositories"]) {
    if (!Array.isArray(warp[key]) || !warp[key].length) fail("complete-native-warp-closure-required");
  }
  if (!warp.modules.some(module => module.path === WARP && module.main && module.directory === join(root, "warp")) ||
      new Set(warp.modules.map(module => module.path)).size !== warp.modules.length ||
      new Set(warp.resolvedModuleGraph.map(module => module.path)).size !== warp.resolvedModuleGraph.length ||
      !warp.packageSelections.some(pkg => pkg.importPath === WARP + "/warpctl" && pkg.module === WARP)) fail("complete-native-warp-closure-required");
  for (const module of warp.modules) {
    if (!module.path || !isAbsolute(module.directory ?? "") || !isAbsolute(module.goMod ?? "") ||
        !["version", "main", "sum", "goModSum", "replacement"].every(key => Object.hasOwn(module, key)) ||
        typeof module.main !== "boolean" || module.replacement !== null &&
          !["path", "version", "directory", "goMod", "sum", "goModSum"].every(key => Object.hasOwn(module.replacement, key)) ||
        !warp.moduleInputs.some(file => file.module === module.path && file.kind === "go.mod" && file.path === module.goMod && regular(file)) ||
        !warp.resolvedModuleGraph.some(row => row.path === module.path && row.version === module.version && row.main === module.main &&
          row.directory === module.directory && (row.replacement?.path ?? null) === (module.replacement?.path ?? null) &&
          (row.replacement?.version ?? null) === (module.replacement?.version ?? null) &&
          (row.replacement?.directory ?? null) === (module.replacement?.directory ?? null))) fail("complete-native-warp-module-resolution-required");
    if ((module.main || module.replacement && module.replacement.version === null) &&
        !warp.moduleInputs.some(file => file.module === module.path && file.kind === "go.sum" &&
          file.path === join(module.directory, "go.sum") && optional(file))) fail("native-warp-module-sum-state-missing");
    if ((module.main || module.replacement && module.replacement.version === null) &&
        !warp.repositories.some(repo => module.directory === repo.directory || module.directory.startsWith(repo.directory + sep))) fail("native-warp-repository-hashes-required");
  }
  const keys = new Set(warp.codeInputs.map(file => file.module + "\0" + file.path)); const selected = new Set();
  for (const pkg of warp.packageSelections) {
    if (!pkg.importPath || !pkg.module || !Array.isArray(pkg.files)) fail("native-warp-package-selection-incomplete");
    for (const path of pkg.files) selected.add(pkg.module + "\0" + path);
  }
  if (keys.size !== warp.codeInputs.length || keys.size !== selected.size || [...selected].some(key => !keys.has(key)) ||
      warp.codeInputs.some(file => !regular(file) || file.module !== "std" && !warp.modules.some(module => module.path === file.module)) ||
      warp.moduleInputs.some(file => !regular(file) && !(file.kind === "go.sum" && optional(file)))) fail("native-warp-selected-code-hash-missing");
  for (const repo of warp.repositories) if (!isAbsolute(repo.directory ?? "") || !/^[a-f0-9]{40,64}$/.test(repo.revision ?? "") ||
      !validHash(repo.dirtyInputHash) || typeof repo.dirty !== "boolean" || !Number.isInteger(repo.inputCount) || repo.inputCount < 1) fail("native-warp-repository-hashes-required");
  if (!version || !isAbsolute(version.versionHome ?? "") || version.warpHome !== root || typeof version.usesWarp !== "boolean" ||
      !["property", "version-file", "clock-host-fallback"].includes(version.versionMode) ||
      !["property", "version-file", "clock-fallback"].includes(version.versionCodeMode) ||
      !Array.isArray(version.files) || version.files.some(file => !optional(file)) ||
      version.files.length !== (version.usesWarp ? 3 : 1) ||
      version.files[0]?.path !== join(root, "android/app/local.properties") ||
      version.usesWarp !== (version.versionMode !== "property" || version.versionCodeMode !== "property") ||
      version.usesWarp && (version.files[1]?.path !== join(root, "warp.json") || version.files[2]?.path !== join(version.versionHome, "version.json")) ||
      (version.versionMode === "version-file" || version.versionCodeMode === "version-file") && !version.files[2]?.present) fail("complete-native-version-context-required");
  if (!ndk || !isAbsolute(ndk.sdkRoot ?? "") || !isAbsolute(ndk.writerNdk ?? "") || !isAbsolute(ndk.stripNdk ?? "") ||
      typeof ndk.writerVersion !== "string" || !regular(ndk.writerProperties) ||
      ndk.writerProperties.path !== join(ndk.writerNdk, "source.properties") ||
      !Object.hasOwn(ndk, "stripOverride") || ndk.stripOverride !== null && !isAbsolute(ndk.stripOverride) ||
      !Array.isArray(ndk.tools) || ndk.tools.length !== 2 || ndk.tools.some(file => !executable(file)) ||
      !ndk.tools.some(file => file.name === "llvm-objcopy" && file.invocationPath.startsWith(ndk.writerNdk + sep)) ||
      !ndk.tools.some(file => file.name === "llvm-strip" && file.invocationPath.startsWith(ndk.stripNdk + "/toolchains/llvm/prebuilt/"))) fail("complete-native-ndk-context-required");
}

function nativeMobileTools(cwd, env, goEnv, tools, makefile, dependencies) {
  const pin = name => {
    const values = [...makefile.matchAll(new RegExp("^" + name + "\\s*\\?=\\s*(v[^\\s]+)\\s*$", "gm"))];
    if (values.length !== 1) fail("exact-native-mobile-tool-pins-required");
    return values[0][1];
  };
  const versions = { gomobile: pin("GOMOBILE_VERSION"), gobind: pin("GOMOBILE_VERSION"), checksec: pin("CHECKSEC_VERSION") };
  const result = [];
  for (const name of ["gomobile", "gobind", "checksec"]) {
    const tool = tools.find(tool => tool.name === name);
    const module = name === "checksec" ? "github.com/slimm609/checksec/v3" : MOBILE;
    const packagePath = name === "checksec" ? module : module + "/cmd/" + name;
    // The frozen Go driver reads the exact executable, not its self-report.
    const raw = invokeFor(dependencies, cwd, env)(join(goEnv.GOROOT, "bin/go"), ["version", "-m", "-json", tool.path]);
    const info = JSON.parse(raw);
    if (info.GoVersion !== goEnv.GOVERSION || info.Path !== packagePath ||
        info.Main?.Path !== module || info.Main.Version !== versions[name] || info.Main.Replace) {
      fail("native-mobile-tool-build-metadata-mismatch");
    }
    const current = inputFile(tool.path);
    if (current.sha256 !== tool.sha256 || current.bytes !== tool.bytes) fail("native-mobile-tool-changed-during-metadata");
    result.push({ name, path: tool.path, sha256: tool.sha256, requiredModule: module,
      requiredPackage: packagePath, requiredVersion: versions[name], buildInfo: info });
  }
  return result;
}

function validateNativeMobileTools(input) {
  if (!Array.isArray(input.mobileTools) || input.mobileTools.length !== 3) fail("complete-native-mobile-tool-metadata-required");
  for (const name of ["gomobile", "gobind", "checksec"]) {
    const tool = input.tools.find(tool => tool.name === name);
    const row = input.mobileTools.find(row => row.name === name);
    const module = name === "checksec" ? "github.com/slimm609/checksec/v3" : MOBILE;
    const packagePath = name === "checksec" ? module : module + "/cmd/" + name;
    if (!row || row.path !== tool.path || row.sha256 !== tool.sha256 ||
        row.requiredModule !== module || row.requiredPackage !== packagePath || !/^v[^\s]+$/.test(row.requiredVersion ?? "") ||
        row.buildInfo?.GoVersion !== input.goEnvironment.GOVERSION || row.buildInfo.Path !== packagePath ||
        row.buildInfo.Main?.Path !== module || row.buildInfo.Main.Version !== row.requiredVersion ||
        row.buildInfo.Main.Replace) fail("native-mobile-tool-build-metadata-mismatch");
  }
}

export function collectNativeInputs(options, dependencies = {}) {
  if (!validLabel(options["build-owner"]) || !validLabel(options["build-id"]) ||
      !/^(?:0|[1-9][0-9]{0,8})$/.test(String(options["profile-rate"]))) fail("explicit-native-build-context-required");
  const root = realpathSync(options.root); const cwd = join(root, "sdk/build");
  // Match the Makefile's tool PATH and Android build context. Metadata lookup is
  // read-only/offline: missing setup dependencies fail instead of downloading.
  const env = { ...(dependencies.env ?? process.env) };
  env.PATH = `${env.PATH ?? ""}:/usr/local/go/bin:${env.HOME ?? ""}/go/bin`;
  env.GOPROXY = "off"; env.GOSUMDB = "off"; env.GOEXPERIMENT = "greenteagc";
  if (env.GOFLAGS && !env.GOFLAGS.split(/\s+/).every(flag => ["-mod=readonly", "-trimpath", "-buildvcs=false"].includes(flag))) {
    fail("unsupported-native-goflags");
  }
  const invoke = invokeFor(dependencies, cwd, env);
  const goEnv = JSON.parse(invoke("go", ["env", "-json", "GOMOD", "GOWORK", "GOFLAGS", "GOVERSION", "GOROOT", "GOTOOLDIR", "GOEXPERIMENT", "GOHOSTOS", "GOHOSTARCH"]));
  if (goEnv.GOMOD !== join(cwd, "go.mod") || !goEnv.GOVERSION || !goEnv.GOROOT || !goEnv.GOTOOLDIR) fail("native-build-module-context-mismatch");
  if (goEnv.GOFLAGS && !goEnv.GOFLAGS.split(/\s+/).every(flag => ["-mod=readonly", "-trimpath", "-buildvcs=false"].includes(flag))) {
    fail("unsupported-native-goflags");
  }
  const makefile = readFileSync(join(cwd, "Makefile"), "utf8");
  const androidRule = makefile.split("_build_android:")[1]?.split("build_ios:")[0];
  if (!androidRule || !androidRule.includes("-target android/arm64,android/arm,android/amd64") ||
      !androidRule.includes("-tags sdk_mobile_bind") || !androidRule.includes("-androidapi 24") ||
      !androidRule.includes("export GOEXPERIMENT=greenteagc")) fail("unsupported-native-build-policy");
  const tools = ["go", "gomobile", "gobind", "checksec"].map(name => ({ name, ...inputFile((dependencies.toolPath ?? toolPath)(name, env)) }));
  for (const name of ["compile", "link", "asm", "cgo"]) tools.push({ name, ...inputFile(join(goEnv.GOTOOLDIR, name)) });
  const mobileTools = nativeMobileTools(cwd, env, goEnv, tools, makefile, dependencies);
  const buildInputs = [join(cwd, "Makefile"), join(cwd, "sdk-android-output-lock.sh"), join(cwd, "gomobile-bind-retry.sh"), join(root, "sdk/build-android.sh"),
    join(root, "android/app/app/build.gradle"), SELF, join(dirname(SELF), "physical_artifact_directory.mjs")].map(path => inputFile(path));
  const propertiesFile = inputFile(join(root, "android/app/local.properties"), true);
  const properties = nativeProperties(propertiesFile);
  const versionContext = nativeVersionContext(root, env, propertiesFile, properties);
  const ndk = nativeNdkInputs(root, env, properties, options["strip-ndk"]);
  const hostTool = nativeWarpInputs(root, env, goEnv, dependencies);
  const workspace = { mode: goEnv.GOWORK === "off" ? "off" : goEnv.GOWORK ? "active" : "none", file: null, sum: null, resolution: null };
  if (workspace.mode === "active") {
    workspace.file = inputFile(goEnv.GOWORK); workspace.sum = inputFile(`${goEnv.GOWORK}.sum`, true);
    workspace.resolution = JSON.parse(invoke("go", ["work", "edit", "-json", goEnv.GOWORK]));
  }
  const mainResolution = JSON.parse(invoke("go", ["mod", "edit", "-json"]));
  if (mainResolution.Module?.Path !== `${SDK}/build` || !Array.isArray(mainResolution.Replace)) fail("native-build-module-context-mismatch");
  const modules = new Map();
  for (const module of parseJsonStream(invoke("go", ["list", "-mod=readonly", "-m", "-json"]))) {
    const record = moduleRecord(module); modules.set(record.path, record);
  }
  const code = new Map(); const packages = []; const resolvedModuleGraph = [];
  for (const abi of ABIS) {
    const targetInvoke = invokeFor(dependencies, cwd, { ...env, GOOS: "android", GOARCH: abi, CGO_ENABLED: "1" });
    // gomobile constructs its generated module from this complete graph, not
    // just the package closure. Unused, undownloaded modules can have no Dir;
    // record that explicitly, while hashing go.mod for every compiled module.
    const graph = parseJsonStream(targetInvoke("go", ["list", "-mod=readonly", "-m", "-json", `-tags=${TAG}`, "all"])).map(module => {
      if (!module.Path || module.Error) fail("native-module-graph-incomplete");
      return { path: module.Path, version: module.Version ?? null, main: module.Main === true, directory: module.Dir ?? null,
        replacement: module.Replace ? { path: module.Replace.Path, version: module.Replace.Version ?? null,
          directory: module.Replace.Dir ?? null } : null };
    });
    resolvedModuleGraph.push({ abi, modules: sorted(graph) });
    const rows = parseJsonStream(targetInvoke("go",
      ["list", "-mod=readonly", "-deps", `-json=ImportPath,Dir,Standard,Module,${FILE_FIELDS.join(",")},Error,Incomplete`,
        `-tags=${TAG}`, SDK, ...BRIDGE_PACKAGES]));
    if (![SDK, CONNECT, ...BRIDGE_PACKAGES].every(id => rows.some(row => row.ImportPath === id))) fail("native-package-selection-incomplete");
    const selections = [];
    for (const row of rows) {
      if (row.Error || row.Incomplete || !row.Dir || !row.ImportPath) fail("native-package-metadata-incomplete");
      let modulePath = "std";
      if (!row.Standard) {
        const record = moduleRecord(row.Module); modulePath = record.path;
        if (modules.has(record.path) && JSON.stringify(modules.get(record.path)) !== JSON.stringify(record)) fail("native-module-resolution-conflict");
        modules.set(record.path, record);
      }
      const files = [];
      for (const field of FILE_FIELDS) for (const name of row[field] ?? []) {
        if (typeof name !== "string" || isAbsolute(name) || name.split(/[\\/]/).includes("..")) fail("native-code-path-invalid");
        const path = resolve(row.Dir, name); files.push(path);
        const key = `${modulePath}\0${path}`;
        if (!code.has(key)) code.set(key, { module: modulePath, kind: extname(path) === ".go" ? "go" : field === "EmbedFiles" ? "embed" : "native",
          ...inputFile(path) });
      }
      selections.push({ importPath: row.ImportPath, module: modulePath, files: [...new Set(files)].sort() });
    }
    packages.push({ abi, selections: sorted(selections) });
  }
  // The Makefile also queries the build package's DefaultGODEBUG and runs the
  // export-contract command. Include their selected source, not test fixtures
  // or generated build/android output (which belongs to binary attestation).
  const buildPackages = parseJsonStream(invoke("go", ["list", "-mod=readonly",
    `-json=ImportPath,Dir,Module,${FILE_FIELDS.join(",")},Error,Incomplete`, ".", "./cmd/mobileexports"])).map(row => {
    if (row.Error || row.Incomplete || !row.Dir || row.Module?.Path !== `${SDK}/build`) fail("native-build-package-selection-incomplete");
    const files = [];
    for (const field of FILE_FIELDS) for (const name of row[field] ?? []) {
      if (typeof name !== "string" || isAbsolute(name) || name.split(/[\\/]/).includes("..")) fail("native-code-path-invalid");
      const path = resolve(row.Dir, name); files.push(path);
      code.set(`${SDK}/build\0${path}`, { module: `${SDK}/build`, kind: extname(path) === ".go" ? "go" :
        field === "EmbedFiles" ? "embed" : "native", ...inputFile(path) });
    }
    return { importPath: row.ImportPath, module: `${SDK}/build`, files: [...new Set(files)].sort() };
  });
  for (const [id, path] of [[SDK, join(root, "sdk")], [CONNECT, join(root, "connect")], [`${SDK}/build`, cwd]]) {
    if (modules.get(id)?.directory !== realpathSync(path)) fail("native-required-replacement-mismatch");
  }
  const bridgeInputs = BRIDGE_FILES.map(name => ({ module: MOBILE, ...inputFile(join(modules.get(MOBILE).directory, name)) }));
  const moduleInputs = [];
  for (const module of modules.values()) {
    moduleInputs.push({ module: module.path, kind: "go.mod", ...inputFile(module.goMod) });
    const local = module.main || module.replacement && module.replacement.version === null;
    if (local) moduleInputs.push({ module: module.path, kind: "go.sum", ...inputFile(join(module.directory, "go.sum"), true) });
  }
  const localModules = [...modules.values()].filter(module => module.main || module.replacement && module.replacement.version === null);
  const repoInputs = new Map();
  for (const directory of [...localModules.map(module => module.directory), join(root, "android")]) {
    const git = invokeFor(dependencies, directory, env);
    const repository = realpathSync(git("git", ["rev-parse", "--show-toplevel"]).trim());
    if (!repoInputs.has(repository)) repoInputs.set(repository, []);
  }
  for (const input of [...code.values(), ...moduleInputs, ...buildInputs, ...bridgeInputs, ...[workspace.file, workspace.sum].filter(Boolean)]) {
    for (const [repository, files] of repoInputs) if (input.path.startsWith(`${repository}${sep}`)) files.push(input);
  }
  const repositories = [];
  for (const [repository, files] of repoInputs) {
    const git = invokeFor(dependencies, repository, env); const revision = git("git", ["rev-parse", "HEAD"]).trim();
    if (!/^[a-f0-9]{40,64}$/.test(revision)) fail("native-repository-revision-required");
    const names = [...new Set(files.map(file => relative(repository, file.path)))].sort();
    if (!names.length) fail("native-repository-inputs-missing");
    const status = git("git", ["status", "--porcelain=v1", "-z", "--untracked-files=all", "--", ...names]);
    const diff = git("git", ["diff", "--binary", "HEAD", "--", ...names]);
    repositories.push({ directory: repository, revision, dirty: Boolean(status), inputCount: names.length,
      dirtyInputHash: sha(status + "\0" + diff + "\0" + (status ? JSON.stringify(sorted(files)) : "")) });
  }
  const inputs = { hostTool, versionContext, ndk, mobileTools, workingDirectory: cwd, targets: ABIS, tags: [TAG], goEnvironment: goEnv, tools: sorted(tools),
    buildInputs: sorted(buildInputs), bridgeInputs: sorted(bridgeInputs), workspace, mainResolution, modules: sorted([...modules.values()]),
    moduleInputs: sorted(moduleInputs), codeInputs: sorted([...code.values()]), packageSelections: packages, resolvedModuleGraph,
    buildPackages: sorted(buildPackages), repositories: sorted(repositories) };
  const manifest = { type: "physical-native-inputs", schemaVersion: 1, phase: options.phase,
    capturedAtUnixMs: (dependencies.now ?? Date.now)(), root, buildOwner: options["build-owner"], buildId: options["build-id"],
    profileRate: Number(options["profile-rate"]), inputs };
  manifest.inputHash = fingerprint(manifest); validateNativeManifest(manifest);
  return manifest;
}

export function validateNativeManifest(manifest) {
  const input = manifest?.inputs;
  if (manifest?.type !== "physical-native-inputs" || manifest.schemaVersion !== 1 || !["before", "after"].includes(manifest.phase) ||
      !validLabel(manifest.buildOwner) || !validLabel(manifest.buildId) || !isAbsolute(manifest.root ?? "") ||
      !Number.isFinite(manifest.capturedAtUnixMs) || !Number.isInteger(manifest.profileRate) || manifest.profileRate < 0 ||
      !input || !validHash(manifest.inputHash)) fail("complete-native-input-manifest-required");
  for (const key of ["tools", "buildInputs", "bridgeInputs", "modules", "moduleInputs", "codeInputs", "packageSelections", "resolvedModuleGraph", "buildPackages", "repositories"]) {
    if (!Array.isArray(input[key]) || !input[key].length) fail("complete-native-input-manifest-required");
  }
  if (input.workingDirectory !== join(manifest.root, "sdk/build") || JSON.stringify(input.targets) !== JSON.stringify(ABIS) || JSON.stringify(input.tags) !== JSON.stringify([TAG]) ||
      input.goEnvironment?.GOMOD !== join(input.workingDirectory, "go.mod") || !input.goEnvironment.GOVERSION ||
      !Object.hasOwn(input.goEnvironment, "GOWORK") || input.goEnvironment.GOEXPERIMENT !== "greenteagc" ||
      input.mainResolution?.Module?.Path !== `${SDK}/build` || !Array.isArray(input.mainResolution.Replace) ||
      !["none", "off", "active"].includes(input.workspace?.mode) ||
      ![SDK, CONNECT, `${SDK}/build`, MOBILE].every(id => input.modules.some(module => module.path === id))) fail("complete-native-module-resolution-required");
  if (input.tools.length !== TOOL_NAMES.length || !TOOL_NAMES.every(name => input.tools.some(tool => tool.name === name)) ||
      ![join(input.workingDirectory, "Makefile"), join(input.workingDirectory, "sdk-android-output-lock.sh"), join(input.workingDirectory, "gomobile-bind-retry.sh"),
        join(manifest.root, "sdk/build-android.sh"), join(manifest.root, "android/app/app/build.gradle"), SELF,
        join(dirname(SELF), "physical_artifact_directory.mjs")]
        .every(path => input.buildInputs.some(file => file.path === path))) fail("native-build-input-hashes-missing");
  for (const file of [...input.tools, ...input.buildInputs, ...input.bridgeInputs, ...input.moduleInputs, ...input.codeInputs]) {
    if (!isAbsolute(file.path ?? "") || typeof file.present !== "boolean" || !Number.isInteger(file.bytes) || file.bytes < 0 ||
        (file.present ? !validHash(file.sha256) : file.sha256 !== null || file.bytes !== 0) ||
        !file.present && file.kind !== "go.sum") fail("complete-native-file-hashes-required");
  }
  const mobileDir = input.modules.find(module => module.path === MOBILE).directory;
  if (input.bridgeInputs.length !== BRIDGE_FILES.length || !BRIDGE_FILES.every(name =>
    input.bridgeInputs.some(file => file.module === MOBILE && file.path === join(mobileDir, name)))) fail("native-bridge-input-hashes-missing");
  for (const id of [SDK, CONNECT, `${SDK}/build`]) {
    if (!input.codeInputs.some(file => file.module === id && file.kind === "go")) fail("native-go-input-hashes-missing");
  }
  for (const module of input.modules) {
    if (!module.path || !isAbsolute(module.directory ?? "") || !isAbsolute(module.goMod ?? "") ||
        !["version", "main", "sum", "goModSum", "replacement"].every(key => Object.hasOwn(module, key)) ||
        typeof module.main !== "boolean" || (module.replacement !== null &&
          !["path", "version", "directory", "goMod", "sum", "goModSum"].every(key => Object.hasOwn(module.replacement, key))) ||
        !input.moduleInputs.some(file => file.module === module.path && file.kind === "go.mod" && file.path === module.goMod)) {
      fail("complete-native-module-resolution-required");
    }
    if ((module.main || module.replacement && module.replacement.version === null) &&
        !input.moduleInputs.some(file => file.module === module.path && file.kind === "go.sum" &&
          file.path === join(module.directory, "go.sum"))) fail("native-module-sum-state-missing");
  }
  if (new Set(input.modules.map(module => module.path)).size !== input.modules.length) fail("native-module-resolution-conflict");
  for (const repo of input.repositories) if (!isAbsolute(repo.directory ?? "") || !/^[a-f0-9]{40,64}$/.test(repo.revision ?? "") ||
      !validHash(repo.dirtyInputHash) || typeof repo.dirty !== "boolean" || !Number.isInteger(repo.inputCount) || repo.inputCount < 1) fail("native-repository-hashes-required");
  const workspaceMode = input.goEnvironment.GOWORK === "off" ? "off" : input.goEnvironment.GOWORK ? "active" : "none";
  if (input.workspace.mode !== workspaceMode || (workspaceMode === "active" ?
      !input.workspace.resolution || input.workspace.file?.path !== input.goEnvironment.GOWORK ||
      !input.workspace.file.present || !validHash(input.workspace.file.sha256) ||
      input.workspace.sum?.path !== `${input.goEnvironment.GOWORK}.sum` ||
      typeof input.workspace.sum.present !== "boolean" ||
      (input.workspace.sum.present ? !validHash(input.workspace.sum.sha256) : input.workspace.sum.sha256 !== null) :
      input.workspace.file !== null || input.workspace.sum !== null || input.workspace.resolution !== null)) fail("native-workspace-inputs-missing");
  for (const abi of ABIS) if (!input.packageSelections.some(selection => selection.abi === abi && Array.isArray(selection.selections) &&
      [SDK, CONNECT, ...BRIDGE_PACKAGES].every(id => selection.selections.some(pkg => pkg.importPath === id)))) fail("native-package-selection-incomplete");
  if (input.packageSelections.length !== ABIS.length || ![`${SDK}/build`, `${SDK}/build/cmd/mobileexports`]
      .every(id => input.buildPackages.some(pkg => pkg.importPath === id && pkg.module === `${SDK}/build`))) fail("native-build-package-selection-incomplete");
  if (input.resolvedModuleGraph.length !== ABIS.length) fail("native-module-graph-incomplete");
  for (const abi of ABIS) {
    const graph = input.resolvedModuleGraph.find(row => row.abi === abi)?.modules;
    if (!Array.isArray(graph) || new Set(graph.map(module => module.path)).size !== graph.length ||
        graph.some(module => !module.path || !["version", "main", "directory", "replacement"].every(key => Object.hasOwn(module, key))) ||
        !input.modules.every(module => graph.some(row => row.path === module.path && row.version === module.version &&
          row.main === module.main && (row.replacement?.path ?? null) === (module.replacement?.path ?? null)))) fail("native-module-graph-incomplete");
  }
  const selected = [...input.packageSelections.flatMap(row => row.selections), ...input.buildPackages];
  const codeKeys = new Set(input.codeInputs.map(file => `${file.module}\0${file.path}`));
  const selectedKeys = new Set();
  for (const pkg of selected) {
    if (!pkg.importPath || !pkg.module || !Array.isArray(pkg.files)) fail("native-package-selection-incomplete");
    for (const path of pkg.files) {
      const key = `${pkg.module}\0${path}`; selectedKeys.add(key);
      if (!codeKeys.has(key)) fail("native-selected-code-hash-missing");
    }
  }
  if (codeKeys.size !== input.codeInputs.length || selectedKeys.size !== codeKeys.size) fail("native-code-selection-mismatch");
  for (const directory of [join(manifest.root, "android"), ...input.modules
    .filter(module => module.main || module.replacement && module.replacement.version === null).map(module => module.directory)]) {
    if (!input.repositories.some(repo => directory === repo.directory || directory.startsWith(`${repo.directory}${sep}`))) {
      fail("native-repository-hashes-required");
    }
  }
  validateNativeHostInputs(manifest.root, input);
  validateNativeMobileTools(input);
  if (fingerprint(manifest) !== manifest.inputHash) fail("native-input-fingerprint-mismatch");
  return true;
}

function compare(before, after) {
  validateNativeManifest(before); validateNativeManifest(after);
  if (before.phase !== "before" || after.phase !== "after" || after.capturedAtUnixMs < before.capturedAtUnixMs ||
      before.buildOwner !== after.buildOwner || before.buildId !== after.buildId || before.inputHash !== after.inputHash) fail("native-inputs-changed-across-build");
}

function requireCurrent(after, dependencies) {
  const current = collectNativeInputs({ root: after.root, phase: "after", "build-owner": after.buildOwner,
    "build-id": after.buildId, "profile-rate": String(after.profileRate), "strip-ndk": after.inputs.ndk.stripOverride }, dependencies);
  if (current.inputHash !== after.inputHash) fail("native-inputs-changed-after-build");
  const sidecar = join(after.root, "sdk/build/android/.build-owner");
  const file = lstatSync(sidecar);
  if (!file.isFile() || file.isSymbolicLink() || file.uid !== process.getuid() || (file.mode & 0o777) !== 0o600 || file.size > 256 ||
      readFileSync(sidecar, "utf8").trim() !== after.buildOwner) fail("native-build-owner-mismatch");
}

export function requireVerifiedNativeInputs(path, buildId, dependencies = {}) {
  const proof = privateRead(path);
  if (proof.type !== "physical-native-input-verification" || proof.schemaVersion !== 1 || proof.eligible !== true ||
      proof.classification !== "NATIVE_INPUTS_VERIFIED" || proof.buildId !== buildId || !validHash(proof.inputHash) ||
      !validHash(proof.beforeSha256) || !validHash(proof.afterSha256)) fail("verified-native-input-proof-required");
  const before = privateRead(proof.before); const after = privateRead(proof.after); compare(before, after);
  if (inputFile(proof.before).sha256 !== proof.beforeSha256 || inputFile(proof.after).sha256 !== proof.afterSha256 ||
      after.buildId !== buildId || after.buildOwner !== proof.buildOwner || after.inputHash !== proof.inputHash) fail("native-input-proof-replaced");
  requireCurrent(after, dependencies);
  return { buildId, inputHash: after.inputHash, buildOwner: after.buildOwner };
}

export function verifyNativeInputs(options, dependencies = {}) {
  const binding = outputBinding(options.output);
  const before = privateRead(options.before); const after = privateRead(options.after); compare(before, after); requireCurrent(after, dependencies);
  const report = { type: "physical-native-input-verification", schemaVersion: 1, eligible: true,
    classification: "NATIVE_INPUTS_VERIFIED", buildId: after.buildId, buildOwner: after.buildOwner, inputHash: after.inputHash,
    before: realpathSync(options.before), after: realpathSync(options.after), beforeSha256: inputFile(options.before).sha256,
    afterSha256: inputFile(options.after).sha256, verifiedAtUnixMs: (dependencies.now ?? Date.now)() };
  dependencies.beforePublish?.();
  writePrivate(options.output, report, binding); return report;
}

export function nativeWriterArguments(buildId, profileRate, memoryProfile, maxWorkers) {
  if (typeof buildId !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(buildId) || !Number.isSafeInteger(profileRate) || profileRate < 0 ||
      !WRITER_MEMORY_PROFILES.includes(memoryProfile) || !Number.isInteger(maxWorkers) || maxWorkers < 1 ||
      maxWorkers > Math.ceil(availableParallelism() * 0.7)) fail("explicit-native-writer-profile-required");
  return [":app:buildSdkAcceptance", "--max-workers", String(maxWorkers),
    `-PurnetworkAcceptanceBuildId=${buildId}`, `-PurnetworkMemoryProfile=${memoryProfile}`,
    `-PurnetworkMemoryProfileRateBytes=${profileRate}`];
}

export function nativeWriterSourceHashes() {
  return ["physical_native_writer.sh", "physical_native_writer.mjs", "physical_native_provenance.mjs", "physical_artifact_directory.mjs"].map(name =>
    ({ name, sha256: inputFile(join(dirname(SELF), name)).sha256 }));
}

export function requireNativeBeforeWriter(options, dependencies = {}) {
  const before = privateRead(options.before); validateNativeManifest(before);
  if (before.phase !== "before" || before.root !== realpathSync(options.root) || before.buildId !== options["build-id"] ||
      before.profileRate !== Number(options["profile-rate"])) fail("native-writer-before-context-mismatch");
  const current = collectNativeInputs({ root: before.root, phase: "before", "build-owner": before.buildOwner,
    "build-id": before.buildId, "profile-rate": String(before.profileRate), "strip-ndk": before.inputs.ndk.stripOverride }, dependencies);
  if (current.inputHash !== before.inputHash) fail("native-inputs-changed-before-writer");
  return before;
}

export function requireNativeWriterReceipt(options, before) {
  const receipt = privateRead(options["writer-receipt"]);
  const valid = receipt.type === "physical-native-writer" && receipt.schemaVersion === 1 && receipt.state === "terminal" &&
    receipt.eligible === true && receipt.childStarted === true && receipt.childExitCode === 0 && receipt.signal === null &&
    receipt.interrupted === false && receipt.root === before.root && receipt.buildId === before.buildId &&
    receipt.buildOwner === before.buildOwner && receipt.profileRate === before.profileRate && receipt.inputHash === before.inputHash &&
    receipt.before === realpathSync(options.before) && receipt.beforeSha256 === inputFile(options.before).sha256 &&
    WRITER_MEMORY_PROFILES.includes(receipt.memoryProfile) && Number.isFinite(receipt.startedAtUnixMs) &&
    Number.isFinite(receipt.completedAtUnixMs) && receipt.startedAtUnixMs >= before.capturedAtUnixMs &&
    receipt.completedAtUnixMs >= receipt.startedAtUnixMs && receipt.workingDirectory === join(before.root, "android/app");
  if (!valid) fail("successful-native-writer-receipt-required");
  if (JSON.stringify(receipt.arguments) !== JSON.stringify(nativeWriterArguments(before.buildId, before.profileRate,
      receipt.memoryProfile, receipt.maxWorkers)) || JSON.stringify(receipt.sourceHashes) !== JSON.stringify(nativeWriterSourceHashes())) {
    fail("native-writer-receipt-context-mismatch");
  }
  const binding = outputBinding(options["writer-receipt"]);
  for (const log of [receipt.stdout, receipt.stderr]) {
    try {
      requireArtifactPaths(binding, [log.path]); const file = lstatSync(log.path);
      if (!file.isFile() || file.isSymbolicLink() || file.uid !== process.getuid() || (file.mode & 0o777) !== 0o600) fail("native-writer-log-binding-mismatch");
      const current = inputFile(log.path);
      if (current.sha256 !== log.sha256 || current.bytes !== log.bytes) fail("native-writer-log-binding-mismatch");
    } catch { fail("native-writer-log-binding-mismatch"); }
  }
  return receipt;
}

// Reuse the existing SDK kernel-lock verifier, including descriptor/inode and
// token checks. Environment markers or a lock-file pathname alone are not proof.
// Preserve only the selected descriptor across spawn; never retain raw output.
export function requireNativeConsumerLock(root, dependencies = {}) {
  const env = dependencies.lockEnv ?? process.env;
  const fd = Number(env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_FD);
  const directory = join(realpathSync(root), "sdk/build");
  const lockPath = join(directory, ".android-output.lock");
  if (env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_HELD !== "1" || env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_ROLE !== "physical-native-consumer" ||
      env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_DIR !== directory || env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_PATH !== lockPath ||
      !Number.isInteger(fd) || fd < 10 || fd > 255) fail("native-consumer-lock-required");
  try {
    const file = lstatSync(lockPath); const descriptor = fstatSync(fd);
    if (!file.isFile() || file.isSymbolicLink() || file.uid !== process.getuid() || (file.mode & 0o777) !== 0o600 || file.size > 4096 ||
        file.dev !== descriptor.dev || file.ino !== descriptor.ino ||
        !/^role=physical-native-consumer$/m.test(readFileSync(lockPath, "utf8"))) fail("native-consumer-lock-required");
    const stdio = Array(fd + 1).fill("ignore"); stdio[1] = "pipe"; stdio[2] = "pipe"; stdio[fd] = fd;
    const result = (dependencies.lockInvoke ?? spawnSync)("bash", [join(directory, "sdk-android-output-lock.sh"), "--verify-held"],
      { cwd: directory, env, stdio, encoding: "utf8", timeout: 5000, maxBuffer: 4096 });
    if (result.status !== 0 || result.signal || result.error) fail("native-consumer-lock-required");
  } catch { fail("native-consumer-lock-required"); }
  return true;
}

// One mandatory post-writer operation. The consumer wrapper cannot invoke its
// assembly/copy/linkage command unless this creates both fresh artifacts and
// verifies the original before manifest, current sources and native build owner.
export function prepareNativeConsumer(options, dependencies = {}) {
  const binding = outputBinding(options.output);
  try { requireArtifactPaths(binding, [options.before, options.after, options.output, options["writer-receipt"]]); }
  catch (error) { fail(artifactDirectoryReason(error)); }
  const before = privateRead(options.before); validateNativeManifest(before);
  if (before.phase !== "before" || before.root !== realpathSync(options.root)) fail("native-consumer-before-context-mismatch");
  (dependencies.writerReceipt ?? requireNativeWriterReceipt)(options, before);
  if (new Set([options.before, options.after, options.output].map(path => resolve(path))).size !== 3 ||
      existsSync(options.after) || existsSync(options.output)) fail("fresh-native-consumer-artifacts-required");
  const checkLock = () => (dependencies.consumerLock ?? requireNativeConsumerLock)(before.root);
  checkLock();
  const after = collectNativeInputs({ root: before.root, phase: "after", "build-owner": before.buildOwner,
    "build-id": before.buildId, "profile-rate": String(before.profileRate), "strip-ndk": before.inputs.ndk.stripOverride }, dependencies);
  checkLock(); writePrivate(options.after, after, binding);
  return verifyNativeInputs(options, { ...dependencies, beforePublish: checkLock });
}

export function parseArgs(argv) {
  const [mode, ...args] = argv;
  const keys = mode === "capture" ? ["phase", "root", "build-owner", "build-id", "profile-rate", "output"] :
    mode === "verify" ? ["before", "after", "output"] : mode === "check" ? ["proof", "build-id"] :
      mode === "prepare-consumer" ? ["root", "before", "after", "output", "writer-receipt"] : [];
  if (!keys.length) fail("native-capture-verify-or-check-required");
  const options = { mode };
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i]?.slice(2);
    if (!args[i]?.startsWith("--") || !(keys.includes(key) || mode === "capture" && key === "strip-ndk") ||
        options[key] !== undefined || !args[i + 1] || args[i + 1].startsWith("--")) {
      fail("explicit-native-manifest-arguments-required");
    }
    options[key] = args[i + 1];
  }
  if (keys.some(key => options[key] === undefined) || mode === "capture" && !["before", "after"].includes(options.phase)) fail("explicit-native-manifest-arguments-required");
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    if (options.mode === "capture") {
      const binding = outputBinding(options.output); writePrivate(options.output, collectNativeInputs(options), binding);
    } else if (options.mode === "verify") verifyNativeInputs(options);
    else if (options.mode === "prepare-consumer") prepareNativeConsumer(options);
    else requireVerifiedNativeInputs(options.proof, options["build-id"]);
    process.stdout.write(`${JSON.stringify({ eligible: true, classification: options.mode === "capture" ? "NATIVE_INPUTS_CAPTURED" : "NATIVE_INPUTS_VERIFIED" })}\n`);
  } catch (error) {
    process.stderr.write(`native input provenance failed: ${reason(error)}${process.argv[2] === "prepare-consumer" ? "-no-consumer-spawn" : ""}\n`); process.exitCode = 2;
  }
}
