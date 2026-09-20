import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync,
  statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { artifactDirectoryPreflight, parseArgs, prepareArtifactDirectory, requireArtifactPaths } from "./physical_artifact_directory.mjs";

function fixture(t) {
  const parent = mkdtempSync(join(tmpdir(), "physical-artifact-directory-test-"));
  t.after(() => rmSync(parent, { recursive: true, force: true }));
  return { parent, directory: join(parent, "private") };
}

test("explicit create makes only the private leaf and check never recreates missing evidence", (t) => {
  const f = fixture(t);
  assert.equal(artifactDirectoryPreflight({ directory: f.directory }).reason, "artifact-directory-missing");
  assert.equal(existsSync(f.directory), false);
  const report = artifactDirectoryPreflight({ directory: f.directory, create: true });
  assert.equal(report.eligible, true); assert.equal(report.created, true);
  assert.equal(statSync(f.directory).mode & 0o7777, 0o700);
  assert.equal(artifactDirectoryPreflight({ directory: f.directory }).created, false);
  rmSync(f.directory, { recursive: true });
  assert.equal(artifactDirectoryPreflight({ directory: f.directory }).reason, "artifact-directory-missing");
  assert.equal(existsSync(f.directory), false);
  assert.equal(artifactDirectoryPreflight({ directory: join(f.parent, "absent", "private"), create: true }).eligible, false);
  assert.equal(existsSync(join(f.parent, "absent")), false, "no recursive ancestor creation");
});

test("wrong mode/owner/type are rejected, never chmodded, adopted or followed", (t) => {
  for (const kind of ["mode", "owner", "symlink", "file"]) {
    const f = fixture(t);
    if (kind === "file") writeFileSync(f.directory, "owned fixture", { mode: 0o600 });
    else if (kind === "symlink") symlinkSync(f.parent, f.directory);
    else mkdirSync(f.directory, { mode: kind === "mode" ? 0o755 : 0o700 });
    const dependencies = kind === "owner" ? { uid: process.getuid() + 1 } : {};
    const before = lstatSync(f.directory);
    const report = artifactDirectoryPreflight({ directory: f.directory, create: true }, dependencies);
    assert.equal(report.eligible, false);
    assert.equal(report.reason, kind === "mode" ? "artifact-directory-mode-not-0700" :
      kind === "owner" ? "artifact-directory-owner-mismatch" : "artifact-directory-untrusted-type");
    assert.equal(lstatSync(f.directory).mode, before.mode);
    assert.equal(lstatSync(f.directory).ino, before.ino);
    assert.equal(JSON.stringify(report).includes(f.directory), false);
    for (const key of ["uid", "inode", "device", "directory", "canonical"]) assert.equal(Object.hasOwn(report, key), false);
  }
  const f = fixture(t); chmodSync(f.parent, 0o755);
  assert.equal(artifactDirectoryPreflight({ directory: f.directory, create: true }).reason, "artifact-directory-mode-not-0700");
  assert.equal(existsSync(f.directory), false);
});

test("bound paths reject sibling destinations, directory removal and replacement", (t) => {
  const f = fixture(t); const binding = prepareArtifactDirectory(f.directory, true);
  assert.equal(requireArtifactPaths(binding, [join(f.directory, "parser.json"), join(f.directory, "owner.json")]), true);
  assert.throws(() => requireArtifactPaths(binding, [join(f.parent, "outside.json")]), /artifact-path-outside-directory/);
  const saved = join(f.parent, "saved-private");
  renameSync(f.directory, saved);
  assert.throws(() => requireArtifactPaths(binding, [join(f.directory, "owner.json")]), /artifact-directory-missing/);
  mkdirSync(f.directory, { mode: 0o700 });
  assert.throws(() => requireArtifactPaths(binding, [join(f.directory, "owner.json")]), /artifact-directory-replaced/);
});

test("directory CLI is bounded, sanitized and reports missing setup without spawning any helper", (t) => {
  assert.deepEqual(parseArgs(["check", "--directory", "private"]), { directory: "private", create: false });
  for (const args of [[], ["create", "--directory", ""], ["check", "--config", "secret"],
    ["check", "--directory", "private", "--serial", "device"]]) assert.throws(() => parseArgs(args));
  const f = fixture(t); const script = new URL("./physical_artifact_directory.mjs", import.meta.url).pathname;
  for (const mode of ["check", "create", "check"]) {
    const result = spawnSync(process.execPath, [script, mode, "--directory", f.directory], { encoding: "utf8", timeout: 3000 });
    assert.equal(result.stderr, "");
    const report = JSON.parse(result.stdout);
    assert.equal(result.status, report.eligible ? 0 : 2);
    assert.equal(result.stdout.includes(f.parent), false);
    assert.equal(report.eligible, mode === "create" || existsSync(f.directory));
  }
  writeFileSync(join(f.directory, "preserved"), "fixture", { mode: 0o600 });
  assert.equal(artifactDirectoryPreflight({ directory: f.directory, create: true }).eligible, true);
  assert.equal(readFileSync(join(f.directory, "preserved"), "utf8"), "fixture");
});
