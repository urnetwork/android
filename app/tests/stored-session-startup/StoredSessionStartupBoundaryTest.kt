package com.bringyour.network

import org.junit.Assert.*
import org.junit.Test

/** Behavioral assertions run against extracted production bodies, never a duplicate startup policy. */
class StoredSessionStartupBoundaryTest {
    @Test
    fun operationalFailuresRetainSavedSessionAndSettings() {
        DeviceInitFailure.entries.forEach { failure ->
            LocalState().use { store ->
                val app = MainApplicationFixture(store)
                val saved = store.savedFiles()
                val apiAuth = app.api!!.byJwt
                app.initialize = { DeviceInitResult.Failed(failure) }
                app.restore()
                assertEquals("startup must retain saved files for $failure", saved, store.savedFiles())
                assertEquals("startup must not log out for $failure", 0, store.logoutCalls)
                assertEquals(apiAuth, app.api!!.byJwt)
                assertEquals(1, app.initializationCalls)
            }
        }
    }

    @Test
    fun laterStorageReadFailureRetainsSavedSessionAndSettings() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            val apiAuth = app.api!!.byJwt
            app.initialize = {
                // The explicit transition makes the later tolerant instance read
                // fail after the owning method has observed usable credentials.
                store.available = false
                assertNull(store.instanceId)
                DeviceInitResult.Failed(DeviceInitFailure.MISSING_INSTANCE_ID)
            }
            app.restore()
            store.available = true
            assertEquals("unknown later read must retain saved files", saved, store.savedFiles())
            assertEquals("unknown later read must not log out", 0, store.logoutCalls)
            assertEquals(apiAuth, app.api!!.byJwt)
        }
    }

    @Test
    fun failedFirstReadDoesNotBecomePartialAuthenticationCleanup() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            store.failReadAt = 1
            app.restore()
            assertEquals("unknown first read must not log out", 0, store.logoutCalls)
            assertEquals(saved, store.savedFiles())
            assertEquals(0, app.initializationCalls)
            assertEquals(1, store.checkedReads)
            assertEquals(0, store.legacyReads)
            app.restore()
            assertEquals(1, app.initializationCalls)
            assertEquals(saved, store.savedFiles())
        }
    }

    @Test
    fun healthyStartupUsesOneCheckedGenerationAndTheClientCredential() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            app.restore()
            assertEquals(1, app.initializationCalls)
            assertEquals("synthetic-client-credential", app.receivedClient)
            assertEquals(1, store.checkedReads)
            assertEquals(0, store.legacyReads)
            assertEquals(saved, store.savedFiles())
        }
    }

    @Test
    fun emptyPartialAndMalformedStoredAuthenticationDoNotDeleteSettings() {
        listOf("empty", "admin-only", "client-only", "malformed-admin").forEach { kind ->
            LocalState().use { store ->
                if (kind == "empty" || kind == "admin-only") store.byClientJwt = ""
                if (kind == "empty" || kind == "client-only") store.byJwt = ""
                if (kind == "malformed-admin") store.validAdmin = false
                val app = MainApplicationFixture(store)
                val saved = store.savedFiles()
                app.restore()
                assertEquals("startup must preserve $kind", 0, store.logoutCalls)
                assertEquals(saved, store.savedFiles())
                assertEquals(0, app.initializationCalls)
            }
        }
    }

    @Test
    fun unexpectedInitializationExceptionClosesTransientDeviceAndRetainsStorage() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            app.initialize = { throw IllegalStateException("synthetic configuration failure") }
            app.restore()
            assertEquals(2, app.deviceManager.clearCalls) // startup stop, then failed partial device
            assertEquals(0, store.logoutCalls)
            assertEquals(saved, store.savedFiles())
        }
    }

    @Test
    fun explicitClientLoginFailureRetainsTheJustPersistedSession() {
        DeviceInitFailure.entries.forEach { failure ->
            LocalState().use { store ->
                val app = MainApplicationFixture(store)
                var persisted: Map<String, String>? = null
                app.initialize = {
                    persisted = store.savedFiles()
                    DeviceInitResult.Failed(failure)
                }
                val apiAuth = app.api!!.byJwt
                assertTrue(app.startClient("synthetic-new-client-credential") is ClientSessionStartResult.Failed)
                assertNotNull(persisted)
                assertEquals("failed initialization must retain the persisted login", 0, store.logoutCalls)
                assertEquals(persisted, store.savedFiles())
                assertEquals(apiAuth, app.api!!.byJwt)
            }
        }
    }

    @Test
    fun explicitClientLoginConfigurationExceptionRetainsTheJustPersistedSession() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            var persisted: Map<String, String>? = null
            app.initialize = {
                persisted = store.savedFiles()
                throw IllegalStateException("synthetic configuration failure")
            }
            val result = app.startClient("synthetic-new-client-credential")
            assertEquals(ClientSessionStartResult.Failed(LoginStartupFailure.DEVICE_LOCAL_CONFIGURATION_FAILED), result)
            assertEquals(0, store.logoutCalls)
            assertEquals(persisted, store.savedFiles())
            assertEquals(1, app.deviceManager.clearCalls)
        }
    }

    @Test
    fun healthyExplicitClientLoginStillReturnsReady() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            assertSame(ClientSessionStartResult.Ready, app.startClient("synthetic-new-client-credential"))
            assertEquals("synthetic-new-client-credential", store.byClientJwt)
            assertEquals("synthetic-new-client-credential", app.receivedClient)
            assertEquals(1, app.initializationCalls)
            assertEquals(0, store.logoutCalls)
        }
    }

    @Test
    fun explicitLogoutStillClearsCredentialsSettingsAndApiAuthentication() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.logout()
            assertEquals(1, store.logoutCalls)
            assertTrue(store.savedFiles().isEmpty())
            assertNull(app.api!!.byJwt)
            assertEquals(1, app.loginStartupTracker.logoutCalls)
        }
    }

    @Test
    fun deliveredCurrentSdkRejectionStillReachesLogoutCleanup() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            assertNotNull(app.deviceManager.onAuthLogout)
            app.deviceManager.onAuthLogout!!.invoke(app.device!!, Any())
            assertEquals(1, store.logoutCalls)
            assertTrue(store.savedFiles().isEmpty())
            assertNull(app.api!!.byJwt)
            assertEquals(1, app.loginStartupTracker.authLogoutCalls)
        }
    }

    @Test
    fun failedLoginPersistenceRetainsSessionSettingsAndDeviceKey() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            store.adminWriteFailure = true
            assertFalse(app.login("synthetic-new-admin"))
            assertEquals(0, store.logoutCalls)
            assertEquals(saved, store.savedFiles())
        }
    }
}
