#!/usr/bin/env node

// No config/serial inputs and no adb. Exercise the actual shared reader only
// with private, generated synthetic documents. Raw subprocess output never
// leaves memory; only source/tool identities and fixed outcomes are persisted.
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { accessSync, closeSync, constants, existsSync, lstatSync, mkdtempSync, openSync,
  readFileSync, readSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { delimiter, dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";

const SELF = fileURLToPath(import.meta.url);
const HELPER = fileURLToPath(new URL("./physical_credentials.mjs", import.meta.url));
const READER = fileURLToPath(new URL("../../../tests/read-tests-config.sh", import.meta.url));
const ARTIFACT_HELPER = fileURLToPath(new URL("./physical_artifact_directory.mjs", import.meta.url));
const MAX_OUTPUT = 64 * 1024;
class PreflightError extends Error {}
const fail = (reason) => { throw new PreflightError(reason); };
const checkNames = (override) => ["node-version", "node-parse-staging", "node-parse-preflight", "reader-shell-syntax",
  ...override ? [] : ["go-version"], "node-helper-contract", "reader-user-pass-1", "reader-user-pass-2",
  "reader-data-plane-account-1", "reader-data-plane-account-2"];

export function parserOutcome(result) {
  const exitCode = Number.isInteger(result?.status) && result.status >= 0 && result.status <= 255 ? result.status : null;
  const timedOut = result?.error?.code === "ETIMEDOUT";
  const stderr = typeof result?.stderr === "string" ? result.stderr.slice(0, MAX_OUTPUT)
    : Buffer.isBuffer(result?.stderr) ? result.stderr.subarray(0, MAX_OUTPUT).toString("utf8") : "";
  let stderrCategory = "none";
  if (timedOut) stderrCategory = "timeout";
  else if (result?.error?.code === "ENOENT") stderrCategory = "executable-unavailable";
  else if (result?.error?.code === "EACCES") stderrCategory = "permission-denied";
  else if (result?.error?.code === "ENOBUFS") stderrCategory = "output-limit";
  else if (/SyntaxError\b|syntax error\b|unexpected token\b|unexpected end\b/i.test(stderr)) stderrCategory = "syntax-error";
  else if (/ERR_MODULE_NOT_FOUND\b|Cannot find (?:module|package)\b|ERR_REQUIRE_ESM\b/.test(stderr)) stderrCategory = "module-loader";
  else if (/test config parser requires Go|^go:|^#\s+\S+|undefined:|cannot find package|build constraints/m.test(stderr)) stderrCategory = "go-toolchain-or-build";
  else if (/^test-config:|flag provided but not defined:|^usage: test-config/m.test(stderr)) stderrCategory = "reader-or-schema";
  else if (/permission denied|operation not permitted/i.test(stderr)) stderrCategory = "permission-denied";
  else if (/no such file or directory|command not found/i.test(stderr)) stderrCategory = "executable-unavailable";
  else if (stderr.trim()) stderrCategory = "other-stderr";
  else if (result?.signal) stderrCategory = "terminated";
  return { exitCode, timedOut, stderrCategory };
}

function privateOutput(path, directory) {
  let binding;
  try {
    binding = prepareArtifactDirectory(directory ?? dirname(path));
    requireArtifactPaths(binding, [path]);
  } catch (error) { fail(artifactDirectoryReason(error)); }
  if (existsSync(path)) fail("output-already-exists");
  return binding;
}

// Hash only a fixed source/tool allowlist, never a configuration or fixture.
function fileHash(path, executable = false) {
  const actual = realpathSync(path);
  const stat = lstatSync(actual);
  if (!stat.isFile() || stat.size > 512 * 1024 * 1024) fail("source-identity-unavailable");
  if (executable) accessSync(actual, constants.X_OK);
  const descriptor = openSync(actual, "r");
  const buffer = Buffer.alloc(64 * 1024);
  const hash = createHash("sha256");
  try {
    for (;;) {
      const count = readSync(descriptor, buffer, 0, buffer.length, null);
      if (!count) return hash.digest("hex");
      hash.update(buffer.subarray(0, count));
    }
  } finally { closeSync(descriptor); }
}

function executablePath(command, env) {
  if (command.includes("/")) { accessSync(command, constants.X_OK); return realpathSync(command); }
  for (const folder of (env.PATH ?? "").split(delimiter)) {
    const candidate = resolve(folder || ".", command);
    try { accessSync(candidate, constants.X_OK); return realpathSync(candidate); } catch { /* continue PATH lookup */ }
  }
  fail("parser-tool-unavailable");
}

export function credentialParserProvenance(env = process.env) {
  const root = env.URNETWORK_ROOT || resolve(dirname(READER), "..");
  const override = env.UR_ACCEPT_TEST_CONFIG_READER;
  const sources = { stagingHelper: fileHash(HELPER), preflightHelper: fileHash(SELF), sharedReader: fileHash(READER),
    artifactDirectoryHelper: fileHash(ARTIFACT_HELPER), nodeExecutable: fileHash(process.execPath, true) };
  const toolVersions = { node: process.version, parserMode: override ? "override" : "go-run" };
  if (override) sources.parserExecutable = fileHash(executablePath(override, env), true);
  else {
    const base = join(root, "build/all/acceptance");
    for (const [key, relative] of Object.entries({ parserMain: "cmd/test-config/main.go", parserConfig: "testconfig/config.go",
      parserUserPass: "testconfig/user_pass.go", goMod: "go.mod", goSum: "go.sum" })) sources[key] = fileHash(join(base, relative));
    sources.goExecutable = fileHash(executablePath("go", env), true);
  }
  return { sources, toolVersions };
}

// An optional staging contract: runbooks require this for performance arms.
// It adds no schema/key fallback and never opens the real configuration.
export function requireCredentialParserPreflight(path, env = process.env) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.uid !== process.getuid() || (stat.mode & 0o777) !== 0o600 || stat.size > MAX_OUTPUT) {
    fail("private-parser-preflight-required");
  }
  const report = JSON.parse(readFileSync(path, "utf8"));
  if (report.type !== "physical-credential-parser-preflight" || report.schemaVersion !== 1 || report.eligible !== true ||
      report.classification !== "CREDENTIAL_PARSER_READY" || !Array.isArray(report.checks) ||
      !report.checks.length || report.checks.some((check) => check.passed !== true)) fail("passing-parser-preflight-required");
  const current = credentialParserProvenance(env);
  if (JSON.stringify(report.provenance) !== JSON.stringify(current)) fail("parser-preflight-provenance-changed");
  if (JSON.stringify(report.checks.map((check) => check.name)) !== JSON.stringify(checkNames(env.UR_ACCEPT_TEST_CONFIG_READER)) ||
      report.checks.some((check) => check.exitCode !== 0 || check.timedOut !== false) ||
      report.toolVersions?.node !== process.version || report.reason !== "synthetic-parser-contracts-passed") {
    fail("complete-parser-preflight-required");
  }
  return true;
}

export function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !["output", "artifact-dir"].includes(key) || options[key] !== undefined ||
        !argv[i + 1] || argv[i + 1].startsWith("--")) fail("only-private-output-is-accepted");
    options[key] = argv[i + 1];
  }
  if (!options.output) fail("only-private-output-is-accepted");
  return options;
}

export function preflightCredentialParser(options, dependencies = {}) {
  if (!options.output || Object.keys(options).some((key) => !["output", "artifact-dir"].includes(key))) fail("only-private-output-is-accepted");
  const directoryBinding = privateOutput(options.output, options["artifact-dir"]);
  const env = { ...(dependencies.env ?? process.env) };
  // Every reader call below replaces this with a generated fixture path.
  delete env.UR_ACCEPT_VAULT;
  const report = { type: "physical-credential-parser-preflight", schemaVersion: 1, eligible: false,
    classification: "FAILED_CREDENTIAL_PARSER_PREFLIGHT", reason: "preflight-unavailable", provenance: null,
    toolVersions: { node: process.version, go: null }, checks: [] };
  let directory;
  const invoke = dependencies.invoke ?? ((command, args, settings) => spawnSync(command, args, settings));
  const probe = (name, command, args, expected, probeEnv = env, timeout = 10_000) => {
    try { requireArtifactPaths(directoryBinding, [options.output]); }
    catch (error) { fail(artifactDirectoryReason(error)); }
    let result;
    try { result = invoke(command, args, { env: probeEnv, encoding: "utf8", timeout, maxBuffer: MAX_OUTPUT }); }
    catch (error) { result = { error }; }
    const passed = result?.status === 0 && !result.error && !result.signal && expected(result.stdout);
    report.checks.push({ name, passed, ...parserOutcome(result) });
    if (!passed) fail(`${name}-failed`);
    return result.stdout;
  };
  try {
    report.provenance = (dependencies.provenance ?? credentialParserProvenance)(env);
    report.toolVersions.node = probe("node-version", process.execPath, ["--version"],
      (output) => typeof output === "string" && output.trim() === process.version).trim();
    probe("node-parse-staging", process.execPath, ["--check", HELPER], (output) => output === "");
    probe("node-parse-preflight", process.execPath, ["--check", SELF], (output) => output === "");
    probe("reader-shell-syntax", "bash", ["-n", READER], (output) => output === "");
    if (!env.UR_ACCEPT_TEST_CONFIG_READER) {
      report.toolVersions.go = probe("go-version", "go", ["version"],
        (output) => typeof output === "string" && /^go version go[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:[a-z0-9.-]+)? [a-z0-9]+\/[a-z0-9]+\s*$/.test(output)).trim();
    }
    // Import in an eval with NO positional helper path: otherwise its CLI
    // main guard could mistake argv[1] for direct execution.
    probe("node-helper-contract", process.execPath, ["--input-type=module", "--eval",
      `const m = await import(process.env.URNETWORK_CREDENTIAL_PREFLIGHT_HELPER);
const p = m.credentialPayload(["synthetic-user", "  synthetic-value  "]);
const s = m.credentialLineStructure(p);
if (s.lineCount !== 2 || s.nonblankLineCount !== 2 || s.newlineCount !== 1) process.exit(2);`],
    (output) => output === "", { ...env, URNETWORK_CREDENTIAL_PREFLIGHT_HELPER: pathToFileURL(HELPER).href });
    directory = mkdtempSync(join(dirname(options.output), "credential-parser-fixture-"));
    const values = ["parser-preflight@example.invalid", "  synthetic \"quoted\" 'literal' $value \\ value  "];
    for (const [schema, keys, contents] of [
      ["user-pass", ["user", "pass"], `user: ${JSON.stringify(values[0])}\npass: ${JSON.stringify(values[1])}\n`],
      ["data-plane-account", ["data_plane_account.email", "data_plane_account.password"],
        `version: 1\nemail_verification:\n  bypass_domains: [acceptance.invalid]\n  suppress_account_messages: true\nsignup:\n  network_name_prefix: parser-probe\n  email: {domain: acceptance.invalid, local_part_prefix: parser-probe}\ndata_plane_account:\n  email: ${JSON.stringify(values[0])}\n  password: ${JSON.stringify(values[1])}\n`],
    ]) {
      const file = join(directory, `${schema}.yml`);
      writeFileSync(file, contents, { mode: 0o600, flag: "wx" });
      keys.forEach((key, index) => probe(`reader-${schema}-${index + 1}`, READER, ["--schema", schema, "get", key],
        (output) => output === values[index], { ...env, UR_ACCEPT_VAULT: file }, 60_000));
    }
    if (JSON.stringify((dependencies.provenance ?? credentialParserProvenance)(env)) !== JSON.stringify(report.provenance)) {
      fail("parser-source-changed-during-preflight");
    }
    report.eligible = true;
    report.classification = "CREDENTIAL_PARSER_READY";
    report.reason = "synthetic-parser-contracts-passed";
  } catch (error) {
    report.reason = error instanceof PreflightError ? error.message : "preflight-unavailable";
  } finally {
    if (directory) rmSync(directory, { recursive: true, force: true });
  }
  writeFileSync(options.output, `${JSON.stringify(report)}\n`, { flag: "wx", mode: 0o600 });
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { if (!preflightCredentialParser(parseArgs(process.argv.slice(2))).eligible) process.exitCode = 2; }
  catch (error) {
    process.stderr.write(`credential parser preflight failed: ${error instanceof PreflightError ? error.message : "evidence-unavailable"}\n`);
    process.exitCode = 2;
  }
}
