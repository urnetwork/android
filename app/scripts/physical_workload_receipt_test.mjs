import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { evaluateWorkloadCoverage, ownWorkloads, parseArgs, recordChild, requireCompletedWorkloads, requireLiveCollector } from "./physical_workload_receipt.mjs";

const script = new URL("./physical_workload_receipt.mjs", import.meta.url).pathname;
const closed = (pid = 500, exitCode = 0) => ({ closed: true, pid, exitCode, signal: null, interrupted: false });
function telemetry(path, label, end = 10_000, first = end - 2000) {
  const records = [{ type: "environment", label }];
  for (let start = first; start < end; start += 1000) records.push({ type: "sample",
    startTimeUnixMs: start, endTimeUnixMs: Math.min(start + 1000, end),
    eligibility: { eligible: true }, telemetryErrors: [] });
  writeFileSync(path, records.map(JSON.stringify).join("\n") + "\n", { mode: 0o600 });
  return records;
}

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "physical-workload-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const options = { serial: "fake-device", label: "cell-01", output: join(directory, "workloads.json"),
    children: ["pages", "fast-1"], "collector-pid": 900, telemetry: join(directory, "telemetry.ndjson"), command: ["fake-owner"] };
  telemetry(options.telemetry, options.label);
  let now = 10_000;
  let ownerPath;
  const calls = [];
  const f = { directory, options, calls, collectorLive: true, live: new Set(),
    childRun: async () => closed(), ownerResult: closed(501),
    deps: { wallNow: () => ++now, isLive: (pid) => pid === 900 ? f.collectorLive : f.live.has(pid),
      adb: (args) => { calls.push(args); return { status: args.includes("pidof") ? 1 : 0, stdout: "", stderr: "" }; } },
    get ownerPath() { return ownerPath; },
    async child(name) { return recordChild({ ownerPath, name, command: ["fake-probe"] }, { ...f.deps, run: f.childRun }); },
    async cleanup(serial = options.serial) { return recordChild({ ownerPath, cleanup: true, serial }, f.deps); },
    async run(body = async () => { for (const name of options.children) await f.child(name); await f.cleanup(); }) {
      return ownWorkloads(options, { ...f.deps, run: async (_command, env) => {
        ownerPath = env.URNETWORK_PERF_WORKLOAD_OWNER; await body(); return f.ownerResult;
      } });
    },
    check() { return requireCompletedWorkloads(options.output, options.label, options.serial, f.deps); },
  };
  return f;
}

test("owner predeclares unique ordered children and requires explicit collector ownership", () => {
  const parsed = parseArgs(["owner", "--serial", "fake", "--label", "cell", "--output", "out", "--children", "pages,fast-1",
    "--collector-pid", "42", "--telemetry", "samples", "--", "sh", "workload.sh"]);
  assert.deepEqual(parsed.children, ["pages", "fast-1"]);
  assert.deepEqual(parsed.command, ["sh", "workload.sh"]);
  for (const args of [[], ["child", "--name", "pages"], ["cleanup", "--serial", "fake", "--", "true"],
    ["owner", "--label", "cell", "--output", "out", "--children", "pages", "--", "true"]]) assert.throws(() => parseArgs(args));
});

test("owner receipt is exclusive, private, device-bound and published only after children plus cleanup", async (t) => {
  const f = fixture(t);
  const receipt = await f.run();
  assert.deepEqual(receipt.childReceipts.map((r) => r.name), ["pages", "fast-1", "cleanup"]);
  assert.equal(receipt.failedChildCount, 0);
  assert.equal(statSync(f.options.output).mode & 0o777, 0o600);
  assert.deepEqual(f.check(), receipt);
  assert.equal(f.calls.length, 2);
  assert.deepEqual(f.calls[0], ["-s", "fake-device", "shell", "am", "force-stop", "com.android.chrome"]);
  assert.deepEqual(f.calls[1], ["-s", "fake-device", "shell", "pidof", "com.android.chrome"]);
  assert.throws(() => requireCompletedWorkloads(f.options.output, "different-cell", "fake-device", f.deps));
  assert.throws(() => requireCompletedWorkloads(f.options.output, f.options.label, "different-device", f.deps));
  await assert.rejects(f.run(), /output-already-exists/);
});

test("gShVxc: executor yield while child is held never creates a completion receipt", async (t) => {
  const f = fixture(t);
  let release;
  let entered;
  const active = new Promise((resolve) => { entered = resolve; });
  let calls = 0;
  f.childRun = async () => {
    if (calls++ === 0) { entered(); return new Promise((resolve) => { release = () => resolve(closed()); }); }
    return closed();
  };
  const result = f.run();
  await active;
  assert.equal(existsSync(f.options.output), false);
  assert.throws(() => f.check());
  assert.equal(f.calls.length, 0, "browser cleanup cannot race an unjoined child");
  release();
  await result;
  assert.equal(f.check().state, "complete");
});

test("missing child, skipped order, duplicate child and missing cleanup cannot qualify", async (t) => {
  for (const body of [async (f) => {}, async (f) => f.child("fast-1"),
    async (f) => { await f.child("pages"); await f.child("pages"); },
    async (f) => { await f.child("pages"); await f.child("fast-1"); }]) {
    const f = fixture(t);
    await assert.rejects(f.run(() => body(f)));
    assert.equal(existsSync(f.options.output), false);
  }
});

test("yielded, signalled, interrupted and shell-masked interruption results are rejected", async (t) => {
  for (const result of [{ sessionId: 17 }, { ...closed(), closed: false }, { ...closed(), signal: "SIGTERM" },
    { ...closed(), interrupted: true }, closed(500, 130)]) {
    const f = fixture(t); f.childRun = async () => result;
    await assert.rejects(f.run(), /child-not-joined-or-interrupted/);
    assert.equal(existsSync(f.options.output), false);
  }
  const f = fixture(t); f.ownerResult = { ...closed(501), interrupted: true };
  await assert.rejects(f.run(), /owner-not-joined-or-interrupted/);
});

test("a shell exit cannot conceal a live background process group", async (t) => {
  const f = fixture(t); f.live.add(-500);
  await assert.rejects(f.run(), /child-not-joined-or-interrupted/);
  assert.equal(existsSync(f.options.output), false);
});

test("normal probe failure is preserved while allowing joined diagnostic memory collection", async (t) => {
  const f = fixture(t); let count = 0;
  f.childRun = async () => closed(500, count++ === 0 ? 2 : 0);
  await f.run();
  assert.equal(f.check().failedChildCount, 1);
  assert.equal(f.check().childReceipts[0].exitCode, 2);
});

test("cleanup must stop the correct browser and prove its process absent", async (t) => {
  for (const result of [{ status: 0, stdout: "123", stderr: "" }, { status: 1, stdout: "", stderr: "adb error" },
    { status: null, stdout: "", stderr: "", signal: "SIGTERM" }]) {
    const f = fixture(t);
    f.deps.adb = (args) => args.includes("pidof") ? result : { status: 0 };
    await assert.rejects(f.run(), /browser-cleanup-not-verified/);
    assert.equal(existsSync(f.options.output), false);
  }
  const f = fixture(t);
  await assert.rejects(f.run(async () => { await f.child("pages"); await f.child("fast-1"); await f.cleanup("wrong-device"); }),
    /matching-browser-cleanup-serial-required/);
});

test("gy8htp: no workload may start before a live collector has a fresh eligible sample", async (t) => {
  for (const mutate of [
    (f) => { f.collectorLive = false; },
    (f) => { writeFileSync(f.options.telemetry, ""); },
    (f) => { telemetry(f.options.telemetry, f.options.label, 4000); },
    (f) => { telemetry(f.options.telemetry, "wrong-label"); },
    (f) => { const r = telemetry(f.options.telemetry, f.options.label); r.at(-1).eligibility.eligible = false;
      writeFileSync(f.options.telemetry, r.map(JSON.stringify).join("\n") + "\n"); },
  ]) {
    const f = fixture(t); mutate(f); let ran = false;
    await assert.rejects(f.run(async () => { ran = true; }));
    assert.equal(ran, false);
    assert.equal(existsSync(f.options.output), false);
  }
});

test("collector death or restart during a child invalidates the owner", async (t) => {
  for (const change of [
    (f) => { f.collectorLive = false; },
    (f) => { telemetry(f.options.telemetry, f.options.label, 10_000, 9000); },
  ]) {
    const f = fixture(t); f.childRun = async () => { change(f); return closed(); };
    await assert.rejects(f.run());
    assert.equal(existsSync(f.options.output), false);
  }
});

test("active coverage rejects a late collector even when its five quiet minutes are perfect", (t) => {
  const f = fixture(t);
  const rows = telemetry(f.options.telemetry, f.options.label, 350_000, 50_000);
  assert.equal(evaluateWorkloadCoverage(rows, 50_000, 350_000, f.options.label).eligible, true);
  assert.equal(evaluateWorkloadCoverage(rows, 1000, 350_000, f.options.label).eligible, false);
  const gap = rows.filter((r) => r.type !== "sample" || r.startTimeUnixMs < 60_000 || r.startTimeUnixMs > 70_000);
  assert.equal(evaluateWorkloadCoverage(gap, 50_000, 350_000, f.options.label).eligible, false);
});

test("terminated collector cannot pass the final live check", async (t) => {
  const f = fixture(t); await f.run();
  f.collectorLive = false;
  assert.throws(() => f.check(), /collector-owner-not-live/);
});

test("real CLI joins actual child processes and fake browser cleanup without adb/devices", (t) => {
  const f = fixture(t);
  telemetry(f.options.telemetry, f.options.label, Date.now());
  writeFileSync(join(f.directory, "adb"), `#!${process.execPath}\n` +
    `const assert=require('node:assert/strict'),a=process.argv.slice(2);assert.deepEqual(a.slice(0,3),['-s','fake-device','shell']);` +
    `assert.equal(a.at(-1),'com.android.chrome');if(a[3]==='pidof')process.exitCode=1;else assert.equal(a[3],'am');\n`, { mode: 0o700 });
  const ownerScript = join(f.directory, "owner.mjs");
  writeFileSync(ownerScript, `import {spawnSync} from 'node:child_process';\n` +
    `for(const name of ['pages','fast-1']){const r=spawnSync(${JSON.stringify(process.execPath)},[${JSON.stringify(script)},'child','--name',name,'--',${JSON.stringify(process.execPath)},'-e','process.exit(0)'],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??2);}\n` +
    `const r=spawnSync(${JSON.stringify(process.execPath)},[${JSON.stringify(script)},'cleanup','--serial','fake-device'],{stdio:'inherit'});process.exitCode=r.status??2;\n`);
  const result = spawnSync(process.execPath, [script, "owner", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", "pages,fast-1", "--collector-pid", `${process.pid}`,
    "--telemetry", f.options.telemetry, "--", process.execPath, ownerScript],
  { encoding: "utf8", timeout: 10_000, env: { ...process.env, PATH: f.directory } });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, "");
  const receipt = requireCompletedWorkloads(f.options.output, f.options.label, f.options.serial);
  assert.equal(receipt.childReceipts.length, 3);
  assert.ok(receipt.childReceipts.every((r) => r.wrapperPid !== process.pid));
});

test("real owner interruption never publishes a receipt", { timeout: 10_000 }, async (t) => {
  const f = fixture(t); telemetry(f.options.telemetry, f.options.label, Date.now());
  const child = spawn(process.execPath, [script, "owner", "--serial", f.options.serial, "--label", f.options.label,
    "--output", f.options.output, "--children", "pages", "--collector-pid", `${process.pid}`,
    "--telemetry", f.options.telemetry, "--", process.execPath, "-e", "process.stdout.write('ready\\n');setInterval(()=>{},1000)"],
  { stdio: ["ignore", "pipe", "pipe"] });
  t.after(() => { if (child.exitCode === null && child.signalCode === null) child.kill("SIGTERM"); });
  const terminal = new Promise((resolve) => child.once("close", (code) => resolve(code)));
  await new Promise((resolve) => child.stdout.once("data", resolve));
  child.kill("SIGTERM");
  assert.equal(await terminal, 2);
  assert.equal(existsSync(f.options.output), false);
});
