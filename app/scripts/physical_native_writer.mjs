#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, openSync, realpathSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { hashNativeInputFile, nativeProvenanceReason, nativeWriterArguments, nativeWriterSourceHashes,
  requireNativeBeforeWriter } from "./physical_native_provenance.mjs";

class WriterError extends Error {}
const fail = reason => { throw new WriterError(reason); };
const reason = error => error instanceof WriterError ? error.message : nativeProvenanceReason(error);

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
  const signals = dependencies.signals ?? process;
  const handlers = new Map(["SIGINT", "SIGTERM", "SIGHUP"].map(signal => [signal, () => {
    interrupted = true;
    if (Number.isInteger(child?.pid) && child.pid > 0) (dependencies.terminate ?? ((processId, value) => {
      try { process.kill(-processId, value); } catch (error) { if (error.code !== "ESRCH") throw error; }
    }))(child.pid, signal);
  }]));
  const startedAtUnixMs = (dependencies.now ?? Date.now)();
  try {
    requireArtifactPaths(binding, paths);
    output = openSync(options.stdout, "wx", 0o600); errors = openSync(options.stderr, "wx", 0o600);
    for (const [signal, handler] of handlers) signals.on(signal, handler);
    try {
      child = (dependencies.spawn ?? spawn)(join(workingDirectory, "gradlew"), args,
        { cwd: workingDirectory, env, detached: true, stdio: ["ignore", output, errors] });
      outcome = await new Promise(finish => {
        child.once("spawn", () => { childStarted = true; });
        child.once("error", () => finish({ childExitCode: null, signal: null }));
        child.once("close", (childExitCode, signal) => finish({ childExitCode, signal }));
      });
    } catch { outcome = { childExitCode: null, signal: null }; }
  } finally {
    for (const [signal, handler] of handlers) signals.off(signal, handler);
    if (output !== undefined) closeSync(output); if (errors !== undefined) closeSync(errors);
  }
  const receipt = { type: "physical-native-writer", schemaVersion: 1, state: "terminal",
    eligible: childStarted && outcome.childExitCode === 0 && outcome.signal === null && !interrupted,
    root: before.root, buildId: before.buildId, buildOwner: before.buildOwner, inputHash: before.inputHash,
    profileRate, memoryProfile: options["memory-profile"], maxWorkers, workingDirectory, arguments: args,
    before: realpathSync(options.before), beforeSha256, sourceHashes,
    startedAtUnixMs, completedAtUnixMs: (dependencies.now ?? Date.now)(), childStarted, interrupted, ...outcome,
    stdout: hashNativeInputFile(options.stdout), stderr: hashNativeInputFile(options.stderr) };
  publish(options.receipt, receipt, binding);
  return receipt;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const receipt = await runNativeWriter(parseArgs(process.argv.slice(2)));
    process.stdout.write(`${JSON.stringify({ eligible: receipt.eligible,
      classification: receipt.eligible ? "NATIVE_WRITER_COMPLETE" : "NATIVE_WRITER_FAILED" })}\n`);
    if (!receipt.eligible) process.exitCode = 2;
  } catch (error) { process.stderr.write(`native writer failed: ${reason(error)}\n`); process.exitCode = 2; }
}
