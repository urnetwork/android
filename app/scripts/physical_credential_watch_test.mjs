import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { credentialMetadataScript, parseArgs, parseCredentialMetadata, watchCredentialMetadata } from "./physical_credential_watch.mjs";

test("external credential witness accepts only bounded primitive metadata and never raw errors", () => {
  assert.deepEqual(parseCredentialMetadata({ status: 0, stdout: "1 regular 1 600 44\n" }),
    { exists: true, fileType: "regular", ownerMatchesApp: true, mode: "600", byteCount: 44 });
  assert.deepEqual(parseCredentialMetadata({ status: 0, stdout: "0 missing - - -\n" }),
    { exists: false, fileType: "missing", ownerMatchesApp: null, mode: null, byteCount: null });
  for (const raw of ["secret-value", "1 regular 1 600 -1", "1 regular 1 600 9007199254740992", "1 private-path 1 600 3", "x".repeat(257)]) {
    const parsed = parseCredentialMetadata({ status: 0, stdout: raw, stderr: "secret-value" });
    assert.equal(parsed.fileType, "unavailable");
    assert.equal(JSON.stringify(parsed).includes("secret-value"), false);
  }
  assert.equal(parseCredentialMetadata({ status: 1, stdout: "1 regular 1 600 44" }).fileType, "unavailable");
  assert.match(credentialMetadataScript, /stat -c '%u %a %s'/);
  assert.doesNotMatch(credentialMetadataScript, /\b(cat|tee|rm|chmod|sha256sum|head|tail|sed|awk)\b/);
});

test("metadata witness requires a bounded deadline and explicit private paths", () => {
  assert.deepEqual(parseArgs(["--serial", "fixture", "--output", "/private/out", "--stop-file", "/private/stop"]),
    { serial: "fixture", output: "/private/out", stopFile: "/private/stop", timeoutMs: 120000 });
  for (const args of [[], ["--serial", "fixture"], ["--password", "secret"],
    ["--serial", "fixture", "--output", "relative", "--stop-file", "/private/stop"],
    ["--serial", "fixture", "--output", "/private/out", "--stop-file", "/private/stop", "--timeout-ms", "180001"]]) {
    assert.throws(() => parseArgs(args));
  }
});

test("retained metadata witness records disappearance without reading or restoring credentials", async (t) => {
  const directory = mkdtempSync(join(tmpdir(), "credential-watch-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const options = { serial: "fixture-device", output: join(directory, "watch.ndjson"), stopFile: join(directory, "stop"), timeoutMs: 1000 };
  let clock = 100; let probes = 0;
  const summary = await watchCredentialMetadata(options, {
    now: () => clock, monotonic: () => clock,
    sleep: async (ms) => { clock += ms; },
    probe: () => {
      probes++;
      if (probes === 3) writeFileSync(options.stopFile, "", { mode: 0o600 });
      return { status: 0, stdout: probes === 1 ? "1 regular 1 600 44\n" : "0 missing - - -\n" };
    },
  });
  assert.deepEqual(summary, { type: "physical-credential-watch-summary", samples: 3, reason: "stop-file" });
  const text = readFileSync(options.output, "utf8");
  const rows = text.trim().split("\n").map(JSON.parse);
  assert.deepEqual(rows.slice(0, 3).map((row) => row.exists), [true, false, false]);
  assert.equal(statSync(options.output).mode & 0o777, 0o600);
  assert.equal(text.includes(options.serial), false);
  assert.equal(text.includes(directory), false);
  await assert.rejects(watchCredentialMetadata(options, { probe: () => assert.fail("existing output must not probe") }));
});
