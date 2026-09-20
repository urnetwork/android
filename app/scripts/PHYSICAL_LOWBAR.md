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
`Target.createBrowserContext`; restart Chrome before each candidate and omit
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
argument. The test keeps one authenticated process alive and drains the SDK's
fixed primitive ring every five seconds. The Go sampler records every 15
seconds without constructing gomobile exit/status/list graphs; the faster host
drain only publishes newly available records. The test accepts private
`ID|VERB|ARG` commands through its app-data acceptance directory. Useful verbs
are `phase`, `connect` (`h1`, `h3`, or `auto`),
`provide`, `peer-connect` (same-network P2P) and `peer-platform-connect`
(fixed provider over the selected platform carrier), both with optional `h1`,
`h3`, or `auto`, `disconnect`, `stop-provide`, `snapshot`,
`heap-profile`, `trim-memory`, and `finish`. `finish` is required: it joins the
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

### Mandatory quiet-window evidence gate

Before any workload, start the collector in a retained owner, retain its exact
PID, and wait for at least one fresh eligible telemetry sample. Keep it alive
through the final quiet gate. Starting it after Wikipedia/fast.com is
`INCOMPLETE_ACTIVE_COVERAGE`, even if all five quiet minutes are captured.

Run the frozen probes through `physical_workload_receipt.mjs`: it predeclares
their order, owns the foreground command, and emits a mode-0600 receipt only
after every named child is joined and explicit browser cleanup is verified.
It checks collector PID, same file/first sample, fresh eligible tail, and gaps
before and after every child. Do not pass a shell/executor session ID as a PID.
The receipt is bound to the block label and serial hash. A yielded executor,
live process group, omitted child, interruption, or missing cleanup cannot
produce a qualifying receipt. Never synthesize that JSON or reuse another arm.

The workload body is a private shell file, not a new benchmark runner. It wraps
the existing commands unchanged. For the normal public block, use the exact
child list `wiki,fast-1,fast-2,fast-3,cnn,bloomberg` and this pattern:

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
node "$RECEIPT" cleanup --serial "$SERIAL"
```

For the video commands above, `--navigate` without `--target-id` creates an
owned `about:blank` page, attaches the probe before navigating, and closes only
that page on success or failure. It must not select an arbitrary existing tab.
An explicit `--target-id` preserves the existing manual-probe behavior and
leaves that caller-owned page open. A CLI/setup exit 1 is a harness failure,
not a playback result; retain it and do not count the site as tested.

After profile/Chrome/collector readiness, launch and retain the owner:

```sh
export SERIAL CDP_PORT PRIVATE_DIR CNN_URL BLOOMBERG_URL
export SCRIPTS="$ROOT/android/app/scripts"
export RECEIPT="$SCRIPTS/physical_workload_receipt.mjs"
node "$RECEIPT" owner --serial "$SERIAL" --label "$LABEL" \
  --collector-pid "$collector_pid" --telemetry "$TELEMETRY" \
  --children wiki,fast-1,fast-2,fast-3,cnn,bloomberg \
  --output "$PRIVATE_DIR/workloads.json" -- sh "$PRIVATE_DIR/traffic-workload.sh"
# A tool yield means STILL RUNNING. Resume/join this exact owner to exit 0.
# If it exits nonzero, finish/clean up the failed attempt; do not start quiet.
```

Ordinary completed probe exit codes (for example video exit 2) remain in the
receipt and `failedChildCount`; they are not performance/correctness successes.
They permit diagnostic quiet collection only after all work is joined. Keep
every original result; no retry or baseline promotion hides those failures.

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
Install the two-line user/password file through `run-as` standard input with a
private umask; never put credentials or the retained acceptance client ID in a
command line or checked-in artifact. Release that client with
`build/all/acceptance/client-cleanup.mjs`, then remove the host/device files.

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
  app/scripts/chrome_readiness_test.mjs \
  app/scripts/physical_memory_profile_test.mjs \
  app/scripts/physical_workload_receipt_test.mjs \
  app/scripts/physical_quiet_gate_test.mjs app/scripts/physical_quiet_phase_test.mjs
```
