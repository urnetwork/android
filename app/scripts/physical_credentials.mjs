#!/usr/bin/env node

// Host-only staging owner. Credentials travel only through captured parser
// stdout and adb stdin, never command arguments, logs or the evidence JSON.
import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, lstatSync, openSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parserOutcome, requireCredentialParserPreflight } from "./physical_credentials_preflight.mjs";
import { artifactDirectoryReason, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";
import { credentialOwnershipPublication, prepareCredentialOwnership, recordCredentialOwnership } from "./physical_credential_ownership.mjs";

const PACKAGE = "com.bringyour.network";
const DESTINATION = "files/acceptance/credentials";
const READER = fileURLToPath(new URL("../../../tests/read-tests-config.sh", import.meta.url));
const SCHEMA_KEYS = Object.freeze({
  "user-pass": ["user", "pass"],
  "data-plane-account": ["data_plane_account.email", "data_plane_account.password"],
});
const MAX_BYTES = 64 * 1024;
// Kotlin/JVM Char.isWhitespace: Character.isWhitespace || isSpaceChar.
// Not JS trim(): for example U+001C is blank, whereas U+FEFF is not.
const KOTLIN_BLANK = /^[\u0009-\u000d\u001c-\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]*$/u;
class StagingError extends Error {}
const fail = (reason) => { throw new StagingError(reason); };
const quote = (value) => `'${value.replaceAll("'", "'\\''")}'`;
const selectedKeys = (schema) => {
  if (!Object.hasOwn(SCHEMA_KEYS, schema)) fail("explicit-credential-schema-required");
  return SCHEMA_KEYS[schema];
};

export function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 1) {
    if (["--inspect-only", "--sentinel-only"].includes(argv[i])) {
      if (options.inspectOnly || options.sentinelOnly) fail("invalid-arguments");
      options[argv[i] === "--inspect-only" ? "inspectOnly" : "sentinelOnly"] = true;
      continue;
    }
    const key = argv[i]?.slice(2);
    if (!argv[i]?.startsWith("--") || !["serial", "config", "schema", "output", "preflight", "artifact-dir",
      "ownership", "native-inputs", "label", "build-id"].includes(key) ||
        !argv[i + 1] || argv[i + 1].startsWith("--") || options[key] !== undefined) fail("invalid-arguments");
    options[key] = argv[i + 1];
    i += 1;
  }
  if (!options.serial || !options.output || (options.inspectOnly || options.sentinelOnly
    ? options.config !== undefined || options.schema !== undefined || options.preflight !== undefined ||
      options["artifact-dir"] !== undefined || ["ownership", "native-inputs", "label", "build-id"].some(key => options[key] !== undefined) : !options.config)) {
    fail("serial-config-output-required-or-diagnostic-mode");
  }
  if (!options.inspectOnly && !options.sentinelOnly) selectedKeys(options.schema);
  const ownershipKeys = ["ownership", "native-inputs", "label", "build-id"];
  if (ownershipKeys.some(key => options[key] !== undefined) && ownershipKeys.some(key => !options[key])) {
    fail("explicit-credential-ownership-context-required");
  }
  return options;
}

export function credentialLineStructure(payload) {
  if (!Buffer.isBuffer(payload) || payload.length > MAX_BYTES) fail("bounded-credential-buffer-required");
  let text;
  try { text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(payload); }
  catch { fail("credential-file-invalid-utf8"); }
  // BufferedReader.readLine / Kotlin File.readLines: CR, LF and CRLF end a
  // record; an unterminated final record counts, a terminator alone adds none.
  const lines = text.length ? text.split(/\r\n|\r|\n/) : [];
  if (/[\r\n]$/.test(text)) lines.pop();
  return { lineCount: lines.length, nonblankLineCount: lines.filter((line) => !KOTLIN_BLANK.test(line)).length,
    byteLength: payload.length, newlineCount: payload.reduce((count, byte) => count + Number(byte === 10), 0) };
}

export function credentialPayload(values) {
  if (!Array.isArray(values) || values.length !== 2 || values.some((value) =>
    typeof value !== "string" || KOTLIN_BLANK.test(value) || /[\r\n\0]/.test(value))) fail("two-raw-nonblank-values-required");
  // Do not trim, unquote, JSON-encode, escape, or interpolate either value.
  const payload = Buffer.from(`${values[0]}\n${values[1]}`, "utf8");
  if (payload.length > MAX_BYTES) fail("credential-payload-too-large");
  return payload;
}

function structure(payload) {
  const counts = credentialLineStructure(payload);
  return { lineCount: counts.lineCount, blankLineCount: counts.lineCount - counts.nonblankLineCount,
    byteLength: payload.length, mode: "600",
    sha256: createHash("sha256").update(payload).digest("hex") };
}

function parseStructure(output) {
  if (typeof output !== "string" || output.length > 256) return undefined;
  const fields = output.trim().split(/\s+/);
  if (fields.length !== 5 || !fields.slice(0, 3).every((value) => /^\d+$/.test(value)) ||
      !/^[0-7]{3,4}$/.test(fields[3]) || !/^[0-9a-f]{64}$/.test(fields[4])) return undefined;
  const [lineCount, blankLineCount, byteLength] = fields.slice(0, 3).map(Number);
  if (![lineCount, blankLineCount, byteLength].every(Number.isSafeInteger)) return undefined;
  return { lineCount, blankLineCount, byteLength, mode: fields[3], sha256: fields[4] };
}

// Hashes are needed for the in-memory equality check, never the persisted
// diagnosis. Substep records accept only fixed outcomes and numeric exit codes.
const reportStructure = (value) => value && ({ lineCount: value.lineCount, blankLineCount: value.blankLineCount,
  byteLength: value.byteLength, mode: value.mode });
const notRun = () => ({ outcome: "not-run", exitCode: null });
const unavailable = () => ({ outcome: "unavailable", exitCode: null });
const PUBLICATION_PHASES = ["create", "copy", "inspect"];
const publicationSteps = () => Object.fromEntries(["stage", "publish", ...PUBLICATION_PHASES, "destinationCleanup", "cleanup"].map((key) => [key, notRun()]));
function exitOutcome(code) {
  return Number.isInteger(code) && code >= 0 && code <= 255
    ? { outcome: code === 0 ? "ok" : "failed", exitCode: code } : unavailable();
}
function invokeStep(steps, name, invoke) {
  steps[name] = unavailable();
  if (name === "publish") {
    for (const phase of PUBLICATION_PHASES) steps[phase] = unavailable();
    steps.destinationCleanup = unavailable();
  }
  const result = invoke();
  steps[name] = exitOutcome(result?.status);
  return result;
}

const STAGING_FAILURES = Object.freeze({
  "files-guard": "files-symlink-rejected",
  "acceptance-guard": "acceptance-symlink-rejected",
  "directory-create": "acceptance-directory-create-failed",
  "directory-mode": "acceptance-directory-mode-failed",
  "destination-absent": "credential-destination-present",
  "destination-guard": "credential-destination-symlink-rejected",
  "temporary-create": "staging-exclusive-create-failed",
  copy: "staging-copy-failed",
  "temporary-mode": "staging-mode-failed",
  inspect: "staging-inspection-failed",
  complete: "staging-terminal-failed",
});

// Never persist stderr, paths, package/serial strings, or payload bytes. A
// phase is attributed only to one exact terminal marker matching the joined
// shell exit. Old/absent/conflicting markers remain explicitly unattributed.
export function credentialStageDiagnostic(result) {
  const stderr = typeof result?.stderr === "string" && result.stderr.length <= 4096 ? result.stderr : "";
  const markers = stderr.split("\n").filter(line => line.startsWith("staging-step "));
  const match = markers.length === 1 ? /^staging-step ([a-z-]+) ([0-9]{1,3})$/.exec(markers[0]) : null;
  const verified = !!match && Object.hasOwn(STAGING_FAILURES, match[1]) && Number(match[2]) <= 255 &&
    result?.status === Number(match[2]) && (result.status !== 0 || match[1] === "complete") && !result?.error && !result?.signal;
  const remainder = stderr.split("\n").filter(line => !line.startsWith("staging-step ")).join("\n");
  const outcome = parserOutcome({ ...result, stderr: remainder });
  if (!result?.error && !result?.signal) {
    if (/^run-as:.*(?:not debuggable|permission denied|operation not permitted)/im.test(remainder)) outcome.stderrCategory = "run-as-denied";
    else if (/^run-as:.*(?:unknown package|package.*not found)/im.test(remainder)) outcome.stderrCategory = "run-as-package-unavailable";
    else if (/^(?:adb: )?error:.*(?:unauthorized|offline|no devices|device .*not found|device not found)/im.test(remainder)) {
      outcome.stderrCategory = "adb-device-unavailable";
    }
  }
  const reason = verified ? result.status === 0 && match[1] === "complete" ? "staging-complete" : STAGING_FAILURES[match[1]] :
    outcome.stderrCategory !== "none" && outcome.stderrCategory !== "other-stderr" ? `staging-${outcome.stderrCategory}` :
      result?.status === 0 ? "staging-terminal-unproven" : "staging-failure-unattributed";
  return { ...outcome, marker: verified ? "verified" : markers.length ? "invalid" : "missing",
    phase: verified ? match[1] : null, reason };
}

// Android app sandboxes may prohibit hard links. Noclobber creates the final
// regular file exclusively; bytes are copied only through the resulting FD.
// The caller must join and validate publication before starting a consumer.
// Identity checks use this shell's builtin test, not a stat child's /proc/self
// (that child may close extra FDs). Normal/error/signal finish is explicit before
// shell exit; the EXIT fallback still refuses deletion if its FD is unavailable.
// A later host process never guesses ownership from a recycled inode number.
const publishScript = (source, destination, inspection, retain = true) => `set -eu
umask 077
export LC_ALL=C
publication_step=create
publication_owned=0
publication_committed=0
publication_finish() {
  publication_status=$1
  trap - 0 HUP INT TERM
  set +e
  # Some shells lose a fatal redirection status while entering EXIT. An
  # unfinished transaction must never emit a successful phase completion.
  if [ "$publication_status" = 0 ] && [ "$publication_committed" != 1 ]; then publication_status=71; fi
  release_status=0
  if [ "$publication_owned" = 1 ] && { [ "$publication_committed" != 1 ] || [ '${retain ? "1" : "0"}' = 0 ]; }; then
    if [ -L ${destination} ]; then
      release_status=72
    elif [ -f ${destination} ]; then
      if ! [ /proc/self/fd/3 -ef /proc/self/fd/3 ]; then
        release_status=73
      elif [ ${destination} -ef /proc/self/fd/3 ]; then
        rm -f ${destination}
        release_status=$?
      else
        release_status=74
      fi
    elif [ -e ${destination} ]; then
      release_status=75
    fi
    printf 'publication-release %s\\n' "$release_status"
  fi
  printf 'publication-step %s %s\\n' "$publication_step" "$publication_status"
  if [ "$publication_owned" = 1 ]; then exec 3>&-; fi
  if [ "$publication_status" = 0 ] && [ "$release_status" != 0 ]; then exit "$release_status"; fi
  exit "$publication_status"
}
publication_require() { "$@" || publication_finish "$?"; }
trap 'publication_finish "$?"' 0
trap 'publication_finish 125' HUP INT TERM
publication_require test ! -e ${destination}
publication_require test ! -L ${destination}
set -C
exec 3> ${destination}
publication_owned=1
publication_require test /proc/self/fd/3 -ef /proc/self/fd/3
publication_require test -f ${destination}
publication_require test ! -L ${destination}
publication_require test ${destination} -ef /proc/self/fd/3
printf 'publication-owned\\n'
printf 'publication-step create 0\\n'
publication_step=copy
cat ${source} >&3 || publication_finish "$?"
printf 'publication-step copy 0\\n'
publication_step=inspect
publication_require test ! -L ${destination}
publication_require test ${destination} -ef /proc/self/fd/3
${inspection}
publication_require test ! -L ${destination}
publication_require test ${destination} -ef /proc/self/fd/3
publication_committed=1
publication_finish 0
`;

function publicationMetadata(result, steps) {
  for (const phase of PUBLICATION_PHASES) steps[phase] = unavailable();
  steps.destinationCleanup = unavailable();
  if (typeof result?.stdout !== "string" || result.stdout.length > 2048) return { metadata: "", owned: false };
  const markers = Object.fromEntries(PUBLICATION_PHASES.map((phase) => [phase, []]));
  let owners = 0; const releases = []; const metadata = []; const ownershipProofs = [];
  for (const line of result.stdout.split("\n")) {
    const match = /^publication-step (create|copy|inspect) ([0-9]{1,3})$/.exec(line);
    const release = /^publication-release ([0-9]{1,3})$/.exec(line);
    const ownership = /^publication-ownership ([0-9a-f]{64})$/.exec(line);
    if (match && Number(match[2]) <= 255) markers[match[1]].push(Number(match[2]));
    else if (line === "publication-owned") owners++;
    else if (release && Number(release[1]) <= 255) releases.push(Number(release[1]));
    else if (ownership) ownershipProofs.push(ownership[1]);
    else metadata.push(line);
  }
  let precedingFailure = false;
  for (const phase of PUBLICATION_PHASES) {
    steps[phase] = markers[phase].length === 1 ? exitOutcome(markers[phase][0])
      : precedingFailure && !markers[phase].length ? notRun() : unavailable();
    precedingFailure ||= steps[phase].outcome === "failed";
  }
  const completedWithoutRelease = Number.isInteger(result.status) && (
    result.status === 0 && owners === 1 && PUBLICATION_PHASES.every((phase) => steps[phase].exitCode === 0) ||
    owners === 0 && steps.create.outcome === "failed" && steps.create.exitCode === result.status);
  steps.destinationCleanup = releases.length === 1 ? exitOutcome(releases[0])
    : releases.length || !completedWithoutRelease ? unavailable() : notRun();
  return { metadata: metadata.join("\n"), owned: owners === 1,
    ownershipProof: ownershipProofs.length === 1 ? ownershipProofs[0] : undefined };
}

// Staged input has already excluded CR and blank scalar values. sed counts
// its logical LF records including an unterminated final record; wc -l does
// not. The read-only inspector below also handles noncanonical CR/CRLF files.
const inspect = (path, guard = "") => `
test -f ${path}${guard}
test ! -L ${path}${guard}
lines=$(sed -n '$=' ${path})${guard}
lines=\${lines:-0}
blanks=$(sed -n '/^[[:space:]]*$/=' ${path} | sed -n '$=')${guard}
blanks=\${blanks:-0}
bytes=$(wc -c < ${path})${guard}
mode=$(stat -c '%a' ${path})${guard}
digest=$(sha256sum ${path})${guard}
digest=\${digest%% *}
printf '%s %s %s %s %s\\n' "$lines" "$blanks" "$bytes" "$mode" "$digest"
`;

function requirePrivateOutput(output, directory, related = []) {
  let binding;
  try {
    binding = prepareArtifactDirectory(directory ?? dirname(output));
    requireArtifactPaths(binding, [output, ...related]);
  } catch (error) { fail(artifactDirectoryReason(error)); }
  if (existsSync(output)) fail("output-already-exists");
  return binding;
}

// Read-only diagnostic of the exact app-private file, including noncanonical
// CR/LF/CRLF input. Raw bytes are captured only in process memory, never logged
// or persisted. Its public result has counts only, deliberately no digest.
export function inspectPhysicalCredentialLines(options, dependencies = {}) {
  if (!options.serial || !options.output || options.sentinelOnly || options.config !== undefined || options.schema !== undefined) {
    fail("serial-output-required-for-inspection");
  }
  requirePrivateOutput(options.output);
  const invoke = dependencies.adb ?? ((args) => spawnSync("adb", args,
    { encoding: "buffer", timeout: 10_000, maxBuffer: MAX_BYTES }));
  const script = `set -eu\ntest -f ${DESTINATION}\ntest ! -L ${DESTINATION}\ncat ${DESTINATION}`;
  const response = invoke(["-s", options.serial, "shell", "-T", "run-as", PACKAGE, "sh", "-c", quote(script)]);
  if (response?.status !== 0 || !Buffer.isBuffer(response.stdout)) fail("credential-inspection-unavailable");
  let counts;
  try { counts = credentialLineStructure(response.stdout); }
  finally { response.stdout.fill(0); }
  const eligible = counts.lineCount === 2 && counts.nonblankLineCount === 2;
  const report = { type: "physical-credential-lines", schemaVersion: 1, hostTimeUnixMs: Date.now(), eligible,
    classification: eligible ? "CREDENTIAL_LINES_VALID" : "INVALID_CREDENTIAL_LINES", ...counts };
  writeFileSync(options.output, `${JSON.stringify(report)}\n`, { flag: "wx", mode: 0o600 });
  return report;
}

// Exported only so fake-ADB tests can execute the exact remote shell protocol.
export function credentialScripts(token, ownership) {
  if (!/^[A-Za-z0-9-]+$/.test(token)) fail("invalid-staging-token");
  const temporary = `${DESTINATION}.pending-${token}`;
  return {
    stage: `set -eu
umask 077
export LC_ALL=C
staging_step=files-guard
staging_complete=0
staging_finish() {
  staging_status=$1
  trap - 0 HUP INT TERM
  set +e
  if [ "$staging_status" = 0 ] && [ "$staging_complete" != 1 ]; then staging_status=71; fi
  printf 'staging-step %s %s\\n' "$staging_step" "$staging_status" >&2
  exit "$staging_status"
}
staging_require() { "$@" || staging_finish "$?"; }
trap 'staging_finish "$?"' 0
trap 'staging_finish 125' HUP INT TERM
staging_require test ! -L files
staging_step=acceptance-guard
staging_require test ! -L files/acceptance
staging_step=directory-create
staging_require mkdir -p files/acceptance
staging_step=directory-mode
staging_require chmod 700 files/acceptance
staging_step=destination-absent
staging_require test ! -e ${DESTINATION}
staging_step=destination-guard
staging_require test ! -L ${DESTINATION}
staging_step=temporary-create
set -C
exec 3> ${temporary}
printf 'staging-owned\\n'
staging_step=copy
cat >&3 || staging_finish "$?"
exec 3>&-
staging_step=temporary-mode
staging_require chmod 600 ${temporary}
staging_step=inspect
${inspect(temporary, ' || staging_finish "$?"')}
staging_step=complete
staging_complete=1
staging_finish 0`,
    publish: publishScript(temporary, DESTINATION, `source_digest=$(sha256sum ${temporary}) || publication_finish "$?"
source_digest=\${source_digest%% *}
source_bytes=$(wc -c < ${temporary}) || publication_finish "$?"
${inspect(DESTINATION, ' || publication_finish "$?"')}
publication_require test "$lines" = 2
publication_require test "$blanks" = 0
publication_require test "$mode" = 600
publication_require test "$bytes" = "$source_bytes"
publication_require test "$digest" = "$source_digest"
${credentialOwnershipPublication(ownership)}
`),
    cleanup: `set -eu
rm -f ${temporary}
`,
  };
}

export function stagePhysicalCredentials(options, dependencies = {}) {
  if (!options.serial || !options.config || !options.output || options.inspectOnly || options.sentinelOnly) fail("serial-config-output-required");
  const keys = selectedKeys(options.schema);
  const directoryBinding = requirePrivateOutput(options.output, options["artifact-dir"], options.preflight ? [options.preflight] : []);
  // This optional source/tool contract is mandatory in the physical runbooks.
  // Reject stale/failed evidence before even inspecting the real config.
  if (options.preflight !== undefined) {
    try { requireCredentialParserPreflight(options.preflight); }
    catch { fail("credential-parser-preflight-invalid"); }
  }
  const ownership = prepareCredentialOwnership(options, dependencies);
  const config = lstatSync(options.config);
  if (!config.isFile() || (config.mode & 0o777) !== 0o600 ||
      (typeof process.getuid === "function" && config.uid !== process.getuid())) fail("private-owned-config-required");
  const token = (dependencies.uuid ?? randomUUID)();
  const scripts = credentialScripts(token, ownership);
  const pendingOutput = `${options.output}.pending-${token}`;
  const descriptor = openSync(pendingOutput, "wx", 0o600);
  const invokeReader = dependencies.reader ?? ((key, schema) => spawnSync(READER, ["--schema", schema, "get", key], {
    env: { ...process.env, UR_ACCEPT_VAULT: options.config }, encoding: "utf8", timeout: 60_000, maxBuffer: MAX_BYTES,
  }));
  const invokeAdb = dependencies.adb ?? ((args, input) => spawnSync("adb", args, {
    input, encoding: "utf8", timeout: 10_000, maxBuffer: 4096,
  }));
  const adb = (script, input) => invokeAdb(["-s", options.serial, "shell", "-T", "run-as", PACKAGE,
    "sh", "-c", quote(script)], input);
  const report = { type: "physical-credential-staging", schemaVersion: 3, eligible: false,
    sourceSchema: options.schema, classification: "FAILED_CREDENTIAL_STAGING",
    reason: "credential-staging-unavailable", destinationOwned: false, expected: null, observed: null,
    parserOutcome: null, stageDiagnostic: null, steps: publicationSteps() };
  let payload;
  let temporaryOwned = false;
  let ownershipProof;
  try {
    const values = keys.map((key) => {
      let value;
      try { value = invokeReader(key, options.schema); }
      catch (error) { report.parserOutcome = parserOutcome({ error }); throw error; }
      if (value?.status !== 0 || typeof value.stdout !== "string") {
        report.parserOutcome = parserOutcome(value);
        fail("config-reader-failed");
      }
      return value.stdout;
    });
    payload = credentialPayload(values);
    const expected = structure(payload);
    report.expected = reportStructure(expected);
    try { requireArtifactPaths(directoryBinding, [options.output]); }
    catch (error) { fail(artifactDirectoryReason(error)); }
    let written;
    try { written = invokeStep(report.steps, "stage", () => adb(scripts.stage, payload)); }
    catch (error) { report.stageDiagnostic = credentialStageDiagnostic({ error }); throw error; }
    report.stageDiagnostic = credentialStageDiagnostic(written);
    // Only an exclusive open grants cleanup ownership. A failed guard/open
    // must not delete a pre-existing staging file belonging to another run.
    temporaryOwned = typeof written?.stdout === "string" && written.stdout.startsWith("staging-owned\n");
    if (written?.status !== 0) fail("device-staging-failed");
    if (!temporaryOwned) fail("device-staging-ownership-unproven");
    if (report.stageDiagnostic.marker !== "verified" || report.stageDiagnostic.phase !== "complete") {
      fail("device-staging-terminal-unproven");
    }
    let observed = parseStructure(written.stdout.slice("staging-owned\n".length)) ?? null;
    report.observed = reportStructure(observed);
    if (!observed || Object.keys(expected).some((key) => observed[key] !== expected[key])) {
      fail("device-credential-structure-mismatch");
    }
    const published = invokeStep(report.steps, "publish", () => adb(scripts.publish));
    const publication = publicationMetadata(published, report.steps);
    ownershipProof = publication.ownershipProof;
    report.destinationOwned = publication.owned;
    if (published?.status !== 0) fail("device-publication-failed");
    if (!publication.owned || PUBLICATION_PHASES.some((phase) => report.steps[phase].exitCode !== 0)) {
      fail("device-publication-ownership-unproven");
    }
    observed = parseStructure(publication.metadata) ?? null;
    report.observed = reportStructure(observed);
    if (!observed || Object.keys(expected).some((key) => observed[key] !== expected[key])) {
      fail("published-credential-structure-mismatch");
    }
    report.eligible = true;
    report.classification = "CREDENTIALS_STAGED";
    report.reason = "two-raw-nonblank-lines-verified";
  } catch (error) {
    report.reason = error instanceof StagingError ? error.message : "credential-staging-unavailable";
  } finally {
    if (temporaryOwned) {
      try {
        if (invokeStep(report.steps, "cleanup", () => adb(scripts.cleanup))?.status !== 0) fail("device-staging-cleanup-failed");
      } catch {
        report.eligible = false;
        report.classification = "FAILED_CREDENTIAL_STAGING";
        report.reason = "device-staging-cleanup-failed";
      }
    }
    payload?.fill(0);
  }
  try {
    try { writeFileSync(descriptor, `${JSON.stringify(report)}\n`); }
    finally { closeSync(descriptor); }
    linkSync(pendingOutput, options.output);
  } finally {
    // No evidence means failure, never permission to start instrumentation.
    // A host artifact-publication failure does not authorize later deletion.
    // Without the joined staging/ownership proof, preserve the destination for
    // explicit user-authorized stopped-session cleanup, never adopt it.
    unlinkSync(pendingOutput);
  }
  // The separate private capability is published only after a joined successful
  // staging report. A crash/lost report cannot later adopt the destination.
  if (ownership && report.eligible) recordCredentialOwnership(ownership, ownershipProof);
  return report;
}

// A separate, exclusively owned zero-byte namespace. No operation references
// the credentials destination, reads config, hashes data, or follows symlinks.
export function sentinelScripts(token) {
  if (!/^[A-Za-z0-9-]+$/.test(token)) fail("invalid-sentinel-token");
  const directory = `files/acceptance/.publication-sentinel-${token}`;
  const source = `${directory}/source`; const destination = `${directory}/published`;
  return {
    stage: `set -eu
umask 077
test ! -L files
test ! -L files/acceptance
mkdir -p files/acceptance
mkdir ${directory}
printf 'sentinel-owned\\n'
set -C
exec 3> ${source}
exec 3>&-
chmod 600 ${source}
`,
    publish: publishScript(source, destination, `publication_require test -f ${destination}
publication_require test ! -L ${destination}
# Prove this device shell rejects a second exclusive open of the same file.
# The subshell contains any fatal-redirection exit; no secret file is involved.
if (trap - 0 HUP INT TERM; set -C; exec 4> ${destination}) 2>/dev/null; then publication_finish 76; fi
sentinel_metadata=$(stat -c '%a %s %h' ${destination}) || publication_finish "$?"
printf '%s\\n' "$sentinel_metadata"
publication_require test "$sentinel_metadata" = '600 0 1'
`, false),
    cleanup: `set -eu
rm -f ${source}
rmdir ${directory}
test ! -e ${directory}
`,
  };
}

export function diagnosePhysicalPublication(options, dependencies = {}) {
  if (!options.sentinelOnly || options.inspectOnly || !options.serial || !options.output || options.config !== undefined || options.schema !== undefined) {
    fail("serial-output-required-for-sentinel");
  }
  requirePrivateOutput(options.output);
  const scripts = sentinelScripts((dependencies.uuid ?? randomUUID)());
  const invoke = dependencies.adb ?? ((args) => spawnSync("adb", args, { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 }));
  const adb = (script) => invoke(["-s", options.serial, "shell", "-T", "run-as", PACKAGE, "sh", "-c", quote(script)]);
  const report = { type: "physical-publication-sentinel", schemaVersion: 2, eligible: false,
    reason: "sentinel-unavailable", destinationOwned: false, steps: publicationSteps(), observed: null };
  let owned = false;
  try {
    const created = invokeStep(report.steps, "stage", () => adb(scripts.stage));
    owned = typeof created?.stdout === "string" && created.stdout.startsWith("sentinel-owned\n");
    if (created?.status !== 0) fail("sentinel-stage-failed");
    if (!owned) fail("sentinel-ownership-unproven");
    const published = invokeStep(report.steps, "publish", () => adb(scripts.publish));
    const publication = publicationMetadata(published, report.steps);
    report.destinationOwned = publication.owned;
    if (published?.status !== 0) fail("sentinel-publication-failed");
    if (!publication.owned || PUBLICATION_PHASES.some((phase) => report.steps[phase].exitCode !== 0) ||
        report.steps.destinationCleanup.exitCode !== 0) fail("sentinel-publication-ownership-unproven");
    if (!/^600 0 1$/.test(publication.metadata.trim())) fail("sentinel-metadata-mismatch");
    report.observed = { mode: "600", byteLength: 0, linkCount: 1, exclusiveCollisionRefused: true };
    report.eligible = true;
    report.reason = "zero-byte-exclusive-create-copy-stat-cleanup-verified";
  } catch (error) {
    report.reason = error instanceof StagingError ? error.message : "sentinel-unavailable";
  } finally {
    if (owned) {
      try {
        if (invokeStep(report.steps, "cleanup", () => adb(scripts.cleanup))?.status !== 0) fail("sentinel-cleanup-failed");
      } catch {
        report.eligible = false;
        report.reason = "sentinel-cleanup-failed";
      }
    }
  }
  writeFileSync(options.output, `${JSON.stringify(report)}\n`, { flag: "wx", mode: 0o600 });
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const options = parseArgs(process.argv.slice(2));
    if (options.sentinelOnly) {
      if (!diagnosePhysicalPublication(options).eligible) process.exitCode = 2;
    } else if (options.inspectOnly) {
      const report = inspectPhysicalCredentialLines(options);
      process.stdout.write(`${JSON.stringify(report)}\n`);
      if (!report.eligible) process.exitCode = 2;
    } else if (!stagePhysicalCredentials(options).eligible) process.exitCode = 2;
  } catch (error) {
    process.stderr.write(`physical credential staging failed: ${error instanceof StagingError ? error.message : "evidence-unavailable"}\n`);
    process.exitCode = 2;
  }
}
