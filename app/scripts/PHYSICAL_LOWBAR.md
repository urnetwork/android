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
The following gate accepts only a cellular, active, unmetered, IPv4-only VPN at
Android signal level one or below:

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
node app/scripts/chrome_page_benchmark.mjs \
  --port 9222 \
  --runs 5 \
  --fresh-context \
  https://www.wikipedia.org/
```

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
cache before every measured run. After a force-stop/restart, require two
successful `/json/version` probes at least five seconds apart before starting
the warm-up; current production Chrome can briefly publish its DevTools socket
and replace that browser process during startup.

For memory work, build the app and Android-test APK with a unique
`-PurnetworkAcceptanceBuildId=LABEL` and run
`PhysicalLowbarSessionTest` with the same `acceptanceBuildId` instrumentation
argument. The test keeps one authenticated process alive and drains the SDK's
fixed primitive ring every five seconds. The Go sampler records every 15
seconds without constructing gomobile exit/status/list graphs; the faster host
drain only publishes newly available records. The test accepts private
`ID|VERB|ARG` commands through its app-data acceptance directory. Useful verbs
are `phase`, `connect` (`h1`, `h3`, or `auto`),
`provide`, `peer-connect`, `disconnect`, `stop-provide`, `snapshot`,
`heap-profile`, `trim-memory`, and `finish`. `finish` is required: it joins the
sampler, writes `physical-summary.json`, disconnects both roles, and logs out.
Install the two-line user/password file through `run-as` standard input with a
private umask; never put credentials or the retained acceptance client ID in a
command line or checked-in artifact. Release that client with
`build/all/acceptance/client-cleanup.mjs`, then remove the host/device files.

`physical-memory.ndjson` separates Go live/allocated/in-use/idle/released heap,
runtime overhead, object/allocation/free counts, GC/forced-GC/pause counters,
pool in-flight/retained/capacity bytes, automatic reclaim decisions and
before/after values, aggregate mobile packet-pressure drops, primitive
client/flow topology, platform transport-budget use, Android PSS, Java/native
heaps, threads, descriptors, route state, and aggregate carrier counters. The
steady streamline signal is `goRuntimeBytes`:
five quiet connected minutes should have p50 and p95 at or below 20 MiB. Keep
the active peak and time-to-recover separate, and investigate every sample over
the 28-MiB active diagnostic threshold. Neither threshold is Android whole-app
PSS or an iOS Network Extension `phys_footprint` ceiling.

Ordinary Android and Apple SDK libraries start the Go runtime with
`memprofilerate=0`. Android and iOS also use the same `GOGC=10` pacing for the
20-MiB campaign; a looser Android heap float is not a valid surrogate for the
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
the <=20-MiB mobile profile samples packet-root ownership every fourth ingress
call below pressure, rejects a complete native ingress batch at 512 or more
roots, and samples every call until ownership drains. Rejected batch ownership
is returned immediately; TCP retransmission/backpressure provides recovery.
Server/default devices do not instantiate this gate. The same mobile profile
retires inactive TCP flow state after three minutes so a closed browser burst
does not preserve the desktop ten-minute graph throughout the five-minute
steady window.

Parser and eligibility tests are dependency-free:

```sh
node --test app/scripts/physical_lowbar_capture_test.mjs
```
