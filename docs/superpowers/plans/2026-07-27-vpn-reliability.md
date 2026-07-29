# VPN tunnel reliability + developer test menu — implementation plan

Date: 2026-07-27
Design: `docs/superpowers/specs/2026-07-27-vpn-reliability-design.md`

Repos, in merge order (bottom-up — each layer must be green before the next):

1. `Ryanmello07/connect` @ `beta/custom-server`
2. `Ryanmello07/urnetwork-sdk` @ `beta/custom-server`
3. `Ryanmello07/urnetwork-android` @ `feat/vpn-reliability-and-dev-menu` → `beta/custom-server`

---

## Phase 0 — Make Go changes verifiable *(blocks everything)*

Nothing below can be trusted until this lands: there is no Go toolchain locally and no Go tests run in CI today.

- [ ] **0.1** Add `.github/workflows/test.yml` to `connect`: `go test ./...` on push/PR to `beta/custom-server`. Needs the sibling checkouts the module replaces require — `glog` and `goidenticons` (mirror `urnetwork-sdk/.github/workflows/beta-build.yml`, which clones them to `../`). Confirm whether `connect`'s own `go.mod` has local replaces before assuming which siblings are needed.
- [ ] **0.2** Confirm the existing suite passes **unmodified** on `beta/custom-server`. A red baseline must be understood before any behaviour change — do not fix and change in the same commit.
- [ ] **0.3** Add the same test step to the `sdk` workflow.

**Exit criteria:** green `go test ./...` on both repos, with the pre-existing test count recorded in the commit message as the baseline.

---

## Phase 1 — `connect` reliability fixes

One commit per fix, each with unit tests in the existing style. Every fix lands behind a `MultiClientSettings` field (D2) defaulting per D4.

- [ ] **1.1 — C1: UDP teardown signal.** Extend the removal path so non-TCP flows emit ICMP destination-unreachable (port unreachable) toward the source, instead of `ipOosRst` returning `false` and the flow going silent. Touches `ip_packet.go` (new builder) and `removeClient` / the `sendPacket` error path in `ip_remote_multi_client.go`. Setting: `UdpTeardownSignal` (default **on**).
  - Tests: given a removed client with a live UDP flow, an ICMP unreachable is delivered to the source; TCP still gets RST; the setting off restores current behaviour.
  - Care: must not fire for the mux's own DNS responder flows (see `blockActionIgnored`) or it will fight the resolver.
- [ ] **1.2 — C2: bound retransmit suppression.** Add a time bound to `canSendPacket`'s collapse-prevention gate so a retransmit is admitted after ~1–2s rather than being held until `AckTimeout` (30s). Touches `canSendPacket` / `multiClientChannelUpdate` sequence state. Setting: `TcpCollapseMaxHold` (default ~1.5s; zero = current behaviour).
  - Tests: a retransmit inside the bound is dropped; the same retransmit after the bound is admitted; SYN/RST still always pass.
- [ ] **1.3 — C3: pin QUIC affinity.** Treat UDP/443 as connection-oriented in `affinityIpPathsWithLock` so a QUIC session does not get re-raced like a stateless datagram. Setting: `QuicFlowAffinity` (default **on**).
  - Tests: two UDP/443 flows to the same base domain resolve to the same affinity path; UDP/53 is unaffected.
- [ ] **1.4 — C4: re-key affinity on late hostname learn.** `invalidateServerNames` (`:867`) currently only drops block-action decision caches. Extend it to re-evaluate affinity grouping for flows created before the name was known. Setting: `AffinityRekeyOnLearn` (default **on**).
  - Care: must **not** migrate an already-established flow to a different exit — that would cause the very breakage being fixed. Re-keying applies to grouping for *future* flows only. Verify this explicitly in a test.
- [ ] **1.5 — C5: idle timeout.** Raise `SequenceIdleTimeout` for TCP toward ~10 min while keeping UDP short. May need the single setting split into TCP/UDP values.
  - Tests: TCP flow idle 3 min survives; UDP flow idle 3 min is reaped.

**Exit criteria:** `go test ./...` green; each fix independently switchable; no change in behaviour with all new settings at their "off" value (proves the toggles are honest).

---

## Phase 2 — Test control surface (`connect` → `sdk`)

- [ ] **2.1** `connect`: export drop-one-provider (remove a single named client from the window, distinct from `Shuffle()`'s all-or-nothing cancel).
- [ ] **2.2** `connect`: export simulate-stalled-provider — a client that accepts sends and never acks, to exercise 1.2 deliberately.
- [ ] **2.3** `connect`: export per-flow readout (flow count, flow → exit mapping) for observing C3/C4 splitting live.
- [ ] **2.4** `connect`: export getters/setters for the Phase 1 settings so they are runtime-togglable.
- [ ] **2.5** `sdk`: surface 2.1–2.4 plus force-reconnect on `DeviceLocal`, gomobile-compatible.
  - Care: gomobile binds a restricted type set. Follow the existing convention — no maps, no slices of pointers across the boundary; use `*XList` wrapper types as `ProviderIdentityList` does. Check `cgo/coverage_report.txt`'s "skipped" section after building to confirm nothing silently dropped off the binding.

**Exit criteria:** SDK builds Android + iOS green; new symbols present in the generated binding.

---

## Phase 3 — Android: settings restructure (D1)

- [ ] **3.1** Replace the `fixed_ip` toggle with a dedicated **Multi-IP** option, applying to both Web and Streaming; remove the `enabled = selectedWindowType != WindowType.AUTO` gate (`ConnectActions.kt:285`) so both modes present the same UI. Auto unchanged.
- [ ] **3.2** Update `ConnectViewModel.updatePerformanceProfile()` (`:223`) — the multi-IP toggle drives `windowSizeMin`/`windowSizeMax` for **both** Quality and Speed, replacing the current `fixedIpSize` branch.
- [ ] **3.3** Strings: add the new label + description; retire `fixed_ip` if unused. Add to `values/strings.xml` **only** — translations are generated upstream. Re-check for duplicate keys after editing (this file has bitten us before).

---

## Phase 4 — Android: Developer section (D3)

- [ ] **4.1** New `URTextInputLabel("Developer")` section in `SettingsScreen.kt`, positioned near Version info (`:1162`) — visible in all flavors, following the existing section idiom.
- [ ] **4.2** Actions: Shuffle providers (existing `Shuffle()`), Drop one provider, Force reconnect, Simulate stalled provider. Destructive actions clearly labelled as disrupting the live connection.
- [ ] **4.3** Toggles: one switch per Phase 1 fix, wired through the 2.4/2.5 setters.
- [ ] **4.4** Live readout: `GetWindowStatus()` (target size, providers added/removed/failed), packet stats, active flow count + flow→exit mapping — via the existing change listeners, not polling.

---

## Phase 5 — Verification

- [ ] **5.1** `go test ./...` green on `connect` and `sdk`.
- [ ] **5.2** All 4 Android flavors green (`github`, `play`, `ethos_dapp`, `solana_dapp`).
- [ ] **5.3** Duplicate-resource and `R.*` resolution check on `strings.xml` before pushing.
- [ ] **5.4** **User-run**: reproduce a freeze, then use the dev menu to determine whether the stuck traffic is QUIC (UDP 443 → C1) or TCP (→ C2), and A/B the corresponding toggle to confirm the fix is the one that mattered.

---

---

## Phase 6 — connection setup latency (added 2026-07-27, from device measurement)

Measured on device with `curl -w` phase timings, VPN on, warm cache:

| phase | time | |
|---|---|---|
| DNS | 1.5ms warm / 190ms cold | not a problem |
| TCP handshake | ~90ms | client↔provider only, see below |
| **TLS handshake** | **~510ms** | dominant cost; ~1.3s for apple.com, reddit.com |
| response | ~90ms | fine |

Off-VPN the same requests total ~90ms, so the tunnel costs roughly **10x on
every new connection**. This is separate from the freeze work in Phase 1: every
request in the sample returned 200, nothing broke.

**Why TLS looks so expensive.** Providers are split-TCP -- `ConnectionState.SynAck`
synthesizes the SYN-ACK locally, so `time_connect` measures only client↔provider
and the provider does not dial the real server until traffic arrives. The
provider→server TCP connect therefore lands *inside* the TLS bucket. ~510ms is
provider→server connect (~100ms) plus TLS round trips across two legs (~190ms
each), not a protocol defect.

**A rejected hypothesis, recorded so it is not re-run:** that the transport pays
a round trip per packet rather than windowing, which would have made the
multi-packet certificate flight cost 5-6 RTTs. Not supported -- `transfer.go` has
a proper ack window, a 32-deep pack buffer and a 256KiB in-flight floor
(`ResendQueueMinByteCount`).

**TLS is not the cost -- measured, not inferred.** Running the same hosts over
plain http and https:

| | conn | ttfb-conn | tls-conn |
|---|---|---|---|
| example.com http | 134ms | **663ms** | no tls |
| example.com https | 121ms | 781ms | **663ms** |
| cloudflare.com http | 110ms | **593ms** | no tls |
| cloudflare.com https | 122ms | 733ms | **612ms** |

Plain http, with no handshake at all, pays the same ~600ms between tcp connect
and first byte. On https that same ~600ms simply occupies the tls window, and
the request afterwards costs only ~120ms. So the cost is **the first round trip
to a new destination through a provider**, and tls is incidental to it.

Client↔provider is healthy at ~120ms. The ~600ms is the provider dialing the
destination and relaying the first exchange -- roughly three round trips on the
provider→server leg.

Do not re-run `openssl s_client -reconnect` to test resumption: under tls 1.3 it
reports `New` for every connection because the session ticket arrives after the
handshake and `-reconnect` does not wait for it. It is a known false negative,
and it does not matter here since tls is not the bottleneck.

Candidate improvements, in the order the measurement supports, each needing its
own toggle and A/B:

- [ ] **6.1 Provider-side connection reuse. CONFIRMED by measurement.** Three
  sequential connections to the same destination held ttfb-conn flat at 559 /
  571 / 521 ms while conn itself fell from 213ms to 100ms as the client↔provider
  path settled. Nothing on the provider→server leg is reused between flows, so
  every new flow pays the full dial again. Pooling provider→destination
  connections is the fix.
- [ ] **6.2 Exit selection weighted by destination proximity.** Two-leg latency
  is client→provider plus provider→destination. Selection currently optimizes
  the first leg (throughput, health) and ignores the second, which is where the
  ~600ms lives.
- [ ] **6.3 Investigate pre-dial and relay overhead. Do this before building
  the pool.** ~550ms is far more than a dial to an anycast host should cost -- a
  healthy host reaches example.com in about two round trips. Pooling would hide
  that overhead rather than remove it, so if a few hundred ms of it is avoidable
  work, that is worth knowing before caching around it.** Three round trips is more
  than a bare tcp connect should need. Check whether contract setup, security
  policy, or buffering delays the provider's dial -- the `[contract]wait` logs in
  the test suite suggest per-destination setup worth ruling in or out.
- [ ] **6.4 Happy-eyeballs on first contact.** COLD showed 1.6-6.4s for a new
  destination. Racing first contact across two exits and keeping the winner
  would cut the tail.
- [ ] ~~TLS session resumption across exits~~ -- ruled out by the http/https
  comparison above.

**Also observed, and belongs with Phase 1 rather than here:** github.com COLD
showed `tot=6.38s` with `tls=0.81s` -- a normal handshake followed by a 5.5s
stall in the response body. That is a flow stalling after connecting, which is
exactly what C1/C2 and the RST fix address, and the first direct evidence of the
freeze in a measurement.

## Phase 7 — measurement and A/B (added 2026-07-28)

Requested scope widened: A/B every candidate, and make a provider drop or
blackhole minimally affect the client on **every** platform (iOS, extension,
Android, desktop). That means candidates land in `connect`/`sdk`, never in
Android UI — the dev menu is one front-end onto shared Go, which is already how
the reliability toggles are built.

### What device evidence established first

- **Exit churn is not the cause.** A 10-minute filtered logcat capture during
  normal use recorded **zero** `remove error client` events, grid stable at
  7→7 throughout. The freeze is not exits being replaced. This killed the
  hypothesis that faster removal/failover was the fix.
- **One transport per exit.** `ip_remote_multi_client_api.go:381` creates a
  `PlatformTransport` per `Client`, so every flow pinned to an exit shares one
  ordered TCP stream and head-of-line blocks together. Matches the device
  result exactly: stalling the 55-flow exit is catastrophic, stalling a 0-flow
  exit is free.
- **QUIC exists and is switched off.** `transport.go:391` — `TransportModeAuto`
  runs only `runH1`; all three `runH3` calls are commented out, above a TODO
  that UDP needs PROXY protocol support in the load balancer
  (nginx/nginx#1061). Upstream's own note says h3 is more CPU efficient with
  better throughput on poor networks.
- **QUIC as implemented would not fix HOL blocking.** `transport.go:1165` opens
  exactly one stream (`OpenStreamSync`, the only stream call in the file). A
  single QUIC stream is reliable and ordered, like TCP. The win that would
  address the symptom — a stream per flow — is not taken.

### Hard limit, applies to every platform

Providers are split-TCP: the exit *is* the remote TCP endpoint. When it dies
those connections cannot migrate, and no client-side work recovers them. The
achievable goal is "the peer is told immediately and reconnects in ~1 RTT",
not "the user sees nothing". State this rather than let it be discovered.

### 7.1 Measurement *(blocks every candidate below)*

- [x] `connect/reliability_metrics.go` — blast radius (flows destroyed per exit
  loss, plus worst single event) and recovery time (exit death → first packet
  back from that destination over a replacement exit). Nil-receiver safe: the
  counters sit on the packet path and must never be able to crash it.
- [x] `sdk` bindings — `GetReliabilityMetrics` / `ResetReliabilityMetrics`,
  int64 and milliseconds since gomobile binds neither uint64 nor
  time.Duration.
- [x] Android readout at the top of the Developer screen, with reset.
- [ ] Measured failure-injection workload: N parallel fetches, blackhole an
  exit mid-run, report recovery as a number. Turns the existing Stall button
  from a vibes test into an A/B measurement.
- [ ] Config snapshot + tagged JSON export so runs compare offline.

`RecoveryMissed` is deliberately reported next to recovery time: a change that
abandons flows rather than recovering them improves the average while making
the product worse, and one number alone cannot catch that.

### 7.2 Candidates, in expected-value order

- [ ] **Cap flows per exit.** Observed distribution 55/18/10/0/0/0 across six
  exits. Spreading cuts blast radius directly. Weigh against destination
  affinity, which concentrates same-domain flows deliberately for stable
  exit IP.
- [ ] **Multiple transports per exit.** The structural fix for per-exit HOL
  blocking.
- [ ] **Pre-warmed spare exits** so replacement costs ~1 RTT, not a cold
  contract negotiation.
- [ ] **Confirm teardown reaches the app.** ICMP port-unreachable and
  RST-with-correct-sequence are built but never verified on-device to reach
  the browser. The whole 30s→1RTT argument depends on this and it is still
  assumed.
- [ ] **QUIC with a stream per flow.** Real fix; needs the load-balancer work
  server-side before the client path can be re-enabled.

## Phase 8 — device testing on mainnet (2026-07-28)

Tested beta-104 against mainnet providers with a USB logcat capture
(`.tools/testA.txt`). Two tests, and the first one's instrument turned out to
be broken — which is itself the main finding.

### The Stall control could not be detected by its own detector

`SendDetailedWithAck` returned on `stalled.Load()` **before** `addSend`.
`addSend` is what sets `pendingSendTime` and increments `sendNackCount`, and
`sendStalled` treats a channel with nothing outstanding as idle rather than
broken. So a stalled exit was invisible to the detection built for it.

Device evidence: stalled an exit carrying a running ISO download at 05:07:20.
Egress to tcp:443 went 97082 → 97105 in the next 30s (~23 packets — the
download was dead), no stall detection, **no `remove error client` at all**,
and Chrome sat at "pending". The window changed at 05:07:52 with no removal
behind it, so that was unrelated rotation.

Fixed by moving the check after `addSend`, which is also the faithful
simulation: a provider that really blackholes commits the packet and never
acknowledges it.

**This invalidated the conclusion drawn from that test.** "Teardown never
happens" and "the browser-backoff theory is dead" were both read off a broken
instrument. They are unproven, not disproved.

### What Drop showed

`DropExit` bypasses that path entirely. Dropping produced removals within ~1s:

```
05:12:31.390  remove error client [019fa89d...] = Blackhole (7 360B)
05:12:31.478  remove error client [019fa8a0...] = Blackhole (7 360B)
05:12:32.132  remove error client [019fa8a0...] = Blackhole (8 400B)
```

So removal and teardown **do** run, and fast, via pre-existing blackhole
detection independent of `sendStalled`. Three removals because the tester
dropped three exits to bisect which carried the download — not a cascade.

But the download still sat at 0bps rather than failing. So the teardown path
executes and the browser still does not recover. **That is the open question:
whether the teardown packets reach the app at all, or reach it and are
ignored.** Those need different fixes and cannot be told apart by watching
Chrome's UI, which is why teardown emission is now logged.

### 8.1 Done

- [x] Stall check moved after `addSend`, so the control reproduces what it
  claims to. `TestStalledChannelSwallowsWithoutError` now asserts the stall
  clock actually starts, and builds a channel with the state accounting
  touches — the old bare struct passed only because the early return skipped
  everything.
- [x] Teardown emission logged in `removeClient`, including the `ctx.Done()`
  branch where teardown is skipped entirely — otherwise "never sent" and
  "sent and ignored" are indistinguishable.

### 8.2 Next

- [ ] **Re-run the stall test on a build with the fix.** Everything about
  stall behaviour is currently unmeasured, not measured-and-bad.
- [ ] **Settle teardown vs browser** using the new logs: teardown sent → does
  Chrome error? If the packet is emitted and the download still hangs, the
  problem is delivery into the tun, not generation.
- [ ] **`watchSendStalls` only calls `resizeMonitor.NotifyAll()`** — it never
  removes the client itself. Confirm the resize pass actually removes a
  stalled client, or detection fires into the void.
- [x] Metrics panel now polls while open. A stale zero read as "no provider
  failures" rather than "nothing measured yet" during testing, and was nearly
  taken as evidence that detection was broken.
- [ ] Test B (new-domain latency) still unrun.

### 8.3 False positive in the same detector (found in review, pre-existing)

`addSend` runs *before* the transport call. If
`SendMultiHopWithTimeoutDetailed` returns `(false, nil)` -- `sendBuffer.Pack`
timing out under backpressure -- or errors, the `ackCallback` is never
invoked: it is only stored on the `SendPack`, and a `Pack` that never enqueues
never reaches `ackItem`. So `sendNackCount` is permanently incremented for
every dropped send.

Failure: a client hits its send-buffer limit during a burst, a few `Pack`
calls time out, traffic shifts elsewhere and the client goes idle. With no
further acks `pendingSendTime` is never reset, so `sendStalled` returns true
and `resize` removes a client that never misbehaved.

Not introduced by the stall fix and already reachable in production, since
real providers always took the `addSend` path. Impact is bounded -- the client
is replaced -- but it is a false positive in the detector, and it becomes more
consequential now that the detector is known to work. Fix by decrementing on
the transport failure paths, or by starting the clock only once the transport
accepts the packet.

### Standing lesson

Four times this session a change was correct in a unit test and wrong in
place: the ICMP code Linux discards, the 15s detection cadence, the leaderboard
fix that was unreachable, and now a Stall control invisible to its own
detector. The failure mode is verifying that code *works* rather than that it
is *reached*. Prefer a device measurement over a passing test when the claim is
about runtime behaviour.

## Phase 9 — mainnet under load (2026-07-28)

### beta-104 baseline vs beta-106 (blackhole fix)

| | beta-104 | beta-106 |
|---|---|---|
| removals/min | 1.92 | **0.40** |
| median gap | 18.5s | **60.5s** |
| span | 18 min | 128 min |

The blackhole receive-timeout split cut removals ~79%. Measured, not argued.

Reasons, now attributable because each branch names itself: **41 no-receive-ack,
10 no-receive-syn**. The earlier claim that all removals came from the
receive-ack branch was inference; the syn branch is real and accounts for ~20%.

### Blast radius is the dominant term

beta-106, 51 removals over 128 min, teardown sizes:

	1 1 2 2 4 4 6 7 20 20 26 35 36 36 43 44 46 53 62 71 73 101 157 484

Most removals cost nothing. A handful cost almost everything. Reported stalls
against sizes:

| flows | stall |
|---|---|
| 4-6 | 3-5s |
| 44 | ~15s |
| 157 | ~15s |
| 484 | ~35s |

Recovery grows sublinearly with a long plateau. **The median teardown was 36
flows, so a permissive cap does nothing** -- 64 would not have touched a single
15s stall. Hence MaxFlowsPerExit default 16.

### The cap must apply to affinity, not just the race

`inheritAffinityClient{4,6}WithLock` writes `update.client` directly and never
reaches `raceClients`. A cap enforced only at client selection would test clean
and never fire for the flows that concentrate -- a feed opens many connections
to a handful of domains, which is exactly what affinity pins together. Applied
at both points.

Never makes a flow unroutable: if every candidate is at the cap the flow is
placed anyway. Bounding blast radius must not turn a slow page into a broken
one.

### Unexplained, and not addressed by anything built so far

A **68 second** stall against a teardown of **2 flows**. The flow-count model
predicts this should have been imperceptible. Ruled out during triage:
`create client args expired` fires on a fixed 2-minute cadence and is not
stall-correlated.

Two leads, both new:

- [ ] **The tunnel drops all ICMP.** 222 `No support for protocol 1` and 6 for
  protocol 58 in one session. Unknown whether anything depends on it.
- [ ] **The window runs below target more than at it** -- grid samples at 5
  (131) and 6 (96) versus 7 (128), and one backfill took ~45s.

### QUIC is the majority of traffic

udp:443 700,715 packets vs tcp:443 540,958 -- **56% QUIC**. Those flows get an
ICMP unreachable rather than a RST (`UdpTeardownSignal` is on by default), and
whether Chrome's QUIC stack acts on it is still unverified. This is the
standing candidate for why recovery is seconds rather than immediate, and it
is what task 7 should settle.

## Phase 10 — flow cap verified, and what it exposed (2026-07-28)

### The cap holds

beta-108, on device. Teardown sizes across the session: 13 14 15 16 16 16 16 --
never above the bound, on both the affinity and race paths. In-app
measurements agreed:

| | beta-104 | beta-108 |
|---|---|---|
| Worst single failure | **484 connections** | **16** |
| Blast radius | -- | 3.9 per failure |
| Removal rate | 1.92/min | ~0.4/min |

Exits readout confirmed the distribution: 14 / 15 / 7 / 0 / 0.

### Recovery latency is independent of blast radius

	Recovery time:   avg 14.554s, worst 1m
	Never came back: 13 of 70

Blast radius fell 30x and recovery barely moved. **Losing 16 connections costs
about the same wall clock as losing 484**, which rules out connection rebuild
as the cost. Something is waiting on a timeout.

### QUIC cannot be signalled, by design

56% of traffic is QUIC (udp:443 700,715 packets vs tcp:443 540,958).

`ipOosUnreachable` is correctly formed -- embeds the original ip header plus
the 8 transport bytes RFC 792 requires, port-unreachable code, correct v4 and
v6 checksums. It does not matter: **RFC 9000 requires QUIC endpoints not to
terminate a connection on ICMP**, because ICMP is spoofable. It is honoured
only for PMTU. So `UdpTeardownSignal` is well-built and structurally inert for
the majority of traffic.

The useful corollary: QUIC identifies connections by Connection ID, not by ip
4-tuple -- which is what makes migration work across a wifi/cellular switch.
So a QUIC flow *can* survive its exit dying. Verified the mechanism already
exists: `removeClient` sets `update.client` to nil but keeps the update, and
`raceClients` in `sendPacket` re-races the flow onto a surviving exit on the
next packet. The server sees the same CID from a new source ip and validates
the path.

### Suspected: the blackhole fix partly undid itself

If flows already re-race, the 14.5s is most likely Chrome's PTO backoff,
accumulated while the flow black-holed **before** the exit was removed. The
recovery clock starts at removal, so that pre-removal period is invisible in
the metric but is felt by the user.

`BlackholeReceiveTimeout` went 5s -> 20s, which is what cut removals 78%. It
also quadrupled how long a flow on a quiet exit black-holes before rescue, and
QUIC's PTO doubles per loss.

- [ ] **A/B this, no build needed.** Reset, 20 min at 20s, note recovery avg;
  set the knob to 5s, reset, 20 min, compare. If recovery drops at 5s the two
  fixes are fighting and the answer is somewhere near 8-10s with the flow cap
  carrying more of the load. If it does not move, the backoff theory is wrong
  and 20s is free.

### Still unexplained

The 68s stall against a 2-flow teardown from Phase 9 remains unaccounted for.
Leads unchanged: the tunnel drops all ICMP (222 protocol-1 rejects in one
session), and the window runs below target more often than at it.

## Working notes

- **Merge bottom-up.** `connect` → `sdk` → `android`. Merging android first breaks CI with "Unresolved reference" against SDK symbols that do not exist yet — this has already cost three CI round-trips once.
- **Sibling checkouts.** Any new local `replace` in a `go.mod` needs a matching clone step in *every* workflow that builds it — the Android workflow, the SDK build inside it, and the SDK's own workflow. `goidenticons` had to be added in three places.
- **Verify the index before every commit** (`git ls-files | wc -l`, expected 438 for android) and confirm the staged diff against a known-good ref. `.git/index` and `.git/config` have both been deleted mid-session by a file-sync client; a partial index silently commits a truncated tree.
- **Do not claim runtime behaviour.** Nothing here can be executed locally. State plainly what was verified by test versus what is reasoned.
