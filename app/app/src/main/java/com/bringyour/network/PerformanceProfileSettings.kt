package com.bringyour.network

import com.bringyour.sdk.PerformanceProfile
import com.bringyour.sdk.Sdk

/**
 * Compares the transport behavior installed by two SDK profiles.
 *
 * Presentation state is reconstructed whenever an Activity/ViewModel is
 * recreated. Object identity is therefore not a change signal: nil, an unset
 * window type, and explicit auto are equivalent when their orthogonal flags
 * match, and auto ignores window-size fields.
 */
internal fun performanceProfilesSemanticallyEqual(
    first: PerformanceProfile?,
    second: PerformanceProfile?,
): Boolean {
    return performanceProfileSnapshotsSemanticallyEqual(
        performanceProfileSnapshot(first),
        performanceProfileSnapshot(second),
    )
}

internal enum class PerformanceWindowMode {
    AUTO,
    QUALITY,
    SPEED,
}

internal data class PerformanceProfileSnapshot(
    val windowMode: PerformanceWindowMode = PerformanceWindowMode.AUTO,
    val windowSize: WindowSizeSnapshot? = null,
    val allowDirect: Boolean = false,
    val postQuantumEncryption: Boolean = false,
)

internal data class WindowSizeSnapshot(
    val minimum: Int,
    val p2pOnlyMinimum: Int,
    val maximum: Int,
    val hardMaximum: Int,
    val reconnectScale: Double,
    val keepHealthiestCount: Int,
    val userLimit: Int,
)

internal fun performanceProfileSnapshotsSemanticallyEqual(
    first: PerformanceProfileSnapshot,
    second: PerformanceProfileSnapshot,
): Boolean {
    if (first.allowDirect != second.allowDirect ||
        first.postQuantumEncryption != second.postQuantumEncryption
    ) {
        return false
    }

    if (first.windowMode != second.windowMode) {
        return false
    }
    if (first.windowMode == PerformanceWindowMode.AUTO) {
        return true
    }
    return effectiveWindowSize(first.windowSize) ==
        effectiveWindowSize(second.windowSize)
}

internal fun performanceProfileSnapshot(
    profile: PerformanceProfile?,
): PerformanceProfileSnapshot {
    return PerformanceProfileSnapshot(
        windowMode = when (profile?.windowType) {
            Sdk.WindowTypeQuality -> PerformanceWindowMode.QUALITY
            Sdk.WindowTypeSpeed -> PerformanceWindowMode.SPEED
            else -> PerformanceWindowMode.AUTO
        },
        windowSize = profile?.windowSize?.let {
            WindowSizeSnapshot(
                minimum = it.windowSizeMin.toInt(),
                p2pOnlyMinimum = it.windowSizeMinP2pOnly.toInt(),
                maximum = it.windowSizeMax.toInt(),
                hardMaximum = it.windowSizeHardMax.toInt(),
                reconnectScale = it.windowSizeReconnectScale,
                keepHealthiestCount = it.keepHealthiestCount.toInt(),
                userLimit = it.ulimit.toInt(),
            )
        },
        allowDirect = profile?.allowDirect ?: false,
        postQuantumEncryption = profile?.postQuantumEncryption ?: false,
    )
}

internal data class PerformanceProfileWritePlan(
    val persist: Boolean,
    val applyLive: Boolean,
)

/**
 * Plans the two independent writes. A missing live device must not make an
 * unequal persisted profile look equal to the target; that previously lost a
 * user change made while the device was being recreated.
 *
 * Null snapshots mean the corresponding storage/device endpoint is absent,
 * not a nil SDK profile (a nil profile is represented by the AUTO snapshot).
 */
internal fun performanceProfileWritePlan(
    stored: PerformanceProfileSnapshot?,
    live: PerformanceProfileSnapshot?,
    target: PerformanceProfileSnapshot,
): PerformanceProfileWritePlan {
    return PerformanceProfileWritePlan(
        persist = stored == null ||
            !performanceProfileSnapshotsSemanticallyEqual(stored, target),
        applyLive = live != null &&
            !performanceProfileSnapshotsSemanticallyEqual(live, target),
    )
}

private fun effectiveWindowSize(settings: WindowSizeSnapshot?): WindowSizeSnapshot {
    if (settings != null) {
        return settings
    }
    // Mirrors connect.DefaultWindowSizeSettings, which the SDK installs
    // when a fixed profile omits WindowSize.
    return WindowSizeSnapshot(
        minimum = 1,
        p2pOnlyMinimum = 0,
        maximum = 1,
        hardMaximum = 4,
        reconnectScale = 1.0,
        keepHealthiestCount = 1,
        userLimit = 0,
    )
}
