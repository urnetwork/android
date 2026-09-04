# All-time points leaderboard — design

Status: DECISIONS RESOLVED 2026-09-03 (D1–D4 answered by the user; D5–D8 are the defaults below). Implementation not started. Order of work once decisions are made:
server → SDK view controller → Android (reference UI) → iOS/macOS → ur.io /app → Windows/Linux.

## What was asked (2026-09-03)

> In the apps we need to add an all time points leaderboard, and it needs to be infinitely
> scrollable for anyone who opts into public points leaderboard. The points leaderboard should
> include: total points, number of blocks with points, consecutive blocks with points (streak).
> Each dimension should have a rank, and the leaderboard should be sortable by any dimension.
> The leaderboard view should have tabs: data and points.

> For the points leaderboard, users have to opt in to display on the leaderboard. I want users to
> be able to associate a string of emojis (only emojis, 1-6 long) with their network name. The
> emojis show even if the network name is not public. Since they are emojis, we don't need to do
> profanity or review of the content.

## What exists today

- **Points** are `account_point` rows per network (`point_value` in nano-points, events
  `payout`, `payout_linked_account`, `payout_multiplier`, `payout_reliability`, `create_time`),
  written by the payment planner when a payout plan is populated
  (`model/account_point_model.go`).
- **Epochs** are the SN payout epochs (`st_epoch`: epoch number, start/finalize chain blocks,
  status, finalized time). An epoch's wall-clock window comes from its chain blocks
  (`snEpochWindow` in `controller/sn_earnings_controller.go`), and a network's points *in* an
  epoch are the point rows whose `create_time` falls in that window
  (`GetNetworkNanoPointsInWindow`). The Earnings screens and the epoch email already call the
  epoch's payout "the block" ("share of block").
- **Data leaderboard**: `POST /stats/leaderboard` returns the top 100 networks by payout bytes
  over the last 4 subsidy payments, computed on every call (`model/leaderboard_model.go`);
  `GET /network/ranking` gives the caller's rank (cached 5 min in-process);
  `POST /network/ranking-visibility` sets `network.leaderboard_public`, which controls whether the
  network NAME shows (hidden rows still appear as anonymous). Android renders it in
  `ui/leaderboard/LeaderboardScreen.kt` (LazyColumn, header with own rank + public switch);
  iOS/macOS, ur.io (`app/screens/Leaderboard.jsx`) and Windows have equivalents; Linux and the
  extension do not.

## Definitions (proposed)

| Term | Definition |
|---|---|
| Total points | `SUM(point_value)` over every `account_point` row of the network, all time. Displayed in points (1 point = 1,000,000 nano points, the same `snNanoPointsPerPoint` the epochs endpoint uses) with the existing `FormatPoints` style. |
| Block | One **finalized** SN epoch. Only finalized epochs count; the open epoch is never counted. |
| Blocks with points | Number of finalized epochs in which the network's points inside the epoch window are > 0. |
| Streak | Number of consecutive finalized epochs with points, **ending at the latest finalized epoch** (current streak). If the latest finalized epoch has no points the streak is 0. `longest_streak` is also stored (cheap, same pass) and shown as a secondary stat, but the rank is on the current streak. |
| Rank | Competition ranking per dimension (`RANK()`: 1, 2, 2, 4) on the sort's full key tuple: a rank is shared only when all three values tie. Tie-break order per sort (user, 2026-09-03), every key desc: **points = (points, streak, blocks)**, **blocks = (blocks, streak, points)**, **streak = (streak, blocks, points)**, then network id asc — a total order, so pages never overlap. The one definition lives in the SDK (`ComparePointsLeaderboardKeys` next to the view controller, which keeps its rows in that order); the server ranks and pages with the same function. |

## Visibility and identity

- **Name switch**: `network.points_leaderboard_public` (default false) means "show my network
  name on the points leaderboard". It is independent of the data leaderboard's
  `leaderboard_public`. Toggled from the Points tab header. (Until 2026-09-04 it meant "appear
  at all"; see the amendment below.)
- **Emoji tag**: new `network.emoji_tag`, 1–6 emoji. Validation (server, and the same Go
  function exported through the SDK so the editor validates live):
  - split into grapheme clusters; every cluster must be an RGI emoji sequence (single
    pictograph, keycap, flag, skin-tone modified, ZWJ family/profession sequences all count as
    ONE); letters, digits, punctuation, whitespace, variation-selector-only or ZWJ-only clusters
    are rejected; 1 ≤ clusters ≤ 6; NFC-normalized; stored as text. Empty string clears the tag.
  - No profanity/review pass (emoji only), as requested.
- **What a row shows**: every ranked network has a row.
  - emoji tag, always, if the network set one;
  - the network name only when the network turned on `points_leaderboard_public`;
  - otherwise the row reads "Anonymous" + emoji (or just "Anonymous").
- **Own row**: the caller always sees its own name, stats and ranks in the header; when the
  name switch is off the hint says the row shows as Anonymous to everyone else.

## Data architecture (server)

- **Snapshot table** `network_points_leaderboard`:
  `snapshot_id, network_id, total_nano_points, blocks_with_points, streak, longest_streak,
  rank_points, rank_blocks, rank_streak`, indexed per snapshot on each `(rank_x)` and on
  `network_id`. Name, emoji and both public flags are JOINed from `network` at read time, so a
  toggle or a new emoji shows on the next request, not the next rebuild.
- **Rebuild** (one task, idempotent): triggered when an epoch finalizes and when a payout plan
  completes (points only change then), plus an hourly fallback. One pass:
  1. per-network totals: `SELECT network_id, SUM(point_value) FROM account_point GROUP BY 1`;
  2. per-(network, epoch) presence for finalized epochs (window join on `create_time`);
  3. streaks in Go over the ordered epoch list per network (epochs are few, networks with
     points are tens of thousands: trivial);
  4. ranks with `RANK()` per dimension, written under a new `snapshot_id`; keep the newest two
     snapshots, prune older. If the full aggregate ever exceeds ~1 min, add an incremental
     `network_points_total` maintained in `ApplyAccountPointsInTx` — not needed on day one.
- **Rank population** (decision D3): rank every network that has any points, or only opted-in
  networks. Recommendation: rank everyone; the flag only controls display. Ranks are then
  stable and honest ("#37 of 61,204"), the own-row rank needs no separate "would-be" math, and a
  toggle never shifts other people's ranks. The public list therefore has gaps (#1, #4, #9…),
  which is fine and expected.
- **Pagination**: keyset cursor = `base64({snapshot_id, sort, last_value, last_tiebreaks,
  last_network_id})`; a request whose snapshot was pruned gets `{restart: true}` and the client
  reloads from the top. Page size 50, max 200. No offsets.
- **Reads**: `WHERE snapshot_id = $1 AND (public filter) AND (keyset) ORDER BY … LIMIT n`,
  index-backed; no per-request aggregation. Optional 30 s response cache is not needed.

## API

- `POST /stats/points-leaderboard` (no auth required; `me` only when authenticated)
  ```
  { "sort": "points" | "blocks" | "streak", "cursor": "…"?, "limit": 50? }
  →
  { "rows": [{ "network_id", "network_name"?, "emoji_tag"?, "anonymous": bool,
               "total_points": 1234.5, "blocks_with_points": 12, "streak": 4, "longest_streak": 9,
               "rank_points": 37, "rank_blocks": 12, "rank_streak": 3 }],
    "next_cursor": "…"?, "restart": bool?, "total_ranked": 61204,
    "snapshot_time": RFC3339, "latest_epoch": 57,
    "me": { …same fields…, "points_leaderboard_public": bool }? ,
    "error": { "message" }? }
  ```
- `GET /network/ranking` gains `points_leaderboard_public`, `emoji_tag`, and the three points
  ranks (so the existing header code on every platform can show them without a second call).
- `POST /network/points-ranking-visibility { "public": bool }`
- `POST /network/emoji { "emoji_tag": "🐬🔥" }` → `{ "emoji_tag" }` or `{ "error": { "message": "Use 1 to 6 emoji." } }`
- `/stats/leaderboard` (data tab) is unchanged.
- `connect/api/bringyour.yml` documents all of it.

## SDK (shared by every app)

- `PointsLeaderboardViewController` (Go, gomobile + wasm + C ABI, allowlisted in
  `build/cmd/mobileexports`): owns `sort`, the appended rows, `loading`, `endReached`, `me`,
  `error`; `SetSort(sort)` (clears and reloads), `LoadMore()` (guarded: one in flight, stops at
  end), `Refresh()`, listeners `PointsLeaderboardChanged`; rows carry preformatted strings
  (points, ranks) next to raw values, like the other list VCs. The app never sorts or ranks.
- `Api.SetPointsLeaderboardPublic`, `Api.SetEmojiTag`, and a pure exported
  `ValidateEmojiTag(s) → (ok, message, count)` used by the editor before the request.
- The data tab keeps `Api.GetLeaderboard`/`GetNetworkLeaderboardRanking` (optionally wrapped in
  a `DataLeaderboardViewController` later for parity; out of scope here).

## Android (reference UI)

- `LeaderboardScreen` gets a tab row **Data | Points** under the title (the existing content
  becomes the Data tab unchanged).
- **Points tab**: header card = own emoji tag + name, three stat tiles (Points, Blocks,
  Streak) each with its rank chip, the "Show on the points leaderboard" switch, and an "Edit
  emoji" pencil that opens a bottom sheet: an emoji-only text field (the emoji keyboard opens;
  non-emoji is rejected live via `ValidateEmojiTag`, counter "3 / 6"), Save/Clear.
- Sort chips **Points · Blocks · Streak**; the list re-sorts through `SetSort`.
- Rows: rank of the active sort (large), emoji + name (or Anonymous), the three values with the
  active one emphasized; own row highlighted when it appears.
- Infinite scroll: `LazyColumn`, `LaunchedEffect` on the last visible index within 10 of the
  end → `loadMore()`; footer spinner; pull-to-refresh → `refresh()`; a `restart` response
  reloads silently.
- Strings: new keys in the localizations store (`android` + the other platforms as they port).

## Rules that apply to the implementation

- Every new or changed endpoint is documented in `connect/api/bringyour.yml` in the same
  commit (user rule, 2026-09-03).
- Every UI string comes from the localizations store.
- Apps never sort, rank or page on their own: the SDK view controller is the only source.

## Decisions (resolved)

- **D1 Block = finalized SN epoch** — RESOLVED (user, 2026-09-03). The open epoch never counts.
- **D2 Streak = current streak** — RESOLVED (user). Consecutive finalized epochs with points
  ending at the latest finalized epoch; 0 if the latest was missed. `longest_streak` stored and
  shown as a secondary number, never ranked.
- **D3 Rank population = everyone with points** — RESOLVED (user). The opt-in only controls
  display; the visible list has gaps; "of N" = all ranked networks.
- **D4 Every ranked network is listed; the switch reveals the name** — RESOLVED (user,
  2026-09-04, replacing the 2026-09-03 "opt-in = appear at all"). The list is one continuous
  sequence with no gaps, loaded in 50-row chunks by the view controller as the user scrolls.
  A row shows the network name only when `points_leaderboard_public` is on; otherwise it reads
  "Anonymous". The data leaderboard's `leaderboard_public` does not affect the points list.
- **D5 Emoji visibility**: the emoji tag shows on every row that set one, named or not, and in
  the network's own header.
- **D6 Ties**: competition ranking (1, 2, 2, 4) on the full key tuple; tie-break order per sort = points (points, streak, blocks), blocks (blocks, streak, points), streak (streak, blocks, points), then network id — RESOLVED (user, 2026-09-03), defined once in the SDK view controller.
- **D7 Rebuild cadence**: on epoch finalize + payout plan complete + hourly fallback; two
  snapshots retained — default.
- **D8 Page size 50**, max 200; `me` is null when unauthenticated — default.

## Amendment (user, 2026-09-03): emoji editor
> The emoji editor should use an emoji-only keyboard. The emoji field should have a default
> suggested random emoji string of 1-3 long.

- The editor never opens the system text keyboard: the field is a read-only display of the
  current tag with a backspace, and an emoji-only keyboard is rendered below it (Android:
  `androidx.emoji2:emojipicker` `EmojiPickerView`; iOS/macOS: the system emoji keyboard forced
  (`UIKeyboardType` has none, so use a custom emoji grid or `textInputMode` emoji trick where
  reliable); web: an in-page emoji picker grid). Pasted/typed non-emoji is impossible by
  construction; `ValidateEmojiTag` still runs before Save.
- When the network has no tag, the field is prefilled with `SuggestEmojiTag(0)` from the SDK:
  1–3 distinct emoji from a curated, widely-rendered set (single-codepoint Emoji_Presentation
  characters; no flags, skin tones or ZWJ sequences). A shuffle button re-rolls the
  suggestion. The suggestion is only a draft: nothing is saved until the user taps Save.

- Decision (user, 2026-09-03): keep the AndroidX `emoji2-emojipicker` as the Android keyboard
  (search, recents, skin tones). It pulls Guava in through `kotlinx-coroutines-guava`, which
  promotes `listenablefuture` to Guava's empty placeholder and breaks `ListenableFuture`
  resolution for the WorkManager worker at compile time, so `com.google.guava:guava` is on the
  compile classpath explicitly. Guava is unrelated to the leaderboard: sorting, ranking and
  paging live only in the SDK's `PointsLeaderboardViewController`. Other platforms pick their
  own emoji keyboard; the tag rules stay in the SDK (`ValidateEmojiTag`, `SuggestEmojiTag`).

## Amendment (user, 2026-09-04): every row listed
> The points leaderboard should show all rows, but only reveal the network name of those that
> have opted in to reveal network name. The UI should be infinitely scrollable so it is just one
> long list loaded in chunks.

- Server: `POST /stats/points-leaderboard` no longer filters on `points_leaderboard_public`;
  `anonymous` / `network_name` follow that flag per row, `emoji_tag` is on every row that set
  one, `total_ranked` equals the number of listed rows across the pages.
- SDK/apps: no shape change; the view controller already appends 50-row pages into one list
  and renders "Anonymous" + emoji for anonymous rows. The header switch's copy becomes "show my
  network name" (store keys `show_on_points_leaderboard`, `points_leaderboard_private_hint`).
