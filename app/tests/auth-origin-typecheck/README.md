# Offline authentication source qualification

This runner never evaluates Gradle or project properties and never fetches tools.
It uses a supplied real SDK AAR, cached Kotlin/Compose/dependency binaries, Android
API 36, and existing surrounding app classes. It records their exact hashes and
all compiler arguments. `support.txt` is the bounded list of current dependencies
missing from older cached app classes. It does not compile an installable app.

Run from the Android repository root:

```sh
GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off GOTELEMETRY=off \
  go run app/tests/auth-origin-typecheck/main.go \
  -sdk /absolute/path/URnetworkSdk.aar -output /tmp/auth-source -flavor google
```

Supported source flavors are `google`, `ungoogle`, `solana_dapp`, and `ethos_dapp`.
`-source-root` overlays captured preimage files over the same current dependencies
and binary classpath. Compare normalized frontend errors with the candidate:
no newly introduced diagnostics is a bounded comparison, not a successful app
compile. Existing cached resource/configuration and unrelated app symbols remain
unresolved in both trees. Resolving those requires the intended complete build.

`-narrow` compiles complete current `DeviceManager.kt`, `LoginRequest.kt`, a typed
native SDK identity/checked-key contract, and unchanged memory companion values
extracted from `MainApplication.kt`. This narrow compilation succeeds against the
real SDK binding. The generated memory boundary does not execute configuration.
The runner does not execute Android, SDK constructors, UI, callbacks, or VPN code.
