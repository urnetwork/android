import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { EventEmitter } from "node:events";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import test, { after } from "node:test";
import { bindInstrumentationReady, checkCollectorSession, checkSessionRole as actualCheckSessionRole,
  instrumentationProcessIdentity, parseArgs, requireRetainedForeground,
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
      return psRow(pid, adbCommand);
    },
    async start() {
      f.result = runInstrumentationSession(options, { foreground: () => foreground, signals, hostProcess: f.hostProcess,
        spawn: (command, args, settings) => {
          assert.equal(command, "adb");
          assert.deepEqual(args, ["-s", "fake-device", "shell", "am", "instrument", "-w", "-r", "-e", "class",
            "com.bringyour.network.acceptance.PhysicalLowbarSessionTest", "-e", "acceptanceBuildId", "fixture",
            "com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner"]);
          f.child = spawn(process.execPath, ["-e", fakeInstrumentation, join(directory, "stop")], settings);
          assert.equal(existsSync(options.owner), false, "no running receipt before live child verification");
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
