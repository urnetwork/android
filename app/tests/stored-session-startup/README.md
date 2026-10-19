# Stored-session startup boundary regression

This offline test compiles the actual `MainApplication` stored-startup,
client-startup, stale-cleanup, explicit-logout and delivered-auth-logout callback
bodies. It extracts them directly from the selected source file without changing
their predicates or effects. It also uses the source result types and failure
mapping. The remaining framework and SDK calls are inert substitutes.

The storage substitute distinguishes a checked read error from legacy getters
that hide errors as empty/nil. Explicit transitions force the first-read and
later-instance-read failures. Assertions inspect persisted synthetic auth and
settings, API authentication, actual logout calls, and the chosen client
credential. Healthy, empty/incomplete, configuration-exception, explicit logout,
delivered current rejection and the adjacent explicit client-login path are
controls. Existing logout fence wiring tests run alongside them.

From the Android repository root, with Java 21 and the pinned Kotlin 2.3.21,
JUnit 4.13.2 and support jars already in the Gradle cache:

```sh
GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off GOTELEMETRY=off \
  go run app/tests/stored-session-startup/main.go -output /tmp/android-startup-green
```

For a causal RED, export the original `MainApplication.kt` from Git to a separate
file and pass `-source /absolute/path/MainApplication.before.kt`. The same tests
must compile successfully, then fail at state-retention assertions. Compilation
failure is not RED. The runner records exact source/tool hashes, commands and
exit codes in `receipt.json`; separate output directories retain both results.
It fails if a source boundary or cached tool is missing. It does not configure
Gradle, fetch dependencies, start Android or read app storage/signing material.

This is a Kotlin owning-boundary regression, not native qualification. The
substitutes do not prove gomobile symbol availability, SDK file operations,
DeviceLocal construction, device callback ownership, Android queue ordering,
UI, VPN/profile state or wire rejection. Before release, compile the complete
app against its pinned SDK Android artifact and reproduce the storage failure
using a synthetic temporary SDK store under an inert test host. The normal
instrumentation runner starts `MainApplication`; disabling only the extender
network client does not establish a fully inert host. Explicit/current rejection
controls must also pass at the real SDK/Android boundary. No installed-device
incident cause or recovery is inferred from this harness.
