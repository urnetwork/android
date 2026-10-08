#!/usr/bin/env node
// One retained host invocation for scoped H1 qualification or owner attribution.
// Existing helpers decide eligibility; this module owns ordering and joins.
// No retries, profile overrides, hidden uninstalls or unknown credential removal.
import { spawn, spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, lstatSync, openSync, readFileSync,
  readdirSync, realpathSync, writeFileSync, writeSync } from "node:fs";
import { availableParallelism } from "node:os";
import { basename, dirname, isAbsolute, join, relative, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { performanceSerials } from "./physical_apk_pair.mjs";
import { requireNativeConsumerLock, hashNativeInputFile } from "./physical_native_provenance.mjs";
import { requireRetainedForeground, checkInstrumentationCommandSession, checkCollectorSession } from "./physical_collector_session.mjs";
import { requireCredentialTargetStopped } from "./physical_credential_ownership.mjs";
import { credentialPayload } from "./physical_credentials.mjs";
import { requireCredentialParserPreflight } from "./physical_credentials_preflight.mjs";
import { requireCompletedWorkloads } from "./physical_workload_receipt.mjs";
import { GO_RUNTIME_LIMIT_BYTES, MEMORY_AUDIT_PROFILE } from "./physical_memory_profile.mjs";
import { evaluateRuntimeReads, evaluateDiagnosticRuntimeReads, evaluateAuxiliaryRuntimeValues, selectMemoryTeardownReceipt,
  evaluateProducerMemorySummaries } from "./physical_memory_teardown.mjs";

const SELF = fileURLToPath(import.meta.url);
const SCRIPTS = dirname(SELF);
const APP = "com.bringyour.network";
const PROFILE = MEMORY_AUDIT_PROFILE;
const CHILDREN = "wiki,fast-1,fast-2,fast-3";
const LABEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/;
const DIAGNOSTIC_TAIL_MS = 45_000;
export const LIMITS = Object.freeze({ command: 30_000, native: 3_600_000, ready: 180_000,
  role: 160_000, workloads: 950_000, quiet: 425_000, finish: 180_000, stop: 30_000, kill: 5_000 });
class ArmError extends Error {}
const fail = reason => { throw new ArmError(reason); };
const safeReason = error => error instanceof ArmError ? error.message : "h1-arm-operation-failed";
const quote = text => `'${String(text).replaceAll("'", "'\\''")}'`;
const delay = ms => new Promise(done => setTimeout(done, ms));
const privateText = path => {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid() ||
      (stat.mode & 0o7777) !== 0o600 || stat.size > 2 * 1024 * 1024) fail("private-arm-evidence-required");
  return readFileSync(path, "utf8");
};
const privateJson = path => JSON.parse(privateText(path));
const publish = (path, value) => {
  requireArtifactPaths(prepareArtifactDirectory(dirname(path)), [path]);
  writeFileSync(path, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
};

export function parseArgs(argv, cpuCount = availableParallelism(), dependencies = {}) {
  const mode = argv[0];
  if (mode === "consume") {
    if (argv.length !== 3 || argv[1] !== "--manifest" || !isAbsolute(argv[2])) fail("explicit-consumer-manifest-required");
    return { mode, manifest: argv[2] };
  }
  const requiredKeys = ["root", "run-dir", "serial", "label", "build-id", "underlay", "config", "cdp-port", "gomaxprocs", "max-workers"];
  const keys = [...requiredKeys, "measurement-mode"];
  const options = { mode };
  if (!["run", "dry-run"].includes(mode)) fail("run-or-dry-run-required");
  for (let index = 1; index < argv.length; index += 2) {
    const key = argv[index]?.slice(2); const value = argv[index + 1];
    if (!argv[index]?.startsWith("--") || !keys.includes(key) || options[key] !== undefined ||
        !value || value.startsWith("--") || /[\0\r\n]/.test(value)) fail("explicit-h1-arm-arguments-required");
    options[key] = value;
  }
  if (requiredKeys.some(key => options[key] === undefined)) fail("explicit-h1-arm-arguments-required");
  options["measurement-mode"] ??= "qualification";
  armMeasurementMode(options);
  // the allowed phones come from tests.yml; a reason naming the key survives
  let serials;
  try { serials = performanceSerials(dependencies); } catch (error) { fail(error.message); }
  if (!serials.includes(options.serial)) fail("allowlisted-device-required");
  if (!LABEL.test(options.label) || !LABEL.test(options["build-id"])) fail("safe-arm-identifiers-required");
  if (!["wifi", "cellular"].includes(options.underlay)) fail("explicit-underlay-required");
  for (const key of ["root", "run-dir", "config"]) {
    if (!isAbsolute(options[key]) || options[key] !== resolve(options[key])) fail("absolute-normalized-arm-paths-required");
  }
  const root = options.root; const run = options["run-dir"];
  const inside = (parent, child) => child === parent || !relative(parent, child).startsWith(`..`) && !isAbsolute(relative(parent, child));
  if (root === "/" || run === "/" || inside(root, run) || inside(run, root) || inside(run, options.config)) {
    fail("private-run-outside-workspace-required");
  }
  for (const key of ["cdp-port", "gomaxprocs", "max-workers"]) {
    if (!/^[1-9][0-9]*$/.test(options[key])) fail("bounded-positive-arm-limits-required");
    options[key] = Number(options[key]);
  }
  if (options["cdp-port"] < 1024 || options["cdp-port"] > 65535 ||
      options.gomaxprocs > Math.ceil(cpuCount * 0.7) ||
      options["max-workers"] > Math.min(4, options.gomaxprocs)) fail("host-resource-budget-exceeded");
  return options;
}

function armMeasurementMode(c) {
  const mode = c["measurement-mode"] ?? "qualification";
  if (!["qualification", "diagnostic"].includes(mode)) fail("qualification-or-diagnostic-mode-required");
  return mode;
}

function armProfileRate(c) { return armMeasurementMode(c) === "diagnostic" ? 65536 : 0; }

export function h1AssemblyStep(c) {
  return { id: "app-assembly", command: join(c.root, "android/app/gradlew"),
    args: [":app:assembleGithubDebug", ":app:assembleGithubDebugAndroidTest", "--max-workers", String(c["max-workers"]),
      `-PurnetworkAcceptanceBuildId=${c["build-id"]}`, `-PurnetworkMemoryProfile=${PROFILE}`,
      `-PurnetworkMemoryProfileRateBytes=${armProfileRate(c)}`],
    cwd: join(c.root, "android/app"), timeoutMs: LIMITS.native };
}

export function armContext(options, buildOwner = `h1-${options.label}-${randomUUID()}`) {
  const artifacts = join(options["run-dir"], "private"); const directory = join(artifacts, options.label);
  const p = name => join(directory, name); const a = name => join(artifacts, `${options.label}.${name}`);
  return { ...options, artifacts, directory, buildOwner, scripts: join(options.root, "android/app/scripts"),
    manifest: join(options["run-dir"], "arm.json"), before: a("native-before.json"), after: a("native-after.json"),
    proof: a("native-proof.json"), writer: a("native-writer.json"), observed: a("apk-devices.json"),
    selection: a("apk-pair.json"), app: a("app.apk"), test: a("test.apk"), aar: a("sdk.aar"),
    owner: a("instrumentation-owner.json"), credentialOwner: a("credential-owner.json"),
    parser: a("credential-parser.json"), collector: p("collector-owner.json"), telemetry: p("telemetry.ndjson"),
    workloads: p("workloads.json"), start: p("quiet-start.json"), end: p("quiet-end.json"),
    memory: p("physical-memory.ndjson"), finalMemory: p("physical-memory-final.ndjson"),
    statusRuntime: p("physical-memory-status.ndjson"), finalStatusRuntime: p("physical-memory-status-final.ndjson"),
    diagnostics: p("physical-diagnostics.ndjson"), finalDiagnostics: p("physical-diagnostics-final.ndjson"),
    teardownMemory: p("physical-memory-teardown.json"), finishStatus: p("physical-finish-status.json"),
    teardownFallback: p("physical-memory-teardown-incomplete.json"),
    liveGate: p("quiet-gate.json"), finalGate: p("teardown-gate.json"),
    connectId: `h1-${options.label}`, finishId: `finish-${options.label}` };
}

// The same descriptors drive execution and dry-run. There is no second shell
// recipe for an operator to reconstruct or a test-only alternate schedule.
export function h1Steps(c) {
  const p = name => join(c.directory, name); const a = name => join(c.artifacts, `${c.label}.${name}`);
  const node = (id, helper, args, extra = {}) => ({ id, kind: "command", command: process.execPath,
    args: [join(c.scripts, helper), ...args], ...extra });
  const shell = (id, helper, args, extra = {}) => ({ id, kind: "command", command: "bash",
    args: [join(c.scripts, helper), ...args], ...extra });
  const adb = (id, args, extra = {}) => ({ id, kind: "command", command: "adb", args: ["-s", c.serial, ...args], ...extra });
  const gateArgs = memory => ["--start", c.start, "--end", c.end, "--memory", memory,
    "--status-runtime", memory === c.finalMemory ? c.finalStatusRuntime : c.statusRuntime,
    "--diagnostics", memory === c.finalMemory ? c.finalDiagnostics : c.diagnostics,
    "--telemetry", c.telemetry, "--phase", `quiet-${c.label}`, "--role", "client", "--underlay", c.underlay];
  const diagnostic = armMeasurementMode(c) === "diagnostic";
  const profileRate = String(armProfileRate(c));
  const boundary = name => {
    const readings = [["before-gc", "owner-census", "json"], ["heap", "heap-profile", "pprof"],
      ["after-gc", "owner-census", "json"], ["stacks", "goroutine-stacks", "txt"]];
    return [{ id: `diagnostic-${name}-boundary`, kind: "diagnostic-boundary", boundary: name },
      ...readings.flatMap(([part, verb, extension]) => {
        const id = `diagnostic-${name}-${part}`;
        const receipt = a(`${id}.command.json`); const output = a(`${id}.${extension}`);
        return [node(id, "physical_diagnostic_command.mjs", ["--serial", c.serial, "--owner", c.owner,
          "--command-id", id, "--verb", verb, "--label", `${name}-${part}`, "--output", receipt], { timeoutMs: 40_000 }),
        node(`${id}-copy`, "physical_diagnostic_copy.mjs", ["--serial", c.serial, "--receipt", receipt, "--output", output]),
        ...(verb === "owner-census" ? [{ id: `${id}-check`, kind: "diagnostic-census-check", path: output,
          ...(part === "after-gc" ? { before: a(`diagnostic-${name}-before-gc.json`) } : {}) }] : [])];
      })];
  };
  return {
    setup: [
      node("aapt-resolve", "physical_aapt.mjs", []),
      node("apk-observe", "physical_apk_pair.mjs", ["observe", "--output", c.observed]),
      node("native-before", "physical_native_provenance.mjs", ["capture", "--phase", "before", "--root", c.root,
        "--build-owner", c.buildOwner, "--build-id", c["build-id"], "--profile-rate", profileRate, "--output", c.before], { timeoutMs: LIMITS.native }),
      shell("native-writer", "physical_native_writer.sh", ["--root", c.root, "--before", c.before, "--build-id", c["build-id"],
        "--profile-rate", profileRate, "--memory-profile", PROFILE, "--max-workers", String(c["max-workers"]), "--receipt", c.writer,
        "--stdout", a("writer-child.stdout"), "--stderr", a("writer-child.stderr")], { timeoutMs: LIMITS.native }),
      shell("native-consumer", "physical_native_consumer.sh", ["--root", c.root, "--before", c.before,
        "--after", c.after, "--proof", c.proof, "--writer-receipt", c.writer, "--", process.execPath,
        join(c.scripts, "physical_h1_arm.mjs"), "consume", "--manifest", c.manifest], { timeoutMs: LIMITS.native }),
      node("native-install-check", "physical_native_provenance.mjs", ["check", "--proof", c.proof, "--build-id", c["build-id"]], { timeoutMs: LIMITS.native }),
      node("apk-install-check", "physical_apk_pair.mjs", ["check", "--selection", c.selection, "--output", a("apk-install-check.json")]),
      adb("install-app", ["install", "-r", "-t", c.app], { timeoutMs: 120_000 }),
      adb("install-test", ["install", "-r", "-t", c.test], { timeoutMs: 120_000 }),
      // StartReceiver can wake the app after *either* replacement install.
      adb("postinstall-stop", ["shell", "am", "force-stop", APP]),
      { id: "installed-binary-check", kind: "installed-check" },
      node("credential-destination", "physical_credential_watch.mjs", ["--preflight", "--serial", c.serial,
        "--output", a("credential-destination.json")]),
      node("credential-parser", "physical_credentials_preflight.mjs", ["--artifact-dir", c.artifacts,
        "--output", c.parser], { timeoutMs: 180_000 }),
      node("credential-stage", "physical_credentials.mjs", ["--serial", c.serial, "--schema", "user-pass", "--config", c.config,
        "--artifact-dir", c.artifacts, "--ownership", c.credentialOwner, "--native-inputs", c.proof,
        "--label", c.label, "--build-id", c["build-id"], "--preflight", c.parser,
        "--output", a("credential-staging.json")], { timeoutMs: 180_000 }),
    ],
    instrument: node("instrumentation", "physical_collector_session.mjs", ["run-instrumentation", "--artifact-dir", c.artifacts,
      "--native-inputs", c.proof, "--credential-ownership", c.credentialOwner, "--owner", c.owner,
      "--stdout", a("instrumentation.stdout"), "--stderr", a("instrumentation.stderr"),
      "--serial", c.serial, "--label", c.label, "--build-id", c["build-id"]], { retained: true, timeoutMs: 2_100_000 }),
    preCollector: [
      { id: "instrumentation-ready", kind: "ready", timeoutMs: LIMITS.ready },
      node("instrumentation-bind", "physical_collector_session.mjs", ["bind-instrumentation-ready", "--owner", c.owner, "--serial", c.serial]),
      node("profile-status", "physical_quiet_gate.mjs", ["--serial", c.serial, "--capture-status", p("ready-profile-status.json")]),
      node("profile-gate", "physical_memory_profile.mjs", ["--mode", armMeasurementMode(c), "--status", p("ready-profile-status.json")], { stdout: p("memory-profile-gate.json") }),
      { id: "h1-connect", kind: "device-command", verb: "connect", argument: "h1", commandId: c.connectId },
      node("h1-role", "physical_collector_session.mjs", ["check-role", "--serial", c.serial, "--session-mode", "h1",
        "--connect-command-id", c.connectId, "--instrumentation-owner", c.owner], { timeoutMs: LIMITS.role }),
    ],
    collector: shell("collector", "physical_host_launch.sh", ["collector", "--owner", c.collector,
      "--instrumentation-owner", c.owner, "--stdout", p("collector.stdout"), "--stderr", p("collector.stderr"),
      "--session-mode", "h1", "--connect-command-id", c.connectId, "--", "--serial", c.serial, "--label", c.label,
      "--duration-seconds", "1800", "--interval-ms", "1000", c.underlay === "wifi" ? "--require-wifi" : "--require-cellular",
      "--require-unmetered-vpn", "--output", c.telemetry, "--stop-file", p("collector.stop")], { retained: true, timeoutMs: 1_850_000 }),
    traffic: [
      { id: "collector-published", kind: "owner-file", timeoutMs: 30_000 },
      shell("collector-ready", "physical_host_launch.sh", ["collector-check", "--owner", c.collector, "--timeout-ms", "15000"]),
      ...(diagnostic ? boundary("connected-idle") : []),
      adb("chrome-stop", ["shell", "am", "force-stop", "com.android.chrome"]),
      adb("chrome-start", ["shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "about:blank", "com.android.chrome"]),
      adb("chrome-forward", ["forward", "--no-rebind", `tcp:${c["cdp-port"]}`, "localabstract:chrome_devtools_remote"]),
      node("chrome-ready", "chrome_readiness.mjs", ["--serial", c.serial, "--port", String(c["cdp-port"]), "--label", c.label,
        "--output", p("chrome-readiness.json")], { timeoutMs: 40_000 }),
      shell("workload-preflight", "physical_host_launch.sh", ["workload-preflight", "--label", c.label, "--output", c.workloads]),
      shell("collector-before-workload", "physical_host_launch.sh", ["collector-check", "--owner", c.collector, "--timeout-ms", "15000"]),
      shell("workload", "physical_host_launch.sh", ["workload", "--serial", c.serial, "--label", c.label,
        "--collector-pid", "<collector-pid>", "--telemetry", c.telemetry, "--children", CHILDREN,
        "--timeout-ms", "940000", "--output", c.workloads], { retained: true, timeoutMs: LIMITS.workloads }),
      ...(diagnostic ? [...boundary("post-traffic"), { id: "diagnostic-tail", kind: "diagnostic-tail" }] : [
        node("quiet-start", "physical_quiet_phase.mjs", ["--serial", c.serial, "--label", c.label, "--workloads", c.workloads, "--output", c.start]),
        node("quiet-end", "physical_quiet_phase.mjs", ["--serial", c.serial, "--start", c.start, "--output", c.end], { timeoutMs: LIMITS.quiet }),
      ]),
      adb("memory-live", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-memory.ndjson"], { stdout: c.memory }),
      adb("status-runtime-live", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-memory-status.ndjson"], { stdout: c.statusRuntime }),
      adb("diagnostics-live", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-diagnostics.ndjson"], { stdout: c.diagnostics }),
      ...(!diagnostic ? [node("memory-live-gate", "physical_quiet_gate.mjs", gateArgs(c.memory), { stdout: c.liveGate })] : []),
    ],
    finish: { id: "finish", kind: "device-command", verb: "finish", argument: "", commandId: c.finishId, timeoutMs: LIMITS.finish },
    afterJoin: [
      adb("memory-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-memory.ndjson"], { stdout: c.finalMemory }),
      adb("status-runtime-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-memory-status.ndjson"], { stdout: c.finalStatusRuntime }),
      adb("diagnostics-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-diagnostics.ndjson"], { stdout: c.finalDiagnostics }),
      adb("finish-status-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-status"], { stdout: c.finishStatus }),
      adb("native-teardown-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-memory-teardown.json"], { stdout: c.teardownMemory }),
      adb("summary-final", ["exec-out", "run-as", APP, "cat", "files/acceptance/physical-summary.json"], { stdout: p("physical-summary.json") }),
    ],
    teardown: node("memory-teardown-gate", "physical_quiet_gate.mjs", [...gateArgs(c.finalMemory), "--live-gate", c.liveGate,
      "--teardown", c.teardownMemory, "--finish-status", c.finishStatus, "--instrumentation-owner", c.owner,
      "--teardown-fallback", c.teardownFallback,
      "--producer-summary", p("physical-summary.json"),
      "--finish-command-id", c.finishId, "--native-inputs", c.proof], { stdout: c.finalGate }),
    diagnosticMemory: { id: "diagnostic-memory", kind: "diagnostic-memory" },
    credentialFinish: node("credential-finish", "physical_credential_ownership.mjs", ["finish", "--ownership", c.credentialOwner,
      "--serial", c.serial, "--label", c.label, "--build-id", c["build-id"], "--instrumentation-owner", c.owner, "--finish-command-id", c.finishId]),
    rollback: node("credential-rollback", "physical_credential_ownership.mjs", ["rollback", "--ownership", c.credentialOwner,
      "--serial", c.serial, "--label", c.label, "--build-id", c["build-id"]]),
    stopCollector: { id: "collector-stop", kind: "stop-marker", path: p("collector.stop") },
    chromeCleanup: adb("chrome-failure-cleanup", ["shell", "am", "force-stop", "com.android.chrome"]),
    forwardCleanup: adb("chrome-forward-remove", ["forward", "--remove", `tcp:${c["cdp-port"]}`]),
    clientsCleanup: { id: "clients-cleanup", kind: "clients-cleanup" },
    results: { id: "scoped-results", kind: "results" },
  };
}

// A fake driver exercises the real schedule without devices/builds/time waits.
// In particular every failure follows this same cleanup path, never a retry.
export async function orchestrateH1(context, driver) {
  const s = h1Steps(context); const errors = []; const complete = new Set();
  const diagnostic = armMeasurementMode(context) === "diagnostic";
  let instrumentation; let collector; let instrumentationJoined = false; let instrumentationClosed = false; let liveGate = false;
  let diagnosticMemory = null;
  const execute = async step => { const result = await driver.execute(step); complete.add(step.id); return result; };
  const cleanup = async operation => { try { return await operation(); } catch (error) {
    errors.push(safeReason(error));
    if (error instanceof ArmError && error.diagnosticMemory) return error.diagnosticMemory;
    return null;
  } };
  try {
    for (const step of s.setup) await execute(step);
    instrumentation = await driver.start(s.instrument);
    for (const step of s.preCollector) await execute(step);
    collector = await driver.start(s.collector);
    for (const step of s.traffic) {
      // A failed live memory gate is evidence, so finish and still re-evaluate
      // final samples. No quiet gate can be manufactured during cleanup.
      if (step.id === "memory-live-gate") liveGate = true;
      await execute(step);
    }
  } catch (error) { errors.push(safeReason(error)); }
  finally {
    if (complete.has("chrome-start") && !complete.has("workload")) await cleanup(() => execute(s.chromeCleanup));
    if (instrumentation) {
      // A pre-ready or failed session can reject finish while its finite
      // instrumentation finally still owns teardown. Join independently.
      await cleanup(() => execute(s.finish));
      const joined = await cleanup(async () => {
        await driver.join(instrumentation, LIMITS.finish);
        instrumentationJoined = true;
        return true;
      });
      if (!joined && instrumentation.live !== false) await cleanup(() => driver.stop(instrumentation));
      instrumentationClosed = instrumentationJoined || instrumentation.live === false;
      if (instrumentationClosed) {
        for (const step of s.afterJoin) await cleanup(() => execute(step));
      }
    } else if (complete.has("credential-stage")) await cleanup(() => execute(s.rollback));
    if (collector) {
      await cleanup(() => execute(s.stopCollector));
      const joined = await cleanup(async () => { await driver.join(collector, LIMITS.stop); return true; });
      if (!joined && collector.live !== false) await cleanup(() => driver.stop(collector));
    }
    if (liveGate && instrumentationClosed) await cleanup(() => execute(s.teardown));
    if (diagnostic && instrumentationClosed) diagnosticMemory = await cleanup(() => execute(s.diagnosticMemory));
    if (instrumentationJoined && complete.has("finish")) {
      await cleanup(() => execute(s.clientsCleanup));
      await cleanup(() => execute(s.credentialFinish));
    }
    if (complete.has("chrome-forward")) await cleanup(() => execute(s.forwardCleanup));
  }
  let measurements = null;
  if (complete.has("workload")) measurements = await cleanup(() => execute(s.results));
  return { type: "scoped-h1-arm", schemaVersion: 2, eligible: errors.length === 0,
    classification: `SCOPED_H1_${diagnostic ? "DIAGNOSTIC_" : ""}${errors.length ? "FAILED" : "COMPLETE"}`, errors,
    measurementMode: armMeasurementMode(context), qualificationEligible: !diagnostic && errors.length === 0,
    profileRate: armProfileRate(context), diagnosticMemory,
    scope: "ios-profile-h1-wikipedia-fast-three", measurements,
    memoryQualified: complete.has("finish") && instrumentationJoined && s.afterJoin.every(step => complete.has(step.id)) && complete.has("memory-live-gate") && complete.has("memory-teardown-gate"),
    cleanupComplete: instrumentationJoined && complete.has("credential-finish") && complete.has("clients-cleanup") &&
      (!complete.has("chrome-forward") || complete.has("chrome-forward-remove")),
    completedSteps: [...complete] };
}

// Diagnostic evidence is deliberately not a release gate. Keep every observed
// byte and breach, including profiling overhead; never subtract it to turn an
// overshoot into a qualified result.
export function evaluateDiagnosticMemory(records, { teardown, fallback, producerSummary, finish, expectedIdentity, expectedFinishCommandId,
  statusRuntime = [], diagnostics = [], ownerCensuses = [] } = {}) {
  const reasons = new Set();
  const reject = reason => reasons.add(reason);
  const producerSummaries = [teardown?.producerSummary, ...(fallback ? [fallback.producerSummary] : []), producerSummary];
  const selection = selectMemoryTeardownReceipt(teardown, fallback, finish, expectedIdentity, expectedFinishCommandId);
  teardown = selection.teardown;
  selection.reasons.forEach(reject);
  const samples = records.filter(row => row?.type === "sample");
  if (!samples.length || samples.length !== records.length) reject("diagnostic-memory-samples-required");
  let previousElapsed = -1;
  for (const row of samples) {
    if (!Number.isSafeInteger(row.elapsedMs) || row.elapsedMs < 0 || row.elapsedMs <= previousElapsed ||
        !Number.isSafeInteger(row.goRuntimeBytes) || row.goRuntimeBytes <= 0 || row.samplerDropped !== 0 ||
        row.memoryProfile !== PROFILE || row.goMemoryProfileRateBytes !== 65536 ||
        row.goMemoryLimitBytes !== 32 * 1024 * 1024) {
      reject("diagnostic-memory-evidence-invalid");
    }
    previousElapsed = row.elapsedMs;
  }
  const nativeRows = Array.isArray(teardown?.native?.samples) ? teardown.native.samples : [];
  const processRows = nativeRows.map(row => ({ ...row, memoryProfile: teardown.memoryProfile }));
  const diagnostic = teardown ? evaluateDiagnosticRuntimeReads(diagnostics, teardown) : { samples: [], reasons: [] };
  if (diagnostic.reasons.length) reject("diagnostic-runtime-stream-invalid");
  const runtime = evaluateRuntimeReads({ devicePrimitiveCount: records, teardownPrimitiveCount: processRows,
    statusRuntimeReadCount: statusRuntime, diagnosticRuntimeReadCount: diagnostic.samples }, 65536);
  for (const census of ownerCensuses) {
    try { validateDiagnosticCensus(census); } catch { reject("diagnostic-owner-census-capability-required"); }
  }
  const auxiliary = evaluateAuxiliaryRuntimeValues(records, statusRuntime, ownerCensuses, Boolean(teardown));
  const producer = evaluateProducerMemorySummaries(teardown ? producerSummaries : [], expectedIdentity ?? teardown,
    expectedFinishCommandId ?? teardown?.finishCommandId, finish?.teardownObserverId ?? teardown?.observerId, runtime.counts);
  producer.reasons.filter(reason => reason !== "producer-memory-runtime-above-32-mib").forEach(reject);
  auxiliary.auxiliaryPeakGoRuntimeBytes = Math.max(auxiliary.auxiliaryPeakGoRuntimeBytes, producer.producerPeakGoRuntimeBytes);
  auxiliary.auxiliaryRuntimeBreachValueCount += producer.producerRuntimeBreachRepresentationCount;
  auxiliary.nativeConflictRepresentationCount = selection.conflictingPrimaryRows.length;
  auxiliary.unqualifiedNativeConflictRepresentationCount = selection.unqualifiedNativeConflictRepresentationCount;
  for (const row of selection.conflictingPrimaryRows) {
    if (!Number.isSafeInteger(row?.goRuntimeBytes) || row.goRuntimeBytes <= 0) {
      reject("native-conflict-runtime-value-invalid"); continue;
    }
    auxiliary.auxiliaryPeakGoRuntimeBytes = Math.max(auxiliary.auxiliaryPeakGoRuntimeBytes, row.goRuntimeBytes);
    if (row.goRuntimeBytes > GO_RUNTIME_LIMIT_BYTES) auxiliary.auxiliaryRuntimeBreachValueCount++;
  }
  if (auxiliary.reasons.some(reason => !["retained-auxiliary-runtime-above-32-mib", "maintenance-runtime-prior-profile-unqualified"].includes(reason))) reject("diagnostic-runtime-auxiliary-invalid");
  if (runtime.reasons.some(reason => reason !== "go-runtime-above-32-mib")) reject("diagnostic-memory-evidence-invalid");
  if (teardown && (teardown.native?.state !== "complete" || teardown.native.observerJoined !== true || teardown.native.dropped !== 0 ||
      teardown.native.terminalSequence !== nativeRows.length || !(teardown.native.joinSequence < teardown.native.terminalSequence) ||
      !teardown.deviceDrainerJoined || !teardown.deviceRingDrained || !teardown.deviceJoined || !teardown.referencesReleased ||
      teardown.failureCount !== 0 || teardown.exporterFailed !== false || teardown.devicePrimitiveCount !== samples.length ||
      teardown.statusRuntimeReadCount !== statusRuntime.length || teardown.diagnosticBatchesProduced !== diagnostic.samples.length ||
      teardown.diagnosticBatchesFlushed !== diagnostic.samples.length)) reject("diagnostic-memory-teardown-incomplete");
  const result = { type: "diagnostic-memory-observation", schemaVersion: 3, measurementMode: "diagnostic", memoryProfile: PROFILE,
    sampleScope: teardown ? "retained-runtime-events-not-all-internal-reads-or-continuous-peak" : "legacy-device-primitive-samples",
    counts: runtime.counts, combinedRuntimeReadCount: runtime.combinedRuntimeReadCount,
    eligible: reasons.size === 0, reasons: [...reasons], fallbackState: selection.fallbackState,
    qualificationEligible: false, profileRate: 65536, sampleCount: samples.length,
    firstElapsedMs: samples[0]?.elapsedMs ?? null, lastElapsedMs: samples.at(-1)?.elapsedMs ?? null,
    combinedRetainedRuntimeEventCount: runtime.combinedRuntimeReadCount,
    auxiliary, producer,
    peakGoRuntimeBytes: Math.max(runtime.peakGoRuntimeBytes, auxiliary.auxiliaryPeakGoRuntimeBytes),
    goRuntimeLimitBytes: GO_RUNTIME_LIMIT_BYTES,
    goRuntimeBreachSampleCount: runtime.goRuntimeBreachSampleCount };
  if (reasons.size) {
    const error = new ArmError([...reasons][0]);
    error.diagnosticMemory = result;
    throw error;
  }
  return result;
}

export function validateDiagnosticCensus(row, before) {
  const whole = value => Number.isSafeInteger(value) && value >= 0;
  const runtime = value => value && ["runtime_bytes", "heap_object_bytes", "heap_unused_bytes", "heap_free_bytes",
    "stack_bytes", "gc_cycles", "forced_gc_cycles", "goroutines"].every(key => whole(value[key])) &&
    value.runtime_bytes > 0 && value.goroutines > 0;
  if (row?.schema !== 1 || row.memory_profile_rate_bytes !== 65536 || !whole(row.unix_millis) || row.unix_millis === 0 ||
      !runtime(row.before) || !runtime(row.after) || !row.owners ||
      ["network_space_api", "client_windows", "provider_transfer", "dns", "process_transfer_pools", "process_transport_claims"]
        .some(key => !row.owners[key] || typeof row.owners[key] !== "object") ||
      !whole(row.owners.block_actions) || !whole(row.owners.block_action_slots) ||
      !Array.isArray(row.allocator_size_classes) || row.allocator_size_classes.length !== 61 ||
      row.allocator_size_classes.some(value => !whole(value?.size_bytes) || !whole(value?.live_objects))) {
    fail("diagnostic-owner-census-capability-required");
  }
  if (before) {
    validateDiagnosticCensus(before);
    if (row.unix_millis < before.unix_millis || row.before.forced_gc_cycles <= before.after.forced_gc_cycles) {
      fail("diagnostic-heap-gc-not-observed");
    }
  }
  return { eligible: true, qualificationEligible: false, memoryProfileRateBytes: 65536 };
}

function requireDiagnosticState(value, previous, fixedCommand = false) {
  if (!value || typeof value.sessionId !== "string" || !value.sessionId || !Number.isSafeInteger(value.pid) || value.pid <= 0 ||
      !Number.isSafeInteger(value.elapsedMs) || value.elapsedMs < 0 || value.state !== "complete" ||
      value.connected !== true || value.tunnelStarted !== true || value.provideEnabled !== false || value.transportMode !== "h1" ||
      typeof value.commandId !== "string" || !value.commandId || typeof value.phase !== "string" || !value.phase) {
    fail("diagnostic-connected-h1-role-required");
  }
  if (previous && (value.sessionId !== previous.sessionId || value.pid !== previous.pid || value.elapsedMs < previous.elapsedMs ||
      (fixedCommand && (value.commandId !== previous.commandId || value.phase !== previous.phase)))) {
    fail("diagnostic-retained-session-changed");
  }
}

// The only diagnostic wait is a fixed 45 seconds, with retained app/collector
// checks at both ends and every five seconds. Clocks and observations can be
// injected by deterministic tests, but the duration cannot be overridden.
export async function retainDiagnosticTail({ readState, now = () => performance.now(), sleep = delay }) {
  const started = now(); let previous; let checks = 0;
  for (;;) {
    const current = await readState();
    requireDiagnosticState(current, previous, true);
    previous = current; checks++;
    const elapsedMs = now() - started;
    if (!Number.isFinite(elapsedMs) || elapsedMs < 0 || elapsedMs > DIAGNOSTIC_TAIL_MS + 30_000) fail("diagnostic-tail-deadline");
    if (elapsedMs >= DIAGNOSTIC_TAIL_MS) return { type: "diagnostic-tail", elapsedMs, checks, qualificationEligible: false };
    await sleep(Math.min(5000, DIAGNOSTIC_TAIL_MS - elapsedMs));
  }
}

export async function retainClientCleanupResult(directory, operation, failureReport) {
  let result;
  try { result = await operation(); }
  catch (error) {
    publish(join(directory, "clients-cleanup.failed.json"), failureReport(error));
    fail("retained-client-cleanup-failed");
  }
  publish(join(directory, "clients-cleanup.json"), { eligible: true, ...result });
  return result;
}

export function ptyCommand(command, args, platform = process.platform) {
  if (platform === "darwin") return { command: "/usr/bin/script", args: ["-q", "/dev/null", command, ...args] };
  if (platform === "linux") return { command: "/usr/bin/script", args: ["-q", "-e", "-f", "-c",
    `exec ${[command, ...args].map(quote).join(" ")}`, "/dev/null"] };
  fail("supported-retained-pty-host-required");
}

// Only scoped process groups launched here can be terminated. PTY command
// owners receive TERM first so their own join/receipt handlers run. A deadline
// remains failure even if the child handles the signal and subsequently exits 0.
export function launchPrivate(step, context, { spawnImpl = spawn, timer = setTimeout, clear = clearTimeout, ptyInput = "inherit" } = {}) {
  // A retained helper exclusively creates its own child-output files. The
  // PTY transcript belongs to this launcher and must use a separate namespace;
  // precreating collector.stdout here prevents the helper from publishing.
  const logName = step.retained ? `${step.id}.pty` : step.id;
  const outputPath = step.stdout ?? join(context.directory, `${logName}.stdout`);
  const errorPath = join(context.directory, `${logName}.stderr`);
  const binding = prepareArtifactDirectory(context.directory);
  requireArtifactPaths(binding, [outputPath, errorPath]);
  const output = openSync(outputPath, "wx", 0o600); const errors = openSync(errorPath, "wx", 0o600);
  const invocation = step.retained ? ptyCommand(step.command, step.args) : step;
  let child; let settled = false; let timedOut = false; let killer; let deadline;
  let resolveDone;
  const done = new Promise(finish => { resolveDone = finish; });
  const finish = result => {
    if (settled) return;
    settled = true; clear(deadline); clear(killer);
    closeSync(output); closeSync(errors);
    resolveDone({ ...result, timedOut, eligible: result.exitCode === 0 && !result.signal && !timedOut });
  };
  const signal = value => {
    if (settled || !child?.pid) return;
    try { process.kill(-child.pid, value); } catch (error) { if (error.code !== "ESRCH") throw error; }
  };
  try {
    child = spawnImpl(invocation.command, invocation.args, { cwd: step.cwd ?? join(context.root, "android"),
      env: { ...process.env, GOMAXPROCS: String(context.gomaxprocs), BRINGYOUR_HOME: context.root, WARP_HOME: context.root,
        URNETWORK_ANDROID_SDK_BUILD_OWNER: context.buildOwner, CDP_PORT: String(context["cdp-port"]) },
      detached: true, stdio: step.retained ? [ptyInput, "pipe", "pipe"] : ["pipe", output, errors] });
    // Retain the parent's terminal input and the PTY master through join.
    // macOS script rejects Node's socket-backed "pipe" stdin (tcgetattr).
    // The CLI requires a foreground terminal; no one-shot background owner.
    if (step.retained) {
      child.stdout.on("data", bytes => { if (!settled) writeSync(output, bytes); });
      child.stderr.on("data", bytes => { if (!settled) writeSync(errors, bytes); });
    }
    if (!step.retained) { child.stdin.on("error", () => {}); child.stdin.end(step.input ?? ""); }
    child.once("error", () => finish({ exitCode: null, signal: null }));
    child.once("close", (exitCode, childSignal) => finish({ exitCode, signal: childSignal }));
    deadline = timer(() => { timedOut = true; signal("SIGTERM"); killer = timer(() => signal("SIGKILL"), LIMITS.kill); }, step.timeoutMs ?? LIMITS.command);
  } catch { finish({ exitCode: null, signal: null }); }
  return { id: step.id, done, get live() { return !settled; }, outputPath, errorPath,
    interrupt() { timedOut = true; signal("SIGTERM"); killer ??= timer(() => signal("SIGKILL"), LIMITS.kill); } };
}

async function joinProcess(handle, timeoutMs) {
  let timer;
  try {
    const result = await Promise.race([handle.done, new Promise((_, reject) => {
      timer = setTimeout(() => { handle.interrupt(); reject(new ArmError(`${handle.id}-join-deadline`)); }, timeoutMs);
    })]);
    if (!result.eligible) fail(`${handle.id}-${result.timedOut ? "deadline" : "failed"}`);
    return result;
  } finally { clearTimeout(timer); }
}

function requireConfig(path) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid() || (stat.mode & 0o7777) !== 0o600) {
    fail("private-owned-config-required");
  }
}

export class HostArmDriver {
  constructor(context) { this.c = context; this.handles = new Map(); this.collectorPid = null; this.interrupted = false; }
  assertInputs() {
    const c = this.c;
    for (const binding of c.directoryBindings) requireArtifactPaths(binding, [join(binding.directory, "identity-check")]);
    for (const source of c.orchestrationSources) {
      if (hashNativeInputFile(source.path).sha256 !== source.sha256) fail("orchestration-source-changed");
    }
  }
  async start(step) {
    this.assertInputs();
    if (this.interrupted) fail("arm-interrupted");
    const actual = { ...step, args: step.args.map(value => value === "<collector-pid>" ? String(this.collectorPid) : value) };
    if (step.id === "workload" && !/^[1-9][0-9]*$/.test(String(this.collectorPid))) fail("verified-collector-pid-required");
    const handle = launchPrivate(actual, this.c);
    this.handles.set(step.id, handle);
    return handle;
  }
  async command(step) {
    const handle = await this.start(step);
    try {
      await joinProcess(handle, (step.timeoutMs ?? LIMITS.command) + LIMITS.kill + 5_000);
      return handle;
    } finally {
      if (!handle.live) publish(join(this.c.directory, `${step.id}.outcome.json`), await handle.done);
    }
  }
  async join(handle, timeoutMs) {
    try { return await joinProcess(handle, timeoutMs); }
    finally {
      if (!handle.live) publish(join(this.c.directory, `${handle.id}.outcome.json`), await handle.done);
    }
  }
  async stop(handle) {
    handle.interrupt();
    const result = await Promise.race([handle.done, delay(LIMITS.kill + 5_000).then(() => fail(`${handle.id}-unjoined`))]);
    publish(join(this.c.directory, `${handle.id}.outcome.json`), result);
  }
  async execute(step) {
    this.assertInputs();
    process.stdout.write(`${JSON.stringify({ type: "scoped-h1-progress", step: step.id })}\n`);
    if (step.kind === "command") {
      if (step.id === "native-teardown-final") {
        let handle, failure, primary, terminal, finish, ready;
        try { handle = await this.command(step); } catch (error) { failure = error; }
        try { primary = privateJson(this.c.teardownMemory); } catch { /* retain original bytes */ }
        try { terminal = privateJson(`${this.c.owner}.terminal.json`); } catch { /* not joined */ }
        try { finish = privateJson(this.c.finishStatus); } catch { /* not bound */ }
        try { ready = privateJson(`${this.c.owner}.ready.json`); } catch { /* not bound */ }
        const native = primary?.native;
        const identityBound = primary?.type === "physical-memory-teardown" && primary.schemaVersion === 1 && primary.memoryProfile === PROFILE &&
          typeof primary.sessionId === "string" && primary.sessionId && primary.sessionId === finish?.sessionId &&
          primary.buildId === this.c["build-id"] && primary.buildId === finish?.buildId &&
          Number.isSafeInteger(primary.pid) && primary.pid > 0 && primary.pid === ready?.targetPid && primary.pid === finish?.pid &&
          primary.finishCommandId === this.c.finishId && primary.finishCommandId === finish?.commandId &&
          typeof primary.observerId === "string" && primary.observerId && primary.observerId === finish?.teardownObserverId && primary.observerId === native?.observerId;
        if (failure || !identityBound || !Array.isArray(native?.samples) || native.samples.length < 4 || native.samples.length > 16 ||
            native.type !== "device-memory-teardown" || native.schemaVersion !== 1 || native.deviceTargetBytes !== 33554432 ||
            native.state !== "complete" || native.failure !== "" || native.observerJoined !== true || native.dropped !== 0 ||
            native.capacity !== 16 || native.intervalNanos !== 15000000000 || native.produced !== native.samples.length || native.drained !== native.samples.length ||
            ["deviceDrainerJoined", "deviceRingDrained", "deviceJoined", "referencesReleased", "filesFlushed"].some(key => primary?.[key] !== true) ||
            primary?.failureCount !== 0 || primary?.exporterFailed !== false ||
            terminal?.state !== "complete" || terminal.exitCode !== 0 || terminal.signal !== null || terminal.interrupted !== false) {
          try {
            await this.command({ ...step, id: "native-teardown-fallback", stdout: this.c.teardownFallback,
              args: [...step.args.slice(0, -1), "files/acceptance/physical-memory-teardown-incomplete.json"] });
          } catch (error) { failure ??= error; }
          failure ??= new ArmError("native-teardown-primary-receipt-incomplete");
        }
        if (failure) throw failure;
        return handle;
      }
      const handle = await this.command(step);
      if (["collector-ready", "collector-before-workload"].includes(step.id)) {
        const value = readFileSync(handle.outputPath, "utf8").trim();
        if (!/^[1-9][0-9]*$/.test(value)) fail("verified-collector-pid-required");
        this.collectorPid = Number(value);
      }
      return;
    }
    if (step.kind === "ready") return this.waitReady(step);
    if (step.kind === "owner-file") {
      const deadline = performance.now() + step.timeoutMs;
      while (!existsSync(this.c.collector)) {
        if (!this.handles.get("collector")?.live) fail("collector-exited-before-publication");
        if (performance.now() >= deadline) fail("collector-publication-deadline");
        await delay(100);
      }
      return;
    }
    if (step.kind === "device-command") return this.deviceCommand(step);
    if (step.kind === "installed-check") return this.installedCheck();
    if (step.kind === "stop-marker") {
      requireArtifactPaths(prepareArtifactDirectory(this.c.directory), [step.path]);
      writeFileSync(step.path, "", { flag: "wx", mode: 0o600 }); return;
    }
    if (step.kind === "clients-cleanup") return this.cleanupClients();
    if (step.kind === "results") return summarizeScopedResults(this.c);
    if (step.kind.startsWith("diagnostic-")) {
      if (armMeasurementMode(this.c) !== "diagnostic") fail("diagnostic-mode-required");
      if (step.kind === "diagnostic-census-check") return validateDiagnosticCensus(privateJson(step.path), step.before ? privateJson(step.before) : undefined);
      if (step.kind === "diagnostic-boundary") return this.diagnosticBoundary(step);
      if (step.kind === "diagnostic-tail") {
        const result = await retainDiagnosticTail({ readState: () => this.diagnosticState() });
        publish(join(this.c.directory, "diagnostic-tail.json"), result);
        return result;
      }
      if (step.kind === "diagnostic-memory") {
        const read = path => {
          let contents;
          try { contents = privateText(path); } catch { return null; }
          if (!contents.trim()) return null;
          try { return JSON.parse(contents); } catch { return { type: "malformed-retained-evidence" }; }
        };
        const rows = path => {
          try { return privateText(path).split("\n").filter(line => line.trim()).map(line => {
            try { return JSON.parse(line); } catch { return null; }
          }); } catch { return []; }
        };
        const records = rows(this.c.finalMemory);
        const teardown = read(this.c.teardownMemory) ?? { native: {} };
        const fallback = read(this.c.teardownFallback);
        const finish = read(this.c.finishStatus);
        const ready = read(`${this.c.owner}.ready.json`);
        const statusRuntime = rows(this.c.finalStatusRuntime);
        const diagnostics = rows(this.c.finalDiagnostics);
        const producerSummary = read(join(this.c.directory, "physical-summary.json"));
        const ownerCensuses = ["connected-idle", "post-traffic"].flatMap(boundary => ["before-gc", "after-gc"].map(part =>
          read(join(this.c.artifacts, `${this.c.label}.diagnostic-${boundary}-${part}.json`))));
        try {
          const result = evaluateDiagnosticMemory(records, { teardown, fallback, producerSummary, finish, statusRuntime, diagnostics, ownerCensuses,
            expectedIdentity: { sessionId: records.find(row => typeof row?.sessionId === "string")?.sessionId,
              buildId: this.c["build-id"], pid: ready?.targetPid }, expectedFinishCommandId: this.c.finishId });
          publish(join(this.c.directory, "diagnostic-memory.json"), result);
          return result;
        } catch (error) {
          if (error instanceof ArmError && error.diagnosticMemory) publish(join(this.c.directory, "diagnostic-memory.json"), error.diagnosticMemory);
          throw error;
        }
      }
    }
    fail("unknown-arm-operation");
  }
  async diagnosticState() {
    this.assertInputs();
    if (this.interrupted) fail("arm-interrupted");
    if (!this.handles.get("instrumentation")?.live || !this.handles.get("collector")?.live) fail("diagnostic-retained-owner-not-live");
    const collectorPid = await checkCollectorSession({ owner: this.c.collector, timeoutMs: 5000 });
    if (collectorPid !== this.collectorPid) fail("diagnostic-collector-changed");
    const state = checkInstrumentationCommandSession({ serial: this.c.serial, owner: this.c.owner });
    requireDiagnosticState(state, this.previousDiagnosticState);
    this.previousDiagnosticState = state;
    return state;
  }
  async diagnosticBoundary(step) {
    const startedHostTimeUnixMs = Date.now();
    const state = await this.diagnosticState();
    let cleanupCompletedHostTimeUnixMs = null;
    if (step.boundary === "post-traffic") {
      const receipt = requireCompletedWorkloads(this.c.workloads, this.c.label, this.c.serial);
      cleanupCompletedHostTimeUnixMs = receipt.childReceipts.at(-1).completedHostTimeUnixMs;
      if (cleanupCompletedHostTimeUnixMs > startedHostTimeUnixMs) fail("diagnostic-workload-cleanup-order-invalid");
    } else if (step.boundary !== "connected-idle") fail("diagnostic-boundary-invalid");
    publish(join(this.c.directory, `${step.id}.json`), { type: "diagnostic-boundary", boundary: step.boundary,
      qualificationEligible: false, startedHostTimeUnixMs, checkedHostTimeUnixMs: Date.now(),
      cleanupCompletedHostTimeUnixMs,
      cleanupToBoundaryMs: cleanupCompletedHostTimeUnixMs === null ? null : startedHostTimeUnixMs - cleanupCompletedHostTimeUnixMs,
      state });
  }
  async waitReady(step, { now = () => performance.now(), sleep = delay } = {}) {
    const deadline = now() + step.timeoutMs;
    const path = join(this.c.directory, "startup-status-observations.jsonl");
    requireArtifactPaths(prepareArtifactDirectory(this.c.directory), [path]);
    const output = openSync(path, "wx", 0o600); let sequence = 0;
    // Retain bounded identity/outcome fields, never arbitrary status extras,
    // raw ADB diagnostics, credentials or exception text.
    const record = (outcome, observed, status, targetPidMatches = null) => {
      writeSync(output, JSON.stringify({ type: "startup-status-observation", schemaVersion: 1,
        sequence: ++sequence, hostTimeUnixMs: Date.now(), outcome,
        observedBuildId: typeof status?.buildId === "string" && LABEL.test(status.buildId) ? status.buildId : null,
        currentBuild: status?.buildId === this.c["build-id"],
        observedPid: Number.isSafeInteger(status?.pid) && status.pid > 0 ? status.pid : null,
        observedState: ["ready", "running", "complete", "error"].includes(status?.state) ? status.state : null,
        targetPidMatches, stdoutBytes: typeof observed?.stdout === "string" ? Buffer.byteLength(observed.stdout) : 0,
        stderrBytes: typeof observed?.stderr === "string" ? Buffer.byteLength(observed.stderr) : 0,
        exitCode: Number.isInteger(observed?.status) ? observed.status : null,
        signalled: Boolean(observed?.signal), transportFailed: Boolean(observed?.error) }) + "\n");
    };
    try {
      while (now() < deadline) {
        if (!this.handles.get("instrumentation")?.live || existsSync(`${this.c.owner}.terminal.json`)) {
          record("owner-exited"); fail("instrumentation-exited-before-ready");
        }
        if (!existsSync(this.c.owner)) { record("await-owner"); await sleep(500); continue; }
        const observed = spawnSync("adb", ["-s", this.c.serial, "shell", "run-as", APP, "cat", "files/acceptance/physical-status"],
          { encoding: "utf8", timeout: 2_000, maxBuffer: 1024 * 1024 });
        let status;
        if (observed.status === 0 && !observed.error && !observed.signal && !observed.stderr?.trim()) {
          try { status = JSON.parse(observed.stdout); } catch { /* startup can still expose a prior or absent file */ }
        }
        if (status?.type !== "status" || status.buildId !== this.c["build-id"] ||
            !Number.isSafeInteger(status.pid) || status.pid <= 0) {
          record(status?.type === "status" && status.buildId !== this.c["build-id"] ? "different-build" : "unattributed-status", observed, status);
          await sleep(500); continue;
        }
        const targetMatches = () => {
          const target = spawnSync("adb", ["-s", this.c.serial, "shell", "pidof", APP],
            { encoding: "utf8", timeout: 2_000, maxBuffer: 4096 });
          return target.status === 0 && !target.error && !target.signal && !target.stderr?.trim() &&
            /^\s*[1-9][0-9]*(?:\s+[1-9][0-9]*)*\s*$/.test(target.stdout ?? "") &&
            target.stdout.trim().split(/\s+/).map(Number).every(Number.isSafeInteger) &&
            target.stdout.trim().split(/\s+/).map(Number).includes(status.pid);
        };
        if (!targetMatches() || !targetMatches()) {
          record("target-not-current", observed, status, false); await sleep(500); continue;
        }
        if (status.state === "error") { record("current-error", observed, status, true); fail("instrumentation-ready-error"); }
        if (status.state === "ready" && status.phase === "ready") { record("current-ready", observed, status, true); return; }
        record("current-not-ready", observed, status, true);
        await sleep(500);
      }
      record("deadline"); fail("instrumentation-ready-deadline");
    } finally { closeSync(output); }
  }
  async deviceCommand(step) {
    // Never overwrite another in-progress operation, even on failed-arm cleanup.
    const deadline = performance.now() + (step.timeoutMs ?? LIMITS.command);
    for (;;) {
      const current = checkInstrumentationCommandSession({ serial: this.c.serial, owner: this.c.owner });
      if (["ready", "complete"].includes(current.state)) break;
      if (current.state === "error" || performance.now() >= deadline) fail(`${step.id}-session-not-idle`);
      await delay(250);
    }
    const name = `files/acceptance/physical-command.${step.commandId}`;
    await this.command({ id: `${step.id}-publish`, kind: "command", command: "adb", args: ["-s", this.c.serial,
      "shell", "run-as", APP, "tee", name], input: `${step.commandId}|${step.verb}|${step.argument}\n` });
    await this.command({ id: `${step.id}-commit`, kind: "command", command: "adb", args: ["-s", this.c.serial,
      "shell", "run-as", APP, "mv", name, "files/acceptance/physical-command"] });
  }
  async installedCheck() {
    const c = this.c;
    for (const [key, name] of [["app", APP], ["test", `${APP}.test`]]) {
      const query = await this.command({ id: `${key}-installed-path`, command: "adb", args: ["-s", c.serial, "shell", "pm", "path", name] });
      const value = readFileSync(query.outputPath, "utf8").trim();
      const match = /^package:(\/data\/app\/[A-Za-z0-9_./=+~-]+\.apk)$/.exec(value);
      if (!match) fail("single-installed-apk-required");
      const output = join(c.directory, `${key}-installed.apk`);
      await this.command({ id: `${key}-installed-copy`, command: "adb", args: ["-s", c.serial, "exec-out", "cat", match[1]], stdout: output });
      if (hashNativeInputFile(output).sha256 !== hashNativeInputFile(c[key]).sha256) fail("installed-apk-hash-mismatch");
    }
    requireCredentialTargetStopped(c.serial);
    publish(join(c.directory, "installed-apk-proof.json"), { eligible: true, appMatches: true, testMatches: true });
  }
  async cleanupClients() {
    const c = this.c;
    requireCredentialTargetStopped(c.serial);
    const marker = join(c.directory, "active-client-ledger.txt");
    await this.command({ id: "active-client-ledger", command: "adb", args: ["-s", c.serial, "exec-out", "run-as", APP,
      "cat", "files/acceptance/active-client-ids"], stdout: marker });
    const values = readFileSync(marker, "utf8").split(/\r?\n/).filter(Boolean);
    if (!values.length || values.length > 64 || values.some(value => !/^[A-Za-z0-9._-]{1,256}$/.test(value))) fail("owned-client-ledger-required");
    const directory = join(c.directory, "clients"); prepareArtifactDirectory(directory, true);
    const files = [...new Set(values)].map((value, index) => {
      const path = join(directory, `client-${index}.txt`); writeFileSync(path, `${value}\n`, { flag: "wx", mode: 0o600 }); return path;
    });
    requireConfig(c.config); requireCredentialParserPreflight(c.parser);
    // Read via the same canonical parser; raw scalars remain only in memory.
    const credentials = ["user", "pass"].map(key => {
      const result = spawnSync(join(c.root, "tests/read-tests-config.sh"), ["--schema", "user-pass", "get", key],
        { env: { ...process.env, UR_ACCEPT_VAULT: c.config }, encoding: "utf8", timeout: 60_000, maxBuffer: 64 * 1024 });
      if (result.status !== 0 || result.error || result.signal) fail("client-cleanup-config-reader-failed");
      return result.stdout;
    });
    credentialPayload(credentials);
    const { cleanupClientFiles, cleanupFailureReport } = await import(pathToFileURL(join(c.root, "build/all/acceptance/client-cleanup.mjs")));
    return retainClientCleanupResult(c.directory,
      () => cleanupClientFiles(files, { UR_ACCEPT_USER: credentials[0], UR_ACCEPT_PASS: credentials[1] }), cleanupFailureReport);
  }
}

export function summarizeScopedResults(c) {
  const workloads = privateJson(c.workloads);
  if (workloads.failedChildCount !== 0) fail("scoped-workload-child-failed");
  const page = readFileSync(join(c.directory, "wiki.jsonl"), "utf8").trim().split("\n").map(line => JSON.parse(line));
  const samples = page.filter(row => row.type === "sample");
  if (samples.length !== 5 || samples.some(row => row.loadTimedOut === true || !Number.isFinite(row.loadMs) ||
      row.loadMs <= 0 || !Number.isFinite(row.ttfbMs))) {
    fail("five-valid-wikipedia-samples-required");
  }
  const speeds = [1, 2, 3].map(index => {
    const row = privateJson(join(c.directory, `fast-${index}.json`));
    const multiplier = { Kbps: 0.001, Mbps: 1, Gbps: 1000 }[row.displayUnits];
    const speed = Number(row.displayValue) * multiplier;
    if (row.type !== "fast-result" || row.valid !== true || row.completed !== true || !Number.isFinite(speed) || speed <= 0) {
      fail("three-valid-fast-results-required");
    }
    return speed;
  });
  const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
  return { wikipediaSamples: 5, wikipediaLoadMedianMs: median(samples.map(row => row.loadMs)),
    wikipediaTtfbMedianMs: median(samples.map(row => row.ttfbMs)), fastSamples: 3,
    fastMedianMbps: median(speeds), fastAtLeast40Mbps: median(speeds) >= 40 };
}

export function prepareArm(options) {
  if (realpathSync(options.root) !== realpathSync(join(SCRIPTS, "../../.."))) fail("orchestrator-checkout-root-mismatch");
  const run = prepareArtifactDirectory(options["run-dir"]);
  const canonicalRoot = realpathSync(options.root);
  if (run.canonical === canonicalRoot || run.canonical.startsWith(`${canonicalRoot}/`) ||
      canonicalRoot.startsWith(`${run.canonical}/`)) fail("private-run-outside-workspace-required");
  if (readdirSync(run.directory).length) fail("fresh-empty-run-directory-required");
  requireConfig(options.config);
  const c = armContext(options);
  c.directoryBindings = [run, prepareArtifactDirectory(c.artifacts, true), prepareArtifactDirectory(c.directory, true)];
  c.orchestrationSources = [SELF, join(SCRIPTS, "physical_h1_workload.sh")].map(hashNativeInputFile);
  writeFileSync(join(c.directory, "traffic-workload.sh"), readFileSync(join(SCRIPTS, "physical_h1_workload.sh")),
    { flag: "wx", mode: 0o600 });
  return c;
}

async function consume(manifest) {
  const c = privateJson(manifest);
  if (c.manifest !== manifest || c.mode !== "run" || c.scripts !== SCRIPTS) fail("matching-arm-consumer-required");
  requireNativeConsumerLock(c.root);
  const driver = new HostArmDriver(c); driver.assertInputs();
  const aapt = await driver.command({ id: "consumer-aapt", command: process.execPath, args: [join(SCRIPTS, "physical_aapt.mjs")] });
  const manifestTool = readFileSync(aapt.outputPath, "utf8").trim();
  const previousTool = readFileSync(join(c.directory, "aapt-resolve.stdout"), "utf8").trim();
  if (!isAbsolute(manifestTool) || manifestTool !== previousTool) fail("aapt-context-changed");
  await driver.command(h1AssemblyStep(c));
  await driver.command({ id: "apk-select", command: process.execPath, args: [join(SCRIPTS, "physical_apk_pair.mjs"), "select",
    "--observed", c.observed, "--app-metadata", join(c.root, "android/app/app/build/outputs/apk/github/debug/output-metadata.json"),
    "--test-metadata", join(c.root, "android/app/app/build/outputs/apk/androidTest/github/debug/output-metadata.json"),
    "--build-id", c["build-id"], "--aapt", manifestTool, "--app-output", c.app, "--test-output", c.test, "--output", c.selection] });
  requireNativeConsumerLock(c.root);
  // Retain the native artifact while the consumer wrapper still holds the lock.
  const source = join(c.root, "sdk/build/android/URnetworkSdk.aar");
  const sourceHash = hashNativeInputFile(source).sha256;
  const fd = openSync(c.aar, "wx", 0o600);
  try { writeSync(fd, readFileSync(source)); } finally { closeSync(fd); }
  if (sourceHash !== hashNativeInputFile(source).sha256 || sourceHash !== hashNativeInputFile(c.aar).sha256) fail("aar-changed-during-retention");
  const aarNative = join(c.directory, "aar-libgojni.so"); const apkNative = join(c.directory, "apk-libgojni.so");
  for (const [id, archive, entry, output] of [["aar-native", c.aar, "jni/arm64-v8a/libgojni.so", aarNative],
    ["apk-native", c.app, "lib/arm64-v8a/libgojni.so", apkNative]]) {
    await driver.command({ id, command: "unzip", args: ["-p", archive, entry], stdout: output });
    if (!lstatSync(output).size) fail("arm64-native-library-required");
  }
  let stripped = false; let stripHash = null;
  if (hashNativeInputFile(aarNative).sha256 !== hashNativeInputFile(apkNative).sha256) {
    const gradle = readFileSync(join(c.root, "android/app/app/build.gradle"), "utf8");
    const version = /^\s*ndkVersion\s*=\s*'([0-9.]+)'/m.exec(gradle)?.[1];
    if (!version) fail("pinned-ndk-version-required");
    const toolRoot = join(dirname(dirname(dirname(manifestTool))), "ndk", version, "toolchains/llvm/prebuilt");
    const candidates = readdirSync(toolRoot).filter(name => name.startsWith(process.platform === "darwin" ? "darwin-" : "linux-"))
      .map(name => join(toolRoot, name, "bin/llvm-strip")).filter(existsSync);
    if (candidates.length !== 1) fail("unambiguous-ndk-strip-tool-required");
    const output = join(c.directory, "stripped-libgojni.so");
    // NDK distributes llvm-strip as a symlink to llvm-objcopy. Hash the
    // executable target, but preserve argv[0]'s strip name for LLVM dispatch.
    stripHash = hashNativeInputFile(realpathSync(candidates[0])).sha256;
    await driver.command({ id: "native-strip", command: candidates[0], args: ["--strip-unneeded", "-o", output, aarNative] });
    if (hashNativeInputFile(output).sha256 !== hashNativeInputFile(apkNative).sha256) fail("native-strip-linkage-mismatch");
    stripped = true;
  }
  requireNativeConsumerLock(c.root);
  publish(join(c.directory, "binary-native-proof.json"), { eligible: true, abi: "arm64-v8a", profileRate: armProfileRate(c),
    measurementMode: armMeasurementMode(c),
    aar: hashNativeInputFile(c.aar), app: hashNativeInputFile(c.app), test: hashNativeInputFile(c.test),
    aarNative: hashNativeInputFile(aarNative), apkNative: hashNativeInputFile(apkNative), stripped, stripHash });
}

async function main() {
  process.umask(0o077);
  const options = parseArgs(process.argv.slice(2));
  if (options.mode === "consume") return consume(options.manifest);
  if (options.mode === "dry-run") {
    const c = armContext(options, "dry-run-no-build-owner");
    process.stdout.write(`${JSON.stringify({ type: "scoped-h1-plan", profile: PROFILE, profileRate: armProfileRate(c),
      measurementMode: armMeasurementMode(c), qualificationEligible: false, assembly: h1AssemblyStep(c), steps: h1Steps(c) }, null, 2)}\n`);
    return;
  }
  const foreground = spawnSync("ps", ["-o", "pgid=,tpgid=", "-p", String(process.pid)], { encoding: "utf8", timeout: 2_000 });
  const groups = foreground.stdout?.trim().split(/\s+/).map(Number) ?? [];
  requireRetainedForeground({ inputTTY: process.stdin.isTTY === true, outputTTY: process.stdout.isTTY === true,
    processGroup: groups[0], foregroundGroup: groups[1] });
  const c = prepareArm(options);
  publish(c.manifest, c);
  const driver = new HostArmDriver(c);
  // Signal the one supervised foreground operation; preserve evidence and run
  // cleanup without reusing this arm. No signal can convert a failed gate green.
  const handlers = ["SIGINT", "SIGTERM", "SIGHUP"].map(signal => [signal, () => {
    driver.interrupted = true;
    for (const handle of driver.handles.values()) if (handle.live) handle.interrupt();
  }]);
  for (const [signal, handler] of handlers) process.on(signal, handler);
  try {
    const result = await orchestrateH1(c, driver);
    publish(join(c["run-dir"], "result.json"), result);
    process.stdout.write(`${JSON.stringify(result)}\n`);
    if (!result.eligible) process.exitCode = 2;
  } finally { for (const [signal, handler] of handlers) process.off(signal, handler); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { process.stderr.write(`scoped H1 arm failed: ${safeReason(error)}\n`); process.exitCode = 2; });
}
