#!/usr/bin/env node

// Host-only directory preparation. Never repairs/adopts an existing untrusted
// path, recursively creates ancestors, or handles device/config/credential data.
import { lstatSync, mkdirSync, realpathSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

class ArtifactDirectoryError extends Error {}
const fail = (reason) => { throw new ArtifactDirectoryError(reason); };
export const artifactDirectoryReason = (error) => error instanceof ArtifactDirectoryError
  ? error.message : "artifact-directory-unavailable";

function inspect(path, dependencies) {
  let stat;
  try { stat = (dependencies.lstat ?? lstatSync)(path); }
  catch (error) { fail(error?.code === "ENOENT" ? "artifact-directory-missing" : "artifact-directory-unavailable"); }
  if (!stat.isDirectory() || stat.isSymbolicLink()) fail("artifact-directory-untrusted-type");
  if (stat.uid !== (dependencies.uid ?? process.getuid())) fail("artifact-directory-owner-mismatch");
  if ((stat.mode & 0o7777) !== 0o700) fail("artifact-directory-mode-not-0700");
  return stat;
}

// create is explicit and leaf-only. Later launch/check calls must never recreate
// a removed evidence directory or mistake a replacement for the same owner.
export function prepareArtifactDirectory(path, create = false, dependencies = {}) {
  if (typeof path !== "string" || !path.trim() || path.includes("\0")) fail("explicit-artifact-directory-required");
  const directory = resolve(path);
  let stat;
  let created = false;
  try { stat = inspect(directory, dependencies); }
  catch (error) {
    if (!create || artifactDirectoryReason(error) !== "artifact-directory-missing") throw error;
    inspect(dirname(directory), dependencies);
    try { mkdirSync(directory, { mode: 0o700 }); created = true; }
    catch (error) { if (error?.code !== "EEXIST") fail("artifact-directory-create-failed"); }
    stat = inspect(directory, dependencies);
  }
  let canonical;
  try { canonical = realpathSync(directory); }
  catch { fail("artifact-directory-unavailable"); }
  return { directory, canonical, device: stat.dev, inode: stat.ino, created };
}

export function requireArtifactPaths(binding, paths, dependencies = {}) {
  const current = prepareArtifactDirectory(binding.directory, false, dependencies);
  if (current.canonical !== binding.canonical || current.device !== binding.device || current.inode !== binding.inode) {
    fail("artifact-directory-replaced");
  }
  if (!Array.isArray(paths) || !paths.length || paths.some((path) => typeof path !== "string" || !path ||
      dirname(resolve(path)) !== binding.directory)) fail("artifact-path-outside-directory");
  return true;
}

export function parseArgs(argv) {
  if (argv.length !== 3 || !["create", "check"].includes(argv[0]) || argv[1] !== "--directory" ||
      !argv[2] || argv[2].startsWith("--")) fail("create-or-check-explicit-directory-required");
  return { directory: argv[2], create: argv[0] === "create" };
}

export function artifactDirectoryPreflight(options, dependencies = {}) {
  try {
    const binding = prepareArtifactDirectory(options.directory, options.create, dependencies);
    requireArtifactPaths(binding, [resolve(binding.directory, "directory-preflight")], dependencies);
    return { type: "physical-artifact-directory", schemaVersion: 1, eligible: true,
      classification: "ARTIFACT_DIRECTORY_READY", reason: "owned-mode-0700-directory-verified",
      created: binding.created, ownerMatched: true, mode: "700" };
  } catch (error) {
    return { type: "physical-artifact-directory", schemaVersion: 1, eligible: false,
      classification: "INVALID_SETUP", reason: artifactDirectoryReason(error),
      created: false, ownerMatched: false, mode: null };
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const report = artifactDirectoryPreflight(parseArgs(process.argv.slice(2)));
    process.stdout.write(`${JSON.stringify(report)}\n`);
    if (!report.eligible) process.exitCode = 2;
  } catch (error) {
    process.stderr.write(`artifact directory preflight failed: ${artifactDirectoryReason(error)}\n`);
    process.exitCode = 2;
  }
}
