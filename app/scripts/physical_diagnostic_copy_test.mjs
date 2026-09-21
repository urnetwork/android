import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { chmodSync, existsSync, fchmodSync, fstatSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { copyDiagnosticArtifact, parseArgs } from "./physical_diagnostic_copy.mjs";
import { diagnosticIdentity } from "./physical_diagnostic_identity.mjs";

function fixture(t, verb = "owner-census") {
  const directory = mkdtempSync(join(tmpdir(), "physical-diagnostic-copy-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const options = { serial: "fake-device", receipt: join(directory, "command.json"), output: join(directory, "copied-artifact") };
  const initial = { sessionId: "11111111-1111-4111-8111-111111111111", pid: 1234, commandId: "0", state: "ready", phase: "ready", elapsedMs: 100,
    connected: false, tunnelStarted: false, provideEnabled: false, transportMode: "auto" };
  const wire = diagnosticIdentity(initial.sessionId, verb, "preflight", "diagnostic-fresh");
  const receipt = { schema: 2, type: "physical-diagnostic-command", eligible: true, verb, label: "preflight",
    commandId: "diagnostic-fresh", wire,
    serialHash: createHash("sha256").update(options.serial).digest("hex"), initial,
    status: { ...initial, commandId: wire.commandId, state: "complete", phase: `${verb}-${wire.label}`, elapsedMs: 200 } };
  writeFileSync(options.receipt, JSON.stringify(receipt), { mode: 0o600 });
  const f = { directory, options, receipt, calls: 0, payload: Buffer.from([0, 1, 255, 97, 10]),
    run(adb = (_args, fd) => { writeFileSync(fd, f.payload); return { status: 0 }; }) {
      return copyDiagnosticArtifact(options, { adb: (args, fd) => { f.calls++; return adb(args, fd); } });
    },
    noOutput() {
      assert.equal(existsSync(options.output), false);
      assert.equal(readdirSync(directory).some(name => name.includes(".pending-")), false);
    },
  };
  return f;
}

for (const verb of ["owner-census", "heap-profile", "goroutine-stacks"]) {
  test(`${verb} streams exact binary bytes into exclusive 0600 output despite a permissive caller umask`, (t) => {
    const f = fixture(t, verb);
    const previous = process.umask(0);
    try {
      const report = f.run((args, fd) => {
        assert.deepEqual(args, ["-s", "fake-device", "exec-out", "run-as", "com.bringyour.network", "cat", `files/acceptance/${f.receipt.wire.artifactName}`]);
        assert.equal(fstatSync(fd).mode & 0o7777, 0o600, "private before receiving the first raw byte");
        // Real child stdout streams to the fd, not a host JS string or tool log.
        return spawnSync(process.execPath, ["-e", "process.stdout.write(Buffer.from([0,1,255,97,10]))"],
          { stdio: ["ignore", fd, "pipe"], timeout: 5000 });
      });
      assert.deepEqual(readFileSync(f.options.output), f.payload);
      assert.equal(statSync(f.options.output).mode & 0o7777, 0o600);
      assert.deepEqual(report, { type: "physical-diagnostic-copy", schema: 1, copied: true, verb, bytes: 5, mode: "600" });
      assert.equal(readdirSync(f.directory).some(name => name.includes(".pending-")), false);
    } finally { process.umask(previous); }
  });
}

test("existing, nonprivate or symlink output is never overwritten, repaired or followed", (t) => {
  for (const kind of ["private", "public", "symlink", "dangling", "directory"]) {
    const f = fixture(t);
    if (kind === "directory") mkdirSync(f.options.output);
    else if (kind === "symlink" || kind === "dangling") symlinkSync(kind === "symlink" ? f.options.receipt : join(f.directory, "absent"), f.options.output);
    else writeFileSync(f.options.output, "preserve", { mode: kind === "public" ? 0o644 : 0o600 });
    assert.throws(() => f.run(), /fresh-diagnostic-output-required/);
    assert.equal(f.calls, 0);
    if (["private", "public"].includes(kind)) assert.equal(readFileSync(f.options.output, "utf8"), "preserve");
  }
});

test("a completed private serial-bound exact diagnostic receipt is mandatory", (t) => {
  for (const kind of ["public", "symlink", "malformed", "wrong-device", "running", "bare-phase", "wrong-pid", "unsafe-label", "other-verb", "role-change",
    "legacy-schema", "missing-session", "changed-session", "wire-label", "wire-id", "wire-artifact", "logical-id"]) {
    const f = fixture(t);
    if (kind === "public") chmodSync(f.options.receipt, 0o644);
    else if (kind === "symlink") {
      const saved = join(f.directory, "saved.json");
      renameSync(f.options.receipt, saved);
      symlinkSync(saved, f.options.receipt);
    } else if (kind === "malformed") writeFileSync(f.options.receipt, "private-raw");
    else {
      if (kind === "wrong-device") f.receipt.serialHash = "0".repeat(64);
      if (kind === "running") f.receipt.status.state = "running";
      if (kind === "bare-phase") f.receipt.status.phase = "owner-census";
      if (kind === "wrong-pid") f.receipt.status.pid = 5678;
      if (kind === "unsafe-label") f.receipt.label = "../secret";
      if (kind === "other-verb") f.receipt.verb = "credentials";
      if (kind === "role-change") f.receipt.status.connected = true;
      if (kind === "legacy-schema") f.receipt.schema = 1;
      if (kind === "missing-session") delete f.receipt.initial.sessionId;
      if (kind === "changed-session") f.receipt.status.sessionId = "22222222-2222-4222-8222-222222222222";
      if (kind === "wire-label") f.receipt.wire.label = "preflight";
      if (kind === "wire-id") f.receipt.wire.commandId = "another-command";
      if (kind === "wire-artifact") f.receipt.wire.artifactName = "physical-owners-preflight.json";
      if (kind === "logical-id") f.receipt.commandId = "another-command";
      writeFileSync(f.options.receipt, JSON.stringify(f.receipt));
    }
    assert.throws(() => f.run(), /private-diagnostic-receipt-required|completed-bound-diagnostic-receipt-required/);
    assert.equal(f.calls, 0);
    f.noOutput();
  }
});

test("copy failure, interruption, empty data or changed output mode never publish success or raw diagnostics", (t) => {
  for (const kind of ["nonzero", "timeout", "signal", "empty", "mode"]) {
    const f = fixture(t);
    assert.throws(() => f.run((_args, fd) => {
      if (kind !== "empty") writeFileSync(fd, "private-raw-partial");
      if (kind === "mode") fchmodSync(fd, 0o644);
      return { status: kind === "nonzero" ? 1 : 0, stderr: "private-raw",
        ...(kind === "timeout" ? { error: new Error("private-raw") } : {}), ...(kind === "signal" ? { signal: "SIGTERM" } : {}) };
    }), /^(Error: )?(diagnostic-copy-failed|empty-diagnostic-output|private-diagnostic-output-required)$/);
    assert.equal(f.calls, 1);
    f.noOutput();
  }
});

test("concurrent output publication preserves the other writer and refuses success", (t) => {
  const f = fixture(t);
  assert.throws(() => f.run((_args, fd) => {
    writeFileSync(fd, "fixture");
    writeFileSync(f.options.output, "other-owner", { mode: 0o600 });
    return { status: 0 };
  }), error => error.code === "EEXIST");
  assert.equal(readFileSync(f.options.output, "utf8"), "other-owner");
  assert.equal(readdirSync(f.directory).some(name => name.includes(".pending-")), false);
});

test("public or missing artifact parents and outside receipts fail before copying", (t) => {
  for (const kind of ["public", "missing", "outside"]) {
    const f = fixture(t);
    if (kind === "public") chmodSync(f.directory, 0o755);
    if (kind === "missing") f.options.output = join(f.directory, "absent", "out");
    if (kind === "outside") { mkdirSync(join(f.directory, "child"), { mode: 0o700 }); f.options.output = join(f.directory, "child", "out"); }
    assert.throws(() => f.run(), /artifact-directory-mode-not-0700|artifact-directory-missing|artifact-path-outside-directory/);
    assert.equal(f.calls, 0);
  }
});

test("copy arguments cannot select arbitrary files or omit explicit destinations", () => {
  const valid = ["--serial", "fake-device", "--receipt", "/fixture/receipt", "--output", "/fixture/out"];
  assert.equal(parseArgs(valid).serial, "fake-device");
  for (const args of [[], valid.slice(0, -1), [...valid, "--file", "credentials"], [...valid, "--serial", "other"],
    valid.map(value => value === "/fixture/out" ? "relative" : value)]) assert.throws(() => parseArgs(args));
});
