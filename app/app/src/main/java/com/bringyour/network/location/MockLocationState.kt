package com.bringyour.network.location

// Pure state model for the mock location engine (no Android dependencies) so
// the state resolution is unit testable on the JVM. See
// ~/urnetwork/android/MOCKLOCATION.md §10.3 for the state machine.

enum class MockLocationStatus {
    // feature toggle off; invariant: no test providers registered
    DISABLED,

    // Settings.Global.DEVELOPMENT_SETTINGS_ENABLED == 0; guide: 7 taps on
    // Build number (cannot be automated)
    NEEDS_DEV_OPTIONS,

    // the MOCK_LOCATION app op is not MODE_ALLOWED for us; guide: Developer
    // options -> Select mock location app -> URnetwork
    NEEDS_SELECTION,

    // the device Location master switch is off; every test-provider call
    // would succeed but nothing would be delivered to any app
    NEEDS_LOCATION_ON,

    // all preconditions met; waiting for tunnel up + a located provider
    ELIGIBLE,

    // test providers registered, 1 Hz posting
    ACTIVE,

    // test providers are (or may be) lingering and cleanup is impossible
    // because the mock-location app op was revoked (deselected in Developer
    // options / Developer options turned off) — MOCKLOCATION.md §6.4. The
    // flag clears only when a cleanup attempt fully succeeds.
    ORPHANED,

    // unexpected failure (e.g. IllegalArgumentException); torn down, retry
    // allowed. Never returned by resolveMockLocationStatus — the controller
    // overlays it while a retry is pending.
    ERROR_TRANSIENT,
}

// The oldest connected provider with coordinates, i.e. where the device
// location is synced to while ACTIVE.
data class MockLocationTarget(
    val clientId: String,
    val label: String,
    val lat: Double,
    val lon: Double,
)

data class MockLocationState(
    val status: MockLocationStatus,
    val enabled: Boolean,
    val target: MockLocationTarget?,
    // The raw setup signals, reported independently of `enabled`/`status`.
    // `status` collapses to DISABLED whenever the toggle is off, so it cannot
    // answer "is the device set up?" — which is exactly what the toggle and
    // the setup guide need to know while the feature is still off.
    val devOptionsEnabled: Boolean = false,
    val mockAppSelected: Boolean = false,
    val locationServicesEnabled: Boolean = false,
) {
    val setupComplete: Boolean
        get() = devOptionsEnabled && mockAppSelected && locationServicesEnabled
}

// Resolves the user-visible status from the engine inputs.
//
// ORPHANED wins over everything, including a disabled toggle: the flag means
// test providers may be lingering without cleanup being possible, and the
// controller clears it only after a successful cleanup — at which point a
// disabled toggle resolves to DISABLED (MOCKLOCATION.md §6.4). The remaining
// gates apply in setup order: developer options -> mock app selection ->
// location services; then ACTIVE only while the tunnel is up and a located
// provider target exists, ELIGIBLE otherwise.
fun resolveMockLocationStatus(
    enabled: Boolean,
    devOptionsEnabled: Boolean,
    isSelectedMockApp: Boolean,
    locationServicesEnabled: Boolean,
    tunnelUp: Boolean,
    target: MockLocationTarget?,
    orphaned: Boolean,
): MockLocationStatus {
    if (orphaned) {
        return MockLocationStatus.ORPHANED
    }
    if (!enabled) {
        return MockLocationStatus.DISABLED
    }
    if (!devOptionsEnabled) {
        return MockLocationStatus.NEEDS_DEV_OPTIONS
    }
    if (!isSelectedMockApp) {
        return MockLocationStatus.NEEDS_SELECTION
    }
    if (!locationServicesEnabled) {
        return MockLocationStatus.NEEDS_LOCATION_ON
    }
    return if (tunnelUp && target != null) {
        MockLocationStatus.ACTIVE
    } else {
        MockLocationStatus.ELIGIBLE
    }
}
