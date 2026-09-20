#!/usr/bin/env node

// Source linkage only: this never builds, installs, reads credentials or changes
// module files. Binary/AAR/APK/strip/consumer-lock attestation remains separate.
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { accessSync, closeSync, constants, existsSync, fstatSync, lstatSync, openSync, readFileSync,
  readSync, realpathSync, writeFileSync } from "node:fs";
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
const TOOL_NAMES = ["go", "gomobile", "gobind", "compile", "link", "asm", "cgo"];
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
  const goEnv = JSON.parse(invoke("go", ["env", "-json", "GOMOD", "GOWORK", "GOFLAGS", "GOVERSION", "GOROOT", "GOTOOLDIR", "GOEXPERIMENT"]));
  if (goEnv.GOMOD !== join(cwd, "go.mod") || !goEnv.GOVERSION || !goEnv.GOROOT || !goEnv.GOTOOLDIR) fail("native-build-module-context-mismatch");
  if (goEnv.GOFLAGS && !goEnv.GOFLAGS.split(/\s+/).every(flag => ["-mod=readonly", "-trimpath", "-buildvcs=false"].includes(flag))) {
    fail("unsupported-native-goflags");
  }
  const makefile = readFileSync(join(cwd, "Makefile"), "utf8");
  const androidRule = makefile.split("_build_android:")[1]?.split("build_ios:")[0];
  if (!androidRule || !androidRule.includes("-target android/arm64,android/arm,android/amd64") ||
      !androidRule.includes("-tags sdk_mobile_bind") || !androidRule.includes("-androidapi 24") ||
      !androidRule.includes("export GOEXPERIMENT=greenteagc")) fail("unsupported-native-build-policy");
  const tools = ["go", "gomobile", "gobind"].map(name => ({ name, ...inputFile((dependencies.toolPath ?? toolPath)(name, env)) }));
  for (const name of ["compile", "link", "asm", "cgo"]) tools.push({ name, ...inputFile(join(goEnv.GOTOOLDIR, name)) });
  const buildInputs = [join(cwd, "Makefile"), join(cwd, "sdk-android-output-lock.sh"), join(root, "sdk/build-android.sh"),
    join(root, "android/app/app/build.gradle"), SELF].map(path => inputFile(path));
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
  const inputs = { workingDirectory: cwd, targets: ABIS, tags: [TAG], goEnvironment: goEnv, tools: sorted(tools),
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
      ![join(input.workingDirectory, "Makefile"), join(input.workingDirectory, "sdk-android-output-lock.sh"),
        join(manifest.root, "sdk/build-android.sh"), join(manifest.root, "android/app/app/build.gradle"), SELF]
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
    "build-id": after.buildId, "profile-rate": String(after.profileRate) }, dependencies);
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
      memoryProfile !== "ios-memory-audit-v1" || !Number.isInteger(maxWorkers) || maxWorkers < 1 ||
      maxWorkers > Math.ceil(availableParallelism() * 0.7)) fail("explicit-native-writer-profile-required");
  return [":app:buildSdkAcceptance", "--max-workers", String(maxWorkers),
    `-PurnetworkAcceptanceBuildId=${buildId}`, `-PurnetworkMemoryProfile=${memoryProfile}`,
    `-PurnetworkMemoryProfileRateBytes=${profileRate}`];
}

export function nativeWriterSourceHashes() {
  return ["physical_native_writer.sh", "physical_native_writer.mjs", "physical_native_provenance.mjs"].map(name =>
    ({ name, sha256: inputFile(join(dirname(SELF), name)).sha256 }));
}

export function requireNativeBeforeWriter(options, dependencies = {}) {
  const before = privateRead(options.before); validateNativeManifest(before);
  if (before.phase !== "before" || before.root !== realpathSync(options.root) || before.buildId !== options["build-id"] ||
      before.profileRate !== Number(options["profile-rate"])) fail("native-writer-before-context-mismatch");
  const current = collectNativeInputs({ root: before.root, phase: "before", "build-owner": before.buildOwner,
    "build-id": before.buildId, "profile-rate": String(before.profileRate) }, dependencies);
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
    receipt.memoryProfile === "ios-memory-audit-v1" && Number.isFinite(receipt.startedAtUnixMs) &&
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
// assembly/copy/linkage command unless this creates BOTH fresh artifacts and
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
    "build-id": before.buildId, "profile-rate": String(before.profileRate) }, dependencies);
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
    if (!args[i]?.startsWith("--") || !keys.includes(key) || options[key] !== undefined || !args[i + 1] || args[i + 1].startsWith("--")) {
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
