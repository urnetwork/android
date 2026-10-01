package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleNetworkSpaceTest {

    /** A space record, standing in for the sdk's NetworkSpace. */
    private data class Space(val hostName: String, val envName: String)

    /**
     * Records every manager call in order so the test can pin that the
     * legacy-key migration happens before the bundled key is touched.
     */
    private class RecordingStore(
        existing: List<Space> = emptyList(),
        override var active: Space? = null,
    ) : BundleNetworkSpaceStore<Space> {
        val spaces = existing.toMutableList()
        val calls = mutableListOf<String>()

        override fun migrate(fromHostName: String, toHostName: String, envName: String): Boolean {
            calls += "migrate $fromHostName->$toHostName/$envName"
            val from = spaces.firstOrNull { it.hostName == fromHostName && it.envName == envName }
                ?: return false
            if (spaces.any { it.hostName == toHostName && it.envName == envName }) {
                return false
            }
            val to = Space(toHostName, envName)
            spaces[spaces.indexOf(from)] = to
            if (active == from) {
                // the sdk re-points the active selection at the moved space
                active = to
            }
            return true
        }

        override fun exists(hostName: String, envName: String): Boolean {
            calls += "exists $hostName/$envName"
            return spaces.any { it.hostName == hostName && it.envName == envName }
        }

        override fun update(hostName: String, envName: String): Space {
            calls += "update $hostName/$envName"
            return spaces.firstOrNull { it.hostName == hostName && it.envName == envName }
                ?: Space(hostName, envName).also { spaces += it }
        }
    }

    private val identity = BundleNetworkSpaceIdentity(
        hostName = "bringyour.com",
        envName = "main",
        legacyHostName = "ur.network",
    )

    @Test
    fun theBundleIsKeyedByTheOperatorHostAndMigratesTheLegacyKey() {
        // the operator stays bringyour.com; the cancelled *.ur.network move
        // leaves only the legacy key behind, and no migration host
        assertEquals("bringyour.com", BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME)
        assertEquals("ur.network", BuildConfig.BRINGYOUR_BUNDLE_LEGACY_HOST_NAME)
        assertEquals("", BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME)
        assertEquals("main", BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        // links keep the ur.io site
        assertEquals("ur.io", BuildConfig.BRINGYOUR_BUNDLE_LINK_HOST_NAME)

        val fromBuild = BundleNetworkSpaceIdentity.fromBuildConfig()
        assertEquals(identity, fromBuild)
        assertTrue(fromBuild.migratesFromLegacyHost)
    }

    @Test
    fun theLegacyKeyIsMigratedBeforeTheBundledKeyIsReadOrUpdated() {
        val legacy = Space("ur.network", "main")
        val store = RecordingStore(existing = listOf(legacy), active = legacy)

        val install = installBundleNetworkSpace(identity, store)

        assertEquals(
            listOf(
                "migrate ur.network->bringyour.com/main",
                "exists bringyour.com/main",
                "update bringyour.com/main",
            ),
            store.calls,
        )
        assertTrue(install.migratedFromLegacyHost)
        assertEquals(Space("bringyour.com", "main"), install.networkSpace)
        // the moved space was already active: nothing is reselected, and the
        // legacy key is gone
        assertFalse(install.activated)
        assertSame(install.networkSpace, store.active)
        assertEquals(listOf(Space("bringyour.com", "main")), store.spaces)
    }

    @Test
    fun aSecondLaunchFindsNothingToMigrate() {
        val legacy = Space("ur.network", "main")
        val store = RecordingStore(existing = listOf(legacy), active = legacy)
        installBundleNetworkSpace(identity, store)
        store.calls.clear()

        val install = installBundleNetworkSpace(identity, store)

        assertFalse(install.migratedFromLegacyHost)
        assertFalse(install.activated)
        assertEquals("migrate ur.network->bringyour.com/main", store.calls.first())
        assertEquals(listOf(Space("bringyour.com", "main")), store.spaces)
    }

    @Test
    fun aFreshInstallCreatesAndSelectsTheBundledSpace() {
        val store = RecordingStore()

        val install = installBundleNetworkSpace(identity, store)

        assertFalse(install.migratedFromLegacyHost)
        assertTrue(install.activated)
        assertEquals(Space("bringyour.com", "main"), install.networkSpace)
        assertSame(install.networkSpace, store.active)
    }

    @Test
    fun aCustomServerSelectionIsKeptWhenTheBundledSpaceAlreadyExists() {
        val custom = Space("vpn.example", "main")
        val bundled = Space("bringyour.com", "main")
        val store = RecordingStore(existing = listOf(bundled, custom), active = custom)

        val install = installBundleNetworkSpace(identity, store)

        assertFalse(install.activated)
        assertSame(custom, store.active)
        assertEquals(bundled, install.networkSpace)
    }

    @Test
    fun anExistingBundledSpaceIsSelectedWhenNothingIsActive() {
        val bundled = Space("bringyour.com", "main")
        val store = RecordingStore(existing = listOf(bundled), active = null)

        val install = installBundleNetworkSpace(identity, store)

        assertTrue(install.activated)
        assertSame(bundled, store.active)
    }

    @Test
    fun noMigrationRunsWithoutADistinctLegacyHost() {
        for (legacyHostName in listOf("", "  ", "bringyour.com")) {
            val noLegacy = identity.copy(legacyHostName = legacyHostName)
            assertFalse(legacyHostName, noLegacy.migratesFromLegacyHost)

            val store = RecordingStore()
            val install = installBundleNetworkSpace(noLegacy, store)

            assertFalse(install.migratedFromLegacyHost)
            assertEquals(
                listOf("exists bringyour.com/main", "update bringyour.com/main"),
                store.calls,
            )
        }
    }

    @Test
    fun anUnavailableManagerInstallsNothing() {
        // the app's store wraps a nullable manager: every call is a no-op
        val store = object : BundleNetworkSpaceStore<Space> {
            override fun migrate(fromHostName: String, toHostName: String, envName: String) = false
            override fun exists(hostName: String, envName: String) = false
            override fun update(hostName: String, envName: String): Space? = null
            override var active: Space? = null
        }

        val install = installBundleNetworkSpace(identity, store)

        assertNull(install.networkSpace)
        assertNull(store.active)
    }
}
