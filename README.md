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

## Updating Gradle

`app/gradle/wrapper/gradle-wrapper.properties` pins the Gradle distribution: the
wrapper checks each download against `distributionSha256Sum` and refuses a zip
that does not match. A version bump changes `distributionUrl` and
`distributionSha256Sum` together.

1. Take the new version's "Binary-only (-bin) ZIP Checksum" from
   https://gradle.org/release-checksums/ and check it against
   `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`.
2. From `app/`, run the wrapper task with both values, or edit both lines by hand:

   ```bash
   ./gradlew wrapper --gradle-version <version> --gradle-distribution-sha256-sum <sha256>
   ```

3. Whatever made the change (the wrapper task, Android Studio's upgrade
   assistant or a hand edit), check that `distributionSha256Sum` is still in
   the file and holds the new version's checksum. A stale checksum fails every
   fresh download, and a missing one turns the check off.
   `GOWORK=off go test -count=1 test-gradle-wrapper_test.go` fails when the
   checksum is missing.

The wrapper task also rewrites `app/gradle/wrapper/gradle-wrapper.jar` and the
`gradlew` scripts with those of the Gradle version that runs the task. Check a
new jar's SHA-256 against that version's "Wrapper JAR Checksum" on the same
page. The jar committed now is Gradle's wrapper jar for 6.6 to 7.0.2
(`e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`), and it
checks `distributionSha256Sum` too.

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
P2P capture has two phases: pre-finish snapshots retain the screenshot, activity,
processes, app status, Go logs and memory/diagnostic timelines. The live phase
does not read bulk logcat; `logcat-status.txt` explicitly records the deferral.
After both instrumentation children are joined, each quiescent, freshly owned
guest gets one bounded full logcat read in `<role>-after-quiescence/`, retaining
the most recent 12,000 lines. A failed full read preserves partial bytes and its
exact exit status and still fails the cell; it is not retried or substituted
for missing instrumentation success. The separate paths preserve the original
pre-finish app state.

Each started session gets a bounded graceful finish and 30 seconds for natural
instrumentation exit before an ownership-checked force-stop. The host records
`p2p-first-failure.json` before forced cleanup, so an ADB interruption cannot be
misreported as an app crash merely because later cleanup stopped the process.
Missing terminal receipts still fail the arm and require a fresh run. A dead
host ADB child does not prove the guest finished its logout/finalizers: without
positive terminal instrumentation, cleanup waits the same 30-second grace and
requires a freshly authorized, successful bounded app force-stop. Both guests
must be quiescent before API client release or ownership-marker deletion;
failed ownership/stop leaves those markers available to final cleanup.

Each P2P host join retains `<role>-instrumentation-exit.json` with its exact
shell wait status and join time (which is not the child's exit time). Owned-AVD
diagnostics additionally retain `guest-observation/<role>/before-workflow/` and,
on failed artifact collection or a lost/failed instrumentation child, one
best-effort `after-break/` observation. Both use the frozen launch PID, AVD and
instance token and a fresh ownership check. Each guest read has a 10-second
timeout with a one-second forced-exit grace and retains at most 64 KiB of
combined stdout/stderr: boot ID, uptime,
adbd PID and the last 256 log records filtered to explicit OS tags. Ownership
checks retain their existing three 15-second command limits. Every attempt
records its owner, host interval, exact read/capture statuses and truncation;
unavailable, invalid or partial evidence must not be treated as a complete
snapshot. These observations neither retry a workflow nor repair its verdict.

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
GOMAXPROCS=2 bash test-main-observation.test.sh
GOMAXPROCS=2 bash test-main-reporting.test.sh
GOMAXPROCS=2 bash test-main.test.sh
```

### Window size tests

Screenshot tests draw the connect screen and the Account and Leaderboard
list-detail layouts in four windows: compact, medium and expanded width, and
compact height (`WindowSizes.kt`), in the navigation frame MainNavHost gives
them. They run on the JVM: Robolectric's native graphics draws them and
Roborazzi compares each with its PNG baseline in
`app/app/src/testPlayDebug/screenshots/`. The sources are in
`app/app/src/testPlayDebug/java/com/bringyour/network/screenshots/`. Both
libraries are `testPlayDebugImplementation` only, so no app build resolves
them, the F-Droid (github) build included.

`./gradlew :app:testPlayDebugUnitTest` verifies them with the other JVM tests.
A mismatch fails the test and writes `<name>_compare.png` under
`app/app/build/outputs/roborazzi/`. After an intended layout change, re-record
the baselines with one command, from `app/`:

```bash
./gradlew :app:testPlayDebugUnitTest -Pscreenshots=record --tests 'com.bringyour.network.screenshots.*'
```

`-Pscreenshots=compare` writes the comparisons without failing.

The tabletop job, `ConnectTabletopTest`, folds a Pixel Fold into tabletop and
checks that connect's canvas sits above the fold and its controls below it.
It runs on a Gradle Managed Device (`pixelFoldApi36`), which Gradle creates,
boots and shuts down; `test-main.sh` runs it once per acceptance run. To run it
on its own, from `app/`:

```bash
./gradlew :app:pixelFoldApi36GithubDebugAndroidTest \
    -Pandroid.experimental.androidTest.enableEmulatorControl=true \
    -Pandroid.experimental.testOptions.managedDevices.setupTimeoutMinutes=30 \
    -Pandroid.testInstrumentationRunnerArguments.class=com.bringyour.network.ui.connect.ConnectTabletopTest
```

The first run boots the device cold to save a snapshot, which can outlast
AGP's default setup limit on a busy machine; hence the 30 minutes. Gradle keeps
the device's AVD for the next run. `./gradlew cleanManagedDevices` deletes it.

On a foldable emulator you start yourself, start it with its gRPC console
behind a token (`emulator -avd <foldable AVD> -grpc 8570 -grpc-use-jwt`):
without one, AGP refuses emulator control and Espresso Device cannot fold it.
Then name that emulator, so no other device is used:

```bash
ANDROID_SERIAL=emulator-5570 ./gradlew :app:connectedPlayDebugAndroidTest \
    -Pandroid.experimental.androidTest.enableEmulatorControl=true \
    -Pandroid.testInstrumentationRunnerArguments.class=com.bringyour.network.ui.connect.ConnectTabletopTest
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
