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

## Working notes

- **Merge bottom-up.** `connect` → `sdk` → `android`. Merging android first breaks CI with "Unresolved reference" against SDK symbols that do not exist yet — this has already cost three CI round-trips once.
- **Sibling checkouts.** Any new local `replace` in a `go.mod` needs a matching clone step in *every* workflow that builds it — the Android workflow, the SDK build inside it, and the SDK's own workflow. `goidenticons` had to be added in three places.
- **Verify the index before every commit** (`git ls-files | wc -l`, expected 438 for android) and confirm the staged diff against a known-good ref. `.git/index` and `.git/config` have both been deleted mid-session by a file-sync client; a partial index silently commits a truncated tree.
- **Do not claim runtime behaviour.** Nothing here can be executed locally. State plainly what was verified by test versus what is reasoned.
