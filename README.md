# android

## Getting Started with Development

- Install Android Studio
- Ensure you have a Java Runtime installed (`brew install java` on Mac).
- Install and configure Warp on your computer (https://github.com/bringyour/warp).
- Make sure `BRINGYOUR_HOME` is set in your .zshrc file.
- You will need to pull down Vault and have Vault access (https://github.com/bringyour/vault).
- Install gomobile (https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile).
- Install NDK + CMake on Android Studio (https://developer.android.com/studio/projects/install-ndk). You can specify version 25.2.9519653 by clicking "Show Package Details" Settings -> Languages & Frameworks -> Android SDK -> SDK Tools.
- Pull down https://github.com/bringyour/bringyour. Inside of this repo, navigate to /bringyour/client and run `make`.

## Debugging notes

### Acceptance-test profiles

`test-main.sh` keeps the full four-flavor and peer-to-peer acceptance run as
its default. For a faster local iteration, use one of the explicit partial
profiles:

```bash
./test-main.sh --profile=smoke --headless
./test-main.sh --profile=flavor --flavor=github --headless
```

Partial profiles cannot write the authoritative acceptance result matrix.
`--skip-build` reuses an APK pair only when its fingerprint matches the source
inputs, local Gradle properties, selected flavor, and SDK artifacts. Each run
writes compact events to `tests/__acceptance__/<run>/results.ndjson`; failed
events include a redacted log excerpt, a failure classification, a suggested
debugging-model tier, and an index of the complete on-disk artifacts.

P2P acceptance requires positive terminal success from both retained
instrumentation streams: the expected single test, `OK (1 test)`, and
`INSTRUMENTATION_CODE: -1`. An app's `finish/complete` status is not a substitute
for a lost ADB instrumentation stream. Artifact collection retries only
transport/ownership unavailability, at most three times with a fresh exact-device
ownership check; each attempt and its stderr remain in the artifact directory.
Logcat snapshots retain the most recent 12,000 lines; app Go logs are also kept.

Each started session gets a bounded graceful finish and 30 seconds for natural
instrumentation exit before an ownership-checked force-stop. The host records
`p2p-first-failure.json` before forced cleanup, so an ADB interruption cannot be
misreported as an app crash merely because later cleanup stopped the process.
Missing terminal receipts still fail the arm and require a fresh run.

Reusing the peer emulator requires both exact live-child ownership and a fresh
bounded boot, shipping API/ABI, interactive-state, and network readiness pass
before package or credential changes. Ownership alone never qualifies a peer
whose previous readiness failed. Every pass retains its own
`peer-emulator/readiness-attempt.*/` receipts; the top-level `readiness.txt` and
`interactive.txt` show the latest attempt without erasing earlier failures.
An early peer-boot failure also retains its finite infrastructure cause and the
current small readiness receipts in the failed cell's `provider-readiness/`
directory; a previous flavor's successful receipt is never reused as evidence.

Package cleanup records a finite ownership/removal status even if ADB ownership
is lost before uninstall can begin. If instrumentation had succeeded,
`cleanup-failure.json` makes that infrastructure failure the primary result
cause; if the app test had already failed, cleanup stays a separate secondary
failure. Neither diagnostic changes a failed cell into a pass.

The deterministic interruption, ownership, terminal-receipt, and teardown
controls run without devices or network access:

```bash
GOMAXPROCS=2 bash test-main-p2p.test.sh
GOMAXPROCS=2 bash test-main-reporting.test.sh
GOMAXPROCS=2 bash test-main.test.sh
```

To take a screencap

```
adb -s XXX exec-out screencap -p > screen.png
```

### Generate a debug keystore

If you get an assemble error below, run the following command.

```
# android > Keystore file '$HOME/.android/debug.keystore' not found for signing config 'debug'.
cd ~/.android
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
```
