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
argument. The test keeps one authenticated process alive, samples every second,
and accepts private `ID|VERB|ARG` commands through its app-data acceptance
directory. Useful verbs are `phase`, `connect` (`h1`, `h3`, or `auto`),
`provide`, `peer-connect`, `disconnect`, `stop-provide`, `snapshot`,
`heap-profile`, `trim-memory`, and `finish`. `finish` is required: it joins the
sampler, writes `physical-summary.json`, disconnects both roles, and logs out.
Install the two-line user/password file through `run-as` standard input with a
private umask; never put credentials or the retained acceptance client ID in a
command line or checked-in artifact. Release that client with
`build/all/acceptance/client-cleanup.mjs`, then remove the host/device files.

`physical-memory.ndjson` separates Go live/allocated/in-use/idle/released heap,
runtime overhead, object/allocation/free counts, GC/forced-GC/pause counters,
pool in-flight/retained/capacity bytes, automatic idle-trim counters, Android
PSS, Java/native heaps, threads, descriptors, route state, and aggregate
carrier counters. The 28-MiB streamline signal is `goRuntimeBytes`, not Android
whole-app PSS. A diagnostic build can set
`-PurnetworkMemoryProfileRateBytes=65536`; ordinary builds must omit it and keep
Go's production 524,288-byte rate. `heap-profile` forces a GC and writes a
private pprof file, so record that forced collection and do not compare its
post-profile sample as an unperturbed recovery point. Interpret
`poolOutstanding` as live/in-flight ownership and `poolRetainedBytes` as
returned free-list ownership: pooling can reduce allocation/GC churn while
still retaining a burst high-water until the quiet-period rebuild runs.

Parser and eligibility tests are dependency-free:

```sh
node --test app/scripts/physical_lowbar_capture_test.mjs
```
