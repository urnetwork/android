#!/usr/bin/env node

// The fixed diagnostic command transaction. It neither creates app storage nor
// starts/restarts an app, changes its role, or reads/copies diagnostic contents.
import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, openSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { pathToFileURL } from "node:url";
import { prepareArtifactDirectory, requireArtifactPaths, artifactDirectoryReason } from "./physical_artifact_directory.mjs";
import { checkInstrumentationCommandSession, statusReadFailureMetadata } from "./physical_collector_session.mjs";
import { DIAGNOSTIC_VERBS, diagnosticIdentity, diagnosticSessionValid } from "./physical_diagnostic_identity.mjs";

const PACKAGE = "com.bringyour.network";
const DIRECTORY = "files/acceptance";
const COMMAND = `${DIRECTORY}/physical-command`;
const VERBS = DIAGNOSTIC_VERBS;
const LABEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
const TIMEOUT_MS = 30_000;
class DiagnosticError extends Error {
  constructor(reason, statusRead) { super(reason); this.statusRead = statusRead; }
}
const fail = (reason, cause) => { throw new DiagnosticError(reason, statusReadFailureMetadata(cause)); };
const reason = error => error instanceof DiagnosticError ? error.message : "diagnostic-evidence-unavailable";
// adb shell joins argv for a second shell parse. The inner script therefore
// needs literal remote quoting even though spawnSync itself uses an argv array.
const quote = value => `'${value.replaceAll("'", "'\\''")}'`;

export function parseArgs(argv) {
  const keys = ["serial", "owner", "command-id", "verb", "label", "output"];
  const options = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !keys.includes(key) || options[key] !== undefined || !argv[i + 1] || argv[i + 1].startsWith("--")) {
      fail("explicit-diagnostic-arguments-required");
    }
    options[key] = argv[i + 1];
  }
  if (keys.some(key => !options[key]) || !LABEL.test(options.label) || !LABEL.test(options["command-id"]) ||
      !Object.hasOwn(VERBS, options.verb) || !isAbsolute(options.owner) || !isAbsolute(options.output)) {
    fail("explicit-diagnostic-arguments-required");
  }
  return options;
}

export function diagnosticPublicationScript(options) {
  if (!LABEL.test(options.label) || !LABEL.test(options["command-id"]) || !Object.hasOwn(VERBS, options.verb)) {
    fail("explicit-diagnostic-arguments-required");
  }
  const [kind, extension] = VERBS[options.verb];
  const artifact = `${DIRECTORY}/physical-${kind}-${options.label}.${extension}`;
  const temporary = `${COMMAND}.${options["command-id"]}`;
  // One noclobber open: precreating with ': > temporary' would make the next
  // redirect fail, even after repairing the remote-shell quoting.
  return `set -eu\numask 077\ntest -d ${DIRECTORY} || exit 20\n` +
    `test ! -L ${DIRECTORY} || exit 21\ntest -f ${DIRECTORY}/physical-status || exit 22\n` +
    `test ! -e ${artifact} && test ! -L ${artifact} || exit 23\n` +
    `set -C\ncat > ${temporary} || exit 24\n` +
    `test -s ${temporary} || exit 24\nmv ${temporary} ${COMMAND} || exit 25\n`;
}

function reserveDiagnostic(binding, verb, wire) {
  for (const [kind, value] of [["command", wire.commandId], [`label-${verb}`, wire.label]]) {
    const path = join(binding.directory, `diagnostic-${kind}-${value}.attempt.json`);
    requireArtifactPaths(binding, [path]);
    let fd;
    try { fd = openSync(path, "wx", 0o600); }
    catch (error) { fail(error?.code === "EEXIST" ? `diagnostic-${kind === "command" ? "command" : "label"}-already-attempted` :
      "diagnostic-attempt-reservation-failed"); }
    try { writeFileSync(fd, '{"schema":1,"type":"physical-diagnostic-attempt","eligible":false}\n'); }
    finally { closeSync(fd); }
  }
  // Reservations survive success, failure and interruption. A later command
  // cannot relabel/retry the same attempted diagnostic or reuse its command ID.
}

export async function runDiagnosticCommand(options, dependencies = {}) {
  // Validate programmatic calls before any artifact or device operation too.
  parseArgs(Object.entries(options).flatMap(([key, value]) => [`--${key}`, value]));
  let binding;
  try { binding = prepareArtifactDirectory(dirname(options.output)); requireArtifactPaths(binding, [options.owner, options.output]); }
  catch (error) { fail(artifactDirectoryReason(error)); }
  if (existsSync(options.output)) fail("fresh-diagnostic-receipt-required");
  const pending = `${options.output}.pending-${randomUUID()}`;
  requireArtifactPaths(binding, [pending]);
  const descriptor = openSync(pending, "wx", 0o600);
  const check = () => (dependencies.checkSession ?? checkInstrumentationCommandSession)(
    { owner: options.owner, serial: options.serial }, dependencies.session);
  const now = dependencies.now ?? performance.now.bind(performance);
  const wallNow = dependencies.wallNow ?? Date.now;
  const pause = dependencies.sleep ?? sleep;
  const deadline = now() + TIMEOUT_MS;
  try {
    let initial;
    try { initial = check(); } catch (error) { fail("live-bound-instrumentation-session-required", error); }
    if (!diagnosticSessionValid(initial.sessionId)) fail("diagnostic-session-identity-required");
    const wire = diagnosticIdentity(initial.sessionId, options.verb, options.label, options["command-id"]);
    const expectedPhase = `${options.verb}-${wire.label}`;
    if (!["ready", "complete"].includes(initial.state) || initial.commandId === wire.commandId) fail("idle-session-and-fresh-command-required");
    reserveDiagnostic(binding, options.verb, wire);
    requireArtifactPaths(binding, [options.owner, options.output, pending]);
    const args = ["-s", options.serial, "shell", "-T", "run-as", PACKAGE, "sh", "-c",
      quote(diagnosticPublicationScript({ ...options, label: wire.label, "command-id": wire.commandId }))];
    const result = (dependencies.adb ?? ((argv, input) => spawnSync("adb", argv, {
      input, encoding: "utf8", timeout: 5000, maxBuffer: 4096,
    })))(args, `${wire.commandId}|${options.verb}|${wire.label}\n`);
    const failures = { 20: "diagnostic-session-directory-missing", 21: "diagnostic-session-directory-untrusted",
      22: "diagnostic-session-status-missing", 23: "diagnostic-artifact-already-exists",
      24: "diagnostic-exclusive-write-failed", 25: "diagnostic-command-publish-failed" };
    if (result?.status !== 0 || result.error || result.signal) fail(failures[result?.status] ?? "diagnostic-command-publication-failed");
    let previousElapsedMs = initial.elapsedMs;
    let acknowledged = false;
    for (;;) {
      let status;
      try { status = check(); } catch (error) { fail("live-bound-instrumentation-session-required", error); }
      if (status.sessionId !== initial.sessionId || status.pid !== initial.pid || status.elapsedMs < previousElapsedMs ||
          ["connected", "tunnelStarted", "provideEnabled", "transportMode"].some(key => status[key] !== initial[key])) fail("diagnostic-session-changed");
      previousElapsedMs = status.elapsedMs;
      if (![initial.commandId, wire.commandId].includes(status.commandId)) fail("concurrent-diagnostic-command-observed");
      if (acknowledged && status.commandId !== wire.commandId) fail("diagnostic-session-changed");
      if (status.commandId === wire.commandId) {
        acknowledged = true;
        if (status.phase !== expectedPhase || !["running", "complete"].includes(status.state)) fail("diagnostic-command-rejected");
        if (status.state === "complete") {
          const report = { schema: 2, type: "physical-diagnostic-command", eligible: true,
            serialHash: createHash("sha256").update(options.serial).digest("hex"),
            verb: options.verb, label: options.label, commandId: options["command-id"], wire,
            hostTimeUnixMs: wallNow(), initial, status };
          requireArtifactPaths(binding, [options.output, pending]);
          writeFileSync(descriptor, `${JSON.stringify(report)}\n`);
          linkSync(pending, options.output);
          return report;
        }
      }
      if (now() >= deadline) fail("diagnostic-command-acknowledgment-timeout");
      await pause(Math.min(250, deadline - now()));
    }
  } finally {
    closeSync(descriptor);
    unlinkSync(pending);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await runDiagnosticCommand(parseArgs(process.argv.slice(2)));
    process.stdout.write('{"eligible":true,"classification":"DIAGNOSTIC_COMMAND_COMPLETE"}\n');
  } catch (error) {
    process.stderr.write(`diagnostic command failed: ${reason(error)}\n`);
    if (error instanceof DiagnosticError && error.statusRead) process.stderr.write(`${JSON.stringify(error.statusRead)}\n`);
    process.exitCode = 2;
  }
}
