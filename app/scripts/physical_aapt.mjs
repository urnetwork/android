#!/usr/bin/env node

// Host-only tool discovery. A checkout root is never a user-home directory.
import { accessSync, constants, readdirSync, realpathSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { isAbsolute, join } from "node:path";
import { pathToFileURL } from "node:url";

class AaptError extends Error {}
const fail = reason => { throw new AaptError(reason); };

export function resolveAapt({ env = process.env, platform = process.platform, home = homedir() } = {}) {
  let sdkRoot = env.ANDROID_SDK_ROOT || env.ANDROID_HOME;
  if (!sdkRoot) {
    if (platform === "darwin") sdkRoot = join(home, "Library", "Android", "sdk");
    else if (platform === "linux") sdkRoot = join(home, "Android", "Sdk");
    else fail("explicit-android-sdk-root-required");
  }
  if (!isAbsolute(sdkRoot) || /[\r\n\0]/.test(sdkRoot)) fail("absolute-android-sdk-root-required");
  let versions;
  try { versions = readdirSync(join(sdkRoot, "build-tools")); }
  catch { fail("android-build-tools-unavailable"); }
  // Ignore preview versions and compare numerically: 9.0.0 sorts after 37.0.0
  // lexically. Only an installed, executable stable aapt can qualify.
  versions = versions.filter(version => /^[0-9]+\.[0-9]+\.[0-9]+$/.test(version))
    .map(version => ({ version, parts: version.split(".").map(Number) }))
    .sort((a, b) => b.parts[0] - a.parts[0] || b.parts[1] - a.parts[1] || b.parts[2] - a.parts[2]);
  for (const { version } of versions) {
    const executable = join(sdkRoot, "build-tools", version, platform === "win32" ? "aapt.exe" : "aapt");
    try {
      if (!statSync(executable).isFile()) continue;
      accessSync(executable, constants.X_OK);
      return realpathSync(executable);
    } catch { /* try the next installed stable build-tools version */ }
  }
  fail("executable-stable-aapt-unavailable");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 2) fail("aapt-resolver-takes-no-arguments");
    process.stdout.write(`${resolveAapt()}\n`);
  } catch (error) {
    process.stderr.write(`AAPT lookup failed: ${error instanceof AaptError ? error.message : "android-sdk-tool-unavailable"}\n`);
    process.exitCode = 2;
  }
}
