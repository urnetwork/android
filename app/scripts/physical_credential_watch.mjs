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
  if (result?.status !== 0 || typeof result.stdout !== "string" || result.stdout.length > 256) return unavailable();
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
  for (let index = 0; index < args.length; index += 2) {
    const flag = args[index]; const value = args[index + 1];
    if (!["--serial", "--output", "--stop-file", "--timeout-ms"].includes(flag) || !value || value.startsWith("--") ||
        Object.hasOwn(options, flag)) throw new Error("invalid-watch-arguments");
    options[flag] = value;
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

export async function watchCredentialMetadata(options, dependencies = {}) {
  requirePrivateParent(options.output); requirePrivateParent(options.stopFile);
  if (options.output === options.stopFile || existsSync(options.stopFile)) throw new Error("fresh-watch-paths-required");
  const descriptor = openSync(options.output, "wx", 0o600);
  const now = dependencies.now ?? Date.now;
  const monotonic = dependencies.monotonic ?? (() => performance.now());
  const sleep = dependencies.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const interrupted = dependencies.interrupted ?? (() => false);
  const probe = dependencies.probe ?? (() => spawnSync("adb", ["-s", options.serial, "shell", "-T", "run-as",
    "com.bringyour.network", "sh", "-c", quote(credentialMetadataScript)],
  { encoding: "utf8", timeout: 5000, maxBuffer: 1024 }));
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
    const summary = await watchCredentialMetadata(parseArgs(process.argv.slice(2)), { interrupted: () => interrupted });
    process.stdout.write(`${JSON.stringify(summary)}\n`);
    if (summary.reason === "interrupted") process.exitCode = 2;
  } catch {
    process.stderr.write("physical credential metadata watch unavailable\n");
    process.exitCode = 2;
  } finally {
    process.off("SIGINT", stop); process.off("SIGTERM", stop);
  }
}
