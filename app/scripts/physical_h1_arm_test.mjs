import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, lstatSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { armContext, h1Steps, LIMITS, launchPrivate, orchestrateH1, parseArgs, prepareArm, ptyCommand } from "./physical_h1_arm.mjs";
import { captureWorkloadScriptPreflight } from "./physical_workload_script.mjs";

const scripts = dirname(fileURLToPath(import.meta.url));
const root = resolve(scripts, "../../..");
const args = run => ["run", "--root", root, "--run-dir", run, "--serial", "3B161FDJG001KT", "--label", "h1-frozen",
  "--build-id", "ios-frozen", "--underlay", "wifi", "--config", "/private/credentials.yml", "--cdp-port", "19322",
  "--gomaxprocs", "10", "--max-workers", "4"];
const context = () => armContext(parseArgs(args("/private/arm-root"), 14), "frozen-native-owner");
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
  assert.equal(result.exitCode, 7, readFileSync(handle.outputPath, "utf8") + readFileSync(join(dir, "pty-proof.stderr"), "utf8"));
  assert.equal(result.eligible, false); assert.equal(result.timedOut, false);
  assert.match(readFileSync(handle.outputPath, "utf8"), /foreground-verified/);
});

test("process deadline cannot be rescued by a zero exit from its TERM handler", async t => {
  const dir = fixture(t); const c = { ...context(), directory: dir };
  const handle = launchPrivate({ id: "deadline-proof", command: process.execPath,
    args: ["-e", "process.on('SIGTERM',()=>process.exit(0));setInterval(()=>{},1000)"], timeoutMs: 500 }, c);
  const result = await handle.done;
  assert.equal(result.timedOut, true); assert.equal(result.eligible, false); assert.equal(result.exitCode, 0);
  assert.equal(handle.live, false); assert.equal(LIMITS.kill, 5000);
});
