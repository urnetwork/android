#!/usr/bin/env node

// Copy only the completed diagnostic receipt's fixed app artifact. Stream raw
// bytes directly to an exclusively created 0600 file; never return/log them.
import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, fstatSync, linkSync, lstatSync, openSync, readFileSync, unlinkSync } from "node:fs";
import { dirname, isAbsolute } from "node:path";
import { pathToFileURL } from "node:url";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { DIAGNOSTIC_VERBS, diagnosticIdentity, diagnosticSessionValid } from "./physical_diagnostic_identity.mjs";

const VERBS = DIAGNOSTIC_VERBS;
const SAFE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
class CopyError extends Error {}
const fail = reason => { throw new CopyError(reason); };

export function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !["serial", "receipt", "output"].includes(key) || options[key] !== undefined ||
        !argv[i + 1] || argv[i + 1].startsWith("--")) fail("explicit-diagnostic-copy-arguments-required");
    options[key] = argv[i + 1];
  }
  if (!options.serial || !isAbsolute(options.receipt ?? "") || !isAbsolute(options.output ?? "")) fail("explicit-diagnostic-copy-arguments-required");
  return options;
}

export function copyDiagnosticArtifact(options, dependencies = {}) {
  parseArgs(Object.entries(options).flatMap(([key, value]) => [`--${key}`, value]));
  let binding;
  try { binding = prepareArtifactDirectory(dirname(options.output)); requireArtifactPaths(binding, [options.receipt, options.output]); }
  catch (error) { fail(artifactDirectoryReason(error)); }
  let receipt;
  try {
    const file = lstatSync(options.receipt);
    if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600 || file.size > 4096) fail("private-diagnostic-receipt-required");
    receipt = JSON.parse(readFileSync(options.receipt, "utf8"));
  } catch (error) { if (error instanceof CopyError) throw error; fail("private-diagnostic-receipt-required"); }
  const before = receipt?.initial;
  const status = receipt?.status;
  let wire;
  try { wire = diagnosticIdentity(before?.sessionId, receipt?.verb, receipt?.label, receipt?.commandId); }
  catch { fail("completed-bound-diagnostic-receipt-required"); }
  if (receipt?.schema !== 2 || receipt.type !== "physical-diagnostic-command" || receipt.eligible !== true ||
      receipt.serialHash !== createHash("sha256").update(options.serial).digest("hex") ||
      !Object.hasOwn(VERBS, receipt.verb) || !SAFE.test(receipt.label ?? "") ||
      !diagnosticSessionValid(status?.sessionId) || status.sessionId !== before?.sessionId ||
      JSON.stringify(receipt.wire) !== JSON.stringify(wire) || status?.commandId !== wire.commandId ||
      !Number.isSafeInteger(status?.pid) || status.pid <= 0 || status.pid !== before?.pid ||
      !SAFE.test(status?.commandId ?? "") || status.commandId === before?.commandId ||
      status.state !== "complete" || status.phase !== `${receipt.verb}-${wire.label}` ||
      !["ready", "complete"].includes(before?.state) ||
      !Number.isFinite(before?.elapsedMs) || before.elapsedMs < 0 ||
      !Number.isFinite(status?.elapsedMs) || status.elapsedMs < before.elapsedMs ||
      ["connected", "tunnelStarted", "provideEnabled"].some(key => typeof status[key] !== "boolean" || status[key] !== before[key]) ||
      typeof status.transportMode !== "string" || !status.transportMode || status.transportMode !== before.transportMode) fail("completed-bound-diagnostic-receipt-required");
  // Check existence including dangling symlinks; do not repair/adopt output.
  try { lstatSync(options.output); fail("fresh-diagnostic-output-required"); }
  catch (error) { if (error?.code !== "ENOENT") throw error; }
  const temporary = `${options.output}.pending-${randomUUID()}`;
  requireArtifactPaths(binding, [temporary]);
  const descriptor = openSync(temporary, "wx", 0o600);
  const original = fstatSync(descriptor);
  try {
    requireArtifactPaths(binding, [options.receipt, options.output, temporary]);
    const result = (dependencies.adb ?? ((args, fd) => spawnSync("adb", args,
      { stdio: ["ignore", fd, "pipe"], timeout: 10_000, maxBuffer: 4096 })))([
      "-s", options.serial, "exec-out", "run-as", "com.bringyour.network", "cat",
      `files/acceptance/${wire.artifactName}`,
    ], descriptor);
    if (result?.status !== 0 || result.error || result.signal) fail("diagnostic-copy-failed");
    const file = fstatSync(descriptor);
    if (!file.isFile() || file.uid !== process.getuid() || (file.mode & 0o7777) !== 0o600) fail("private-diagnostic-output-required");
    if (file.size <= 0) fail("empty-diagnostic-output");
    requireArtifactPaths(binding, [options.output, temporary]);
    const named = lstatSync(temporary);
    if (!named.isFile() || named.dev !== original.dev || named.ino !== original.ino) fail("diagnostic-output-replaced");
    linkSync(temporary, options.output);
    return { type: "physical-diagnostic-copy", schema: 1, copied: true, verb: receipt.verb,
      bytes: file.size, mode: "600" };
  } finally {
    closeSync(descriptor);
    // Only unlink our still-owned staging inode, never a substituted artifact.
    try {
      requireArtifactPaths(binding, [temporary]);
      const file = lstatSync(temporary);
      if (file.isFile() && file.dev === original.dev && file.ino === original.ino) unlinkSync(temporary);
    } catch { /* preserve the original failure and any replaced private evidence */ }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { process.stdout.write(`${JSON.stringify(copyDiagnosticArtifact(parseArgs(process.argv.slice(2))))}\n`); }
  catch (error) {
    process.stderr.write(`diagnostic copy failed: ${error instanceof CopyError ? error.message : "diagnostic-copy-unavailable"}\n`);
    process.exitCode = 2;
  }
}
