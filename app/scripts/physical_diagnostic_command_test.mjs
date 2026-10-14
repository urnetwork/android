import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { diagnosticPublicationScript, parseArgs, runDiagnosticCommand } from "./physical_diagnostic_command.mjs";
import { diagnosticIdentity } from "./physical_diagnostic_identity.mjs";
import { copyDiagnosticArtifact } from "./physical_diagnostic_copy.mjs";

const quote = value => `'${value.replaceAll("'", "'\\''")}'`;
const initialStatus = { sessionId: "11111111-1111-4111-8111-111111111111", pid: 1234, commandId: "0", state: "ready", phase: "ready", elapsedMs: 100,
  connected: false, tunnelStarted: false, provideEnabled: false, transportMode: "auto" };

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "physical-diagnostic-command-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const app = join(directory, "app");
  const outer = join(directory, "outer-shell");
  const bin = join(directory, "bin");
  const acceptance = join(app, "files", "acceptance");
  mkdirSync(acceptance, { recursive: true, mode: 0o700 });
  mkdirSync(outer, { mode: 0o700 });
  mkdirSync(bin, { mode: 0o700 });
  // Like Android run-as, select the package working directory and execute argv.
  // The outer shell must first reparse ADB's joined remote command string.
  writeFileSync(join(bin, "run-as"), '#!/bin/sh\n[ "$1" = com.bringyour.network ] || exit 90\nshift\ncd "$FAKE_APP" || exit 91\nexec "$@"\n', { mode: 0o700 });
  writeFileSync(join(acceptance, "physical-status"), JSON.stringify(initialStatus), { mode: 0o600 });
  const options = { serial: "fake-device", owner: join(directory, "owner.json"), "command-id": "diag-preflight",
    verb: "owner-census", label: "preflight", output: join(directory, "command.json") };
  writeFileSync(options.owner, "fixture owner is checked separately", { mode: 0o600 });
  const f = { directory, app, outer, acceptance, options, calls: [], now: 0, initial: { ...initialStatus },
    get wire() { return diagnosticIdentity(f.initial.sessionId, options.verb, options.label, options["command-id"]); },
    shell(args, input) {
      assert.deepEqual(args.slice(0, 4), ["-s", "fake-device", "shell", "-T"]);
      f.calls.push(args);
      return spawnSync("sh", ["-c", args.slice(4).join(" ")], { cwd: outer, input, encoding: "utf8", timeout: 5000,
        env: { ...process.env, FAKE_APP: app, PATH: `${bin}:${process.env.PATH}` } });
    },
    complete(overrides = {}) { return { ...f.initial, commandId: f.wire.commandId, phase: `${options.verb}-${f.wire.label}`,
      state: "complete", elapsedMs: 200, ...overrides }; },
    run(dependencies = {}) {
      let checks = 0;
      return runDiagnosticCommand(options, {
        checkSession: () => checks++ === 0 ? f.initial : f.complete(), adb: f.shell,
        now: () => f.now, wallNow: () => 1234567890, sleep: async ms => { f.now += ms; }, ...dependencies,
      });
    },
    noReceipt() {
      assert.equal(existsSync(options.output), false);
      assert.equal(readdirSync(directory).some(name => name.includes(".pending-")), false);
    },
  };
  return f;
}

test("app command acknowledgment contract includes the exact nonempty argument in both running and complete phases", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  // Anchor this fixture to the producer, not just the host's expected-value
  // implementation: if Kotlin's wire contract changes, update both sides.
  assert.match(source, /val argument = parts\.getOrElse\(2\) \{ "" \}/);
  assert.match(source, /phase = when \(verb\) \{\s*"phase" -> argument\.ifEmpty \{ "idle" \}\s*else -> "\$verb\$\{if \(argument\.isEmpty\(\)\) "" else "-\$argument"\}"\s*\}\s*status\(id, "running", application, startElapsedMs\)/);
  for (const verb of ["owner-census", "heap-profile", "goroutine-stacks"]) {
    const branch = source.slice(source.indexOf(`"${verb}" -> {`));
    assert.match(branch.slice(0, branch.indexOf("return false")), /status\(\s*id,\s*"complete",/);
    assert.doesNotMatch(branch.slice(0, branch.indexOf("return false")), /\bphase\s*=/);
  }
});

// Source wiring only: the SDK owns atomic accounting; Android may annotate the
// one returned batch, but must not resample, rebuild, filter or relabel its data.
function assertAtomicDiagnosticCollector(source) {
  const sampler = source.match(/    private fun startSampler\([\s\S]*?(?=\n    private fun status\()/)?.[0];
  assert.ok(sampler, "bounded diagnostic sampler source is required");
  assert.equal((sampler.match(/\btransferDiagnosticSnapshotJson\(/g) ?? []).length, 1,
    "one SDK diagnostic batch read per sampler iteration");
  const batch = sampler.match(/val batch = device\.transferDiagnosticSnapshotJson\(\)[\s\S]*?(?=takePrimitiveSamples\(device, startUnixMs\))/)?.[0];
  assert.ok(batch, "retain the SDK batch before draining independent primitives");
  assert.match(batch, /val sequence = diagnosticBatchesProduced\.incrementAndGet\(\)/, "one batch sequence");
  assert.match(batch, /val parts = batch\.lineSequence\(\)\.filter \{ it\.isNotBlank\(\) \}\.map \{ JSONObject\(it\) \}\.toList\(\)/,
    "parse every nonempty SDK part without filtering its data");
  assert.match(batch, /check\(parts\.count \{ it\.optString\("part"\) == "memory" \} == 1\)/,
    "one runtime observation per diagnostic batch");
  assert.match(batch, /for \(record in parts\) \{[\s\S]*diagnostics\.append\(record\.toString\(\)\)\.append\('\\n'\)/,
    "append each annotated SDK record, not another snapshot or fabricated row");
  const metadata = [
    ["diagnosticBatchSequence", "sequence"], ["pid", "Process.myPid()"],
    ["sessionId", "memorySessionId"], ["buildId", "BuildConfig.URNETWORK_ACCEPTANCE_BUILD_ID"],
    ["memoryProfile", "MainApplication.MEMORY_PROFILE_NAME"], ["phase", "phase"],
  ];
  assert.deepEqual([...batch.matchAll(/\.put\(([^,\n]+),/g)].map(match => match[1]),
    metadata.map(([key]) => JSON.stringify(key)), "only host identity and phase metadata may be added");
  for (const [key, value] of metadata) {
    assert.ok(batch.includes(".put(" + JSON.stringify(key) + ", " + value + ")"), "bind diagnostic metadata: " + key);
  }
  assert.doesNotMatch(batch, /\brecord\.(?:remove|accumulate|append)\(/, "SDK diagnostic data must remain unchanged");
  assert.match(batch, /if \(record\.optString\("part"\) == "memory"\) \{\s*val bytes = physicalMemoryLong\(record\.opt\("go_total_bytes"\)\)\s*physicalMemoryLong\(record\.opt\("go_limit_bytes"\)\)\s*physicalMemoryLong\(record\.opt\("memory_profile_rate_bytes"\)\)\s*diagnosticRuntimePeak\.updateAndGet \{ current -> maxOf\(current, bytes\) \}\s*if \(bytes > GO_RUNTIME_SPIKE_BYTES\) diagnosticRuntimeBreaches\.incrementAndGet\(\)/,
    "retain the actual diagnostic runtime peak and selected-profile breaches");
  assert.match(batch, /diagnostics\.append\(record\.toString\(\)\)\.append\('\\n'\)\s*\}\s*diagnostics\.flush\(\)\s*diagnosticBatchesFlushed\.incrementAndGet\(\)\s*$/,
    "count a flushed batch only after all annotated records are written and flushed");
  assert.match(sampler, /getOrElse \{ error ->\s*exporterFailed\.set\(true\)/, "failed export must remain failed");
  return sampler;
}

test("physical evidence is wired to explicit US, live routes, and the opt-in atomic SDK collector", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  assert.doesNotMatch(source, /connectBestAvailable\(/);
  assert.match(source, /physicalUsCountryIndex\(locations\.map/);
  assert.match(source, /device\.connectedProviderLocations/);
  assert.match(source, /"countryCode", if \(device\.connectEnabled\) physicalLiveCountry\(providers\)/);
  assert.match(source, /"selectedPeerId", physicalSelectedPeer\(/);
  assert.match(source, /"selectedCarrier", physicalSelectedCarriers\(carrierBaseline, carrierBytes\(device\)/);
  assert.match(source, /File\(acceptanceDir, "physical-diagnostics\.ndjson"\)/);
  assertAtomicDiagnosticCollector(source);
  const optIn = source.indexOf("val previousDiagnosticOptIn = Sdk.setTransferDiagnosticSnapshotsEnabled(true)");
  assert.ok(optIn >= 0 && optIn < source.indexOf("withPhysicalCredentialCheckpoints("));
  assert.match(source, /finally \{[\s\S]*?Sdk\.setTransferDiagnosticSnapshotsEnabled\(previousDiagnosticOptIn\)/);
  assert.doesNotMatch(source, /put\("(?:transport_budget_|device_transport_budget_|transfer_root_)/,
    "the SDK owns the atomic budget graph; Kotlin must not fabricate another copy");
});

test("diagnostic collector root regression: identity decoration preserves the batch rejected by the old direct-append predicate", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const sampler = assertAtomicDiagnosticCollector(source);
  assert.doesNotMatch(sampler, /diagnostics\.append\(device\.transferDiagnosticSnapshotJson\(\)\)/,
    "the historical predicate rejects the current identity-bound batch, despite one unchanged SDK read");
});

test("diagnostic collector custody rejects resampling, payload changes, lost identity and premature success", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const sampler = assertAtomicDiagnosticCollector(source);
  const mutations = [
    ["for (record in parts) {", "for (record in parts) {\n device.transferDiagnosticSnapshotJson()", /one SDK diagnostic batch read/],
    ["it.isNotBlank()", 'it.isNotBlank() && !it.contains("memory_device_transport")', /parse every nonempty SDK part/],
    ['it.optString("part") == "memory" } == 1', 'it.optString("part") == "memory" } >= 1', /one runtime observation/],
    ['record.put("diagnosticBatchSequence", sequence)', 'record.put("unix_millis", System.currentTimeMillis()).put("diagnosticBatchSequence", sequence)', /only host identity/],
    ['record.put("diagnosticBatchSequence", sequence)', 'record.put("go_total_bytes", 0).put("diagnosticBatchSequence", sequence)', /only host identity/],
    ['record.put("diagnosticBatchSequence", sequence)', 'record.put("transfer_root_used_bytes", 0).put("diagnosticBatchSequence", sequence)', /only host identity/],
    ['.put("sessionId", memorySessionId)', "", /only host identity/],
    ['.put("memoryProfile", MainApplication.MEMORY_PROFILE_NAME)', '.put("memoryProfile", "android")', /bind diagnostic metadata: memoryProfile/],
    ["diagnostics.append(record.toString())", 'record.remove("go_limit_bytes")\n diagnostics.append(record.toString())', /SDK diagnostic data must remain unchanged/],
    ["diagnostics.append(record.toString())", "diagnostics.append(device.transferDiagnosticSnapshotJson())", /one SDK diagnostic batch read/],
    ["diagnostics.append(record.toString())", "diagnostics.append(JSONObject().toString())", /append each annotated SDK record/],
    ["diagnostics.flush()", "diagnosticBatchesFlushed.incrementAndGet()\n diagnostics.flush()", /count a flushed batch only after/],
    ["diagnosticRuntimePeak.updateAndGet { current -> maxOf(current, bytes) }", "", /retain the actual diagnostic runtime peak/],
    ["bytes > GO_RUNTIME_SPIKE_BYTES", "bytes > Long.MAX_VALUE", /retain the actual diagnostic runtime peak/],
    ["exporterFailed.set(true)", "exporterFailed.set(false)", /failed export must remain failed/],
  ];
  for (const [before, after, reason] of mutations) {
    assert.equal(sampler.split(before).length, 2, "mutation must touch exactly one sampler site: " + before);
    const changed = source.replace(sampler, sampler.replace(before, after));
    assert.throws(() => assertAtomicDiagnosticCollector(changed), reason);
  }
});

for (const [verb, label] of [
  ["owner-census", "preflight"],
  ["heap-profile", "idle-before-gc"],
  ["goroutine-stacks", "quiet.after_35"],
]) {
  test(`${verb} root regression: exact labeled app phase qualifies, not a bare verb`, async (t) => {
    const f = fixture(t);
    Object.assign(f.options, { verb, label });
    const phase = `${verb}-${f.wire.label}`;
    let checks = 0;
    const statuses = [initialStatus, f.complete({ state: "running", phase }), f.complete({ phase })];
    const report = await f.run({ checkSession: () => statuses[Math.min(checks++, 2)], adb: () => ({ status: 0 }) });
    assert.equal(checks, 3);
    assert.equal(report.status.phase, phase);
    assert.equal(report.status.commandId, f.wire.commandId);
    assert.equal(report.status.state, "complete");
  });

  test(`${verb} acknowledgment rejects wrong labels, bare verbs, lookalikes, IDs, states and identities`, async (t) => {
    const otherVerb = verb === "owner-census" ? "heap-profile" : "owner-census";
    const wire = diagnosticIdentity(initialStatus.sessionId, verb, label, "diag-preflight");
    const phase = `${verb}-${wire.label}`;
    const deltas = [verb, `${verb}-wrong-label`, `${otherVerb}-${label}`, `${phase}-extra`, `prefix-${phase}`,
      `${verb}-${label}`, `${verb}-${wire.label.toUpperCase()}`, `${verb}-`, `${verb}-${label} `].map(value => ({ phase: value }));
    deltas.push({ commandId: "another-command" }, { state: "error" }, { state: "ready" }, { pid: 5678 });
    for (const delta of deltas) {
      const f = fixture(t);
      Object.assign(f.options, { verb, label });
      let checks = 0;
      await assert.rejects(f.run({ checkSession: () => checks++ === 0 ? initialStatus : f.complete(delta),
        adb: () => ({ status: 0 }) }), /diagnostic-command-rejected|concurrent-diagnostic-command-observed|diagnostic-session-changed/);
      f.noReceipt();
    }
  });
}

test("J49FvL root cause: unquoted ADB sh -c executes redirects outside run-as despite intact app storage", (t) => {
  const f = fixture(t);
  const tmp = `files/acceptance/physical-command.${f.options["command-id"]}`;
  const old = `umask 077; set -C; : > ${tmp}; cat > ${tmp}; mv ${tmp} files/acceptance/physical-command`;
  const result = f.shell(["-s", "fake-device", "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c", old], "fixture\n");
  assert.notEqual(result.status, 0);
  assert.match(result.stdout, /^[0-7]{3,4}\r?\n$/, "umask had no argument inside run-as");
  assert.equal(existsSync(join(f.acceptance, "physical-status")), true, "app acceptance storage was never missing");
  assert.equal(existsSync(join(f.acceptance, "physical-command")), false);
  assert.equal(existsSync(join(f.acceptance, `physical-command.${f.options["command-id"]}`)), false);
  const oldCheck = f.shell(["-s", "fake-device", "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c",
    "test -d files && test -d files/acceptance && test -f files/acceptance/physical-status"]);
  assert.notEqual(oldCheck.status, 0, "argumentless inner test falsely rejects a present directory");
});

test("quoting alone is insufficient: a second noclobber open rejects the precreated command", (t) => {
  const f = fixture(t);
  const tmp = `files/acceptance/physical-command.${f.options["command-id"]}`;
  const script = `set -e; umask 077; set -C; : > ${tmp}; cat > ${tmp}; mv ${tmp} files/acceptance/physical-command`;
  const result = f.shell(["-s", "fake-device", "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c", quote(script)], "fixture\n");
  assert.notEqual(result.status, 0);
  assert.equal(statSync(join(f.app, tmp)).size, 0);
  assert.equal(existsSync(join(f.acceptance, "physical-command")), false);
});

for (const verb of ["owner-census", "heap-profile", "goroutine-stacks"]) {
  test(`${verb} publishes one exact private command in the app context and waits for its complete acknowledgment`, async (t) => {
    const f = fixture(t);
    f.options.verb = verb;
    let checks = 0;
    const report = await f.run({ checkSession: () => [initialStatus, initialStatus,
      f.complete({ state: "running" }), f.complete()][Math.min(checks++, 3)] });
    const command = join(f.acceptance, "physical-command");
    assert.equal(readFileSync(command, "utf8"), `${f.wire.commandId}|${verb}|${f.wire.label}\n`);
    assert.equal(statSync(command).mode & 0o777, 0o600);
    assert.equal(existsSync(`${command}.${f.wire.commandId}`), false);
    assert.equal(existsSync(join(f.outer, "files")), false);
    assert.equal(f.calls.length, 1, "never retries command publication");
    assert.equal(checks, 4);
    assert.equal(report.status.state, "complete");
    assert.equal(report.verb, verb);
    assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
    assert.deepEqual(JSON.parse(readFileSync(f.options.output, "utf8")), report);
    assert.equal(readdirSync(f.directory).some(name => name.includes(".pending-")), false);
  });
}

test("genuinely missing or untrusted device setup fails closed without recreating or clobbering evidence", async (t) => {
  const cases = [
    ["directory-missing", "diagnostic-session-directory-missing"],
    ["directory-symlink", "diagnostic-session-directory-untrusted"],
    ["status-missing", "diagnostic-session-status-missing"],
    ["artifact-exists", "diagnostic-artifact-already-exists"],
    ["artifact-symlink", "diagnostic-artifact-already-exists"],
    ["temporary-exists", "diagnostic-exclusive-write-failed"],
  ];
  for (const [kind, reason] of cases) {
    const f = fixture(t);
    const saved = join(f.directory, "saved-acceptance");
    const artifact = join(f.acceptance, f.wire.artifactName);
    const tmp = join(f.acceptance, `physical-command.${f.wire.commandId}`);
    if (kind.startsWith("directory-")) {
      renameSync(f.acceptance, saved);
      if (kind.endsWith("symlink")) symlinkSync(saved, f.acceptance);
    }
    if (kind === "status-missing") rmSync(join(f.acceptance, "physical-status"));
    if (kind === "artifact-exists") writeFileSync(artifact, "preserve", { mode: 0o600 });
    if (kind === "artifact-symlink") symlinkSync(join(f.directory, "missing-target"), artifact);
    if (kind === "temporary-exists") writeFileSync(tmp, "preserve", { mode: 0o600 });
    await assert.rejects(f.run(), new RegExp(reason));
    f.noReceipt();
    assert.equal(f.calls.length, 1);
    assert.equal(existsSync(join(f.acceptance, "physical-command")), false);
    if (kind === "directory-missing") assert.equal(existsSync(f.acceptance), false);
    if (kind === "artifact-exists") assert.equal(readFileSync(artifact, "utf8"), "preserve");
    if (kind === "temporary-exists") assert.equal(readFileSync(tmp, "utf8"), "preserve");
  }
});

test("all diagnostic verbs reject existing outputs before issuing their command", async (t) => {
  for (const verb of ["heap-profile", "goroutine-stacks"]) {
    const f = fixture(t);
    f.options.verb = verb;
    const file = f.wire.artifactName;
    writeFileSync(join(f.acceptance, file), "preserve", { mode: 0o600 });
    await assert.rejects(f.run(), /diagnostic-artifact-already-exists/);
    assert.equal(readFileSync(join(f.acceptance, file), "utf8"), "preserve");
    f.noReceipt();
  }
});

test("sequential sessions reuse logical preflight labels without colliding with or deleting legacy artifacts", async t => {
  for (const [verb, legacy] of [["owner-census", "physical-owners-preflight.json"], ["heap-profile", "physical-heap-preflight.pprof"],
    ["goroutine-stacks", "physical-stacks-preflight.txt"]]) {
    const f = fixture(t);
    f.options.verb = verb;
    const oldPath = join(f.acceptance, legacy);
    writeFileSync(oldPath, "unknown-legacy-owner", { mode: 0o600 });
    const reports = [];
    for (const [index, sessionId] of [initialStatus.sessionId, "22222222-2222-4222-8222-222222222222"].entries()) {
      f.initial = { ...initialStatus, sessionId }; // PID may be reused; owner UUID must not be.
      f.options.output = join(f.directory, `session-${index}.json`);
      const report = await f.run({ adb: (args, input) => {
        const result = f.shell(args, input);
        if (result.status === 0) writeFileSync(join(f.acceptance, f.wire.artifactName), `session-${index}`, { flag: "wx", mode: 0o600 });
        return result;
      } });
      reports.push(report);
      assert.equal(report.schema, 2);
      assert.equal(report.label, "preflight");
      assert.equal(report.commandId, "diag-preflight");
      assert.equal(report.status.sessionId, sessionId);
      const output = join(f.directory, `copied-${index}`);
      copyDiagnosticArtifact({ serial: f.options.serial, receipt: f.options.output, output }, { adb: (args, fd) => {
        assert.equal(args.at(-1), `files/acceptance/${report.wire.artifactName}`);
        writeFileSync(fd, readFileSync(join(f.app, args.at(-1))));
        return { status: 0 };
      } });
      assert.equal(readFileSync(output, "utf8"), `session-${index}`);
      assert.equal(readFileSync(oldPath, "utf8"), "unknown-legacy-owner");
    }
    assert.notEqual(reports[0].wire.label, reports[1].wire.label);
    assert.notEqual(reports[0].wire.commandId, reports[1].wire.commandId);
    for (let index = 0; index < reports.length; index++) {
      assert.equal(readFileSync(join(f.acceptance, reports[index].wire.artifactName), "utf8"), `session-${index}`);
    }
  }
});

test("same-session logical labels and command IDs remain one-shot even after another command or failed publication", async t => {
  for (const kind of ["label", "command", "failed-publication"]) {
    const f = fixture(t);
    if (kind === "failed-publication") {
      await assert.rejects(f.run({ adb: () => ({ status: 1 }) }), /diagnostic-command-publication-failed/);
    } else await f.run();
    f.initial = { ...initialStatus, state: "complete", commandId: "intervening-command" };
    f.options.output = join(f.directory, "retry.json");
    if (kind === "label") f.options["command-id"] = "another-command-id";
    if (kind === "command") f.options.label = "another-label";
    await assert.rejects(f.run({ adb: () => assert.fail("reserved attempt cannot reach the device") }),
      kind === "label" ? /diagnostic-label-already-attempted/ : /diagnostic-command-already-attempted/);
    f.noReceipt();
    const reservations = readdirSync(f.directory).filter(name => name.endsWith(".attempt.json"));
    assert.ok(reservations.length >= 2);
    for (const name of reservations) assert.equal(statSync(join(f.directory, name)).mode & 0o7777, 0o600);
  }
});

test("session namespace is mandatory and cannot change during acknowledgment", async t => {
  for (const sessionId of [undefined, "", "unbound", "1".repeat(36)]) {
    const f = fixture(t);
    await assert.rejects(f.run({ checkSession: () => ({ ...initialStatus, sessionId }),
      adb: () => assert.fail("unbound session cannot publish") }), /diagnostic-session-identity-required/);
    f.noReceipt();
  }
  const f = fixture(t);
  let reads = 0;
  await assert.rejects(f.run({ checkSession: () => reads++ === 0 ? initialStatus :
    f.complete({ sessionId: "22222222-2222-4222-8222-222222222222" }) }), /diagnostic-session-changed/);
});

test("full-length logical labels remain bounded and map injectively across session, ID and label changes", () => {
  const label = "x".repeat(64), id = "y".repeat(64);
  const wire = diagnosticIdentity(initialStatus.sessionId, "owner-census", label, id);
  assert.match(wire.label, /^[a-f0-9]{64}$/);
  assert.match(wire.commandId, /^[a-f0-9]{64}$/);
  assert.deepEqual(diagnosticIdentity(initialStatus.sessionId, "owner-census", label, id), wire);
  assert.notEqual(diagnosticIdentity(initialStatus.sessionId, "owner-census", `${label.slice(0, -1)}z`, id).label, wire.label);
  assert.notEqual(diagnosticIdentity(initialStatus.sessionId, "owner-census", label, `${id.slice(0, -1)}z`).commandId, wire.commandId);
  assert.equal(diagnosticIdentity(initialStatus.sessionId, "owner-census", label, "another-id").label, wire.label);
  assert.notEqual(wire.label, wire.commandId);
});

test("a stale, stopped, unbound, or busy instrumentation session cannot publish a command", async (t) => {
  for (const kind of ["check-failure", "running", "error", "same-id"]) {
    const f = fixture(t);
    await assert.rejects(f.run({ checkSession: () => {
      if (kind === "check-failure") throw new Error("private details must stay private");
      return kind === "same-id" ? f.complete() : { ...initialStatus, state: kind };
    } }), /live-bound-instrumentation-session-required|idle-session-and-fresh-command-required/);
    assert.equal(f.calls.length, 0);
    f.noReceipt();
  }
});

test("PID, role, clock, phase, command and terminal changes cannot manufacture completion", async (t) => {
  for (const delta of [{ pid: 5678 }, { elapsedMs: 50 }, { connected: true }, { tunnelStarted: true },
    { provideEnabled: true }, { transportMode: "h3" }, { phase: "snapshot" }, { state: "error" },
    { state: "ready" }, { commandId: "unrelated-command" }]) {
    const f = fixture(t);
    let checks = 0;
    await assert.rejects(f.run({ checkSession: () => checks++ === 0 ? initialStatus : f.complete(delta) }),
      /diagnostic-session-changed|diagnostic-command-rejected|concurrent-diagnostic-command-observed/);
    assert.equal(f.calls.length, 1);
    f.noReceipt();
  }
  const f = fixture(t);
  let checks = 0;
  await assert.rejects(f.run({ checkSession: () => {
    if (checks++ === 0) return initialStatus;
    throw new Error("terminated private owner");
  } }), /live-bound-instrumentation-session-required/);
  f.noReceipt();
});

test("running acknowledgments cannot roll back to an older status or earlier clock", async (t) => {
  for (const revert of [true, false]) {
    const f = fixture(t);
    const statuses = [initialStatus, f.complete({ state: "running", elapsedMs: 300 }),
      revert ? { ...initialStatus, elapsedMs: 400 } : f.complete({ elapsedMs: 200 })];
    let checks = 0;
    await assert.rejects(f.run({ checkSession: () => statuses[Math.min(checks++, 2)] }), /diagnostic-session-changed/);
    f.noReceipt();
  }
});

test("missing completion has a bounded deadline and never retries publication", async (t) => {
  const f = fixture(t);
  await assert.rejects(f.run({ checkSession: () => initialStatus }), /diagnostic-command-acknowledgment-timeout/);
  assert.equal(f.now, 30000);
  assert.equal(f.calls.length, 1);
  f.noReceipt();
});

test("transport publication failures retain no success receipt or raw error", async (t) => {
  const f = fixture(t);
  await assert.rejects(f.run({ adb: () => ({ status: 1, stderr: "private device exception", stdout: "private data" }) }),
    error => error.message === "diagnostic-command-publication-failed");
  f.noReceipt();
});

test("explicit bounded arguments and fresh private host output are required before device access", async (t) => {
  const f = fixture(t);
  const argv = Object.entries(f.options).flatMap(([key, value]) => [`--${key}`, value]);
  assert.deepEqual(parseArgs(argv), f.options);
  for (const args of [[], [...argv, "--verb", "owner-census"], [...argv, "--unknown", "x"],
    argv.slice(0, -1), argv.map(value => value === "preflight" ? "../bad" : value)]) {
    assert.throws(() => parseArgs(args), /explicit-diagnostic-arguments-required/);
  }
  for (const change of [{ label: "x|finish|" }, { label: "x;touch bad" }, { label: "a".repeat(65) },
    { "command-id": "../bad" }, { verb: "finish" }, { verb: "__proto__" }, { owner: "relative" }, { output: "relative" }]) {
    await assert.rejects(runDiagnosticCommand({ ...f.options, ...change }, { adb: () => assert.fail("invalid arguments must not reach adb") }));
    assert.throws(() => diagnosticPublicationScript({ ...f.options, ...change, ...(change.owner || change.output ? { label: "../invalid" } : {}) }));
  }
  writeFileSync(f.options.output, "preserve", { mode: 0o600 });
  await assert.rejects(f.run(), /fresh-diagnostic-receipt-required/);
  assert.equal(readFileSync(f.options.output, "utf8"), "preserve");
  assert.equal(f.calls.length, 0);
  rmSync(f.options.output);
  chmodSync(f.directory, 0o755);
  await assert.rejects(f.run(), /artifact-directory-mode-not-0700/);
  assert.equal(f.calls.length, 0);
});

test("ioloop-clock retains exact same-process nanoseconds and actual labeled acknowledgment", async (t) => {
  const f = fixture(t);
  Object.assign(f.options, { verb: "ioloop-clock", label: "fast-1-before" });
  const times = [9007199254740993n, 9007199254741009n];
  const report = await f.run({ monotonicNanos: () => times.shift(), adb: () => ({ status: 0 }) });
  assert.deepEqual(report.clockBracket, { source: "host-hrtime-v1", beforeNs: "9007199254740993", afterNs: "9007199254741009" });
  assert.equal(report.qualificationEligible, false);
  assert.equal(report.status.phase, "ioloop-clock-" + f.wire.label);
  assert.equal(f.wire.artifactName, "physical-ioloop-clock-" + f.wire.label + ".json");
  assert.equal(times.length, 0);
});

test("native-loop diagnostic is opt-in at the actual JNI call and fenced after retained done posts", () => {
  const service = readFileSync(new URL("../app/src/main/java/com/bringyour/network/MainService.kt", import.meta.url), "utf8");
  const app = readFileSync(new URL("../app/src/main/java/com/bringyour/network/MainApplication.kt", import.meta.url), "utf8");
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const cold = readFileSync(new URL("../app/src/acceptanceShared/java/com/bringyour/network/acceptance/PhysicalIoLoopDiagnostic.kt", import.meta.url), "utf8");
  assert.match(app, /var ioLoopDiagnostics: com\.bringyour\.sdk\.IoLoopDiagnosticSession\? = null/);
  assert.match(service, /diagnostic\.newIoLoop\(deviceLocal, pfd\.detachFd\(\), doneCallback\)/);
  assert.match(service, /Sdk\.newIoLoop\(deviceLocal, pfd\.detachFd\(\), doneCallback\)/, "public default branch remains real");
  assert.match(service, /if \(packetFlow === endedPacketFlow\)/, "an old callback may not retire a replacement");
  assert.match(service, /if \(app\.service\?\.get\(\) == this@MainService\)/, "a detached service cannot schedule a replacement");
  assert.match(service, /app\.device\?\.let \{ device ->/, "actual construction rechecks the current device");
  assert.match(app, /vpnServiceUpdateGeneration \+= 1/, "logout invalidates retained service updates");
  assert.match(service, /mainHandler\.post/);
  assert.match(service, /check\(Looper\.myLooper\(\) == mainLooper\)/);
  assert.match(service, /check\(packetFlow == null && boundDevice == null\)/);
  assert.match(service, /check\(tunnelRetryKind != TunnelRetryKind\.CONFIGURATION\)/);
  assert.match(source, /physicalIoLoopDiagnosticRequested\(ioLoopDiagnosticOption, BuildConfig\.DEBUG\)/);
  assert.match(source, /mainFence = \{\s*instrumentation\.runOnMainSync \{\s*check\(application\.ioLoopDiagnostics === diagnostic\)\s*check\(application\.device == null\)/);
  assert.match(source, /diagnostic\.sealAndSnapshotJson\(remainingJoinMillis\)/);
  assert.match(source, /if \(application\.ioLoopDiagnostics === diagnostic\) application\.ioLoopDiagnostics = null/);
  const order = ['attempt("ioloop-fence-before",', 'attempt("ioloop-native-join")',
    'attempt("ioloop-fence-after",', 'attempt("ioloop-receipt")', 'attempt("ioloop-diagnostic-release",'].map(value => cold.indexOf(value));
  assert.ok(order.every((value, index) => value >= 0 && (index === 0 || order[index - 1] < value)));
  assert.match(source, /writePrivate\(output, record\.toString\(\) \+ "\\n"\)\s*ioLoopDiagnosticReceiptName = output\.name\s*check\(record\.getBoolean\("complete"\)\)/,
    "incomplete joined evidence is written before its assertion");
});

