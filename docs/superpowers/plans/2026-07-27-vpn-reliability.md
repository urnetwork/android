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

## Working notes

- **Merge bottom-up.** `connect` → `sdk` → `android`. Merging android first breaks CI with "Unresolved reference" against SDK symbols that do not exist yet — this has already cost three CI round-trips once.
- **Sibling checkouts.** Any new local `replace` in a `go.mod` needs a matching clone step in *every* workflow that builds it — the Android workflow, the SDK build inside it, and the SDK's own workflow. `goidenticons` had to be added in three places.
- **Verify the index before every commit** (`git ls-files | wc -l`, expected 438 for android) and confirm the staged diff against a known-good ref. `.git/index` and `.git/config` have both been deleted mid-session by a file-sync client; a partial index silently commits a truncated tree.
- **Do not claim runtime behaviour.** Nothing here can be executed locally. State plainly what was verified by test versus what is reasoned.
