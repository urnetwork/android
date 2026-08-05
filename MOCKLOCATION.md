# Mock Location on Android — Research Report & Implementation Blueprint

Companion reference for `~/urnetwork/sdk/PROVIDERLOCATIONS.md` (provider locations +
"sync device location with oldest provider"). Researched 2026-08-04 against AOSP `main`,
SDK 36 platform sources, and current Google Play policy pages.

**Scope:** feasibility, exact APIs, lifecycle, and UX for an opt-in "sync device location
with exit provider" feature in the URnetwork Android VPN app (minSdk 26, targetSdk 36,
foreground VpnService).

**Verdict up front:** the feature is fully implementable with public SDK APIs, requires
**no runtime location permission and no location foreground-service type**, and has clear
commercial precedent (Surfshark). The two things that will bite are (a) **test providers
are never auto-removed** — not on process death, not on force-stop, not on uninstall —
and (b) if the user deselects the app in Developer options while mocking is active, **the
app permanently loses the ability to clean up until re-selection or reboot**. Both are
verified in AOSP source below and drive most of the blueprint.

---

## 1. Eligibility: appearing in "Select mock location app"

### 1.1 The manifest declaration

```xml
<uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
```

The permission is declared in AOSP's framework manifest as:

```xml
<!-- @SystemApi Allows an application to create mock location providers for testing.
     <p>Protection level: signature
     @hide -->
<permission android:name="android.permission.ACCESS_MOCK_LOCATION"
    android:protectionLevel="signature" />
```
— [core/res/AndroidManifest.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)

Because it is `signature`/`@hide`, a normal app **never actually gets it granted** and it
does not appear in any permission UI. Its only function for a third-party app is as a
**manifest marker that makes the app show up in the Settings picker**. Declaring an
ungrantable signature permission is harmless (no install failure, no Play declaration
form). Actual authority comes from the **app op**, not the permission.

### 1.2 What the Settings picker actually filters on

`MockLocationAppPreferenceController` launches the app picker with the permission name as
the filter, and on selection flips the app op:

```java
args.putString(DevelopmentAppPicker.EXTRA_REQUESTING_PERMISSION,
        Manifest.permission.ACCESS_MOCK_LOCATION);
...
mAppsOpsManager.setMode(AppOpsManager.OP_MOCK_LOCATION, ai.uid,
        mockLocationAppName, AppOpsManager.MODE_ALLOWED);
```
— [MockLocationAppPreferenceController.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/MockLocationAppPreferenceController.java)

The picker's candidate filter ([DevelopmentAppPicker.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/DevelopmentAppPicker.java),
legacy [AppPicker.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/AppPicker.java)) is:

1. `ai.uid != Process.SYSTEM_UID`
2. **debuggable filter applies only if `EXTRA_DEBUGGABLE` was passed** — the mock-location
   controller does **not** pass it (only the "Select debug app" picker does)
3. the package's `requestedPermissions` contains `ACCESS_MOCK_LOCATION`

**Conclusion: the app need NOT be debuggable — and must not be.** A normal, Play-signed,
non-debuggable release build appears in the list purely by declaring the permission. This
is exactly how Surfshark ships it.

### 1.3 The app-op is the real gate

- `OPSTR_MOCK_LOCATION = "android:mock_location"`, `OP_MOCK_LOCATION`, **default mode
  `MODE_ERRORED`** (`AppOpsManager` source:
  `new AppOpInfo.Builder(OP_MOCK_LOCATION, OPSTR_MOCK_LOCATION, "MOCK_LOCATION").setDefaultMode(AppOpsManager.MODE_ERRORED)`).
- Selecting an app sets `MODE_ALLOWED` for it and `MODE_ERRORED` for the previous one
  (`removeAllMockLocations()` → `removeMockLocationForApp()`).
- Turning **Developer options off** calls `onDeveloperOptionsDisabled()` →
  `removeAllMockLocations()` — i.e. it revokes the op but **does not remove
  already-registered test providers** (see §6.4).
- Server side, every test-provider call runs
  `mInjector.getAppOpsHelper().noteOp(AppOpsManager.OP_MOCK_LOCATION, identity)`; the
  throwing `noteOp` variant raises `SecurityException` on `MODE_ERRORED`
  ([AppOpsManager.noteOp](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/app/AppOpsManager.java),
  [LocationManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/LocationManagerService.java)).

### 1.4 Google Play policy status

Full rendered **Device and Network Abuse**, **Deceptive Behavior**, **Permissions & APIs
that Access Sensitive Information**, and **User Data** policy pages were fetched and
text-searched:

| term | occurrences across all four policy pages |
|---|---|
| `mock` | **0** |
| `spoof` | **0** |
| `ACCESS_MOCK_LOCATION` | **0** |

— [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379),
[Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/9888077),
[Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170),
[User Data](https://support.google.com/googleplay/android-developer/answer/9888076)

So: **no Play policy prohibits or specially regulates mock-location apps**, and
`ACCESS_MOCK_LOCATION` is not on any sensitive-permission declaration form. What *does*
apply:

- **VPN Service Policy** (in the sensitive-permissions policy): "Google Play permits its
  use only for apps with core VPN functionality… All apps using VpnService must clearly
  document this in their Google Play listing and encrypt all data from the device to the
  VPN tunnel endpoint." Already satisfied.
- **Location Permissions Policy**: applies to `ACCESS_COARSE/FINE/BACKGROUND_LOCATION`.
  **The mock-only design requests none of these**, so it is out of scope — a strong
  argument for *not* building a "mirror the real location" pass-through (§8, §10.4).
- Google's own developer docs bless the feature: "**Select mock location app**: Use this
  option to fake the GPS location of the device to test whether your app behaves the same
  in other locations. To use this option, download and install a GPS mock location app."
  — [Configure on-device developer options](https://developer.android.com/studio/debug/dev-options)

### 1.5 Precedent

- **Surfshark "Override GPS location"** ships in the Play-distributed Surfshark Android
  app: "it will automatically match your GPS location with your chosen server's
  location… also called fake GPS, mock GPS, or GPS spoofing" —
  [Surfshark: What is the GPS override feature](https://support.surfshark.com/hc/en-us/articles/360011723459-What-is-the-GPS-override-feature-and-how-to-use-it),
  [TechRadar coverage](https://www.techradar.com/news/surfshark-adds-gps-spoofing-feature-to-its-vpn).
- Dozens of dedicated spoofers are live on Play (e.g.
  [Mock Locations (fake GPS path)](https://play.google.com/store/apps/details?id=ru.gavrikov.mocklocations),
  [Fake GPS location](https://play.google.com/store/apps/details?id=com.lexa.fakegps)).
- Open-source references used throughout this report:
  [warren-bank/Android-Mock-Location (Mock-my-GPS)](https://github.com/warren-bank/Android-Mock-Location)
  and [mcastillof/FakeTraveler](https://github.com/mcastillof/FakeTraveler).

---

## 2. Exact API sequence per API level

All signatures verified against Android SDK 36 platform sources
(`android/location/LocationManager.java`), cross-checked with the
[API 31 diff report](https://developer.android.com/sdk/api_diff/31/changes/android.location.LocationManager).

### 2.1 `addTestProvider`

**API 31+ (preferred):**
```java
void addTestProvider(@NonNull String provider, @NonNull ProviderProperties properties)
void addTestProvider(@NonNull String provider, @NonNull ProviderProperties properties,
                     @NonNull Set<String> extraAttributionTags)
```
> `@throws IllegalArgumentException if provider is null` / `if properties is null`
> `@throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION mock
> location app op} is not set to MODE_ALLOWED for your app.`
> "Creates a test location provider and adds it to the set of active providers. **This
> provider will replace any provider with the same name that exists prior to this call.**"

**API 26–30 (legacy 10-arg overload, still present and not formally deprecated):**
```java
void addTestProvider(String provider, boolean requiresNetwork, boolean requiresSatellite,
        boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude,
        boolean supportsSpeed, boolean supportsBearing, int powerUsage, int accuracy)
```
On S+ this simply builds a `ProviderProperties` internally. `powerUsage`/`accuracy` take
`Criteria.POWER_LOW/MEDIUM/HIGH` (1/2/3) and `Criteria.ACCURACY_FINE/COARSE` (1/2) —
values are numerically identical to `ProviderProperties.POWER_USAGE_*` / `ACCURACY_*`
(verified in both source files).

**Critical pre-S behavioral difference:** on API ≤ 30, `addTestProvider` on a name that is
*already* a test provider **throws `IllegalArgumentException("Provider \"x\" already
exists")`**, and mocking `"passive"` throws
`IllegalArgumentException("Cannot mock the passive location provider")` —
[Android 10 LocationManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/LocationManagerService.java).
On S+ the call is idempotent (replace). **Always `removeTestProvider` first, in a
try/catch, on all versions.**

### 2.2 The rest of the surface (unchanged since API 18)

```java
void setTestProviderEnabled(@NonNull String provider, boolean enabled)
    // throws SecurityException (app op), IllegalArgumentException if provider is null or not a test provider
void setTestProviderLocation(@NonNull String provider, @NonNull Location location)
    // throws SecurityException (app op)
    //        IllegalArgumentException if the provider is null or not a test provider
    //        IllegalArgumentException if the location is null or incomplete
void removeTestProvider(@NonNull String provider)
    // "Removes the test location provider with the given name or does nothing if no such
    //  test location provider exists."  (S+; pre-S it throws IllegalArgumentException if unknown)
@Deprecated void clearTestProviderLocation(String)   // no-op, always was
@Deprecated void clearTestProviderEnabled(String)    // == setTestProviderEnabled(p, false)
```

### 2.3 `setTestProviderEnabled(true)` is mandatory

A freshly added test provider starts **disallowed**: `MockLocationProvider` →
`AbstractLocationProvider(…)` initializes from
`State.EMPTY_STATE = new State(false /*allowed*/, …)`
([AbstractLocationProvider.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/AbstractLocationProvider.java)).
Without the explicit enable, `isProviderEnabled("gps")` returns **false** for every app on
the device and no updates are delivered — i.e. you'd break location instead of faking it.

### 2.4 Required `Location` fields

Client side (`LocationManager.setTestProviderLocation`):
```java
Preconditions.checkArgument(location.isComplete(),
        "incomplete location object, missing timestamp or accuracy?");
```
where (`android/location/Location.java`):
```java
public boolean isComplete() {
    return mProvider != null && hasAccuracy() && mTimeMs != 0 && mElapsedRealtimeNs != 0;
}
```
(The `BLOCK_INCOMPLETE_LOCATIONS` compat change is `@EnabledAfter(JELLY_BEAN)`, so it
applies to every app we'll ever ship.) The system server re-checks
`location.isComplete()` and additionally validates via `LocationResult.validate()`:

- lat ∈ [-90,90], lon ∈ [-180,180], neither NaN
- `hasAccuracy()` **and** `0 ≤ accuracy ≤ 1_000_000 m`
- `getTime() ≥ 0`
- `elapsedRealtimeNanos` **must not be in the future**
  (`> SystemClock.elapsedRealtimeNanos()` → reject) and must be monotonically
  non-decreasing within a batch
- **mock locations are exempt** from the "must not be at 0,0" and "must have valid
  provider" checks (`if (!location.isMock())` guard)
- out-of-range `speed` is silently stripped rather than rejected

Violations surface as `IllegalArgumentException` (server wraps `BadLocationException` in
`MockLocationProvider.setProviderLocation`).

**So the mandatory set is: `provider` (must equal the test-provider name — a mismatch is
logged as a security-event, b/33091107), `latitude`, `longitude`, `accuracy`, `time`,
`elapsedRealtimeNanos`.**

### 2.5 Recommended field values for a plausible static city fix

```java
Location l = new Location(providerName);              // provider name MUST match
l.setLatitude(lat);  l.setLongitude(lon);
l.setAccuracy(8f);                                    // plausible GNSS fix; 3–25 m
l.setAltitude(cityElevationMeters);                   // e.g. 10–100; some consumers require hasAltitude
l.setSpeed(0f);
l.setBearing(0f);
l.setTime(System.currentTimeMillis());
l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());   // fresh EVERY post
// API 26+ (our minSdk) — cheap and makes the fix look complete:
l.setVerticalAccuracyMeters(12f);
l.setSpeedAccuracyMetersPerSecond(0.1f);
l.setBearingAccuracyDegrees(1f);
// API 29+: l.setElapsedRealtimeUncertaintyNanos(1_000_000d);
```
For comparison, FakeTraveler uses accuracy `3f`, altitude `3f`, speed `0.01f`, bearing
`1f` plus the O+ accuracy fields
([MockedLocationProvider.java](https://github.com/mcastillof/FakeTraveler/blob/master/app/src/main/java/cl/coders/faketraveler/MockedLocationProvider.java)).
Avoid huge accuracy values: Maps-style consumers draw an accuracy circle, and several
"is this fix usable" heuristics discard low-accuracy fixes.

Optional: ±2–5 m of jitter per post makes the track look organic; bit-identical repeated
coordinates are a trivial spoof heuristic. Not required for correctness.

---

## 3. Provider coverage

### 3.1 Which platform providers to mock

Constants (`LocationManager`): `GPS_PROVIDER = "gps"`, `NETWORK_PROVIDER = "network"`,
`PASSIVE_PROVIDER = "passive"`, **`FUSED_PROVIDER = "fused"` — added to the public API in
API 31** (confirmed in the
[API 31 diff](https://developer.android.com/sdk/api_diff/31/changes/android.location.LocationManager);
`addTestProvider(String, ProviderProperties)` landed in the same release).

**Mock `gps` + `network` on all versions, plus `fused` on API 31+.** That is exactly what
both production references do — Mock-my-GPS lists `GPS_PROVIDER`, `NETWORK_PROVIDER`,
`FUSED_PROVIDER`; FakeTraveler adds `FUSED_PROVIDER` only under
`Build.VERSION.SDK_INT >= S`.

Why `fused` matters on S+ but not before:
- **Pre-31**, the fused provider existed but was deliberately hidden:
  `getAllProviders()`/`getProviders()` skip it and `isProviderEnabled("fused")`
  hard-returns `false` — "*Fused provider is accessed indirectly via criteria rather than
  the provider-based APIs, so we discourage its use*" (Android 10 LMS). Mocking it there
  is possible but nearly useless.
- **On 31+** it's the platform default: `LocationManager.getLastLocation()` is literally
  `getLastKnownLocation(FUSED_PROVIDER)`, the deprecated `Criteria` overloads all say
  "use `FUSED_PROVIDER`", and **platform geofencing requests `FUSED_PROVIDER`**
  (`GeofenceManager.java`:
  `getLocationManager().requestLocationUpdates(FUSED_PROVIDER, …)`).

**Do NOT mock `passive`.** Pre-S it throws `IllegalArgumentException` outright; on S+
nothing blocks it, but `PassiveLocationProviderManager` is a special manager and replacing
it would break passive delivery for the whole device. It's unnecessary anyway: mock fixes
are automatically forwarded to passive listeners — `LocationProviderManager
.onReportLocation()` ends with `mPassiveManager.updateLocation(processed)`.

### 3.2 Google Play services `FusedLocationProviderClient`

Verbatim from the official reference
([FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)):

> **`setMockMode(boolean mockMode)`** — "Sets whether or not the Fused Location Provider
> is in mock mode. Entering mock mode clears the FLP's cached locations, and ensures that
> the FLP will only report locations set through `setMockLocation(Location)`. Exiting mock
> mode will clear any mock locations set from the FLP's cache as well. **Mock mode affects
> all location clients using the FLP, including location clients in other processes and
> derivative APIs such as geofencing** and so forth. Because this affects all FLP usage,
> clients should always ensure they properly set the mock mode to false when finished.
> **Successfully using this API on devices running Android M+ requires the client to
> request the `android.permission.ACCESS_MOCK_LOCATION` permission and to be selected as
> the mock location app within the device developer settings.**"

> **`setMockLocation(Location)`** — "Delivers the given location to the FLP as if it was
> coming from an underlying location source. Normal FLP logic around receiving and
> delivering location will generally apply. For this reason **the timestamps of the
> location should be set appropriately, as the FLP may expect monotonically increasing
> timestamps.** When this location is reported to FLP clients it will be marked as a mock
> location… This API can only be successfully used while the FLP is in mock mode."

Note the security requirement is **identical** to the platform path — same permission,
same Developer-options selection. No extra user step.

**Does FLP honor `LocationManager` test providers without `setMockMode`?** There is **no
Google statement either way** (the entire FLP reference page contains no sentence
connecting LM test providers to FLP). The empirical answer, from the most-credible
open-source implementation, is *yes, usually*: Mock-my-GPS's README states the AOSP-only
build "supplies mock location data to… `LocationManager.GPS_PROVIDER`,
`NETWORK_PROVIDER`, `FUSED_PROVIDER`", and its separate GMS build additionally supplies
"`FusedLocationProviderClient` in the *Google Location Services* (GLS)" with the caveat
that it "requires that *Google Play Services* is installed, enabled, and sufficiently
recent" — i.e. the author found it necessary to ship a *separate variant* for guaranteed
GLS coverage. Mechanically this matches the architecture: GMS's FLP fuses the platform
GNSS engine (which the `"gps"` test provider fully replaces, §7.1) with its own
network-location estimate, so mocking `"gps"` usually moves FLP, while mocking `"fused"`
replaces the AOSP fused *proxy* that GMS registers via `ACTION_FUSED_PROVIDER`
(`LocationManagerService` binds `network` and `fused` as `ProxyLocationProvider`s to a
provider package = GMS on Play devices). A known imperfection: fused output is not always
tagged as mock — "*If you switch on a fake location provider, every now and then a fake
location will arrive that is not labeled as a mock… the result of some erroneous fusion
logic inside Google's API*"
([KlaasNotFound, "Location on Android: Stop Mocking Me!"](https://klaasnotfound.com/2016/05/27/location-on-android-stop-mocking-me/)).

**Recommendation:** ship the platform path (`gps`/`network`/`fused`) as the mandatory
core; treat `FusedLocationProviderClient.setMockMode(true)` + `setMockLocation()` as an
**optional, reflection-free, gracefully-degrading enhancement** only on flavors that
already depend on `play-services-location`. Do **not** add a GMS dependency solely for
this — it costs binary size, fails on non-GMS devices (GrapheneOS/microG, Huawei), and
the platform path already covers the overwhelming majority of consumers. If added,
`setMockMode(false)` on teardown is mandatory (it's device-global and affects other
processes).

### 3.3 Geocoder and geofencing

- **`Geocoder` is unaffected.** It's a separate service (`ProxyGeocodeProvider`, bound
  independently in `LocationManagerService`) that takes explicit coordinates as input.
  This is *good* here: an app that reverse-geocodes the mock lat/lon gets the genuine
  city/country name of the exit provider's city.
- **Platform geofencing follows the mock** — `GeofenceManager` requests updates from
  `FUSED_PROVIDER`, so on API 31+ mocking `fused` moves platform geofences;
  `LocationManager.addProximityAlert` likewise.
- **GMS geofencing** follows the FLP, so it moves if the FLP moves (and per the doc above,
  `setMockMode` explicitly affects "derivative APIs such as geofencing").

---

## 4. Runtime detection and failure modes

### 4.1 Am I the selected mock app? (check before acting)

```java
AppOpsManager aom = ctx.getSystemService(AppOpsManager.class);
boolean allowed = aom.checkOpNoThrow(
        AppOpsManager.OPSTR_MOCK_LOCATION,   // "android:mock_location"
        Process.myUid(),
        ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
```

Semantics confirmed end-to-end:
- The op **is** the Developer-options selection: Settings writes `MODE_ALLOWED` for the
  chosen package and `MODE_ERRORED` for all others; default is `MODE_ERRORED` (§1.3).
- Self-checks need no permission: `AppOpsService.enforceGetAppOpsStatsPermissionIfNeeded`
  short-circuits with "*Apps can access their own data*" when `uid == callingUid`;
  `checkOperationImpl` itself performs no permission enforcement. (`GET_APP_OPS_STATS` is
  only needed to inspect *other* apps.)
- API-level note: `checkOpNoThrow(String, int, String)` has existed since API 19; on 23+
  it is the canonical way to read this. `unsafeCheckOpNoThrow` is the same call,
  deprecated in favor of `checkOpNoThrow`. Both return `MODE_ERRORED` rather than
  throwing.

**Watch for changes** (public API, no permission — "*You can watch op changes only for
your UID*"):
```java
aom.startWatchingMode(AppOpsManager.OPSTR_MOCK_LOCATION, ctx.getPackageName(), listener);
```
This fires when the user selects/deselects the app, letting the UI react immediately.
**Caveat:** Settings revokes the *old* app's op **before** granting the new one, so by
the time deselection is observed the ability to `removeTestProvider` is already lost
(§6.4). The listener is for UI truthfulness and for opportunistic cleanup when the op is
*restored*, not for a clean exit.

### 4.2 Failure modes to catch

| Call | Failure | Cause |
|---|---|---|
| `addTestProvider` | `SecurityException` | not the selected mock app (op ≠ ALLOWED) |
| `addTestProvider` | `IllegalArgumentException` | pre-S: provider already exists / is `"passive"`; any: null args |
| `setTestProviderEnabled` | `IllegalArgumentException` | provider is not (or no longer) a test provider |
| `setTestProviderLocation` | `IllegalArgumentException` | incomplete location; provider doesn't exist; bad lat/lon/accuracy; `elapsedRealtimeNanos` in the future |
| any | `SecurityException` mid-session | user deselected the app while active |
| all succeed, nothing happens | — | device **Location master switch is OFF** (see §6.6) |

Belt-and-braces: check the app op first (cheap, no exception, drives UI state), *and*
wrap every call in try/catch, because the op can flip between the check and the call.

### 4.3 Are Developer options even enabled?

```java
boolean devOptions = Settings.Global.getInt(ctx.getContentResolver(),
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
```
`DEVELOPMENT_SETTINGS_ENABLED = "development_settings_enabled"`, documented as "*Whether
user has enabled development settings*" and annotated `@Readable` — public settings are
always readable, so this is safe for targetSdk 30+ (the `@Readable` allowlist covers
"all the public settings" per the annotation's own doc).

Use this to fork the setup guide: **dev options off** → show the 7-taps-on-Build-number
instructions (cannot be automated; there is no intent for it) → **dev options on but not
selected** → deep-link to Developer options and describe the picker → **selected** →
show "Ready".

---

## 5. Settings deep links

**There is no public deep link to the "Select mock location app" picker.** Verified from
the Settings app manifest
([packages/apps/Settings/AndroidManifest.xml](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/AndroidManifest.xml)):

```xml
<activity android:name=".development.AppPicker"
          android:label="@string/select_application" />
```
No `android:exported="true"`, no `<intent-filter>` — unreachable from a third-party app.
The modern replacement (`DevelopmentAppPicker`, behind the `deprecateListActivity` flag)
is a `SubSettings` fragment launched internally via `SubSettingLauncher`, also not
exported.

**The deepest supported public link is the Developer options root:**
```xml
<activity android:name="Settings$DevelopmentSettingsActivity" android:exported="true">
    <intent-filter android:priority="1">
        <action android:name="android.settings.APPLICATION_DEVELOPMENT_SETTINGS" />
        <action android:name="com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS" />
        …
```
```java
Intent i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);   // "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
```
SDK doc: "*Show settings to allow configuration of application development-related
settings. As of `JELLY_BEAN_MR1` this action is a required part of the platform. Input:
Nothing. Output: Nothing.*" Note the general Settings caveat that a matching activity may
not exist on all devices — **always guard with `resolveActivity()` and fall back to
`Settings.ACTION_SETTINGS`.**

Two useful refinements:
- **Scroll-to-and-highlight the row (undocumented, best-effort):** Settings supports
  `":settings:fragment_args_key"` (`SettingsActivity.EXTRA_FRAGMENT_ARG_KEY`) and the
  mock preference key is `"mock_location_app"` (`MOCK_LOCATION_APP_KEY`). Adding
  `i.putExtra(":settings:fragment_args_key", "mock_location_app")` will, on stock
  Android, scroll to and highlight the entry. Purely additive — harmless where
  unsupported.
- **Dev options not yet enabled:** no intent exists. Best available:
  `Settings.ACTION_DEVICE_INFO_SETTINGS` (About phone) plus written instructions.
  Google's own doc supplies the per-OEM Build-number paths
  ([dev-options](https://developer.android.com/studio/debug/dev-options)): Pixel
  `Settings > About phone > Build number`; **Samsung Galaxy S8+**
  `Settings > About phone > Software information > Build number`; LG G6+
  `Settings > About phone > Software info > Build number`; HTC U11+
  `Settings > About > Software information > More > Build number`; OnePlus 5T+
  `Settings > About phone > Build number`; then "Tap the Build Number option seven times
  until you see the message *You are now a developer!*".

**OEM caveats** (lower-confidence, secondary sources): Samsung One UI nests the entry
under **Developer options → Debugging → Mock location app**, and older One UI calls it
"Allow mock locations"
([Tenorshare guide](https://www.tenorshare.com/change-location/samsung-galaxy-s25-mock-location.html)).
Xiaomi MIUI/HyperOS keeps the same Developer-options entry but community reports describe
aggressive background/battery management killing spoofer processes
([xiaomiui.net](https://xiaomiui.net/mock-location-xiaomi-in-2026-why-most-fake-gps-apps-fail-on-hyperos-and-what-actually-works-71976/))
— largely moot here since the poster lives inside the already-exempt VPN foreground
service, but a "disable battery optimization" hint in the guide is worthwhile for
Xiaomi/Huawei/Oppo. Keep the guide text generic ("open Developer options and find
*Select mock location app*") rather than hard-coding one OEM's path.

---

## 6. Update cadence and lifecycle

### 6.1 Does a mock fix go stale?

A posted mock fix is **retained indefinitely** as the provider's last known location —
`getLastKnownLocation()` will return it forever with an ever-growing age. But:

- **`requestLocationUpdates` clients get one callback per `setTestProviderLocation`
  call.** Post once and stop, and a navigation app subscribed at 1 Hz simply never gets
  another update and will conclude the signal was lost.
- **`getCurrentLocation()` applies a 30-second freshness rule**: `LocationProviderManager`
  defines `MAX_CURRENT_LOCATION_AGE_MS = 30 * 1000` and, on registration, immediately
  delivers the last location only if it is younger than that; otherwise it waits for a
  fresh one — which never arrives if posting stopped, so the request hangs until its own
  timeout and may return `null`.
- GMS FLP applies its own (undocumented) staleness/monotonicity logic and explicitly
  warns that "the FLP may expect monotonically increasing timestamps."

### 6.2 Recommended cadence

**Re-post every 1 second while active** (all providers), which is what production
spoofers do (Mock-my-GPS exposes a configurable "frequency at which location providers
receive mock updates"). 1 Hz is well inside every consumer's expectation, comfortably
under the 30 s `getCurrentLocation` window, and cheap. To be conservative about wakeups:
1 Hz while the screen is interactive and 5–10 s while the screen is off is a defensible
compromise; do not exceed ~15 s or `getCurrentLocation` callers start to see stale/no
results.

**`time` and `elapsedRealtimeNanos` must be recomputed on every post** — see the
validation rules in §2.4 (`elapsedRealtimeNanos` in the future is rejected outright;
reusing an old value makes every consumer see an increasingly stale fix and can trip
FLP's monotonicity logic).

**Battery:** near-zero net cost, and plausibly *negative*. Because a test provider
**replaces** the real provider implementation, the system explicitly stops the real one
(§7.1) — the GNSS engine is turned off while mocking `"gps"`. Own cost is one binder
call per provider per tick (3 calls/s) from a process that is already alive for the VPN.
Use a single `Handler`/`ScheduledExecutorService` tick on an existing thread, not
`AlarmManager`.

### 6.3 What happens when the app process dies

**Nothing removes the test providers.** This is the single most important operational
fact in this report.

- `LocationManagerService` registers **no `DeathRecipient` for test providers**. The only
  `linkToDeath` calls in the location stack are in `LocationProviderManager`'s *listener
  registration* classes (for `requestLocationUpdates` clients).
- The package-reset path (`SystemPackageResetHelper` listening to
  `ACTION_PACKAGE_CHANGED/REMOVED/RESTARTED`) reaches
  `LocationProviderManager.onPackageReset(packageName)`, which only iterates
  `updateRegistrations(... registration.remove())` — **it removes listener
  registrations, not mock providers.**
- Settings' "Developer options off" path only resets the app op.

**Therefore:** after a crash, a force-stop, "Clear data", or even an **uninstall**, a
registered test provider keeps shadowing the real provider, stays `enabled`, and never
publishes another fix — the device's location silently freezes at the last mock
coordinates until `removeTestProvider` is called again or the device **reboots** (the
state is in-memory in system_server). This matches the user reports behind threads like
[XDA: "GPS issues after playing with mock location"](https://xdaforums.com/t/gps-issues-after-playing-with-mock-location.4689979/).

Mitigation (mandatory in the blueprint): **on every app/service start, if the feature is
not supposed to be active, defensively call `removeTestProvider` for each of our provider
names inside try/catch.** It's a no-op on S+ when nothing is registered.

### 6.4 Deselection while active is unrecoverable

If the user picks a different mock app (or clears the selection) while mocking is active:
Settings sets our op to `MODE_ERRORED` **first**, so the subsequent `removeTestProvider`
throws `SecurityException` and the providers **linger** exactly as in §6.3. There is no
API to recover. The UX must therefore:
1. detect it (op watcher / `SecurityException`),
2. show an explicit recovery instruction: *"Location override could not be removed.
   Re-select URnetwork under Developer options → Select mock location app and turn this
   feature off, or restart your device."*,
3. auto-clean the moment the op comes back (op watcher → if feature off →
   `removeTestProvider` ×N).

Turning Developer options **off** has the same effect (op revoked, providers linger) —
worth a line in the guide: *turn this feature off in URnetwork before turning off
Developer options.*

### 6.5 Teardown order, and does real location resume?

```java
for (String p : activeProviders) {       // "fused" (31+), "network", "gps"
    try { lm.removeTestProvider(p); } catch (Throwable ignored) {}
}
// if the GMS layer was used: flp.setMockMode(false)
```
Order among providers doesn't matter functionally; remove them all. Notes:
- `setTestProviderEnabled(p, false)` before removing is **not** required and is slightly
  worse — it broadcasts a `PROVIDERS_CHANGED` "provider disabled" transition to every
  listener on the device. Just remove.
- **Real location resumes immediately and cleanly.**
  `MockableLocationProvider.setMockProvider(null)` → `setProviderLocked(mRealProvider)`
  restarts the real provider and re-applies the current `ProviderRequest`. Additionally,
  `LocationProviderManager.setMockProvider(null)` explicitly **purges the stale mock from
  last-known-location caches** and resets the coarse-location fudger:
  ```java
  // when removing a mock provider, also clear any mock last locations and reset the
  // location fudger. the mock provider could have been used to infer the current
  // location fudger offsets.
  if (provider == null) { for (…) mLastLocations.valueAt(i).clearMock(); mLocationFudger.resetOffsets(); }
  ```
  So after teardown, `getLastKnownLocation` won't hand back the fake city; consumers get
  `null` (or a real cached fix) until the GNSS engine produces a real fix — a normal
  cold-start delay of seconds to tens of seconds outdoors.

### 6.6 The Location master switch gates everything

`LocationProviderManager.onEnabledChanged`:
```java
boolean enabled = mState == STATE_STARTED
        && mProvider.getState().allowed
        && mSettingsHelper.isLocationEnabled(userId);
```
If the user has **Settings → Location** turned **off**, the mock provider is reported
disabled and no app receives the fixes, even though every API call succeeds. Detect with
`LocationManager.isLocationEnabled()` (API 28+) and surface it in the setup guide with a
link to `Settings.ACTION_LOCATION_SOURCE_SETTINGS`. On API 26–27 fall back to
`isProviderEnabled(GPS_PROVIDER) || isProviderEnabled(NETWORK_PROVIDER)`.

---

## 7. Effects while active

### 7.1 Full shadowing, device-wide

Yes — a test provider **completely replaces** the same-named real provider for **all**
apps. `MockableLocationProvider.setProviderLocked()` swaps the active implementation and,
for the displaced real provider, calls `setRequest(ProviderRequest.EMPTY_REQUEST)` then
`stop()`. There is no way for any app (including ours) to reach the real `"gps"` while it
is mocked. This is what makes a "pass-through" design structurally impossible (§10.4).

### 7.2 Mock flagging is unavoidable

`MockLocationProvider.setProviderLocation()` does:
```java
Location location = new Location(l);
location.setIsFromMockProvider(true);
```
Every delivered fix therefore has `Location.isMock() == true` (`isFromMockProvider()` is
the deprecated alias since API 31). The `Location` class doc says it plainly:

> "Android provides the ability for applications to submit 'mock' or faked locations…
> These locations can be identified via the `isMock()` API… **Keep in mind that the user
> may have a good reason for mocking their location, and thus apps should generally
> reject mock locations only when it is essential to their use case.**"

**Who rejects mock fixes in practice:** banking/fintech apps, ride-hailing and delivery
apps (driver-side especially), location-based games (Pokémon GO is the canonical
example), attendance/field-service apps, and some streaming/geo-licensing apps — see
[this developer security guide](https://blog.anmolthedeveloper.com/how-to-detect-fake-gps-and-mock-location-in-android-apps-a-developers-security-guide)
and [MockLocationDetector](https://github.com/smarques84/MockLocationDetector). Google
Maps itself does **not** reject mock fixes (it's the standard way users verify a spoofer
works; Surfshark's own guide tells users to "Open Maps app to verify location displays
according to connected VPN location"). Some detectors go further and enumerate installed
packages holding `ACCESS_MOCK_LOCATION` — meaning *merely declaring the permission* can
get URnetwork flagged by aggressive apps even when the feature is off. Worth a sentence
in the feature's disclosure text.

**Direct implication for design:** any "mirror the real location through the mock
provider" scheme would stamp `isMock=true` on genuinely-real fixes, breaking those same
apps *while the user believes location is normal*. That is a strong argument against
pass-through (§10.4).

### 7.3 Android 12–16 behavior changes

The official behavior-change pages for Android 12, 14, 15 and 16 (all-apps editions)
contain **zero occurrences of "mock"** —
[12](https://developer.android.com/about/versions/12/behavior-changes-all),
[14](https://developer.android.com/about/versions/14/behavior-changes-all),
[15](https://developer.android.com/about/versions/15/behavior-changes-all),
[16](https://developer.android.com/about/versions/16/behavior-changes-all)

What *has* changed, from source:
- **Android 12 (API 31):** the big one. `LocationManagerService` was refactored into
  `LocationProviderManager` + `MockableLocationProvider`; `FUSED_PROVIDER` and the
  `ProviderProperties` `addTestProvider` overloads became public API; `addTestProvider`
  became idempotent (replace instead of throw); `Location.isMock()` replaced
  `isFromMockProvider()`; the platform default provider for
  `getCurrentLocation`/geofencing became `fused`.
- **Since then (13/14/15/16):** no changes to the mock-location contract. Current
  `main`-branch code paths are as quoted throughout this report.
- **Ignore the SEO claims** circulating that "Android 14 verifies the mock app's
  signature / monitors behavior / restricts spoofing for sensitive apps". There is no
  such code in AOSP and no such statement in any Google doc. The gate is, and remains,
  exactly one app op.
- One genuine hardening to be aware of: `LocationResult.validate()` gained flag-guarded
  strict range/monotonicity checks (`Flags.locationValidation()`), which is why sloppy
  `Location` construction now fails with `IllegalArgumentException` on recent builds
  where it used to pass.

---

## 8. Permissions and foreground-service requirements

**Posting mock locations requires neither `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`
nor a `location` foreground-service type.** Verified in the server: `addTestProvider`,
`removeTestProvider`, `setTestProviderLocation`, `setTestProviderEnabled` each perform
exactly one authorization step —

```java
if (!mInjector.getAppOpsHelper().noteOp(AppOpsManager.OP_MOCK_LOCATION, identity)) { return; }
```

— and no `enforceCallingOrSelfPermission(ACCESS_*_LOCATION)`, no while-in-use/foreground
check. The SDK javadoc for all four methods documents only `SecurityException` for the
app op and `IllegalArgumentException` for arguments. `OP_MOCK_LOCATION` is not a
runtime-permission op, so it is not subject to the foreground/background app-op gating
that applies to location ops.

Contrast — the `location` FGS type
([Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)):
> "Runtime prerequisites: The user must have enabled location services **and** the app
> must be granted at least one of the following runtime permissions:
> `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`… **the location runtime permissions
> are subject to while-in-use restrictions. For this reason, you cannot create a location
> foreground service while your app is in the background, unless you've been granted
> `ACCESS_BACKGROUND_LOCATION`.**"

So a **pass-through/mirroring design would require** `ACCESS_FINE_LOCATION` +
`ACCESS_BACKGROUND_LOCATION` + the `location` FGS type + `FOREGROUND_SERVICE_LOCATION`,
dragging in Play's Location Permissions policy (prominent disclosure,
background-location justification/video review, "central to core functionality" test).
The mock-only design needs **none** of it.

**Practically:** run the 1 Hz poster from the existing VPN foreground service (or a
lifecycle object it owns). No new manifest permission beyond `ACCESS_MOCK_LOCATION`, no
new FGS type, no new runtime prompt. The only user-visible prerequisite is the
Developer-options selection.

---

## 9. UX precedent: Surfshark's "Override GPS location" guide

From [How to enable the GPS override function](https://support.surfshark.com/hc/en-us/articles/360011517640-How-to-enable-the-GPS-override-function)
(also mirrored at
[How to add Surfshark to the mock locations app list](https://support.surfshark.com/hc/en-us/articles/360009709419-How-to-add-Surfshark-to-the-mock-locations-app-list)):

**Setup**
1. Open the Surfshark app → **Settings**
2. Tap **VPN settings**
3. Scroll to **Advanced settings**, tap it
4. Find **Override GPS location**, toggle the slider
5. A pop-up appears explaining the device settings that must change → tap **Let's go**
6. Developer options must be enabled first → tap **Open settings**
7. Tap **Developer options**
8. Find **Select mock location app**, tap it
9. Select **Surfshark** from the list
10. Press **CLOSE**

**Verification**
1. Connect to the preferred VPN location
2. Open Maps to confirm the displayed location matches the VPN location
3. A notification is shown while the VPN is active

**Positioning** — the feature is described as "automatically match your GPS location with
your chosen server's location", motivated by two user goals: "Don't want apps to see
your location at all times" and "Wish to use an app that only provides its service in
specific countries."

**Gaps in their guide that we should close:** no instructions for enabling Developer
options itself (the 7 taps), no disable/cleanup instructions, no warning about apps that
reject mock locations, no mention of the Location master switch, and no recovery path
for the deselection-while-active trap.

---

## 10. Recommended implementation blueprint

### 10.1 Components

| Class | Responsibility |
|---|---|
| **`MockLocationController`** | Sole owner of all `LocationManager` test-provider calls. Holds the state machine, the active provider-name set, the current target lat/lon, and the repost ticker. Single-threaded (own `Handler`), no locking. Exposes `StateFlow<MockLocationState>`. |
| **`MockLocationEligibility`** (small, could be static methods on the controller) | Pure reads: `isDeveloperOptionsEnabled()`, `isSelectedMockApp()`, `isLocationServicesEnabled()`, `devSettingsIntent()`. No side effects. |
| **VPN service integration** | Calls `controller.onExitProviderChanged(city)` / `onTunnelDown()` / `onTunnelUp()`. Owns the controller's lifetime; starts it in `onCreate`, `shutdown()`s in `onDestroy`. |
| **Settings UI + setup sheet** | Renders state; drives the guide; hosts the toggle. Never touches `LocationManager` directly. |
| **`MockLocationStartupCleaner`** (can be one method on the controller) | On process start: if the persisted preference is OFF, best-effort `removeTestProvider` ×N to clear leftovers from a previous process. |

### 10.2 Exact calls, per version branch

```java
private static final String[] PROVIDERS_BASE = { LocationManager.GPS_PROVIDER,
                                                 LocationManager.NETWORK_PROVIDER };
// API 31+ additionally: LocationManager.FUSED_PROVIDER

private void addProvider(LocationManager lm, String name) {
    try { lm.removeTestProvider(name); } catch (Throwable ignored) {}   // required pre-S; harmless on S+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        lm.addTestProvider(name, new ProviderProperties.Builder()
                .setHasNetworkRequirement(false)
                .setHasSatelliteRequirement(false)
                .setHasCellRequirement(false)
                .setHasMonetaryCost(false)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build());
    } else {                                   // API 26–30
        lm.addTestProvider(name,
                /*requiresNetwork*/  false, /*requiresSatellite*/ false, /*requiresCell*/ false,
                /*hasMonetaryCost*/  false, /*supportsAltitude*/  true,
                /*supportsSpeed*/    true,  /*supportsBearing*/   true,
                Criteria.POWER_LOW,          // == ProviderProperties.POWER_USAGE_LOW (1)
                Criteria.ACCURACY_FINE);     // == ProviderProperties.ACCURACY_FINE   (1)
    }
    lm.setTestProviderEnabled(name, true);     // MANDATORY — providers start disallowed
}
```
Provider set: `PROVIDERS_BASE` on 26–30; `PROVIDERS_BASE + FUSED_PROVIDER` on 31+. Never
`"passive"`.

Post loop (1 Hz), per provider:
```java
Location l = new Location(name);
l.setLatitude(lat); l.setLongitude(lon);
l.setAccuracy(8f); l.setAltitude(alt); l.setSpeed(0f); l.setBearing(0f);
l.setVerticalAccuracyMeters(12f);                 // API 26+
l.setSpeedAccuracyMetersPerSecond(0.1f);          // API 26+
l.setBearingAccuracyDegrees(1f);                  // API 26+
if (Build.VERSION.SDK_INT >= 29) l.setElapsedRealtimeUncertaintyNanos(1_000_000d);
l.setTime(System.currentTimeMillis());
l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());   // fresh, never future-dated
lm.setTestProviderLocation(name, l);
```

Teardown, per provider: `try { lm.removeTestProvider(name); } catch (Throwable ignored) {}`.

Optional GMS layer (only on flavors where `play-services-location` is already a
dependency, and only guarded by
`GoogleApiAvailability.isGooglePlayServicesAvailable() == SUCCESS`):
`flp.setMockMode(true)` → per-tick `flp.setMockLocation(l)` → **always**
`flp.setMockMode(false)` on teardown, including from `onDestroy` and from the startup
cleaner.

### 10.3 State machine

```
                    ┌──────────────────────────────────────────────┐
                    │              DISABLED (default)              │  feature toggle off
                    │  invariant: no test providers registered     │
                    └───────┬──────────────────────────────────────┘
                            │ user enables toggle
                            ▼
      ┌────────────── NEEDS_DEV_OPTIONS ──────────────┐   DEVELOPMENT_SETTINGS_ENABLED == 0
      │  guide: 7 taps on Build number (manual)       │   → ACTION_DEVICE_INFO_SETTINGS + text
      └───────┬───────────────────────────────────────┘
              │ dev options on
              ▼
      ┌────────────── NEEDS_SELECTION ────────────────┐   checkOpNoThrow(OPSTR_MOCK_LOCATION) != ALLOWED
      │  guide: open Developer options → Select mock  │   → ACTION_APPLICATION_DEVELOPMENT_SETTINGS
      │  location app → URnetwork                     │      (+ :settings:fragment_args_key)
      └───────┬───────────────────────────────────────┘
              │ op becomes ALLOWED (op watcher fires)
              ▼
      ┌────────────── NEEDS_LOCATION_ON ──────────────┐   !isLocationEnabled()
      │  guide: turn on Settings → Location           │   → ACTION_LOCATION_SOURCE_SETTINGS
      └───────┬───────────────────────────────────────┘
              │
              ▼
      ┌──────────────── ELIGIBLE ─────────────────────┐   all preconditions met, tunnel/target unknown
      └───────┬───────────────────────────────────────┘
              │ tunnel up AND exit city known
              ▼
      ┌──────────────── ACTIVE ───────────────────────┐   providers registered, 1 Hz posting
      └───┬───────────────────────┬───────────────────┘
          │ toggle off / tunnel   │ SecurityException mid-session
          │ down / service death  │ (user deselected us)
          ▼                       ▼
       (teardown)          ┌── ORPHANED ─────────────────────────────────┐
        → DISABLED         │ cannot remove providers; device location is │
                           │ frozen. Show recovery instructions; retry   │
                           │ cleanup on op-restored. → DISABLED on success│
                           └─────────────────────────────────────────────┘
```
Additional error state **`ERROR_TRANSIENT`** for unexpected `IllegalArgumentException`
(log, tear down, back to `ELIGIBLE`, allow retry). Recompute eligibility on `onResume` of
the settings screen, on the op-watcher callback, and on
`ACTION_LOCATION_MODE_CHANGED`/`PROVIDERS_CHANGED` broadcasts.

### 10.4 Pass-through when disabled: remove vs. mirror

**Recommendation: remove the test providers. Do not mirror.** The mirroring option is not
merely worse, it is largely unbuildable:

| | **A. Remove test providers (recommended)** | **B. Mirror real location through the mock provider** |
|---|---|---|
| Can it even work? | Yes. Real providers are automatically restored (`setProviderLocked(mRealProvider)`), stale mock last-knowns are purged. | **Structurally broken.** While `"gps"` is mocked, the real GPS implementation is stopped (`setRequest(EMPTY)` + `stop()`) and *nobody*, including us, can read it. There is no un-mocked source to mirror from once gps+network+fused are covered. |
| Permissions | None. | `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` + `FOREGROUND_SERVICE_LOCATION` + `location` FGS type. |
| Play policy | Out of scope of Location Permissions policy. | Background-location justification, prominent disclosure, review. |
| Correctness for other apps | Real fixes are genuinely real: `isMock=false`. Banking/rideshare/games behave normally. | Every "real" fix is stamped `isMock=true` — silently breaks those apps while the user believes location is off. |
| Battery | Real GNSS runs only when some app actually requests it. | Must hold a continuous location request to have something to mirror; duty-cycles GNSS permanently. |
| Latency after toggle-off | Seconds (normal GNSS reacquisition). | N/A |
| Failure blast radius | Small — worst case is the orphan state (§6.4). | Large — a crash leaves a mock provider that mirrors nothing. |

Only conceivable niche for B: mocking a *subset* (e.g. `gps` only) while mirroring from
`network`. Don't — it produces inconsistent readings between providers, is trivially
detectable, and still taints fixes as mock.

**Corollary:** the toggle-off path is simply "stop the ticker → `removeTestProvider` ×N
(→ `setMockMode(false)`)". Real location resumes natively, unflagged.

### 10.5 Edge cases and required handling

| Edge case | Behavior / required handling |
|---|---|
| **Exit provider changes mid-session** | Just update the target coordinates; the next tick posts the new city. Do **not** re-add providers, do not interpolate — a teleport is expected of a VPN feature. Consider resetting `speed`/`bearing` to 0 and briefly widening `accuracy` so consumers treat it as a new fix rather than an implausible 900 km/h move (many apps filter on implied velocity). |
| **VPN disconnects / tunnel down** | Tear down (remove providers) so the device isn't left reporting a city it isn't exiting through. Re-arm automatically when the tunnel returns and the feature is still on. Never leave a mock fix live without a corresponding tunnel. |
| **Exit provider city unknown / no geo data** | Stay in `ELIGIBLE`, don't register providers. Surface "waiting for provider location" rather than posting a guess. |
| **App process death / force-stop / crash** | Providers linger (§6.3). At every process start: if the persisted toggle is OFF → best-effort `removeTestProvider` ×N; if ON → re-add and resume (on S+ `addTestProvider` cleanly replaces the orphan). Persist the toggle *and* the provider-name set registered. |
| **Uninstall while active** | Providers linger until reboot; nothing fixable from code. Mention it in the guide: *turn the feature off before uninstalling.* |
| **User deselects the app in Developer options while active** | `SecurityException` on the next call; cleanup impossible (§6.4). Transition to `ORPHANED`, show the recovery instructions, and retry cleanup automatically via the `startWatchingMode` callback if the op is ever restored. |
| **User turns Developer options off entirely** | Same as deselection (Settings resets the op via `onDeveloperOptionsDisabled`). Same `ORPHANED` handling. |
| **Another mock app is selected** | Implicit deselection → `ORPHANED`; also treat "op ALLOWED but our providers were replaced by theirs" as a normal loss and just report `NEEDS_SELECTION`. |
| **Location master switch off** | All calls succeed, nothing is delivered. Detect with `isLocationEnabled()` (28+) and gate into `NEEDS_LOCATION_ON`. |
| **Multi-user / work profile** | The op is per-uid-per-user; the picker only lists apps in the current user. Nothing special to do, but don't assume a single global state. |
| **Direct boot / pre-unlock** | Don't attempt anything before user unlock; `LocationManagerService`'s provider set is still settling during boot. Arm from the existing post-unlock VPN startup path. |
| **API 26–30 device** | No `fused` mocking (pointless pre-31 anyway); legacy `addTestProvider` overload; must remove-before-add; `isLocationEnabled()` unavailable on 26–27 → fall back to `isProviderEnabled`. |
| **Non-GMS device (GrapheneOS, Huawei)** | Platform path works unchanged; skip the optional FLP layer behind a `GoogleApiAvailability` check. |
| **Aggressive OEM battery management (Xiaomi/Huawei/Oppo)** | The poster lives in the VPN foreground service, so it survives; still worth a "disable battery optimization for URnetwork" hint if users report frozen locations. |

### 10.6 Disclosure text worth shipping with the toggle

Short, three bullets, shown in the setup sheet:
1. *This changes the location reported to **all** apps on your device, not just
   URnetwork.*
2. *Apps can tell the location is simulated. Banking, ride-hailing, delivery and some
   game apps may refuse to work while this is on.*
3. *Turn this feature off inside URnetwork **before** turning off Developer options,
   deselecting URnetwork, or uninstalling — otherwise your device's location may stay
   frozen until you restart it.*

---

## Sources

**AOSP source (authoritative):**
- [core/res/AndroidManifest.xml — `ACCESS_MOCK_LOCATION` protection level](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)
- [services/…/location/LocationManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/LocationManagerService.java)
- [services/…/location/provider/LocationProviderManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/LocationProviderManager.java)
- [services/…/location/provider/MockableLocationProvider.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/MockableLocationProvider.java), [MockLocationProvider.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/MockLocationProvider.java), [AbstractLocationProvider.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/AbstractLocationProvider.java)
- [services/…/location/injector/SystemAppOpsHelper.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/injector/SystemAppOpsHelper.java), [SystemPackageResetHelper.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/injector/SystemPackageResetHelper.java)
- [services/…/location/geofence/GeofenceManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/geofence/GeofenceManager.java)
- [services/…/appop/AppOpsService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/appop/AppOpsService.java)
- [Android 10 LocationManagerService (pre-refactor behavior)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/LocationManagerService.java)
- Settings app: [AndroidManifest.xml](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/AndroidManifest.xml), [MockLocationAppPreferenceController.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/MockLocationAppPreferenceController.java), [AppPicker.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/AppPicker.java), [DevelopmentAppPicker.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/DevelopmentAppPicker.java), [SettingsActivity.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/SettingsActivity.java)
- Platform SDK 36 sources (identical on [cs.android.com](https://cs.android.com)): `android/location/LocationManager.java`, `Location.java`, `LocationResult.java`, `provider/ProviderProperties.java`, `Criteria.java`, `android/app/AppOpsManager.java`, `android/provider/Settings.java`

**Official documentation:**
- [Configure on-device developer options](https://developer.android.com/studio/debug/dev-options) — "Select mock location app", OEM Build-number table
- [LocationManager reference](https://developer.android.com/reference/android/location/LocationManager) · [API 31 diff report](https://developer.android.com/sdk/api_diff/31/changes/android.location.LocationManager)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) · [Android 14 FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- Behavior changes: [12](https://developer.android.com/about/versions/12/behavior-changes-all) · [14](https://developer.android.com/about/versions/14/behavior-changes-all) · [15](https://developer.android.com/about/versions/15/behavior-changes-all) · [16](https://developer.android.com/about/versions/16/behavior-changes-all) (no mock-location changes in any)
- [Google Play services: FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) · [FusedLocationProviderApi](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderApi)
- Play policy: [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379) · [Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/9888077) · [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170) · [User Data](https://support.google.com/googleplay/android-developer/answer/9888076)

**Precedent / secondary:**
- Surfshark: [How to enable the GPS override function](https://support.surfshark.com/hc/en-us/articles/360011517640-How-to-enable-the-GPS-override-function) · [What is the GPS override feature](https://support.surfshark.com/hc/en-us/articles/360011723459-What-is-the-GPS-override-feature-and-how-to-use-it) · [How to add Surfshark to the mock locations app list](https://support.surfshark.com/hc/en-us/articles/360009709419-How-to-add-Surfshark-to-the-mock-locations-app-list) · [TechRadar](https://www.techradar.com/news/surfshark-adds-gps-spoofing-feature-to-its-vpn)
- Open source: [warren-bank/Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location) · [mcastillof/FakeTraveler](https://github.com/mcastillof/FakeTraveler) · [smarques84/MockLocationDetector](https://github.com/smarques84/MockLocationDetector) · [SaeedMasoumi/mock-location-automator](https://github.com/SaeedMasoumi/mock-location-automator)
- Detection/engineering writeups: [KlaasNotFound — Location on Android: Stop Mocking Me!](https://klaasnotfound.com/2016/05/27/location-on-android-stop-mocking-me/) · [Detecting fake GPS — developer's security guide](https://blog.anmolthedeveloper.com/how-to-detect-fake-gps-and-mock-location-in-android-apps-a-developers-security-guide)
- OEM paths (low confidence, community sources): [Samsung One UI](https://www.tenorshare.com/change-location/samsung-galaxy-s25-mock-location.html) · [Xiaomi HyperOS](https://xiaomiui.net/mock-location-xiaomi-in-2026-why-most-fake-gps-apps-fail-on-hyperos-and-what-actually-works-71976/) · [XDA: GPS issues after playing with mock location](https://xdaforums.com/t/gps-issues-after-playing-with-mock-location.4689979/)
