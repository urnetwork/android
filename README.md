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
