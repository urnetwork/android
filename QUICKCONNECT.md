# Quick connect and Home Screen widgets (Android)

Status, 2026-09-02: designed and implemented on `main` (uncommitted), the
Android half of the program in `apple/QUICKCONNECT.md`; the design and layout
notes there are the reference, and this document records what is the same,
what Android changes, and why. The debug build passes; verification on the
API 35 emulator is recorded in section 8.

The Android counterparts of the four iOS surfaces:

| iOS surface | Android surface | since |
| --- | --- | --- |
| Control Center / Lock Screen / Action button toggle | **Quick Settings tile** (existing, polished: active, toggleable, connectivity category, lock-screen toggle without unlock, placement prompt) plus **launcher shortcuts** Connect / Disconnect | API 26 (tile since 24; placement prompt 33) |
| Dashboard widget (medium, large) | **Dashboard widget**, responsive: compact row, short (header + balance), tall (+ charts) | API 26 |
| Provider globe widget | **Provider globe widget**, responsive: globe, globe + list, globe over list | API 26 |
| Contracts widget | **Contracts widget**, flowing grid of per-peer stacks | API 26 |

All of it is built with Jetpack Glance 1.2 (`androidx.glance:glance-appwidget`),
Google's Compose-based widget toolkit, with `SizeMode.Responsive` so the
launcher picks the largest layout that fits as the user resizes.

## 1. What the research settled

### 1.1 Quick Settings tiles

- A tile's `onClick` is delivered on the lock screen with no unlock gating;
  only activity launches go through the keyguard. Google's own Internet and
  Bluetooth tiles toggle while locked and only dismiss the keyguard to open
  Settings; every open-source VPN app wraps its toggle in `unlockAndRun` as
  a design choice, not a platform requirement. With the product decision
  from iOS (no authentication on the lock screen, like the system's VPN
  control), the tile toggles directly. [T1][T2]
- `META_DATA_ACTIVE_TILE` makes the tile *active*: the shade no longer binds
  it on every open, and the app pushes state with
  `TileService.requestListeningState`. `META_DATA_TOGGLEABLE_TILE` exposes it
  to accessibility as a switch; `META_DATA_TILE_CATEGORY` (36.1 SDK) files it
  under Connectivity on the edit page. Android 16 QPR1 tiles snap to 1x1,
  icon only, so the icon must carry meaning alone. [T1][T3]
- `StatusBarManager.requestAddTileService` (API 33) shows a system dialog to
  add the tile; Google's guidance is to call it in context (after the user
  first uses the feature) and WireGuard exposes it as a Settings row hidden
  once the tile was added (tracked from `onTileAdded`/`onTileRemoved`). [T1][T4]
- `VpnService.prepare()` still returns an intent when the system consent
  dialog is needed; only an activity can present it, so a tile connect on a
  fresh install hands off to the app, as before. [T5]
- `startActivityAndCollapse(Intent)` throws on API 34+; the PendingIntent
  overload is required. [T2]

### 1.2 Other native surfaces

- **Launcher shortcuts** (long-press the icon): static `shortcuts.xml`
  targeting an activity; up to four shown. v2rayNG and NekoBox ship
  Start/Stop shortcuts through translucent no-UI trampoline activities;
  WireGuard, Tailscale, Mullvad and Proton ship none. There is no Assistant
  built-in intent for VPN, so `capability` bindings are moot; Android 16's
  AppFunctions are the forward path for agents. [S1][S2]
- **Device Controls** (`ControlsProviderService`): built for smart-home
  devices, buried behind the lock-screen "Home controls" shortcut on modern
  Android, OEM-dependent; no VPN app uses it. Skipped. [D1]
- **Android 16 Live Updates** (promoted ongoing notifications): Google's
  guidance is "ongoing, user-initiated, time-sensitive… with a distinct start
  and end", explicitly not "quick access to app features" or ambient
  information; a months-long VPN session does not qualify (the same reason
  Apple discourages Live Activities for it). The existing foreground VPN
  notification with its Disconnect action stays the always-available
  control. [L1]
- The system VPN settings' Disconnect also switches always-on off; nothing
  restarts an app VPN the app itself stopped. [V1]

### 1.3 Widgets

- Glance 1.2.0 is stable (Aug 2026) and Google's docs are Compose-first;
  Android 17 moves to "a single, Compose-based development model for all
  widgets". Glance renders to RemoteViews: Box/Column/Row/Text/Image/Button,
  `LazyColumn`/`LazyVerticalGrid`, no Canvas, no custom fonts — anything
  custom-drawn is a bitmap. [W1][W2]
- `SizeMode.Responsive(setOf(DpSize…))` composes once per size and lets the
  launcher pick the best fit; sizes are dp ranges, not cells (a 4x2 is
  245–624 × 115–276 dp on handsets; cells and margins vary by launcher).
  Declare `targetCellWidth/Height` for Android 12+ and `minWidth/Height` for
  older launchers, `minResizeWidth/Height` for the smallest layout,
  `resizeMode`, `previewLayout` (12–14) and `description`; Android 15+ can
  also take a generated preview. [W3][W4]
- Bitmaps in one widget update are capped at 1.5 × the screen's pixel bytes;
  from Android 17 (targetSdk 37) the cap counts Icons too and exceeding it is
  fatal. Charts and the globe are rasterized only for their own regions, at
  the widget's dp size × density. [W5]
- `updatePeriodMillis` is floored at 30 minutes; the right way for a widget
  whose data changes while the app process is alive is to push updates from
  that process (`GlanceAppWidget.updateAll`), and to not update every minute
  when it is not. Widgets are RemoteViews cached by the launcher, so they
  survive the process. [W6]
- Widget quality tiers: resize to 2x2/4x1/4x2, a consistent header, light
  and dark (the app is dark-only; so are the widgets), a call to action when
  not signed in, the system corner radius, accurate previews and
  descriptions. [W7]
- Prior art: Proton VPN Android ships Glance widgets (2x2 and 4x3,
  `SizeMode.Responsive` with nine sizes, state pushed from a Hilt singleton
  through `updateAll`, clicks via a broadcast receiver); Surfshark and NordVPN
  ship resizable connect widgets; nobody ships throughput or globe widgets.
  [P1][P2]

### 1.4 What this app already had

- Android is single-process: the app, the `VpnService`, the tile and the Go
  SDK device (`DeviceLocal`) share one process, so widgets' update code
  reads the SDK directly — there is no cross-process snapshot to keep and no
  shared "intent record" to reconcile, unlike iOS. The connect screen's
  saved location in the SDK local state is the one truth.
- The Quick Settings tile (`QuickConnectTileService.kt`, July 2026) already
  connected silently in-process and opened the app only when logged out or
  when consent was missing.
- The Kotlin globe geometry, the world topology decoder and the topology
  asset (`ui/connect/providerlocations/GlobeGeometry.kt`,
  `WorldTopology.kt`, `assets/world-110m.json`) already existed as Android
  parity ports, and so did the SDK view controllers that the provider
  details and contract details screens use in-process.

## 2. Quick connect

`QuickConnect.kt` is the one connect/disconnect path behind every surface:
a short-lived `ConnectViewController` connecting to the saved location (else
best available) or disconnecting, as the connect screen does, returning
`APPLIED`, `NEEDS_CONSENT` (first-ever connect: the app must show the system
dialog) or `NEEDS_APP` (logged out / not initialized). Used by:

- **The tile** (`QuickConnectTileService.kt`): now active + toggleable +
  Connectivity category; the app calls `requestUpdate` on every connect
  change (`MainApplication`'s connect listener). Label is the app name, the
  subtitle "Connected" / "Disconnected" / "Not signed in", mirrored into the
  accessibility state description. The icon is the solid connector mark in
  both states — the system tints the active tile, which carries the state,
  matching the iOS decision (solid white mark, colored when on). Logged out
  or consent missing → open the app. `onTileAdded`/`onTileRemoved` remember
  whether the tile is placed.
- **Launcher shortcuts** (`res/xml/shortcuts.xml` on the launcher activity):
  Connect and Disconnect, through `QuickConnectActivity`, a
  `Theme.NoDisplay` trampoline that applies the request and finishes,
  opening the app only for `NEEDS_APP` / `NEEDS_CONSENT`. The Android
  counterpart of the iOS Siri / Shortcuts intents.
- **The widgets' button** — the same trampoline with `QUICK_TOGGLE`
  (section 4).
- **The notification's Disconnect action** — unchanged.
- **Settings**: "Add quick connect tile" (API 33+, hidden once the tile is
  placed) calls the placement prompt; "Add Home Screen widgets" pins the
  dashboard widget through the launcher's confirmation.

## 3. Data: the snapshot writer

`widgets/WidgetSnapshot.kt` holds the same snapshot model as iOS
(`WidgetTunnelSnapshot`, `WidgetBalanceSnapshot`, providers with coordinates
and colors, one-minute throughput buckets with the resumable accumulator,
per-peer contract stacks), serialized with kotlinx.serialization to
`filesDir/widgets/*.json`.

`widgets/WidgetSnapshotWriter.kt` runs in the app process for the life of
the process (`MainApplication.initializeApplicationState`) on its own
thread, following the device through `DeviceManager.addDeviceChangeListener`:

| datum | source | cadence |
| --- | --- | --- |
| on/off, signed in | live `device.connectEnabled`, `device != null` at render time | every update |
| connect / disconnect | `addConnectChangeListener` | immediate write + re-render of every widget |
| location | `addConnectLocationChangeListener` + `Sdk.getColorHex` | on change |
| providers | `ProviderLocationsViewController.providerLocations` (the app's display order) when a globe widget is placed, else the device's list | on change, globe re-render with a 60 s floor |
| throughput buckets | `addPacketStatsChangeListener` / `addProviderPacketStatsChangeListener` folded per event | file every 60 s, routine re-render 5 min |
| contracts | `ContractDetailsViewController` (client) rows, `setAtTop(true)`, when a contracts widget is placed | events coalesced to 2 s; re-render on membership change with a 60 s floor |
| balance | `api.subscriptionBalance` every 30 min while a device exists, plus every fetch the app makes (`SubscriptionBalanceViewModel`) | on change |

The two SDK view controllers are presentation work, so they are opened only
while a widget that needs them is placed (`GlanceAppWidgetManager.getGlanceIds`)
and closed when the last one is removed (`GlanceAppWidgetReceiver.onEnabled/
onDisabled` → `widgetsChanged`). Android has no reload budget, but the
launcher re-inflates on every update, so the cadences stay gentle. Logout
clears both files and re-renders the empty states.

## 4. The widgets

Common: brand black background, the launcher's corner radius
(`system_app_widget_background_radius`), 14 dp edge padding, system font
(Glance cannot bundle the app's fonts), monospace for client ids. Every
widget re-reads the snapshot and the live device state on each update
(`WidgetEntry.load`). A tap anywhere on a widget opens the app on its
screen: the dashboard on the connect tab, the globe on the provider
details, the contracts widget on the client contract details. The tap goes
through the same no-UI trampoline (`QuickConnectActivity`, action OPEN with
a route), which records the route on `MainApplication.widgetRoute` and opens
the app through its normal entry; `MainNavHost` observes the route, switches
to the connect tab and pushes the screen — whether the app was cold-started
for the tap or was already running. With no account the tap opens sign-in.
The quick connect button's own click wins inside the dashboard.

**Dashboard** (`DashboardWidget.kt`; default 4x2, resizable 110×48 →
624×422 dp): three responsive layouts — *compact* (one row: connector mark,
location, quick connect button), *short* (header + balance bar, the iOS
medium layout), *tall* (adds the client and provider charts and the
"Updated N min ago" footer). The mark next to the location is white when
off and the app's connected green when up; the quick connect button is the
mark in a capsule, gray when off and the accent pink when on, and taps the
trampoline. The balance bar is the app's `UsageBar` (used / pending /
available, 1.5 % minimum segment). Charts are bitmaps from
`render/ThroughputChartRenderer.kt`: the app's `TransferChart` smoothing and
mirrored layout over 60 one-minute buckets; client = the remote route,
provider = the provider counters' local + block routes; "Not providing"
replaces the peak label when providing is off.

**Provider globe** (`ProviderGlobeWidget.kt`; default 2x2): a bitmap from
`render/GlobeBitmapRenderer.kt` — the app's `ProviderGlobe` drawing
(`GlobeGeometry`, `WorldTopology`, graticule, country-colored dots) turned to
face the providers' centroid, dots with a legible floor radius. Small: globe
+ count badge; wide: globe left, list right (four rows); tall: globe over a
six-row list. Rows: country dot, "City, Country", and the app's compact
duration ("3h 24m", the same string resources), in the provider details
view's order.

**Contracts** (`ContractsWidget.kt`; default 4x2): one card per peer with
its send stack (green, arrow right) and receive stack (pink, arrow left) as
rows of circles rendered by `render/ContractStackRenderer.kt` (area from the
contract's total against the stack's largest, inner disc the used fraction,
brighter ring when active, double ring for streams, dashed placeholder for
an empty stack). Glance has no measuring flow layout, so rows are packed
from each card's estimated width (id and rate measured with `Paint` at the
widget's density, stacks from circle counts) against `LocalSize`; cards
flow left to right and wrap until the height is used up, most relevant
peers first (the SDK's row order).

## 5. Implementation map

| path | role |
| --- | --- |
| `QuickConnect.kt` | the shared connect/disconnect path and its results |
| `QuickConnectActivity.kt`, `res/xml/shortcuts.xml` | trampoline + launcher shortcuts |
| `QuickConnectTileService.kt` | the polished tile |
| `widgets/WidgetSnapshot.kt` | snapshot model, accumulator, file store |
| `widgets/WidgetSnapshotWriter.kt` | the in-process writer and reload throttle |
| `widgets/GlanceWidgetRefresh.kt` | Glance reload + receivers + pin request |
| `widgets/WidgetEntry.kt`, `WidgetTheme.kt` | per-update state, palette |
| `widgets/DashboardWidget.kt`, `ProviderGlobeWidget.kt`, `ContractsWidget.kt` | the widgets |
| `widgets/render/*.kt` | bitmap renderers (globe, chart, stacks) |
| `res/xml/widget_*_info.xml`, `res/layout/widget_preview_*.xml` | provider metadata, picker previews |
| `MainApplication.kt`, `SubscriptionBalanceViewModel.kt`, `ui/settings/SettingsScreen.kt` | hooks: writer lifecycle, balance publish, settings rows |
| `app/build.gradle` | Glance 1.2.0 dependencies |
| `localizations/keys/widget_*.yaml` + 7 Android keys | strings, regenerated with `npm run gen:android` |

## 6. Divergences from iOS, and why

- **No shared intent record.** iOS needed `TunnelIntentStore` because the
  app and the tunnel extension keep separate SDK states in separate
  processes. Android's one process makes the connect screen's saved
  location the single truth that every surface reads and writes.
- **Providers and contracts come from the SDK view controllers**, not a
  reimplementation: they are in-process here, so the widget order and
  grouping are the app's by construction (the iOS extension had to port the
  contract grouping and export the provider ordering from the SDK).
- **Cadences are a little faster** (60 s floors, 5-minute routine) because
  Android has no daily reload budget; the launcher still re-inflates on
  every update, so they are not faster than that.
- **The quick connect tap is an activity start** (the no-UI trampoline)
  rather than an intent in the widget process, because Android 12+ forbids
  starting the consent or sign-in activity from a background callback; the
  trampoline can, and it is the same code the shortcuts use.
- **Fonts**: the app's fonts are not available to widgets; system sans and
  monospace are used.

## 7. Setup

Nothing in the developer console; widgets and tiles need no capabilities.
`./gradlew :app:assembleGithubDebug` builds; `:app:installGithubDebug` (or
`adb install`) installs. Strings live in the localizations store: the
widget keys are tagged `android` and regenerated into `res/values*/strings.xml`
(the Android generator's output was clean: only additions).

## 8. Verification

Done here, on the API 35 emulator (Pixel-class, 1080×2400 @ 420 dpi,
Pixel Launcher) with an instant account and a real tunnel:
- the tile in the shade with the connector icon, "Connected" while the
  tunnel was up (the active tile is now rendered once per process start and
  on add, since it showed the manifest defaults until first bound);
- the launcher long-press menu with "Connect to URnetwork" and "Disconnect"
  (the launcher shows the long label where it fits, else the short one);
- the three widgets pinned through the launcher's "Add to home screen"
  confirmation, rendering their disconnected states, the sample entry, and
  then live data: the dashboard with "Best available provider", the
  provider count, the balance bar and the client chart; the 2x2 globe with
  the five connected US providers plotted and listed in the app's order with
  durations; the contracts grid with five peer cards flowing three per row;
- the widget's quick connect button: it started the no-UI trampoline, which
  connected and, this being the first-ever connect, opened the app for the
  system "Connection request" dialog; accepting it brought the tunnel up and
  every widget and the tile re-rendered; the Disconnect shortcut then
  brought it down again.
Two rendering findings were fixed on the way: in Glance's responsive mode a
composition only sees its size bucket, which stretched the chart bitmaps
and left the contracts grid half empty (all three widgets now use exact
sizing); and Glance rows only weight equally, so the balance bar is a
bitmap.

Debug builds carry adb hooks for this (`src/debug/.../DebugWidgetReceiver`):
a broadcast pins a widget, another switches the widgets to the sample entry.

To exercise on a phone: connect from the tile on the lock screen; long-press
the icon and use Disconnect; place the dashboard at 4x1, 4x2 and 4x3 and
resize between them; tap its button with the app closed (it toggles without
opening the app; the first-ever connect opens it for consent); place the
globe at 2x2 and 4x2 and watch providers join; place the contracts widget
while traffic flows; sign out and check the empty states; on Android 16 QPR1+
check the lock-screen widgets and the 1x1 tile icon.

## 9. Follow-ups

- Generated widget previews on Android 15+ (`providePreview` +
  `setWidgetPreviews`) so the picker shows real content in dynamic color.
- Offer the tile placement prompt once after the first successful connect
  (today it is a Settings row).
- A "Connecting…" subtitle and optimistic state on the tile while the
  service comes up (Proton's pattern), if the transition proves visible.
- Wear OS tiles; a Material You (dynamic color) variant of the widgets like
  Proton's; Android 17 RemoteCompose animations when Glance 1.3 is stable.

## Sources

Tiles: [T1] developer.android.com/develop/ui/views/quicksettings-tiles ·
[T2] developer.android.com/reference/android/service/quicksettings/TileService,
AOSP SystemUI `CustomTile.java`, `InternetTile.java`, `BluetoothTile.java` ·
[T3] androidpolice.com/android-16-qpr-1-beta-resize-quick-settings-tiles ·
[T4] developer.android.com/reference/android/app/StatusBarManager;
github.com/WireGuard/wireguard-android (`QuickTileService.kt`, `QuickTilePreference.kt`) ·
[T5] developer.android.com/reference/android/net/VpnService.
Shortcuts: [S1] developer.android.com/develop/ui/views/launch/shortcuts ·
[S2] github.com/2dust/v2rayNG (`res/xml/shortcuts.xml`), github.com/MatsuriDayo/NekoBoxForAndroid (`QuickToggleShortcut.kt`).
Device Controls: [D1] developer.android.com/develop/ui/views/device-control.
Live Updates: [L1] developer.android.com/develop/ui/views/notifications/live-update.
VPN: [V1] AOSP `packages/apps/Settings/.../vpn2/AppDialogFragment.java`, `services/core/.../connectivity/Vpn.java`.
Widgets: [W1] developer.android.com/jetpack/androidx/releases/glance ·
[W2] developer.android.com/develop/ui/compose/glance/build-ui ·
[W3] developer.android.com/develop/ui/views/appwidgets/layouts, …/reference/android/appwidget/AppWidgetProviderInfo ·
[W4] developer.android.com/design/ui/mobile/guides/widgets/sizing, …/develop/ui/views/appwidgets/previews ·
[W5] developer.android.com/reference/android/appwidget/AppWidgetManager (bitmap memory), …/about/versions/17/behavior-changes-17 ·
[W6] developer.android.com/develop/ui/compose/glance/glance-app-widget, …/develop/ui/views/appwidgets/advanced ·
[W7] developer.android.com/docs/quality-guidelines/widget-quality.
Prior art: [P1] github.com/ProtonVPN/android-app (`widget/ui/ProtonVpnGlanceWidget.kt`, `res/xml/widget_info*.xml`) ·
[P2] support.surfshark.com (Android widget), support.nordvpn.com (widgets).
