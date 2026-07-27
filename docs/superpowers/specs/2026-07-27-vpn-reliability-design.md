# VPN tunnel reliability + developer test menu — design

Date: 2026-07-27
Status: Approved (design phase) — see `docs/superpowers/plans/2026-07-27-vpn-reliability.md` for execution.

## Context

Reported symptom: with multiple IP exits in play, **DNS or whole websites freeze until the user reconnects or restarts the browser**. The goal is traditional-VPN smoothness without giving up multi-exit routing.

A read-only investigation across all three repos (`Ryanmello07/connect`, `Ryanmello07/urnetwork-sdk`, `Ryanmello07/urnetwork-android`) produced the causes below. Every claim here was read out of source; **none of it was observed at runtime** — there is no Go toolchain or JDK on the dev machine, and no packet capture was taken. Ranking is reasoning, not measurement. That is exactly why the developer menu (below) is part of this work and not a nice-to-have.

## How the tunnel actually works (established facts)

**Android tun** (`MainService.kt`): MTU 1440 (`:269`), `setBlocking(false)` (`:270`), `setUnderlyingNetworks(null)` (`:271`), address + DNS from the SDK (`tunnelLocalAddress()`, `tunnelDnsAddressesIpv4()`), default route minus RFC1918, per-app split via `addAllowedApplication`/`addDisallowedApplication`.

**Exits are not packet forwarders.** In `connect/ip.go` the provider calls `DialContext` (`:2430` TCP, `:1329` UDP) and synthesizes the SYN-ACK back to the client (`:3140`). Each provider **terminates** the client's TCP connection and opens its **own socket** to the destination — a split-TCP user-space NAT.

Two consequences drive this entire design:

1. **A TCP connection cannot survive an exit change.** The replacement provider has no knowledge of the old socket. There is no migration path — only reset. This is by design and is not being changed.
2. **Classic PMTUD blackholing is largely ruled out** as a cause. There is no MSS clamping and the tunnel never emits ICMP "fragmentation needed", but because providers terminate TCP, path MTU to the destination is the provider OS's concern. The 1440 MTU only governs the local tun.

**Flow → exit binding.** Each flow (5-tuple) is pinned to one provider channel via `multiClientChannelUpdate` (`ip_remote_multi_client.go:1092`). `affinityIpPathsWithLock` (`:933`) groups flows so a whole site shares one exit, keyed on the **base domain** — `a.foo.com`, `b.c.foo.com` and `foo.com` all collapse to `foo.com`.

**Window sizes** (`:113`): Quality min 2 / max 6 / hard max 12; Speed `FixedWindowSize: 1`, max 2, hard max 4.

**Auto routes by port** (`:922`): destination 443 → `[Quality, Speed]`; everything else → `[Speed, Quality]`. **Auto's behaviour is explicitly out of scope for this change** (user decision).

## Root causes, ranked

### C1 — UDP flows have no failure signal *(best match for the reported symptom)*

`removeClient` (`:1363`) sends a TCP RST for every affected flow, so the browser learns immediately and retries. But `ipOosRst` builds a RST **only for TCP** — every other protocol returns `false`:

```go
// ip_packet.go:29
func ipOosRst(ipPath *IpPath) ([]byte, bool) {
	switch ipPath.Protocol {
	case IpProtocolTcp:
		...
	default:
		return nil, false   // UDP: no signal at all
	}
}
```

No UDP equivalent, no ICMP port-unreachable. The flow goes silent and is re-raced onto a new exit with a different source IP (`:1760`). Both **DNS (53)** and **QUIC/HTTP3 (UDP 443)** ride on this. A QUIC session whose source IP changes mid-stream stalls until the browser abandons it and falls back to TCP — which commonly survives a tab reload. That is precisely "freezes until you restart the browser", and TCP would not behave this way because it receives a RST.

### C2 — TCP retransmits suppressed for up to ~30s

`TcpCollapsePrevention: true` (`:198`). Once a packet is committed to an exit, pure retransmits are dropped — `canUpdateSequence` returns false so `canSendPacket` refuses them (`:1687`). Sound in principle: the provider channel is reliable, so retransmits are duplicates.

The failure case is an exit that is **stalled but not yet declared dead**. The app's retransmits — its only recovery mechanism — are discarded until failure detection fires, bounded by `BlackholeTimeout` 5s, `StatsWindowMaxUnhealthyDuration` 15s, `AckTimeout` 30s (`:132`). Up to ~30s of a fully frozen connection with no path to self-heal.

The code already suspects something here: `// TODO it's still not clear why one client might stop working occasionally` (`:2821`).

### C3 — Per-site affinity silently degrades to per-IP

Affinity depends on `serverNameLookup`, populated by the DNS mux observing plaintext queries on :53 (`ip_mux_upgrade.go:1380`). With no hostname it falls back (`:970`) to per-destination-IP for ports 80/443/53.

It goes blind whenever the app runs its own DoH (Chrome Secure DNS, Android Private DNS) or the OS answers from cache — a case the source comments on directly, citing a 24h-TTL record. A CDN-hosted site spans many IPs; per-IP affinity in Web mode scatters them across up to 6 exits, so one page load originates from several IPs.

### C4 — Affinity computed once, at flow creation

`affinityIpPathsWithLock` runs only when a flow is first created (`:1163`). If the DNS answer has not been recorded yet, early flows get **IP-based** affinity while later ones get **name-based** — different groups, different exits, one site split.

### C5 — 2-minute idle timeout

`SequenceIdleTimeout: 120s` (`:117`). Flows idle 2 minutes are torn down and reset. Traditional VPNs hold TCP NAT state 5–30 minutes. Breaks idle-but-live SSH, websockets and push connections far more aggressively than users expect.

Related, not separately fixed: `MaxClientLifetime: 60min` (`:190`) puts an exit into a permanent drain state (`:2859`) — graceful on its own, but combined with C1 it silently kills long-lived UDP sessions on a timer.

## Decisions

**D1 — Multi-IP becomes a dedicated, orthogonal option.** Today "multi-exit" is welded to Web (Quality) and "Fixed IP" is disabled in Auto (`ConnectViewModel.kt:130`). It becomes its own toggle applying equally to Web and Streaming, so those two modes present the **same UI** and differ only in provider selection strategy. Auto is untouched.

**D2 — Every fix is a runtime toggle.** C1–C5 each get a switch. This converts "which cause was it?" from speculation into an A/B the user can run against a live freeze. It costs plumbing through connect → sdk → android, and that cost is the point: the ranking above is unverified reasoning, so shipping all five silently would leave us unable to tell which mattered.

**D3 — The Developer section ships in all builds** as a normal `URTextInputLabel` section near the bottom of Settings, beside Version info — discoverable, not hidden, not `BuildConfig.DEBUG`-gated. It contains buttons that deliberately disrupt live connections, so destructive actions must be clearly labelled and require no ambiguity about what they do.

**D4 — Defaults ship conservative.** Each fix defaults to **on** except where it changes behaviour users may depend on; C5's timeout change ships as a value bump, not a removal. Any fix can be switched off in the field without a rebuild.

## Test control surface

Already exported, no new work:

| Capability | API |
|---|---|
| Full provider swap | `DeviceLocal.Shuffle()` (`sdk/device_local.go:2749`) → cancels every client in every window (`connect:3259`) |
| Window state readout | `GetWindowStatus()` → `TargetSize`, `MinSatisfied`, `ProviderState{InEvaluation,EvaluationFailed,NotAdded,Added,Removed}` (`sdk/device.go:293`) |
| Live window changes | `AddWindowStatusChangeListener` |
| Packet counters | `GetProviderPacketStats()`, `AddProviderPacketStatsChangeListener` |
| Live profile change | `SetPerformanceProfile()` (`connect:782`) |

To be added (Phase 2):

- **Drop a single provider** — `Shuffle()` is all-or-nothing; simulating *one* exit dying is the exact C1/C2 trigger.
- **Simulate a stalled provider** — accepts packets, never acks. The only way to exercise C2 deliberately.
- **Force full tunnel reconnect.**
- **Per-flow readout** — flow count and flow→exit mapping, to see splitting (C3/C4) as it happens.

## Verification constraints (read this before implementing)

- **No Go toolchain and no JDK on the dev machine.** Nothing in `connect`, `sdk` or the Android app can be compiled or run locally. CI is the only verification.
- **No Go tests run in CI anywhere today.** `connect` has only `provider-release.yml` (triggers on `go.mod`/`go.sum`); the SDK workflow builds Android + iOS but never tests. `connect` *does* have good pure-Go unit tests with no network dependency (`ip_remote_multi_client_window_type_test.go`, `..._pqe_test.go`, `..._identity_test.go`). They need to be *run*, which is Phase 0 and blocks everything else.
- **`connect` and `sdk` are shared with iOS and Windows.** Data-path timing changes affect every platform, not just Android. Changes must be justified as correct in general, not merely as Android fixes.
- **Three-repo dependency chain** (`connect` → `sdk` → `android`), each round-trip ~8 minutes of CI. Merge bottom-up.
- **Runtime confirmation belongs to the user.** The agent cannot run the VPN. The fastest discriminator between C1 and C2 is to reproduce a freeze and check whether the stuck traffic is QUIC (UDP 443 → C1) or TCP (→ C2); they need different fixes.

## Out of scope

- Auto mode's port-based window selection (explicit user decision).
- The split-TCP provider architecture.
- MSS clamping / ICMP fragmentation-needed generation — investigated and largely ruled out (see "How the tunnel actually works").
- `MaxClientLifetime` rotation policy.
