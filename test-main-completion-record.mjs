// SPDX-License-Identifier: MPL-2.0
// Private bounded completion receipt. Drain all input so the caller can retain
// the ADB/timeout exit, not a reader-induced SIGPIPE. Never print app bytes.
import { openSync, closeSync, writeSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const limit = 4096;

try {
  const [directory, expectedBuild, expectedRepeat] = process.argv.slice(2);
  if (!directory || !expectedBuild || !expectedRepeat || process.argv.length !== 5) {
    throw new Error('invalid capture arguments');
  }
  const retained = Buffer.alloc(limit);
  let retainedBytes = 0;
  let receivedBytes = 0;
  const fd = openSync(join(directory, 'result.bin'), 'wx', 0o600);
  try {
    for await (const chunk of process.stdin) {
      receivedBytes += chunk.length;
      const count = Math.min(limit - retainedBytes, chunk.length);
      if (count > 0) {
        chunk.copy(retained, retainedBytes, 0, count);
        let written = 0;
        while (written < count) {
          const countWritten = writeSync(fd, chunk, written, count - written);
          if (countWritten === 0) throw new Error('capture write made no progress');
          written += countWritten;
        }
        retainedBytes += count;
      }
    }
  } finally {
    closeSync(fd);
  }
  const receipt = { flag: 'wx', mode: 0o600 };
  writeFileSync(join(directory, 'received-bytes.txt'), String(receivedBytes) + '\n', receipt);
  // Preserve the existing CR normalization and first-two-line comparison.
  // Binary or unexpected content remains private in result.bin, not shell text.
  const lines = retained.subarray(0, retainedBytes).toString('utf8').replace(/\r/g, '').split('\n');
  const result = receivedBytes > limit ? 'oversize'
    : lines[0] === expectedBuild && lines[1] === expectedRepeat ? 'match' : 'mismatch';
  writeFileSync(join(directory, 'capture-result.txt'), result + '\n', receipt);
} catch {
  process.stderr.write('completion result capture failed\n');
  process.exitCode = 1;
}
