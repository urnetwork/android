import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { credentialParserProvenance, parseArgs, parserOutcome, preflightCredentialParser,
  requireCredentialParserPreflight } from "./physical_credentials_preflight.mjs";

const SENSITIVE = "private-value/token/path-never-retained";
const HELPER = fileURLToPath(new URL("./physical_credentials.mjs", import.meta.url));
const CLI = fileURLToPath(new URL("./physical_credentials_preflight.mjs", import.meta.url));
const fixedProvenance = () => ({ sources: { syntheticTestSource: "a".repeat(64) },
  toolVersions: { node: process.version, parserMode: "go-run" } });

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "physical-parser-preflight-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const f = { directory, options: { output: join(directory, "preflight.json") }, calls: [], readerCalls: [] };
  f.env = { PATH: process.env.PATH, UR_ACCEPT_VAULT: join(directory, "must-never-open-real-config") };
  f.invoke = (command, args, settings) => {
    f.calls.push({ command, args });
    assert.equal(settings.maxBuffer, 64 * 1024);
    assert.ok(settings.timeout <= 60_000);
    if (basename(command) === "read-tests-config.sh") {
      assert.equal(args[0], "--schema"); assert.equal(args[2], "get");
      const path = settings.env.UR_ACCEPT_VAULT;
      assert.notEqual(path, f.env.UR_ACCEPT_VAULT);
      assert.ok(path.startsWith(join(directory, "credential-parser-fixture-")));
      assert.equal(statSync(path).mode & 0o777, 0o600);
      const schema = args[1];
      const keys = schema === "user-pass" ? ["user", "pass"] : ["data_plane_account.email", "data_plane_account.password"];
      const raw = readFileSync(path, "utf8");
      const values = [...raw.matchAll(/^\s*(?:user|pass|email|password): (".*")$/gm)].map((match) => JSON.parse(match[1]));
      assert.equal(values.length, 2);
      assert.notEqual(keys.indexOf(args[3]), -1);
      f.readerCalls.push({ schema, key: args[3], path });
      return { status: 0, stdout: values[keys.indexOf(args[3])], stderr: "" };
    }
    assert.equal(settings.env.UR_ACCEPT_VAULT, undefined, "non-reader probes never inherit real config selector");
    if (args[0] === "--version") return { status: 0, stdout: `${process.version}\n`, stderr: "" };
    if (command === "go") return { status: 0, stdout: "go version go1.26.5 darwin/arm64\n", stderr: "" };
    return { status: 0, stdout: "", stderr: "" };
  };
  f.deps = { env: f.env, provenance: fixedProvenance, invoke: (...args) => f.invoke(...args) };
  return f;
}

function assertSanitized(report) {
  const json = JSON.stringify(report);
  for (const forbidden of [SENSITIVE, '"stdout":', '"stderr":', '"path":', '"signal":', "parser-preflight@example.invalid", "synthetic "]) {
    assert.equal(json.includes(forbidden), false, forbidden);
  }
  for (const check of report.checks) {
    assert.deepEqual(Object.keys(check).sort(), ["exitCode", "name", "passed", "stderrCategory", "timedOut"]);
  }
}

test("fixed parser categories discard raw output, errors, signals, paths and nonnumeric exits", () => {
  for (const [result, category] of [
    [{ status: 2, stderr: `SyntaxError: ${SENSITIVE}` }, "syntax-error"],
    [{ status: 1, stderr: `ERR_MODULE_NOT_FOUND ${SENSITIVE}` }, "module-loader"],
    [{ status: 1, stderr: `go: build ${SENSITIVE}` }, "go-toolchain-or-build"],
    [{ status: 2, stderr: `test-config: ${SENSITIVE}` }, "reader-or-schema"],
    [{ status: 1, stderr: `permission denied: ${SENSITIVE}` }, "permission-denied"],
    [{ status: 1, stderr: `command not found: ${SENSITIVE}` }, "executable-unavailable"],
    [{ status: null, error: { code: "ETIMEDOUT", message: SENSITIVE }, stderr: SENSITIVE }, "timeout"],
    [{ status: null, error: { code: "ENOBUFS", message: SENSITIVE }, stderr: SENSITIVE }, "output-limit"],
    [{ status: null, error: { code: "ENOENT", message: SENSITIVE } }, "executable-unavailable"],
    [{ status: null, error: { code: "EACCES", message: SENSITIVE } }, "permission-denied"],
    [{ status: null, signal: SENSITIVE, stderr: "" }, "terminated"],
    [{ status: 1, stderr: Buffer.from(SENSITIVE), stdout: SENSITIVE }, "other-stderr"],
    [{ status: 1, stderr: "" }, "none"],
    [{ status: 0, stdout: SENSITIVE, stderr: "" }, "none"],
    [undefined, "none"],
  ]) {
    const outcome = parserOutcome(result);
    assert.equal(outcome.stderrCategory, category);
    assert.equal(outcome.timedOut, result?.error?.code === "ETIMEDOUT");
    assert.deepEqual(Object.keys(outcome).sort(), ["exitCode", "stderrCategory", "timedOut"]);
    assert.equal(JSON.stringify(outcome).includes(SENSITIVE), false);
  }
  for (const status of [-1, 256, 1.2, SENSITIVE, NaN, Infinity]) assert.equal(parserOutcome({ status }).exitCode, null);
  assert.equal(parserOutcome({ status: 255 }).exitCode, 255);
  assert.equal(parserOutcome({ stderr: `${"x".repeat(64 * 1024)}SyntaxError` }).stderrCategory, "other-stderr", "bounded stderr scan");
});

test("preflight accepts only a private output, never config, serial, password or schema input", (t) => {
  assert.deepEqual(parseArgs(["--output", "private.json"]), { output: "private.json" });
  for (const args of [[], ["--output", ""], ["--output", "--config"], ["--config", SENSITIVE],
    ["--output", "private.json", "--serial", "fake"], ["--output", "private.json", "--schema", "user-pass"]]) {
    assert.throws(() => parseArgs(args), /only-private-output-is-accepted/);
  }
  const f = fixture(t);
  assert.throws(() => preflightCredentialParser({ ...f.options, config: SENSITIVE }, f.deps));
  chmodSync(f.directory, 0o755);
  assert.throws(() => preflightCredentialParser(f.options, f.deps), /artifact-directory-mode-not-0700/);
  assert.equal(f.calls.length, 0);
});

test("parser preflight binds its output to the checked directory before source inspection or probes", (t) => {
  const f = fixture(t);
  f.options["artifact-dir"] = join(f.directory, "missing");
  f.deps.provenance = () => assert.fail("missing evidence must reject before inspecting source");
  assert.throws(() => preflightCredentialParser(f.options, f.deps), /artifact-directory-missing/);
  f.options["artifact-dir"] = f.directory;
  f.options.output = join(f.directory, "outside", "preflight.json");
  assert.throws(() => preflightCredentialParser(f.options, f.deps), /artifact-path-outside-directory/);
  assert.equal(f.calls.length, 0);
  assert.equal(parseArgs(["--artifact-dir", f.directory, "--output", "private.json"])["artifact-dir"], f.directory);
});

test("healthy preflight exercises exact keys for both synthetic schemas and destroys only fixtures", (t) => {
  const f = fixture(t);
  const report = preflightCredentialParser(f.options, f.deps);
  assert.equal(report.eligible, true);
  assert.equal(report.classification, "CREDENTIAL_PARSER_READY");
  assert.equal(report.checks.length, 10);
  assert.deepEqual(f.readerCalls.map(({ schema, key }) => [schema, key]), [
    ["user-pass", "user"], ["user-pass", "pass"], ["data-plane-account", "data_plane_account.email"],
    ["data-plane-account", "data_plane_account.password"],
  ]);
  assert.deepEqual(readdirSync(f.directory), ["preflight.json"]);
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.deepEqual(JSON.parse(readFileSync(f.options.output, "utf8")), report);
  assert.equal(f.calls.some(({ command }) => basename(command) === "adb"), false);
  assertSanitized(report);
});

test("Node parse failure is retained before helper import, shared reader or any device call", (t) => {
  const f = fixture(t); const ordinary = f.invoke;
  f.invoke = (command, args, settings) => args[0] === "--check" && args[1] === HELPER
    ? { status: 1, stdout: SENSITIVE, stderr: `SyntaxError: ${SENSITIVE}` } : ordinary(command, args, settings);
  const report = preflightCredentialParser(f.options, f.deps);
  assert.equal(report.eligible, false); assert.equal(report.reason, "node-parse-staging-failed");
  assert.deepEqual(report.checks.at(-1), { name: "node-parse-staging", passed: false,
    exitCode: 1, timedOut: false, stderrCategory: "syntax-error" });
  assert.equal(f.readerCalls.length, 0); assert.equal(report.checks.length, 2);
  assertSanitized(report);
});

test("reader errors, partial/wrapped scalar output and timeout fail closed without retaining output", (t) => {
  for (const result of [{ status: 1, stdout: SENSITIVE, stderr: `test-config: ${SENSITIVE}` },
    { status: 0, stdout: '"parser-preflight@example.invalid"', stderr: "" },
    { status: null, error: { code: "ETIMEDOUT", message: SENSITIVE }, stderr: SENSITIVE }]) {
    const f = fixture(t); const ordinary = f.invoke;
    f.invoke = (command, args, settings) => basename(command) === "read-tests-config.sh"
      ? result : ordinary(command, args, settings);
    const report = preflightCredentialParser(f.options, f.deps);
    assert.equal(report.eligible, false); assert.equal(report.reason, "reader-user-pass-1-failed");
    assert.equal(report.checks.at(-1).passed, false);
    assert.deepEqual(readdirSync(f.directory), ["preflight.json"]);
    assertSanitized(report);
  }
});

test("source change during preflight is ineligible even after all synthetic contracts pass", (t) => {
  const f = fixture(t); let calls = 0;
  f.deps.provenance = () => ({ ...fixedProvenance(), revision: calls++ });
  const report = preflightCredentialParser(f.options, f.deps);
  assert.equal(report.eligible, false); assert.equal(report.reason, "parser-source-changed-during-preflight");
  assert.equal(report.checks.length, 10); assertSanitized(report);
});

test("passing source-bound contract rejects incomplete, changed and nonprivate receipts", (t) => {
  const f = fixture(t); f.deps.provenance = credentialParserProvenance;
  const report = preflightCredentialParser(f.options, f.deps);
  assert.equal(report.eligible, true);
  assert.equal(requireCredentialParserPreflight(f.options.output, f.env), true);
  const save = (value) => writeFileSync(f.options.output, JSON.stringify(value), { mode: 0o600 });
  save({ ...report, checks: report.checks.slice(0, -1) });
  assert.throws(() => requireCredentialParserPreflight(f.options.output, f.env), /complete-parser-preflight-required/);
  save({ ...report, provenance: fixedProvenance() });
  assert.throws(() => requireCredentialParserPreflight(f.options.output, f.env), /parser-preflight-provenance-changed/);
  save(report); chmodSync(f.options.output, 0o644);
  assert.throws(() => requireCredentialParserPreflight(f.options.output, f.env), /private-parser-preflight-required/);
});

test("real CLI preflight imports helper safely and selected reader sees only synthetic configs", (t) => {
  const f = fixture(t);
  const reader = join(f.directory, "synthetic-reader");
  writeFileSync(reader, `#!${process.execPath}
const fs = require('node:fs'); const path = require('node:path'); const args = process.argv.slice(2);
if (args[0] !== '--config' || args[2] !== '--schema' || args[4] !== 'get') process.exit(7);
if (!path.basename(path.dirname(args[1])).startsWith('credential-parser-fixture-')) {
  process.stderr.write('test-config: ${SENSITIVE}'); process.exit(6);
}
const raw = fs.readFileSync(args[1], 'utf8');
const values = [...raw.matchAll(/^\\s*(?:user|pass|email|password): (".*")$/gm)].map(m => JSON.parse(m[1]));
const keys = args[3] === 'user-pass' ? ['user','pass'] : ['data_plane_account.email','data_plane_account.password'];
if (values.length !== 2 || !keys.includes(args[5])) process.exit(8);
process.stdout.write(values[keys.indexOf(args[5])]);
`, { mode: 0o700 });
  const env = { ...process.env, UR_ACCEPT_VAULT: f.env.UR_ACCEPT_VAULT, UR_ACCEPT_TEST_CONFIG_READER: reader };
  const preflight = spawnSync(process.execPath, [CLI, "--output", f.options.output], { env, encoding: "utf8", timeout: 15_000 });
  assert.equal(preflight.status, 0, preflight.stderr);
  assert.equal(preflight.stdout, ""); assert.equal(preflight.stderr, "");
  const report = JSON.parse(readFileSync(f.options.output, "utf8"));
  assert.equal(report.checks.length, 9); assert.equal(report.provenance.toolVersions.parserMode, "override");
  assert.equal(requireCredentialParserPreflight(f.options.output, env), true);
  assertSanitized(report);
  // A later exact CLI reader failure retains its category, not its stderr.
  const config = join(f.directory, "synthetic-config.yml");
  writeFileSync(config, "user: fixture\npass: fixture-value\n", { mode: 0o600 });
  const stagedOutput = join(f.directory, "staging.json");
  const args = [HELPER, "--serial", "unused-fake-device", "--schema", "user-pass", "--config", config,
    "--preflight", f.options.output, "--output", stagedOutput];
  const staging = spawnSync(process.execPath, args, { env, encoding: "utf8", timeout: 15_000 });
  assert.equal(staging.status, 2); assert.equal(staging.stdout, ""); assert.equal(staging.stderr, "");
  const failure = JSON.parse(readFileSync(stagedOutput, "utf8"));
  assert.equal(failure.reason, "config-reader-failed");
  assert.deepEqual(failure.parserOutcome, { exitCode: 6, timedOut: false, stderrCategory: "reader-or-schema" });
  assert.equal(JSON.stringify(failure).includes(SENSITIVE), false);
  assert.equal(Object.values(failure.steps).every(step => step.outcome === "not-run"), true);
  // Reader changes after the passing receipt must be rejected before reading
  // the synthetic config or attempting even the first device command.
  writeFileSync(reader, "#!/bin/sh\nexit 0\n", { mode: 0o700 });
  const staleArgs = [...args]; staleArgs[staleArgs.indexOf("--output") + 1] = join(f.directory, "stale-staging.json");
  const stale = spawnSync(process.execPath, staleArgs, { env, encoding: "utf8", timeout: 15_000 });
  assert.equal(stale.status, 2); assert.equal(stale.stdout, "");
  assert.match(stale.stderr, /credential-parser-preflight-invalid/);
  assert.equal(existsSync(f.env.UR_ACCEPT_VAULT), false);
});
