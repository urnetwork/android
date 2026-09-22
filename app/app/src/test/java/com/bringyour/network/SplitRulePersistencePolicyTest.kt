package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRulePersistencePolicyTest {

    @Test
    fun disconnectedDevicePersistsToStorageWithoutLiveApply() {
        // When device is null / disconnected, mutations must still be written
        // to localState so they survive app restarts and APK updates.
        val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = false)
        assertFalse(plan.applyLive)
        assertTrue(plan.persistToStorage)
    }

    @Test
    fun connectedDeviceAppliesLiveAndLeavesPersistenceToTheDevice() {
        // The device persists on its own serial queue; a second write from
        // the app races it and can land a stale or duplicated list.
        val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = true)
        assertTrue(plan.applyLive)
        assertFalse(plan.persistToStorage)
    }

    @Test
    fun everyWriteLandsInExactlyOnePlace() {
        for (connected in listOf(true, false)) {
            val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = connected)
            assertTrue(plan.applyLive != plan.persistToStorage)
        }
    }

    @Test
    fun resolveEffectivePrefersLiveDeviceOverStored() {
        // The blockActions caller keys off "a device exists": when the device
        // is present its (possibly empty/null) state is authoritative and never
        // falls back to storage.
        // 1. Device present -> its value wins.
        assertEquals("live", SplitRulePersistencePolicy.resolveEffective(livePresent = true, live = "live", stored = "stored"))
        // 2. Device present but empty -> empty is the answer, not stored.
        assertEquals(
            emptyList<String>(),
            SplitRulePersistencePolicy.resolveEffective(livePresent = true, live = emptyList(), stored = listOf("stored"))
        )
        // 3. Device present but null -> null is authoritative (no resurrection).
        assertEquals(
            null,
            SplitRulePersistencePolicy.resolveEffective<String>(livePresent = true, live = null, stored = "stored")
        )

        // The dnsSettings caller keys off "the device reported a value".
        // 4. No live value -> stored value is used.
        assertEquals("stored", SplitRulePersistencePolicy.resolveEffective(livePresent = false, live = null, stored = "stored"))
        // 5. Neither source has the value -> null.
        assertEquals(null, SplitRulePersistencePolicy.resolveEffective(livePresent = false, live = null, stored = null))
    }

    @Test
    fun resolveEffectiveBlockerPrefersLiveThenStoredThenDefault() {
        // 1. Live device takes precedence.
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(true, false, false))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(false, true, false))
        // 2. Stored blocker is preserved when disconnected / device is null.
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, true, false))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, false, true))
        // 3. Default blocker used when neither live nor stored exists.
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, null, true))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, null, false))
    }
}