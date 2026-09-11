#!/usr/bin/env node

// Produces one compact, machine-readable acceptance event. The runner keeps
// the raw logs and screenshots on disk; this file is intentionally small
// enough to be the first context supplied to a debugging model.

import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const MAX_ARTIFACTS = 96;
const MAX_EXCERPT_LINES = 36;

function redact(text) {
  return text
    .replace(/\b(Bearer\s+)[^\s,;]+/gi, "$1[REDACTED]")
    .replace(/\b(password|passphrase|secret|token|authorization|credential)\s*([=:])\s*[^\s,;]+/gi, "$1$2[REDACTED]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[REDACTED_EMAIL]");
}

function normalizeSignature(text) {
  return redact(text)
    .replace(/^\s*\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+\s+[A-Z]\s+/, "")
    .replace(/0x[0-9a-f]+/gi, "<hex>")
    .replace(/:[0-9]+(?=[)\s]|$)/g, ":<line>")
    .replace(/\b\d{4,}\b/g, "<n>")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 280);
}

function meaningfulFailureLine(lines) {
  const patterns = [
    /\b(?:AssertionError|[A-Za-z_$][\w$]*(?:Exception|Error))\b/,
    /FATAL EXCEPTION|Process crashed|INSTRUMENTATION_FAILED|shortMsg=/,
    /FAILURE: Build failed with an exception|FAILURES!!!/,
    /\b(?:timed out|timeout|network is unreachable|unknown host)\b/i,
  ];
  for (const pattern of patterns) {
    const index = lines.findIndex((line) => pattern.test(line));
    if (index !== -1) return index;
  }
  return lines.findIndex((line) => /\b(?:fail(?:ed|ure)?|error)\b/i.test(line));
}

export function summarizeLog(log, maxLines = MAX_EXCERPT_LINES) {
  const lines = log.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
  if (lines.at(-1) === "") lines.pop();
  const failureIndex = meaningfulFailureLine(lines);
  if (failureIndex === -1) {
    return { signature: "no recognizable failure line", excerpt: [] };
  }

  const before = Math.min(10, failureIndex);
  const start = failureIndex - before;
  const end = Math.min(lines.length, start + maxLines);
  return {
    signature: normalizeSignature(lines[failureIndex]),
    excerpt: lines.slice(start, end).map((text, index) => ({
      line: start + index + 1,
      text: redact(text).slice(0, 1000),
    })),
  };
}

export function classifyFailure(phase, log) {
  const lower = log.toLowerCase();
  if (phase === "build") return "build";
  if (phase === "cache") return "cache";
  if (phase === "install") return "installation";
  if (/timed out|timeout|deadline exceeded/.test(lower)) return "timeout";
  if (/unknownhost|network is unreachable|connectexception|sockettimeout|unable to resolve host|dns/.test(lower)) {
    return "network";
  }
  if (/fatal exception|process crashed|sigsegv|shortmsg=/.test(lower)) return "crash";
  if (/assertionerror|failures!!!|expected:.*but was:/.test(lower)) return "assertion";
  if (/instrumentation_failed/.test(lower)) return "instrumentation";
  return phase;
}

export function recommendedModelTier(status, phase, classification) {
  if (status !== "failed") return "none";
  // Live acceptance, peer-to-peer, crash, network, and timeout failures are
  // expensive to reproduce and most benefit from the strongest debugger.
  if (phase === "instrumentation" || phase === "peer-to-peer" ||
      ["network", "crash", "timeout"].includes(classification)) {
    return "strong";
  }
  return "focused";
}

function artifactIndex(root, outputPath) {
  const artifacts = [];
  const outputResolved = path.resolve(outputPath);

  function visit(directory) {
    if (artifacts.length >= MAX_ARTIFACTS) return;
    let entries;
    try {
      entries = fs.readdirSync(directory, { withFileTypes: true })
        .sort((left, right) => left.name.localeCompare(right.name));
    } catch {
      return;
    }
    for (const entry of entries) {
      if (artifacts.length >= MAX_ARTIFACTS) return;
      const absolute = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) continue;
      if (entry.isDirectory()) {
        visit(absolute);
        continue;
      }
      if (!entry.isFile() || path.resolve(absolute) === outputResolved) continue;
      try {
        artifacts.push({
          path: path.relative(root, absolute),
          bytes: fs.statSync(absolute).size,
        });
      } catch {
        // An artifact can disappear during cleanup. The remaining index is
        // still useful and must not mask the original test failure.
      }
    }
  }

  visit(root);
  return artifacts;
}

export function buildResult({ outputPath, environment = process.env }) {
  const status = environment.UR_ACCEPT_RESULT_STATUS;
  const phase = environment.UR_ACCEPT_RESULT_PHASE;
  if (!new Set(["passed", "failed", "skipped"]).has(status)) {
    throw new Error("UR_ACCEPT_RESULT_STATUS must be passed, failed, or skipped");
  }
  if (!phase || !/^[A-Za-z0-9._-]+$/.test(phase)) {
    throw new Error("UR_ACCEPT_RESULT_PHASE is invalid");
  }

  const artifactRoot = environment.UR_ACCEPT_RESULT_ARTIFACT_ROOT ?? path.dirname(outputPath);
  const logPath = environment.UR_ACCEPT_RESULT_LOG ?? "";
  let log = "";
  if (logPath && fs.existsSync(logPath)) {
    // A bounded read keeps a pathological log from becoming a summarizer
    // memory problem. The raw artifact remains indexed for on-demand access.
    const descriptor = fs.openSync(logPath, "r");
    try {
      const size = fs.fstatSync(descriptor).size;
      const bytes = Buffer.alloc(Math.min(size, 1_000_000));
      fs.readSync(descriptor, bytes, 0, bytes.length, Math.max(0, size - bytes.length));
      log = bytes.toString("utf8");
    } finally {
      fs.closeSync(descriptor);
    }
  }

  const classification = status === "failed" ? classifyFailure(phase, log) : null;
  const failure = status === "failed" ? {
    classification,
    ...summarizeLog(log),
  } : null;

  return {
    schemaVersion: 1,
    runId: environment.UR_ACCEPT_RESULT_RUN_ID ?? "unknown",
    profile: environment.UR_ACCEPT_RESULT_PROFILE ?? "full",
    target: requiredFrom(environment, "UR_ACCEPT_RESULT_TARGET"),
    phase,
    status,
    exitCode: integerFrom(environment, "UR_ACCEPT_RESULT_EXIT_CODE"),
    buildId: environment.UR_ACCEPT_RESULT_BUILD_ID || null,
    inputFingerprint: environment.UR_ACCEPT_RESULT_INPUT_FINGERPRINT || null,
    testScope: environment.UR_ACCEPT_RESULT_TEST_SCOPE || null,
    reproCommand: environment.UR_ACCEPT_RESULT_REPRO_COMMAND || null,
    failure,
    recommendedModelTier: recommendedModelTier(status, phase, classification),
    artifacts: artifactIndex(artifactRoot, outputPath),
  };
}

function requiredFrom(environment, name) {
  const value = environment[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function integerFrom(environment, name) {
  const value = requiredFrom(environment, name);
  if (!/^-?\d+$/.test(value)) throw new Error(`${name} must be an integer`);
  return Number(value);
}

function writeAtomically(outputPath, value) {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true, mode: 0o700 });
  const temporary = `${outputPath}.tmp.${process.pid}`;
  fs.writeFileSync(temporary, `${JSON.stringify(value)}\n`, { mode: 0o600 });
  fs.renameSync(temporary, outputPath);
}

async function main() {
  const outputPath = process.argv[2];
  if (!outputPath || process.argv.length !== 3) {
    throw new Error("usage: acceptance-result.mjs OUTPUT.json");
  }
  writeAtomically(outputPath, buildResult({ outputPath }));
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(2);
  });
}
