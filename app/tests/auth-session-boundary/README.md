# Authentication session boundary regression

The offline runner extracts these production Kotlin bodies without editing their
predicates or effects: `DeviceManager.initDevice`, its creation/configuration
helpers, `MainApplication` space replacement, accepted login, client startup,
logout and stop methods, and its constructor callback/admission prefix. It also
compiles the complete production `LoginStartupState.kt` (tracker and both login
coordinators) and the `ConnectCommands` class. The application callback prefix
ends after the successful construction check, notice reset and recovery reset;
the later unrelated listener wiring is outside this harness.

Inert framework and SDK fixtures supply controlled API responses, an explicitly
drained main queue, synthetic disposable storage and constructor results. Tests
force old rejection delivery before/after accepted login, old device callbacks
after replacement, reentrant teardown, held and queued password/client responses,
constructor exception/null and checked-key-load failures. Assertions inspect
saved auth/settings/key material, API auth, construction calls, device publication,
navigation/notices, real tracker results and connect admission. Current rejection,
pending and failed login, explicit logout, successful replacement, original-key
retry and healthy first install are controls. The stored-session-startup suite
remains an independent regression for the earlier startup retention repair.

From the Android repository root, using already cached Kotlin 2.3.21, serialization
1.11.0, coroutines 1.10.2 (test scheduler 1.9.0) and JUnit 4.13.2 jars with Java 21:

```sh
GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off GOTELEMETRY=off \
  go run app/tests/auth-session-boundary/main.go -output /tmp/android-auth-green
```

For causal RED, export the pre-fix `MainApplication.kt` and `DeviceManager.kt` from
Git and pass their absolute paths with `-source` and `-manager`. The same tests
must compile and then fail at the affected behavior. A compilation failure is not
RED. `-adjacent` additionally runs the current source's login-state, logout-fence,
quick-connect-fence and sign-in notice tests.
Every receipt binds source, fixture, generated source and cached jar hashes, exact
commands and separate compile/test exit codes. No Gradle configuration runs and
no dependencies are fetched.

This is a Kotlin owning-boundary result, not Android or native SDK qualification.
The storage fixture models checked versus tolerant reads; its constructor errors
are synthetic, not real peer-pin file errors. It does not exercise gomobile symbol
availability, SDK file locking and error propagation, actual Android dispatch,
DeviceLocal background callbacks, UI/VPN state, or server rejection. Full app
compilation against the intended SDK artifact and controlled native temporary-
store/peer-pin tests remain required. The real SDK current-rejection and explicit-
logout controls must also pass under an inert Android host before release.

The `-continuation` suite additionally compiles the original Activity finish
methods and instant-create method. It uses virtual coroutine time for delayed
navigation and a canceled model scope for one-time phrase retention. The suite
covers outer-request ownership captured before the API call, queued/repeated
results, delayed persistence/client/navigation helpers, selected-space admission,
separate native wrappers, same-key replacement, and failed login persistence.
The fixture models `NetworkSpace.same` with a distinct native identity object;
real SDK/JNI identity tests are separate evidence.

Use `-adjacent -continuation` for all 75 current boundary checks (49 existing
checks plus 26 continuation checks). The separate startup runner retains its
15 checks. To replay the continuation RED, pass the saved pre-continuation
`MainApplication.kt`, `LoginActivity.kt`, and `CreateNetworkInstantViewModel.kt`
with `-source`, `-activity`, and `-instant`. Compatibility adapters only supply
new call shapes to old owning methods; they discard the new ownership metadata
and do not introduce checks into the old policy. Preimage tests compile and fail
24/75; a candidate mutant moving session retirement after settings/API persistence
fails only its two partial-acceptance controls. The candidate passes 75/75.

The continuation evidence is retained separately in Factury's
`monitor/review-20261019-android-auth-continuation`. The earlier review artifacts
remain unchanged. Root's real SDK/JNI fixture and real-AAR narrow source compile
close their stated binding/storage boundaries; neither runs this application's
native callback, constructor, UI, or VPN integration.
