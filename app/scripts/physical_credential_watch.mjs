#!/usr/bin/env node

// Diagnostic-only, read-only external witness. Never reads file contents.
import { spawnSync } from "node:child_process";
import { closeSync, existsSync, lstatSync, openSync, writeSync } from "node:fs";
import { dirname, isAbsolute } from "node:path";
import { performance } from "node:perf_hooks";
import { pathToFileURL } from "node:url";

const quote = (text) => `'${text.replaceAll("'", "'\\''")}'`;
export const credentialMetadataScript = `set -eu
file=files/acceptance/credentials
# Check each ancestor while its parent is traversable. An inaccessible
# directory is unavailable metadata, not evidence that the file is missing.
for directory in . files files/acceptance; do
  if [ ! -e "$directory" ] && [ ! -L "$directory" ]; then
    printf '0 missing - - -\\n'
    exit 0
  fi
  test ! -L "$directory"
  test -d "$directory"
  test -x "$directory"
done
if [ ! -e "$file" ] && [ ! -L "$file" ]; then
  printf '0 missing - - -\\n'
  exit 0
fi
kind=other
if [ -L "$file" ]; then kind=symlink
elif [ -f "$file" ]; then kind=regular
elif [ -d "$file" ]; then kind=directory
fi
metadata=$(stat -c '%u %a %s' "$file" 2>/dev/null)
set -- $metadata
test "$#" -eq 3
owner=0
if [ "$1" = "$(id -u)" ]; then owner=1; fi
printf '1 %s %s %s %s\\n' "$kind" "$owner" "$2" "$3"
`;

const unavailable = () => ({ exists: null, fileType: "unavailable", ownerMatchesApp: null, mode: null, byteCount: null });
export function parseCredentialMetadata(result) {
  if (result?.status !== 0 || result.error || result.signal ||
      typeof result.stdout !== "string" || result.stdout.length > 256) return unavailable();
  const fields = result.stdout.trim().split(/\s+/);
  if (fields.join(" ") === "0 missing - - -") {
    return { exists: false, fileType: "missing", ownerMatchesApp: null, mode: null, byteCount: null };
  }
  const [exists, fileType, owner, mode, bytes] = fields;
  if (fields.length !== 5 || exists !== "1" || !["regular", "directory", "symlink", "other"].includes(fileType) ||
      !/^[01]$/.test(owner) || !/^[0-7]{3,4}$/.test(mode) || !/^\d+$/.test(bytes) || !Number.isSafeInteger(Number(bytes))) return unavailable();
  return { exists: true, fileType, ownerMatchesApp: owner === "1", mode, byteCount: Number(bytes) };
}

export function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length;) {
    const flag = args[index]; const value = args[index + 1];
    if (flag === "--preflight" && !Object.hasOwn(options, flag)) {
      options[flag] = true;
      index++;
      continue;
    }
    if (!["--serial", "--output", "--stop-file", "--timeout-ms"].includes(flag) || !value || value.startsWith("--") ||
        Object.hasOwn(options, flag)) throw new Error("invalid-watch-arguments");
    options[flag] = value;
    index += 2;
  }
  if (options["--preflight"]) {
    if (!options["--serial"] || !isAbsolute(options["--output"] ?? "") ||
        options["--stop-file"] !== undefined || options["--timeout-ms"] !== undefined) {
      throw new Error("credential-preflight-arguments-required");
    }
    return { serial: options["--serial"], output: options["--output"], preflight: true };
  }
  const timeoutMs = Number(options["--timeout-ms"] ?? "120000");
  if (!options["--serial"] || !isAbsolute(options["--output"] ?? "") || !isAbsolute(options["--stop-file"] ?? "") ||
      !Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 180000) throw new Error("bounded-watch-arguments-required");
  return { serial: options["--serial"], output: options["--output"], stopFile: options["--stop-file"], timeoutMs };
}

function requirePrivateParent(path) {
  const directory = lstatSync(dirname(path));
  if (!directory.isDirectory() || (directory.mode & 0o077) !== 0 ||
      (typeof process.getuid === "function" && directory.uid !== process.getuid())) throw new Error("private-watch-directory-required");
}

function credentialProbe(serial, dependencies) {
  // adb joins shell arguments into a second shell command. The script needs
  // its own literal quote layer; host argv grouping alone does not preserve it.
  const invoke = dependencies.adb ?? (args => spawnSync("adb", args,
    { encoding: "utf8", timeout: 5000, maxBuffer: 1024 }));
  return invoke(["-s", serial, "shell", "-T", "run-as", "com.bringyour.network", "sh", "-c", quote(credentialMetadataScript)]);
}

// One read-only pre-staging check, with the same remote quoting as the retained
// watcher. APK reinstall preserves app data and is not proof of absence.
export function preflightCredentialDestination(options, dependencies = {}) {
  if (!options.preflight || !options.serial || !isAbsolute(options.output ?? "")) {
    throw new Error("credential-preflight-arguments-required");
  }
  requirePrivateParent(options.output);
  const descriptor = openSync(options.output, "wx", 0o600);
  try {
    let raw;
    try { raw = credentialProbe(options.serial, dependencies); } catch { raw = undefined; }
    const observed = parseCredentialMetadata(raw);
    const report = { type: "physical-credential-destination-preflight", schemaVersion: 1,
      eligible: observed.exists === false,
      reason: observed.exists === false ? "credential-destination-absent" : observed.exists === true
        ? "credential-destination-present" : "credential-destination-unavailable", ...observed };
    writeSync(descriptor, `${JSON.stringify(report)}\n`);
    return report;
  } finally { closeSync(descriptor); }
}

export async function watchCredentialMetadata(options, dependencies = {}) {
  requirePrivateParent(options.output); requirePrivateParent(options.stopFile);
  if (options.output === options.stopFile || existsSync(options.stopFile)) throw new Error("fresh-watch-paths-required");
  const descriptor = openSync(options.output, "wx", 0o600);
  const now = dependencies.now ?? Date.now;
  const monotonic = dependencies.monotonic ?? (() => performance.now());
  const sleep = dependencies.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const interrupted = dependencies.interrupted ?? (() => false);
  const probe = dependencies.probe ?? (() => credentialProbe(options.serial, dependencies));
  const started = monotonic(); let samples = 0;
  try {
    while (monotonic() - started < options.timeoutMs && !existsSync(options.stopFile) && !interrupted()) {
      const timeUnixMs = now();
      let raw;
      try { raw = probe(); } catch { raw = undefined; }
      writeSync(descriptor, `${JSON.stringify({ type: "physical-credential-external", schemaVersion: 1,
        timeUnixMs, observedTimeUnixMs: now(), ...parseCredentialMetadata(raw) })}\n`);
      samples++;
      if (monotonic() - started < options.timeoutMs && !existsSync(options.stopFile) && !interrupted()) await sleep(250);
    }
    const reason = interrupted() ? "interrupted" : existsSync(options.stopFile) ? "stop-file" : "deadline";
    const summary = { type: "physical-credential-watch-summary", samples, reason };
    writeSync(descriptor, `${JSON.stringify(summary)}\n`);
    return summary;
  } finally {
    closeSync(descriptor);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let interrupted = false;
  const stop = () => { interrupted = true; };
  process.on("SIGINT", stop); process.on("SIGTERM", stop);
  try {
    const options = parseArgs(process.argv.slice(2));
    const summary = options.preflight ? preflightCredentialDestination(options)
      : await watchCredentialMetadata(options, { interrupted: () => interrupted });
    process.stdout.write(`${JSON.stringify(summary)}\n`);
    if (summary.eligible === false || summary.reason === "interrupted") process.exitCode = 2;
  } catch {
    process.stderr.write("physical credential metadata watch unavailable\n");
    process.exitCode = 2;
  } finally {
    process.off("SIGINT", stop); process.off("SIGTERM", stop);
  }
}
