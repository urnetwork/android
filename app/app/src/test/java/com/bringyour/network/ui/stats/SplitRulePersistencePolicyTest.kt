package com.bringyour.network.ui.stats

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
    fun resolveEffectiveOverridesFollowsHierarchy() {
        val vcRules = listOf("rule-vc")
        val deviceRules = listOf("rule-dev")
        val storedRules = listOf("rule-stored")

        // 1. ViewController overrides take highest precedence
        assertEquals(
            vcRules,
            SplitRulePersistencePolicy.resolveEffectiveOverrides(vcRules, deviceRules, storedRules)
        )

        // 2. Device overrides take precedence when ViewController is absent
        assertEquals(
            deviceRules,
            SplitRulePersistencePolicy.resolveEffectiveOverrides(null, deviceRules, storedRules)
        )

        // 3. Stored localState overrides are used when offline (device is null)
        assertEquals(
            storedRules,
            SplitRulePersistencePolicy.resolveEffectiveOverrides(null, null, storedRules)
        )

        // 4. Empty list fallback when all sources are null
        assertEquals(
            emptyList<String>(),
            SplitRulePersistencePolicy.resolveEffectiveOverrides<String>(null, null, null)
        )
    }

    @Test
    fun resolveEffectiveDnsFollowsHierarchy() {
        val liveDns = "live-dns"
        val storedDns = "stored-dns"
        val defaultDns = "default-dns"

        // 1. Live device takes precedence
        assertEquals(
            liveDns,
            SplitRulePersistencePolicy.resolveEffectiveDns(liveDns, storedDns, defaultDns)
        )

        // 2. Stored DNS is preserved when disconnected / device is null
        assertEquals(
            storedDns,
            SplitRulePersistencePolicy.resolveEffectiveDns(null, storedDns, defaultDns)
        )

        // 3. Default DNS used when neither live nor stored exists
        assertEquals(
            defaultDns,
            SplitRulePersistencePolicy.resolveEffectiveDns(null, null, defaultDns)
        )
    }

    @Test
    fun resolveEffectiveBlockerFollowsHierarchy() {
        // 1. Live device takes precedence
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(true, false, false))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(false, true, false))

        // 2. Stored blocker is preserved when disconnected / device is null
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, true, false))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, false, true))

        // 3. Default blocker used when neither live nor stored exists
        assertTrue(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, null, true))
        assertFalse(SplitRulePersistencePolicy.resolveEffectiveBlocker(null, null, false))
    }
}

