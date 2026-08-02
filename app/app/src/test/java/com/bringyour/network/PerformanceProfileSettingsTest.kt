package com.bringyour.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceProfileSettingsTest {
    @Test
    fun nilUnsetAndExplicitAutoProfilesAreEquivalent() {
        val unset = PerformanceProfileSnapshot()
        val auto = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.AUTO)

        assertTrue(
            performanceProfileSnapshotsSemanticallyEqual(
                PerformanceProfileSnapshot(),
                unset,
            )
        )
        assertTrue(performanceProfileSnapshotsSemanticallyEqual(unset, auto))
    }

    @Test
    fun autoProfileIgnoresReconstructedWindowSize() {
        val first = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.AUTO,
            windowSize = windowSize(minimum = 17, maximum = 23),
        )
        val second = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.AUTO,
            windowSize = windowSize(minimum = 41, maximum = 47),
        )

        assertTrue(performanceProfileSnapshotsSemanticallyEqual(first, second))
    }

    @Test
    fun orthogonalProfileChangesAreNotSuppressed() {
        val baseline = PerformanceProfileSnapshot()
        val direct = PerformanceProfileSnapshot(allowDirect = true)
        val encrypted = PerformanceProfileSnapshot(postQuantumEncryption = true)

        assertFalse(performanceProfileSnapshotsSemanticallyEqual(baseline, direct))
        assertFalse(performanceProfileSnapshotsSemanticallyEqual(baseline, encrypted))
    }

    @Test
    fun omittedFixedWindowEqualsItsEffectiveDefault() {
        val omitted = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.QUALITY,
        )
        val explicit = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.QUALITY,
            windowSize = defaultWindowSize(),
        )

        assertTrue(performanceProfileSnapshotsSemanticallyEqual(omitted, explicit))
    }

    @Test
    fun fixedWindowPolicyChangeIsNotSuppressed() {
        val first = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.SPEED,
            windowSize = windowSize(minimum = 1, maximum = 1),
        )
        val second = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.SPEED,
            windowSize = windowSize(minimum = 2, maximum = 2),
        )

        assertFalse(performanceProfileSnapshotsSemanticallyEqual(first, second))
    }

    @Test
    fun fixedWindowTypeChangeIsNotSuppressed() {
        val quality = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.QUALITY,
        )
        val speed = PerformanceProfileSnapshot(
            windowMode = PerformanceWindowMode.SPEED,
        )

        assertFalse(performanceProfileSnapshotsSemanticallyEqual(quality, speed))
    }

    @Test
    fun missingLiveDeviceDoesNotSuppressPersistedChange() {
        val stored = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.SPEED)
        val target = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.AUTO)

        val plan = performanceProfileWritePlan(
            stored = stored,
            live = null,
            target = target,
        )

        assertTrue(plan.persist)
        assertFalse(plan.applyLive)
    }

    @Test
    fun equalStoredAndLiveProfilesNeedNoWrites() {
        val target = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.SPEED)

        val plan = performanceProfileWritePlan(
            stored = target.copy(),
            live = target.copy(),
            target = target,
        )

        assertFalse(plan.persist)
        assertFalse(plan.applyLive)
    }

    @Test
    fun staleStoredProfileIsUpdatedWithoutResettingEqualLiveDevice() {
        val target = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.SPEED)

        val plan = performanceProfileWritePlan(
            stored = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.QUALITY),
            live = target.copy(),
            target = target,
        )

        assertTrue(plan.persist)
        assertFalse(plan.applyLive)
    }

    @Test
    fun staleLiveProfileIsUpdatedWithoutRewritingEqualStorage() {
        val target = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.SPEED)

        val plan = performanceProfileWritePlan(
            stored = target.copy(),
            live = PerformanceProfileSnapshot(windowMode = PerformanceWindowMode.QUALITY),
            target = target,
        )

        assertFalse(plan.persist)
        assertTrue(plan.applyLive)
    }

    private fun defaultWindowSize(): WindowSizeSnapshot {
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

    private fun windowSize(minimum: Int, maximum: Int): WindowSizeSnapshot {
        return WindowSizeSnapshot(
            minimum = minimum,
            p2pOnlyMinimum = 0,
            maximum = maximum,
            hardMaximum = 0,
            reconnectScale = 0.0,
            keepHealthiestCount = 0,
            userLimit = 0,
        )
    }
}
