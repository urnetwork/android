import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { captureQuietPhase, parseArgs } from "./physical_quiet_phase.mjs";
import { evaluateQuietWindow } from "./physical_quiet_gate.mjs";

function fixture(t, base = 1_000_000) {
  const directory = mkdtempSync(join(tmpdir(), "quiet-phase-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  let time = 0;
  let token = 0;
  let command;
  let published = false;
  let reads = 0;
  let current = { type: "status", state: "complete", commandId: "traffic-end", phase: "traffic",
    goMemoryProfileRateBytes: 0,
    goMemoryLimitBytes: 32 * 1024 * 1024, trackedMemory: { targetBytes: 20 * 1024 * 1024 },
    pid: 17, elapsedMs: 1000, connected: true, tunnelStarted: true, provideEnabled: false };
  const calls = [];
  const telemetryPath = join(directory, "telemetry.ndjson");
  const updateTelemetry = () => {
    const rows = [{ type: "environment", label: "run-01" }];
    for (let start = base - 2000; start < base + time; start += 1000) {
      rows.push({ type: "sample", startTimeUnixMs: start, endTimeUnixMs: Math.min(start + 1000, base + time),
        eligibility: { eligible: true }, telemetryErrors: [] });
    }
    writeFileSync(telemetryPath, rows.map(JSON.stringify).join("\n") + "\n");
  };
  updateTelemetry();
  const workloadsPath = join(directory, "workloads.json");
  const child = { schema: 1, type: "workload-child", ownerId: "fixture-owner", state: "complete",
    exitCode: 0, signal: null, interrupted: false, wrapperPid: 10_000_001,
    startedHostTimeUnixMs: base - 900, completedHostTimeUnixMs: base - 800 };
  writeFileSync(workloadsPath, JSON.stringify({ schema: 1, type: "workload-owner", ownerId: "fixture-owner",
    label: "run-01", serialHash: createHash("sha256").update("fake-device").digest("hex"), ownerPid: 10_000_002,
    retainedOwner: { schema: 1, identity: "a".repeat(64), foreground: {
      inputTTY: true, outputTTY: true, processGroup: 10_000_002, foregroundGroup: 10_000_002 } },
    state: "complete", exitCode: 0, signal: null, interrupted: false, processGroup: 10_000_003,
    children: ["pages"], startedHostTimeUnixMs: base - 1000, completedHostTimeUnixMs: base - 500, failedChildCount: 0,
    collector: { pid: process.pid, path: telemetryPath },
    childReceipts: [{ ...child, name: "pages", processGroup: 10_000_004 },
      { ...child, name: "cleanup", startedHostTimeUnixMs: base - 700, completedHostTimeUnixMs: base - 600,
        browserStopped: true, browserPackage: "com.android.chrome" }] }), { mode: 0o600 });
  const value = {
    directory, calls, options: { serial: "fake-device", label: "run-01", workloads: workloadsPath, output: join(directory, "start.json") },
    get current() { return current; }, set current(status) { current = status; published = false; },
    poll: undefined,
    advance(ms) { time += ms; updateTelemetry(); },
    deps: {
      now: () => time, wallNow: () => base + time,
      uuid: () => `token-${++token}`, sleep: async (ms) => { time += ms; updateTelemetry(); },
      adb: (args, input, timeout) => {
        calls.push({ args, input, timeout });
        assert.deepEqual(args.slice(0, 5), ["-s", "fake-device", "shell", "run-as", "com.bringyour.network"]);
        assert.ok(timeout > 0 && timeout <= 2000);
        const verb = args[5];
        if (verb === "tee") {
          assert.match(args[6], /^files\/acceptance\/physical-command\.token-\d+$/);
          command = input.trim().split("|");
          assert.equal(command[1], "phase");
          published = false;
          return { status: 0, stdout: input };
        }
        if (verb === "mv") {
          assert.equal(args[7], "files/acceptance/physical-command");
          published = true;
          reads = 0;
          return { status: 0, stdout: "" };
        }
        assert.equal(verb, "cat");
        if (published) {
          const next = { ...current, commandId: command[0], phase: command[2], state: "complete", elapsedMs: 1000 + time };
          current = value.poll?.(next, reads++) ?? next;
        }
        return { status: 0, stdout: JSON.stringify(current) };
      },
    },
  };
  return value;
}

test("caller supplies a label and joined workloads, or a saved start, never a raw phase or command ID", () => {
  assert.deepEqual(parseArgs(["--serial", "fake", "--label", "cell-1", "--workloads", "joined", "--output", "out"]),
    { serial: "fake", label: "cell-1", workloads: "joined", output: "out" });
  for (const args of [
    ["--serial", "fake", "--phase", "quiet", "--output", "out"],
    ["--serial", "fake", "--label", "cell", "--start", "old", "--output", "out"],
    ["--serial", "fake", "--output", "out"],
    ["--serial", "fake", "--label", "cell-1", "--output", "out"],
    ["--serial", "fake", "--start", "old", "--workloads", "joined", "--output", "out"],
  ]) assert.throws(() => parseArgs(args));
});

test("command, matching acknowledgment and private host envelope are one operation", async (t) => {
  const f = fixture(t);
  f.poll = (next, attempt) => {
    assert.equal(existsSync(f.options.output), false, "must not publish running/stale status");
    return attempt < 2 ? { ...next, state: "running" } : next;
  };
  const result = await captureQuietPhase(f.options, f.deps);
  assert.equal(result.status.phase, "quiet-run-01");
  assert.equal(result.status.state, "complete");
  assert.equal(result.hostTimeUnixMs, 1_000_500);
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.deepEqual(JSON.parse(readFileSync(f.options.output)), result);
  assert.deepEqual(f.calls.map((call) => call.args[5]), ["cat", "tee", "mv", "cat", "cat", "cat"]);
  assert.equal(f.calls[1].input, "quiet-token-1|phase|quiet-run-01\n");
  assert.deepEqual(readdirSync(f.directory), ["start.json", "telemetry.ndjson", "workloads.json"]);
});

test("WdNkB1 regression: raw status cannot be reused as an end's start envelope", async (t) => {
  const f = fixture(t);
  writeFileSync(f.options.output, JSON.stringify({ ...f.current, phase: "quiet" }));
  await assert.rejects(captureQuietPhase({ serial: "fake-device", start: f.options.output,
    output: join(f.directory, "end.json") }, f.deps), /validated-start-envelope-required/);
  assert.equal(f.calls.length, 0);
});

test("invalid labels are rejected before any adb call or output file", async (t) => {
  const f = fixture(t);
  for (const label of ["", "phase|finish", "bad label", "line\nbreak"]) {
    await assert.rejects(captureQuietPhase({ ...f.options, label }, f.deps), /safe-nonempty-label/);
  }
  assert.equal(f.calls.length, 0);
  assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson", "workloads.json"]);
});

test("end inherits exact phase/process and uses a fresh command ID; gate accepts its shape", async (t) => {
  const f = fixture(t);
  const start = await captureQuietPhase(f.options, f.deps);
  f.advance(330_000);
  const end = await captureQuietPhase({ serial: "fake-device", start: f.options.output,
    output: join(f.directory, "end.json") }, f.deps);
  assert.equal(end.status.phase, start.status.phase);
  assert.notEqual(end.status.commandId, start.status.commandId);
  const result = evaluateQuietWindow({ start, end, phase: start.status.phase, role: "client", underlay: "wifi",
    memory: Array.from({ length: 21 }, (_, i) => ({ type: "sample", phase: start.status.phase,
      goMemoryProfileRateBytes: 0,
      goMemoryLimitBytes: 32 * 1024 * 1024,
      elapsedMs: start.status.elapsedMs + (i + 1) * 15000, samplerDropped: 0, goRuntimeBytes: 20 * 1024 * 1024 })),
    telemetry: [{ type: "environment", label: "run-01" }, ...Array.from({ length: 332 }, (_, i) => ({ type: "sample",
      startTimeUnixMs: start.hostTimeUnixMs + (i - 1) * 1000, endTimeUnixMs: start.hostTimeUnixMs + (i - 1) * 1000 + 100,
      network: { activeNetwork: { transports: ["VPN"] }, underlayNetworks: [{ transports: ["WIFI"] }] },
      eligibility: { eligible: true, reasons: [] }, telemetryErrors: [],
    }))],
  });
  assert.equal(result.eligible, true, JSON.stringify(result));
});

test("stale complete status never qualifies; timeout is deterministic and publishes nothing", async (t) => {
  const f = fixture(t);
  const stale = f.current;
  f.poll = () => stale;
  await assert.rejects(captureQuietPhase(f.options, f.deps), /acknowledgment-timeout/);
  assert.equal(f.deps.now(), 30_000);
  assert.equal(existsSync(f.options.output), false);
  assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson", "workloads.json"]);
});

test("wrong phase, error, new process, another command and role changes are not acknowledged", async (t) => {
  for (const change of [
    { phase: "quiet" }, { state: "error" }, { pid: 99 }, { commandId: "other-owner" }, { connected: false },
  ]) {
    const f = fixture(t);
    f.poll = (next) => ({ ...next, ...change });
    await assert.rejects(captureQuietPhase(f.options, f.deps));
    assert.equal(existsSync(f.options.output), false);
    assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson", "workloads.json"]);
  }
});

test("existing output is never overwritten or followed by a device command", async (t) => {
  const f = fixture(t);
  writeFileSync(f.options.output, "prior-evidence");
  await assert.rejects(captureQuietPhase(f.options, f.deps), /output-already-exists/);
  assert.equal(readFileSync(f.options.output, "utf8"), "prior-evidence");
  assert.equal(f.calls.length, 0);
});

test("failed writes, empty stdin echo and failed atomic publish leave no qualifying envelope", async (t) => {
  for (const mode of ["write", "echo", "publish"]) {
    const f = fixture(t);
    const adb = f.deps.adb;
    const cleanup = [];
    f.deps.adb = (args, input, timeout) => {
      if (args[5] === "rm") { cleanup.push(args); return { status: 0 }; }
      if ((mode === "write" && args[5] === "tee") || (mode === "publish" && args[5] === "mv")) {
        return { status: 1, stdout: "private remote error" };
      }
      if (mode === "echo" && args[5] === "tee") return { status: 0, stdout: "" };
      return adb(args, input, timeout);
    };
    await assert.rejects(captureQuietPhase(f.options, f.deps), /phase-command-/);
    assert.equal(existsSync(f.options.output), false);
    assert.deepEqual(readdirSync(f.directory), ["telemetry.ndjson", "workloads.json"]);
    assert.equal(cleanup.length, 1);
    assert.match(cleanup[0].at(-1), /^files\/acceptance\/physical-command\.token-\d+$/);
  }
});

test("end rejects a role lifecycle change before issuing another phase command", async (t) => {
  const f = fixture(t);
  await captureQuietPhase(f.options, f.deps);
  f.calls.length = 0;
  f.current = { ...f.current, phase: "finish" };
  await assert.rejects(captureQuietPhase({ serial: "fake-device", start: f.options.output,
    output: join(f.directory, "end.json") }, f.deps), /live-idle-session-required/);
  assert.deepEqual(f.calls.map((call) => call.args[5]), ["cat"]);
});

test("CLI actually forwards stdin through shell/run-as/tee, not exec-out", (t) => {
  const f = fixture(t, Date.now());
  const statePath = join(f.directory, "fake-state.json");
  const inputPath = join(f.directory, "fake-input");
  writeFileSync(statePath, JSON.stringify(f.current));
  writeFileSync(join(f.directory, "adb"), `#!${process.execPath}\n` +
    `const fs=require('node:fs'),assert=require('node:assert/strict'),a=process.argv.slice(2);` +
    `assert.deepEqual(a.slice(0,5),['-s','fake-device','shell','run-as','com.bringyour.network']);` +
    `const p=${JSON.stringify(statePath)},q=${JSON.stringify(inputPath)};` +
    `if(a[5]==='tee'){const s=fs.readFileSync(0,'utf8');fs.writeFileSync(q,s);process.stdout.write(s);}` +
    `else if(a[5]==='mv'){const s=JSON.parse(fs.readFileSync(p));const c=fs.readFileSync(q,'utf8').trim().split('|');` +
    `s.commandId=c[0];s.phase=c[2];s.state='complete';s.elapsedMs++;fs.writeFileSync(p,JSON.stringify(s));}` +
    `else{assert.equal(a[5],'cat');process.stdout.write(fs.readFileSync(p));}\n`, { mode: 0o700 });
  const result = spawnSync(process.execPath, [new URL("./physical_quiet_phase.mjs", import.meta.url).pathname,
    "--serial", "fake-device", "--label", "run-01", "--workloads", f.options.workloads, "--output", f.options.output], {
    encoding: "utf8", env: { ...process.env, PATH: f.directory },
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, "");
  const envelope = JSON.parse(readFileSync(f.options.output));
  assert.equal(envelope.status.phase, "quiet-run-01");
  assert.equal(typeof envelope.hostTimeUnixMs, "number");
  assert.equal(readFileSync(inputPath, "utf8"), `${envelope.status.commandId}|phase|quiet-run-01\n`);
});

test("gShVxc regression: yielded, live, interrupted or uncleaned workloads cannot issue quiet-start", async (t) => {
  for (const mutate of [
    (r) => { r.state = "running"; r.sessionId = 123; },
    (r) => { r.ownerPid = process.pid; },
    (r) => { r.interrupted = true; },
    (r) => { r.childReceipts.pop(); },
    (r) => { r.childReceipts[0].wrapperPid = process.pid; },
    (r) => { r.childReceipts[0].signal = "SIGTERM"; },
    (r) => { r.childReceipts.at(-1).browserStopped = false; },
    (r) => { r.label = "other-cell"; },
  ]) {
    const f = fixture(t);
    const receipt = JSON.parse(readFileSync(f.options.workloads)); mutate(receipt);
    writeFileSync(f.options.workloads, JSON.stringify(receipt));
    await assert.rejects(captureQuietPhase(f.options, f.deps), /completed-workload-receipt-required/);
    assert.equal(f.calls.length, 0, "no adb call may precede owner/children/cleanup proof");
    assert.equal(existsSync(f.options.output), false);
  }
});
