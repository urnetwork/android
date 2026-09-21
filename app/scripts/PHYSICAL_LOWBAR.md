# Physical Android low-bar capture

`physical_lowbar_capture.mjs` records a timestamped, privacy-safe NDJSON
telemetry stream beside a real-device workload. It is intended to correlate the
existing `chrome_page_benchmark.mjs` samples with the actual radio and VPN
state. It does not emulate a weak link and it does not turn a strong-signal run
into low-bar evidence.

The collector never writes raw `dumpsys` output. Android diagnostic output can
contain subscriber, carrier, cell, address, DNS, and application data. Only an
explicit allow-list is retained:

- radio technology, Android signal level, and RSRP/RSRQ/SINR-style quality;
- active VPN and underlay transport, metered/constrained/validated state, MTU,
  address-family presence, and interface names without addresses;
- battery level/power/temperature, thermal status and generic sensor values;
- URnetwork process PSS/RSS categories and non-loopback interface counters.

Use wireless debugging for battery measurements. A USB-attached run is useful
for functional validation but is invalid when `--require-unplugged` is set.
The following is an **IPv4-only capability** gate for a deliberately
IPv4-only profile; it is not the standard H1/Auto performance bracket. The
current production VPN capture configuration is dual-stack, so an ordinary H1
or Auto run must use `--require-unmetered-vpn` without
`--require-ipv4-only-vpn`; otherwise every dual-stack sample is correctly
ineligible. Retain the IPv4-only flag only when the selected profile itself
claims IPv4-only operation. This specialized gate accepts only a cellular,
active, unmetered, IPv4-only VPN at Android signal level one or below:

```sh
node app/scripts/physical_lowbar_capture.mjs \
  --serial DEVICE \
  --label auto-cell-lowbar-01 \
  --duration-seconds 180 \
  --interval-ms 1000 \
  --require-cellular \
  --require-unmetered-vpn \
  --require-ipv4-only-vpn \
  --max-signal-level 1 \
  --require-unplugged \
  --output auto-cell-lowbar-01.telemetry.ndjson
```

Run the page workload concurrently after forwarding Chrome's DevTools socket:

```sh
adb -s DEVICE forward tcp:9222 localabstract:chrome_devtools_remote
node app/scripts/chrome_readiness.mjs --serial DEVICE --port 9222 \
  --label page-control --output "$PRIVATE_DIR/chrome-readiness.json"
# Only exit 0 permits the workload. Keep the same forward active.
node app/scripts/chrome_page_benchmark.mjs \
  --port 9222 \
  --runs 5 \
  --fresh-context \
  https://www.wikipedia.org/
```

Use the dedicated harness for a canonical real fast.com run:

```sh
node app/scripts/chrome_fast_benchmark.mjs \
  --port 9222 \
  --timeout-ms 90000
```

Its displayed value is Fast.com's aggregate result. The emitted page request
and encoded-byte fields are diagnostic only because Chrome may move the bulk
downloads to worker or child targets. Bracket the run with the SDK's H1 ingress
counter when exact tunnel bytes are required. The harness never emits request
URLs, response headers, or Fast.com's ephemeral signed download tokens.
Only exit 0 with `valid=true` and `completed=true` is a speed measurement.
Exit 2 preserves the same aggregate JSON with `failureReason` when the display
is empty/nonpositive or its units are invalid, the deadline passes before a
stop/stable result, or no nonempty page response completed. A transient number
followed by an empty display cannot pass. The existing stable-display fallback
is recorded separately from a stopped indicator in `completionReason`; this is
not an added minimum speed or worker-byte threshold. Retain unsuccessful runs
in the failure denominator instead of turning them into zero Mbps, dropping
them, or substituting a retry.

For a video failure, attach to the already-open page target and prove whether
the media clock advances:

```sh
node app/scripts/chrome_video_probe.mjs \
  --port 9222 \
  --target-id TARGET_ID \
  --timeout-ms 45000
```

Use `--reload` to instrument one cache-disabled reload, or `--navigate URL`
to install instrumentation before a single navigation. The probe emits only
document/video readiness, clock/buffer/error state, response hostname/status,
protocol and Chrome connection metadata. It never emits request paths, headers,
cookies, manifests or signed tokens. Exit code 0 means the first video advanced
by at least one second at readiness 2 or higher; exit code 2 means it did not.
Observation starts as soon as navigation commits, without waiting for every
ad/telemetry subresource to finish loading the document. Check
`navigationCommitted`, `observationStatus`, and `sampleCount` before interpreting
readiness: `navigation-timeout` or `observation-timeout` with zero samples is
incomplete observation, not a sampled readyState-zero player or a transport
verdict. An observed robot challenge with no video is an application denial.
The probe observes playback; it does not click a play/consent button or bypass
browser autoplay policy.

Interpret `rejectedConnections` at the transport boundary. A later response
with the same Chrome `connectionId` is another HTTP request multiplexed on the
same established H2/TLS connection and cannot cause MultiClient to choose a new
provider. Only `retryOnNewConnection=true` proves that the browser created a
fresh transport placement opportunity. An encrypted HTTP 403 is ordinary
returned network traffic to Connect and must not be classified as a packet
blackhole.

Each telemetry sample includes `eligibility.eligible` and exact invalidation
reasons. The final summary reports valid sample count, signal range, peak app
memory and thermal status, battery endpoints, and interface-counter deltas.
For variable-length real traffic, set a generous `--duration-seconds` ceiling
and pass `--stop-file`; creating that host marker after the required recovery
window ends the capture cleanly and still emits its summary.
Keep the benchmark output, telemetry NDJSON, a header-only device capture, and
the matching edge capture under the same opaque run label. Do not put device
serials, subscriber/carrier identifiers, cell identities, IP addresses, DNS
answers, URLs containing user data, or packet payloads into checked-in results.

For a comparable campaign, alternate Direct, H1, H3, Auto, and P2P where
available on the same physical route, use fresh app/browser processes, and
record failures and readiness time rather than retrying them out of the sample.
Use `--require-wifi` for Wi-Fi-underlay cells (it is mutually exclusive with
`--require-cellular`), and use `--require-no-vpn` for Direct controls.
Production Android Chrome does not support DevTools
`Target.createBrowserContext`; restart Chrome before each candidate, **after
the bracket's role and retained collector readiness gates below**, and omit
`--fresh-context` there. The benchmark still disables and clears the browser
cache before every measured run. After the caller's force-stop/restart and
forward setup, use the executable readiness gate before warm-up or workloads:

```sh
node "$ROOT/android/app/scripts/chrome_readiness.mjs" \
  --serial "$SERIAL" --port "$CDP_PORT" --label "$LABEL" \
  --output "$PRIVATE_DIR/chrome-readiness.json"
# Only exit 0 permits traffic. Exit 2 takes the common failed-readiness
# finish/join/credential cleanup path; preserve the JSON and exit status.
```

Do not substitute manual `curl` probes or a fixed sleep under `set -e`.
Immediately after Chrome launch, `/json/version` can close with zero bytes
(curl exit 52) even though a later response is valid. The helper retries
within a fixed **30-second deadline**, then requires two complete valid Chrome
JSON responses at least **5,000 ms apart**, identifying the same browser
instance. An empty, failed, truncated or malformed response invalidates the
pending pair; a replacement browser starts a fresh five-second pair within
the same deadline. Neither one valid response nor matching version strings
alone qualifies. A restart/replacement is recorded, not hidden.

Before and after each bounded local HTTP request, the helper verifies the
existing `tcp:$CDP_PORT` forward maps to this serial's
`localabstract:chrome_devtools_remote`. A different device/socket fails closed.
Read-only `pidof com.android.chrome` checks also require one unchanged browser
process before/after each response and across the stable pair. This is needed
because Android can return the tokenless `/devtools/browser` websocket path;
that path alone is not process identity. Missing/multiple PIDs cannot qualify.
The helper never discovers another device, recreates a forward, restarts Chrome, or
requests a public website. Do not change that forward between readiness and
the workload. Its exclusive mode-0600 result contains version/protocol and
aggregate timing/rejection counters, never raw responses, user agents, serials,
or debugger URLs/tokens. Failed results are retained too. A yielded host call
still needs to be joined to terminal status; the deadline is not a success.

For iOS-profile memory work, build the app and Android-test APK with both
`-PurnetworkMemoryProfile=ios-memory-audit-v1` and a unique
`-PurnetworkAcceptanceBuildId=LABEL`; the Gradle default is Android's 40-MiB
profile and is invalid for this campaign. Run
`PhysicalLowbarSessionTest` with the same `acceptanceBuildId` instrumentation
argument, after the credential-staging gate below has passed. The test keeps
one authenticated process alive and drains the SDK's
fixed primitive ring every five seconds. The Go sampler records every 15
seconds without constructing gomobile exit/status/list graphs; the faster host
drain only publishes newly available records. The test accepts private
`ID|VERB|ARG` commands through its app-data acceptance directory. Useful verbs
are `phase`, `connect` (`h1`, `h3`, or `auto`),
`provide`, `peer-connect` (same-network P2P) and `peer-platform-connect`
(fixed provider over the selected platform carrier), both with optional `h1`,
`h3`, or `auto`, `disconnect`, `stop-provide`, `snapshot`,
`heap-profile`, `owner-census`, `goroutine-stacks`, `trim-memory`, and `finish`.
The three diagnostic file commands are explicitly opt-in and cannot qualify a
performance/memory acceptance arm. `finish` is required: it joins the
sampler, writes `physical-summary.json`, disconnects both roles, and logs out.
The host must own this instrumentation command for the entire session: launch
it in a retained PTY/session or supervised process and join it after `finish`.
Do not background `adb shell am instrument` from a one-shot shell, whose exit
can terminate the test before it creates `files/acceptance/physical-status`.
Poll status only through `adb -s <allowlisted-serial> shell run-as
com.bringyour.network cat files/acceptance/physical-status`; an absent file
after the retained owner exits is a readiness failure, not an empty successful
session. On that failure, preserve the owner exit output and remove the private
credential and command files before a persistent-owner retry.

Native provenance is a separate pre-traffic gate. A fresh acceptance build ID
only proves the app/test wrapper; `assembleGithubDebug` does not rebuild the
native SDK dependency. Freeze SDK/Connect/replacement-module revisions and
dirty-input hashes, explicitly run `:app:buildSdkAcceptance` with a unique
`URNETWORK_ANDROID_SDK_BUILD_OWNER` and the intended
`urnetworkMemoryProfileRateBytes`, then assemble both APKs. Use the SDK output
consumer-lock plus `.build-owner` verification in `android/test-main.sh` to
exclude an intervening writer and retain private AAR/app/test copies and
SHA-256 digests before releasing the lock. Record the packaged ABI's native
library digest in AAR and APK, prove their linkage through any stripping step,
verify the installed APK against the retained copy, and recheck source-input
hashes. A missing native build/identity chain is `INVALID_NATIVE_PROVENANCE`
for candidate attribution, even when the profile passes and Kotlin tasks ran.
The lBOYKS diagnostic arm had up-to-date JNI merge tasks without retained native
artifacts; its 29,900,832-byte runtime peak cannot be attributed to the current
ClientStrategy candidate. Its memory breach still counts as observed failure.

Use `physical_native_provenance.mjs` for the source-input half of this gate;
manual revision lists are insufficient. Follow the exact before-build,
after-build and verify/check commands in `tests/RUN-PERF.md` (native provenance).
Before the explicit SDK build, freeze `URNETWORK_ANDROID_SDK_BUILD_OWNER`,
`ACCEPTANCE_BUILD_ID`, `NATIVE_PROFILE_RATE` (0 normally; 65536 only for approved
owner diagnostics), the build PATH/GOWORK/GOFLAGS environment, and the private
`NATIVE_INPUTS_BEFORE`, `NATIVE_INPUTS_AFTER`, `NATIVE_INPUTS_PROOF` and
`NATIVE_WRITER_RECEIPT` paths. Capture `before`, then launch
`physical_native_writer.sh` with explicit build ID, rate,
`--memory-profile ios-memory-audit-v1`, bounded `--max-workers`, and private
receipt/stdout/stderr paths using RUN-PERF's exact standalone retained call.
The Bash entry point supervises the fixed `:app:buildSdkAcceptance` task and
atomically writes its own terminal receipt after child outcome/join. Do not
assign an outer-shell `status` or manually write the receipt: zsh reserves that
name, which caused `terra_proof_arm` to lose its writer evidence. Failed or
interrupted writer receipts never authorize consumption; absent terminal
evidence is incomplete setup. Raw child logs and receipt remain private 0600.
The default executor is zsh, including commands **before** `exec bash`.
Restore a saved arm using RUN-PERF's exact `NATIVE-CONTEXT` scalar-read block
before **each** retained writer and consumer call. Its private `arm-identifiers`
record has exactly `LABEL\nBUILD_ID\n`. Freeze/restore the explicit profile rate,
worker limit and tool environment too. Never use `readarray`, `mapfile`, arrays,
`eval`, or source artifact text; a Bash-only outer restore caused the fresh
`xRWdea` arm to fail before writer spawn. No caller `bash -lc` override is
required. The regression suite executes those exact documented blocks under
zsh, through the actual writer and consumer with a temporary fixture lock.
After successful writer join, use **only**
`physical_native_consumer.sh --root ... --before ... --after ... --proof ...
--writer-receipt ... -- <assembly/copy/ABI-linkage command>` for the post-writer consumer phase.
It acquires the existing consumer output lock itself, automatically captures
`after` and verifies/publishes the proof before invoking that command, and
holds the lock through command join and APK/AAR retention/linkage. Do not
assemble first and manually remember after/verify later (the CXAusf failure).
The consumer command must not rebuild the SDK, install, stage credentials or
perform any device work. Use the exact retained/private-log invocation in
RUN-PERF; the wrapper forwards argv without eval and preserves the caller cwd.
Missing/stale source evidence, missing after/proof, or invalid/lost lock ownership
fails with a fixed `…-no-consumer-spawn` reason; never continue or reuse partial
artifacts. The consumer also rejects missing/nonzero/altered writer receipts;
it binds the zero exit and joined outcome to the original before manifest,
explicit profile/task arguments and current helper/private-log hashes.
Canonical log paths remain valid through an ancestor alias such as macOS
`/tmp` -> `/private/tmp`; the guard accepts only the original or bound canonical
direct parent and rechecks the original directory's device/inode/canonical
identity. The `WhH7v8` consumer failure was this lexical-alias bug: the receipt,
helper hashes and private logs were intact. It did not show source mutation.
The directory helper is now a frozen manifest and writer-receipt input too.
After any selected-source/helper change, use a fresh independent arm and before
capture; never retry a failed arm by reusing its writer receipt or partial proof.
Run `check --proof ... --build-id ...` immediately before first device
install/launch; the retained AM supervisor requires `--native-inputs` and
rechecks current source linkage itself before spawning adb.

The owned mode-0600 manifests contain separate SHA-256s for actual ABI-selected
Connect/SDK/dependency Go/embed/native code, plus gomobile JNI/runtime packages
and copied Go/C/header/Java support templates; resolved `go.mod` and local
`go.sum` inputs; the **`sdk/build/go.mod`** main module and complete per-ABI
`go list -m all` replacement graph used by gomobile's generated module;
explicit workspace none/off or `go.work`/optional `go.work.sum` and resolution;
build/tool support hashes; and repository revisions plus selected-input dirty
hashes. Missing optional sum files are explicit states, never omitted fields.
Metadata lookup is offline/read-only and fails if dependencies/tools are not
already set up. Tests/generated output are excluded from the source closure.
`verify` rejects missing fields, any before/after/current drift, or stale
`.build-owner`; its proof must be in `ARTIFACT_DIR`. Keep manifests private and
report only fixed reasons/booleans, never paths/raw subprocess output.
This does not attest binaries, strip steps, installed APKs or locks; retain the
separate full native chain above and do not retrofit missing historical hashes.

### Retained instrumentation owner

After the attested APK pair is installed and private credential staging has
passed, full H1/Direct arms must use the dedicated AM supervisor below. This is
a standalone retained foreground PTY call (`tty:true`); keep/resume its exact
executor session through normal `finish` and terminal join. Do not append `&`,
redirect the supervisor terminal, substitute a one-shot shell, or discard the
session on tool yield. The helper owns the private child stdout/stderr itself.

```sh
# INSTRUMENTATION executor call: retained PTY; no later commands in this call.
: "${ROOT:?workspace root required}" "${RUN_DIR:?owned mode-0700 arm root required}"
: "${ARTIFACT_DIR:?restore the frozen run-root/private path from staging}"
: "${SERIAL:?pinned serial required}" "${LABEL:?frozen arm label required}"
: "${ACCEPTANCE_BUILD_ID:?exact attested APK build ID required}"
: "${NATIVE_INPUTS_PROOF:?verified private native input proof required}"
# Freeze this exact path across all later executor calls, even if a workload
# uses a different PRIVATE_DIR. Do not manufacture the receipt yourself.
umask 077
node "$ROOT/android/app/scripts/physical_artifact_directory.mjs" check \
  --directory "$ARTIFACT_DIR" >"$RUN_DIR/$LABEL.directory-before-instrumentation.json" || exit 2
INSTRUMENTATION_OWNER="$ARTIFACT_DIR/$LABEL.instrumentation-owner.json"
exec node "$ROOT/android/app/scripts/physical_collector_session.mjs" run-instrumentation \
  --artifact-dir "$ARTIFACT_DIR" \
  --native-inputs "$NATIVE_INPUTS_PROOF" \
  --credential-ownership "$ARTIFACT_DIR/$LABEL.credential-owner.json" \
  --owner "$INSTRUMENTATION_OWNER" \
  --stdout "$ARTIFACT_DIR/$LABEL.instrumentation.stdout" --stderr "$ARTIFACT_DIR/$LABEL.instrumentation.stderr" \
  --serial "$SERIAL" --label "$LABEL" --build-id "$ACCEPTANCE_BUILD_ID"
```

The fixed class is `PhysicalLowbarSessionTest`; the default component is the
normal `AndroidJUnitRunner`. This is the same instrumentation workflow, not an
auth/readiness bypass. Its private mode-0600 `instrumentation-session` receipt
is created only after the retained supervisor and spawned adb child are live.
Before any host owner inspection/stream open/adb spawn, it verifies the artifact
directory is an owned mode-0700 directory (no symlink). Owner, ready,
terminal and child-log paths must be direct children of that same directory.
It rechecks directory identity before opening and immediately before spawning;
removal/replacement/mode changes produce a fixed
`artifact-directory-…-no-spawn` reason, and mismatched paths produce
`artifact-path-outside-directory-no-spawn`. It never recreates missing evidence.
Keep the check result in the run root so an absent `private/` cannot destroy its
own failure record. Preserve supervisor exit/status under the same run root;
this is `INVALID_SETUP`, not an instrumentation/auth failure.
It binds their process-start/command identities, foreground state, arm label,
serial hash, runner component/class and target package. No raw host command line
is retained. After child join, `.terminal.json` records the real exit/signal and
interruption; even a clean exit disqualifies subsequent role/collector startup.

Once the existing readiness wait observes the same live session's `ready`
status, bind its target PID exactly once from another executor. A yielded
launcher is not readiness. This read-only binding does not wait for login or
retry a failed session; missing/not-ready status remains failed setup.

```sh
umask 077
: "${INSTRUMENTATION_OWNER:?restore the exact supervisor owner path}"
node "$ROOT/android/app/scripts/physical_collector_session.mjs" bind-instrumentation-ready \
  --owner "$INSTRUMENTATION_OWNER" --serial "$SERIAL" \
  >"$ARTIFACT_DIR/$LABEL.instrumentation-ready-proof.json" || exit 2
```

The helper creates `.ready.json` (0600) only while both host processes retain
their original identities and `adb shell pidof com.bringyour.network` contains
the PID read from target `run-as ... cat physical-status`, before and after
that read. The PID stays private in the ready receipt. Guard output contains
only sanitized primitives; publish booleans/reasons, never raw PIDs, serials,
command lines or receipt fingerprints. Do not manufacture/rebind an owner to
rescue an old arm. The test APK executes in the target app; a separate
`com.bringyour.network.test` process is **not required**. `run-as kill -0` is
not used: signal permission is not the ownership contract.

Before connecting or driving public traffic, capture the retained owner's
fresh ready status and require the **offline** profile gate to exit 0. The
effective values must be exactly 20-MiB device admission and 32-MiB Go soft
limit; a smaller observed runtime is not a substitute. A missing build flag
selects Android's 28/40-MiB policy and makes the cohort incomparable. A unique
build ID by itself does not prove the memory profile.

```sh
umask 077
node app/scripts/physical_quiet_gate.mjs --serial "$SERIAL" \
  --capture-status "$PRIVATE_DIR/ready-profile-status.json"
node app/scripts/physical_memory_profile.mjs \
  --status "$PRIVATE_DIR/ready-profile-status.json" \
  >"$PRIVATE_DIR/memory-profile-gate.json"
# Only exit 0 permits connect/traffic; otherwise preserve INVALID_MEMORY_PROFILE
# and take the common finish/credential-cleanup path. Do not retry into the row.
```

This read-only capture is preflight evidence, **not a quiet boundary**. The
quiet gate independently rechecks both boundaries' admission/soft-limit inputs
and every primitive sample's soft limit, including active and teardown samples.
A profile mismatch never suppresses a measured >24-MiB failure; it additionally
disqualifies baseline comparison. Neither gate changes the app's budgets.

### H1 and Direct startup order

For **H1**, the order is retained instrumentation ready/bound → profile gate →
completed `connect|h1` → retained `--require-unmetered-vpn` collector ready →
fresh Chrome/forward/readiness → receipt-owned workloads → connected quiet.
Do not start a VPN-required collector while waiting to issue `connect`: its
readiness correctly requires the VPN that has not been established yet.
The in-app primitive sampler is already running during connection setup; retain
those startup samples separately. A post-connect host collector does not prove
host telemetry coverage of connection setup, but must cover **all** Chrome and
public workloads, drain and quiet without gaps.

For **Direct**, leave both VPN roles off, prove the disconnected session, then
start/check `--require-no-vpn` collection **before** fresh Chrome/readiness or
any workload. Do not connect H1 to make Direct readiness pass. Use separate fresh
collector/receipt artifacts per bracket; never change a live collector's policy.

After the profile gate, use this prerequisite block in a normal retained/joined
executor call. `SESSION_MODE` is exactly `h1` or `direct`. For H1, freeze a fresh
safe `CONNECT_ID` (for example `h1-` plus a UUID) and retain its exact value for
the collector call. The instrumentation command stream must have one owner and
be idle before publication. The existing `connect` verb uses best-available
exit selection: a completed H1 command proves carrier policy/tunnel, **not** US
egress; retain the separate location/egress evidence required by the campaign.

```sh
umask 077
: "${ROOT:?workspace root required}" "${PRIVATE_DIR:?fresh private arm directory required}"
: "${SERIAL:?pinned serial required}" "${SESSION_MODE:?h1 or direct required}"
: "${INSTRUMENTATION_OWNER:?restore the exact supervisor owner path}"
case "$SESSION_MODE" in
  h1)
    : "${CONNECT_ID:?fresh frozen H1 command ID required}"
    case "$CONNECT_ID" in *[!A-Za-z0-9._-]*) exit 2 ;; esac
    printf '%s|connect|h1\n' "$CONNECT_ID" |
      adb -s "$SERIAL" shell run-as com.bringyour.network tee \
        "files/acceptance/physical-command.$CONNECT_ID" >/dev/null || exit 2
    adb -s "$SERIAL" shell run-as com.bringyour.network mv \
      "files/acceptance/physical-command.$CONNECT_ID" files/acceptance/physical-command || exit 2
    node "$ROOT/android/app/scripts/physical_collector_session.mjs" check-role \
      --serial "$SERIAL" --session-mode h1 --connect-command-id "$CONNECT_ID" \
      --instrumentation-owner "$INSTRUMENTATION_OWNER" \
      >"$PRIVATE_DIR/role-ready.json" 2>"$PRIVATE_DIR/role-check.stderr" || exit 2
    ;;
  direct)
    node "$ROOT/android/app/scripts/physical_collector_session.mjs" check-role \
      --serial "$SERIAL" --session-mode direct \
      --instrumentation-owner "$INSTRUMENTATION_OWNER" \
      >"$PRIVATE_DIR/role-ready.json" 2>"$PRIVATE_DIR/role-check.stderr" || exit 2
    ;;
  *) exit 2 ;;
esac
```

Join `check-role` to exit 0 **before** the collector call. It performs bounded
read-only host-owner/target-`pidof`/target-`run-as cat` checks, waits for that exact
command's complete status in the original ready-bound target process, and checks H1 mode, connected
tunnel and disabled provider. It neither connects nor starts a collector.
Its 150-second prerequisite deadline accommodates the existing 120-second app
connect wait plus command polling; no telemetry/coverage timeout is extended.
Error, malformed/missing status, process replacement or a competing command
fails setup. Direct is an immediate disconnected-role check, not a VPN wait.

Retain `role-check.stderr` under the same mode-0700 directory with mode 0600.
A failed status read now emits one fixed-classification JSON record alongside
the failure reason: transport/exit category, output byte counts, status-schema
failure category, and ready-target continuity booleans. It never prints raw
status, stderr, IDs, serials or paths, and never retries the failed read. A
`status-path-missing` result means only that the read saw a missing path; even
with the same live target before/after, it does **not** prove a publication race.
The invalid `DQlHyJ` arm retained successful idle diagnostic receipts but not
the failed read's underlying result, so its cause cannot be assigned between
ADB/read failure, a status predicate failure, or the test writer's known
delete-before-rename gap. Its H1 `tee` then `mv` command has no nested `sh -c`
quoting contract. Do not patch over missing evidence by accepting stale status,
retrying into the cohort, or changing the app based only on this hypothesis.

### Mandatory quiet-window evidence gate

After the H1/Direct prerequisite above and before Chrome or any workload, start
the collector in a retained owner, retain its exact
PID, and wait for at least one fresh eligible telemetry sample. Keep it alive
through the final quiet gate. Starting it after Wikipedia/fast.com is
`INCOMPLETE_ACTIVE_COVERAGE`, even if all five quiet minutes are captured.

Use `physical_collector_session.mjs` for the retained launch. Start `run` below
as its own foreground PTY/session (`exec_command` with `tty:true` and a short
yield); keep the returned session ID until it terminates. Do not append `&`,
use a one-shot background shell, or redirect the owner itself. The helper
requires a foreground terminal and creates the child's private output files.
It never detaches/restarts a collector or changes a coverage threshold.

```sh
# COLLECTOR executor call: only after check-role exited 0; retained PTY only.
# This command remains running throughout Chrome, workload and quiet coverage.
# Use fresh paths in an existing mode-0700 arm directory. Set the same frozen
# variables again in later executor calls; shell assignments are not global.
: "${ROOT:?workspace root required}" "${PRIVATE_DIR:?private arm directory required}"
: "${SERIAL:?pinned serial required}" "${LABEL:?frozen cell label required}"
: "${TELEMETRY:?fresh telemetry path required}"
: "${INSTRUMENTATION_OWNER:?restore the exact supervisor owner path}"
: "${UNDERLAY_FLAG:?underlay eligibility required}" "${SESSION_MODE:?h1 or direct required}"
set -- --session-mode "$SESSION_MODE"
case "$SESSION_MODE" in
  h1) VPN_FLAG=--require-unmetered-vpn
    set -- "$@" --connect-command-id "${CONNECT_ID:?same acknowledged H1 command ID required}" ;;
  direct) VPN_FLAG=--require-no-vpn ;;
  *) exit 2 ;;
esac
exec node "$ROOT/android/app/scripts/physical_collector_session.mjs" run \
  --owner "$PRIVATE_DIR/collector-owner.json" \
  --instrumentation-owner "$INSTRUMENTATION_OWNER" \
  --stdout "$PRIVATE_DIR/collector.stdout" --stderr "$PRIVATE_DIR/collector.stderr" "$@" -- \
  --serial "$SERIAL" --label "$LABEL" --duration-seconds 1800 --interval-ms 1000 \
  "$UNDERLAY_FLAG" "$VPN_FLAG" --output "$TELEMETRY" --stop-file "$PRIVATE_DIR/collector.stop"
```

The public H1/Direct block requires `--session-mode`; `run` rechecks the current
live supervisor, adb child, ready-bound target PID and role immediately before spawning, so a stale saved prerequisite cannot
start a pre-connect collector. Omitting this optional argument is only for the
existing general-purpose collector protocol (e.g. separately specified provider
roles), not permission to skip this public-block guard.

After that call **yields a still-live session**, check from another executor:

```sh
collector_pid=$(node "$ROOT/android/app/scripts/physical_collector_session.mjs" check \
  --owner "$PRIVATE_DIR/collector-owner.json" --timeout-ms 15000) || exit 2
```

The check returns a PID only while both recorded owner and collector are live
and the unchanged existing telemetry eligibility/freshness check passes.
Only then force-stop/start Chrome, establish its fresh forward, and join the
two-response `chrome_readiness.mjs` gate. Do not open/restore public pages before
collector readiness, or issue another connection command between these checks.
Repeat this check immediately before the workload-owner command after Chrome
readiness. The `LqIam9` failure emitted seven eligible samples before its
one-shot launcher died: that prefix is not coverage and cannot qualify. An
owner failure takes the common failed-arm cleanup; no restart into the same
artifact paths is allowed. A PTY is not durable if the caller discards its
session, so retain it through finish/stop-file cleanup and join its terminal
result. Preserve `collector-owner.json.terminal.json` (mode 0600). A clean
collector exit does not override memory, network, or workload failures.

Run the frozen probes through `physical_workload_receipt.mjs`: it predeclares
their order, owns the foreground command, and emits a mode-0600 receipt only
after every named child is joined and explicit browser cleanup is verified.
The owner itself must be a **standalone retained foreground PTY session**. It
checks input/output TTYs plus live, non-stopped foreground process/terminal
identity before collector reads, owner publication or any workload launch.
One-shot/piped/redirected owners fail `retained-workload-foreground-pty-required`;
background/no-terminal or dead owners fail `workload-owner-not-foreground` or
`workload-owner-not-live`. Treat these as `INVALID_WORKLOAD_OWNER` before traffic,
retain the fixed reason/exit status, and take common cleanup without retrying
that row. Do not wrap the owner in a new one-shot pseudo-terminal as a workaround.

Its private `retainedOwner` proof contains a hash of process-start/terminal
identity and foreground state, not raw `ps` output, terminal names or commands.
Each child/cleanup wrapper rechecks the same live/foreground owner before launch
and before its completion receipt. Dead/reused PID, changed terminal or lost
foreground invalidates progress. Keep IDs/hashes private; no missing proof may
be fabricated. This is current ownership, not a promise that the caller will
keep the executor: discarding it or SIGKILL can still strand in-flight work.
Missing/failed terminal evidence remains invalid and cannot authorize quiet.
It checks collector PID, same file/first sample, fresh eligible tail, and gaps
before and after every child. Do not pass a shell/executor session ID as a PID.
The receipt is bound to the block label and serial hash. A yielded executor,
live process group, omitted child, interruption, or missing cleanup cannot
produce a qualifying receipt. Never synthesize that JSON or reuse another arm.

The workload body is a private shell file, not a new benchmark runner. It wraps
the existing commands unchanged. For the normal public block, use the exact
child list `wiki,fast-1,fast-2,fast-3,cnn,bloomberg` and this pattern:

Before writing the body or **any** label-local stdout/stderr/receipt, create the
exact fresh label directory under the already validated mode-0700 private root.
Do this during arm setup, before native build/instrumentation/traffic:

```sh
umask 077
: "${PRIVATE_DIR:?exact private LABEL directory required}"
node "$ROOT/android/app/scripts/physical_artifact_directory.mjs" create \
  --directory "$PRIVATE_DIR" || exit 2
# Only after the eligible directory result: write traffic-workload.sh there.
```

This helper creates only a missing leaf as 0700; it never adopts/repairs an
existing 0755 directory or symlink. Do not let an editor/patch implicitly create
the label directory with its default permissions. Keep the fixed directory
result on the retained host output until the destination has been validated.

The currently scoped iOS owner-diagnostic arm uses only
`wiki,fast-1,fast-2,fast-3` plus cleanup: omit the CNN/Bloomberg commands below.
Before any traffic, validate the exact cleanup invocation offline:
`node "$RECEIPT" validate -- cleanup --serial "$SERIAL"`. This only parses
arguments; it never connects to a device or stops a browser. The workload must
later use `node "$RECEIPT" cleanup --serial "$SERIAL"` exactly. Cleanup is a
built-in verified operation, **not** a wrapper accepting `-- adb ...`.

```sh
# Private traffic-workload.sh; inherited variables must be exported by owner.
# Do not use set -e: ordinary probe failures remain in their child receipts,
# while completing diagnostic quiet memory capture. Missing/interrupted child
# receipts still reject the owner; they cannot be bypassed by the shell.
set -u
node "$RECEIPT" child --name wiki -- node "$SCRIPTS/chrome_page_benchmark.mjs" \
  --port "$CDP_PORT" --runs 5 https://www.wikipedia.org/ >"$PRIVATE_DIR/wiki.jsonl" 2>"$PRIVATE_DIR/wiki.stderr"
for n in 1 2 3; do
  node "$RECEIPT" child --name "fast-$n" -- node "$SCRIPTS/chrome_fast_benchmark.mjs" \
    --port "$CDP_PORT" --timeout-ms 90000 >"$PRIVATE_DIR/fast-$n.json" 2>"$PRIVATE_DIR/fast-$n.stderr"
done
node "$RECEIPT" child --name cnn -- node "$SCRIPTS/chrome_video_probe.mjs" \
  --port "$CDP_PORT" --navigate "$CNN_URL" >"$PRIVATE_DIR/cnn.json" 2>"$PRIVATE_DIR/cnn.stderr"
node "$RECEIPT" child --name bloomberg -- node "$SCRIPTS/chrome_video_probe.mjs" \
  --port "$CDP_PORT" --navigate "$BLOOMBERG_URL" >"$PRIVATE_DIR/bloomberg.json" 2>"$PRIVATE_DIR/bloomberg.stderr"
# Explicitly stop Chrome, including background targets; verify pidof is empty.
# This does not stop/restart the VPN or instrumentation.
node "$RECEIPT" cleanup --serial "$SERIAL" >"$PRIVATE_DIR/chrome-cleanup.stdout" 2>"$PRIVATE_DIR/chrome-cleanup.stderr"
```

For the video commands above, `--navigate` without `--target-id` creates an
owned `about:blank` page, attaches the probe before navigating, and closes only
that page on success or failure. It must not select an arbitrary existing tab.
An explicit `--target-id` preserves the existing manual-probe behavior and
leaves that caller-owned page open. A CLI/setup exit 1 is a harness failure,
not a playback result; retain it and do not count the site as tested.
Likewise, exit 2 with zero observations must remain incomplete media evidence,
even if unrelated telemetry received HTTP responses. Keep an observed document
403/challenge distinct from media transport errors.

After profile → H1/Direct role → collector → Chrome readiness, launch the workload
owner as a **new standalone retained PTY call** (`tty:true`), not a continuation
of the readiness executor. Restore the exact arm variables; keep input/output
attached, use `exec`, and retain/resume its exact session through terminal join.
No `&`, owner redirection/piping, one-shot shell or later commands in this call.
Per-child redirection in `traffic-workload.sh` remains unchanged:

```sh
# WORKLOAD executor call: retained foreground PTY; no later commands in this call.
umask 077
: "${ROOT:?workspace root required}" "${SERIAL:?pinned serial required}" "${LABEL:?frozen arm label required}"
export SERIAL CDP_PORT PRIVATE_DIR CNN_URL BLOOMBERG_URL
export SCRIPTS="$ROOT/android/app/scripts"
export RECEIPT="$SCRIPTS/physical_workload_receipt.mjs"
# Do not combine this assignment with the `node "$RECEIPT"` command: shell
# expansion happens before an inline assignment takes effect and can otherwise
# start Node's interactive REPL. These guards must execute before any workload.
: "${PRIVATE_DIR:?private workload directory required}"
: "${SCRIPTS:?Android script directory required}"
: "${RECEIPT:?workload receipt helper required}"
node "$SCRIPTS/physical_artifact_directory.mjs" check \
  --directory "$PRIVATE_DIR" || exit 2
node "$RECEIPT" script-preflight --label "$LABEL" \
  --output "$PRIVATE_DIR/workloads.json" \
  >"$PRIVATE_DIR/script-preflight.stdout.json" \
  2>"$PRIVATE_DIR/script-preflight.stderr" || exit 2
collector_pid=$(node "$SCRIPTS/physical_collector_session.mjs" check \
  --owner "$PRIVATE_DIR/collector-owner.json" --timeout-ms 15000) || exit 2
exec node "$RECEIPT" owner-script --serial "$SERIAL" --label "$LABEL" \
  --collector-pid "$collector_pid" --telemetry "$TELEMETRY" \
  --children wiki,fast-1,fast-2,fast-3,cnn,bloomberg \
  --output "$PRIVATE_DIR/workloads.json"
# A tool yield means STILL RUNNING. Resume/join this exact owner to exit 0.
# If it exits nonzero, finish/clean up the failed attempt; do not start quiet.
```

Use `owner-script` for physical arms: it derives `traffic-workload.sh` as the
exact direct child of the frozen label directory instead of accepting a second
handwritten path. For this layout `PRIVATE_DIR` must end in `LABEL` and the
output must be `PRIVATE_DIR/workloads.json`. It also supports RUN-PERF's
`private/LABEL.workloads.json` output, deriving `private/LABEL/traffic-workload.sh`.
The private directory and its parent must already be owned mode 0700; the body
must be an owned regular file, not a symlink or group/other-writable file.
No missing path is created or repaired. The invalid `1eGmda` arm selected a
different label's body path; this is now rejected before collector inspection
or body launch, rather than becoming an outer shell error.

The offline preflight exclusively writes `workloads.json.script-preflight.json`
(0600), binding the directory, script inode/hash and actual final built-in
cleanup line. Keep that line exactly as shown above, optionally with
`>"$PRIVATE_DIR/chrome-cleanup.stdout" 2>"$PRIVATE_DIR/chrome-cleanup.stderr"`.
It cannot be a comment, `cleanup -- adb ...`, or followed by `|| true`/another
command. Preflight failure is `INVALID_WORKLOAD_SCRIPT`; untrusted/missing
parents may prevent file publication and retain only the fixed CLI failure.
The owner rechecks the same binding immediately before spawn, derives
`PRIVATE_DIR`, `SERIAL`, `RECEIPT`, and `SCRIPTS` for the body, and does not accept
a replacement script or failed preflight. Preserve
`.script-preflight.failed.json` on recheck failure. These guards neither run
the body nor validate arbitrary shell semantics. Legacy `owner -- sh ...`
calls are also path-checked; they are not a bypass or the physical runbook form.

Keep the producer's `|| exit 2` gate even if stdout is redirected: it publishes
a private receipt for **both** success and failure, so existence is not success.
`script-preflight.stdout.json` is a proof-free console summary, not the receipt;
never redirect it to `workloads.json.script-preflight.json`. A successful private
receipt has schema 1, type `workload-script-preflight`, `eligible: true`,
classification `WORKLOAD_SCRIPT_READY`, reason
`exact-label-script-and-cleanup-verified`, and the complete matching proof.
The owner verifies all of these plus the exact label/hash/file identities.

In invalid arm `7V4Dk3`, the final cleanup used different log redirections from
the exact form above. Preflight correctly published `eligible: false` with
`exact-final-cleanup-serial-required`; its call lacked the exit-status gate and
the owner then correctly rejected it as `passing-script-preflight-required`.
This was not a success-receipt schema mismatch or stdout overwriting the receipt.
Use the exact body and gated launch on a fresh arm; do not edit/reuse the failed
arm or convert its negative evidence into success.

In invalid arm `FNr3Q9`, the producer did run: its fixed stderr was
`artifact-directory-mode-not-0700`. The arm's private root was 0700, but its label
directory was 0755 and the redirected stderr was 0644. A regular body and exact
cleanup line cannot qualify that output parent. There was no successful receipt
or eligible classification; do not infer a command-launch or schema failure.
The producer/owner now check the private root and label directory first. If only
the label directory is missing/untrusted, they retain an exclusive 0600
`private/LABEL.workload-script-launch.failed.json` in the verified private root;
the negative launch record is not a script proof and blocks both output layouts
even after a later permission change. If the root itself is untrusted or failure
publication is unavailable, preserve only the fixed CLI outcome; never write a
fallback elsewhere, repair the directory, or reuse that arm. The directory
guard above runs before shell redirection, so even the logs require a safe parent.

Ordinary completed probe exit codes (for example video exit 2) remain in the
receipt and `failedChildCount`; they are not performance/correctness successes.
They permit diagnostic quiet collection only after all work is joined. Keep
every original result; no retry or baseline promotion hides those failures.

The invalid `8vdtZ8` arm actually had four nested child completion receipts,
all exit 0 and uninterrupted. Its body then invoked
`cleanup -- adb -s "$SERIAL" shell am force-stop com.android.chrome`; the
parser rejected this before cleanup started. The outer shell exited nonzero,
and the old owner omitted its failure receipt. Do not infer missing children
from a top-level directory scan: child records live in
`workloads.json.owner-OWNER_ID/`.

After owner publication, handled owner failures now retain private
`workloads.json.failed.json`; child/cleanup failures retain
`NAME.failed.json` beside their started/completed records, including malformed
cleanup arguments. These record fixed reason/stage, observed exit/signal or
launch failure, and whether process-group join was actually observed. They
never replace `workloads.json`, satisfy quiet-start, or permit a same-attempt
retry. An ordinary completed nonzero probe retains its original complete
receipt/exit code; an outer `set -e` abort is a failed owner. Pre-launch failures
still need the retained executor's fixed reason/exit evidence.

SIGKILL, executor loss, or unavailable storage cannot guarantee a final write.
Retain the existing owner/started records and classify their missing terminal
without inventing an exit, signal, or cleanup result. A read-only inspection is:
`node "$RECEIPT" inspect --owner "$WORKLOAD_OWNER_JSON" --output "$PRIVATE_DIR/workloads.json"`.
It reports only artifact counts, liveness verification and fixed classifications;
`eligible` is always false and it is never a replacement for the completion
gate. Even when an owner failure file exists, finish/join test-owned processes
through the common cleanup path before starting a separate fresh arm.

Before normal completion, require that owner to terminate, then use
`physical_quiet_phase.mjs` below to issue and acknowledge the quiet boundary.
Do not manually send `phase|quiet`, save a raw status as the boundary, or use
the older read-only `--capture-status` path for this protocol. The helper
constructs `quiet-LABEL`, sends a unique command through `adb shell run-as
... tee`, atomically publishes it, waits for its exact ID/phase/complete
acknowledgment, and publishes a mode-0600 host-timestamp envelope. Keep the instrumentation and host
collector alive, with the tested role and underlay unchanged. Do not disconnect,
call `free-memory`/`trim-memory`, stop the collector, or send `finish` to create
an apparently quiet result. Poll in bounded intervals, retaining ownership of
the session.

Any workload still live after the quiet boundary is `INVALID_WORKLOAD_OVERLAP`.
The phase helper refuses missing/unjoined receipts before any adb command and
requires the same collector live at both boundaries. Never restart traffic
after receipt completion. The final gate checks uninterrupted telemetry from
owner start through quiet end and the collector still live; it no longer
certifies only the quiet tail.

Require **300,000 ms of primitive memory samples inside the quiet phase**, not
300 seconds of total session time or 20 samples. The 15-second Go sampler and
five-second drain usually need 315–335 seconds after the phase command to
produce 21 samples spanning five minutes. The gate, not a sleep or sample-count
estimate, decides completion. The drain labels records with the current phase;
explicit status-time boundaries prevent older ring records from counting.

Use the same helper with `--start` for the end boundary. It inherits the exact
phase and process from the saved start and generates a fresh command ID;
`snapshot` changes the phase, so do not substitute it. Neither helper call
connects/disconnects the app, starts traffic, waits the five-minute window, or
finishes the session. Use one retained host owner for these commands. Failed
acknowledgment or an existing output file exits 2 without publishing a valid
new boundary. The fixed acknowledgment timeout is 30 seconds.

```sh
# LABEL is the frozen opaque block label; the helper adds the required prefix.
QUIET_PHASE="quiet-$LABEL"
node app/scripts/physical_quiet_phase.mjs --serial "$SERIAL" \
  --label "$LABEL" --workloads "$PRIVATE_DIR/workloads.json" --output "$PRIVATE_DIR/quiet-start.json"
# Keep role/collector alive for the full sampled window described above.
# Only then issue the end boundary; it derives its phase from the start file.
node app/scripts/physical_quiet_phase.mjs --serial "$SERIAL" \
  --start "$PRIVATE_DIR/quiet-start.json" --output "$PRIVATE_DIR/quiet-end.json"
# Allow the still-running collector to emit a sample after the end capture.
# Pull the per-sample file; physical-summary.json is not a substitute.
adb -s "$SERIAL" exec-out run-as com.bringyour.network \
  cat files/acceptance/physical-memory.ndjson >"$PRIVATE_DIR/physical-memory.ndjson"
node app/scripts/physical_quiet_gate.mjs \
  --start "$PRIVATE_DIR/quiet-start.json" --end "$PRIVATE_DIR/quiet-end.json" \
  --memory "$PRIVATE_DIR/physical-memory.ndjson" --telemetry "$TELEMETRY_FILE" \
  --phase "$QUIET_PHASE" --role "$QUIET_ROLE" --underlay "$UNDERLAY" \
  >"$PRIVATE_DIR/quiet-gate.json"
```

`QUIET_PHASE` is the exact `quiet-LABEL`; role is `client`, `provider`, or
`direct`; underlay is `wifi` or `cellular`. Client requires connected/tunnel
status and continuous VPN telemetry; Direct/provider require no client VPN,
and provider requires provision enabled at both boundaries. All require
unchanged phase, fresh same-process status, no dropped/error samples, and
collector coverage bracketing the window. Host and device clocks are evaluated
separately. A Direct quiet control **never qualifies connected client memory**.
Provider role counters/identity and all other MEMSTEADY assertions remain
separate gates; this helper covers workload telemetry and the quiet window,
not overall website correctness or the full campaign.

Exit 2 rejects missing/short/interrupted evidence as `INCOMPLETE_QUIET_WINDOW`;
any retained runtime sample above 24 MiB is `FAILED_MEMORY_LIMIT`, including
active samples before quiet. A below-threshold peak or instrumentation exit 0
does not override either result. Only exit 0 allows normal `finish`/collector
stop. On failure or safety timeout still finish and clean up, but preserve the
failed attempt and per-sample file; never promote its baseline. Readiness-only
sessions may finish early and do not qualify memory. Finally pull the joined
sampler output again so teardown samples are retained and checked as well.
The teardown recheck uses the same gate arguments plus
`--live-gate "$PRIVATE_DIR/quiet-gate.json"`, writing a separate output. This
requires the retained schema-2 live collector proof for the same workload
owner; it never substitutes a late collector or claims one is currently live.

For a controlled provider, install its exact client ID through standard input
as the private `files/acceptance/physical-expected-peer-id` file before issuing
either peer-connect command. The harness then waits for that peer instead of
silently choosing a stale cached provider. Never print or retain that ID in
benchmark output, and remove the pin with the other private acceptance files.
Before launching the retained instrumentation owner, use the host credential
staging helper; do not construct the file with an inline YAML/JSON parser,
quoted shell expansion or `echo`. From the Android repository:

Before touching the real configuration, run this **no-config/no-device parser
preflight** in the same host environment that will perform staging. First
freeze a single artifact directory for parser/staging/retained AM evidence:

```sh
umask 077
: "${RUN_DIR:?fresh owned mode-0700 arm root required}"
ARTIFACT_DIR="$RUN_DIR/private"
node app/scripts/physical_artifact_directory.mjs create \
  --directory "$ARTIFACT_DIR" >"$RUN_DIR/$LABEL.directory-created.json" || exit 2
```

Use `create` only during initial fresh setup: it creates a missing **leaf** under
an already-owned mode-0700 parent, never ancestors, and verifies existing paths
without repairing wrong owner/mode or following symlinks. Later calls must use
`check`. If evidence disappears, abort the arm; do not recreate it to hide an
external cleanup. Keep the fixed directory outcomes mode 0600 in the run root,
outside the leaf under test. Freeze/restore `ARTIFACT_DIR` across executor calls;
a workload's later `PRIVATE_DIR` must not redirect parser/staging/AM artifacts.

After APK installation, prove the credential destination is absent with the
metadata-only helper before parser/staging work:

```sh
node app/scripts/physical_credential_watch.mjs --preflight \
  --serial "$SERIAL" --output "$ARTIFACT_DIR/$LABEL.credential-destination-preflight.json" || exit 2
```

Require exit 0 and `eligible=true` / `credential-destination-absent`. A reinstall
preserves app data; it does not prove cleanup. Existing or unavailable metadata
stops setup and never authorizes file removal. Do not use an inline `adb shell
run-as ... sh -c 'test ...'` check: ADB joins argv for the device shell, losing
the host's quote grouping. That form can execute `sh -c test` without operands
and falsely report absence. The helper preserves a second, literal quote layer
and returns only bounded metadata. Its host regression reproduces the false
absence and confirms the corrected command preserves an existing file.

```sh
umask 077
node app/scripts/physical_artifact_directory.mjs check \
  --directory "$ARTIFACT_DIR" >"$RUN_DIR/$LABEL.directory-before-parser.json" || exit 2
node app/scripts/physical_credentials_preflight.mjs \
  --artifact-dir "$ARTIFACT_DIR" \
  --output "$ARTIFACT_DIR/$LABEL.credential-parser-preflight.json"
```

Join it and require exit 0 / `CREDENTIAL_PARSER_READY`. It verifies current
Node version, both helper files' syntax, an actual helper import/framing check,
shared-reader shell syntax, and the selected parser's exact raw scalar contract
for both schemas using only generated private synthetic fixtures. No config or
serial option is accepted and no adb operation occurs. Temporary fixtures are
removed on completion; the exclusive mode-0600 receipt retains private
source/tool hashes, tool versions and fixed outcomes, never raw command output.
These are **source/executable hashes, not credential or configuration hashes**;
do not publish the provenance receipt. A missing, failed or incomplete receipt
blocks a physical arm. The staging `--preflight` contract rechecks source/tool
identity before inspecting the real config; changed source, cached reader or
Node/Go executable requires a new passing preflight. Keep both receipts and
terminal exit statuses, including failures. No current passing check can recover
the cause of a prior parser-failed arm that retained neither output category nor
source identity.

```sh
umask 077
node app/scripts/physical_artifact_directory.mjs check \
  --directory "$ARTIFACT_DIR" >"$RUN_DIR/$LABEL.directory-before-staging.json" || exit 2
node app/scripts/physical_credentials.mjs \
  --serial "$SERIAL" --schema user-pass --config "$HOME/urnetwork/.tests.yml" \
  --artifact-dir "$ARTIFACT_DIR" \
  --ownership "$ARTIFACT_DIR/$LABEL.credential-owner.json" \
  --native-inputs "$NATIVE_INPUTS_PROOF" --label "$LABEL" --build-id "$ACCEPTANCE_BUILD_ID" \
  --preflight "$ARTIFACT_DIR/$LABEL.credential-parser-preflight.json" \
  --output "$ARTIFACT_DIR/$LABEL.credential-staging.json" || exit 2
```

Require exit 0 and `eligible=true` before instrumentation. The user-provided
`$HOME/urnetwork/.tests.yml` uses `user`/`pass`, so the helper invokes the canonical
`tests/read-tests-config.sh` with exactly `--schema user-pass get user` and
`--schema user-pass get pass`, capturing raw stdout privately. For the versioned
repository vault, use this **alternative** with the workspace `$ROOT`:

```sh
umask 077
node app/scripts/physical_artifact_directory.mjs check \
  --directory "$ARTIFACT_DIR" >"$RUN_DIR/$LABEL.directory-before-staging.json" || exit 2
node app/scripts/physical_credentials.mjs \
  --serial "$SERIAL" --schema data-plane-account \
  --config "$ROOT/vault/main/tests.yml" \
  --artifact-dir "$ARTIFACT_DIR" \
  --ownership "$ARTIFACT_DIR/$LABEL.credential-owner.json" \
  --native-inputs "$NATIVE_INPUTS_PROOF" --label "$LABEL" --build-id "$ACCEPTANCE_BUILD_ID" \
  --preflight "$ARTIFACT_DIR/$LABEL.credential-parser-preflight.json" \
  --output "$ARTIFACT_DIR/$LABEL.credential-staging.json" || exit 2
```

That selects exactly `data_plane_account.email` and `data_plane_account.password`.
Schema selection is mandatory and never inferred from a path or failed key;
wrong schemas fail before device staging. The shared reader's default remains
the versioned acceptance vault. Its explicit `user-pass` mode is get-only,
single-document and bounded to 128KiB, accepting only `user` and `pass` with
nonblank string values; it cannot validate/provision acceptance fixtures.
Rebuild any `UR_ACCEPT_TEST_CONFIG_READER` override from current
`build/all/acceptance/cmd/test-config` before use. Do not work around an old
reader by dropping `--schema` or falling back to other keys.

Both schemas preserve spaces, quotes and punctuation literally. The helper
forms exactly `email` + LF + `password`, with **no trailing LF**. Blank values and embedded CR,
LF or NUL fail before any device call. No JSON quotes, YAML labels, escaping
or extra blank lines belong in `files/acceptance/credentials`.

The config must be an owned regular mode-0600 file and `$ARTIFACT_DIR` an owned
mode-0700 directory. Use a fresh output path and a cleaned, stopped prior
session. Staging binds its output and parser receipt to that directory before
inspecting the real configuration, and rechecks it before device staging. A
missing, changed or untrusted directory never triggers automatic recreation.
Use the same explicit directory on all setup helpers. Staging is through
`adb shell -T run-as com.bringyour.network`
standard input with umask 077, never `exec-out` stdin, command-line values or
world-readable device staging. Both temporary and final app-private files are
verified in `run-as`: two logical records (including the unterminated final
record), zero blank records, mode 0600, exact byte length
and SHA-256 equality. Exclusive publication refuses existing credentials.
Schema-3 JSON contains only structural counts/mode/length and fixed substep
outcomes/exit codes. SHA-256 equality is still checked internally, but no digest,
value, serial, path, token or raw subprocess output is persisted. Keep this
mode-0600 diagnosis private; older schema-1 reports contained credential-derived
digests and must not be made public.
Reader failures additionally retain only
`parserOutcome={exitCode,timedOut,stderrCategory}` with fixed categories and no
raw stderr/stdout, exception text, path or partial scalar. Successful staging
leaves it null. Syntax/module-loader/toolchain/reader-schema/timeout and other
fixed categories improve diagnosis without asserting an auth cause. Failed
preflight or staging is `INVALID_SETUP`: stop and retain it, never fall back to
inline parsing, drop schema selection, or retry it into the same cohort.

Android sandbox hardlinks are not assumed supported. Publication uses
`set -C; exec 3> ...` to create the final regular file exclusively, then copies
the verified temporary file through that descriptor. There is no overwriting
rename or device hardlink. The final path exists during the bounded copy:
**join the helper and require exit 0 / eligible=true before starting any
instrumentation/consumer**. A yielded executor is not a completed helper.
Publication must not overlap another instrumentation or cleanup owner.

New physical arms must retain the separate `--ownership` capability from the
commands above and pass it to the retained AM supervisor. It is published
only after joined successful staging and binds the exact serial/label/attested
build/input owner, staging receipt, random device marker and salted file
fingerprint (contents plus inode/device and nanosecond modification/change
identity). Keep it mode 0600/private: no capability token/fingerprint/native
identity in public reports. It contains neither credential values nor an
unsalted credential digest. Parser preflight hashes these new helper
dependencies, so a pre-change preflight cannot authorize new staging.

On a terminal setup failure **before instrumentation handoff**, run:

```sh
node app/scripts/physical_credential_ownership.mjs rollback \
  --ownership "$ARTIFACT_DIR/$LABEL.credential-owner.json" \
  --serial "$SERIAL" --label "$LABEL" --build-id "$ACCEPTANCE_BUILD_ID"
```

Remain `INVALID_SETUP`; this is cleanup, not a retry or an auth correction.
The AM supervisor automatically calls this helper on pre-spawn exceptions.
It requires a stopped target app, complete matching host/device proof, no
handoff/terminal/crashed-operation marker, and unchanged files; it reopens and
compares both files before removing only its credential and owner marker.
Host operations are serialized, and the single-session/no-concurrent-same-UID
mutation rule remains mandatory: this is not a hostile-writer atomic unlink.
Missing/crashed/unknown/substituted ownership is preserved. Immediately before
AM spawn, an irreversible private handoff disables rollback and removes only
the marker, leaving credentials for normal login. Post-handoff failures use
the joined session cleanup, never this setup rollback.

The stale unmarked `diag9` credential predates this proof and **cannot be
adopted or automatically deleted**. Request explicit user approval for that
exact stopped-session file removal, or ask the user to clear app data.
Never recursively clear acceptance storage or guess ownership from a recycled
inode. Full contract: [prospective rollback](../../../tests/RUN-PERF.md#prospective-credential-setup-rollback).

Diagnostics distinguish `steps.create` (exclusive open), `steps.copy`,
`steps.inspect` (final structural/hash inspection), and `steps.publish` (whole
remote command). The shell keeps the destination descriptor open through
verification and failed/caught-interruption rollback. Rollback compares the
current regular non-symlink path with that live descriptor's device/inode before
removing it; an observed substituted file/symlink/directory is preserved.
Identity uses the owning shell's builtin `test -ef`, never an external stat
child's `/proc/self/fd/3`: a child's descriptor table may differ. Normal finish,
checked copy/inspection failures and caught signals release explicitly before
shell exit. EXIT is a fallback that still refuses deletion if the FD is gone;
the normal success path must not depend on an EXIT trap retaining descriptors.
`destinationOwned` means the exclusive-open marker was observed, not that a
later host process may remove the path. `steps.destinationCleanup` is the
same-shell rollback/release; `steps.cleanup` is temporary-source cleanup.
Successful credentials persist, with destinationCleanup `not-run`. Lost/invalid
results are `unavailable`, never inferred success. A host-side verification
failure after the FD closes, lost result, or SIGKILL can leave the destination:
abort setup and preserve it for explicit stopped-session cleanup, never guess
ownership from a reusable inode, overwrite it or silently retry. These shell
checks assume the protocol's single session owner; they are not an atomic
compare-and-unlink defense against a concurrent same-UID path mutator.

For `device-staging-failed` **before** publication, retain the schema-3 report's
additive `stageDiagnostic`. It contains only a fixed phase/reason, matched-marker
status, numeric exit, timeout flag and classified transport/stderr category.
The stage shell emits one fixed terminal marker: files/acceptance symlink
guards, directory create/mode, destination absent/symlink guards, temporary
exclusive create, copy, temporary mode, inspection, or complete. Only one marker
matching the joined exit can attribute a phase; missing/conflicting markers stay
unattributed. Run-as denial/package absence and ADB/tool/timeout categories are
separate from remote filesystem failures. No raw stderr, credential values,
hashes, package names or serials are retained. A zero exit additionally needs the
exact `complete` marker before publication; ownership/byte/structure checks
remain mandatory. Existing destinations are never overwritten/adopted/removed
by a failed stage, and no failure authorizes retry.

In `W83rhj`, parser preflight passed and the values passed local structural
validation, but remote staging exited 1 before its ownership marker; every
publication/cleanup substep was not-run. Its older receipt has no phase marker,
so existing destination, directory guard and run-as/transport failures cannot
be distinguished retrospectively. No authentication occurred: this is **not**
evidence of an incorrect account/password. Preserve the invalid arm; use the
classified evidence on a separately authorized fresh arm, not speculative
credential changes or cleanup of a destination whose ownership is unknown.

To test only the run-as filesystem publication primitive, with no credential
file/config read and no authentication, use a fresh private output path:

```sh
node app/scripts/physical_credentials.mjs --sentinel-only \
  --serial "$SERIAL" --output "$PRIVATE_DIR/$LABEL.publication-sentinel.json"
```

Do not supply config/schema/inspect-only flags and do not overlap instrumentation
or a session-cleanup owner. The helper exclusively creates a separately named
`.publication-sentinel-*` directory with a zero-byte source, exclusively creates
and copies to another name, verifies FD/path identity, mode 600, zero bytes and
one link, and proves a second noclobber open is refused
(`observed.exclusiveCollisionRefused=true`). It then releases the destination **while holding its FD** and removes
only the source/directory. No command references the credentials
destination; existing credentials and other sentinel owners are preserved.
Require exit 0 / eligible=true / destinationOwned=true and all step exitCode=0,
including destinationCleanup and cleanup; the sentinel report is schema 2.
Validate this sentinel on the allowlisted device before any new credential
attempt after changing the publication primitive. Failure never
authorizes a credential or login retry. If cleanup fails, retain the outcome;
do not use broad acceptance-directory cleanup to remove a diagnostic namespace.
Report only the fixed outcomes/codes and zero-byte primitive measurements, never
command output, names, paths or tokens. This is not an authentication test and
does not require an APK/native rebuild or device traffic.

For a credential-readiness failure, inspect the **exact** app-private file
immediately before instrumentation and again after failure, before cleanup,
using distinct evidence filenames:

```sh
node app/scripts/physical_credentials.mjs --inspect-only \
  --serial "$SERIAL" --output "$PRIVATE_DIR/$LABEL.credential-lines-before.json"
```

This read-only command captures the run-as file internally and emits/persists
only its capture time, logical line count, nonblank count, total bytes and
**LF-byte count** (`newlineCount`), plus the structural verdict. It emits no
values, IDs or digest and never stores the raw bytes. Correlate these records
with the staging result and the exact instrumentation failure; counts do not
prove authentication success. Kotlin `File.readLines()` accepts two nonblank
records with **either no final LF or one final LF**. An additional blank record
or third value fails. Thus one trailing LF alone is not a demonstrated login
root cause, and `wc -l` is not the logical record count: our canonical file has
one LF byte but two records. Do not use `String.split`'s terminal empty element
as a proxy for the file reader. Staging still requires its exact canonical
bytes/digest; a noncanonical but structurally valid file is not a staging pass.

For an unexplained missing file after successful staging, use a **readiness-only
diagnostic**, not another traffic arm or a credential-restoration workaround:

1. Rebuild the matched app/test APK pair with the same acceptance build ID and
   existing provenance/profile rules, then select the **separate diagnostic
   runner component** below with `-e acceptanceCredentialDiagnostics true`.
   Verify the built/installed test manifest contains both
   `androidx.test.runner.AndroidJUnitRunner` and
   `com.bringyour.network.acceptance.PhysicalCredentialDiagnosticRunner`, each
   targeting `com.bringyour.network`. The source manifest must keep the default
   runner first: AGP injects its runner setting into the first instrumentation
   element and would silently rename a lone diagnostic declaration. This is
   also checked by `physical_credential_runner_test.mjs`.

   The ordinary runner/commands remain unchanged. Even when the diagnostic
   component is explicitly selected, an absent/false flag delegates normally
   without any credential metadata reads or checkpoint output. With the exact
   flag `true`, it records runner-on-create entry before calling the base
   runner and return after successful base initialization. The physical test
   also records method entry before its build assertions and setup, then the
   existing before-launch, after-launch and before-read checkpoints. Leave
   logout/login ordering unchanged; do not move, restore or re-stage inputs to
   conceal a missing-file observation.
   Fields are fixed stage/time, exists/type, owner-match (not UID), permission
   mode and byte count only; no file bytes, hash, path, ID or exception text.
2. Start this command in a separate **retained foreground host session** and
   verify its initial metadata sample and live owner before starting
   instrumentation. Do not use `&` inside a one-shot executor:

   ```sh
   node app/scripts/physical_credential_watch.mjs \
     --serial "$SERIAL" --output "$PRIVATE_DIR/$LABEL.credential-watch.ndjson" \
     --stop-file "$PRIVATE_DIR/$LABEL.credential-watch.stop" --timeout-ms 120000
   ```

   This read-only witness runs bounded `run-as` stat/type checks about every
   250ms and persists mode-0600 metadata. It never opens the credential file for
   content. Unavailable/permission-failed probes are not evidence of absence.
   Before instrumentation, require its first sample to show a regular present
   file, owner-match true, mode 600 and the staged byte count; otherwise preserve
   invalid setup and do not start authentication.
3. Run this **readiness-only** instrumentation in another retained session,
   capturing its output privately under the existing owner protocol:

   ```sh
   adb -s "$SERIAL" shell am instrument -w -r \
     -e class com.bringyour.network.acceptance.PhysicalLowbarSessionTest \
     -e acceptanceBuildId "$ACCEPTANCE_BUILD_ID" \
     -e acceptanceCredentialDiagnostics true \
     com.bringyour.network.test/com.bringyour.network.acceptance.PhysicalCredentialDiagnosticRunner
   ```

   Do not issue connect, browser, media or performance commands. On readiness,
   finish the retained session through its normal private command protocol;
   on failure, retain the original failure without retry. On readiness/failure, create
   the empty stop file and join the watcher to obtain its terminal summary.
   Until then, do not reinstall, clear app data or run credential/session
   cleanup. Keep complete private AM stdout/stderr and the metadata-only
   `PhysicalCredential` logcat tag.
4. Before common cleanup, capture the test's checkpoint stream privately:

   ```sh
   umask 077
   adb -s "$SERIAL" shell -T run-as com.bringyour.network \
     cat cache/acceptance/physical-credential-checkpoints.ndjson \
     > "$PRIVATE_DIR/$LABEL.credential-checkpoints.ndjson"
   ```

   The checkpoint file lives under cache, outside `filesDir`, and appends bounded
   fixed-schema records (serialized appends enforce a 16KiB total bound).
   The sink uses `dataDir` to stat the credential path without recreating a
   deleted files directory. Cache writes are owned mode-0600, no-follow and
   nonblocking; missing/invalid persistence emits only a fixed warning.
   Correlate all timestamped records with the external watcher. Retain
   evidence before explicitly cleaning this test-owned cache file. An absent
   checkpoint, dead watcher or deadline preceding the failure is incomplete
   attribution, not proof of which actor deleted the file. Current SDK logout
   removes `filesDir/network_spaces/<host>/<env>/.by`, not sibling acceptance
   inputs; direct-storage-home LocalState likewise owns only its `.by` child.

Interpret the new checkpoints as bounded intervals, not identification of the
deleting actor:

- Missing at `runner-on-create-entry`: loss predates the first argument-aware
  runner hook. Android Application construction/content-provider setup and
  framework/host work can occur earlier.
- Present at entry, missing at `runner-on-create-return`: loss occurred during
  base runner initialization or concurrent work in that interval.
- Present at runner return, missing at `test-method-entry`: inspect subsequent
  application startup, JUnit/rule setup and concurrent owners before the method.
- Present at method entry, missing at later test checkpoints: the existing
  launch/read discriminator now identifies the narrower test-setup interval.

The return checkpoint does not mean all asynchronous application/test setup is
complete. A thrown base-runner exception is propagated unchanged and has no
false return record. Missing early records while using the ordinary runner or
without complete observer coverage are incomplete attribution, not evidence
of a missing credential. This setup-only run is not a memory/performance arm.

Host helper success cleanup removes only its temporary source file. Deterministic
tests cover helper exit, inspect-only exit, a separate login reader and SDK
logout storage isolation. None justifies blaming helper cleanup or logout for
a device `ENOENT` without the lifecycle timeline above. Diagnostic observation
does not restore credentials, retry authentication or change acceptance gates.

No gate bypass or manual retry is allowed after a staging failure. Take the
common cleanup path; a disconnected/interrupted host can leave private files
that require explicitly authorized cleanup before a fresh session if ownership
cannot be proved. Never put credentials or the
retained acceptance client ID in a command line or checked-in artifact.
Release the retained client with `build/all/acceptance/client-cleanup.mjs`,
then remove only owned host/device private files. The staging helper
does not change app authentication semantics or start an instrumentation run.

`physical-memory.ndjson` separates Go live/allocated/in-use/idle/released heap,
runtime overhead, object/allocation/free counts, GC/forced-GC/pause counters,
pool in-flight/retained/capacity bytes, automatic reclaim decisions and
before/after values, aggregate mobile packet-pressure drops, primitive
client/flow topology, platform transport-budget use, Android PSS, Java/native
heaps, threads, descriptors, route state, and aggregate carrier counters.
Sampler schema 12 also publishes resend, Pack-handoff, receive-reorder
used/capacity bytes, and H1 iterative-depth diagnostics: saturation count,
granted steps, deepened-flow count, maximum earned count, and maximum earned
logical bytes. It additionally separates H1 platform receive-route drops from
bounded reliable-carrier backpressure; the former creates a Transfer sequence
hole, while the latter retains the same fixed channel capacity and lets TCP
slow the sender. Schema 12 also separates client and provider Pack handoff,
ACK-route wait/error, initial-write, timeout-recovery, and exact
ACK-pending-resend-preemption counters. This is required when the opposite
phone drives traffic through the device: client topology counters alone do not
describe provider memory or recovery churn. The Pack/receive values are
device-wide shared budgets, not one selected flow's queue. The
steady streamline signal is `goRuntimeBytes`:
five quiet connected minutes after burst ownership drains should have p50 and
p95 at or below 24 MiB. Keep
the active peak and time-to-recover separate, and investigate every sample over
the 28-MiB active diagnostic threshold. Neither threshold is Android whole-app
PSS or an iOS Network Extension `phys_footprint` ceiling.

Ordinary Android and Apple SDK libraries start the Go runtime with
`memprofilerate=0`. Android and iOS also use the same `GOGC=25` pacing for the
24-MiB campaign; a looser Android heap float is not a valid surrogate for the
iOS Network Extension. A private diagnostic build can opt in before native
runtime initialization with `-PurnetworkMemoryProfileRateBytes=65536`; the
Gradle value is passed to both the AAR linker and the diagnostic app API.
`heap-profile` forces a GC and writes a private pprof file, so it is useful only
in an opted-in diagnostic artifact. Record that forced collection and do not
compare its post-profile sample as an unperturbed recovery point. Interpret
`poolOutstanding` as live/in-flight ownership and `poolRetainedBytes` as
returned free-list ownership: pooling can reduce allocation/GC churn while
still retaining a burst high-water until the quiet-period rebuild runs. The
mobile reclaimer waits for payload quiet and bounded outstanding ownership,
then performs at most one pass per cooldown; use its deferred, below-target,
cooldown, and before/after counters to distinguish policy from a leak.

### Opt-in retained-owner diagnosis

The attested H1 memory failure needs owner evidence, not another threshold
change. Build the native SDK **and** both APKs with
`-PurnetworkMemoryProfile=ios-memory-audit-v1`,
`-PurnetworkMemoryProfileRateBytes=65536`, and the same unique acceptance build
ID, using the native build-owner/consumer-lock procedure above. Preserve the
AAR/native/APK/source-input hash chain. No APK-only rebuild or reused native
output qualifies. Before public traffic, issue an `owner-census` command with
a fresh `preflight` label and require a matching `complete` status and a
nonempty schema-1 private file. An old SDK binding fails closed; compiling the
test APK alone does not demonstrate availability. Verify its reported sampling
rate is 65536 in addition to the normal iOS-profile gate.

Use the host diagnostic publisher, not an inline `adb ... sh -c` command:

```sh
node "$ROOT/android/app/scripts/physical_diagnostic_command.mjs" \
  --serial "$SERIAL" --owner "$INSTRUMENTATION_OWNER" \
  --command-id "$COMMAND_ID" --verb owner-census --label "$CENSUS_LABEL" \
  --output "$ARTIFACT_DIR/$COMMAND_ID.command.json"
# After successful exact completion, copy the receipt's fixed diagnostic file.
node "$ROOT/android/app/scripts/physical_diagnostic_copy.mjs" \
  --serial "$SERIAL" --receipt "$ARTIFACT_DIR/$COMMAND_ID.command.json" \
  --output "$ARTIFACT_DIR/$COMMAND_ID.owners.json"
```

Set a fresh command ID and label first. Owner and output must be direct
children of the same existing owned mode-0700 host artifact directory; the
output must not exist. The helper supports only `owner-census`, `heap-profile`,
and `goroutine-stacks`. It verifies the live retained AM supervisor/ADB child
and ready-bound target before publication and through the exact `complete`
acknowledgment, rejecting role/PID changes or competing commands. It does not
create app storage, restart an app, change the VPN role, or read diagnostic
contents. `running`, process exit, an old status, or a timeout is not success;
there is no automatic retry. Use `physical_diagnostic_copy.mjs` for the private
download, then validate its schema where applicable. The copy helper derives
the exact app filename from a complete, private, serial-bound receipt. It
streams raw bytes directly into an exclusively created mode-0600 file before
publishing it, independently of the caller's umask, and refuses existing files,
symlinks, untrusted directories, empty data or failed reads. Only byte counts
and the fixed artifact kind are returned; raw profiles/stacks never enter its
output. For heap/stacks choose a fresh `.pprof`/`.txt` host output instead. Do
not use shell redirection or `adb pull` to bypass this permission guard. This
copy is evidence collection, not validation or rehabilitation of a failed arm.

For all three diagnostic verbs, the app acknowledges with the exact phase
`VERB-WIRE_LABEL` in both `running` and `complete` statuses. The publisher
requires this full phase, the exact wire command ID, and `complete`; a different
label, partial match, or matching phase from a different ID cannot qualify. A
diagnostic rejected by the earlier bare-verb check remains an invalid arm even
if its private output was written; do not reuse it after correcting the host
contract.

CLI IDs and labels are logical names: each must be 1–64 characters, start with
an ASCII letter/digit, and otherwise use letters/digits/`._-`. The publisher
derives separate 64-character wire IDs and labels from the live instrumentation
owner's session UUID and the logical name. The wire record is
`WIRE_ID|owner-census|WIRE_LABEL`, and census output is
`physical-owners-WIRE_LABEL.json`. Its private schema-2 command receipt records
both names and the mapping. Always copy through that receipt; do not reconstruct
an app filename from the CLI label.

A fresh session may reuse logical `preflight` without colliding with an old
`physical-owners-preflight.json` or another session's hashed artifact. Existing
artifacts remain untouched. Within one session, IDs and diagnostic labels are
one-shot: private attempt reservations survive success, failure and
interruption, so changing a command ID does not permit retrying an attempted
label. The helper uses one exclusive mode-0600 temporary command write and an
atomic rename, and still rejects an existing destination for the current
session. A collision is a failed arm; never delete unknown artifacts or retry
under a new label. Do not use `exec-out` for command stdin.

The failed `J49FvL` diagnostic stopped before census or traffic. Its unquoted
remote `sh -c` was reparsed by ADB: only argumentless `umask` ran under `run-as`,
while redirections ran in the outer shell's working directory. The similarly
unquoted follow-up `test` did not prove missing app storage. Precreating a
temporary file before a second `set -C` redirect was a separate latent failure.
The host fixture reproduces both with actual shell reparsing. The helper
quotes the entire remote script and opens the temporary file only once. A
genuinely absent acceptance directory or stale/stopped owner still fails
closed; never repair it with `mkdir` or reuse the failed arm. Start a fresh
fully attested arm after stopping/joining test-owned sessions.

For a separately approved diagnostic arm, retain the ordinary continuous
collector and workload-owner proof. At matched idle, joined post-traffic, and
180-second connected-quiet boundaries, collect in this order:

1. `owner-census` with `BOUNDARY-before-gc` (no forced GC or shedding).
2. `heap-profile` with `BOUNDARY` (explicitly forces GC; record the intervention).
3. `owner-census` with `BOUNDARY-after-gc`.
4. `goroutine-stacks` with `BOUNDARY` (allocates its own private scratch buffer;
   collect last so it cannot contaminate the preceding heap profile).

Only the existing private acceptance command interface triggers these reads;
there is no new periodic sample, packet hook, owner registry, or retained
device reference. Do not run them during a qualifying five-minute quiet gate.
The census reports current workers separately from routing indexes (a removed
index can still own a draining worker), channel capacities, pacing services,
flow/canceled-flow counts, API connection state, DNS/association caches, pool
objects, and admission claims by class. It reads existing owning locks one at
a time. Known structure/slice-slot bytes exclude allocator rounding, map
buckets, backing object graphs, sockets, and TLS; reservations are **not**
physical-memory measurements. Its before/after runtime and size-class values
identify sampling/GC changes but cannot assign span slack to an owner by
themselves. A topology larger than 64 unique sampled clients reports omitted
entries explicitly and is incomplete, not silently complete.
Scope is the current device's indexed windows/provider/shared API strategy;
already-unlinked client generations or other NetworkSpaces need private heap
and goroutine evidence. Zero current-owner counts do not prove process-wide
absence of leaked generations. No diagnostic registry is added to retain them.

Retain census JSON, pprof, and `physical-stacks-LABEL.txt` only under the
mode-0700 host artifact's private directory, with files mode 0600. Census JSON
contains aggregate scalars only. Raw goroutine stacks are **not sanitized**:
they can contain arguments/addresses and must never appear in logs, reports,
tool output, or chat. Command status exposes only bounded filenames/sizes;
offline reporting may expose aggregate function/state counts only, never raw
frames, paths, IDs, endpoints, or tokens. Heap profiles are likewise private.
Correlate aggregate heap allocation owners with owner-count/capacity deltas and
a deterministic owner release test before proposing a lifecycle fix. Confirm
any fix in a fresh unprofiled, fully attested arm with **every** runtime sample
at or below 25,165,824 bytes and unchanged page/Fast.com performance gates.

`packetPressureDropCount` is a cumulative overload counter, not a pool leak:
the <=24-MiB mobile profile samples exact packet-root bytes every fourth ingress
call below pressure, admits the largest ordered prefix that fits below 1 MiB,
and samples every call until ownership drains. The message pool gives <=256-byte
ACK/control traffic its own class, so an ordinary TCP ACK is charged 256 bytes
rather than one 2-KiB full-MTU root. Explicit H1 with provider work disabled may
rescue exact ACK-only TCP packets from the rejected suffix up to the 2-MiB
aggregate ceiling; H3, Auto, provider-on, data, and connection-state packets
retain the 1-MiB base ceiling. Rejected ownership is returned immediately; TCP
retransmission/backpressure provides recovery. The accepted performance profile
keeps send, Transfer-ACK, forward, contract, H3, and control sequences at 16,
gives only H1 receive a fixed 64-message / 128-KiB handoff with lossless Pack
backpressure to cancellation and a 1-ms ACK wait on the reliable carrier,
retains 16-packet/24-KiB logical groups,
fixes Auto quality/speed windows at 4/1, and keeps a 256-KiB packet warm set
split between the small and full-MTU classes after reclaim. The mobile receive
reorder budget is 1.68 MiB while providing at the 24-MiB target (with a
1.5-MiB floor) and 2 MiB when provider work is off.
Its shared charge is retained allocation, including pooled outer/message roots
and a rounded decoded-owner envelope; the independent per-flow limit remains
logical payload bytes. A newly empty flow retains one progress item, but cannot
admit a second until aggregate budget returns; the mobile flow cap bounds that
deliberate liveness overdraft. Do not raise the base byte gate or statically
raise the H1 receive window to 128; earlier count-based experiments reached
28.41--29.95 MiB and about 25.05 MiB respectively on the physical surrogate.
A later iterative diagnostic grew only repeatedly saturated H1 flows from
64/128 KiB to 128/256 KiB. Its full-depth arm remained memory-safe at a
22.45-MiB peak and 20.91-MiB recovery p95, but Cloudflare measured only
1.18 Mbit/s and fast.com about 0.20 Mbit/s against an 87.4-Mbit/s Direct
median. The production mobile policy therefore clears the adaptive settings;
schema-12 telemetry and the generic Connect mechanism remain for controlled-
provider experiments. The same schema records the retained diagnostic;
server/default devices do not instantiate this gate or pay the retained-root
scan. The same mobile profile
retires inactive TCP flow state after three minutes so a closed browser burst
does not preserve the desktop ten-minute graph throughout the five-minute
steady window.

The 32-message H1 ready-drain is not an additional
buffer: it still stops ordinary data at 12 KiB, uses the existing 16-KiB
WebSocket wrapper, and never waits for a batch to fill. The 2026-08-24 physical
pass improved the ten-run Cloudflare median from 1.695 to 2.02 Mbit/s, but a
45-second real fast.com burst moved 28.74 MiB H1 ingress and left 6.48 MiB in
returned packet pools. Go runtime briefly reached 29.48 MiB for three sampler
records before one quiet rebuild reduced it to 19.85 MiB. That run fails the
active/post-burst 28-MiB gate even though it recovered; retain the speed and
memory evidence together.

The accepted 2026-08-25 allocation-accurate pass reduced the same failure mode
without shrinking the useful per-flow receive window. All ten full 1-MiB
Cloudflare responses completed at 2.04--6.00 Mbit/s (2.78 median), and fast.com
moved at least 20.53 MiB ingress in the inner counter bracket. Go runtime
peaked at 21.77 MiB, packet roots at 1.78 MiB, and exact receive use at
2.00/2.00 MiB; none of 61 samples exceeded 24 or 28 MiB. During five quiet
connected minutes, runtime p50/p95/range/last were
19.91/20.16/19.85--20.20/19.91 MiB, queued receive bytes were zero, the packet
warm set was 256 KiB, and neither forced GC nor idle trim ran. Nine of 11
bounded Pack waits succeeded; the two misses returned 2,880 bytes and every
payload still completed. Pre/hot/post-recovery Wikipedia median load was
455.1/627.4/613.6 ms. Preserve one hot reused-H2 5.3-second resource outlier in
the record; it had no concurrent tunnel handoff/pressure drop or timeout resend,
and the seven post-recovery pages had no multi-second resource tail.

This validates the Android Go-allocation surrogate, not iOS extension
`phys_footprint` or jetsam behavior. The adjacent Direct upper-pair median was
41.53 Mbit/s while the unchanged public H1 provider remained much slower;
provider grouping/direct-ACK deployment to a controlled exit is required
before claiming restoration of 40+ Mbit/s.

A later same-session directionality A/B explains why client-only tuning cannot
make that claim. Eight bounded H1 flow lanes produced a 348.8-ms Wikipedia load
median and 152 timeout resends, versus 1,157.8 ms and 1,053 resends after a
lane-zero rebuild. Runtime peaks remained 20.60 and 19.43 MiB respectively,
with no >28-MiB samples. Fast.com still displayed only 3.6--10 Mbit/s in the
lane-eight arm and 4.4 Mbit/s in lane zero while adjacent Direct displayed
410 Mbit/s and 1.1 Gbit/s. The public provider put all return data on lane zero:
client lanes isolated requests and inner TCP ACKs, not the download. The next
valid speed experiment must pin explicit H1 on both a controlled mobile client
and provider, enable the same eight negotiated lanes plus provider grouping and
direct ACK application, and retain the <=24-MiB active-memory gate. Default
Auto remains unchanged until that provider-side carrier-transition A/B passes.

The pinned-provider A/B then found the limiting hop. With fixed depth, a full
32-message platform receive route discarded 530 already-read H1 messages and
filled 1.993/2.000 MiB of receive reorder behind the synthetic holes; fast.com
displayed 6.1 Mbit/s. Carrier-only backpressure made that counter zero but
moved 24 drops to the finite Pack wait and displayed 3.5 Mbit/s. The accepted
pipeline keeps both capacities unchanged and waits only for H1 capacity or
cancellation. Three canonical repeats displayed 38, 41, and 52 Mbit/s; carrier
and Pack drop deltas were zero, all 762 Pack waits succeeded, and both shared
queues drained. Seven Wikipedia pages measured 439.2-ms median load and
181.5-ms median document TTFB. Runtime peaked at 17.60 MiB, then a 345-second
quiet window measured 17.57/17.61-MiB p50/p95 with zero sample above 24 MiB.
Treat `platformH1ReceiveBackpressureCount` as proof the fixed bound engaged;
accept only when the corresponding drop delta is zero, Pack waits equal
successes, and final reorder use is zero. This is Android allocation-surrogate
evidence, not an iOS `phys_footprint` result.

The 2026-08-26 two-device schema-12 pass adds a provider-memory warning to
that acceptance result. Each attached phone provided once while the other
alternated Wi-Fi H1, same-LAN P2P, and cellular. Every client phase stayed
below 24 MiB: 17.19--18.57 MiB on the Galaxy and 22.78--23.04 MiB on the
Pixel. Provider work did not: the Pixel peaked at 26.12 MiB and the Galaxy at
30.43 MiB, including ten Galaxy samples above 28 MiB. At that maximum the
Galaxy had only about 0.21 MiB in returned packet-pool storage, at most
1.78 MiB of packet ownership, 13.99 MiB of live heap, and 748 goroutines.
A fresh host provider profile reproduced the shape after 192 short UDP flows:
30.7 MiB runtime, 13.6 MiB live heap, and 621 goroutines, of which 384 were
per-flow UDP reader/send loops. Treat a provider spike with high goroutine
count and low returned-pool bytes as flow scheduling/stack retention, not as a
reason to shrink the useful H1 window or run a forced GC. The next experiment
must measure a bounded shared provider UDP poller; until it passes, provider
mode fails the <=24-MiB active gate even when client mode passes. Exact device
measurements and the WireGuard/gVisor comparison are in `connect/MEMSTEADY.md`.

The 2026-08-27 current-source fresh-flow pass exercised validated Wi-Fi and
cellular on both phones, followed by exact-ID same-LAN P2P. A public Wi-Fi H1
arm measured 61/40/110 Mbit/s on fast.com (61-Mbit/s median) and 153.1-ms
Wikipedia document TTFB. The other public-route medians were 0.68, 6.3 and
4.4 Mbit/s and P2P measured 3.5 Mbit/s; do not turn the one successful
40-Mbit/s-class route into a universal provider claim. The client runtime
peak/p95 was 22.00/21.61 MiB with no sample above 24 MiB. Provider-inclusive
runtime peaked at 29.45 MiB with two samples above 28 MiB, and its 20-sample
quiet p95 remained 25.20 MiB. Returned packet storage was at most 0.25 MiB and
there were no packet-pressure or H1 receive-queue drops, again pointing to
provider flow/goroutine topology rather than pool retention.

The same pass is the interpretation reference for `chrome_video_probe.mjs`.
Bloomberg played on one public Wi-Fi route even while five 403 Fetch responses
used one reused H2 connection. It failed on another public route where seven
403 Fetch responses used one H2 connection. A forced fresh Chrome transport
did create a new placement opportunity, but its top-level document was also
challenged and Chrome did not retry. P2P likewise received a document challenge
while provider build/policy diagnostics were present and every provider block
counter was zero. Thus default-off fresh affinity can improve only genuinely
new TCP/TLS flows; it cannot reroute requests multiplexed on H2 or guarantee
that a second provider has clean destination-specific reputation.

Parser and eligibility tests are dependency-free:

```sh
node --test app/scripts/physical_lowbar_capture_test.mjs \
  app/scripts/physical_collector_session_test.mjs \
  app/scripts/physical_diagnostic_command_test.mjs \
  app/scripts/physical_diagnostic_copy_test.mjs \
  app/scripts/chrome_readiness_test.mjs \
  app/scripts/physical_memory_profile_test.mjs \
  app/scripts/physical_workload_receipt_test.mjs \
  app/scripts/physical_workload_script_test.mjs \
  app/scripts/physical_quiet_gate_test.mjs app/scripts/physical_quiet_phase_test.mjs
```
