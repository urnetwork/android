#!/usr/bin/env node
import { spawn, spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, openSync, realpathSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { hashNativeInputFile, nativeProvenanceReason, nativeWriterArguments, nativeWriterSourceHashes,
  requireNativeBeforeWriter } from "./physical_native_provenance.mjs";

class WriterError extends Error {}
const fail = reason => { throw new WriterError(reason); };
const reason = error => error instanceof WriterError ? error.message : nativeProvenanceReason(error);
const OWNER_TERM_MS = 1000;
const OWNER_JOIN_MS = 5000;
const OWNER_SIGNALS = ["SIGINT", "SIGTERM", "SIGHUP"];

// A live session leader signals only its own group. Its parent never signals a
// numeric identity after reaping. This covers the launched group, not a helper
// that deliberately starts another session; daemon policy is bound in argv.
async function runGradleOwner(nonce, args) {
  if (!process.send || !/^[a-f0-9-]{36}$/.test(nonce)) fail("private-writer-owner-channel-required");
  let child; let childStarted = false; let childClosed = false; let stopping = false; let finished = false;
  let killTimer; let pollTimer; let outcome = { childExitCode: null, signal: null };
  const send = (type, fields = {}, callback = () => {}) => {
    if (!process.connected) { callback(new Error("writer-parent-disconnected")); return; }
    process.send({ nonce, ownerPid: process.pid, type, ...fields }, callback);
  };
  const finish = () => {
    if (finished) return;
    finished = true; clearTimeout(killTimer); clearTimeout(pollTimer);
    process.off("disconnect", disconnected);
    send("complete", { childStarted, ...outcome }, error => {
      if (error && process.connected) process.disconnect();
    });
  };
  const stop = (signal, cause) => {
    if (finished || stopping) return;
    stopping = true; // Own-group TERM is delivered back here too.
    send("stopping", { cause });
    killTimer = setTimeout(() => {
      send("escalating", {}, () => process.kill(-process.pid, "SIGKILL"));
    }, OWNER_TERM_MS);
    process.kill(-process.pid, signal);
    if (!child) { childClosed = true; queueMicrotask(check); }
  };
  const check = () => {
    if (finished || !childClosed) return;
    // Numeric census only: never retain command lines or adopt another group.
    // ps itself is the exact, already-reaped census child, not an orphan.
    const rows = spawnSync("/bin/ps", ["-axo", "pid=,pgid="],
      { encoding: "utf8", timeout: 2000, maxBuffer: 1024 * 1024, env: { ...process.env, LC_ALL: "C" } });
    let others = true;
    try {
      if (rows.error || rows.status !== 0 || rows.signal || rows.stderr.trim() ||
          !Number.isSafeInteger(rows.pid) || rows.pid <= 0) throw new Error();
      const members = rows.stdout.trim().split("\n").map(row => {
        const match = /^\s*(\d+)\s+(\d+)\s*$/.exec(row);
        if (!match || !match.slice(1).every(value => Number.isSafeInteger(Number(value)))) throw new Error();
        return match.slice(1).map(Number);
      });
      if (!members.some(([pid, group]) => pid === process.pid && group === process.pid)) throw new Error();
      others = members.some(([pid, group]) => group === process.pid && pid !== process.pid && pid !== rows.pid);
    } catch { stop("SIGTERM", "census-unproved"); }
    if (!others) { finish(); return; }
    if (!stopping) stop("SIGTERM", "orphaned");
    pollTimer = setTimeout(check, 25);
  };
  const disconnected = () => stop("SIGTERM", "parent-disconnected");
  process.on("disconnect", disconnected);
  for (const signal of OWNER_SIGNALS) process.on(signal, () => stop(signal, "interrupted"));
  process.on("message", message => {
    if (finished) {
      if (message?.nonce === nonce && ["release", "stop"].includes(message.action) && process.connected) process.disconnect();
      return;
    }
    if (message?.nonce !== nonce) { stop("SIGTERM", "invalid-owner-message"); return; }
    if (message.action === "stop" && OWNER_SIGNALS.includes(message.signal)) { stop(message.signal, "interrupted"); return; }
    if (message.action !== "start" || child || stopping) { stop("SIGTERM", "invalid-owner-message"); return; }
    try {
      child = spawn(join(process.cwd(), "gradlew"), args, { env: process.env, stdio: ["ignore", 1, 2], detached: false });
      child.once("spawn", () => { childStarted = true; send("started"); });
      child.once("error", () => {}); // close owns the terminal path, including spawn refusal.
      child.once("close", (childExitCode, signal) => {
        // A refused spawn has no child exit status; Node's negative error code is not one.
        outcome = childStarted ? { childExitCode, signal } : { childExitCode: null, signal: null };
        childClosed = true;
        send("exited", { childStarted, ...outcome }); check();
      });
    } catch { childClosed = true; send("exited", { childStarted, ...outcome }); check(); }
  });
  send("ready");
}

export function parseArgs(argv) {
  const keys = ["root", "before", "build-id", "profile-rate", "memory-profile", "max-workers", "receipt", "stdout", "stderr"];
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !keys.includes(key) || options[key] !== undefined || !argv[i + 1] || argv[i + 1].startsWith("--")) {
      fail("explicit-writer-arguments-required");
    }
    options[key] = argv[i + 1];
  }
  if (keys.some(key => options[key] === undefined) || !/^(?:0|[1-9][0-9]{0,8})$/.test(options["profile-rate"]) ||
      !/^[1-9][0-9]*$/.test(options["max-workers"])) fail("explicit-writer-arguments-required");
  for (const key of ["root", "before", "receipt", "stdout", "stderr"]) if (!isAbsolute(options[key])) fail("absolute-writer-paths-required");
  nativeWriterArguments(options["build-id"], Number(options["profile-rate"]), options["memory-profile"], Number(options["max-workers"]));
  return options;
}

function publish(path, value, binding) {
  const temporary = `${path}.pending-${randomUUID()}`;
  try {
    requireArtifactPaths(binding, [path, temporary]);
    writeFileSync(temporary, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
    requireArtifactPaths(binding, [path, temporary]);
    linkSync(temporary, path); // Atomic, exclusive publication on the host.
  } finally { if (existsSync(temporary)) unlinkSync(temporary); }
}

export async function runNativeWriter(options, dependencies = {}) {
  let binding;
  try {
    binding = prepareArtifactDirectory(dirname(options.receipt));
    requireArtifactPaths(binding, [options.before, options.receipt, options.stdout, options.stderr]);
  } catch (error) { fail(`${artifactDirectoryReason(error)}-no-writer-spawn`); }
  const paths = [options.before, options.receipt, options.stdout, options.stderr].map(path => resolve(path));
  if (new Set(paths).size !== paths.length || [options.receipt, options.stdout, options.stderr].some(existsSync)) {
    fail("fresh-writer-artifacts-required-no-writer-spawn");
  }
  const maxWorkers = Number(options["max-workers"]); const profileRate = Number(options["profile-rate"]);
  const args = nativeWriterArguments(options["build-id"], profileRate, options["memory-profile"], maxWorkers);
  const before = requireNativeBeforeWriter(options, dependencies);
  const sourceHashes = nativeWriterSourceHashes(); const beforeSha256 = hashNativeInputFile(options.before).sha256;
  const workingDirectory = join(before.root, "android/app");
  const env = { ...(dependencies.env ?? process.env), BRINGYOUR_HOME: before.root, WARP_HOME: before.root,
    URNETWORK_ANDROID_SDK_BUILD_OWNER: before.buildOwner, GOMAXPROCS: String(maxWorkers) };
  if (Object.keys(env).some(key => key.startsWith("URNETWORK_ANDROID_SDK_OUTPUT_LOCK_") && env[key])) fail("writer-cannot-inherit-output-lock-no-writer-spawn");
  let output; let errors; let child; let interrupted = false; let childStarted = false;
  let outcome = { childExitCode: null, signal: null };
  const ownership = { schemaVersion: 1, scope: "launched-process-group", nonce: randomUUID(), ownerPid: null,
    buildId: before.buildId, buildOwner: before.buildOwner, inputHash: before.inputHash,
    ready: false, ownerClosed: false, ownerExitCode: null, ownerSignal: null,
    groupJoined: false, complete: false, orphaned: false, escalated: false, failure: null };
  const signals = dependencies.signals ?? process;
  const stop = signal => {
    if (child?.connected) child.send({ nonce: ownership.nonce, action: "stop", signal }, error => {
      if (error) ownership.failure ??= "owner-channel-failed";
    });
  };
  const handlers = new Map(OWNER_SIGNALS.map(signal => [signal, () => {
    interrupted = true;
    stop(signal);
  }]));
  const startedAtUnixMs = (dependencies.now ?? Date.now)();
  try {
    requireArtifactPaths(binding, paths);
    output = openSync(options.stdout, "wx", 0o600); errors = openSync(options.stderr, "wx", 0o600);
    for (const [signal, handler] of handlers) signals.on(signal, handler);
    try {
      child = (dependencies.spawn ?? spawn)(process.execPath, [fileURLToPath(import.meta.url), "--owned-gradle", ownership.nonce,
        options["build-id"], String(profileRate), options["memory-profile"], String(maxWorkers)],
      { cwd: workingDirectory, env, detached: true, stdio: ["ignore", output, errors, "ipc"] });
      ownership.ownerPid = child.pid ?? null;
      await new Promise(finish => {
        let ownerExit; let ownerDisconnected = false;
        const finishOwner = (exitCode, signal) => {
          if (ownership.ownerClosed) return;
          ownership.ownerClosed = true; ownership.ownerExitCode = exitCode; ownership.ownerSignal = signal; finish();
        };
        const joinOwner = () => {
          if (ownerExit && ownerDisconnected) finishOwner(ownerExit.exitCode, ownerExit.signal);
        };
        child.on("message", message => {
          if (message?.nonce !== ownership.nonce || message.ownerPid !== child.pid) {
            ownership.failure ??= "owner-message-unbound"; stop("SIGTERM"); return;
          }
          switch (message.type) {
            case "ready":
              if (ownership.ready) { ownership.failure ??= "owner-ready-repeated"; stop("SIGTERM"); break; }
              ownership.ready = true;
              if (interrupted) stop("SIGTERM");
              else child.send({ nonce: ownership.nonce, action: "start" }, error => {
                if (error) { ownership.failure ??= "owner-channel-failed"; stop("SIGTERM"); }
              });
              break;
            case "started": childStarted = true; break;
            case "exited": outcome = { childExitCode: message.childExitCode, signal: message.signal }; break;
            case "stopping":
              if (message.cause === "orphaned") ownership.orphaned = true;
              else if (message.cause === "interrupted") interrupted = true;
              else ownership.failure ??= message.cause;
              break;
            case "escalating": ownership.escalated = true; break;
            case "complete":
              ownership.complete = message.childStarted === childStarted &&
                message.childExitCode === outcome.childExitCode && message.signal === outcome.signal;
              if (!ownership.complete) ownership.failure ??= "owner-completion-unbound";
              child.send({ nonce: ownership.nonce, action: "release" }, error => {
                if (error) ownership.failure ??= "owner-channel-failed";
              });
              break;
            default: ownership.failure ??= "owner-message-invalid"; stop("SIGTERM");
          }
        });
        child.once("error", () => { ownership.failure ??= "owner-spawn-failed"; });
        // Output uses inherited file descriptors, not parent-owned pipes. Reaping
        // plus actual IPC termination joins this owner even if manual disconnect
        // prevents Node from emitting close. Group absence is proved separately.
        child.once("exit", (exitCode, signal) => { ownerExit = { exitCode, signal }; joinOwner(); });
        child.once("disconnect", () => {
          ownerDisconnected = true;
          if (!ownership.complete) ownership.failure ??= "owner-channel-failed";
          joinOwner();
        });
        child.once("close", finishOwner); // A refused owner spawn need not emit exit.
      });
      // Observation only after reaping: never signal a possibly reused group.
      if (Number.isSafeInteger(ownership.ownerPid) && ownership.ownerPid > 0) {
        const until = Date.now() + OWNER_JOIN_MS;
        do {
          try { process.kill(-ownership.ownerPid, 0); }
          catch (error) {
            if (error.code === "ESRCH") ownership.groupJoined = true;
            else ownership.failure ??= "owner-group-unproved";
            break;
          }
          if (Date.now() >= until) break;
          await delay(25);
        } while (true);
      }
      if (!ownership.groupJoined) ownership.failure ??= "owner-group-not-joined";
    } catch { ownership.failure ??= "owner-launch-failed"; }
  } finally {
    for (const [signal, handler] of handlers) signals.off(signal, handler);
    if (output !== undefined) closeSync(output); if (errors !== undefined) closeSync(errors);
  }
  const receipt = { type: "physical-native-writer", schemaVersion: 1, state: "terminal",
    eligible: childStarted && outcome.childExitCode === 0 && outcome.signal === null && !interrupted && ownership.ready &&
      ownership.ownerClosed && ownership.ownerExitCode === 0 && ownership.ownerSignal === null &&
      ownership.groupJoined && ownership.complete && !ownership.orphaned && !ownership.escalated && !ownership.failure,
    root: before.root, buildId: before.buildId, buildOwner: before.buildOwner, inputHash: before.inputHash,
    profileRate, memoryProfile: options["memory-profile"], maxWorkers, workingDirectory, arguments: args,
    before: realpathSync(options.before), beforeSha256, sourceHashes,
    startedAtUnixMs, completedAtUnixMs: (dependencies.now ?? Date.now)(), childStarted, interrupted, ...outcome, ownership,
    stdout: hashNativeInputFile(options.stdout), stderr: hashNativeInputFile(options.stderr) };
  publish(options.receipt, receipt, binding);
  return receipt;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv[2] === "--owned-gradle") {
      if (process.argv.length !== 8) fail("private-writer-owner-arguments-required");
      await runGradleOwner(process.argv[3], nativeWriterArguments(process.argv[4], Number(process.argv[5]), process.argv[6], Number(process.argv[7])));
    } else {
      const receipt = await runNativeWriter(parseArgs(process.argv.slice(2)));
      process.stdout.write(`${JSON.stringify({ eligible: receipt.eligible,
        classification: receipt.eligible ? "NATIVE_WRITER_COMPLETE" : "NATIVE_WRITER_FAILED" })}\n`);
      if (!receipt.eligible) process.exitCode = 2;
    }
  } catch (error) { process.stderr.write(`native writer failed: ${reason(error)}\n`); process.exitCode = 2; }
}
