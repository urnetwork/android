import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { EventEmitter } from "node:events";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import test, { after } from "node:test";
import { bindInstrumentationReady, checkCollectorSession, checkInstrumentationCommandSession, checkSessionRole as actualCheckSessionRole,
  formatSessionFailure, statusReadFailureMetadata, instrumentationProcessIdentity, parseArgs, requireRetainedForeground,
  runCollectorSession as actualRunCollectorSession, runInstrumentationSession as actualRunInstrumentationSession } from "./physical_collector_session.mjs";

const script = new URL("./physical_collector_session.mjs", import.meta.url).pathname;
const foreground = { inputTTY: true, outputTTY: true, processGroup: 100, foregroundGroup: 100 };
let hostPid;
const psRow = (pid, command) => ({ status: 0, stdout: `${pid} S Mon Sep 21 10:11:12 2026 ${command}\n` });
const adbCommand = "/sdk/platform-tools/adb -s fake-device shell am instrument -w -r -e class com.bringyour.network.acceptance.PhysicalLowbarSessionTest -e acceptanceBuildId fixture com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner";
const hostProcess = (pid) => {
  if (pid === process.pid) return psRow(pid, "/node /fixture/retained-supervisor.mjs");
  assert.equal(pid, hostPid);
  return psRow(pid, adbCommand);
};
const checkSessionRole = (options, dependencies, wait) => actualCheckSessionRole(options,
  { hostProcess, ...dependencies }, wait);
const runCollectorSession = (options, dependencies) => actualRunCollectorSession(options, { hostProcess, ...dependencies });
const runInstrumentationSession = (options, dependencies) => actualRunInstrumentationSession(options, {
  verifyNativeInputs: (path, buildId) => {
    assert.equal(path, join(options["artifact-dir"] ?? join(options.owner, ".."), "native-inputs.json"));
    assert.equal(buildId, "fixture");
    return { buildId, inputHash: "a".repeat(64), buildOwner: "fixture-owner" };
  }, ...dependencies,
});
const fakeCapture = `
  const fs = require('node:fs');
  const [output,label,stop] = process.argv.slice(1);
  const emit = row => fs.appendFileSync(output, JSON.stringify(row)+'\\n', {mode:0o600});
  emit({type:'environment',label});
  const tick = setInterval(() => {
    if(fs.existsSync(stop)){emit({type:'summary'});clearInterval(tick);return;}
    const now = Date.now();
    emit({type:'sample',startTimeUnixMs:now-2,endTimeUnixMs:now,eligibility:{eligible:true},telemetryErrors:[]});
  },20);
  setTimeout(()=>process.exit(99),5000).unref();
`;

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "collector-session-test-"));
  const options = { owner: join(directory, "owner.json"), stdout: join(directory, "stdout"), stderr: join(directory, "stderr"),
    captureArgs: ["--serial", "fake-device", "--label", "test-cell", "--duration-seconds", "60", "--interval-ms", "1000",
      "--output", join(directory, "telemetry"), "--stop-file", join(directory, "stop")] };
  const signals = new EventEmitter();
  const f = { directory, options, signals,
    async start(dependencies = {}) {
      f.result = runCollectorSession(options, { ...dependencies, foreground: () => foreground, signals,
        spawn: (_exe, args, settings) => {
          dependencies.beforeSpawn?.();
          assert.ok(args[0].endsWith("physical_lowbar_capture.mjs"));
          assert.deepEqual(args.slice(1), options.captureArgs);
          f.child = spawn(process.execPath, ["-e", fakeCapture, join(directory, "telemetry"), "test-cell", join(directory, "stop")], settings);
          return f.child;
        } });
      const deadline = Date.now() + 3000;
      while ((!existsSync(options.owner) || !existsSync(join(directory, "telemetry")) ||
          !readFileSync(join(directory, "telemetry"), "utf8").includes('"sample"')) && Date.now() < deadline) await sleep(10);
      assert.ok(existsSync(options.owner), "owner started");
    },
    stop() { writeFileSync(join(directory, "stop"), "", {mode:0o600}); },
    check(dependencies) { return checkCollectorSession({ owner: options.owner, timeoutMs: 2000 }, dependencies); },
  };
  t.after(async () => {
    if (f.child?.exitCode === null && f.child?.signalCode === null) f.child.kill("SIGTERM");
    if (f.result) await f.result.catch(() => {});
    rmSync(directory, { recursive: true, force: true });
  });
  return f;
}

function instrumentationFixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "retained-am-session-test-"));
  const options = { owner: join(directory, "owner.json"), stdout: join(directory, "stdout"), stderr: join(directory, "stderr"),
    serial: "fake-device", label: "test-cell", "build-id": "fixture", "native-inputs": join(directory, "native-inputs.json") };
  const signals = new EventEmitter();
  const f = { directory, options, signals,
    hostProcess(pid) {
      if (pid === process.pid) return psRow(pid, "/node /fixture/retained-supervisor.mjs");
      assert.equal(pid, f.child.pid);
      return psRow(pid, adbCommand.replace("com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner",
        `${options["hosted-transport-policy"] === undefined ? "" : `-e hostedTransportPolicy ${options["hosted-transport-policy"]} `}${options["qualification-scope"] === undefined ? "" : `-e qualificationScope ${options["qualification-scope"]} `}${options.component ?? "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner"}`));
    },
    async start(dependencies = {}) {
      f.result = runInstrumentationSession(options, { foreground: () => foreground, signals, hostProcess: f.hostProcess, ...dependencies,
        spawn: (command, args, settings) => {
          assert.equal(command, "adb");
          assert.deepEqual(args, ["-s", "fake-device", "shell", "am", "instrument", "-w", "-r", "-e", "class",
            "com.bringyour.network.acceptance.PhysicalLowbarSessionTest", "-e", "acceptanceBuildId", "fixture",
            ...(options["launch-id"] === undefined ? [] : ["-e", "acceptanceLaunchId", options["launch-id"]]),
            ...(options["io-loop-diagnostic"] === "1" ? ["-e", "ioLoopDiagnostic", "1"] : []),
            ...(options["hosted-transport-policy"] === undefined ? [] : ["-e", "hostedTransportPolicy", options["hosted-transport-policy"]]),
            ...(options["qualification-scope"] === undefined ? [] : ["-e", "qualificationScope", options["qualification-scope"]]),
            options.component ?? "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner"]);
          f.child = spawn(process.execPath, ["-e", fakeInstrumentation, join(directory, "stop")], settings);
          assert.equal(existsSync(options.owner), false, "no running receipt before live child verification");
          dependencies.afterSpawn?.(f.child);
          return f.child;
        },
      });
      await Promise.race([f.result.then(() => assert.fail("AM ended before startup")), (async () => {
        for (let i = 0; i < 200 && !existsSync(options.owner); i++) await sleep(10);
        assert.equal(existsSync(options.owner), true);
      })()]);
    },
    bind(adb = roleAdb([disconnectedStatus])) {
      return bindInstrumentationReady({ owner: options.owner, serial: "fake-device" }, { hostProcess: f.hostProcess, adb });
    },
    stop() { writeFileSync(join(directory, "stop"), "", { mode: 0o600 }); },
  };
  t.after(async () => {
    if (f.child?.exitCode === null && f.child?.signalCode === null) f.child.kill("SIGTERM");
    if (f.result) await f.result.catch(() => {});
    rmSync(directory, { recursive: true, force: true });
  });
  return f;
}

const disconnectedStatus = { type: "status", pid: 1234, commandId: "0", state: "ready", phase: "ready",
  buildId: "fixture", sessionId: "11111111-1111-4111-8111-111111111111", launchId: "abcdef0123456789abcdef0123456789",
  elapsedMs: 100, transportMode: "auto", connected: false, tunnelStarted: false, provideEnabled: false };
const connectedStatus = { ...disconnectedStatus, commandId: "h1-fresh", state: "complete", phase: "connect-h1",
  elapsedMs: 200, transportMode: "h1", connected: true, tunnelStarted: true };
const ownerDirectory = mkdtempSync(join(tmpdir(), "instrumentation-owner-test-"));
const instrumentationOwner = join(ownerDirectory, "instrumentation-owner.json");
const h1Role = { serial: "fake-device", "session-mode": "h1", "connect-command-id": "h1-fresh",
  "instrumentation-owner": instrumentationOwner };
const directRole = { serial: "fake-device", "session-mode": "direct", "instrumentation-owner": instrumentationOwner };

function roleAdb(statuses, events = [], alive = true) {
  let index = 0;
  return (args) => {
    assert.deepEqual(args.slice(0, 3), ["-s", "fake-device", "shell"]);
    const command = args.slice(3);
    events.push(command[0]);
    if (command[0] === "run-as") {
      assert.deepEqual(command, ["run-as", "com.bringyour.network", "cat", "files/acceptance/physical-status"],
        "run-as signal permission is not a liveness requirement");
      return { status: 0, stdout: JSON.stringify(statuses[Math.min(index++, statuses.length - 1)]) };
    }
    assert.deepEqual(command, ["pidof", "com.bringyour.network"], "a separate test-package process is not required");
    return { status: alive ? 0 : 1, stdout: alive ? "1234\n" : "" };
  };
}
const fakeInstrumentation = `
  const fs=require('node:fs');
  const stop=process.argv[1];
  const tick=setInterval(()=>{if(fs.existsSync(stop))clearInterval(tick);},10);
  setTimeout(()=>process.exit(99),30000).unref();
`;
const globalInstrumentation = runInstrumentationSession({ owner: instrumentationOwner,
  stdout: join(ownerDirectory, "stdout"), stderr: join(ownerDirectory, "stderr"),
  serial: "fake-device", label: "test-cell", "build-id": "fixture", "native-inputs": join(ownerDirectory, "native-inputs.json") }, {
  foreground: () => foreground, signals: new EventEmitter(), hostProcess,
  spawn: (command, args, settings) => {
    assert.equal(command, "adb");
    assert.equal(args.includes("com.bringyour.network.acceptance.PhysicalLowbarSessionTest"), true);
    const child = spawn(process.execPath, ["-e", fakeInstrumentation, join(ownerDirectory, "stop")], settings);
    hostPid = child.pid;
    return child;
  },
});
after(async () => {
  writeFileSync(join(ownerDirectory, "stop"), "");
  assert.equal(await globalInstrumentation, 0);
  const terminal = JSON.parse(readFileSync(`${instrumentationOwner}.terminal.json`, "utf8"));
  assert.equal(terminal.state, "complete");
  assert.equal(terminal.interrupted, false);
  rmSync(ownerDirectory, { recursive: true, force: true });
});
await Promise.race([globalInstrumentation.then(() => assert.fail("instrumentation exited before owner publication")),
  (async () => {
    for (let i = 0; i < 200 && !existsSync(instrumentationOwner); i++) await sleep(10);
    assert.equal(existsSync(instrumentationOwner), true);
  })()]);
bindInstrumentationReady({ owner: instrumentationOwner, serial: "fake-device" },
  { hostProcess, adb: roleAdb([disconnectedStatus]) });

test("run and check require explicit artifacts and bounded readiness", () => {
  assert.deepEqual(parseArgs(["check", "--owner", "owner.json"]).timeoutMs, 15000);
  for (const args of [[], ["run", "--owner", "x"], ["check", "--owner", ""],
    ["check", "--owner", "x", "--timeout-ms", "0"], ["check", "--owner", "x", "--timeout-ms", "30001"],
    ["check", "--owner", "x", "--", "node"]]) assert.throws(() => parseArgs(args));
});

test("role CLI requires explicit H1 command or Direct without a connect command", () => {
  assert.equal(parseArgs(["check-role", "--serial", "fake-device", "--session-mode", "direct",
    "--instrumentation-owner", instrumentationOwner]).mode, "check-role");
  assert.equal(parseArgs(["check-role", "--serial", "fake-device", "--session-mode", "h1",
    "--connect-command-id", "h1-fresh", "--instrumentation-owner", instrumentationOwner])["connect-command-id"], "h1-fresh");
  for (const args of [
    ["check-role", "--session-mode", "direct"],
    ["check-role", "--serial", "fake-device"],
    ["check-role", "--serial", "fake-device", "--session-mode", "h1"],
    ["check-role", "--serial", "fake-device", "--session-mode", "h3"],
    ["check-role", "--serial", "fake-device", "--session-mode", "direct", "--connect-command-id", "unexpected"],
    ["check-role", "--serial", "fake-device", "--session-mode", "h1", "--connect-command-id", "invalid|command"],
  ]) assert.throws(() => parseArgs(args));
});

test("instrumentation owner CLI names only the bounded physical session and explicit private artifacts", () => {
  assert.equal(parseArgs(["run-instrumentation", "--owner", "owner", "--stdout", "out", "--stderr", "err",
    "--serial", "fake-device", "--label", "cell", "--build-id", "fixture", "--native-inputs", "proof"]).mode, "run-instrumentation");
  assert.equal(parseArgs(["bind-instrumentation-ready", "--owner", "owner", "--serial", "fake-device"]).mode,
    "bind-instrumentation-ready");
  for (const args of [
    ["run-instrumentation", "--owner", "owner"],
    ["run-instrumentation", "--owner", "owner", "--stdout", "out", "--stderr", "err",
      "--serial", "fake-device", "--label", "cell", "--build-id", "fixture"],
    ["bind-instrumentation-ready", "--owner", "owner"],
    ["bind-instrumentation-ready", "--owner", "owner", "--serial", "fake-device", "--host-pid", "100"],
    ["run-instrumentation", "--owner", "owner", "--stdout", "out", "--stderr", "err",
      "--serial", "fake-device", "--label", "cell", "--build-id", "fixture", "--component", "other/Runner"],
  ]) assert.throws(() => parseArgs(args));
});

test("AM missing or untrusted artifact directory fails with a specific reason before any spawn", async (t) => {
  for (const kind of ["missing", "mode", "owner", "file", "symlink", "outside"]) {
    const f = instrumentationFixture(t);
    f.options["artifact-dir"] = f.directory;
    if (kind === "missing") rmSync(f.directory, { recursive: true });
    if (kind === "mode") chmodSync(f.directory, 0o755);
    if (kind === "file" || kind === "symlink") {
      const path = join(f.directory, "not-a-directory");
      if (kind === "file") writeFileSync(path, "fixture", { mode: 0o600 });
      else symlinkSync(f.directory, path);
      f.options["artifact-dir"] = path;
    }
    if (kind === "outside") f.options.stderr = join(f.directory, "nested", "stderr");
    const reason = kind === "missing" ? "artifact-directory-missing" : kind === "mode" ? "artifact-directory-mode-not-0700" :
      kind === "owner" ? "artifact-directory-owner-mismatch" : kind === "outside" ? "artifact-path-outside-directory" :
        "artifact-directory-untrusted-type";
    await assert.rejects(runInstrumentationSession(f.options, {
      directory: kind === "owner" ? { uid: process.getuid() + 1 } : {},
      foreground: () => assert.fail("directory rejection precedes foreground process inspection"),
      hostProcess: () => assert.fail("untrusted evidence must not inspect owners"),
      spawn: () => assert.fail("untrusted evidence must not launch instrumentation"),
    }), new RegExp(`^Error: ${reason}-no-spawn$`));
    assert.equal(existsSync(f.options.owner), false); assert.equal(existsSync(f.options.stdout), false);
  }
});

test("AM rechecks directory identity after host inspection and before stream creation/spawn", async (t) => {
  for (const kind of ["removed", "replaced", "mode"]) {
    const f = instrumentationFixture(t); const saved = `${f.directory}-saved`;
    t.after(() => rmSync(saved, { recursive: true, force: true }));
    let inspected = 0;
    await assert.rejects(runInstrumentationSession(f.options, { foreground: () => foreground,
      hostProcess: (pid) => {
        assert.equal(pid, process.pid); inspected++;
        if (kind === "mode") chmodSync(f.directory, 0o755);
        else {
          renameSync(f.directory, saved);
          if (kind === "replaced") mkdirSync(f.directory, { mode: 0o700 });
        }
        return hostProcess(pid);
      },
      spawn: () => assert.fail("a previous directory preflight must not authorize this spawn"),
    }), new RegExp(`${kind === "mode" ? "artifact-directory-mode-not-0700" :
      kind === "replaced" ? "artifact-directory-replaced" : "artifact-directory-missing"}-no-spawn`));
    assert.equal(inspected, 1);
    assert.equal(existsSync(f.options.owner), false); assert.equal(existsSync(f.options.stdout), false);
  }
});

test("real AM CLI reports missing private leaf rather than generic evidence-unavailable without PTY or adb", (t) => {
  const f = instrumentationFixture(t);
  rmSync(f.directory, { recursive: true });
  const result = spawnSync(process.execPath, [script, "run-instrumentation", "--artifact-dir", f.directory,
    "--owner", f.options.owner, "--stdout", f.options.stdout, "--stderr", f.options.stderr,
    "--serial", "unused-fake-device", "--label", "fixture", "--build-id", "fixture",
    "--native-inputs", f.options["native-inputs"]], { encoding: "utf8", timeout: 3000 });
  assert.equal(result.status, 2); assert.equal(result.stdout, "");
  assert.equal(result.stderr, "collector session failed: artifact-directory-missing-no-spawn\n");
  assert.equal(existsSync(f.directory), false);
});

test("AM supervisor publishes running only for its live child, binds ready once, and terminal only after join", async (t) => {
  const f = instrumentationFixture(t);
  await f.start();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.type, "instrumentation-session");
  assert.equal(owner.supervisorPid, process.pid);
  assert.equal(owner.adbPid, f.child.pid);
  assert.equal(owner.className, "com.bringyour.network.acceptance.PhysicalLowbarSessionTest");
  assert.equal(owner.targetPackage, "com.bringyour.network");
  assert.deepEqual(owner.foreground, foreground);
  assert.equal(owner.nativeInputHash, "a".repeat(64));
  assert.equal(owner.nativeBuildOwner, "fixture-owner");
  assert.equal(owner.targetPid, undefined, "target is not invented at host startup");
  assert.equal(existsSync(`${f.options.owner}.terminal.json`), false);
  const proof = f.bind();
  assert.equal(Object.values(proof).every((value) => value === true), true);
  const ready = JSON.parse(readFileSync(`${f.options.owner}.ready.json`, "utf8"));
  assert.equal(ready.targetPid, disconnectedStatus.pid);
  assert.equal(ready.ownerId, owner.ownerId);
  assert.deepEqual(JSON.parse(readFileSync(f.options.owner, "utf8")), owner, "ready must not rewrite the owner");
  const role = { ...h1Role, "instrumentation-owner": f.options.owner };
  assert.equal((await checkSessionRole(role, { hostProcess: f.hostProcess, adb: roleAdb([connectedStatus]) })).connected, true);
  f.stop();
  assert.equal(await f.result, 0);
  const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`, "utf8"));
  assert.equal(terminal.state, "complete");
  assert.equal(terminal.exitCode, 0);
  assert.equal(terminal.interrupted, false);
  assert.equal(f.child.exitCode, 0, "terminal is post-join, not a yielded executor receipt");
  for (const path of [f.options.owner, `${f.options.owner}.ready.json`, `${f.options.owner}.terminal.json`, f.options.stdout, f.options.stderr]) {
    assert.equal(statSync(path).mode & 0o777, 0o600);
  }
  await assert.rejects(checkSessionRole(role, { adb: () => assert.fail("terminal owner must not query a device") }), /already-finished/);
});

test("MjXnWF: missing or incomplete native source linkage cannot launch instrumentation", async (t) => {
  for (const state of ["missing", "incomplete", "public"]) {
    const f = instrumentationFixture(t);
    if (state !== "missing") writeFileSync(f.options["native-inputs"], JSON.stringify({ eligible: true }),
      { mode: state === "public" ? 0o644 : 0o600 });
    if (state === "public") chmodSync(f.options["native-inputs"], 0o644); // A private harness umask must not repair this fixture.
    await assert.rejects(actualRunInstrumentationSession(f.options, { foreground: () => foreground,
      hostProcess: f.hostProcess,
      spawn: () => assert.fail("incomplete source evidence must not spawn any device command"),
    }), new RegExp(`${state === "incomplete" ? "verified-native-input-proof-required" : "private-native-manifest-required"}-no-spawn`));
    assert.equal(existsSync(f.options.stdout), false); assert.equal(existsSync(f.options.owner), false);
  }
});

test("AM supervisor refuses one-shot/background launches, existing artifacts and an unverified child", async (t) => {
  const f = instrumentationFixture(t);
  for (const change of [{ inputTTY: false }, { outputTTY: false }, { foregroundGroup: 101 }]) {
    await assert.rejects(runInstrumentationSession(f.options, { foreground: () => ({ ...foreground, ...change }),
      spawn: () => assert.fail("nonretained AM must not launch") }), /retained-foreground-pty-required/);
  }
  writeFileSync(f.options.stdout, "prior", { mode: 0o600 });
  await assert.rejects(runInstrumentationSession(f.options, { foreground: () => foreground,
    spawn: () => assert.fail("prior artifacts must not be overwritten") }), /fresh-instrumentation-artifacts-required/);
  assert.equal(readFileSync(f.options.stdout, "utf8"), "prior");
  const g = instrumentationFixture(t);
  const child = new EventEmitter();
  child.pid = 9999;
  child.kill = () => { queueMicrotask(() => child.emit("close", null, "SIGTERM")); return true; };
  await assert.rejects(runInstrumentationSession(g.options, { foreground: () => foreground,
    hostProcess: (pid) => pid === process.pid ? hostProcess(pid) : { status: 1, stdout: "" },
    spawn: () => { queueMicrotask(() => child.emit("spawn")); return child; },
  }), /retained-instrumentation-owner-not-live/);
  assert.equal(existsSync(g.options.owner), false);
});

test("AM supervisor forwards interruption and unexpected child exit cannot leave a live-owner proof", async (t) => {
  for (const interrupted of [true, false]) {
    const f = instrumentationFixture(t);
    await f.start();
    f.bind();
    if (interrupted) f.signals.emit("SIGHUP");
    else f.child.kill("SIGTERM");
    assert.equal(await f.result, 2);
    const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`, "utf8"));
    assert.equal(terminal.state, "failed");
    assert.equal(terminal.interrupted, interrupted);
    await assert.rejects(checkSessionRole({ ...h1Role, "instrumentation-owner": f.options.owner },
      { adb: () => assert.fail("exited AM must reject before device reads") }), /already-finished/);
  }
});

test("ready binding rejects a non-ready target and cannot outlive either host owner", async (t) => {
  const f = instrumentationFixture(t);
  await f.start();
  assert.throws(() => f.bind(roleAdb([connectedStatus])), /ready-session-before-owner-binding-required/);
  assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
  let observed = false;
  const adb = roleAdb([disconnectedStatus]);
  assert.throws(() => bindInstrumentationReady({ owner: f.options.owner, serial: "fake-device" }, {
    hostProcess: (pid) => observed ? { status: 1, stdout: "" } : f.hostProcess(pid),
    adb: (args) => { const result = adb(args); if (args.at(-1) === "files/acceptance/physical-status") observed = true; return result; },
  }), /supervisor-not-live/);
  assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
});

test("host ps adapter recognizes the current supervisor without running a device command", async (t) => {
  const f = instrumentationFixture(t);
  await assert.rejects(runInstrumentationSession(f.options, { foreground: () => foreground,
    spawn: () => { throw new Error("host-ps-adapter-verified"); },
  }), /host-ps-adapter-verified/);
  assert.equal(existsSync(f.options.owner), false);
});

test("diagnostic checks accept diagnostic phases only for a live ready-bound target and intact owner", async (t) => {
  const f = instrumentationFixture(t);
  await f.start();
  const options = { owner: f.options.owner, serial: "fake-device" };
  assert.throws(() => checkInstrumentationCommandSession(options, { hostProcess: f.hostProcess,
    adb: () => assert.fail("unbound owner cannot query the target") }), /bound-ready/);
  f.bind();
  for (const state of ["running", "complete"]) {
    const status = { ...connectedStatus, commandId: "census-fresh", state, phase: "owner-census-preflight" };
    const report = checkInstrumentationCommandSession(options, { hostProcess: f.hostProcess, adb: roleAdb([status]) });
    assert.equal(report.phase, "owner-census-preflight");
    assert.equal(report.state, state);
    assert.equal(report.transportMode, "h1");
    assert.equal(report.sessionId, JSON.parse(readFileSync(f.options.owner, "utf8")).ownerId);
  }
  for (const delta of [{ elapsedMs: 99 }, { provideEnabled: undefined }]) {
    assert.throws(() => checkInstrumentationCommandSession(options, { hostProcess: f.hostProcess,
      adb: roleAdb([{ ...connectedStatus, ...delta }]) }), /target-process-changed|live-instrumentation-status-required/);
  }
  assert.throws(() => checkInstrumentationCommandSession(options, { hostProcess: f.hostProcess,
    adb: args => args[3] === "pidof" ? { status: 0, stdout: "5678\n" }
      : { status: 0, stdout: JSON.stringify({ ...connectedStatus, pid: 5678 }) } }), /target-process-changed/);
  for (const deadPid of [process.pid, f.child.pid]) {
    assert.throws(() => checkInstrumentationCommandSession(options, {
      hostProcess: pid => pid === deadPid ? { status: 1, stdout: "" } : f.hostProcess(pid),
      adb: () => assert.fail("dead host owner cannot query the target"),
    }), /not-live/);
  }
  f.stop();
  assert.equal(await f.result, 0);
  assert.throws(() => checkInstrumentationCommandSession(options, {
    adb: () => assert.fail("terminal owner cannot query the target"),
  }), /already-finished/);
});

test("diagnostic status reads cannot hide host-owner death or receipt replacement during the read", async (t) => {
  for (const changed of ["owner", "supervisor", "adb"]) {
    const f = instrumentationFixture(t);
    await f.start();
    f.bind();
    let observed = false;
    const adb = roleAdb([connectedStatus]);
    assert.throws(() => checkInstrumentationCommandSession({ owner: f.options.owner, serial: "fake-device" }, {
      hostProcess: pid => observed && (changed === "supervisor" ? pid === process.pid : changed === "adb" && pid === f.child.pid)
        ? { status: 1, stdout: "" } : f.hostProcess(pid),
      adb: args => {
        const result = adb(args);
        if (args.at(-1) === "files/acceptance/physical-status") {
          observed = true;
          if (changed === "owner") {
            const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
            writeFileSync(f.options.owner, JSON.stringify({ ...owner, label: "replaced" }));
          }
        }
        return result;
      },
    }), /receipt-replaced|not-live/);
  }
});

test("VaUc1l: live retained AM owner and target PID pass with no test-package PID and denied run-as signals", async () => {
  const calls = [];
  const adb = roleAdb([{ ...connectedStatus, liveExitCount: 5 }]);
  const result = await checkSessionRole(h1Role, { adb: (args) => {
    calls.push(args.slice(3));
    if (args.includes("kill") || args.at(-1) === "com.bringyour.network.test") return { status: 1, stdout: "" };
    return adb(args);
  } });
  assert.equal(result.connected, true);
  assert.equal(result.retainedOwnerLive, true);
  assert.equal(result.targetProcessMatchesStatus, true);
  assert.equal(result.targetStatusReadable, true);
  assert.deepEqual(calls, [["pidof", "com.bringyour.network"],
    ["run-as", "com.bringyour.network", "cat", "files/acceptance/physical-status"],
    ["pidof", "com.bringyour.network"]]);
});

test("UJrG1j: H1 command rejection retains its bounded wait stage", async () => {
  const stages = ["peer-traffic-counters", "client-disconnect", "provider-stop", "transport-policy",
    "us-country-pool", "public-vpn-connection", "us-provider-carrier-evidence", "same-network-provider",
    "connectable-peer", "peer-vpn-connection", "peer-carrier-evidence", "provider-traffic-counters"];
  for (const stage of stages) {
    await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([{ ...connectedStatus,
      state: "error", extra: { errorType: "PhysicalWaitTimeout", stage, failure: "wait-timeout",
        message: "private-error-message", cause: "private-cause", password: "private-password" },
    }]) }), error => {
      const lines = formatSessionFailure(error).trim().split("\n");
      assert.equal(lines[0], "collector session failed: h1-connect-command-rejected");
      assert.equal(lines.length, 2, "retain safe failure evidence in the already captured stderr");
      assert.deepEqual(JSON.parse(lines[1]), { type: "physical-command-failure", schema: 1,
        phase: "connect-h1", outcome: "wait-timeout", stage });
      assert.doesNotMatch(lines[1], /private|1234|fake-device|h1-fresh|PhysicalWaitTimeout/);
      return true;
    });
  }
});

test("H1 command failure evidence never copies unclassified errors or arbitrary stage text", async () => {
  for (const extra of [undefined, null, {}, { errorType: "AssertionError" },
    { stage: "private-password", failure: "wait-timeout" },
    { stage: "public-vpn-connection", failure: "private-error-message" },
    { stage: ["public-vpn-connection"], failure: "wait-timeout" },
    { stage: "public-vpn-connection", failure: ["wait-timeout"] },
    { stage: "constructor", failure: "wait-timeout" }]) {
    await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([{ ...connectedStatus, state: "error", extra }]) }), error => {
      const lines = formatSessionFailure(error).trim().split("\n");
      assert.equal(lines[0], "collector session failed: h1-connect-command-rejected");
      assert.equal(lines.length, 2);
      assert.deepEqual(JSON.parse(lines[1]), { type: "physical-command-failure", schema: 1,
        phase: "connect-h1", outcome: "unclassified", stage: null });
      return true;
    });
  }
});

test("H1 wait evidence cannot override successful role checks or a mismatched command phase", async () => {
  const extra = { stage: "public-vpn-connection", failure: "wait-timeout" };
  assert.equal((await checkSessionRole(h1Role, { adb: roleAdb([{ ...connectedStatus, extra }]) })).connected, true);
  await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([{ ...connectedStatus,
    phase: "private-phase", state: "error", extra }]) }), error => {
    assert.equal(formatSessionFailure(error), "collector session failed: h1-connect-command-rejected\n");
    return true;
  });
});

test("retained supervisor and adb child are each required before and after target status reads", async () => {
  for (const [deadPid, reason] of [[process.pid, /supervisor-not-live/], [hostPid, /adb-not-live/]]) {
    for (const exitAfterRead of [false, true]) {
      let read = false;
      const adb = roleAdb([connectedStatus]);
      await assert.rejects(checkSessionRole(h1Role, {
        hostProcess: (pid) => pid === deadPid && (!exitAfterRead || read) ? { status: 1, stdout: "" } : hostProcess(pid),
        adb: (args) => {
          assert.equal(exitAfterRead, true, "a dead initial host owner must reject before device reads");
          const result = adb(args);
          if (args.at(-1) === "files/acceptance/physical-status") read = true;
          return result;
        },
      }), reason);
    }
  }
});

test("host PID reuse, zombies, other adb devices or non-instrumentation commands never qualify", async () => {
  for (const [pid, reason] of [[process.pid, /supervisor-changed/], [hostPid, /adb-changed/]]) {
    await assert.rejects(checkSessionRole(h1Role, { adb: () => assert.fail("must fail before device reads"),
      hostProcess: (value) => value === pid ? { status: 0, stdout: hostProcess(value).stdout.replace("10:11:12", "10:11:13") } : hostProcess(value),
    }), reason);
  }
  const valid = hostProcess(hostPid);
  assert.match(instrumentationProcessIdentity(valid, "fake-device", hostPid), /^[a-f0-9]{64}$/);
  for (const result of [{ status: 1, stdout: "private-process-details" }, { ...valid, signal: "SIGTERM" },
    { status: 0, stdout: valid.stdout.replace(" S ", " Z ") },
    { status: 0, stdout: valid.stdout.replace("fake-device", "other-device") },
    { status: 0, stdout: valid.stdout.replace("am instrument", "am force-stop") },
    { status: 0, stdout: valid.stdout.replace("PhysicalLowbarSessionTest", "DifferentTest") }]) {
    assert.throws(() => instrumentationProcessIdentity(result, "fake-device", hostPid), /retained-/);
  }
});

test("target must match ready binding and be present before and after a readable status", async () => {
  const realAdb = roleAdb([connectedStatus]);
  for (const output of ["", "0", "pid=1234", "5678\n"]) {
    await assert.rejects(checkSessionRole(h1Role, { adb: (args) => args[3] === "pidof"
      ? { status: output ? 0 : 1, stdout: output } : realAdb(args) }), /target-app-process-/);
  }
  let probes = 0;
  await assert.rejects(checkSessionRole(h1Role, { adb: (args) => args[3] === "pidof"
    ? { status: 0, stdout: ++probes === 1 ? "1234\n" : "5678\n" } : realAdb(args) }), /target-app-process-changed/);
  await assert.rejects(checkSessionRole(h1Role, { adb: (args) => args[3] === "pidof"
    ? { status: 0, stdout: "5678\n" }
    : { status: 0, stdout: JSON.stringify({ ...connectedStatus, pid: 5678 }) } }), /instrumentation-target-process-changed/);
  // Extra same-name processes are irrelevant when the exact ready/status PID
  // remains present; a separate test-package process is never required.
  assert.equal((await checkSessionRole(h1Role, { adb: (args) => args[3] === "pidof"
    ? { status: 0, stdout: "1234 9999\n" } : realAdb(args) })).targetProcessMatchesStatus, true);
  await assert.rejects(checkSessionRole(h1Role, { adb: (args) => args.at(-1) === "files/acceptance/physical-status"
    ? { status: 1, stdout: "private-denial" } : realAdb(args) }), /live-instrumentation-status-required/);
});

test("private running and ready receipts are exclusive and cannot be replaced or marked terminal", async (t) => {
  const f = fixture(t);
  const path = join(f.directory, "instrumentation.json");
  const owner = JSON.parse(readFileSync(instrumentationOwner, "utf8"));
  const ready = JSON.parse(readFileSync(`${instrumentationOwner}.ready.json`, "utf8"));
  const reset = () => {
    writeFileSync(path, JSON.stringify(owner), { mode: 0o600 });
    writeFileSync(`${path}.ready.json`, JSON.stringify(ready), { mode: 0o600 });
  };
  const options = { ...h1Role, "instrumentation-owner": path };
  reset();
  assert.equal(statSync(instrumentationOwner).mode & 0o777, 0o600);
  assert.equal(statSync(`${instrumentationOwner}.ready.json`).mode & 0o777, 0o600);
  assert.throws(() => bindInstrumentationReady({ owner: path, serial: "fake-device" },
    { hostProcess, adb: () => assert.fail("must not rebind a ready owner") }), /already-bound/);
  let now = 0;
  await assert.rejects(checkSessionRole(options, { now: () => now, adb: roleAdb([disconnectedStatus]),
    sleep: async () => { now += 250; writeFileSync(path, JSON.stringify({ ...owner, ownerId: "00000000-0000-0000-0000-000000000000" })); },
  }), /bound-ready-instrumentation-required|receipt-replaced/);
  reset();
  writeFileSync(`${path}.terminal.json`, "{}", { mode: 0o600 });
  await assert.rejects(checkSessionRole(options, { adb: () => assert.fail("finished owner must reject before device reads") }), /already-finished/);
});

test("DQlHyJ diagnostic: missing-path publication gap is distinct from ADB failure and never retried into success", async (t) => {
  const f = fixture(t);
  const statusFile = join(f.directory, "physical-status");
  const pending = `${statusFile}.tmp`;
  writeFileSync(statusFile, JSON.stringify(disconnectedStatus));
  writeFileSync(pending, JSON.stringify(connectedStatus));
  // Deterministically pause a delete-then-rename publisher in its gap. This
  // proves how the read classifies that condition, not what the lost arm saw.
  rmSync(statusFile);
  let reads = 0;
  let pidReads = 0;
  await assert.rejects(checkSessionRole(h1Role, { adb: args => {
    if (args[3] === "pidof") { pidReads++; return { status: 0, stdout: "1234\n" }; }
    reads++;
    assert.equal(existsSync(statusFile), false);
    renameSync(pending, statusFile); // Recovery cannot make the failed read valid.
    return { status: 1, stdout: "", stderr: "cat: files/acceptance/physical-status: No such file or directory\n" };
  }, sleep: () => assert.fail("do not retry a missing status") }), error => {
    const metadata = statusReadFailureMetadata(error);
    assert.equal(metadata.outcome, "status-path-missing");
    assert.equal(metadata.targetPresentBefore, true);
    assert.equal(metadata.targetPresentAfter, true);
    assert.equal(metadata.targetSetUnchanged, true);
    assert.equal(metadata.transportError, null);
    assert.equal(metadata.exitCode, 1);
    assert.equal(metadata.stdoutBytes, 0);
    const lines = formatSessionFailure(error).trim().split("\n");
    assert.equal(lines[0], "collector session failed: live-instrumentation-status-required");
    assert.deepEqual(JSON.parse(lines[1]), metadata);
    assert.doesNotMatch(lines[1], /1234|fake-device|files\/acceptance|No such file|physical-status"/);
    return true;
  });
  assert.equal(reads, 1);
  assert.equal(pidReads, 2);
  assert.equal(existsSync(statusFile), true);
});

test("failed-read metadata distinguishes bounded transport, JSON and schema outcomes without raw diagnostics", async () => {
  const cases = [
    [{ status: null, error: { code: "ETIMEDOUT", message: "private-raw" }, stderr: "private-raw" }, "adb-transport-error", "timeout"],
    [{ status: null, error: { code: "ENOBUFS" } }, "adb-transport-error", "output-limit"],
    [{ status: null, error: { code: "ENOENT" } }, "adb-transport-error", "spawn-unavailable"],
    [{ status: null, error: { code: "EPIPE" } }, "adb-transport-error", "pipe-closed"],
    [{ status: null, error: { code: "private-raw" } }, "adb-transport-error", "other"],
    [{ status: null, error: { code: "constructor" } }, "adb-transport-error", "other"],
    [{ status: null, signal: "SIGTERM" }, "adb-signalled", null],
    [{ status: 1, stdout: "private-raw", stderr: "device private-raw unavailable" }, "adb-nonzero-exit", null],
    [{ status: 1, stderr: "cat: files/acceptance/physical-status: Permission denied\n" }, "status-read-denied", null],
    [{ status: 0, stdout: "\n" }, "empty-status-output", null],
    [{ status: 0, stdout: "{private-raw" }, "malformed-status-json", null],
    ...[[{ type: "sample" }, "invalid-status-type"], [{ pid: 0 }, "invalid-status-pid"],
      [{ commandId: "private raw" }, "invalid-status-command-id"], [{ elapsedMs: -1 }, "invalid-status-elapsed"],
      [{ elapsedMs: 99 }, "status-elapsed-regressed"],
      [{ state: "private-raw" }, "invalid-status-state"], [{ phase: "finish" }, "finished-status"]]
      .map(([delta, outcome]) => [{ status: 0, stdout: JSON.stringify({ ...connectedStatus, ...delta }) }, outcome, null]),
  ];
  for (const [result, outcome, transportError] of cases) {
    let reads = 0;
    await assert.rejects(checkSessionRole(h1Role, { adb: args => {
      if (args[3] === "pidof") return { status: 0, stdout: "1234\n" };
      reads++;
      return result;
    }, sleep: () => assert.fail("failed reads are not retried") }), error => {
      const metadata = statusReadFailureMetadata(error);
      assert.equal(metadata.outcome, outcome);
      assert.equal(metadata.transportError, transportError);
      assert.equal(metadata.classification, "INVALID_SETUP");
      assert.equal(metadata.targetPresentAfter, true);
      assert.doesNotMatch(formatSessionFailure(error), /private-raw|private raw|fake-device|1234|files\/acceptance/);
      return true;
    });
    assert.equal(reads, 1);
  }
  assert.equal(formatSessionFailure(new Error("private-raw")), "collector session failed: evidence-unavailable\n");
});

test("failed-read metadata never calls a disappeared or unobservable target continuously live", async () => {
  for (const after of [{ status: 0, stdout: "5678\n" }, { status: 1, stdout: "" }]) {
    let probes = 0;
    await assert.rejects(checkSessionRole(h1Role, { adb: args => args[3] === "pidof"
      ? ++probes === 1 ? { status: 0, stdout: "1234\n" } : after
      : { status: 0, stdout: "" } }), error => {
      const metadata = statusReadFailureMetadata(error);
      assert.equal(metadata.targetPresentBefore, true);
      assert.equal(metadata.targetPresentAfter, after.status === 0 ? false : null);
      assert.equal(metadata.targetSetUnchanged, after.status === 0 ? false : null);
      return true;
    });
  }
});

test("H1 prerequisite waits for the exact complete connect command, never starts or mutates anything", async () => {
  const events = [];
  let now = 0;
  const result = await checkSessionRole(h1Role, { now: () => now,
    sleep: async (ms) => { now += ms; events.push("wait"); },
    adb: roleAdb([disconnectedStatus, { ...connectedStatus, state: "running", elapsedMs: 150 },
      { ...connectedStatus, extra: { credential: "must-not-escape" } }], events) });
  assert.deepEqual(events, ["pidof", "run-as", "pidof", "wait", "pidof", "run-as", "pidof", "wait", "pidof", "run-as", "pidof"]);
  assert.equal(result.commandId, "h1-fresh");
  assert.equal(result.connected, true);
  assert.equal(result.tunnelStarted, true);
  assert.equal(result.transportMode, "h1");
  assert.equal(JSON.stringify(result).includes("must-not-escape"), false);
});

test("H1 collector before connect fails before spawning or creating artifacts, not at telemetry readiness", async (t) => {
  const f = fixture(t);
  Object.assign(f.options, h1Role);
  f.options.captureArgs.push("--require-unmetered-vpn");
  for (const status of [disconnectedStatus, { ...connectedStatus, state: "running" }]) {
    await assert.rejects(runCollectorSession(f.options, { foreground: () => foreground,
      adb: roleAdb([status]), sleep: () => assert.fail("run must not wait for connect"),
      spawn: () => assert.fail("collector must not start before connect") }), /h1-connect-must-complete-before-collector/);
  }
  for (const path of [f.options.owner, f.options.stdout, f.options.stderr, join(f.directory, "telemetry")]) {
    assert.equal(existsSync(path), false);
  }
});

test("H1 collector rereads current connected status after the prerequisite before spawning", async (t) => {
  const f = fixture(t);
  Object.assign(f.options, h1Role);
  f.options.captureArgs.push("--require-unmetered-vpn");
  const events = [];
  const adb = roleAdb([connectedStatus], events);
  await checkSessionRole(h1Role, { adb });
  await f.start({ adb, beforeSpawn: () => {
    assert.deepEqual(events, ["pidof", "run-as", "pidof", "pidof", "run-as", "pidof"]);
    events.push("spawn");
  } });
  assert.equal(await f.check(), f.child.pid);
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.sessionRole.sessionMode, "h1");
  assert.equal(owner.sessionRole.commandId, "h1-fresh");
  assert.equal(owner.sessionRole.connected, true);
  f.stop();
  assert.equal(await f.result, 0);
});

test("a previously successful H1 prerequisite cannot authorize a subsequently disconnected collector", async (t) => {
  const f = fixture(t);
  Object.assign(f.options, h1Role);
  f.options.captureArgs.push("--require-unmetered-vpn");
  await checkSessionRole(h1Role, { adb: roleAdb([connectedStatus]) });
  await assert.rejects(runCollectorSession(f.options, { foreground: () => foreground,
    adb: roleAdb([{ ...connectedStatus, connected: false, tunnelStarted: false }]),
    spawn: () => assert.fail("must not reuse the earlier connected proof") }), /completed-h1-tunnel-required/);
  assert.equal(existsSync(f.options.owner), false);
  assert.equal(existsSync(f.options.stdout), false);
});

test("Direct collector starts disconnected with no H1 command and retains no-VPN eligibility", async (t) => {
  const f = fixture(t);
  Object.assign(f.options, directRole);
  f.options.captureArgs.push("--require-no-vpn");
  await f.start({ adb: roleAdb([disconnectedStatus]) });
  assert.equal(await f.check(), f.child.pid);
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.sessionRole.sessionMode, "direct");
  assert.equal(owner.sessionRole.connected, false);
  await assert.rejects(checkSessionRole(directRole,
    { adb: roleAdb([connectedStatus]) }), /disconnected-direct-session-required/);
  f.stop();
  assert.equal(await f.result, 0);
});

test("role guard rejects opposite collector policy before device access or child launch", async (t) => {
  const f = fixture(t);
  for (const [role, flag] of [[h1Role, "--require-no-vpn"],
    [directRole, "--require-vpn"]]) {
    await assert.rejects(runCollectorSession({ ...f.options, ...role, captureArgs: [...f.options.captureArgs, flag] },
      { foreground: () => foreground, adb: () => assert.fail("policy mismatch precedes adb"),
        spawn: () => assert.fail("policy mismatch precedes spawn") }), /collector-vpn-requirement-does-not-match-session-role/);
  }
});

test("ready PID bindings must stay private before any target status read", async (t) => {
  const f = fixture(t);
  const path = join(f.directory, "instrumentation.json");
  writeFileSync(path, readFileSync(instrumentationOwner), { mode: 0o600 });
  writeFileSync(`${path}.ready.json`, readFileSync(`${instrumentationOwner}.ready.json`), { mode: 0o600 });
  chmodSync(`${path}.ready.json`, 0o644);
  await assert.rejects(checkSessionRole({ ...h1Role, "instrumentation-owner": path }, {
    adb: () => assert.fail("nonprivate ready binding must reject before device reads"),
  }), /private-instrumentation-ready-receipt-required/);
});

test("role prerequisite rejects bad completion, failure, process change, stale clock and dead owner", async () => {
  for (const change of [{ state: "error" }, { phase: "connect-h3" }, { transportMode: "h3" },
    { connected: false }, { tunnelStarted: false }, { provideEnabled: true }]) {
    await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([{ ...connectedStatus, ...change }]) }),
      /h1-connect-command-rejected|completed-h1-tunnel-required/);
  }
  for (const [status, reason] of [[{ ...connectedStatus, pid: 5678 }, /target-app-process-changed/],
    [{ ...connectedStatus, commandId: "other-command" }, /concurrent-command-observed/],
    [{ ...connectedStatus, elapsedMs: 99 }, /live-instrumentation-status-required/]]) {
    await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([disconnectedStatus, status]), sleep: async () => {} }), reason);
  }
  await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([connectedStatus], [], false) }), /process-not-live/);
  for (const result of [{ status: 1, stdout: "private error" }, { status: 0, stdout: "{partial" },
    { status: 0, stdout: JSON.stringify({ ...connectedStatus, elapsedMs: -0.5 }) }]) {
    const adb = roleAdb([connectedStatus]);
    await assert.rejects(checkSessionRole(h1Role, {
      adb: (args) => args.at(-1) === "files/acceptance/physical-status" ? result : adb(args),
    }), /live-instrumentation-status-required/);
  }
});

test("H1 prerequisite deadline remains bounded and Direct does not wait for VPN", async () => {
  let now = 0;
  await assert.rejects(checkSessionRole(h1Role, { adb: roleAdb([disconnectedStatus]), now: () => now,
    sleep: async () => { now += 150_000; } }), /h1-connect-prerequisite-deadline/);
  const result = await checkSessionRole(directRole,
    { adb: roleAdb([disconnectedStatus]), sleep: () => assert.fail("Direct must not await a VPN") });
  assert.equal(result.connected, false);
});

test("foreground guard rejects one-shot pipes, redirected owners and background TTY groups", () => {
  requireRetainedForeground(foreground);
  for (const change of [{inputTTY:false},{outputTTY:false},{processGroup:0},{foregroundGroup:101}]) {
    assert.throws(() => requireRetainedForeground({...foreground,...change}), /retained-foreground-pty-required/);
  }
});

test("LqIam9: a one-shot executor cannot start the collector or manufacture a ready prefix", (t) => {
  const f = fixture(t);
  const result = spawnSync(process.execPath, [script, "run", "--owner", f.options.owner,
    "--stdout", f.options.stdout, "--stderr", f.options.stderr, "--", ...f.options.captureArgs],
    {encoding:"utf8",timeout:5000});
  assert.equal(result.status, 2);
  assert.equal(result.stdout, "");
  assert.match(result.stderr, /retained-foreground-pty-required/);
  assert.equal(existsSync(f.options.owner), false);
  assert.equal(existsSync(join(f.directory,"telemetry")), false);
  assert.equal(existsSync(f.options.stdout), false);
});

test("retained owner spans independent executor checks and joins the actual collector", async (t) => {
  const f = fixture(t); await f.start();
  let completed = false;
  f.result.then(() => { completed = true; });
  for (let i=0;i<2;i++) {
    const checked = spawnSync(process.execPath, [script,"check","--owner",f.options.owner,"--timeout-ms","2000"],
      {encoding:"utf8",timeout:3000});
    assert.equal(checked.status, 0, checked.stderr);
    assert.equal(checked.stdout.trim(), String(f.child.pid));
    assert.equal(completed, false, "an executor check must not end the retained owner");
  }
  for (const path of [f.options.owner,f.options.stdout,f.options.stderr]) assert.equal(statSync(path).mode & 0o777, 0o600);
  f.stop();
  assert.equal(await f.result, 0);
  const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`,"utf8"));
  assert.equal(terminal.state,"complete");
  assert.equal(terminal.exitCode,0);
  assert.equal(terminal.interrupted,false);
  await assert.rejects(f.check(), /already-finished/);
});

test("fresh eligible samples cannot cover a dead launcher or collector", async (t) => {
  const f = fixture(t); await f.start();
  for (const dead of [process.pid,f.child.pid]) {
    await assert.rejects(f.check({isLive:pid=>pid!==dead,sleep:()=>assert.fail("dead owner must not be retried")}), /owner-not-live/);
  }
  f.child.kill("SIGTERM");
  assert.equal(await f.result,2);
  await assert.rejects(f.check(), /already-finished/);
});

test("terminal interruption is forwarded and preserved, never a normal completion", async (t) => {
  const f = fixture(t); await f.start();
  f.signals.emit("SIGHUP");
  assert.equal(await f.result,2);
  const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`,"utf8"));
  assert.equal(terminal.interrupted,true);
  assert.equal(terminal.state,"failed");
  assert.equal(terminal.signal,"SIGHUP");
});

test("readiness never replaces the owner or relaxes eligibility while polling", async (t) => {
  const f = fixture(t); await f.start();
  // Pin a wall clock far enough ahead that the otherwise valid prefix is stale.
  let now=0;
  await assert.rejects(f.check({wallNow:()=>Date.now()+10000,now:()=>now,
    sleep:async()=>{now+=2000;}}), /ready-deadline/);
  const owner = JSON.parse(readFileSync(f.options.owner,"utf8"));
  now=0;
  await assert.rejects(f.check({wallNow:()=>Date.now()+10000,now:()=>now,
    sleep:async()=>{writeFileSync(f.options.owner,JSON.stringify({...owner,ownerId:"00000000-0000-0000-0000-000000000000"}));now+=100;}}), /owner-replaced/);
});

test("existing artifacts are rejected without overwrite or a collector launch", async (t) => {
  const f=fixture(t);
  writeFileSync(f.options.stdout,"prior evidence");
  await assert.rejects(runCollectorSession(f.options,{foreground:()=>foreground,spawn:()=>assert.fail("must not start")}), /fresh-collector-artifacts-required/);
  assert.equal(readFileSync(f.options.stdout,"utf8"),"prior evidence");
});

// These fixtures use the real collector publisher and its real child. Only the
// host command view is supplied, because the test itself calls the run API.
function boundCollectorFixture(t) {
  const f = fixture(t);
  f.options["launch-id"] = "0123456789abcdef0123456789abcdef";
  f.hostProcess = (pid) => {
    assert.equal(pid, process.pid);
    return psRow(pid, `${process.execPath} ${script} run --owner ${f.options.owner} --launch-id ${f.options["launch-id"]}`);
  };
  f.boundCheck = (dependencies = {}) => checkCollectorSession({ owner: f.options.owner, timeoutMs: 15_000,
    "launch-id": f.options["launch-id"], launcherPid: process.pid }, { hostProcess: f.hostProcess, ...dependencies });
  f.publish = () => f.start({ hostProcess: f.hostProcess });
  return f;
}

test("bound collector readiness starts before publication and accepts only its launched owner", async (t) => {
  const f = boundCollectorFixture(t);
  let releasePublication; let reachedPublicationBarrier;
  const publication = new Promise(resolve => { releasePublication = resolve; });
  const held = new Promise(resolve => { reachedPublicationBarrier = resolve; });
  let waits = 0;
  const checking = f.boundCheck({ sleep: async () => {
    assert.equal(++waits, 1, "first check must be waiting for owner publication, not telemetry");
    assert.equal(existsSync(f.options.owner), false);
    reachedPublicationBarrier();
    await publication;
    await f.publish();
  } });
  try {
    await Promise.race([held, checking.then(() => assert.fail("readiness accepted before publication"))]);
    assert.equal(existsSync(f.options.owner), false, "publication remains behind the explicit barrier");
    releasePublication();
    assert.equal(await checking, f.child.pid);
    const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
    assert.equal(owner.ownerPid, process.pid);
    assert.equal(owner.launchId, f.options["launch-id"]);
    assert.match(owner.launcherIdentity, /^[a-f0-9]{64}$/);
    assert.equal(waits, 1);
    f.stop(); assert.equal(await f.result, 0);
  } finally { releasePublication(); }
});

test("collector startup binding is explicit and legacy missing owners never poll", async (t) => {
  const f = boundCollectorFixture(t);
  const options = parseArgs(["check", "--owner", f.options.owner, "--launch-id", f.options["launch-id"],
    "--launcher-pid", String(process.pid)]);
  assert.equal(options.launcherPid, process.pid); assert.equal(options.timeoutMs, 15_000);
  for (const extra of [["--launch-id", f.options["launch-id"]], ["--launcher-pid", String(process.pid)],
    ["--launch-id", "bad", "--launcher-pid", String(process.pid)],
    ["--launch-id", f.options["launch-id"], "--launcher-pid", "0"],
    ["--launch-id", f.options["launch-id"], "--launcher-pid", "9007199254740992"]]) {
    assert.throws(() => parseArgs(["check", "--owner", f.options.owner, ...extra]), /explicit-collector-launch-binding-required/);
  }
  await assert.rejects(checkCollectorSession({ owner: f.options.owner, timeoutMs: 15_000 }, {
    sleep: () => assert.fail("an unbound future receipt is not startup authority"),
  }), /collector-session-owner-required/);
});

test("bound collector publication refuses dead terminal replaced or wrong-command launchers", async (t) => {
  for (const kind of ["dead", "terminal", "replaced", "wrong-script", "wrong-mode", "wrong-launch"]) {
    const f = boundCollectorFixture(t);
    let waiting = false; let waits = 0;
    const hostProcess = pid => {
      const row = f.hostProcess(pid);
      if (!waiting) return row;
      if (kind === "replaced") row.stdout = row.stdout.replace("10:11:12", "10:11:13");
      if (kind === "wrong-script") row.stdout = row.stdout.replace(script, "/fixture/other.mjs");
      if (kind === "wrong-mode") row.stdout = row.stdout.replace(" run ", " check ");
      if (kind === "wrong-launch") row.stdout = row.stdout.replace(f.options["launch-id"], "f".repeat(32));
      return row;
    };
    const reason = kind === "dead" ? "launcher-not-live" : kind === "terminal" ? "already-finished" :
      kind === "replaced" ? "launcher-replaced" : "launcher-command-mismatch";
    await assert.rejects(f.boundCheck({ hostProcess, isLive: () => !(waiting && kind === "dead"), sleep: async () => {
      assert.equal(++waits, 1, `${kind}: an invalid launcher must not be retried`);
      waiting = true;
      if (kind === "terminal") writeFileSync(`${f.options.owner}.terminal.json`, "{}", { mode: 0o600 });
    } }), new RegExp(reason), kind);
    assert.equal(waits, 1); assert.equal(existsSync(f.options.owner), false);
  }
});

test("bound collector receipt cannot substitute another launch or child owner", async (t) => {
  for (const kind of ["pid", "launch", "identity", "missing-binding", "dead-child", "malformed"]) {
    const f = boundCollectorFixture(t);
    let published = false;
    await assert.rejects(f.boundCheck({ isLive: pid => !(published && kind === "dead-child" && pid === f.child.pid),
      sleep: async () => {
        assert.equal(published, false, `${kind}: substituted owner must not be retried`);
        await f.publish(); published = true;
        const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
        if (kind === "pid") owner.ownerPid += 1;
        if (kind === "launch") owner.launchId = "f".repeat(32);
        if (kind === "identity") owner.launcherIdentity = "f".repeat(64);
        if (kind === "missing-binding") { delete owner.launchId; delete owner.launcherIdentity; }
        if (kind === "malformed") owner.state = "complete";
        writeFileSync(f.options.owner, JSON.stringify(owner));
      },
    }), new RegExp(kind === "dead-child" ? "owner-not-live" : kind === "malformed" ? "invalid-collector-session-owner" :
      "launch-binding-mismatch"), kind);
    assert.equal(published, true);
    f.stop(); assert.equal(await f.result, 0);
  }
});

test("bound collector rechecks launch and receipt identity after eligible telemetry", async (t) => {
  for (const kind of ["dead", "replaced", "terminal", "owner-id", "launch-id", "identity"]) {
    const f = boundCollectorFixture(t); await f.publish();
    let readTelemetry = false;
    await assert.rejects(f.boundCheck({
      isLive: pid => !(readTelemetry && kind === "dead" && pid === process.pid),
      hostProcess: pid => {
        const row = f.hostProcess(pid);
        if (readTelemetry && kind === "replaced") row.stdout = row.stdout.replace("10:11:12", "10:11:13");
        return row;
      },
      wallNow: () => {
        assert.equal(readTelemetry, false); readTelemetry = true;
        const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
        if (kind === "terminal") writeFileSync(`${f.options.owner}.terminal.json`, "{}", { mode: 0o600 });
        if (kind === "owner-id") owner.ownerId = "00000000-0000-0000-0000-000000000000";
        if (kind === "launch-id") owner.launchId = "f".repeat(32);
        if (kind === "identity") owner.launcherIdentity = "f".repeat(64);
        writeFileSync(f.options.owner, JSON.stringify(owner));
        return Date.now();
      },
      sleep: () => assert.fail(`${kind}: valid telemetry must not hide changed ownership`),
    }), new RegExp(kind === "dead" ? "launcher-not-live" : kind === "replaced" ? "launcher-replaced" :
      kind === "terminal" ? "already-finished" : "owner-replaced"), kind);
    assert.equal(readTelemetry, true);
    if (kind === "terminal") rmSync(`${f.options.owner}.terminal.json`);
    f.stop(); assert.equal(await f.result, 0);
  }
});

test("bound collector publication and telemetry share the original total deadline", async (t) => {
  for (const kind of ["never-published", "late-publication", "stale-prefix"]) {
    const f = boundCollectorFixture(t); let now = 0; const waits = [];
    await assert.rejects(f.boundCheck({ now: () => now, wallNow: () => Date.now() + 10_000,
      sleep: async delayMs => {
        waits.push(delayMs);
        if (waits.length === 1) {
          now = kind === "stale-prefix" ? 14_999 : 15_000;
          if (kind !== "never-published") await f.publish();
        } else { assert.equal(kind, "stale-prefix"); assert.equal(waits.length, 2); now += delayMs; }
      },
    }), /collector-session-ready-deadline/, kind);
    assert.equal(now, 15_000);
    assert.deepEqual(waits, kind === "stale-prefix" ? [100, 1] : [100]);
    if (f.result) { f.stop(); assert.equal(await f.result, 0); }
  }
});


// The actual child has spawned before its spawn acknowledgment is delivered
// to the supervisor. Target readiness therefore cannot imply host publication.
function boundInstrumentationFixture(t) {
  const f = instrumentationFixture(t);
  f.options["launch-id"] = "abcdef0123456789abcdef0123456789";
  const originalHostProcess = f.hostProcess;
  f.hostProcess = pid => pid === process.pid
    ? psRow(pid, `${process.execPath} ${script} run-instrumentation --owner ${f.options.owner} --launch-id ${f.options["launch-id"]}`)
    : originalHostProcess(pid);
  f.boundOptions = { owner: f.options.owner, serial: "fake-device", "launch-id": f.options["launch-id"],
    launcherPid: process.pid, timeoutMs: 170_000 };
  f.boundBind = (dependencies = {}, changes = {}) => bindInstrumentationReady({ ...f.boundOptions, ...changes },
    { hostProcess: f.hostProcess, adb: roleAdb([disconnectedStatus]), ...dependencies });
  return f;
}

test("bound instrumentation ready waits for its owner after the actual child has spawned", async (t) => {
  const f = boundInstrumentationFixture(t);
  let acknowledgeSpawn; let childSpawned; let checkerWaiting; let continueChecking;
  const spawned = new Promise(resolve => { childSpawned = resolve; });
  const held = new Promise(resolve => { checkerWaiting = resolve; });
  const released = new Promise(resolve => { continueChecking = resolve; });
  const starting = f.start({ afterSpawn: child => {
    const once = child.once.bind(child);
    once("spawn", childSpawned);
    child.once = (event, listener) => {
      if (event === "spawn") { acknowledgeSpawn = () => listener(); return child; }
      return once(event, listener);
    };
  } });
  try {
    await Promise.race([spawned, starting.then(() => assert.fail("owner cannot publish through the held spawn acknowledgment"))]);
    assert.equal(existsSync(f.options.owner), false);
    const target = roleAdb([disconnectedStatus])(["-s", "fake-device", "shell", "run-as",
      "com.bringyour.network", "cat", "files/acceptance/physical-status"]);
    assert.equal(JSON.parse(target.stdout).state, "ready", "target readiness is independent of host publication");
    let waits = 0;
    const binding = f.boundBind({ sleep: async () => {
      assert.equal(++waits, 1); assert.equal(existsSync(f.options.owner), false);
      checkerWaiting(); await released;
    } });
    await Promise.race([held, Promise.resolve(binding).then(() => assert.fail("bound readiness accepted an absent owner"))]);
    acknowledgeSpawn();
    await starting;
    continueChecking();
    assert.equal(Object.values(await binding).every(value => value === true), true);
    const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
    const ready = JSON.parse(readFileSync(`${f.options.owner}.ready.json`, "utf8"));
    assert.equal(owner.launchId, f.options["launch-id"]); assert.equal(owner.adbPid, f.child.pid);
    assert.equal(ready.ownerId, owner.ownerId); assert.equal(ready.targetPid, disconnectedStatus.pid);
    f.stop(); assert.equal(await f.result, 0);
  } finally {
    acknowledgeSpawn?.(); continueChecking();
    await starting.catch(() => {});
  }
});

test("instrumentation binding keeps legacy synchronous refusal and requires an explicit remaining deadline", async (t) => {
  const f = boundInstrumentationFixture(t);
  assert.throws(() => f.bind(), /^Error: retained-instrumentation-owner-receipt-required$/);
  const args = ["bind-instrumentation-ready", "--owner", f.options.owner, "--serial", "fake-device"];
  const bound = ["--launch-id", f.options["launch-id"], "--launcher-pid", String(process.pid)];
  assert.equal(parseArgs([...args, ...bound, "--timeout-ms", "170000"]).timeoutMs, 170_000);
  for (const extra of [["--timeout-ms", "1"], bound, [...bound, "--timeout-ms", "0"],
    [...bound, "--timeout-ms", "180001"], ["--launch-id", f.options["launch-id"]],
    ["--launcher-pid", String(process.pid)]]) assert.throws(() => parseArgs([...args, ...extra]));
  await f.start();
  const proof = f.bind();
  assert.equal(typeof proof?.then, "undefined", "legacy callers retain their strict synchronous API");
  assert.equal(Object.values(proof).every(value => value === true), true);
  f.stop(); assert.equal(await f.result, 0);
});

test("bound instrumentation publication refuses lost terminal replaced and wrong-entry launchers", async (t) => {
  for (const kind of ["dead", "terminal", "replaced", "wrong-mode", "wrong-launch"]) {
    const f = boundInstrumentationFixture(t);
    let waiting = false; let waits = 0;
    await assert.rejects(f.boundBind({
      isLive: () => !(waiting && kind === "dead"),
      hostProcess: pid => {
        const row = f.hostProcess(pid);
        if (!waiting) return row;
        if (kind === "replaced") row.stdout = row.stdout.replace("10:11:12", "10:11:13");
        if (kind === "wrong-mode") row.stdout = row.stdout.replace(" run-instrumentation ", " run ");
        if (kind === "wrong-launch") row.stdout = row.stdout.replace(f.options["launch-id"], "f".repeat(32));
        return row;
      },
      adb: () => assert.fail("unpublished owner cannot authorize target inspection"),
      sleep: async () => {
        assert.equal(++waits, 1); waiting = true;
        if (kind === "terminal") writeFileSync(`${f.options.owner}.terminal.json`, "{}", { mode: 0o600 });
      },
    }), new RegExp(kind === "dead" ? "launcher-not-live" : kind === "terminal" ? "already-finished" :
      kind === "replaced" ? "launcher-replaced" : "launcher-command-mismatch"), kind);
    assert.equal(waits, 1); assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
  }
});

test("bound instrumentation rejects substituted publication and changed ownership during target reads", async (t) => {
  for (const kind of ["pid", "launch", "identity", "dead-child", "read-replaced", "read-terminal", "read-launcher"]) {
    const f = boundInstrumentationFixture(t); await f.start();
    const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
    if (kind === "pid") owner.supervisorPid += 1;
    if (kind === "launch") owner.launchId = "f".repeat(32);
    if (kind === "identity") owner.supervisorIdentity = "f".repeat(64);
    writeFileSync(f.options.owner, JSON.stringify(owner));
    let read = false;
    const adb = roleAdb([disconnectedStatus]);
    await assert.rejects(f.boundBind({
      hostProcess: pid => {
        if (kind === "dead-child" && pid === f.child.pid) return { status: 1, stdout: "" };
        const row = f.hostProcess(pid);
        if (read && kind === "read-launcher" && pid === process.pid) row.stdout = row.stdout.replace("10:11:12", "10:11:13");
        return row;
      },
      adb: args => {
        assert.ok(kind.startsWith("read-"), `${kind}: bad owner must reject before target reads`);
        const result = adb(args);
        if (args.at(-1) === "files/acceptance/physical-status") {
          read = true;
          if (kind === "read-replaced") writeFileSync(f.options.owner, JSON.stringify({ ...owner, launchId: "f".repeat(32) }));
          if (kind === "read-terminal") writeFileSync(`${f.options.owner}.terminal.json`, "{}", { mode: 0o600 });
        }
        return result;
      },
      sleep: () => assert.fail("invalid publication or target ownership must not be retried"),
    }), new RegExp(kind === "dead-child" ? "adb-not-live" : kind === "read-replaced" ? "receipt-replaced" :
      kind === "read-terminal" ? "already-finished" : kind === "read-launcher" ? "supervisor-changed" :
        "launch-binding-mismatch"), kind);
    assert.equal(read, kind.startsWith("read-"));
    assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
    if (kind === "read-terminal") rmSync(`${f.options.owner}.terminal.json`);
    f.stop(); assert.equal(await f.result, 0);
  }
});

test("bound instrumentation publication and target checks consume only the supplied remaining budget", async (t) => {
  for (const kind of ["never-published", "late-publication", "late-target-read", "within-budget"]) {
    const f = boundInstrumentationFixture(t); let now = 0; const waits = [];
    const adb = roleAdb([disconnectedStatus]);
    const checking = f.boundBind({ now: () => now, sleep: async delayMs => {
      waits.push(delayMs); assert.equal(waits.length, 1);
      now = kind === "never-published" || kind === "late-publication" ? 7 : 6;
      if (kind !== "never-published") await f.start();
    }, adb: args => {
      assert.ok(kind === "late-target-read" || kind === "within-budget");
      const result = adb(args);
      if (kind === "late-target-read" && args.at(-1) === "files/acceptance/physical-status") now = 7;
      return result;
    } }, { timeoutMs: 7 });
    if (kind === "within-budget") assert.equal(Object.values(await checking).every(value => value === true), true);
    else await assert.rejects(checking, /^Error: instrumentation-session-ready-deadline$/, kind);
    assert.deepEqual(waits, [7], "no reset to the default 180-second budget");
    assert.equal(existsSync(`${f.options.owner}.ready.json`), kind === "within-budget");
    if (f.result) { f.stop(); assert.equal(await f.result, 0); }
  }
});

// Restore the original identity on the very next read. An observed mismatch
// must remain terminal, not become a telemetry retry that later accepts it.
async function oneShotCollectorOwnershipMismatch(t, kind) {
  const f = boundCollectorFixture(t); await f.publish();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  let prefixRead = false; let postPrefixReads = 0; let retries = 0;
  let failure; let result;
  try {
    try {
      result = await f.boundCheck({
        hostProcess: pid => {
          const row = f.hostProcess(pid);
          if (prefixRead) {
            postPrefixReads++;
            if (kind === "launcher" && postPrefixReads === 1) {
              row.stdout = row.stdout.replace("10:11:12", "10:11:13");
            }
            if (kind === "receipt") {
              writeFileSync(f.options.owner, JSON.stringify(postPrefixReads === 1
                ? { ...owner, ownerId: "00000000-0000-0000-0000-000000000000" } : owner));
            }
          }
          return row;
        },
        wallNow: () => { prefixRead = true; return Date.now(); },
        sleep: async () => { retries++; },
      });
    } catch (error) { failure = error; }
  } finally {
    writeFileSync(f.options.owner, JSON.stringify(owner));
    f.stop(); assert.equal(await f.result, 0);
  }
  assert.equal(prefixRead, true, "the mutation follows an actual eligible prefix read");
  assert.ok(postPrefixReads >= 1, "the changed identity was actually returned to the owner check");
  if (!failure) {
    assert.equal(result, f.child.pid, "the old retry restored eligible collector evidence");
    assert.fail(`${kind}: observed one-shot ownership mismatch was retried into eligible acceptance`);
  }
  assert.match(failure.message, new RegExp(`^collector-session-${kind === "launcher" ? "launcher" : "owner"}-replaced$`));
  assert.equal(postPrefixReads, 1, "a later restored identity cannot erase the first observed mismatch");
  assert.equal(retries, 0, "ownership failure is outside the telemetry retry catch");
}

test("bound collector rejects one-shot launcher start drift after an eligible prefix", async (t) => {
  await oneShotCollectorOwnershipMismatch(t, "launcher");
});

test("bound collector rejects one-shot receipt drift after an eligible prefix", async (t) => {
  await oneShotCollectorOwnershipMismatch(t, "receipt");
});

test("collector exported readiness rejects invalid deadlines before any owner inspection or wait", async (t) => {
  const f = boundCollectorFixture(t);
  for (const timeoutMs of [undefined, NaN, Infinity, -Infinity, 0, -1, 30_001, "15000", null]) {
    await assert.rejects(checkCollectorSession({ owner: f.options.owner, "launch-id": f.options["launch-id"],
      launcherPid: process.pid, timeoutMs }, {
      now: () => assert.fail("invalid deadline cannot start an observation window"),
      hostProcess: () => assert.fail("invalid deadline cannot inspect a process"),
      isLive: () => assert.fail("invalid deadline cannot inspect liveness"),
      sleep: () => assert.fail("invalid deadline cannot wait for future publication"),
    }), /^Error: invalid-session-deadline$/);
  }
});


// A single failed probe inside requireLiveCollector must not be hidden by its
// caller's subsequent successful owner check. The child itself stays alive.
async function oneShotCollectorHelperLiveness(t, point) {
  const f = boundCollectorFixture(t); await f.publish();
  let prefixRead = false; let prefixProbes = 0; let injected = false; let retries = 0;
  let failure; let result;
  try {
    try {
      result = await f.boundCheck({
        wallNow: () => { prefixRead = true; prefixProbes = 0; return Date.now(); },
        isLive: pid => {
          if (prefixRead && pid === f.child.pid) {
            prefixProbes++;
            if (!injected && prefixProbes === (point === "before" ? 1 : 2)) {
              injected = true; return false;
            }
          }
          return true;
        },
        sleep: async () => { retries++; },
      });
    } catch (error) { failure = error; }
  } finally { f.stop(); assert.equal(await f.result, 0); }
  assert.equal(injected, true, "the actual helper must observe the one-shot child-liveness failure");
  if (!failure) {
    assert.equal(result, f.child.pid);
    assert.fail(`${point}: observed helper liveness failure was retried into eligible acceptance`);
  }
  assert.equal(failure.message, "collector-owner-not-live");
  assert.equal(retries, 0, "an observed dead child is not pending telemetry");
}

test("bound collector refuses one-shot helper liveness loss before the prefix read", async (t) => {
  await oneShotCollectorHelperLiveness(t, "before");
});

test("bound collector refuses one-shot helper liveness loss after the prefix read", async (t) => {
  await oneShotCollectorHelperLiveness(t, "after");
});

test("bound collector cannot retry observed terminal malformed or ineligible prefix evidence", async (t) => {
  for (const kind of ["summary", "malformed", "coverage"]) {
    const f = boundCollectorFixture(t); await f.publish();
    const telemetry = join(f.directory, "telemetry");
    const saved = readFileSync(telemetry, "utf8");
    let injected = false; let retries = 0; let failure; let result;
    try {
      try {
        result = await f.boundCheck({
          wallNow: () => {
            if (!injected) {
              injected = true;
              writeFileSync(telemetry, kind === "summary" ? saved + '{"type":"summary"}\n' :
                kind === "malformed" ? saved + 'not-json\n' :
                  saved.replace('"label":"test-cell"', '"label":"other-cell"'));
            }
            return Date.now();
          },
          hostProcess: pid => {
            // This is the next outer ownership check, AFTER the helper read.
            if (injected) writeFileSync(telemetry, saved);
            return f.hostProcess(pid);
          },
          sleep: async () => { retries++; },
        });
      } catch (error) { failure = error; }
    } finally {
      writeFileSync(telemetry, saved);
      f.stop(); assert.equal(await f.result, 0);
    }
    assert.equal(injected, true);
    if (!failure) {
      assert.equal(result, f.child.pid);
      assert.fail(`${kind}: observed invalid prefix was retried into eligible acceptance`);
    }
    if (kind === "malformed") assert.ok(failure instanceof SyntaxError);
    else assert.equal(failure.message, kind === "summary" ? "collector-already-finished" : "collector-workload-coverage-incomplete");
    assert.equal(retries, 0, `${kind}: a restored prefix cannot erase invalid evidence`);
  }
});

// Keep the real publisher and a real stop/join-owned child, but let the fixture
// publish prefix bytes at explicit barriers instead of a periodic sampler.
test("bound collector retries only missing or partial startup prefixes within the same deadline", async (t) => {
  for (const kind of ["missing", "partial"]) {
    const f = boundCollectorFixture(t);
    f.result = runCollectorSession(f.options, { foreground: () => foreground, signals: f.signals, hostProcess: f.hostProcess,
      spawn: (_exe, _args, settings) => {
        f.child = spawn(process.execPath, ["-e", fakeInstrumentation, join(f.directory, "stop")], settings);
        return f.child;
      },
    });
    assert.equal(existsSync(f.options.owner), true, "the launched owner is already published");
    const telemetry = join(f.directory, "telemetry");
    if (kind === "partial") writeFileSync(telemetry, '{"type":"environment"', { mode: 0o600 });
    else assert.equal(existsSync(telemetry), false);
    const stamp = Date.now(); let now = 0; let waits = 0;
    const prefix = [{ type: "environment", label: "test-cell" },
      { type: "sample", startTimeUnixMs: stamp - 2, endTimeUnixMs: stamp,
        eligibility: { eligible: true }, telemetryErrors: [] }].map(row => JSON.stringify(row) + "\n").join("");
    const result = await f.boundCheck({ now: () => now, wallNow: () => stamp, sleep: async delayMs => {
      assert.equal(++waits, 1); assert.equal(delayMs, 100);
      now += delayMs;
      writeFileSync(telemetry, prefix, { mode: 0o600 });
    } });
    assert.equal(result, f.child.pid); assert.equal(waits, 1); assert.equal(now, 100);
    f.stop(); assert.equal(await f.result, 0);
  }
});


// Source wiring complements the actual AM argv assertion in instrumentationFixture:
// the producer must stamp the launch outside snapshot/fallback and before writing.
test("instrumentation launch reaches AM and every app status path", async (t) => {
  const f = boundInstrumentationFixture(t);
  await f.start();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.launchId, f.options["launch-id"]);
  assert.equal(owner.buildId, f.options["build-id"]);
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  assert.match(source, /private val acceptanceLaunchId = InstrumentationRegistry\.getArguments\(\)\.getString\("acceptanceLaunchId"\)/);
  assert.ok(source.includes('require(Regex("^[a-f0-9]{32}$").matches(it))'));
  const status = source.slice(source.indexOf("    private fun status("), source.indexOf("    private fun failureStatus("));
  const fallback = status.indexOf('.put("snapshotErrorType",');
  const kind = status.indexOf('.put("type", "status")');
  assert.ok(fallback > 0 && kind > fallback, "status identity must be outside both snapshot paths");
  for (const field of ['.put("launchId", acceptanceLaunchId)', '.put("sessionId", memorySessionId)',
    '.put("buildId", BuildConfig.URNETWORK_ACCEPTANCE_BUILD_ID)']) {
    assert.ok(status.indexOf(field) > kind && status.indexOf(field) < status.indexOf("writePrivate(statusFile,"));
    assert.equal(status.split(field).length, 2);
  }
  assert.match(source, /status\(id, "error", application, startElapsedMs, extra, failure\.phase\)/);
  f.stop(); assert.equal(await f.result, 0);
});

test("acceptance flushes native logs after UI cleanup and before instrumentation termination", () => {
  for (const name of ["PhysicalLowbarSessionTest", "MainAcceptanceTest", "InsufficientBalanceSessionTest"]) {
    const source = readFileSync(new URL(`../app/src/androidTest/java/com/bringyour/network/acceptance/${name}.kt`, import.meta.url), "utf8");
    assert.match(source, /@get:Rule\(order = 0\)\s+val logFlush = AcceptanceLogFlushRule\(Sdk::flushGlog\)/,
      `${name} must flush the existing SDK sink before JUnit can complete`);
    assert.match(source, /@get:Rule\(order = 1\)\s+val compose = createEmptyComposeRule\(\)/,
      `${name} must finish Compose cleanup inside the log flush rule`);
  }
});

test("physical failure attribution is captured before owned teardown and published without rereading login state", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const owned = source.slice(source.indexOf("    private fun runOwnedDeviceSession("),
    source.indexOf("    private class MemoryTeardownState"));
  const caught = owned.indexOf("} catch (error: Throwable) {");
  const captured = owned.indexOf("captureFailure(error)", caught);
  const rethrown = owned.indexOf("throw error", caught);
  const final = owned.indexOf("} finally {", caught);
  const close = owned.indexOf("closeOwnedDeviceScope(", final);
  assert.ok(caught > 0 && captured > caught && rethrown > captured && final > rethrown && close > final,
    "actual owned session must capture its failure before finally logs out the app");
  const publish = source.slice(source.indexOf("    private fun failureStatus("),
    source.indexOf("    private fun stopClient("));
  assert.match(publish, /failure: PhysicalFailureSnapshot/);
  assert.match(publish, /JSONObject\(failure\.statusFields\(\)\)/);
  assert.match(publish, /status\(id, "error", application, startElapsedMs, extra, failure\.phase\)/);
  assert.doesNotMatch(publish, /loginStartupState|error\.message|error\.cause/);
  const outer = source.slice(source.indexOf("    fun physicalLowbarSession()"),
    source.indexOf("    private fun runOwnedDeviceSession("));
  assert.match(outer, /failureStatus\(activeCommandId, application, startElapsedMs, captureFailure\(error\)\)/);
  assert.match(outer, /\.onFailure\(error::addSuppressed\)\s+throw error/);
  assert.match(outer, /sessionFailure\.addSuppressed\(finalError\)/);
  const status = source.slice(source.indexOf("    private fun status("),
    source.indexOf("    private fun captureFailure("));
  assert.match(status, /statusPhase: String = phase/);
  assert.ok(status.indexOf('.put("phase", statusPhase)') > status.indexOf('.put("snapshotErrorType",'),
    "the failure phase must survive both snapshot and fallback paths");
});

test("physical egress and startup errors are attributed only at their actual throw boundaries", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const probe = source.slice(source.indexOf("    private fun peerEgressProbe(): String"),
    source.indexOf("    private fun peerEgressProbeWithTrafficProof("));
  assert.match(probe, /EgressProbeRequest\.queryPublicIp/);
  assert.match(probe, /captureFailure\(error, PhysicalFailureOperation\.EGRESS_PROBE\)/);
  assert.match(probe, /\.onFailure\(error::addSuppressed\)\s+throw error/);
  assert.equal(source.split("EgressProbeRequest.queryPublicIp").length, 2,
    "public, peer, and explicit probe callers must share the same failure boundary");
  assert.equal(source.split("peerEgressProbeWithTrafficProof(device)").length, 4);
  assert.match(source, /is LoginStartupState\.Failed -> throw LoginStartupFailureException\(state\)/);
  assert.match(source, /is LoginStartupState\.LoggedOut -> throw PhysicalLoginLoggedOutException\(\)/);
  assert.match(source, /throwLoginStartupTimeout\(\s+state,\s+PhysicalLoginStartupTimeout\(state, AUTH_TIMEOUT_MILLIS\)/);
});

test("egress worker stacks are default off and wired only to the nonqualifying diagnostic owner", () => {
  const base = "../app/src/androidTest/java/com/bringyour/network/acceptance/";
  const source = readFileSync(new URL(`${base}PhysicalLowbarSessionTest.kt`, import.meta.url), "utf8");
  const request = readFileSync(new URL(`${base}EgressProbeRequest.kt`, import.meta.url), "utf8");
  const activity = readFileSync(new URL(`${base}EgressProbeActivity.java`, import.meta.url), "utf8");
  const client = readFileSync(new URL("../app/src/acceptanceShared/java/com/bringyour/network/acceptance/EgressProbeClient.java", import.meta.url), "utf8");
  const probe = source.slice(source.indexOf("    private fun peerEgressProbe(): String"),
    source.indexOf("    private fun peerEgressProbeWithTrafficProof("));
  assert.match(probe, /EgressProbeRequest\.queryPublicIp\(\s*instrumentation,\s*EGRESS_TIMEOUT_MILLIS,\s*diagnosticWorkerStacks = ioLoopDiagnostics != null,/);
  assert.match(source, /private var ioLoopDiagnostics: com\.bringyour\.sdk\.IoLoopDiagnosticSession\? = null/);
  assert.match(source, /physicalIoLoopDiagnosticRequested\(ioLoopDiagnosticOption, BuildConfig\.DEBUG\)/);
  assert.match(source, /if \(ioLoopDiagnosticRequested\) \{[\s\S]*?val diagnostic = Sdk\.newIoLoopDiagnosticSession\(\)\s*ioLoopDiagnostics = diagnostic/);
  assert.equal((source.match(/ioLoopDiagnostics = diagnostic/g) || []).length, 2,
    "only the explicit branch may assign the session and application diagnostic owners");
  assert.equal((request.match(/diagnosticWorkerStacks: Boolean = false/g) || []).length, 3);
  assert.match(request, /execute\(instrumentation, timeoutMillis, diagnosticWorkerStacks = diagnosticWorkerStacks\)/);
  assert.match(request, /if \(diagnosticWorkerStacks && fixedResult == null && trafficBytes <= 0\) \{\s*putExtra\(EgressProbeActivity\.EXTRA_DIAGNOSTIC_WORKER_STACKS, true\)/);
  assert.match(activity, /getBooleanExtra\(EXTRA_DIAGNOSTIC_WORKER_STACKS, false\)/);
  assert.match(activity, /EgressProbeClient\.queryPublicIp\(diagnosticWorkerStacks\)/);
  assert.match(client, /static String queryPublicIp\(\) throws Exception \{\s*return queryPublicIp\(false\);/);
  const collector = readFileSync(new URL("physical_collector_session.mjs", import.meta.url), "utf8");
  assert.match(collector, /"io-loop-diagnostic"\] === "1" \? \{ ioLoopDiagnostic: true, qualificationEligible: false \}/);
});

test("public startup rejects intent-only readiness before egress", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const body = source.slice(source.indexOf("    private fun connectPublic("),
    source.indexOf("    private fun startProvider("));
  assert.doesNotMatch(body, /waitFor\(PhysicalWaitStage\.PUBLIC_VPN_CONNECTION, CONNECT_TIMEOUT_MILLIS\)\s*\{\s*connectVc\.connected && device\.connectEnabled && device\.tunnelStarted\s*\}/,
    "selected intent and an established tunnel are not provider-window admission");
  assert.match(body, /runPhysicalPublicEgress\(/);
  assert.match(body, /timeoutMillis = CONNECT_TIMEOUT_MILLIS/);
  assert.match(body, /nowMillis = SystemClock::elapsedRealtime/);
  assert.match(body, /sleepMillis = SystemClock::sleep/);
  assert.match(body, /egressProof = \{ peerEgressProbeWithTrafficProof\(device\) \}/);
  const route = body.slice(body.indexOf("routeState = {"), body.indexOf("egressProof = {"));
  assert.doesNotMatch(route, /carrierBytes|physicalSelectedCarriers|peerEgressProbe/);
  assert.ok(body.indexOf("PhysicalWaitStage.US_PROVIDER_CARRIER_EVIDENCE") > body.indexOf("egressProof = {"),
    "application-byte proof must remain after the one-shot probe");
});

test("public startup brackets provider evidence with the current connection generation", () => {
  const source = readFileSync(new URL("../app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt", import.meta.url), "utf8");
  const body = source.slice(source.indexOf("    private fun connectPublic("),
    source.indexOf("    private fun startProvider("));
  assert.match(body, /connectVc\.connect\(checkNotNull\(selected\)\)\s+expectedConnectionGeneration = checkNotNull\(device\.windowStatus\)\.connectionGeneration/);
  assert.ok(body.indexOf("uiDevice.clickVerifiedVpnConsentIfPresent()") < body.indexOf("runPhysicalPublicEgress("),
    "the existing startup budget begins after connect/consent");
  assert.match(body, /val before = device\.windowStatus\s+val providers = liveProviders\(device\)\s+val after = device\.windowStatus/);
  assert.match(body, /generationBefore = before\?\.connectionGeneration \?: 0L/);
  assert.match(body, /generationAfter = after\?\.connectionGeneration \?: 0L/);
  assert.match(body, /minSatisfied = before\?\.minSatisfied == true && after\?\.minSatisfied == true/);
  assert.match(body, /failed = before\?\.failed != false \|\| after\?\.failed != false/);
  const peer = source.slice(source.indexOf("    private fun connectPeer("),
    source.indexOf("    private fun commandResult("));
  assert.match(peer, /runPhysicalPeerEgress\(/);
  assert.match(peer, /requestedPeerId = device\.connectLocation\?\.connectLocationId\?\.clientId\?\.idStr/);
  assert.match(peer, /providers = liveProviders\(device\)/);
});

test("bound ready refuses prior or malformed app generations before publishing identity", async (t) => {
  for (const change of [{ launchId: undefined }, { launchId: "f".repeat(32) }, { launchId: null },
    { launchId: 123 }, { launchId: [disconnectedStatus.launchId] }, { buildId: "other-fixture" },
    { buildId: null }, { buildId: 123 }, { buildId: ["fixture"] }, { sessionId: undefined },
    { sessionId: null }, { sessionId: 123 }, { sessionId: "not-a-session" }, { sessionId: [disconnectedStatus.sessionId] }]) {
    const f = boundInstrumentationFixture(t); await f.start();
    await assert.rejects(f.boundBind({ adb: roleAdb([{ ...disconnectedStatus, ...change }]),
      sleep: () => assert.fail("wrong target generation must not be retried") }),
    /^Error: instrumentation-target-generation-mismatch$/);
    assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
    f.stop(); assert.equal(await f.result, 0);
  }
});

test("ready-bound command and role readers reject generation drift with the same live PID", async (t) => {
  const f = boundInstrumentationFixture(t); await f.start(); await f.boundBind();
  const ready = JSON.parse(readFileSync(`${f.options.owner}.ready.json`, "utf8"));
  assert.equal(ready.launchId, f.options["launch-id"]); assert.equal(ready.buildId, "fixture");
  assert.equal(ready.targetSessionId, disconnectedStatus.sessionId);
  for (const change of [{ launchId: undefined }, { launchId: "f".repeat(32) }, { launchId: null },
    { launchId: 123 }, { launchId: [disconnectedStatus.launchId] }, { buildId: "other-fixture" },
    { sessionId: "22222222-2222-4222-8222-222222222222" }, { sessionId: null },
    { sessionId: 123 }, { sessionId: [disconnectedStatus.sessionId] }]) {
    const row = { ...disconnectedStatus, ...change };
    assert.throws(() => checkInstrumentationCommandSession({ owner: f.options.owner, serial: "fake-device" },
      { hostProcess: f.hostProcess, adb: roleAdb([row]) }), /^Error: instrumentation-target-generation-mismatch$/);
    await assert.rejects(actualCheckSessionRole({ ...directRole, "instrumentation-owner": f.options.owner },
      { hostProcess: f.hostProcess, adb: roleAdb([row]),
        sleep: () => assert.fail("generation mismatch cannot become a role wait") }, false),
    /^Error: instrumentation-target-generation-mismatch$/);
  }
  const original = readFileSync(`${f.options.owner}.ready.json`, "utf8");
  for (const change of [{ launchId: "f".repeat(32) }, { buildId: "other-fixture" },
    { targetSessionId: "" }, { targetSessionId: null }, { targetSessionId: 123 },
    { targetSessionId: [disconnectedStatus.sessionId] }]) {
    writeFileSync(`${f.options.owner}.ready.json`, JSON.stringify({ ...ready, ...change }), { mode: 0o600 });
    assert.throws(() => checkInstrumentationCommandSession({ owner: f.options.owner, serial: "fake-device" },
      { hostProcess: f.hostProcess, adb: () => assert.fail("invalid ready identity must fail before device reads") }),
    /^Error: bound-ready-instrumentation-generation-required$/);
  }
  writeFileSync(`${f.options.owner}.ready.json`, original);
  f.stop(); assert.equal(await f.result, 0);
});

test("bound owners require typed launch and build identity even for legacy binder calls", async (t) => {
  const f = boundInstrumentationFixture(t); await f.start();
  const original = readFileSync(f.options.owner, "utf8");
  const owner = JSON.parse(original);
  for (const change of [{ buildId: undefined }, { buildId: null }, { buildId: 123 }, { buildId: ["fixture"] },
    { launchId: [f.options["launch-id"]] }, { launchId: "" }, { launchId: null }, { launchId: 123 }]) {
    writeFileSync(f.options.owner, JSON.stringify({ ...owner, ...change }), { mode: 0o600 });
    assert.throws(() => f.bind(() => assert.fail("invalid bound owner must not query the device")),
      /^Error: invalid-instrumentation-owner-receipt$/);
    await assert.rejects(f.boundBind({ adb: () => assert.fail("invalid bound owner must not query the device") }),
      /^Error: invalid-instrumentation-owner-receipt$/);
    assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
  }
  writeFileSync(f.options.owner, original);
  await f.boundBind();
  f.stop(); assert.equal(await f.result, 0);
});

test("native-loop opt-in reaches actual AM argv, ready binding and unchanged terminal ownership", async (t) => {
  const f = boundInstrumentationFixture(t);
  f.options["io-loop-diagnostic"] = "1";
  await f.start();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.ioLoopDiagnostic, true);
  assert.equal(owner.qualificationEligible, false);
  await assert.rejects(f.boundBind({ adb: roleAdb([disconnectedStatus]) }), /native-loop-diagnostic-opt-in-not-observed/);
  await f.boundBind({ adb: roleAdb([{ ...disconnectedStatus, ioLoopDiagnosticEnabled: true }]) });
  const ready = JSON.parse(readFileSync(f.options.owner + ".ready.json", "utf8"));
  assert.equal(ready.ioLoopDiagnostic, true);
  assert.equal(ready.qualificationEligible, false);
  assert.equal(ready.targetSessionId, disconnectedStatus.sessionId);
  f.stop();
  assert.equal(await f.result, 0);
  const terminal = JSON.parse(readFileSync(f.options.owner + ".terminal.json", "utf8"));
  assert.equal(terminal.ioLoopDiagnostic, true);
  assert.equal(terminal.qualificationEligible, false);
  assert.equal(terminal.ownerId, owner.ownerId);
});

test("native-loop absent option stays absent and invalid opt-ins cannot spawn", async (t) => {
  const f = boundInstrumentationFixture(t);
  await f.start();
  assert.equal(Object.hasOwn(JSON.parse(readFileSync(f.options.owner, "utf8")), "ioLoopDiagnostic"), false);
  f.stop(); assert.equal(await f.result, 0);
  for (const invalid of ["", "0", "true", "2"]) {
    const g = boundInstrumentationFixture(t);
    g.options["io-loop-diagnostic"] = invalid;
    await assert.rejects(runInstrumentationSession(g.options, { spawn: () => assert.fail("invalid opt-in must not spawn") }),
      /invalid-native-loop-diagnostic-option-no-spawn/);
    assert.equal(existsSync(g.options.owner), false);
  }
  const h = instrumentationFixture(t);
  h.options["io-loop-diagnostic"] = "1";
  await assert.rejects(runInstrumentationSession(h.options, { foreground: () => foreground,
    spawn: () => assert.fail("unbound launch must not spawn") }), /native-loop-diagnostic-launch-binding-required-no-spawn/);
});

const hostedH1Policy = "hosted-h1-api-alt-off-v1";
const hostedH1Runner = "com.bringyour.network.test/com.bringyour.network.acceptance.HostedH1PhysicalRunner";

test("hosted H1 policy parser requires the exact dedicated runner and policy together", () => {
  const base = ["run-instrumentation", "--owner", "/fixture/owner.json", "--stdout", "/fixture/stdout",
    "--stderr", "/fixture/stderr", "--serial", "fake-device", "--label", "fixture", "--build-id", "fixture",
    "--native-inputs", "/fixture/native.json", "--launch-id", "abcdef0123456789abcdef0123456789"];
  const parsed = parseArgs([...base, "--component", hostedH1Runner, "--hosted-transport-policy", hostedH1Policy]);
  assert.equal(parsed.component, hostedH1Runner);
  assert.equal(parsed["hosted-transport-policy"], hostedH1Policy);
  assert.equal(parseArgs(base)["hosted-transport-policy"], undefined);
  for (const tail of [["--component", hostedH1Runner], ["--hosted-transport-policy", hostedH1Policy],
    ["--component", hostedH1Runner, "--hosted-transport-policy", "hosted-h1-api-alt-off-v2"],
    ["--component", "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner", "--hosted-transport-policy", hostedH1Policy]]) {
    assert.throws(() => parseArgs([...base, ...tail]), /invalid-hosted-h1-policy-no-spawn/);
  }
});

test("hosted H1 launch forwards exact policy and requires actual API alt off before ready", async (t) => {
  const f = boundInstrumentationFixture(t);
  f.options.component = hostedH1Runner;
  f.options["hosted-transport-policy"] = hostedH1Policy;
  await f.start();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.hostedTransportPolicy, hostedH1Policy);
  assert.equal(owner.qualificationEligible, false);
  for (const status of [disconnectedStatus,
    { ...disconnectedStatus, hostedTransportPolicy: hostedH1Policy },
    { ...disconnectedStatus, hostedTransportPolicy: hostedH1Policy, apiAltEnabled: true },
    { ...disconnectedStatus, hostedTransportPolicy: hostedH1Policy, apiAltEnabled: "false" },
    { ...disconnectedStatus, hostedTransportPolicy: "another-policy", apiAltEnabled: false }]) {
    await assert.rejects(f.boundBind({ adb: roleAdb([status]) }), /hosted-h1-policy-not-observed/);
    assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
  }
  const status = { ...disconnectedStatus, hostedTransportPolicy: hostedH1Policy, apiAltEnabled: false };
  await f.boundBind({ adb: roleAdb([status]) });
  const ready = JSON.parse(readFileSync(`${f.options.owner}.ready.json`, "utf8"));
  assert.equal(ready.hostedTransportPolicy, hostedH1Policy);
  assert.equal(ready.apiAltEnabled, false);
  assert.equal(ready.qualificationEligible, false);
  assert.equal(ready.targetSessionId, disconnectedStatus.sessionId);
  const commandOptions = { owner: f.options.owner, serial: "fake-device" };
  assert.throws(() => checkInstrumentationCommandSession(commandOptions, {
    hostProcess: f.hostProcess, adb: roleAdb([{ ...status, apiAltEnabled: true }]),
  }), /hosted-h1-policy-not-observed/);
  const command = checkInstrumentationCommandSession(commandOptions, { hostProcess: f.hostProcess, adb: roleAdb([status]) });
  assert.equal(command.sessionId, owner.ownerId);
  f.stop(); assert.equal(await f.result, 0);
  const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`, "utf8"));
  assert.equal(terminal.ownerId, owner.ownerId);
  assert.equal(terminal.hostedTransportPolicy, hostedH1Policy);
  assert.equal(terminal.qualificationEligible, false);
});

test("hosted H1 rejects unbound or mismatched launch before spawn", async (t) => {
  for (const change of [{ component: hostedH1Runner }, { "hosted-transport-policy": hostedH1Policy },
    { component: hostedH1Runner, "hosted-transport-policy": "unknown" }]) {
    const f = boundInstrumentationFixture(t);
    Object.assign(f.options, change);
    await assert.rejects(runInstrumentationSession(f.options, {
      foreground: () => assert.fail("invalid policy must precede process inspection"),
      spawn: () => assert.fail("invalid policy cannot spawn"),
    }), /invalid-hosted-h1-policy-no-spawn/);
    assert.equal(existsSync(f.options.owner), false);
  }
  const unbound = instrumentationFixture(t);
  unbound.options.component = hostedH1Runner;
  unbound.options["hosted-transport-policy"] = hostedH1Policy;
  await assert.rejects(runInstrumentationSession(unbound.options, { foreground: () => foreground,
    spawn: () => assert.fail("unbound hosted policy cannot spawn") }), /hosted-h1-policy-launch-binding-required-no-spawn/);
});

test("hosted H1 process and ready records cannot relabel a default or different policy", async (t) => {
  const validCommand = adbCommand.replace("com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner",
    `-e hostedTransportPolicy ${hostedH1Policy} ${hostedH1Runner}`);
  assert.match(instrumentationProcessIdentity(psRow(4321, validCommand), "fake-device", 4321), /^[a-f0-9]{64}$/);
  for (const command of [validCommand.replace(hostedH1Policy, "unknown"),
    validCommand.replace(`-e hostedTransportPolicy ${hostedH1Policy} `, ""),
    validCommand.replace(hostedH1Runner, `-e hostedTransportPolicy ${hostedH1Policy} ${hostedH1Runner}`)]) {
    assert.throws(() => instrumentationProcessIdentity(psRow(4321, command), "fake-device", 4321), /retained-adb-hosted-h1-policy-required/);
  }
  const f = boundInstrumentationFixture(t);
  f.options.component = hostedH1Runner;
  f.options["hosted-transport-policy"] = hostedH1Policy;
  await f.start();
  await f.boundBind({ adb: roleAdb([{ ...disconnectedStatus, hostedTransportPolicy: hostedH1Policy, apiAltEnabled: false }]) });
  const path = `${f.options.owner}.ready.json`;
  const original = readFileSync(path, "utf8");
  for (const change of [{ hostedTransportPolicy: undefined }, { hostedTransportPolicy: "unknown" },
    { apiAltEnabled: true }, { apiAltEnabled: "false" }, { qualificationEligible: true }]) {
    writeFileSync(path, JSON.stringify({ ...JSON.parse(original), ...change }));
    assert.throws(() => checkInstrumentationCommandSession({ owner: f.options.owner, serial: "fake-device" }, {
      hostProcess: f.hostProcess, adb: () => assert.fail("invalid ready policy must precede device reads"),
    }), /bound-ready-instrumentation-required/);
  }
  writeFileSync(path, original);
  f.stop(); assert.equal(await f.result, 0);
});


const scopedH1Policy = "hosted-h1-api-alt-off-scoped-v2";
const scopedH1Scope = "hosted-h1-dual-memory-v1";

test("scoped hosted parser rejects relabelled legacy and diagnostics before any owner can spawn", () => {
  const base = ["run-instrumentation", "--owner", "/fixture/owner.json", "--stdout", "/fixture/stdout",
    "--stderr", "/fixture/stderr", "--serial", "fake-device", "--label", "fixture", "--build-id", "fixture",
    "--native-inputs", "/fixture/native.json", "--launch-id", "abcdef0123456789abcdef0123456789",
    "--component", hostedH1Runner, "--hosted-transport-policy"];
  const valid = [...base, scopedH1Policy, "--qualification-scope", scopedH1Scope];
  assert.equal(parseArgs(valid)["qualification-scope"], scopedH1Scope);
  for (const args of [
    [...base, scopedH1Policy],
    [...base, scopedH1Policy, "--qualification-scope", "synthetic-other-scope"],
    [...base, hostedH1Policy, "--qualification-scope", scopedH1Scope],
    [...valid, "--io-loop-diagnostic", "1"],
    [...valid, "--qualification-scope", scopedH1Scope],
  ]) assert.throws(() => parseArgs(args), /invalid-(hosted-h1-policy|scoped-hosted-h1-diagnostic|session-arguments)/);
});

test("scoped process identity binds exact policy and scope not merely a receipt label", () => {
  const valid = adbCommand.replace("com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner",
    `-e hostedTransportPolicy ${scopedH1Policy} -e qualificationScope ${scopedH1Scope} ${hostedH1Runner}`);
  assert.match(instrumentationProcessIdentity(psRow(4321, valid), "fake-device", 4321), /^[a-f0-9]{64}$/);
  for (const command of [
    valid.replace(`-e qualificationScope ${scopedH1Scope} `, ""),
    valid.replace(scopedH1Scope, "synthetic-other-scope"),
    valid.replace(scopedH1Policy, hostedH1Policy),
    valid.replace(hostedH1Runner, `-e qualificationScope ${scopedH1Scope} ${hostedH1Runner}`),
    valid.replace(hostedH1Runner, `-e ioLoopDiagnostic 1 ${hostedH1Runner}`),
  ]) assert.throws(() => instrumentationProcessIdentity(psRow(4321, command), "fake-device", 4321),
    /retained-adb-hosted-h1-policy-required/);
});

test("scoped invalid launch cannot inspect foreground native inputs credentials or spawn", async (t) => {
  for (const change of [
    { "qualification-scope": undefined },
    { "qualification-scope": "synthetic-other-scope" },
    { "hosted-transport-policy": hostedH1Policy },
    { "io-loop-diagnostic": "1" },
  ]) {
    const f = boundInstrumentationFixture(t);
    Object.assign(f.options, { component: hostedH1Runner, "hosted-transport-policy": scopedH1Policy,
      "qualification-scope": scopedH1Scope }, change);
    await assert.rejects(runInstrumentationSession(f.options, {
      foreground: () => assert.fail("invalid scope cannot inspect processes"),
      verifyNativeInputs: () => assert.fail("invalid scope cannot acquire native custody"),
      spawn: () => assert.fail("invalid scope cannot acquire instrumentation custody"),
    }), /invalid-hosted-h1-policy-no-spawn|invalid-scoped-hosted-h1-diagnostic-no-spawn/);
    assert.equal(existsSync(f.options.owner), false);
  }
});

test("scoped real owner waits for actual app scope and retains nonfull eligibility through join", async (t) => {
  const f = boundInstrumentationFixture(t);
  Object.assign(f.options, { component: hostedH1Runner, "hosted-transport-policy": scopedH1Policy,
    "qualification-scope": scopedH1Scope });
  await f.start();
  const owner = JSON.parse(readFileSync(f.options.owner, "utf8"));
  assert.equal(owner.qualificationScope, scopedH1Scope);
  assert.equal(owner.scopedQualificationEligible, true);
  assert.equal(owner.qualificationEligible, false);
  const valid = { ...disconnectedStatus, hostedTransportPolicy: scopedH1Policy, apiAltEnabled: false,
    qualificationScope: scopedH1Scope, scopedQualificationEligible: true, qualificationEligible: false };
  for (const change of [
    { qualificationScope: undefined }, { qualificationScope: "synthetic-other-scope" },
    { scopedQualificationEligible: false }, { qualificationEligible: true }, { apiAltEnabled: true },
  ]) {
    await assert.rejects(f.boundBind({ adb: roleAdb([{ ...valid, ...change }]) }),
      /scoped-hosted-h1-evidence-mismatch|hosted-h1-policy-not-observed/);
    assert.equal(existsSync(`${f.options.owner}.ready.json`), false);
  }
  await f.boundBind({ adb: roleAdb([valid]) });
  const path = `${f.options.owner}.ready.json`;
  const original = readFileSync(path, "utf8");
  const ready = JSON.parse(original);
  assert.equal(ready.qualificationScope, scopedH1Scope);
  assert.equal(ready.scopedQualificationEligible, true);
  assert.equal(ready.qualificationEligible, false);
  writeFileSync(path, JSON.stringify({ ...ready, qualificationScope: "synthetic-other-scope" }));
  assert.throws(() => checkInstrumentationCommandSession({ owner: f.options.owner, serial: "fake-device" }, {
    hostProcess: f.hostProcess, adb: () => assert.fail("bad scoped owner must fail before device reads"),
  }), /scoped-hosted-h1-evidence-mismatch/);
  writeFileSync(path, original);
  f.stop(); assert.equal(await f.result, 0);
  const terminal = JSON.parse(readFileSync(`${f.options.owner}.terminal.json`, "utf8"));
  assert.equal(terminal.ownerId, owner.ownerId);
  assert.equal(terminal.qualificationScope, scopedH1Scope);
  assert.equal(terminal.scopedQualificationEligible, true);
  assert.equal(terminal.qualificationEligible, false);
});
