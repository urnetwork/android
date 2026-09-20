import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import test from "node:test";

const script = new URL("./physical_native_consumer.sh", import.meta.url).pathname;
const provenance = new URL("./physical_native_provenance.mjs", import.meta.url).href;
// Locate the shared gate explicitly; only copies in temporary fixture roots are
// locked. Never acquire the production SDK output lock or run a build/device.
const gateSource = readFileSync(new URL("../../../sdk/build/sdk-android-output-lock.sh", import.meta.url), "utf8");
const write = (path, value, mode = 0o600) => { mkdirSync(dirname(path), { recursive: true, mode: 0o700 }); writeFileSync(path, value, { mode }); };
const fakePrepare = `
import fs from 'node:fs';
import {parseArgs,requireNativeConsumerLock} from ${JSON.stringify(provenance)};
const [helper,...args]=process.argv.slice(2);
if(!helper.endsWith('/physical_native_provenance.mjs'))throw Error('wrong-helper');
const options=parseArgs(args);
if(options.mode!=='prepare-consumer')throw Error('wrong-operation');
const emit=x=>fs.appendFileSync(process.env.EVENTS,x+'\\n',{mode:0o600});
requireNativeConsumerLock(options.root); emit('lock');
if(process.env.FAIL==='capture'){emit('capture-failed');process.exit(2)}
if(process.env.FAIL!=='omit-after')fs.writeFileSync(options.after,JSON.stringify({phase:'after'}),{mode:0o600,flag:'wx'});
emit('after');
if(process.env.FAIL==='verify'){emit('verify-failed');process.exit(2)}
if(process.env.FAIL!=='omit-proof')fs.writeFileSync(options.output,JSON.stringify({eligible:true}),{mode:0o600,flag:'wx'});
emit('verified');
if(process.env.FAIL==='lose-lock')fs.renameSync(process.env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_PATH,process.env.URNETWORK_ANDROID_SDK_OUTPUT_LOCK_PATH+'.replaced');
`;
const fakeConsumer = `
import fs from 'node:fs';
import {requireNativeConsumerLock} from ${JSON.stringify(provenance)};
requireNativeConsumerLock(process.env.FIXTURE_ROOT);
for(const path of [process.env.AFTER,process.env.PROOF])if(!fs.existsSync(path))throw Error('missing-proof-before-consumer');
fs.appendFileSync(process.env.EVENTS,'assemble\\ncopy\\nabi-linkage\\nam-boundary\\n',{mode:0o600});
if(process.argv[2]!=='literal space ; $value')throw Error('argv-was-reinterpreted');
process.exit(Number(process.env.CONSUMER_EXIT??0));
`;

function fixture(t) {
  const root = realpathSync(mkdtempSync(join(tmpdir(), "native-consumer-order-")));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const sdk = join(root, "sdk/build"); const directory = join(root, "private");
  mkdirSync(directory, { mode: 0o700 });
  write(join(sdk, "sdk-android-output-lock.sh"), gateSource);
  const paths = { before: join(directory, "before.json"), after: join(directory, "after.json"), proof: join(directory, "proof.json"),
    writer: join(directory, "writer.json") };
  write(paths.before, JSON.stringify({ fixture: true }));
  write(join(root, "fake-prepare.mjs"), fakePrepare); write(join(root, "fake-consumer.mjs"), fakeConsumer);
  write(join(root, "bin/node"), `#!/bin/sh\nexec ${JSON.stringify(process.execPath)} ${JSON.stringify(join(root, "fake-prepare.mjs"))} "$@"\n`, 0o700);
  const env = { ...process.env, PATH: `${join(root, "bin")}:${process.env.PATH}`, FIXTURE_ROOT: root,
    EVENTS: join(directory, "events"), AFTER: paths.after, PROOF: paths.proof };
  for (const key of Object.keys(env)) if (key.startsWith("URNETWORK_ANDROID_SDK_OUTPUT_LOCK_")) delete env[key];
  const args = [script, "--root", root, "--before", paths.before, "--after", paths.after, "--proof", paths.proof,
    "--writer-receipt", paths.writer, "--", process.execPath, join(root, "fake-consumer.mjs"), "literal space ; $value"];
  return { root, sdk, paths, env, args, events: () => existsSync(env.EVENTS) ? readFileSync(env.EVENTS, "utf8").trim().split("\n") : [],
    run(extra = {}) { return spawnSync("bash", args, { cwd: root, env: { ...env, ...extra }, encoding: "utf8", timeout: 10000 }); } };
}

test("CXAusf real wrapper retains a real kernel consumer lock and cannot skip after/verify before any consumer step", t => {
  const f = fixture(t); const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(f.events(), ["lock", "after", "verified", "assemble", "copy", "abi-linkage", "am-boundary"]);
  assert.equal(result.stdout, ""); assert.equal(result.stderr, "");
});

test("capture/verify failure, missing published after/proof, and a replaced lock cannot reach assembly or AM", t => {
  for (const kind of ["capture", "verify", "omit-after", "omit-proof", "lose-lock"]) {
    const f = fixture(t); const result = f.run({ FAIL: kind });
    assert.equal(result.status, 2, `${kind}: ${result.stderr}`);
    assert.equal(f.events().includes("assemble"), false, kind); assert.equal(f.events().includes("am-boundary"), false, kind);
    if (kind.startsWith("omit")) assert.equal(result.stderr, "native consumer failed: native-proof-not-published-no-consumer-spawn\n");
    if (kind === "lose-lock") assert.equal(result.stderr, "native consumer failed: output-lock-lost-no-consumer-spawn\n");
  }
});

test("consumer command failure is joined and propagated, not replaced with proof-success exit zero", t => {
  const f = fixture(t); const result = f.run({ CONSUMER_EXIT: "7" });
  assert.equal(result.status, 7); assert.equal(f.events().at(-1), "am-boundary");
  // A separate owner can take the fixture output after the wrapper has joined.
  const released = spawnSync("bash", [join(f.sdk, "sdk-android-output-lock.sh"), "fixture-next-owner", "--", "true"],
    { cwd: f.sdk, env: f.env, encoding: "utf8", timeout: 5000 });
  assert.equal(released.status, 0, released.stderr);
});

test("missing options and inherited writer markers reject before metadata or consumer spawn", t => {
  const f = fixture(t);
  for (const args of [[script], f.args.filter((value, index) => index !== 5 && index !== 6)]) {
    const result = spawnSync("bash", args, { cwd: f.root, env: f.env, encoding: "utf8", timeout: 3000 });
    assert.equal(result.status, 2); assert.deepEqual(f.events(), []);
  }
  assert.equal(f.run({ URNETWORK_ANDROID_SDK_OUTPUT_LOCK_HELD: "1" }).stderr,
    "native consumer failed: inherited-output-lock-not-accepted-no-consumer-spawn\n");
  assert.deepEqual(f.events(), []);
});

test("a live writer lock fails closed with a fixed no-spawn reason, without exposing lock diagnostics", async t => {
  const f = fixture(t);
  const child = spawn("bash", ["-c", 'source "$1"; sdk_android_output_lock_acquire fixture-writer || exit; printf "ready\\n"; read -r done',
    "fixture", join(f.sdk, "sdk-android-output-lock.sh")], { cwd: f.sdk, env: f.env, stdio: ["pipe", "pipe", "pipe"] });
  const closed = new Promise(resolve => child.once("close", resolve));
  t.after(async () => { child.stdin.end("done\n"); await closed; });
  let ready = ""; child.stdout.on("data", data => { ready += data; });
  for (let i = 0; i < 200 && !ready.includes("ready"); i++) await sleep(10);
  assert.equal(ready, "ready\n");
  const result = f.run(); assert.equal(result.status, 2);
  assert.equal(result.stderr, "native consumer failed: output-lock-unavailable-no-consumer-spawn\n");
  assert.deepEqual(f.events(), []);
});
