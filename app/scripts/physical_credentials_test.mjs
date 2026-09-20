import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync,
  symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { credentialLineStructure, credentialPayload, credentialScripts, inspectPhysicalCredentialLines,
  diagnosePhysicalPublication, parseArgs, sentinelScripts, stagePhysicalCredentials } from "./physical_credentials.mjs";

const USER = "physical-fixture@example.invalid";
const PASSWORD = "  p'\"$(touch INJECTED) `touch OTHER` \\ ; & Unicode-雪  ";
const shellQuote = (text) => `'${text.replaceAll("'", "'\\''")}'`;
// macOS has /dev/fd but no /proc. Its ksh builtin resolves descriptor identity;
// bash/dash stat the fdesc device node instead. Preserve a real shell FD and
// its lifetime rather than manufacturing an identity result in fake stat.
const HOST_SHELL = process.platform === "darwin" ? "/bin/ksh" : "/bin/sh";
const hostFdPaths = (script) => process.platform === "darwin"
  ? script.replaceAll("/proc/self/fd/3", "/dev/fd/3") : script;

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "physical-credentials-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const app = join(directory, "app"); const bin = join(directory, "bin");
  mkdirSync(app, { mode: 0o700 }); mkdirSync(bin, { mode: 0o700 });
  // Emulate Android toybox's stat/sha256sum syntax portably. All file I/O is
  // test-owned; other commands execute the exact run-as shell transaction.
  const metadataTool = `#!${process.execPath}
const fs = require('node:fs'); const path = require('node:path'); const crypto = require('node:crypto');
const args = process.argv.slice(2); const file = args.at(-1);
if (path.basename(process.argv[1]).startsWith('stat')) {
  const fd = file === '/proc/self/fd/3' || file === '/dev/fd/3' ? 3 : null;
  // Model the external child's inaccessible owner FD without closing a
  // descriptor that Node itself may already have reused for its event loop.
  if (fd !== null && process.env.TEST_METADATA_HIDES_FD === '1') process.exit(1);
  const stat = fd !== null ? fs.fstatSync(fd, {bigint:true}) : fs.statSync(file, {bigint:true});
  const format = args[args.indexOf('-c')+1];
  process.stdout.write((format === '%d %i' ? stat.dev+' '+stat.ino : (stat.mode & 511n).toString(8)+(format === '%a %s %h' ? ' '+stat.size+' '+stat.nlink : ''))+'\\n');
}
else process.stdout.write(crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')+'  '+file+'\\n');
`;
  for (const name of ["stat", "stat-real", "sha256sum"]) writeFileSync(join(bin, name), metadataTool, { mode: 0o700 });
  const options = { serial: "fake-device", schema: "data-plane-account",
    config: join(directory, "config.yml"), output: join(directory, "staging.json") };
  writeFileSync(options.config, "version: 1\n", { mode: 0o600 });
  const keys = []; const calls = [];
  const env = { ...process.env, PATH: `${bin}:${process.env.PATH}` };
  const f = { directory, app, bin, env, options, keys, calls, values: [USER, PASSWORD], sourceSchema: "data-plane-account" };
  f.failStat = (format, code) => writeFileSync(join(bin, "stat"), `#!/bin/sh
if [ "$1" = '-c' ] && [ "$2" = '${format}' ]; then printf 'private-diagnostic-marker\\n' >&2; exit ${code}; fi
exec '${join(bin, "stat-real")}' "$@"
`, { mode: 0o700 });
  f.adb = (args, input) => {
    assert.deepEqual(args.slice(0, 8), ["-s", "fake-device", "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c"]);
    assert.equal(args.join(" ").includes(USER), false);
    assert.equal(args.join(" ").includes(PASSWORD), false);
    calls.push({ args, hasInput: input !== undefined });
    const command = [...args.slice(6)];
    command[0] = HOST_SHELL;
    command[command.length - 1] = shellQuote(f.shellPrelude ?? "") + command.at(-1);
    return spawnSync(HOST_SHELL, ["-c", hostFdPaths(command.join(" "))],
      { cwd: app, input, encoding: "utf8", timeout: 3000, env });
  };
  f.deps = { uuid: () => "fixture-token", reader: (key, schema) => {
    keys.push(key);
    const selected = f.sourceSchema === "user-pass" ? ["user", "pass"] : ["data_plane_account.email", "data_plane_account.password"];
    const index = selected.indexOf(key);
    return schema !== f.sourceSchema || index < 0 ? { status: 1, stdout: "", stderr: "wrong schema" }
      : { status: 0, stdout: f.values[index] };
  }, adb: (...args) => f.adb(...args) };
  return f;
}

function assertNoSecrets(report) {
  const text = JSON.stringify(report);
  for (const secret of [USER, PASSWORD, "fake-device"]) assert.equal(text.includes(secret), false);
}

function assertPrivateOutcomesOnly(report) {
  assertNoSecrets(report);
  const text = JSON.stringify(report);
  for (const forbidden of ["sha256", '"stdout":', '"stderr":', "fixture-token", "files/acceptance", "private-diagnostic-marker"]) {
    assert.equal(text.includes(forbidden), false, forbidden);
  }
  for (const step of Object.values(report.steps)) {
    assert.deepEqual(Object.keys(step).sort(), ["exitCode", "outcome"]);
    assert.equal(["not-run", "ok", "failed", "unavailable"].includes(step.outcome), true);
    assert.equal(step.exitCode === null || Number.isInteger(step.exitCode) && step.exitCode >= 0 && step.exitCode <= 255, true);
  }
}

test("staging requires explicit serial, config schema and private evidence path", () => {
  for (const schema of ["user-pass", "data-plane-account"]) {
    assert.deepEqual(parseArgs(["--serial", "fake", "--schema", schema, "--config", "config", "--output", "out"]),
      { serial: "fake", schema, config: "config", output: "out" });
  }
  for (const args of [[], ["--serial", "fake"], ["--password", "secret"],
    ["--serial", "fake", "--config", "config", "--output", "out"],
    ["--serial", "fake", "--schema", "guess", "--config", "config", "--output", "out"],
    ["--inspect-only", "--serial", "fake", "--schema", "user-pass", "--output", "out"],
    ["--inspect-only", "--serial", "fake", "--config", "config", "--output", "out"],
    ["--inspect-only", "--inspect-only", "--serial", "fake", "--output", "out"],
    ["--inspect-only", "--sentinel-only", "--serial", "fake", "--output", "out"],
    ["--sentinel-only", "--serial", "fake", "--config", "config", "--output", "out"],
    ["--sentinel-only", "--serial", "fake", "--schema", "user-pass", "--output", "out"],
    ["--serial", "fake", "--config", "config", "--output", "out", "--serial", "again"]]) {
    assert.throws(() => parseArgs(args));
  }
  assert.deepEqual(parseArgs(["--inspect-only", "--serial", "fake", "--output", "out"]),
    { inspectOnly: true, serial: "fake", output: "out" });
  assert.deepEqual(parseArgs(["--sentinel-only", "--serial", "fake", "--output", "out"]),
    { sentinelOnly: true, serial: "fake", output: "out" });
  assert.equal(parseArgs(["--serial", "fake", "--schema", "user-pass", "--config", "config",
    "--output", "out", "--preflight", "private-report"]).preflight, "private-report");
  for (const mode of ["--inspect-only", "--sentinel-only"]) {
    assert.throws(() => parseArgs([mode, "--serial", "fake", "--output", "out", "--preflight", "private-report"]));
  }
});

test("failed parser preflight stops staging before config inspection, reader or device", (t) => {
  const f = fixture(t);
  f.options.preflight = join(f.directory, "preflight.json");
  writeFileSync(f.options.preflight, JSON.stringify({ eligible: false }), { mode: 0o600 });
  f.options.config = join(f.directory, "deliberately-absent-config");
  assert.throws(() => stagePhysicalCredentials(f.options, f.deps), /credential-parser-preflight-invalid/);
  assert.equal(f.keys.length, 0); assert.equal(f.calls.length, 0);
  assert.equal(existsSync(f.options.output), false);
});

test("staging binds evidence and parser receipt to one existing private directory before reading config", (t) => {
  const f = fixture(t);
  f.options.config = join(f.directory, "deliberately-absent-config");
  f.options["artifact-dir"] = join(f.directory, "removed-private");
  assert.throws(() => stagePhysicalCredentials(f.options, f.deps), /artifact-directory-missing/);
  f.options["artifact-dir"] = f.directory;
  f.options.preflight = join(f.directory, "outside", "preflight.json");
  assert.throws(() => stagePhysicalCredentials(f.options, f.deps), /artifact-path-outside-directory/);
  delete f.options.preflight;
  f.options.output = join(f.directory, "outside", "staging.json");
  assert.throws(() => stagePhysicalCredentials(f.options, f.deps), /artifact-path-outside-directory/);
  assert.equal(f.keys.length, 0); assert.equal(f.calls.length, 0);
  assert.equal(parseArgs(["--serial", "fake", "--schema", "user-pass", "--config", "config", "--output", "out",
    "--artifact-dir", f.directory])["artifact-dir"], f.directory);
});

test("reader failures retain only bounded fixed outcome, never raw error or partial values", (t) => {
  const cases = [
    [{ status: 2, stdout: USER, stderr: `SyntaxError: ${PASSWORD} /private/secret-path` },
      { exitCode: 2, timedOut: false, stderrCategory: "syntax-error" }],
    [{ status: null, error: { code: "ETIMEDOUT", message: PASSWORD }, stderr: USER },
      { exitCode: null, timedOut: true, stderrCategory: "timeout" }],
    [{ status: 1, stdout: PASSWORD, stderr: `test-config: ${USER}` },
      { exitCode: 1, timedOut: false, stderrCategory: "reader-or-schema" }],
    [{ status: null, signal: "SIGTERM", stderr: "" },
      { exitCode: null, timedOut: false, stderrCategory: "terminated" }],
  ];
  for (const [result, expected] of cases) {
    const f = fixture(t); f.deps.reader = () => result;
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false); assert.equal(report.reason, "config-reader-failed");
    assert.deepEqual(report.parserOutcome, expected);
    assertPrivateOutcomesOnly(report);
    assert.equal(JSON.stringify(report).includes("/private/secret-path"), false);
    assert.equal(f.calls.length, 0);
    assert.deepEqual(JSON.parse(readFileSync(f.options.output, "utf8")), report);
  }
  const f = fixture(t);
  f.deps.reader = () => { throw Object.assign(new Error(PASSWORD), { code: "ENOENT" }); };
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.reason, "credential-staging-unavailable", "preserve existing generic thrown-error semantics");
  assert.deepEqual(report.parserOutcome, { exitCode: null, timedOut: false, stderrCategory: "executable-unavailable" });
  assertPrivateOutcomesOnly(report); assert.equal(f.calls.length, 0);
});

test("each explicit schema invokes only its exact reader keys and records the selection", (t) => {
  for (const schema of ["user-pass", "data-plane-account"]) {
    const f = fixture(t); f.options.schema = schema; f.sourceSchema = schema;
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, true);
    assert.equal(report.sourceSchema, schema);
    assert.deepEqual(f.keys, schema === "user-pass" ? ["user", "pass"] : ["data_plane_account.email", "data_plane_account.password"]);
    assert.deepEqual(readFileSync(join(f.app, "files/acceptance/credentials")), Buffer.from(`${USER}\n${PASSWORD}`));
    assertNoSecrets(report);
  }
});

test("wrong schema fails without alternate-key fallback or any device call", (t) => {
  for (const schema of ["user-pass", "data-plane-account"]) {
    const f = fixture(t); f.options.schema = schema;
    f.sourceSchema = schema === "user-pass" ? "data-plane-account" : "user-pass";
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "config-reader-failed");
    assert.deepEqual(f.keys, schema === "user-pass" ? ["user"] : ["data_plane_account.email"]);
    assert.equal(f.calls.length, 0);
    assert.equal(report.expected, null);
    assertNoSecrets(report);
  }
});

test("raw scalar values use one LF separator and no trailing LF or JSON/shell escaping", () => {
  assert.deepEqual(credentialPayload([USER, PASSWORD]), Buffer.from(`${USER}\n${PASSWORD}`));
  for (const bad of ["", " \t ", "\u001c", "\u00a0", "\u2007", "one\ntwo", "one\r", "one\0two"]) {
    assert.throws(() => credentialPayload([USER, bad]), /two-raw-nonblank-values-required/);
    assert.throws(() => credentialPayload([bad, PASSWORD]), /two-raw-nonblank-values-required/);
  }
  assert.deepEqual(credentialPayload([USER, "\ufeff"]), Buffer.from(`${USER}\n\ufeff`), "BOM is not Kotlin isBlank");
});

test("exact run-as protocol verifies structure/digest and exclusively publishes a private raw file", (t) => {
  const f = fixture(t);
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, true);
  assert.equal(report.reason, "two-raw-nonblank-lines-verified");
  assert.deepEqual(f.keys, ["data_plane_account.email", "data_plane_account.password"]);
  assert.deepEqual(f.calls.map((call) => call.hasInput), [true, false, false]);
  const payload = Buffer.from(`${USER}\n${PASSWORD}`);
  const file = join(f.app, "files/acceptance/credentials");
  assert.deepEqual(readFileSync(file), payload);
  assert.equal(statSync(file).mode & 0o777, 0o600);
  assert.equal(statSync(join(f.app, "files/acceptance")).mode & 0o777, 0o700);
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assert.equal(existsSync(join(f.app, "INJECTED")), false);
  assert.equal(existsSync(join(f.app, "OTHER")), false);
  assert.deepEqual(report.expected, { lineCount: 2, blankLineCount: 0, byteLength: payload.length, mode: "600" });
  assert.deepEqual(report.observed, report.expected);
  assert.equal(report.schemaVersion, 3);
  assert.deepEqual(report.steps, { ...Object.fromEntries(["stage", "publish", "create", "copy", "inspect", "cleanup"].map((key) =>
    [key, { outcome: "ok", exitCode: 0 }])), destinationCleanup: { outcome: "not-run", exitCode: null } });
  assert.equal(JSON.stringify(report).includes(createHash("sha256").update(payload).digest("hex")), false);
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.deepEqual(JSON.parse(readFileSync(f.options.output, "utf8")), report);
  assertNoSecrets(report);
});

test("denied sandbox hardlinks do not prevent exclusive credential or sentinel publication", (t) => {
  const f = fixture(t);
  writeFileSync(join(f.bin, "ln"), "#!/bin/sh\nprintf called >> hardlink-attempts\nexit 13\n", { mode: 0o700 });
  writeFileSync(join(f.app, "hardlink-source"), "", { mode: 0o600 });
  const denied = spawnSync("ln", ["hardlink-source", "hardlink-destination"], { cwd: f.app, env: f.env });
  assert.equal(denied.status, 13, "fixture must reproduce the rejected publication primitive");
  assert.equal(existsSync(join(f.app, "hardlink-destination")), false);
  const staged = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(staged.eligible, true);
  const sentinel = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true,
    output: join(f.directory, "sentinel.json") }, f.deps);
  assert.equal(sentinel.eligible, true);
  assert.equal(readFileSync(join(f.app, "hardlink-attempts"), "utf8"), "called", "neither protocol may invoke ln");
  assertPrivateOutcomesOnly(staged); assertPrivateOutcomesOnly(sentinel);
});

test("concurrent publishers past both prechecks have exactly one exclusive-open winner", async (t) => {
  const f = fixture(t);
  const inputs = [Buffer.from("first@example.invalid\nfirst-password"), Buffer.from("second@example.invalid\nsecond-password")];
  const scripts = inputs.map((_, i) => credentialScripts(`concurrent-${i}`));
  for (let i = 0; i < scripts.length; i++) {
    const staged = spawnSync(HOST_SHELL, ["-c", scripts[i].stage], { cwd: f.app, env: f.env, input: inputs[i], encoding: "utf8" });
    assert.equal(staged.status, 0, staged.stderr);
  }
  const publishers = scripts.map((script) => {
    // A test-only wrapper stops each shell after its final absent-path guard,
    // before the unchanged production exclusive-open command. Both see the
    // path absent, so the file-open primitive, not a prior test, must arbitrate.
    const barrier = `publication_barrier=1
test() {
  command test "$@" || return $?
  if [ "$*" = '! -L files/acceptance/credentials' ] && [ "$publication_barrier" = 1 ]; then
    publication_barrier=0
    printf 'barrier-ready\\n'
    read -r publication_release
  fi
}
`;
    const child = spawn(HOST_SHELL, ["-c", hostFdPaths(barrier + script.publish)], { cwd: f.app, env: f.env, timeout: 5000 });
    let stdout = ""; let stderr = ""; let readyResolve;
    const ready = new Promise((resolve) => { readyResolve = resolve; });
    child.stdout.on("data", (chunk) => { stdout += chunk; if (stdout.includes("barrier-ready\n")) readyResolve(true); });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    const done = new Promise((resolve) => {
      child.on("error", (error) => { readyResolve(false); resolve({ status: null, stdout, stderr: error.message }); });
      child.on("close", (status) => { readyResolve(false); resolve({ status, stdout, stderr }); });
    });
    t.after(async () => { if (child.exitCode === null) child.kill("SIGKILL"); await done; });
    return { child, ready, done };
  });
  assert.deepEqual(await Promise.all(publishers.map((publisher) => publisher.ready)), [true, true]);
  for (const publisher of publishers) publisher.child.stdin.end("release\n");
  const results = await Promise.all(publishers.map((publisher) => publisher.done));
  // The actual helper requires all phase/ownership evidence as well as exit 0.
  // ksh can report exit 0 from an EXIT trap after a fatal redirection failure;
  // that must not admit a publisher which never acquired the destination.
  const admitted = (result) => result.status === 0 && result.stdout.includes("publication-owned\n") &&
    ["create", "copy", "inspect"].every((phase) => result.stdout.includes(`publication-step ${phase} 0\n`));
  const winner = results.findIndex(admitted);
  assert.notEqual(winner, -1, JSON.stringify(results));
  assert.equal(results.filter(admitted).length, 1, JSON.stringify(results));
  const loser = results[1 - winner];
  assert.doesNotMatch(loser.stdout, /publication-owned|publication-release/);
  assert.match(results[winner].stdout, /publication-step inspect 0\n/);
  const destination = join(f.app, "files/acceptance/credentials");
  assert.deepEqual(readFileSync(destination), inputs[winner]);
  for (const script of scripts) {
    const cleanup = spawnSync(HOST_SHELL, ["-c", script.cleanup], { cwd: f.app, env: f.env });
    assert.equal(cleanup.status, 0);
  }
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assert.deepEqual(readFileSync(destination), inputs[winner], "neither later temporary cleanup may delete the winner");
});

test("run-as counts logical LF records including the unterminated final line, not newline bytes", (t) => {
  // Kotlin File.readLines/BufferedReader accept either 0 or 1 final LF. Only
  // another blank record is a third entry. Production emits no final LF.
  for (const [suffix, lines, blanks] of [["", 2, 0], ["\n", 2, 0], ["\n\n", 3, 1], ["\nextra", 3, 0]]) {
    const f = fixture(t);
    const payload = Buffer.from(`${USER}\n${PASSWORD}${suffix}`);
    const script = credentialScripts("line-count-fixture").stage;
    const quoted = `'${script.replaceAll("'", "'\\''")}'`;
    const result = f.adb(["-s", "fake-device", "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c", quoted], payload);
    assert.equal(result.status, 0, result.stderr);
    const fields = result.stdout.slice("staging-owned\n".length).trim().split(/\s+/);
    assert.equal(Number(fields[0]), lines);
    assert.equal(Number(fields[1]), blanks);
    assert.equal(Number(fields[2]), payload.length);
  }
});

test("unexpected final LF or a third logical entry fails exact-byte publication, not app auth", (t) => {
  for (const suffix of ["\n", "\n\n", "\nextra"]) {
    const f = fixture(t); const original = f.adb;
    f.adb = (args, input) => original(args, input === undefined ? input : Buffer.concat([input, Buffer.from(suffix)]));
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "device-credential-structure-mismatch");
    assert.equal(report.observed.lineCount, suffix === "\n" ? 2 : 3);
    assert.equal(report.observed.blankLineCount, suffix === "\n\n" ? 1 : 0);
    assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
    assertNoSecrets(report);
  }
});

test("logical counts match Kotlin File.readLines and isBlank, not String.split or newline count", () => {
  for (const [text, lineCount, nonblankLineCount, newlineCount] of [
    ["user\npassword", 2, 2, 1], ["user\npassword\n", 2, 2, 2],
    ["user\npassword\n\n", 3, 2, 3], ["user\npassword\nextra", 3, 3, 2],
    ["user\r\npassword\r\n", 2, 2, 2], ["user\rpassword", 2, 2, 0],
    ["", 0, 0, 0], ["\n", 1, 0, 1], ["user\r\r", 2, 1, 0],
    ["user\n\u001c", 2, 1, 1], ["user\n\u00a0", 2, 1, 1], ["user\n\u2007", 2, 1, 1],
    ["user\n\ufeff", 2, 2, 1],
  ]) {
    assert.deepEqual(credentialLineStructure(Buffer.from(text)),
      { lineCount, nonblankLineCount, byteLength: Buffer.byteLength(text), newlineCount });
  }
  assert.throws(() => credentialLineStructure(Buffer.from([0xff])), /credential-file-invalid-utf8/);
});

test("read-only inspection reports counts from the exact app file without values or digest", (t) => {
  for (const suffix of ["", "\n", "\n\n", "\nextra"]) {
    const f = fixture(t);
    mkdirSync(join(f.app, "files/acceptance"), { recursive: true, mode: 0o700 });
    const file = join(f.app, "files/acceptance/credentials");
    const original = Buffer.from(`${USER}\n${PASSWORD}${suffix}`);
    writeFileSync(file, original, { mode: 0o600 });
    const report = inspectPhysicalCredentialLines({ serial: f.options.serial, output: f.options.output }, {
      adb: (args) => { const result = f.adb(args); return { ...result, stdout: Buffer.from(result.stdout) }; },
    });
    assert.equal(report.eligible, suffix === "" || suffix === "\n");
    assert.equal(report.lineCount, report.eligible ? 2 : 3);
    assert.equal(report.nonblankLineCount, suffix === "\nextra" ? 3 : 2);
    assert.equal(report.byteLength, original.length);
    assert.equal(report.newlineCount, suffix === "\n\n" ? 3 : suffix ? 2 : 1);
    assert.deepEqual(readFileSync(file), original, "inspection must not rewrite the file");
    assert.equal(f.calls.length, 1);
    assert.equal(f.calls[0].hasInput, false);
    assert.equal("sha256" in report, false);
    assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
    assertNoSecrets(report);
  }
});

test("blank/multiline configuration and reader failures never issue a device command", (t) => {
  for (const kind of ["blank", "multiline", "reader-failed"]) {
    const f = fixture(t);
    f.values[1] = kind === "blank" ? " \t" : "one\ntwo";
    if (kind === "reader-failed") f.deps.reader = () => ({ status: 1, stdout: PASSWORD, stderr: PASSWORD });
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(f.calls.length, 0);
    assertNoSecrets(report);
  }
});

test("device count, blank, mode, byte length and digest mismatches fail before publication", (t) => {
  for (const index of [0, 1, 2, 3, 4, "malformed"]) {
    const f = fixture(t); const original = f.adb;
    f.adb = (...args) => {
      const result = original(...args);
      if (f.calls.length === 1 && result.status === 0) {
        const fields = result.stdout.slice("staging-owned\n".length).trim().split(/\s+/);
        if (index === "malformed") result.stdout = `staging-owned\nprivate malformed ${PASSWORD}`;
        else {
          fields[index] = ["3", "1", "1", "644", "0".repeat(64)][index];
          result.stdout = `staging-owned\n${fields.join(" ")}`;
        }
      }
      return result;
    };
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false, index);
    assert.equal(report.reason, "device-credential-structure-mismatch", index);
    assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
    assert.equal(f.calls.length, 2);
    assertNoSecrets(report);
  }
});

test("post-process metadata corruption fails closed without guessing ownership of a completed publication", (t) => {
  const f = fixture(t); const original = f.adb;
  f.adb = (...args) => {
    const result = original(...args);
    if (f.calls.length === 2 && result.status === 0) result.stdout = result.stdout.split("\n")
      .map((line) => /^\d+ \d+ \d+ /.test(line) ? "private malformed metadata" : line).join("\n");
    return result;
  };
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "published-credential-structure-mismatch");
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assert.deepEqual(readFileSync(join(f.app, "files/acceptance/credentials")), Buffer.from(`${USER}\n${PASSWORD}`));
});

test("publication distinguishes collision partial-copy inspection and lost-result failures without unsafe cleanup", (t) => {
  for (const kind of ["collision", "copy", "inspect", "lost-result", "interrupted"]) {
    const f = fixture(t); const original = f.adb;
    f.adb = (...args) => {
      if (f.calls.length === 1) {
        if (kind === "collision") writeFileSync(join(f.app, "files/acceptance/credentials"), "other-owner", { mode: 0o600 });
        if (kind === "copy" || kind === "interrupted") writeFileSync(join(f.bin, "cat"),
          `#!/bin/sh\nprintf partial-copy\n${kind === "interrupted" ? 'kill -TERM "$PPID"' : ""}\nexit 19\n`, { mode: 0o700 });
        if (kind === "inspect") f.failStat("%a", 17);
      }
      const result = original(...args);
      if (kind === "lost-result" && f.calls.length === 2) {
        return { status: null, signal: "private-diagnostic-marker", stdout: "publication-owned\npublication-step create 0\n", stderr: PASSWORD };
      }
      return result;
    };
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "device-publication-failed");
    assert.deepEqual(report.steps.create, { outcome: kind === "collision" ? "failed" : "ok", exitCode: kind === "collision" ? 1 : 0 });
    assert.deepEqual(report.steps.inspect, kind === "inspect" ? { outcome: "failed", exitCode: 17 }
      : { outcome: kind === "lost-result" ? "unavailable" : "not-run", exitCode: null });
    assert.deepEqual(report.steps.publish, kind === "lost-result" ? { outcome: "unavailable", exitCode: null }
      : { outcome: "failed", exitCode: kind === "collision" ? 1 : kind === "inspect" ? 17 : kind === "interrupted" ? 125 : 19 });
    assert.deepEqual(report.steps.cleanup, { outcome: "ok", exitCode: 0 });
    assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["collision", "lost-result"].includes(kind) ? ["credentials"] : []);
    if (kind === "collision") assert.equal(readFileSync(join(f.app, "files/acceptance/credentials"), "utf8"), "other-owner");
    if (["copy", "inspect", "interrupted"].includes(kind)) assert.deepEqual(report.steps.destinationCleanup, { outcome: "ok", exitCode: 0 });
    if (kind === "lost-result") assert.deepEqual(report.steps.destinationCleanup, { outcome: "unavailable", exitCode: null });
    assertPrivateOutcomesOnly(report);
  }
});

test("failed copy cannot roll back a different owner substituted for the still-open destination", (t) => {
  for (const kind of ["regular", "symlink", "directory"]) {
    const f = fixture(t); const original = f.adb;
    f.adb = (...args) => {
      if (f.calls.length === 1) writeFileSync(join(f.bin, "cat"), `#!/bin/sh
printf partial-copy
mv files/acceptance/credentials files/acceptance/original-owned-inode
${kind === "regular" ? 'printf other-owner > files/acceptance/credentials'
    : kind === "symlink" ? 'printf other-owner > files/acceptance/other-owner\nln -s other-owner files/acceptance/credentials'
      : 'mkdir files/acceptance/credentials\nprintf other-owner > files/acceptance/credentials/other-owner'}
exit 19
`, { mode: 0o700 });
      return original(...args);
    };
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(report.destinationOwned, true);
    assert.equal(report.reason, "device-publication-failed");
    assert.deepEqual(report.steps.copy, { outcome: "failed", exitCode: 19 });
    assert.deepEqual(report.steps.destinationCleanup, { outcome: "failed", exitCode: kind === "regular" ? 74 : kind === "symlink" ? 72 : 75 });
    const directory = join(f.app, "files/acceptance");
    assert.equal(readFileSync(join(directory, "credentials", ...(kind === "directory" ? ["other-owner"] : [])), "utf8"), "other-owner");
    assert.equal(readFileSync(join(directory, "original-owned-inode"), "utf8"), "partial-copy");
    assert.equal(existsSync(join(directory, "credentials.pending-fixture-token")), false);
    assertPrivateOutcomesOnly(report);
  }
});

test("uncatchable publication termination retains the partial destination and refuses a retry", (t) => {
  const f = fixture(t); const original = f.adb;
  f.adb = (...args) => {
    if (f.calls.length === 1) writeFileSync(join(f.bin, "cat"),
      '#!/bin/sh\nprintf partial-copy\nkill -KILL "$PPID"\nexit 19\n', { mode: 0o700 });
    return original(...args);
  };
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "device-publication-failed");
  assert.equal(report.destinationOwned, true);
  assert.equal(report.steps.create.exitCode, 0);
  assert.notEqual(report.steps.publish.exitCode, 0);
  for (const phase of ["copy", "inspect", "destinationCleanup"]) {
    assert.deepEqual(report.steps[phase], { outcome: "unavailable", exitCode: null });
  }
  const file = join(f.app, "files/acceptance/credentials");
  assert.equal(readFileSync(file, "utf8"), "partial-copy");
  const retained = statSync(file);
  const retry = stagePhysicalCredentials({ ...f.options, output: join(f.directory, "retry.json") }, f.deps);
  assert.equal(retry.eligible, false);
  assert.equal(retry.reason, "device-staging-failed");
  assert.equal(statSync(file).ino, retained.ino);
  assert.equal(readFileSync(file, "utf8"), "partial-copy");
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assertPrivateOutcomesOnly(report); assertPrivateOutcomesOnly(retry);
});

test("unavailable FD identity cannot authorize removal of an exclusively created destination", (t) => {
  const f = fixture(t); const original = f.adb;
  f.adb = (args, input) => {
    if (f.calls.length === 1) {
      const modified = [...args];
      modified[modified.length - 1] = modified.at(-1).replace("publication_owned=1\n", "publication_owned=1\nexec 3>&-\n");
      assert.notEqual(modified.at(-1), args.at(-1));
      args = modified;
    }
    return original(args, input);
  };
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.destinationOwned, false, "a created FD without identity proof emits no ownership marker");
  assert.deepEqual(report.steps.create, { outcome: "failed", exitCode: 1 });
  assert.deepEqual(report.steps.destinationCleanup, { outcome: "failed", exitCode: 73 });
  assert.deepEqual(readFileSync(join(f.app, "files/acceptance/credentials")), Buffer.alloc(0));
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assertPrivateOutcomesOnly(report);
});

test("cleanup failure preserves its exit code without concealing the publication substeps", (t) => {
  const f = fixture(t); const original = f.adb;
  f.adb = (...args) => {
    const result = original(...args);
    return f.calls.length === 3 ? { status: 23, stdout: PASSWORD, stderr: "private-diagnostic-marker" } : result;
  };
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "device-staging-cleanup-failed");
  assert.deepEqual(report.steps.create, { outcome: "ok", exitCode: 0 });
  assert.deepEqual(report.steps.inspect, { outcome: "ok", exitCode: 0 });
  assert.deepEqual(report.steps.cleanup, { outcome: "failed", exitCode: 23 });
  assertPrivateOutcomesOnly(report);
});

test("zero-byte sentinel verifies exclusive copy stat and cleanup without reading config or touching credentials", (t) => {
  const f = fixture(t);
  const directory = join(f.app, "files/acceptance");
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  const credentials = join(directory, "credentials");
  writeFileSync(credentials, "pre-existing-credential-owner", { mode: 0o600 });
  const before = statSync(credentials);
  const original = f.adb;
  f.adb = (...args) => {
    assert.equal(args[0].join(" ").includes("files/acceptance/credentials"), false);
    return original(...args);
  };
  const options = { serial: f.options.serial, sentinelOnly: true, output: f.options.output };
  const report = diagnosePhysicalPublication(options, f.deps);
  assert.equal(report.eligible, true);
  assert.equal(report.reason, "zero-byte-exclusive-create-copy-stat-cleanup-verified");
  assert.deepEqual(report.observed, { mode: "600", byteLength: 0, linkCount: 1, exclusiveCollisionRefused: true });
  assert.deepEqual(f.keys, []);
  assert.deepEqual(f.calls.map((call) => call.hasInput), [false, false, false]);
  assert.deepEqual(readdirSync(directory), ["credentials"]);
  assert.equal(readFileSync(credentials, "utf8"), "pre-existing-credential-owner");
  assert.equal(statSync(credentials).ino, before.ino);
  assert.equal(statSync(credentials).mode & 0o777, 0o600);
  assert.equal(statSync(options.output).mode & 0o777, 0o600);
  assertPrivateOutcomesOnly(report);
  const scripts = sentinelScripts("test-token");
  for (const script of Object.values(scripts)) assert.doesNotMatch(script, /credentials|sha256sum|\bln\b|\bmv\b/);
});

test("publication identity belongs to the live shell even when its FD is unavailable to metadata children", (t) => {
  const f = fixture(t);
  f.env.TEST_METADATA_HIDES_FD = "1";
  const sentinel = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
  assert.equal(sentinel.eligible, true, JSON.stringify(sentinel.steps));
  assert.deepEqual(sentinel.steps.destinationCleanup, { outcome: "ok", exitCode: 0 });
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
  const credentials = stagePhysicalCredentials({ ...f.options, output: join(f.directory, "credentials.json") }, f.deps);
  assert.equal(credentials.eligible, true, JSON.stringify(credentials.steps));
  assert.deepEqual(readFileSync(join(f.app, "files/acceptance/credentials")), Buffer.from(`${USER}\n${PASSWORD}`));
  assertPrivateOutcomesOnly(sentinel); assertPrivateOutcomesOnly(credentials);
});

test("normal error and caught-signal release complete before EXIT can invalidate the shell descriptor", (t) => {
  for (const kind of ["success", "copy", "inspect", "interrupted"]) {
  const f = fixture(t);
  // Model an EXIT boundary that drops user FDs before running the trap. Keep
  // the original exit status; ordinary explicit finish must avoid this path.
  f.shellPrelude = 'test_restore_exit_status() { return "$1"; }\n';
  const original = f.adb;
  f.adb = (args, input) => {
    if (f.calls.length === 1) {
      if (kind === "copy" || kind === "interrupted") writeFileSync(join(f.bin, "cat"),
        `#!/bin/sh\n${kind === "interrupted" ? 'kill -TERM "$PPID"\n' : ""}exit 19\n`, { mode: 0o700 });
      if (kind === "inspect") f.failStat("%a %s %h", 17);
      const script = args.at(-1).slice(1, -1).replaceAll("'\\''", "'");
      assert.equal(shellQuote(script), args.at(-1));
      const modified = script.replace(/^trap (.+) 0$/m, (_, handler) => {
        const action = handler.startsWith("'") ? handler.slice(1, -1) : handler;
        return `trap ${shellQuote('test_exit_status=$?; set +e; exec 3>&-; printf fired > exit-hook-fired; test_restore_exit_status "$test_exit_status"; ' + action)} 0`;
      });
      assert.notEqual(modified, script);
      args = [...args.slice(0, -1), shellQuote(modified)];
    }
    return original(args, input);
  };
  const report = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
  assert.equal(report.eligible, kind === "success", JSON.stringify(report.steps));
  assert.equal(report.steps.publish.exitCode, { success: 0, copy: 19, inspect: 17, interrupted: 125 }[kind]);
  assert.deepEqual(report.steps.destinationCleanup, { outcome: "ok", exitCode: 0 });
  assert.equal(existsSync(join(f.app, "exit-hook-fired")), false, "normal cleanup must not depend on an EXIT trap");
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
  assertPrivateOutcomesOnly(report);
  }
});

test("sentinel distinguishes copy inspection and cleanup failures and removes only its owned files", (t) => {
  for (const kind of ["copy", "inspect", "cleanup", "metadata"]) {
    const f = fixture(t); const original = f.adb;
    f.adb = (...args) => {
      if (f.calls.length === 1 && kind === "copy") {
        writeFileSync(join(f.bin, "cat"), "#!/bin/sh\nprintf 'private-diagnostic-marker\\n' >&2\nexit 19\n", { mode: 0o700 });
      }
      if (f.calls.length === 1 && kind === "inspect") {
        f.failStat("%a %s %h", 17);
      }
      if (f.calls.length === 2 && kind === "cleanup") {
        writeFileSync(join(f.bin, "rmdir"), "#!/bin/sh\nprintf 'private-diagnostic-marker\\n' >&2\nexit 23\n", { mode: 0o700 });
      }
      const result = original(...args);
      if (f.calls.length === 2 && kind === "metadata") result.stdout = result.stdout.replace("600 0 1", "600 1 1");
      return result;
    };
    const report = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
    assert.equal(report.eligible, false);
    assert.deepEqual(report.steps.create, { outcome: "ok", exitCode: 0 });
    if (kind === "copy") {
      assert.deepEqual(report.steps.copy, { outcome: "failed", exitCode: 19 });
      assert.deepEqual(report.steps.inspect, { outcome: "not-run", exitCode: null });
    } else {
      assert.deepEqual(report.steps.copy, { outcome: "ok", exitCode: 0 });
      assert.deepEqual(report.steps.inspect, { outcome: kind === "inspect" ? "failed" : "ok", exitCode: kind === "inspect" ? 17 : 0 });
    }
    assert.deepEqual(report.steps.cleanup, { outcome: kind === "cleanup" ? "failed" : "ok", exitCode: kind === "cleanup" ? 23 : 0 });
    const remaining = readdirSync(join(f.app, "files/acceptance"));
    assert.deepEqual(remaining, kind === "cleanup" ? [".publication-sentinel-fixture-token"] : []);
    if (kind === "cleanup") assert.deepEqual(readdirSync(join(f.app, "files/acceptance", remaining[0])), []);
    if (kind === "metadata") assert.equal(report.reason, "sentinel-metadata-mismatch");
    assertPrivateOutcomesOnly(report);
  }
});

test("sentinel rejects a shell that unexpectedly accepts its second exclusive open", (t) => {
  const f = fixture(t); const original = f.adb;
  f.adb = (args, input) => {
    if (f.calls.length === 1) {
      const modified = [...args];
      modified[modified.length - 1] = modified.at(-1).replace("set -C; exec 4>", "set +C; exec 4>");
      assert.notEqual(modified.at(-1), args.at(-1));
      return original(modified, input);
    }
    return original(args, input);
  };
  const report = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.observed, null);
  assert.deepEqual(report.steps.inspect, { outcome: "failed", exitCode: 76 });
  assert.deepEqual(report.steps.destinationCleanup, { outcome: "ok", exitCode: 0 });
  assert.deepEqual(report.steps.cleanup, { outcome: "ok", exitCode: 0 });
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
  assertPrivateOutcomesOnly(report);
});

test("sentinel never reuses or cleans an existing directory or symlink from another owner", (t) => {
  for (const kind of ["directory", "symlink"]) {
    const f = fixture(t);
    const directory = join(f.app, "files/acceptance");
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    const ownedElsewhere = join(f.directory, "other-owner");
    mkdirSync(ownedElsewhere, { mode: 0o700 });
    writeFileSync(join(ownedElsewhere, "source"), "untouched", { mode: 0o600 });
    const target = join(directory, ".publication-sentinel-fixture-token");
    if (kind === "symlink") symlinkSync(ownedElsewhere, target);
    else { mkdirSync(target, { mode: 0o700 }); writeFileSync(join(target, "source"), "untouched", { mode: 0o600 }); }
    const report = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, "sentinel-stage-failed");
    assert.equal(f.calls.length, 1);
    assert.deepEqual(report.steps.cleanup, { outcome: "not-run", exitCode: null });
    assert.equal(readFileSync(join(target, "source"), "utf8"), "untouched");
    assert.equal(readFileSync(join(ownedElsewhere, "source"), "utf8"), "untouched");
    assertPrivateOutcomesOnly(report);
  }
});

test("a sentinel destination collision preserves the new owner and reports incomplete namespace cleanup", (t) => {
  const f = fixture(t); const original = f.adb;
  const directory = join(f.app, "files/acceptance/.publication-sentinel-fixture-token");
  f.adb = (...args) => {
    if (f.calls.length === 1) writeFileSync(join(directory, "published"), "other-owner", { mode: 0o600 });
    return original(...args);
  };
  const report = diagnosePhysicalPublication({ serial: f.options.serial, sentinelOnly: true, output: f.options.output }, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.destinationOwned, false);
  assert.deepEqual(report.steps.create, { outcome: "failed", exitCode: 1 });
  assert.deepEqual(report.steps.destinationCleanup, { outcome: "not-run", exitCode: null });
  assert.equal(report.steps.cleanup.outcome, "failed");
  assert.deepEqual(readdirSync(directory), ["published"]);
  assert.equal(readFileSync(join(directory, "published"), "utf8"), "other-owner");
  assertPrivateOutcomesOnly(report);
});

test("an existing credential file is never overwritten or removed on failure", (t) => {
  const f = fixture(t);
  mkdirSync(join(f.app, "files/acceptance"), { recursive: true, mode: 0o700 });
  const file = join(f.app, "files/acceptance/credentials");
  writeFileSync(file, "other-owner", { mode: 0o600 });
  const report = stagePhysicalCredentials(f.options, f.deps);
  assert.equal(report.eligible, false);
  assert.equal(report.reason, "device-staging-failed");
  assert.equal(readFileSync(file, "utf8"), "other-owner");
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
});

test("exclusive staging failures do not clean another owner's file or follow a symlink", (t) => {
  for (const kind of ["existing-staging", "staging-symlink", "directory-symlink"]) {
    const f = fixture(t);
    const protectedFile = join(f.directory, "other-owner");
    writeFileSync(protectedFile, "unchanged", { mode: 0o600 });
    if (kind === "directory-symlink") symlinkSync(f.directory, join(f.app, "files"));
    else {
      mkdirSync(join(f.app, "files/acceptance"), { recursive: true, mode: 0o700 });
      const pending = join(f.app, "files/acceptance/credentials.pending-fixture-token");
      if (kind === "staging-symlink") symlinkSync(protectedFile, pending);
      else writeFileSync(pending, "other-staging-owner", { mode: 0o600 });
    }
    const report = stagePhysicalCredentials(f.options, f.deps);
    assert.equal(report.eligible, false, kind);
    assert.equal(report.reason, "device-staging-failed", kind);
    assert.equal(f.calls.length, 1, "no ownership was granted for cleanup");
    assert.equal(readFileSync(protectedFile, "utf8"), "unchanged", kind);
    if (kind === "existing-staging") {
      assert.equal(readFileSync(join(f.app, "files/acceptance/credentials.pending-fixture-token"), "utf8"), "other-staging-owner");
    }
  }
});

test("private host permissions, symlinks and existing evidence fail before any credential read", (t) => {
  for (const kind of ["config-mode", "config-symlink", "output-mode", "output-exists"]) {
    const f = fixture(t);
    if (kind === "config-mode") chmodSync(f.options.config, 0o644);
    if (kind === "config-symlink") {
      const link = join(f.directory, "config-link"); symlinkSync(f.options.config, link); f.options.config = link;
    }
    if (kind === "output-mode") chmodSync(f.directory, 0o755);
    if (kind === "output-exists") writeFileSync(f.options.output, "existing", { mode: 0o600 });
    assert.throws(() => stagePhysicalCredentials(f.options, f.deps));
    assert.equal(f.calls.length, 0); assert.equal(f.keys.length, 0);
  }
});

test("real CLI stage exit then inspect exit preserves credentials for a separate login reader with both schemas", (t) => {
  for (const schema of ["user-pass", "data-plane-account"]) {
  const f = fixture(t); f.options.schema = schema;
  const reader = join(f.bin, "fixture-reader"); const readerLog = join(f.directory, "reader-keys");
  writeFileSync(reader, `#!${process.execPath}
const fs = require('node:fs'); const args = process.argv.slice(2);
if (args[0] !== '--config' || args[1] !== process.env.TEST_CONFIG || args[2] !== '--schema' || args[3] !== process.env.TEST_SCHEMA || args[4] !== 'get') process.exit(3);
const keys = process.env.TEST_SCHEMA === 'user-pass' ? ['user','pass'] : ['data_plane_account.email','data_plane_account.password'];
fs.appendFileSync(process.env.TEST_READER_LOG, args[5]+'\\n');
if (args[5] === keys[0]) process.stdout.write(${JSON.stringify(USER)});
else if (args[5] === keys[1]) process.stdout.write(${JSON.stringify(PASSWORD)});
else process.exit(4);
`, { mode: 0o700 });
  writeFileSync(join(f.bin, "adb"), `#!${process.execPath}
const fs = require('node:fs'); const cp = require('node:child_process'); const args = process.argv.slice(2);
if (JSON.stringify(args.slice(0,8)) !== JSON.stringify(['-s','fake-device','shell','-T','run-as','com.bringyour.network','sh','-c'])) process.exit(5);
const shell = process.platform === 'darwin' ? '/bin/ksh' : '/bin/sh';
const commandArgs = args.slice(6); commandArgs[0] = shell;
const command = commandArgs.join(' ');
const r = cp.spawnSync(shell, ['-c', process.platform === 'darwin' ? command.replaceAll('/proc/self/fd/3','/dev/fd/3') : command], {cwd:process.env.TEST_APP, input:fs.readFileSync(0), encoding:'utf8', env:process.env});
process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || ''); process.exit(r.status ?? 6);
`, { mode: 0o700 });
  const cli = new URL("./physical_credentials.mjs", import.meta.url).pathname;
  const result = spawnSync(process.execPath, [cli, "--serial", f.options.serial, "--schema", schema, "--config", f.options.config,
    "--output", f.options.output], { encoding: "utf8", timeout: 10_000,
    env: { ...f.env, UR_ACCEPT_TEST_CONFIG_READER: reader, TEST_CONFIG: f.options.config, TEST_SCHEMA: schema,
      TEST_READER_LOG: readerLog, TEST_APP: f.app } });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, ""); assert.equal(result.stderr, "");
  assert.equal(readFileSync(readerLog, "utf8"), schema === "user-pass" ? "user\npass\n" : "data_plane_account.email\ndata_plane_account.password\n");
  const credentials = join(f.app, "files/acceptance/credentials");
  assert.deepEqual(readFileSync(credentials), Buffer.from(`${USER}\n${PASSWORD}`));
  const stagedStat = statSync(credentials);
  assert.equal(stagedStat.nlink, 1, "publication owns an independent file, not a hardlink");
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), ["credentials"]);
  assertNoSecrets(JSON.parse(readFileSync(f.options.output, "utf8")));
  const inspected = spawnSync(process.execPath, [cli, "--inspect-only", "--serial", f.options.serial,
    "--output", join(f.directory, "line-counts.json")], { encoding: "utf8", timeout: 10_000,
    env: { ...f.env, TEST_APP: f.app } });
  assert.equal(inspected.status, 0, inspected.stderr);
  const counts = JSON.parse(inspected.stdout);
  assert.equal(counts.lineCount, 2); assert.equal(counts.nonblankLineCount, 2); assert.equal(counts.newlineCount, 1);
  assert.equal("sha256" in counts, false);
  assertNoSecrets(counts);
  assert.equal(statSync(credentials).ino, stagedStat.ino, "inspection must preserve the published inode");
  assert.equal(statSync(credentials).mode & 0o777, 0o600);
  // New process, after both helper processes have exited: model the app's
  // later File.readLines without inherited file descriptors or cached bytes.
  const loginRead = spawnSync(process.execPath, ["--input-type=module", "-e", `
import fs from 'node:fs'; import path from 'node:path';
const file = path.join(process.env.TEST_APP, 'files/acceptance/credentials');
const text = fs.readFileSync(file, 'utf8');
const lines = text.length ? text.split(/\\r\\n|\\r|\\n/) : [];
if (/[\\r\\n]$/.test(text)) lines.pop();
if (lines.length !== 2 || lines[0] !== ${JSON.stringify(USER)} || lines[1] !== ${JSON.stringify(PASSWORD)}) process.exit(7);
`], { encoding: "utf8", timeout: 10_000, env: { ...f.env, TEST_APP: f.app } });
  assert.equal(loginRead.status, 0);
  assert.equal(loginRead.stdout, ""); assert.equal(loginRead.stderr, "");
  const wrongSchema = schema === "user-pass" ? "data-plane-account" : "user-pass";
  const wrongOutput = join(f.directory, "wrong-schema.json");
  const rejected = spawnSync(process.execPath, [cli, "--serial", f.options.serial, "--schema", wrongSchema,
    "--config", f.options.config, "--output", wrongOutput], { encoding: "utf8", timeout: 10_000,
    env: { ...f.env, UR_ACCEPT_TEST_CONFIG_READER: reader, TEST_CONFIG: f.options.config, TEST_SCHEMA: schema,
      TEST_READER_LOG: readerLog, TEST_APP: f.app } });
  assert.equal(rejected.status, 2);
  assert.equal(rejected.stdout, ""); assert.equal(rejected.stderr, "");
  const failure = JSON.parse(readFileSync(wrongOutput, "utf8"));
  assert.equal(failure.reason, "config-reader-failed");
  assert.equal(failure.expected, null);
  assert.equal(readFileSync(readerLog, "utf8"), schema === "user-pass" ? "user\npass\n" : "data_plane_account.email\ndata_plane_account.password\n");
  assertNoSecrets(failure);
  assert.equal(statSync(credentials).ino, stagedStat.ino, "a later failed helper must not remove prior credentials");
  // Only explicit session cleanup, not helper exit or inspection, removes it.
  rmSync(credentials);
  assert.equal(existsSync(credentials), false);
  const sentinelOutput = join(f.directory, "sentinel.json");
  const sentinel = spawnSync(process.execPath, [cli, "--sentinel-only", "--serial", f.options.serial, "--output", sentinelOutput],
    { encoding: "utf8", timeout: 10_000, env: { ...f.env, TEST_APP: f.app, UR_ACCEPT_TEST_CONFIG_READER: "/not-a-reader" } });
  assert.equal(sentinel.status, 0, sentinel.stderr);
  assert.equal(sentinel.stdout, ""); assert.equal(sentinel.stderr, "");
  const sentinelReport = JSON.parse(readFileSync(sentinelOutput, "utf8"));
  assert.equal(sentinelReport.eligible, true);
  assertPrivateOutcomesOnly(sentinelReport);
  assert.deepEqual(readdirSync(join(f.app, "files/acceptance")), []);
  }
});
