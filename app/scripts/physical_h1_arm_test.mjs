import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, mkdirSync, writeFileSync, readFileSync, lstatSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { armContext, h1Steps, h1AssemblyStep, HostArmDriver, LIMITS, launchPrivate, orchestrateH1, parseArgs,
  prepareArm, ptyCommand, evaluateDiagnosticMemory, validateDiagnosticCensus, retainDiagnosticTail,
  retainClientCleanupResult } from "./physical_h1_arm.mjs";
import { checkCollectorSession } from "./physical_collector_session.mjs";
import { captureWorkloadScriptPreflight } from "./physical_workload_script.mjs";
import { parseArgs as parseDiagnosticArgs } from "./physical_diagnostic_command.mjs";
import { parseArgs as parseCopyArgs } from "./physical_diagnostic_copy.mjs";

const scripts = dirname(fileURLToPath(import.meta.url));
const root = resolve(scripts, "../../..");
const args = run => ["run", "--root", root, "--run-dir", run, "--serial", "3B161FDJG001KT", "--label", "h1-frozen",
  "--build-id", "ios-frozen", "--underlay", "wifi", "--config", "/private/credentials.yml", "--cdp-port", "19322",
  "--gomaxprocs", "10", "--max-workers", "4"];
const context = () => armContext(parseArgs(args("/private/arm-root"), 14), "frozen-native-owner");
const diagnosticContext = () => armContext(parseArgs([...args("/private/arm-root"), "--measurement-mode", "diagnostic"], 14), "frozen-native-owner");
const fixture = t => {
  const dir = mkdtempSync(join(tmpdir(), "h1-arm-test-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  return dir;
};

class FakeDriver {
  constructor(failure) { this.events = []; this.failure = failure; this.finished = new Set(); }
  async execute(step) {
    this.events.push(step.id);
    if (step.id === this.failure) throw new Error("deliberate fake failure with no device access");
    return step.id === "scoped-results" ? { fastMedianMbps: 42 } : undefined;
  }
  async start(step) { this.events.push(`start:${step.id}`); return { id: step.id }; }
  async join(handle, timeoutMs) { assert.ok(timeoutMs > 0); this.events.push(`join:${handle.id}`); this.finished.add(handle.id); }
  async stop(handle) { this.events.push(`stop:${handle.id}`); }
}

test("the real schedule binds one root, identifiers and rate zero through Bash writer/consumer", () => {
  const c = context(); const plan = h1Steps(c);
  const writer = plan.setup.find(step => step.id === "native-writer");
  const consumer = plan.setup.find(step => step.id === "native-consumer");
  assert.equal(writer.command, "bash"); assert.equal(consumer.command, "bash");
  for (const step of [writer, plan.setup.find(step => step.id === "native-before")]) {
    assert.equal(step.args[step.args.indexOf("--profile-rate") + 1], "0");
    assert.equal(step.args[step.args.indexOf("--build-id") + 1], "ios-frozen");
    assert.equal(step.args[step.args.indexOf("--root") + 1], root);
  }
  assert.ok(consumer.args.includes(c.before)); assert.ok(consumer.args.includes(c.after)); assert.ok(consumer.args.includes(c.proof));
  assert.deepEqual(consumer.args.slice(-3), ["consume", "--manifest", c.manifest]);
  const stage = plan.setup.find(step => step.id === "credential-stage");
  assert.ok(stage.args.includes(c.credentialOwner)); assert.ok(stage.args.includes(c.proof));
  assert.equal(stage.args[stage.args.indexOf("--schema") + 1], "user-pass");
  assert.ok(plan.instrument.args.includes(c.credentialOwner)); assert.ok(plan.instrument.args.includes(c.proof));
  assert.ok(plan.collector.args.includes(c.owner)); assert.ok(plan.collector.args.includes(c.connectId));
  assert.equal(plan.traffic.find(step => step.id === "workload").args.at(-1), c.workloads);
  assert.equal(plan.finish.commandId, c.finishId);
  assert.ok(plan.credentialFinish.args.includes(c.finishId));
  assert.ok(plan.teardown.args.includes(c.liveGate)); assert.ok(plan.teardown.args.includes(c.finalMemory));
});

test("normal schedule stops after the last install, binds profile/role before traffic, and joins before credential finish", async () => {
  const driver = new FakeDriver(); const result = await orchestrateH1(context(), driver);
  assert.equal(result.eligible, true); assert.equal(result.memoryQualified, true); assert.equal(result.cleanupComplete, true);
  const before = (a, b) => assert.ok(driver.events.indexOf(a) < driver.events.indexOf(b), `${a} before ${b}`);
  for (const [a, b] of [["native-before", "native-writer"], ["native-writer", "native-consumer"], ["native-consumer", "install-app"],
    ["install-app", "install-test"], ["install-test", "postinstall-stop"], ["postinstall-stop", "credential-stage"],
    ["credential-stage", "start:instrumentation"], ["instrumentation-bind", "profile-gate"], ["profile-gate", "h1-connect"],
    ["h1-role", "start:collector"], ["start:collector", "collector-published"], ["collector-published", "collector-ready"],
    ["collector-ready", "chrome-start"], ["chrome-ready", "workload"],
    ["workload", "quiet-start"], ["quiet-start", "quiet-end"], ["memory-live-gate", "finish"],
    ["finish", "join:instrumentation"], ["join:instrumentation", "memory-final"], ["memory-final", "collector-stop"],
    ["join:collector", "memory-teardown-gate"], ["join:instrumentation", "credential-finish"]]) before(a, b);
  assert.equal(driver.events.filter(value => value === "quiet-end").length, 1);
  assert.equal(driver.events.filter(value => value === "workload").length, 1);
  assert.equal(driver.events.includes("credential-rollback"), false);
});

test("build/staging/profile/role/workload failures never advance or retry the failed gate", async () => {
  for (const [failure, forbidden] of [["native-consumer", "install-app"], ["postinstall-stop", "credential-stage"],
    ["credential-stage", "start:instrumentation"], ["profile-gate", "h1-connect"], ["h1-role", "start:collector"],
    ["workload", "quiet-start"]]) {
    const driver = new FakeDriver(failure); const result = await orchestrateH1(context(), driver);
    assert.equal(result.eligible, false, failure);
    assert.equal(driver.events.includes(forbidden), false, `${failure} must prevent ${forbidden}`);
    assert.equal(driver.events.filter(value => value === failure).length, 1);
    if (["profile-gate", "h1-role", "workload"].includes(failure)) {
      assert.ok(driver.events.includes("finish")); assert.ok(driver.events.includes("join:instrumentation"));
      assert.ok(driver.events.includes("credential-finish"));
    }
  }
});

test("an absolute memory failure still pulls teardown evidence and fails the arm", async () => {
  const driver = new FakeDriver("memory-live-gate"); const result = await orchestrateH1(context(), driver);
  assert.equal(result.eligible, false); assert.equal(result.memoryQualified, false);
  for (const step of ["finish", "join:instrumentation", "memory-final", "collector-stop", "join:collector", "memory-teardown-gate", "credential-finish"]) {
    assert.ok(driver.events.includes(step), step);
  }
  assert.equal(driver.events.filter(value => value === "quiet-start").length, 1);
});

test("quiet uses the original full sampled helper and each Fast.com child retains its independent watchdog", () => {
  const c = context(); const steps = h1Steps(c);
  const end = steps.traffic.find(step => step.id === "quiet-end");
  assert.ok(end.args[0].endsWith("physical_quiet_phase.mjs"));
  assert.deepEqual(end.args.slice(1), ["--serial", c.serial, "--start", c.start, "--output", c.end]);
  assert.equal(end.timeoutMs, 425_000);
  const body = readFileSync(join(scripts, "physical_h1_workload.sh"), "utf8");
  assert.match(body, /for n in 1 2 3/);
  assert.match(body, /child --name "fast-\$n" --timeout-ms 95000/);
  assert.match(body, /--port "\$CDP_PORT" --timeout-ms 90000/);
  assert.match(body, /--runs 5 https:\/\/www\.wikipedia\.org\//);
  assert.doesNotMatch(body, /cnn|bloomberg|heap-profile|owner-census|trim-memory/);
  const workload = steps.traffic.find(step => step.id === "workload");
  assert.equal(workload.args[workload.args.indexOf("--children") + 1], "wiki,fast-1,fast-2,fast-3");
});

test("serial, root scope, labels, port and CPU limits fail closed before any invocation", () => {
  const mutate = (key, value) => { const result = args("/private/arm-root"); result[result.indexOf(key) + 1] = value; return result; };
  for (const [key, value] of [["--serial", "arbitrary-device"], ["--root", "/"], ["--run-dir", "/Users"],
    ["--run-dir", `${root}/artifacts`], ["--run-dir", "/private/../tmp/test"], ["--label", "../other-arm"],
    ["--build-id", "x;echo secret"], ["--cdp-port", "0"], ["--underlay", "auto"], ["--max-workers", "5"], ["--gomaxprocs", "11"]]) {
    assert.throws(() => parseArgs(mutate(key, value), 14), undefined, `${key} ${value}`);
  }
  assert.throws(() => parseArgs([...args("/private/arm-root"), "--profile-rate", "65536"], 14));
  assert.throws(() => parseArgs([...args("/private/arm-root"), "--label", "second"], 14));
});

test("prepare owns fresh private leaves and its exact workload passes the existing script preflight", t => {
  const dir = fixture(t); const run = join(dir, "run"); mkdirSync(run, { mode: 0o700 });
  const config = join(dir, "credentials.yml"); writeFileSync(config, "synthetic: unused\n", { mode: 0o600 });
  const argv = args(run); argv[argv.indexOf("--config") + 1] = config;
  const options = parseArgs(argv, 14); const c = prepareArm(options);
  for (const path of [c.artifacts, c.directory]) assert.equal(lstatSync(path).mode & 0o777, 0o700);
  assert.equal(lstatSync(join(c.directory, "traffic-workload.sh")).mode & 0o777, 0o600);
  const proof = captureWorkloadScriptPreflight({ mode: "script-preflight", label: c.label, output: c.workloads });
  assert.equal(proof.eligible, true);
  assert.throws(() => prepareArm(options), /fresh-empty-run-directory-required/);
});

test("PTY argv remains literal under both supported host command forms", () => {
  const weird = "spaces 'quotes' ; $(false) `false`";
  assert.deepEqual(ptyCommand("/bin/echo", [weird], "darwin"), { command: "/usr/bin/script", args: ["-q", "/dev/null", "/bin/echo", weird] });
  assert.match(ptyCommand("/bin/echo", [weird], "linux").args[4], /^exec '\/bin\/echo' '/);
  assert.throws(() => ptyCommand("/bin/echo", [], "win32"));
});

test("actual retained PTY satisfies the existing foreground guard and propagates nonzero exit", async t => {
  const dir = fixture(t); const c = { ...context(), directory: dir };
  const module = new URL("./physical_collector_session.mjs", import.meta.url).href;
  const code = `import {spawnSync} from 'node:child_process'; import {requireRetainedForeground} from ${JSON.stringify(module)};
    const [group,foreground] = spawnSync('ps',['-o','pgid=,tpgid=','-p',String(process.pid)],{encoding:'utf8'}).stdout.trim().split(/\\s+/).map(Number);
    requireRetainedForeground({inputTTY:process.stdin.isTTY===true,outputTTY:process.stdout.isTTY===true,processGroup:group,foregroundGroup:foreground});
    process.stdout.write('foreground-verified\\n'); process.exit(7);`;
  const handle = launchPrivate({ id: "pty-proof", command: process.execPath, args: ["--input-type=module", "-e", code],
    retained: true, timeoutMs: 5000 }, c, process.stdin.isTTY ? {} : { ptyInput: "ignore" });
  const result = await handle.done;
  assert.equal(result.exitCode, 7, readFileSync(handle.outputPath, "utf8") + readFileSync(handle.errorPath, "utf8"));
  assert.equal(result.eligible, false); assert.equal(result.timedOut, false);
  assert.match(readFileSync(handle.outputPath, "utf8"), /foreground-verified/);
});

// Keep the production collector argument vector, foreground guard, artifact
// checks, role gate, owner publication and child join. Only the instrumentation
// observations and telemetry child are synthetic; no ADB command is launched.
function collectorLaunchFixture(t) {
  const dir = fixture(t);
  const c = armContext(parseArgs(args(dir), 14), "fixture-native-owner");
  mkdirSync(c.artifacts, { mode: 0o700 }); mkdirSync(c.directory, { mode: 0o700 });
  const step = h1Steps(c).collector;
  const helper = join(dir, "collector-fixture.mjs");
  const module = new URL("./physical_collector_session.mjs", import.meta.url).href;
  const captureCode = `
    const fs = require('node:fs');
    const [output,label,stop] = process.argv.slice(1);
    const emit = row => fs.appendFileSync(output, JSON.stringify(row)+'\\n', {mode:0o600});
    process.stdout.write('capture-stdout\\n'); process.stderr.write('capture-stderr\\n');
    emit({type:'environment',label});
    const tick = setInterval(() => {
      if (fs.existsSync(stop)) { emit({type:'summary'}); clearInterval(tick); return; }
      const now = Date.now();
      emit({type:'sample',startTimeUnixMs:now-2,endTimeUnixMs:now,eligibility:{eligible:true},telemetryErrors:[]});
    },20);
    setTimeout(()=>process.exit(99),10000).unref();`;
  writeFileSync(helper, `
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {writeFileSync} from 'node:fs';
import {parseArgs,runCollectorSession,formatSessionFailure} from ${JSON.stringify(module)};
const options = parseArgs(process.argv.slice(2));
const value = key => options.captureArgs[options.captureArgs.indexOf(key)+1];
const serial = value('--serial'); const app = 'com.bringyour.network';
const supervisorPid = 912345; const adbPid = 912346;
const started = 'Mon Sep 21 10:11:12 2026';
const command = pid => pid === supervisorPid ? '/node fixture-owner' :
  '/sdk/platform-tools/adb -s '+serial+' shell am instrument -w -e class '+app+'.acceptance.PhysicalLowbarSessionTest '+app+'.test/androidx.test.runner.AndroidJUnitRunner';
const hash = text => createHash('sha256').update(text).digest('hex');
const identity = pid => hash(pid+'\\n'+started+'\\n'+command(pid));
const ownerId = '11111111-2222-3333-4444-555555555555';
writeFileSync(options['instrumentation-owner'], JSON.stringify({schema:1,type:'instrumentation-session',state:'running',
  ownerId,supervisorPid,adbPid,supervisorIdentity:identity(supervisorPid),adbIdentity:identity(adbPid),serialHash:hash(serial),
  label:value('--label'),className:app+'.acceptance.PhysicalLowbarSessionTest',targetPackage:app,
  component:app+'.test/androidx.test.runner.AndroidJUnitRunner',startedHostTimeUnixMs:Date.now(),
  foreground:{inputTTY:true,outputTTY:true,processGroup:supervisorPid,foregroundGroup:supervisorPid}}), {flag:'wx',mode:0o600});
writeFileSync(options['instrumentation-owner']+'.ready.json',JSON.stringify({schema:1,type:'instrumentation-session-ready',
  ownerId,serialHash:hash(serial),targetPid:1234,elapsedMs:100}), {flag:'wx',mode:0o600});
try {
  process.stdout.write('fixture-owner-start\\n');
  process.exitCode = await runCollectorSession(options, {
    hostProcess: pid => ({status:0,stdout:pid+' S '+started+' '+command(pid)+'\\n'}),
    adb: args => {
      assert.deepEqual(args.slice(0,3),['-s',serial,'shell']);
      if (args[3] === 'pidof') { assert.deepEqual(args.slice(3),['pidof',app]); return {status:0,stdout:'1234\\n'}; }
      assert.deepEqual(args.slice(3),['run-as',app,'cat','files/acceptance/physical-status']);
      return {status:0,stdout:JSON.stringify({type:'status',pid:1234,commandId:options['connect-command-id'],
        state:'complete',phase:'connect-h1',elapsedMs:200,transportMode:'h1',connected:true,tunnelStarted:true,provideEnabled:false})};
    },
    spawn: (exe,argv,settings) => {
      assert.equal(exe,process.execPath); assert.ok(argv[0].endsWith('/physical_lowbar_capture.mjs'));
      assert.deepEqual(argv.slice(1),options.captureArgs);
      return spawn(process.execPath,['-e',${JSON.stringify(captureCode)},value('--output'),value('--label'),value('--stop-file')],settings);
    }
  });
  process.stdout.write('fixture-owner-done\\n');
} catch (error) { process.stderr.write(formatSessionFailure(error)); process.exitCode = 2; }
`, { flag: "wx", mode: 0o600 });
  return { c, step: { ...step, command: process.execPath, args: [helper, "run", ...step.args.slice(2)], timeoutMs: 10_000 } };
}

test("canonical collector PTY publishes readiness with distinct owner and capture output streams", async t => {
  const { c, step } = collectorLaunchFixture(t);
  const handle = launchPrivate(step, c, process.stdin.isTTY ? {} : { ptyInput: "ignore" });
  try {
    const deadline = Date.now() + 5000;
    while (!existsSync(c.collector) && handle.live && Date.now() < deadline) await new Promise(done => setTimeout(done, 10));
    assert.equal(existsSync(c.collector), true, readFileSync(handle.outputPath, "utf8"));
    const pid = await checkCollectorSession({ owner: c.collector, timeoutMs: 2000 });
    assert.ok(pid > 0); assert.equal(handle.live, true);
    writeFileSync(join(c.directory, "collector.stop"), "", { flag: "wx", mode: 0o600 });
    assert.deepEqual(await handle.done, { exitCode: 0, signal: null, timedOut: false, eligible: true });
    const terminal = JSON.parse(readFileSync(`${c.collector}.terminal.json`, "utf8"));
    assert.equal(terminal.state, "complete"); assert.equal(terminal.collectorPid, pid);
    assert.equal(terminal.interrupted, false);
    const output = step.args[step.args.indexOf("--stdout") + 1];
    const errors = step.args[step.args.indexOf("--stderr") + 1];
    assert.equal(readFileSync(output, "utf8"), "capture-stdout\n");
    assert.equal(readFileSync(errors, "utf8"), "capture-stderr\n");
    assert.match(readFileSync(handle.outputPath, "utf8"), /fixture-owner-start[\r\n]+fixture-owner-done/);
    for (const path of [handle.outputPath, handle.errorPath, output, errors, c.collector, `${c.collector}.terminal.json`]) {
      assert.equal(lstatSync(path).mode & 0o777, 0o600);
    }
  } finally {
    if (handle.live) { handle.interrupt(); await handle.done; }
  }
});

test("collector still refuses a real capture-output collision before owner publication", async t => {
  const { c, step } = collectorLaunchFixture(t);
  const output = step.args[step.args.indexOf("--stdout") + 1];
  const handle = launchPrivate({ ...step, stdout: output }, c, process.stdin.isTTY ? {} : { ptyInput: "ignore" });
  const result = await handle.done;
  assert.equal(result.exitCode, 2); assert.equal(result.eligible, false); assert.equal(result.timedOut, false);
  assert.match(readFileSync(handle.outputPath, "utf8"), /fresh-collector-artifacts-required/);
  assert.equal(existsSync(c.collector), false); assert.equal(existsSync(c.telemetry), false);
});

test("process deadline cannot be rescued by a zero exit from its TERM handler", async t => {
  const dir = fixture(t); const c = { ...context(), directory: dir };
  const handle = launchPrivate({ id: "deadline-proof", command: process.execPath,
    args: ["-e", "process.on('SIGTERM',()=>process.exit(0));setInterval(()=>{},1000)"], timeoutMs: 500 }, c);
  const result = await handle.done;
  assert.equal(result.timedOut, true); assert.equal(result.eligible, false); assert.equal(result.exitCode, 0);
  assert.equal(handle.live, false); assert.equal(LIMITS.kill, 5000);
});

function stoppedTargetFixture(t, mode, operation = "installedCheck") {
  const dir = fixture(t); const output = join(dir, "output"); const bin = join(dir, "bin");
  mkdirSync(output, { mode: 0o700 }); mkdirSync(bin, { mode: 0o700 });
  const c = { ...context(), directory: output, app: join(dir, "app.apk"), test: join(dir, "test.apk"),
    directoryBindings: [], orchestrationSources: [] };
  writeFileSync(c.app, "synthetic-app-apk", { mode: 0o600 });
  writeFileSync(c.test, "synthetic-test-apk", { mode: 0o600 });
  // Exercise the production HostArmDriver and the helper's default ADB path.
  // Only this subprocess sees the fake PATH; no physical command is forwarded.
  writeFileSync(join(bin, "adb"), `#!${process.execPath}
const {appendFileSync,readFileSync} = require('node:fs');
const {join} = require('node:path');
const args = process.argv.slice(2); const dir = process.env.H1_TARGET_FIXTURE;
appendFileSync(join(dir,'calls.jsonl'),JSON.stringify(args)+'\\n',{mode:0o600});
if (args[0] !== '-s' || args[1] !== '3B161FDJG001KT') process.exit(97);
const command = args.slice(2); const app = 'com.bringyour.network';
if (command[0] === 'shell' && command[1] === 'pm' && command[2] === 'path' &&
    [app,app+'.test'].includes(command[3]) && command.length === 4) {
  process.stdout.write('package:/data/app/'+(command[3] === app ? 'app' : 'test')+'/base.apk\\n');
} else if (command[0] === 'exec-out' && command[1] === 'cat' && command.length === 3 &&
    ['/data/app/app/base.apk','/data/app/test/base.apk'].includes(command[2])) {
  const key = command[2].includes('/test/') ? 'test' : 'app';
  process.stdout.write(process.env.H1_TARGET_MODE === 'mismatched-apk' && key === 'app'
    ? 'different-app-apk' : readFileSync(join(dir,key+'.apk')));
} else if (JSON.stringify(command) === JSON.stringify(['shell','pidof',app])) {
  if (process.env.H1_TARGET_MODE === 'live') { process.stdout.write('1234\\n'); process.exit(0); }
  if (process.env.H1_TARGET_MODE === 'transport-error') { process.stderr.write('synthetic transport error\\n'); process.exit(1); }
  if (process.env.H1_TARGET_MODE === 'wrong-status') process.exit(2);
  if (process.env.H1_TARGET_MODE === 'empty-success') process.exit(0);
  process.exit(1);
} else if (JSON.stringify(command) === JSON.stringify(['exec-out','run-as',app,'cat','files/acceptance/active-client-ids'])) {
  // Stop cleanup before config/account access, after the real stopped-app gate.
  process.exit(2);
} else process.exit(97);
`, { mode: 0o700 });
  const module = new URL("./physical_h1_arm.mjs", import.meta.url).href;
  const code = `import {HostArmDriver} from ${JSON.stringify(module)};
const driver = new HostArmDriver(JSON.parse(process.env.H1_TARGET_CONTEXT));
try { await driver[process.env.H1_TARGET_OPERATION](); process.stdout.write(JSON.stringify({passed:true})); }
catch(error) { process.stdout.write(JSON.stringify({passed:false,reason:error.message})); }`;
  const child = spawnSync(process.execPath, ["--input-type=module", "-e", code], {
    encoding: "utf8", timeout: 15_000, env: { ...process.env, PATH: `${bin}:${process.env.PATH}`,
      H1_TARGET_FIXTURE: dir, H1_TARGET_MODE: mode, H1_TARGET_CONTEXT: JSON.stringify(c), H1_TARGET_OPERATION: operation },
  });
  assert.equal(child.status, 0, child.stderr); assert.equal(child.error, undefined);
  const calls = existsSync(join(dir, "calls.jsonl"))
    ? readFileSync(join(dir, "calls.jsonl"), "utf8").trim().split("\n").map(line => JSON.parse(line)) : [];
  return { c, result: JSON.parse(child.stdout), calls };
}

test("installed-check verifies the retained APKs then proves the stopped app through default ADB", t => {
  const { c, result, calls } = stoppedTargetFixture(t, "stopped");
  assert.deepEqual(result, { passed: true });
  assert.deepEqual(calls.at(-1), ["-s", c.serial, "shell", "pidof", "com.bringyour.network"]);
  assert.equal(calls.length, 5);
  const proof = join(c.directory, "installed-apk-proof.json");
  assert.deepEqual(JSON.parse(readFileSync(proof)), { eligible: true, appMatches: true, testMatches: true });
  assert.equal(lstatSync(proof).mode & 0o777, 0o600);
});

test("installed-check rejects a live app and uncertain pidof results without publishing eligibility", async t => {
  for (const mode of ["live", "transport-error", "wrong-status", "empty-success"]) await t.test(mode, t => {
    const { c, result, calls } = stoppedTargetFixture(t, mode);
    assert.deepEqual(result, { passed: false, reason: "credential-target-not-proven-stopped" });
    assert.equal(calls.length, 5);
    assert.equal(existsSync(join(c.directory, "installed-apk-proof.json")), false);
  });
});

test("installed-check still rejects a replaced installed APK before accepting stopped state", t => {
  const { c, result, calls } = stoppedTargetFixture(t, "mismatched-apk");
  assert.deepEqual(result, { passed: false, reason: "installed-apk-hash-mismatch" });
  assert.equal(calls.some(args => args.includes("pidof")), false);
  assert.equal(existsSync(join(c.directory, "installed-apk-proof.json")), false);
});

test("client cleanup also uses the default stopped-app probe before reading its owned ledger", t => {
  const stopped = stoppedTargetFixture(t, "stopped", "cleanupClients");
  assert.deepEqual(stopped.result, { passed: false, reason: "active-client-ledger-failed" });
  assert.deepEqual(stopped.calls.map(args => args.slice(2)), [
    ["shell", "pidof", "com.bringyour.network"],
    ["exec-out", "run-as", "com.bringyour.network", "cat", "files/acceptance/active-client-ids"],
  ]);
  const live = stoppedTargetFixture(t, "live", "cleanupClients");
  assert.deepEqual(live.result, { passed: false, reason: "credential-target-not-proven-stopped" });
  assert.equal(live.calls.length, 1);
});

test("explicit diagnostic mode binds rate 65536 through native build, APK assembly and effective runtime gate", () => {
  const c = diagnosticContext(); const plan = h1Steps(c);
  for (const id of ["native-before", "native-writer"]) {
    const step = plan.setup.find(step => step.id === id);
    assert.equal(step.args[step.args.indexOf("--profile-rate") + 1], "65536");
  }
  assert.ok(h1AssemblyStep(c).args.includes("-PurnetworkMemoryProfileRateBytes=65536"));
  assert.ok(h1AssemblyStep(context()).args.includes("-PurnetworkMemoryProfileRateBytes=0"));
  const gate = plan.preCollector.find(step => step.id === "profile-gate");
  assert.equal(gate.args[gate.args.indexOf("--mode") + 1], "diagnostic");
  assert.equal(context()["measurement-mode"], "qualification");
  for (const value of ["65536", "profiled-qualification", "", "Diagnostic"]) {
    assert.throws(() => parseArgs([...args("/private/arm-root"), "--measurement-mode", value], 14));
  }
  assert.throws(() => parseArgs([...args("/private/arm-root"), "--measurement-mode", "diagnostic", "--measurement-mode", "qualification"], 14));
});

test("diagnostic descriptors use the actual strict command/copy parsers and private sibling artifacts", () => {
  const c = diagnosticContext(); const steps = h1Steps(c).traffic;
  const commands = steps.filter(step => step.kind === "command" && step.args[0].endsWith("physical_diagnostic_command.mjs"));
  assert.equal(commands.length, 8);
  for (const step of commands) {
    const parsed = parseDiagnosticArgs(step.args.slice(1));
    assert.equal(parsed.owner, c.owner); assert.equal(parsed.serial, c.serial);
    assert.equal(dirname(parsed.output), dirname(c.owner));
    const copy = steps.find(candidate => candidate.id === `${step.id}-copy`);
    const output = parseCopyArgs(copy.args.slice(1));
    assert.equal(output.receipt, parsed.output); assert.equal(dirname(output.output), dirname(c.owner));
    assert.ok(!parsed["command-id"].includes(c.label), "session UUID helper owns identity, not an unbounded arm-label concatenation");
  }
});

test("diagnostic schedule brackets traffic with pre-GC census, forced-GC heap, post-GC census and stacks then a 45s tail", async () => {
  const driver = new FakeDriver(); const result = await orchestrateH1(diagnosticContext(), driver);
  assert.equal(result.eligible, true); assert.equal(result.memoryQualified, false);
  assert.equal(result.qualificationEligible, false); assert.equal(result.measurementMode, "diagnostic");
  assert.equal(result.profileRate, 65536); assert.equal(result.classification, "SCOPED_H1_DIAGNOSTIC_COMPLETE");
  assert.equal(result.cleanupComplete, true);
  const boundaries = ["connected-idle", "post-traffic"].flatMap(name => [
    `diagnostic-${name}-boundary`, `diagnostic-${name}-before-gc`, `diagnostic-${name}-before-gc-copy`, `diagnostic-${name}-before-gc-check`,
    `diagnostic-${name}-heap`, `diagnostic-${name}-heap-copy`, `diagnostic-${name}-after-gc`, `diagnostic-${name}-after-gc-copy`,
    `diagnostic-${name}-after-gc-check`, `diagnostic-${name}-stacks`, `diagnostic-${name}-stacks-copy`,
  ]);
  const expected = ["h1-role", "start:collector", "collector-ready", ...boundaries.slice(0, 11),
    "chrome-start", "workload", ...boundaries.slice(11), "diagnostic-tail", "memory-live", "finish", "join:instrumentation",
    "memory-final", "collector-stop", "join:collector", "diagnostic-memory", "clients-cleanup", "credential-finish"];
  let previous = -1;
  for (const id of expected) { const at = driver.events.indexOf(id); assert.ok(at > previous, id); previous = at; }
  for (const id of ["quiet-start", "quiet-end", "memory-live-gate", "memory-teardown-gate"]) assert.ok(!driver.events.includes(id), id);
  assert.equal(driver.events.filter(id => id === "workload").length, 1);
});

test("diagnostic capability and capture failures never advance or retry but still join and clean up", async () => {
  for (const [failure, forbidden] of [["diagnostic-connected-idle-before-gc-check", "chrome-start"],
    ["diagnostic-connected-idle-after-gc-check", "chrome-start"], ["diagnostic-post-traffic-heap", "diagnostic-tail"],
    ["diagnostic-tail", "memory-live"]]) {
    const driver = new FakeDriver(failure); const result = await orchestrateH1(diagnosticContext(), driver);
    assert.equal(result.eligible, false, failure); assert.equal(result.qualificationEligible, false);
    assert.equal(result.classification, "SCOPED_H1_DIAGNOSTIC_FAILED");
    assert.equal(driver.events.includes(forbidden), false, forbidden);
    assert.equal(driver.events.filter(id => id === failure).length, 1);
    for (const id of ["finish", "join:instrumentation", "memory-final", "join:collector", "diagnostic-memory", "credential-finish"]) {
      assert.ok(driver.events.includes(id), `${failure}: ${id}`);
    }
  }
});

const census = () => {
  const runtime = { runtime_bytes: 26_000_000, heap_object_bytes: 10_000_000, heap_unused_bytes: 5_000_000,
    heap_free_bytes: 1_000_000, stack_bytes: 3_000_000, gc_cycles: 10, forced_gc_cycles: 0, goroutines: 100 };
  return { schema: 1, unix_millis: 1000, memory_profile_rate_bytes: 65536, before: { ...runtime }, after: { ...runtime },
    owners: { network_space_api: {}, client_windows: {}, provider_transfer: {}, dns: {}, process_transfer_pools: {},
      process_transport_claims: {}, block_actions: 0, block_action_slots: 0 },
    allocator_size_classes: Array.from({ length: 61 }, (_, index) => ({ size_bytes: index * 8, live_objects: 0 })) };
};

test("owner census capability preflight requires the actual rate, bounded metrics and complete owner/size-class shape", () => {
  assert.equal(validateDiagnosticCensus(census()).eligible, true);
  for (const mutation of [row => { row.schema = 0; }, row => { row.memory_profile_rate_bytes = 0; },
    row => { delete row.owners.client_windows; }, row => { row.before.heap_unused_bytes = -1; },
    row => { row.after.runtime_bytes = 0; }, row => { row.allocator_size_classes.pop(); },
    row => { row.allocator_size_classes[1].live_objects = NaN; }]) {
    const row = census(); mutation(row);
    assert.throws(() => validateDiagnosticCensus(row), /diagnostic-owner-census-capability-required/);
  }
  const before = census(); const after = census(); after.before.forced_gc_cycles++;
  assert.equal(validateDiagnosticCensus(after, before).eligible, true);
  assert.throws(() => validateDiagnosticCensus(before, before), /diagnostic-heap-gc-not-observed/);
  after.unix_millis--;
  assert.throws(() => validateDiagnosticCensus(after, before), /diagnostic-heap-gc-not-observed/);
});

const connectedState = elapsedMs => ({ sessionId: "fixed-session", pid: 1234, commandId: "fixed-stacks", state: "complete",
  phase: "goroutine-stacks", elapsedMs, connected: true, tunnelStarted: true, provideEnabled: false, transportMode: "h1" });

test("retained diagnostic tail observes both endpoints and cannot shorten the fixed 45 seconds", async () => {
  let elapsed = 0; const observations = []; const pauses = [];
  const result = await retainDiagnosticTail({ now: () => elapsed,
    readState: async () => { observations.push(elapsed); return connectedState(elapsed); },
    sleep: async ms => { pauses.push(ms); elapsed += ms; } });
  assert.equal(result.elapsedMs, 45000); assert.equal(result.checks, 10); assert.equal(result.qualificationEligible, false);
  assert.deepEqual(observations, Array.from({ length: 10 }, (_, index) => index * 5000));
  assert.deepEqual(pauses, Array(9).fill(5000));
});

test("retained diagnostic tail rejects replaced sessions, activity/role changes, clock rollback and stalled checks", async () => {
  for (const patch of [{ pid: 4321 }, { sessionId: "replacement" }, { state: "busy" }, { elapsedMs: -1 },
    { connected: false }, { tunnelStarted: false }, { provideEnabled: true }, { transportMode: "h3" },
    { commandId: "other-command" }, { phase: "other-phase" }]) {
    let elapsed = 0;
    await assert.rejects(retainDiagnosticTail({ now: () => elapsed,
      readState: async () => ({ ...connectedState(elapsed), ...(elapsed ? patch : {}) }), sleep: async ms => { elapsed += ms; } }), /diagnostic-/);
  }
  for (const jump of [-1, 80000]) {
    let elapsed = 0;
    await assert.rejects(retainDiagnosticTail({ now: () => elapsed,
      readState: async () => connectedState(10), sleep: async () => { elapsed = jump; } }), /diagnostic-tail-deadline/);
  }
  await assert.rejects(retainDiagnosticTail({ readState: async () => { throw new Error("collector-exited"); } }), /collector-exited/);
});

const memorySample = (elapsedMs, bytes = 23 * 1024 * 1024) => ({ type: "sample", elapsedMs, goRuntimeBytes: bytes,
  goMemoryProfileRateBytes: 65536, goMemoryLimitBytes: 32 * 1024 * 1024, samplerDropped: 0 });

test("diagnostic memory preserves exact raw global/teardown breaches and never confers qualification", () => {
  const result = evaluateDiagnosticMemory([memorySample(0, 29_405_216), memorySample(15000), memorySample(30000, 25_268_256)]);
  assert.equal(result.peakGoRuntimeBytes, 29_405_216); assert.equal(result.goRuntimeBreachSampleCount, 2);
  assert.equal(result.qualificationEligible, false); assert.equal(result.sampleCount, 3);
  assert.equal(result.goRuntimeLimitBytes, 25_165_824);
  assert.equal(evaluateDiagnosticMemory([memorySample(0, 1)]).qualificationEligible, false);
  for (const records of [[], [{ type: "error" }], [memorySample(0), memorySample(0)],
    [{ ...memorySample(0), samplerDropped: 1 }], [{ ...memorySample(0), goMemoryProfileRateBytes: 0 }],
    [{ ...memorySample(0), goMemoryLimitBytes: 64 * 1024 * 1024 }], [memorySample(0, 0)]]) {
    assert.throws(() => evaluateDiagnosticMemory(records), /diagnostic-memory-/);
  }
});

test("diagnostic owner checks run through the actual driver before any traffic and reject qualification use", async t => {
  const dir = fixture(t); const path = join(dir, "census.json");
  writeFileSync(path, JSON.stringify(census()), { mode: 0o600 });
  const c = { ...diagnosticContext(), directory: dir, directoryBindings: [], orchestrationSources: [] };
  const driver = new HostArmDriver(c); const step = { id: "fixture-census-check", kind: "diagnostic-census-check", path };
  assert.equal((await driver.execute(step)).eligible, true);
  const qualified = new HostArmDriver({ ...c, "measurement-mode": "qualification" });
  await assert.rejects(qualified.execute(step), /diagnostic-mode-required/);
  driver.interrupted = true;
  await assert.rejects(driver.diagnosticState(), /arm-interrupted/);
  driver.interrupted = false;
  await assert.rejects(driver.diagnosticState(), /diagnostic-retained-owner-not-live/);
});

test("actual retained PTY can complete the canonical diagnostic schedule without becoming qualification", async t => {
  const dir = fixture(t); const c = { ...diagnosticContext(), directory: dir };
  const module = new URL("./physical_h1_arm.mjs", import.meta.url).href;
  const foreground = new URL("./physical_collector_session.mjs", import.meta.url).href;
  const code = `import assert from 'node:assert/strict'; import {spawnSync} from 'node:child_process';
import {orchestrateH1,retainDiagnosticTail} from ${JSON.stringify(module)};
import {requireRetainedForeground} from ${JSON.stringify(foreground)};
const [group,foreground] = spawnSync('ps',['-o','pgid=,tpgid=','-p',String(process.pid)],{encoding:'utf8'}).stdout.trim().split(/\\s+/).map(Number);
requireRetainedForeground({inputTTY:process.stdin.isTTY===true,outputTTY:process.stdout.isTTY===true,processGroup:group,foregroundGroup:foreground});
const events=[]; let elapsed=0;
const result=await orchestrateH1(${JSON.stringify(c)}, {
  async execute(step) { events.push(step.id); if(step.id==='diagnostic-tail') await retainDiagnosticTail({now:()=>elapsed,
    sleep:async ms=>{elapsed+=ms},readState:async()=>({...${JSON.stringify(connectedState(0))},elapsedMs:elapsed})}); },
  async start(step) {events.push('start:'+step.id);return {id:step.id}}, async join(handle){events.push('join:'+handle.id)}, async stop(){throw Error('unexpected stop')}
});
assert.equal(result.eligible,true); assert.equal(result.qualificationEligible,false); assert.equal(result.memoryQualified,false);
assert.equal(elapsed,45000); assert.ok(events.indexOf('join:instrumentation')<events.indexOf('credential-finish'));
process.stdout.write('diagnostic-pty-nonqualifier-complete\\n');`;
  const handle = launchPrivate({ id: "diagnostic-pty", command: process.execPath, args: ["--input-type=module", "-e", code],
    retained: true, timeoutMs: 5000 }, c, process.stdin.isTTY ? {} : { ptyInput: "ignore" });
  assert.deepEqual(await handle.done, { exitCode: 0, signal: null, timedOut: false, eligible: true });
  assert.match(readFileSync(handle.outputPath, "utf8"), /diagnostic-pty-nonqualifier-complete/);
});

test("cleanup failure publishes the safe private receipt before failing and cannot create success or retry", async t => {
  const dir = fixture(t); let calls = 0;
  const report = { type: "retained-client-cleanup", eligible: false, failedGroups: 6,
    failures: [{ stage: "login", kind: "timeout", status: null, networkCode: null }] };
  await assert.rejects(retainClientCleanupResult(dir, async () => { calls++; throw new Error("private-network-client"); },
    () => report), /retained-client-cleanup-failed/);
  const path = join(dir, "clients-cleanup.failed.json");
  assert.deepEqual(JSON.parse(readFileSync(path)), report); assert.equal(lstatSync(path).mode & 0o777, 0o600);
  assert.equal(existsSync(join(dir, "clients-cleanup.json")), false); assert.equal(calls, 1);
  assert.doesNotMatch(readFileSync(path, "utf8"), /private-network-client/);
  await assert.rejects(retainClientCleanupResult(dir, async () => { throw new Error("another"); }, () => ({ changed: true })));
  assert.deepEqual(JSON.parse(readFileSync(path)), report, "prior failure receipt is never overwritten");
});
