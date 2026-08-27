import assert from "node:assert/strict";
import test from "node:test";

import {
  parseArgs,
  safeHost,
  summarizeRejectedConnections,
} from "./chrome_video_probe.mjs";

test("argument parser keeps reload and navigation mutually exclusive", () => {
  assert.deepEqual(
    parseArgs([
      "--port", "9223",
      "--target-id", "target",
      "--timeout-ms", "5000",
      "--interval-ms", "250",
      "--reload",
    ]),
    {
      port: 9223,
      targetId: "target",
      timeoutMs: 5000,
      intervalMs: 250,
      reload: true,
      navigateUrl: "",
    },
  );
  assert.throws(
    () => parseArgs(["--reload", "--navigate", "https://example.test/video"]),
    /mutually exclusive/,
  );
});

test("403 retry summary distinguishes same and new TLS connections", () => {
  const responses = [
    {
      host: "media.example.test",
      resourceType: "Fetch",
      timestamp: 1,
      status: 403,
      protocol: "h2",
      connectionId: "8895",
      connectionReused: true,
    },
    {
      host: "unrelated.example.test",
      resourceType: "Fetch",
      timestamp: 2,
      status: 200,
      protocol: "h2",
      connectionId: "other",
      connectionReused: true,
    },
    {
      host: "media.example.test",
      resourceType: "Fetch",
      timestamp: 3,
      status: 403,
      protocol: "h2",
      connectionId: "8895",
      connectionReused: true,
    },
    {
      host: "media.example.test",
      resourceType: "Document",
      timestamp: 4,
      status: 403,
      protocol: "h2",
      connectionId: "9547",
      connectionReused: false,
    },
  ];

  const rejected = summarizeRejectedConnections(responses);
  assert.equal(rejected.length, 3);
  assert.equal(rejected[0].laterResponseCount, 2);
  assert.equal(rejected[0].retryOnSameConnection, true);
  assert.equal(rejected[0].retryOnNewConnection, true);
  assert.equal(rejected[1].retryOnSameConnection, false);
  assert.equal(rejected[1].retryOnNewConnection, true);
  assert.equal(rejected[2].retryOnSameConnection, false);
  assert.equal(rejected[2].retryOnNewConnection, false);
});

test("host sanitizer never retains a request path or signed query", () => {
  assert.equal(
    safeHost("https://media.example.test/private/manifest.mpd?token=secret"),
    "media.example.test",
  );
  assert.equal(safeHost("not a URL"), "invalid");
});
