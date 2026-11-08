// Test-only custody adapter. Production command behavior is supplied by the
// existing workload watchdog; no SDK, lock, metadata or device policy changes.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { rmSync } from "node:fs";
import { runCommand } from "./physical_workload_receipt.mjs";

// Fixture commands use the existing group watchdog; output is bounded and raw
// child text never appears in timeout/ownership diagnostics.
export function fixtureProcessIsLive(pid) {
  try { process.kill(pid, 0); return true; }
  catch (error) { if (error.code === "ESRCH") return false; throw error; }
}

export function fixtureCommandDiagnostic(result) {
  return JSON.stringify({ type: "native-fixture-command", closed: result.closed, exitCode: result.exitCode,
    signal: result.signal, timedOut: result.timedOut, interrupted: result.interrupted,
    joinTimedOut: result.joinTimedOut, orphaned: result.orphaned, launchError: result.launchError, errorCode: result.error?.code ?? null, groupJoined: result.groupJoined,
    output: result.output });
}

export async function runFixtureCommand(f, command, args, options = {}, dependencies = {}) {
  const capture = () => ({ bytes: 0, retained: 0, chunks: [], hash: createHash("sha256") });
  const stdout = capture(), stderr = capture();
  const limits = { timeoutMs: options.timeout ?? 20000, killAfterMs: 1000, joinAfterMs: 5000 };
  const keep = (output, data) => {
    if (output.sealed) return; // Continue draining late pipe data without mutating a sealed/unknown digest.
    output.bytes += data.length; output.hash.update(data);
    const count = Math.min(data.length, 64 * 1024 - output.retained);
    if (count > 0) { output.chunks.push(Buffer.from(data.subarray(0, count))); output.retained += count; }
  };
  const owner = { done: null, joined: false, result: null };
  f.commandOwners.push(owner);
  owner.done = (async () => {
    const fate = await runCommand([command, ...args], options.env ?? process.env,
      limits, { ...dependencies,
        spawn(executable, arguments_, settings) {
          const child = (dependencies.spawn ?? spawn)(executable, arguments_, { ...settings, cwd: options.cwd, stdio: ["ignore", "pipe", "pipe"] });
          child.stdout.on("data", data => keep(stdout, data));
          child.stderr.on("data", data => keep(stderr, data));
          return child;
        } });
    const isLive = dependencies.isLive ?? fixtureProcessIsLive;
    const validPid = Number.isInteger(fate.pid) && fate.pid > 0;
    const groupLive = () => { try { return isLive(-fate.pid); } catch { return true; } };
    const orphaned = fate.closed === true && fate.joinTimedOut !== true && validPid && groupLive();
    // runCommand joins timed-out/interrupted groups. A normal leader may have
    // detached its stdio from a surviving same-group child; retain that custody.
    const orphanJoined = !orphaned || await reapFixtureOrphanGroup(fate.pid, limits, dependencies);
    const groupJoined = fate.closed === true && fate.joinTimedOut !== true && orphanJoined &&
      (validPid ? !groupLive() : Boolean(fate.launchError));
    const summary = output => {
      output.sealed = true;
      const sha256 = fate.closed ? output.hash.digest("hex") : null;
      output.hash = null;
      return { bytes: output.bytes, sha256, complete: fate.closed, truncated: output.bytes > output.retained };
    };
    const result = { ...fate, status: fate.exitCode, groupJoined, orphaned, joinTimedOut: fate.joinTimedOut || !orphanJoined,
      error: fate.launchError ? { code: fate.launchError } : fate.timedOut ? { code: "ETIMEDOUT" } : undefined,
      stdout: Buffer.concat(stdout.chunks).toString("utf8"), stderr: Buffer.concat(stderr.chunks).toString("utf8"),
      output: { stdout: summary(stdout), stderr: summary(stderr) } };
    owner.result = result; owner.joined = groupJoined;
    const diagnostic = fixtureCommandDiagnostic(result);
    if (!groupJoined || orphaned || fate.timedOut || fate.interrupted || fate.launchError || result.output.stdout.truncated || result.output.stderr.truncated) {
      f.testContext.diagnostic(diagnostic);
    }
    assert.equal(groupJoined, true, `NATIVE_FIXTURE_GROUP_NOT_JOINED ${diagnostic}`);
    assert.equal(orphaned, options.expectedOrphan === true, diagnostic);
    assert.equal(fate.interrupted, false, diagnostic);
    assert.equal(fate.launchError, null, diagnostic);
    assert.equal(fate.timedOut, options.expectedTimeout === true, diagnostic);
    if (options.expectedTimeout !== true) assert.equal(fate.signal, null, diagnostic);
    assert.equal(result.output.stdout.truncated || result.output.stderr.truncated, false, diagnostic);
    return result;
  })();
  return owner.done;
}

// A normal leader close can leave same-group work after closing its pipes.
// Reuse the command's bounded TERM/KILL/join settings for that remaining group.
export async function reapFixtureOrphanGroup(pid, limits, dependencies = {}) {
  const isLive = dependencies.isLive ?? fixtureProcessIsLive;
  const setTimer = dependencies.setTimer ?? setTimeout, clearTimer = dependencies.clearTimer ?? clearTimeout;
  const live = () => { try { return isLive(-pid); } catch { return true; } };
  const signal = name => { try { (dependencies.kill ?? process.kill)(-pid, name); } catch {} };
  if (!live()) return true;
  signal("SIGTERM");
  let killTimer, joinTimer, pollTimer, finished = false;
  return await new Promise(resolve => {
    const finish = joined => {
      if (finished) return;
      finished = true;
      clearTimer(killTimer); clearTimer(joinTimer); clearTimer(pollTimer);
      resolve(joined);
    };
    const poll = () => {
      if (finished) return;
      if (!live()) finish(true);
      else pollTimer = setTimer(poll, 25);
    };
    killTimer = setTimer(() => { signal("SIGKILL"); }, limits.killAfterMs);
    joinTimer = setTimer(() => { finish(!live()); }, limits.killAfterMs + limits.joinAfterMs);
    poll();
  });
}

export function fixtureCommandOwner(testContext, root) {
  const owner = { testContext, commandOwners: [], beforeCleanup: [] };
  let cleanupPromise;
  owner.cleanup = () => cleanupPromise ??= (async () => {
    // Registered commands keep the fixture alive through group join, including
    // failures. An unresolved group keeps its files for its cleanup owner.
    for (const finish of owner.beforeCleanup) await finish();
    await Promise.allSettled(owner.commandOwners.map(command => command.done));
    const requireJoined = () => {
      for (const command of owner.commandOwners) assert.equal(command.joined, true,
        `NATIVE_FIXTURE_CLEANUP_BEFORE_JOIN ${command.result ? fixtureCommandDiagnostic(command.result) : "unresolved-command"}`);
    };
    requireJoined();
    if (owner.beforeRemove) await owner.beforeRemove();
    requireJoined();
    rmSync(root, { recursive: true, force: true });
  })();
  testContext.after(owner.cleanup);
  return owner;
}
