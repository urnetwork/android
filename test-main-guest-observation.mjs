// SPDX-License-Identifier: MPL-2.0
// Drain the bounded guest read, retaining a private 64 KiB prefix. Do not close
// its pipe at the limit: that would replace the original ADB exit with SIGPIPE.
import { openSync, closeSync, writeSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

try {
  const [directory] = process.argv.slice(2);
  if (!directory || process.argv.length !== 3) throw new Error('invalid arguments');
  const limit = 65536;
  const fd = openSync(join(directory, 'guest.txt'), 'wx', 0o600);
  const prefix = Buffer.alloc(limit);
  let receivedBytes = 0;
  let retainedBytes = 0;
  try {
    for await (const chunk of process.stdin) {
      receivedBytes += chunk.length;
      const count = Math.min(limit - retainedBytes, chunk.length);
      let written = 0;
      while (written < count) {
        const progress = writeSync(fd, chunk, written, count - written);
        if (!progress) throw new Error('capture write made no progress');
        written += progress;
      }
      chunk.copy(prefix, retainedBytes, 0, count);
      retainedBytes += count;
    }
  } finally {
    closeSync(fd);
  }
  const lines = prefix.subarray(0, retainedBytes).toString('utf8').replace(/\r/g, '').split('\n');
  const bootId = /^boot_id=([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/.exec(lines[0] ?? '');
  const uptime = /^uptime=([0-9]+\.[0-9]+) [0-9]+\.[0-9]+$/.exec(lines[1] ?? '');
  const adbdPid = /^adbd_pid=([1-9][0-9]*)$/.exec(lines[2] ?? '');
  const identity = bootId && uptime && adbdPid && lines[3] === 'os_log_begin'
    ? { bootId: bootId[1], uptimeSeconds: uptime[1], adbdPid: adbdPid[1] } : null;
  writeFileSync(join(directory, 'capture.json'), JSON.stringify({
    schemaVersion: 1, receivedBytes, retainedBytes, truncated: receivedBytes > limit,
    // A caller must also require ownership-status=0 and read-status=0. A
    // valid prefix alone never turns a failed or truncated ADB read into proof.
    identity,
  }) + '\n', { flag: 'wx', mode: 0o600 });
  if (!identity) process.exitCode = 1;
} catch {
  process.stderr.write('guest observation capture failed\n');
  process.exitCode = 1;
}
