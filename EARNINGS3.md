# EARNINGS3 — points-first earnings, one Bittensor wallet, direct SN25α claims, Top 200

Status (2026-09-02): SDK DONE (all frameworks rebuilt, full test suite green).
Server DONE (spec conformance + new unit tests green; DB-backed model tests not
run locally). Web (mmm/ur.io) DONE (vite build, lint, 70 script tests; verified
against the rebuilt wasm). Apple DONE (iOS + macOS Debug builds against the
rebuilt xcframework; smoke binary over the connect/claim flows; installed on the
iPhone). Windows DONE source-only (names verified against the regenerated C ABI
headers; needs the on-VM build). Linux DONE (ninja build clean, GUI captures of
every state). Android DONE (all four flavors assemble against the final AAR;
github build exercised on the emulator: no-wallet state, connected wallet card,
Unclaimed tile, Top 200 tile, claim dialog ready → sent → claimed, needs-gas
dialog, history rows, bridge round trip, dashboard widget tap landing on
Connect like the tab). This document is the design record, the SDK/server
contract as implemented, and the per-platform checklist. It supersedes the
design review artifact "UR Protocol Earnings on Android".

Related: `android/QUICKCONNECT.md` (widgets), `apple/BITTENSOR.md` (wallet
sign-in bridge), sn `WHITEPAPER.md` §8.4/§11.4 (head miners, fleet binding).

## Goal

Make the app points-first and separate from the subnet, remove USDC payouts,
and let a network settle its earnings as SN25α through the UR protocol with a
claim that never passes through URnetwork.

1. **Points are the headline, always.** Points are URnetwork's own earnings
   system and never go away. The Earnings tab (renamed from Wallets) leads with
   points earned, the breakdown, and a history of points per finalized epoch.
   That is the whole screen for a network without a wallet.
2. **The subnet is an opt-in layer.** Connecting a Bittensor coldkey adds a
   second column: the same history rows gain SN25α, an Unclaimed tile appears,
   and a claim dialog collects it. Nothing says SN25α until a wallet is
   connected, and connecting never replaces points.
3. **One wallet kind.** Only a Bittensor coldkey can be connected. Solana,
   Polygon, Circle programmable wallets and Saga are removed from every app.
4. **No USDC anywhere in the apps.** The single exception is the Solana
   subscription payment note "Paid in USDC on Solana." Past USDC payout history
   is not shown; support@ur.io answers requests for it.
5. **Claims are direct.** The claim is the user's own transaction from the SDK
   on their device to the settlement vault contract. No URnetwork API is in the
   claim path, no relayer submits on a user's behalf, and no URnetwork key ever
   signs an alpha transaction. URnetwork's only role is upstream: it publishes
   the payout artifact whose leaves name each coldkey, and the chain commits its
   root and content hash.
6. **The claim code and the gas key live in the SDK, not the app.** Every app
   (Android, iOS, macOS, Windows, Linux, web) claims through the same code and
   owns only the dialog around `Device.SnClaims` and `Device.SnClaim`.
7. **Not retroactive.** The wallet must be attached before an epoch is committed
   for that epoch to settle to it. Data provided before connecting earns points
   only; that alpha is lost, not deferred. The first alpha row is the first
   epoch after connecting.
8. **Top 200.** A network eligible for one of the ~200 head mining UIDs sees a
   gold "Top 200" tile (and an email line) linking to the ur.io route that
   claims the spot; a bound network sees a quiet status row with UID, hotkey and
   rank plus a demotion warning near the floor.
9. **Every wallet entry validates first**: syntax locally, then the
   unauthenticated validate endpoint (chain existence + ban list). Bad syntax is
   rejected before any network call; a never-seen address warns "looks like a
   new wallet" and continues; a banned address is blocked and never sent on.
10. **The epoch earnings email** replaces the USDC payout email: points, the
    network's share of the block, leaderboard rank, the Top 200 badge/status,
    and unclaimed SN25α only with a wallet, with "Claim your SN25α" linking to
    the ur.io claim route.

## Decisions (2026-09-02)

- **Q1 Solana payments keep one USDC mention.** The Solana one-time yearly
  subscription payment stays; its note reads "Paid in USDC on Solana" and
  nothing else in the app says USDC.
- **Q2 Seeker multiplier is points-only.** A standalone "Verify Seeker" action
  stays on the Solana dApp flavor (not a wallet). The multiplier applies to
  points only and has no effect on SN25α. Apple has no Seeker action; its row
  says "The Seeker multiplier applies to points only."
- **Q3 Claims are direct; the SDK carries a small vault client.** The
  miner, validator and claim daemon stay in the sn repo. The SDK gains only what
  the three app flows need (add a wallet, attach it to the provider, claim):
  copies of `sn/ss58` and `sn/merkle`, a hand-rolled EVM client for the vault
  (no go-ethereum), an SDK-generated gas key, and wallet/claim methods on Api and
  Device. sn keeps its own copies for now and can re-import later.
- **Q4 "Unclaimed" is read from the chain by the SDK** (vault entitlements and
  `leafClaimed` flags), never from a URnetwork mirror.
- **Q5 No USDC history in the app.** Points history only.
- **Q6 One email per finalized epoch**, to networks that earned points in the
  window or hold a leaf in the epoch.
- **Gas.** The gas key is an EVM key the SDK generates and keeps in device local
  state; apps see only its address and ss58 mirror. The user funds it with a
  little TAO. It only ever pays gas: the vault pays the coldkey named in the
  leaf, so a leaked gas key can spend TAO dust, never alpha.
- **Chain parameters** (RPC endpoints, chain id, vault, coordinator, operator
  no_id, netuid, explorer) are SDK release constants with overrides. The release
  vault/coordinator/no_id are not in any repo yet, so they ship EMPTY and are
  filled from `GET /sn/epoch` on first use (see Ops below).
- **Widget tap.** The time-series (dashboard) widget opens the app to the
  connect screen through the same code path as tapping the Connect tab, on
  Android and Apple.

## Audit: what left the apps

Removed on every platform: the Wallets/payout screens and their view models,
Solana Mobile Stack and Saga connect, the Circle programmable wallet (init and
transfer-out), Polygon manual entry, per-chain icon sets, the `Blockchain`
enum branches other than Bittensor, "N USDC" totals and "+X USDC" payout rows,
the "Payouts occur every Sunday…" threshold copy, and the USDC superscript
beside the account earnings figure. The server's `subscription_send_payment`
email (html, txt, sms, subject) and `SendPaymentTemplate` are deleted; the
payout planner is left alone in this phase (retire it separately once no
network has a pending USDC balance). Legacy keys in the localizations store are
deprecated per platform, never deleted (see Strings).

## The Earnings screen

One column, in this order. The points block is the headline in both states.

| Block | No wallet | Wallet connected |
|---|---|---|
| Points earned | total + breakdown: Providing (the server's `payout` event), Referral, Reliability, Multiplier (points-only) | same |
| Protocol note | "SN25α are your earnings on the UR protocol. The UR protocol is an open source protocol from URnetwork. Connect a Bittensor wallet to settle them as SN25α through the UR protocol." + "Learn how it works at ur.xyz" | same note; the wallet block below shows the connected key |
| Top 200 | gold tile when eligible and unbound → ur.io/app/account/top200; status row when bound (UID · hotkey · rank, demotion warning near the floor); nothing otherwise | same |
| Unclaimed | absent | "3.2410 SN25α · 2 epochs · claims open" + Claim button |
| Bittensor wallet | "Connect Bittensor wallet" (+ "Enter address manually") and the not-retroactive sentence | short ss58 + Change; "Connected to the UR protocol. Claims land here. Alpha accrues from the next epoch after connecting." |
| History (per finalized epoch) | "Epoch 42 · 0.71% of block · 1,240 pts" | adds "2.0310 SN25α · unclaimed/claimed/expired"; epochs before the wallet show a dash |
| Leaderboard / rank / reliability | kept from the old screen | kept |

Amounts use the symbol "SN25α" with four decimals and tabular figures, formatted
from rao by the SDK (`FormatAlpha`). Share of block is `share_bps` as a
percentage with two decimals (`FormatShareBps`).

## Connecting the Bittensor wallet

1. "Connect Bittensor wallet" opens the ur.io wallet bridge
   (`ur://bittensor-sign-message`, the same round trip as sign-in) with the new
   purpose `connect`. The signed challenge proves control of the coldkey.
2. Validation runs before anything is sent: `ValidateSs58` locally (reject with
   `invalid_ss58_address`), then `POST /sn/wallet/validate` ("Checking
   address…"): `exists_on_chain=false` → warn `wallet_looks_new_warning` and let
   the user continue; `banned=true` → block with `wallet_blocked`, address never
   sent on. `Device.ConnectSnWallet` does both itself.
3. `POST /sn/wallet` with `coldkey_ss58`, `signature`, `message` (the single-use
   TAO challenge from `POST /auth/wallet-challenge`) and this device's
   `client_id`. The server verifies sr25519 (`model/auth_bittensor.go`) and
   rejects unsigned sets (unless st.yml `wallet_allow_unsigned: true`, a CLI
   compatibility gate, default off) and banned addresses.
4. From the next committed epoch the network's payout leaf names this coldkey.
   The SDK caches the wallet in local state (`.sn_wallet`) and notifies
   listeners; the wallet block shows the connected key. A manual ss58 paste is
   still signed through the bridge, and a signer that is not the typed address
   is rejected.

## Claim dialog

Opens from the Unclaimed tile. Shows the total in gold, each unclaimed finalized
epoch with its amount and status chip, the destination coldkey, and the gas key
(address, ss58 mirror, TAO balance). Everything comes from `Device.SnClaims`;
one tap calls `Device.SnClaim(epochs, callback)`.

States: claimable · needs gas (mirror address, suggested top-up, Copy) ·
sending · sent (tx hash + explorer link) · claimed (confirmed) · expired ·
failed. Error copy: "Connect a Bittensor wallet first", "Send about 0.005 TAO
to 5Gh…2q for gas", "Claims for epoch 41 have expired", "The chain RPC is
unreachable, try again". The needs-gas threshold and the suggested top-up are
UI heuristics (the SDK reports `needs_gas` without an estimate); the SDK's
failure code stays authoritative.

What the SDK does behind `SnClaims` / `SnClaim` (`sdk/sn_claims.go`):

1. `entitlement(epoch, noId)` and `leafClaimed(epoch, keccak(noId, coldkey))`
   from the vault by `eth_call` batches, for the finalized epochs since the
   wallet's `from_epoch` (lookback 128).
2. The payout artifact for (epoch, no_id) fetched by the content hash the chain
   recorded: `<api>/sn/artifact?hash=sha256:<hex>`; sha256 of the unsigned
   canonical json must equal the on-chain `artifactHash`, the artifact's
   epoch/no_id must match, and its `payout_root` must equal the vault root.
3. The network's leaf and Merkle proof rebuilt with `sn/merkle` and verified
   against the vault root before anything is shown as claimable.
4. `claim(epoch, noId, coldkey, shareBps, proof)` calldata, signed with the gas
   key (legacy tx by default, EIP-1559 opt-in via `TxType`), sent with
   `eth_sendRawTransaction`, receipt awaited, reported through the callback.

Hosts that sign with an external wallet (the web claim route with an injected
EVM wallet) use `SnClaimTransactions` / wasm `URnetworkSnClaimTransactionsFor`
to get the unsigned transactions and send them themselves.

## Top 200

Head-miner UIDs: the top ~200 fleets by split-adjusted routable egress-IP score
(each live egress hash worth 1 divided by the networks backing it), paid
natively to their own coldkey, no Merkle claim. `GET /sn/head` returns
`eligible, score, floor, rank_estimate, cutoff (200), bound, hotkey, uid, rank,
epoch, netuid, source`. Phase A (now): `source: "server"`, a server estimate
from the trail egress index, fleet ranking cached 10 minutes, `floor` = the
200th score, `bound` from the mirrored `bindHead` registry then the coordinator
`bindingAt` for up to 8 clients. Phase B: the validators' on-chain consensus,
`source: "chain"`, same words in the UI.

The ur.io route `/app/account/top200`: explain and check (score, floor, what a
head spot pays), register a hotkey (burn registration via the bridge or the
btcli command), bind the fleet (the §11.4 dual-signed payload: each provider
device signs with its Ed25519 client key through `Device.SignSnFleetBinding`,
the hotkey signs sr25519 through the operator's own tooling), then submit the
binding transaction from the operator's own EVM wallet. `POST /sn/head/binding`
stores the client signature and returns `bindFleetMember` calldata only when
both signatures verify; the server submits nothing on-chain for users.

## Epoch earnings email (`subscription_epoch_earnings`)

Subject "You earned 1,240 points this epoch". Body: points earned this epoch ·
share of the current block provided by the network (0.71%) · leaderboard rank
"#38 of 5,120" · Top 200 badge ("Top 200 · you qualify" + "Claim your head
spot" → /app/account/top200; bound: "Top 200 · UID 143 · rank #118", no link;
neither: row omitted) · unclaimed SN25α with "Claim your SN25α" →
ur.io/app/account/claim, only with a wallet (without one: "Connect a Bittensor
wallet to settle your points as SN25α", same route) · "Earnings come from the
UR protocol. Learn how it works at ur.xyz." · the support line. SMS: "Your
network earned 1,240 points this epoch, 0.71% of the block, rank #38. 3.2410
SN25α is ready to claim." Sent once per epoch on the finalized transition
(`stMarkEpochFinalized` → `StNotifyEpochEarnings`, guarded by
`st_epoch_notification`). The template test's one allowed external link moved
from the chain explorer to ur.xyz.

## SDK surface (as implemented, `github.com/urnetwork/sdk`, root package)

Packages: `sn/ss58` (blake2b), `sn/merkle` (sha3), `sn/evm` (ABI for
claim/entitlement/leafClaimed/currentEpoch, RLP, EIP-155 + EIP-1559 signing
with decred secp256k1, JSON-RPC client with endpoint failover and batching,
revert decoding; goldens pinned against go-ethereum and the sn miner),
`sn/protocol` (fleet-binding payload, keccak digest, Ed25519 client signature).
Root files: `sn_util.go`, `sn_chain.go`, `sn_local_state.go` (`.sn_wallet`,
`.sn_gas_key`, `.sn_chain`, `.sn_claim_tx`, `.sn_artifacts/`), `sn_wallet.go`,
`sn_gas.go`, `sn_claims.go`, `sn_head.go`, `sn_device.go`. wasm globals in
`js/sn.go`; the C ABI regenerated (56 new `urnet_*` exports).

Wallet
- `SnWallet{ColdkeySs58, ClientId, SetAtMillis, FromEpoch}`, `SnWalletList`.
- `SnSetWalletArgs{ColdkeySs58, ClientId, Signature, Message}`;
  `Api.SnSetWallet(args, cb)` (async) → `SnSetWalletResult{Wallet, Error}`.
- `Api.SnGetWallet(cb)` → `SnGetWalletResult{Wallet, Wallets, Error}`.
- `Api.SnValidateWallet(address, cb)` → `SnValidateWalletResult{ValidSyntax,
  ExistsOnChain, Banned, Message, Error}` (unauthenticated).
- Device: `GetSnWallet()`, `ConnectSnWallet(coldkey, signature, message, cb)` →
  `SnConnectWalletResult{Wallet, ExistsOnChain, Warning, Error}` (validates
  first; `Warning == "wallet_looks_new_warning"`, `Error.Code ==
  "wallet_blocked"` with nothing sent), `SyncSnWallet(cb)`,
  `ClearSnWalletCache()`, `AddSnWalletChangeListener(l) Sub`.

Gas key and chain settings
- `SnGasKey{Address, MirrorSs58}`; `Device.GetSnGasKey()` (created on first
  call, secret never exported); `Device.SnGasBalance(cb)` →
  `SnGasBalanceResult{Wei, Tao, Error}`; root `SnGasBalanceFor(settings,
  address, cb)`.
- `SnChainSettings{RpcUrls (StringList), ChainId, VaultAddress,
  CoordinatorAddress, NoId, Netuid, ExplorerTxUrl, TxType, LookbackEpochs}` with
  `Merge`, `Copy`, `IsConfigured`, `ExplorerUrlForTx`; `DefaultSnChainSettings()`
  = mainnet 964, `https://lite.chain.opentensor.ai`, explorer
  `https://evm.taostats.io/tx/%s`, netuid 25, legacy tx, EMPTY
  vault/coordinator/no_id; `SnTestnetChainSettings()` (945). Fill with
  `NetworkSpaceValues.SnChain` (json `sn_chain`), `Device.SetSnChainSettings`
  (persisted) or `Device.SyncSnChainSettings(cb)` (reads `GET /sn/epoch`).
  `Device.GetSnChainSettings()` is the merged view. `SnClaims` returns
  `chain_not_configured` until vault + coordinator + no_id are set.

Claims
- `SnEpochClaim{Epoch, ShareBps, AmountRao, Status (open | claimable | claimed
  | expired | not-finalized), ClaimOpenBlock (0 for now), ExpiryBlock, TxHash,
  PayoutRoot, ArtifactHash, Message}`; `SnClaimsResult{Claims (newest first),
  TotalClaimableRao, CurrentEpoch, BlockNumber, ColdkeySs58, Error}`.
- `Device.SnClaims(cb)`; `Device.SnClaim(epochs *Int64List, SnClaimCallback{
  Sent(epoch, txHash), Confirmed(epoch, txHash, amountRao), Failed(epoch,
  message), Done()})`; `Device.SnClaimTransactions(epochs)` →
  `SnUnsignedTxList` of `SnUnsignedTx{To, Data, Value, ChainId, Epoch}`.
- Root: `SnClaimsFor(settings, coldkey, fromEpoch, cb)`,
  `SnClaimTransactionsFor(...)` (wasm/hosts without a device).
- `SnError{Code, Message}`; codes: `invalid_ss58_address`, `wallet_blocked`,
  `connect_wallet_first`, `chain_not_configured`, `chain_rpc_unreachable`,
  `chain_rpc_error`, `needs_gas`, `claims_for_epoch_expired`, `already_claimed`,
  `not_claimable`, `artifact_unavailable`, `proof_mismatch`, `claim_failed`,
  `local_state_unavailable`, `server_error`. `Failed` messages start with the
  code followed by `": detail"`.

Points, head, binding
- `Api.AccountEpochs(cb)` → `AccountEpochsResult{Epochs: AccountEpochList of
  AccountEpoch{Epoch, StartMillis, EndMillis, Points, ShareBps}, Error}`.
- `Api.SnHead(cb)` → `SnHeadResult{Eligible, Score, Floor, RankEstimate,
  Cutoff, Bound, Hotkey, Uid, Rank, Epoch, Source, Error}`.
- `Device.SignSnFleetBinding(bindingJson) (string, error)`,
  `Device.GetSnClientKey()`, root `SnFleetBindingDigest(bindingJson)`.
- `Api.SnEpoch(cb)` (async) → `SnEpochResult` now also carrying
  `SettlementVaultAddress, NoId, Netuid, RpcUrl`.

Utilities: `ValidateSs58`, `ShortSs58`, `EvmMirrorSs58`, `FormatAlpha(rao)`
("3.2410 SN25α"), `FormatAlphaAmount`, `AlphaFromRao`, `FormatShareBps`,
`VerifyPayoutProofHex(rootHex, leafHex, *StringList)` (`VerifyPayoutProof` is
noexport: gomobile cannot bind `[][]byte`), `SnPayoutLeafHex`, `Int64List` /
`NewInt64List`, constants `SnAlphaSymbol`, `SnRaoPerAlpha`, `SnSs58Prefix`.

DeviceRemote (Apple app, hosted, Windows, Linux) exposes the same surface as
DeviceLocal: wallet, gas key, chain settings, vault reads and claim sends run in
the calling process, no tunnel needed; only `SignSnFleetBinding` and
`GetSnClientKey` forward over DeviceLocalRpc.

wasm: `URnetworkDefaultSnChainSettings`, `URnetworkValidateSs58`,
`URnetworkSnClaimsFor`, `URnetworkSnClaimTransactionsFor`,
`URnetworkSnGasBalanceFor`, `URnetworkFormatAlpha` (PascalCase objects). C ABI:
`urnet_device_{local,remote}_{get_sn_wallet, connect_sn_wallet, get_sn_gas_key,
sn_gas_balance, sn_claims, sn_claim, get_sn_chain_settings,
sync_sn_chain_settings, …}`, `urnet_api_{account_epochs, sn_get_wallet,
sn_set_wallet, sn_validate_wallet, sn_head}`, `urnet_validate_ss58`,
`urnet_format_alpha`, `urnet_format_share_bps`, `urnet_short_ss58`,
`urnet_default_sn_chain_settings`, `urnet_sn_claims_for`,
`urnet_sn_claim_transactions_for`.

Gotchas: `[32]byte` json fields decode from number arrays (how sn/payoutartifact
marshals); the artifact content hash covers the canonical json with
signer/content_hash/signature zeroed (splice-replace, never re-marshal); vault
amounts are alpha rao; the js and cgo modules have their own go.mod and needed
`go get` of decred secp256k1; subtensor EVM works with legacy txs.

## Server (as implemented; spec in `connect/api/bringyour.yml`)

| Endpoint | Auth | Shape |
|---|---|---|
| `POST /sn/wallet` | network | `{coldkey_ss58, signature, message, client_id?}`; single-use TAO challenge verified sr25519; unsigned rejected unless `wallet_allow_unsigned`; banned rejected |
| `GET /sn/wallet` | network | `{wallet?, wallets: [{coldkey_ss58, client_id?, set_at_millis}]}` |
| `POST /sn/wallet/validate` | none | `{address}` → `{valid_syntax, exists_on_chain, banned, message?}`; existence = `System.Account` via `state_getStorage` through the st gateway, cached per key, rate limited per source IP, fails open with a message; ban list `controller.snBannedColdkeys` (empty) |
| `GET /account/epochs?limit=N` | network | `{epochs: [{epoch, start_millis, end_millis, points, share_bps}]}` newest first (default 26, max 104); bounds from chain block times or an estimate anchored on finalized_time |
| `GET /sn/head` | network | see Top 200; chain outage degrades to the estimate, never an error |
| `POST /sn/head/binding` | network | `{binding{…}, client_signature, hotkey_signature?, hotkey?}` → `{digest, client_signature_valid, hotkey_signature_valid, ready, calldata?, to, contract_address, chain_id}`; stored in `st_fleet_binding_signature` |
| `POST /stats/leaderboard` | network | adds `rank` (0 = unranked) and `total` |
| `GET /sn/epoch` | network | adds `settlement_vault_address`, `no_id`, `netuid`, `rpc_url` (from st.yml `public_rpc_url`; absent until ops sets it) |
| `GET /sn/artifact?hash=sha256:<hex>` | none | immutable artifact bytes, hash rechecked before serving; the on-chain root commitment carries the same hash |

Migrations: `st_fleet_binding_signature`, `st_epoch_notification`. Email
templates: `subscription_epoch_earnings.{html,txt,sms.txt,subject.txt}`
replace `subscription_send_payment.*`. New files:
`controller/{sn_earnings_controller,sn_substrate,epoch_earnings_email}.go`,
`model/{sn_earnings_model,verify_head_model}.go`. `MissingWalletTemplate` is
untouched because the USDC payout planner still sends it.

## Strings

All copy comes from the localizations store (`keys/*.yaml`); generated
`strings.xml` / `.xcstrings` / `.resw` / `.po` are never hand-edited. Shared
keys added for every platform: `earnings`, `points_earned`, `providing`,
`sn_alpha_symbol` (untranslatable), `sn_protocol_note`, `learn_at_ur_xyz`,
`connect_bittensor_wallet`, `bittensor_wallet`, `wallet_connected_to_protocol`,
`wallet_not_retroactive`, `enter_address_manually`, `invalid_ss58_address`,
`unclaimed`, `claim`, `claim_alpha_title`, `claim_amount_button`,
`claim_across_epochs`, `claims_open_after_finalization`,
`claim_sends_from_device`, `gas_key`, `add_tao_for_gas`, `send_tao_to_mirror`,
`claim_sent/confirmed/expired/failed`, `claims_for_epoch_expired`,
`chain_rpc_unreachable`, `connect_wallet_first`, `epoch_history`,
`epoch_row_title`, `epoch_share_of_block`, `points_short`, `top200`,
`top200_you_qualify`, `top200_detail`, `claim_your_spot`, `top200_bound_status`,
`top200_bound_detail`, `top200_demotion_warning`, `verify_seeker`,
`seeker_points_only`, `paid_in_usdc_on_solana`, `no_points_yet`,
`earnings_email_support`, `wallet_looks_new_warning`, `wallet_blocked`,
`checking_wallet_address`. Platform-only keys carry a suffix or prefix
(`*_linux`, `earnings_*` for windows, `site_app_*` for the web).

Legacy wallet-era keys (USDC, Solana/Polygon/Circle/Saga, payouts) are
deprecated per platform with `deprecated: - <platform>`; Apple keeps them as
`extractionState: stale`. The generator's dead-key rule now also treats a key
whose last platform retired itself (`platforms: []` with a non-empty
`deprecated`) as dead, so retired keys no longer leak into the Windows and Linux
outputs, which otherwise carry every key in the store.

## Per-platform notes

**Android (reference).** `ui/wallet/` now holds `EarningsScreen.kt`,
`EarningsViewModel.kt`, `EarningsModels.kt` (the `EarningsProtocolSource` seam
with `NoProtocolSource` / `SampleProtocolSource` and debug flags),
`SdkProtocolSource.kt` (the real SDK binding: wallet cache + listener, connect,
validate, gas key and balance, claims, claim callback, epochs, head, formatting,
chain settings synced once before the first vault read), `ClaimDialog.kt`,
`ConnectWalletSheet.kt`, `EpochHistory.kt`, `Top200Tile.kt`, plus
`utils/Ss58.kt` (local syntax gate). `MainNavHost` gained `Route.Earnings` and
`selectTopLevelRoute`, the single tab-selection function shared by the bottom
bar and the dashboard widget, and a pending navigation after the bridge
redirect; the four `LoginActivity` forward `purpose=connect` redirects to
`MainActivity` with `SnWalletConnectExtras`. Deleted: WalletsScreen,
WalletViewModel, WalletsScreenViewModel, NoPayoutsFound, WalletsPayoutsList,
WalletProviderIconButton, ManualWalletAddressSheet, WalletChainIcon,
SetupWallet, PayoutRow, AccountPoints, WalletCard, AddWalletPopup,
`ui/payout/PayoutScreen.kt` and the four flavor `WalletScreen.kt`; Saga/MWA
wallet retrieval left the `MainActivity`s. The Solana dApp flavor keeps the
"Verify Seeker" action (points-only) and the three alt subscription screens
show "Paid in USDC on Solana". Manual entry still signs through the bridge
(the server rejects unsigned sets); a looks-new address asks for confirmation
before `ConnectSnWallet`. Gas threshold constants (`MIN_GAS_TAO` 0.002,
suggested 0.01 TAO) are guesses pending real numbers.

**Apple (iOS, macOS).** `app/network/Main/Account/Earnings/` (11 files):
`EarningsView` + `EarningsTiles`, `ConnectBittensorWalletFlow` + sheet,
`ClaimAlphaSheet`, `EarningsClient` (SDK + preview), `EarningsViewModel`
(syncs chain settings once before the first vault read). Removed the WalletsView
tree, WalletView, PayoutItemView, PaymentsList, PayoutWalletTag, WalletIcon,
WalletChain and the AccountWallets/PayoutWallet/AccountPayments view models.
`selectConnectTab()` in MainTabView and MainNavigationSplitView is the single
path for the Connect tab item and the dashboard widget. The change listener
imports into Swift as `add(_:)`. The apple catalog was not regenerated by the
stream (new keys display English until the store owner regenerates).

**Web (mmm/ur.io).** `/app/account/earnings` replaces Wallets (old `wallets`
and `payouts` paths redirect); `/app/account/claim` (wasm `SnClaimsFor` +
`SnClaimTransactionsFor`, sent by `window.ethereum` after switching/adding the
chain; without an injected wallet it explains the device gas-key alternative);
`/app/account/top200` (status, hotkey guidance, one binding payload per provider
device posted to `/sn/head/binding`, hotkey-signature field, submission from the
operator's own EVM wallet or copy). `src/app/sn/addressCheck.js` (local ss58
with `@noble/hashes` blake2b or the wasm check, then the validate endpoint),
`WalletConnectCard.jsx`, `wasm.js` (feature-detects the four globals, lays the
server's `/sn/epoch` release constants over the SDK defaults once per page
load). The browser does not produce the hotkey signature (extension signatures
are Bytes-wrapped); the page asks for it from the operator's tooling.

**Windows (WinUI 3, uncompiled on the Mac).** `WalletPage.*` rewritten as the
Earnings destination (three panes), `EarningsSheets.*` (formatters +
`ClaimAlphaSheet`) replace `WalletSheets.*`, `SdkHost::SignWithBittensorWallet`,
`WalletConnect::SignMessageBittensor(purpose)`, six new MainWindow handlers,
`App.vcxproj` updated, 28 resw regenerated. Verified against the regenerated
`sdk/cgo/include` headers only; the VM build must compile it and confirm the
PRI build has no missing-resource warnings. The app process holds only a
`DeviceRemote`, so claims and the gas key are unreachable after Disconnect tears
it down (the tile says claims load once the app has a session). No Solana
payment path, so no USDC note. `AddSnWalletChangeListener` not wired (the page
reloads after connect).

**Linux (GTK4).** `app/src/EarningsPage.{hpp,cpp}` rewritten (panes A/B/C,
`ClaimAlphaSheet`), `SdkHost::SignBittensorConnect`, `WalletConnect` purpose
parameter, `UrTheme` gold classes, preview hooks (`URNETWORK_PREVIEW_UI=earnings
URNETWORK_PREVIEW_SAMPLE=1` + `URNETWORK_PREVIEW_{WALLET,CLAIM,GAS,MANUAL,TOP200}`),
`docs/parity/earnings.md`. The SDK's sn state lives on the `DeviceRemote`, which
the host constructs only when the tunnel first starts, so the claim tile reads
"Claiming is not available on this device yet" before that; constructing the
device at sign-in would lift this. `chain_not_configured` renders as the generic
error (no store key). Not exercised live (no signed-in session or chain here).

## Ops and release checklist

- Set the release `settlement_vault_address`, `no_id` and `netuid` so
  `GET /sn/epoch` serves them (the SDK defaults are empty and every app shows
  the chain-not-configured state until then), and st.yml `public_rpc_url`
  (the gateway `rpc_urls` are LAN-only; absent → SDK default RPC).
- Fill `controller.snBannedColdkeys` when the operator ban list exists.
- The ur.io bridge must accept and echo `purpose=connect`.
- Windows: rebuild the DLL on the VM from `sdk/cgo/build/URnetworkSdkWindows.zip`
  (the local zip was zig cross-built) and compile the app there.
- Apple: regenerate the catalog (`gen:apple`) once the store owner confirms
  the catalog has no drift; until then new keys show English.
- Retire the USDC payout planner and `MissingWalletTemplate` once no network has
  a pending USDC balance.
- Windows/Linux: consider constructing the remote device at sign-in so claims
  and the gas key are available without starting the tunnel.
- Add a store key for `chain_not_configured` if the state should read better
  than the generic error on Linux.
- Phase B for Top 200: read the validators' published consensus from the chain
  (`source: "chain"`), and a chain-side floor.

## Open items

- `ClaimOpenBlock` is always 0; the "claims open 48 hours after finalization"
  copy is informational until the vault exposes the open block.
- The head `floor` is the server's 200th-score estimate, not a chain value.
- The wallet challenge message is the login one ("Sign in to URnetwork"); the
  server does not key on `purpose`.
- DB-backed server model tests (`sn_earnings_model`, wallet set/get) were not
  run locally; run them where a Postgres test env exists.
- `cespare/xxhash/v2` is a direct server import still marked indirect in go.mod.
