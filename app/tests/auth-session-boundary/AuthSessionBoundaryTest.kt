package com.bringyour.network

import android.os.Handler
import android.os.Looper
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** Forces the production application, manager and coordinator orderings with inert SDK calls. */
class AuthSessionBoundaryTest {
    private fun drain() { while (Handler.queue.isNotEmpty()) Handler.queue.removeFirst().invoke() }
    private fun reject(device: DeviceLocal) { device.authListeners.single().invoke() }
    private fun clientJwt(): String = listOf("{}", "{\"client_id\":\"synthetic-client\"}", "synthetic-signature")
        .joinToString(".") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }

    private fun assertRetained(app: MainApplicationFixture, store: LocalState, saved: Map<String, String>, api: String?) {
        assertEquals("queued old rejection erased saved authentication/settings", saved, store.savedFiles())
        assertEquals(0, store.logoutCalls)
        assertEquals(api, app.api!!.byJwt)
        assertEquals(0, app.navigations)
        assertEquals(0, app.signInNotices.deliveries)
    }

    @Test
    fun queuedRejectionCannotEraseReplacementDevice() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            reject(old)
            assertEquals(1, Handler.queue.size)
            assertTrue(app.login("synthetic-new-admin"))
            assertSame(ClientSessionStartResult.Ready, app.startClient(clientJwt()))
            val replacement = app.device!!
            assertNotSame(old, replacement)
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            drain()
            assertRetained(app, store, saved, api)
            assertSame(replacement, app.device)
            assertEquals("synthetic-route", replacement.connectLocation)
        }
    }

    @Test
    fun acceptedLoginBeforeDeviceReplacementRetiresQueuedRejection() =
        checkAcceptedLogin("synthetic-new-admin")

    @Test
    fun equalByteAcceptedLoginStillRetiresQueuedRejection() =
        checkAcceptedLogin("synthetic-admin-credential")

    private fun checkAcceptedLogin(jwt: String) {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            reject(old)
            assertTrue(app.login(jwt))
            if (jwt != "synthetic-admin-credential") {
                assertEquals("", store.byClientJwt)
                assertNull(store.instanceId)
            }
            assertSame("test must retain the old device through the acceptance gap", old, app.device)
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            drain()
            assertRetained(app, store, saved, api)
        }
    }

    @Test
    fun directAcceptedClientLoginRetiresRejectionBeforeConstructorPublication() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            reject(old)
            app.loginStartupTracker.begin(LoginStartupStage.AUTH_CLIENT)
            var savedAtConstruction: Map<String, String>? = null
            var savedAfterRejection: Map<String, String>? = null
            Sdk.factories[app.space] = { key ->
                assertSame(old, app.device)
                savedAtConstruction = store.savedFiles()
                drain()
                savedAfterRejection = store.savedFiles()
                DeviceLocal(app.space, key!!)
            }
            assertSame(ClientSessionStartResult.Ready, app.startClient(clientJwt()))
            assertNotNull(savedAtConstruction)
            assertEquals("old rejection ran between persistence and device publication", savedAtConstruction, savedAfterRejection)
            assertEquals(0, store.logoutCalls)
        }
    }

    @Test
    fun delayedSdkCallbackRetainsItsOriginalSessionAfterHandlersAreReplaced() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            val admittedOldCallback = old.authListeners.single()
            assertTrue(app.login("synthetic-new-admin"))
            app.startClient(clientJwt())
            val replacement = app.device!!
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            admittedOldCallback()
            drain()
            assertRetained(app, store, saved, api)
            assertSame(replacement, app.device)
        }
    }

    @Test
    fun stopRetiresQueuedRejectionBeforeReentrantTeardown() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            reject(app.device!!)
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            app.stopHook = { app.stopHook = {}; drain() }
            app.stop()
            assertRetained(app, store, saved, api)
        }
    }

    @Test
    fun pendingPasswordRequestDoesNotRetireCurrentServingRejection() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            app.beginPassword()
            reject(app.device!!)
            drain()
            assertEquals(1, store.logoutCalls)
            assertTrue(store.savedFiles().isEmpty())
            assertNull(app.api!!.byJwt)
            assertEquals(1, app.navigations)
            assertEquals("synthetic-revocation", app.signInNotices.cause)
            assertEquals(LoginStartupFailure.AUTH_LOGOUT, (app.loginStartupState.value as LoginStartupState.Failed).failure)
        }
    }

    @Test
    fun restoredCurrentSessionRejectionStillLogsOutExactlyOnce() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val current = app.device!!
            reject(current)
            reject(current)
            drain()
            assertEquals(1, store.logoutCalls)
            assertEquals(1, app.navigations)
            assertEquals(1, app.signInNotices.deliveries)
            assertEquals("synthetic-revocation", app.signInNotices.cause)
            assertTrue(store.savedFiles().isEmpty())
            assertNull(app.api!!.byJwt)
        }
    }

    @Test
    fun failedClientPersistenceDoesNotRetireCurrentServingRejection() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            app.loginStartupTracker.begin(LoginStartupStage.AUTH_CLIENT)
            store.clientWriteFailure = true
            assertEquals(ClientSessionStartResult.Failed(LoginStartupFailure.CLIENT_JWT_PERSISTENCE_FAILED), app.startClient(clientJwt()))
            assertSame(old, app.device)
            reject(old)
            drain()
            assertEquals(1, store.logoutCalls)
            assertNull(app.api!!.byJwt)
            assertEquals(1, app.navigations)
        }
    }

    @Test
    fun immediateFenceCancelsOldConnectButReplacementConnectRemainsUsable() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val old = app.device!!
            var connects = 0
            var disconnects = 0
            assertTrue(app.connectCommands.run(old, true) { connects += 1 })
            reject(old)
            assertFalse(app.connectCommands.run(old, true) { connects += 1 })
            assertTrue(app.connectCommands.run(old, false) { disconnects += 1 })
            app.login("synthetic-new-admin")
            app.startClient(clientJwt())
            assertTrue(app.connectCommands.run(app.device!!, true) { connects += 1 })
            while (app.commands.isNotEmpty()) app.commands.removeFirst().run()
            drain()
            assertEquals(1, connects)
            assertEquals(1, disconnects)
            assertEquals(0, store.logoutCalls)
        }
    }

    @Test
    fun explicitLogoutMakesAlreadyQueuedRejectionANoop() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            reject(app.device!!)
            app.logout()
            drain()
            assertEquals(1, store.logoutCalls)
            assertEquals(0, app.navigations)
            assertNull(app.signInNotices.cause)
            assertTrue(store.savedFiles().isEmpty())
            assertNull(app.api!!.byJwt)
        }
    }

    @Test
    fun heldPasswordReplyCannotWriteIntoReplacementSpace() {
        LocalState().use { store -> LocalState().use { next ->
            val app = MainApplicationFixture(store)
            app.restore()
            var completion: PasswordLoginCompletion? = null
            app.beginPassword { completion = it }
            val reply = app.passwordReply!!
            next.byClientJwt = ""
            val replacement = NetworkSpace(AsyncLocalState(next))
            val saved = next.savedFiles()
            app.replaceSpace(replacement)
            reply(PasswordAuthWireResult(byJwt = "synthetic-old-space-admin"), null)
            drain()
            assertEquals(saved, next.savedFiles())
            assertEquals("synthetic-api-credential", replacement.api!!.byJwt)
            assertEquals(PasswordLoginCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT), completion)
            assertNull(app.clientReply)
        } }
    }

    @Test
    fun queuedPasswordReplyIsRetiredBeforeSpaceTeardown() {
        LocalState().use { store -> LocalState().use { next ->
            val app = MainApplicationFixture(store)
            app.restore()
            var completion: PasswordLoginCompletion? = null
            app.beginPassword { completion = it }
            Looper.isMain = false
            app.passwordReply!!(PasswordAuthWireResult(byJwt = "synthetic-old-space-admin"), null)
            Looper.isMain = true
            assertEquals(1, Handler.queue.size)
            next.byClientJwt = ""
            val savedOld = store.savedFiles()
            val savedNext = next.savedFiles()
            app.stopHook = { app.stopHook = {}; drain() }
            app.replaceSpace(NetworkSpace(AsyncLocalState(next)))
            assertEquals("retirement must precede reentrant teardown", savedOld, store.savedFiles())
            assertEquals(savedNext, next.savedFiles())
            assertEquals(PasswordLoginCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT), completion)
        } }
    }

    @Test
    fun heldAndQueuedClientRepliesCannotWriteIntoReplacementSpace() {
        listOf(false, true).forEach { queued ->
            LocalState().use { store -> LocalState().use { next ->
                val app = MainApplicationFixture(store)
                app.restore()
                app.beginPassword()
                app.passwordReply!!(PasswordAuthWireResult(byJwt = "synthetic-accepted-admin"), null)
                val reply = app.clientReply!!
                var allocations = 0
                app.loginStartupTracker.addAllocationListener { allocations += 1 }
                val result = AuthClientWireResult("synthetic-client", clientJwt())
                if (queued) {
                    Looper.isMain = false
                    reply(result, null)
                    Looper.isMain = true
                }
                next.byClientJwt = ""
                val saved = next.savedFiles()
                app.replaceSpace(NetworkSpace(AsyncLocalState(next)))
                if (!queued) reply(result, null)
                drain()
                assertEquals(saved, next.savedFiles())
                assertEquals(0, next.keyWrites)
                assertNull(app.device)
                assertEquals("late allocations must still be offered to their cleanup observer", 1, allocations)
            } }
        }
    }

    @Test
    fun newPasswordLoginAfterSpaceReplacementStillReachesReady() {
        LocalState().use { store -> LocalState().use { next ->
            val app = MainApplicationFixture(store)
            app.restore()
            app.beginPassword()
            val oldReply = app.passwordReply!!
            next.byClientJwt = ""
            val replacement = NetworkSpace(AsyncLocalState(next))
            Sdk.factories[replacement] = { key -> DeviceLocal(replacement, key!!) }
            app.replaceSpace(replacement)
            var completion: PasswordLoginCompletion? = null
            app.beginPassword { completion = it }
            val currentReply = app.passwordReply!!
            oldReply(PasswordAuthWireResult(byJwt = "synthetic-retired-admin"), null)
            currentReply(PasswordAuthWireResult(byJwt = "synthetic-replacement-admin"), null)
            app.clientReply!!(AuthClientWireResult("synthetic-client", clientJwt()), null)
            drain()
            assertEquals(PasswordLoginCompletion.Ready("synthetic-client"), completion)
            assertTrue(app.loginStartupState.value is LoginStartupState.Ready)
            assertEquals("synthetic-replacement-admin", next.byJwt)
            assertEquals(clientJwt(), next.byClientJwt)
            assertSame(replacement, app.device!!.networkSpace)
            assertEquals(0, next.logoutCalls)
        } }
    }

    @Test
    fun constructorExceptionNeverClearsOrRetriesStoredIdentity() = checkConstructorFailure(true)

    @Test
    fun constructorNullNeverClearsOrRetriesStoredIdentity() = checkConstructorFailure(false)

    private fun checkConstructorFailure(throws: Boolean) {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val calls = mutableListOf<String?>()
            val saved = store.savedFiles()
            Sdk.factories[app.space] = { key ->
                calls += key
                if (key == null) DeviceLocal(app.space, "synthetic-wrong-new-key")
                else if (throws) error("synthetic peer-pin storage failure") else null
            }
            val result = app.deviceManager.initDevice(app.space, clientJwt(), "synthetic-description", "synthetic-spec")
            assertEquals(DeviceInitResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CREATION_FAILED), result)
            assertEquals(listOf("synthetic-device-key"), calls)
            assertEquals(saved, store.savedFiles())
            assertEquals(0, store.keyWrites)
            assertEquals(0, store.keyClears)
            assertNull(app.device)
        }
    }

    @Test
    fun checkedKeyReadFailureCannotBecomeFreshConstructionOrWrite() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            store.keyReadFailure = true
            var constructors = 0
            Sdk.factories[app.space] = { key -> constructors += 1; DeviceLocal(app.space, key ?: "synthetic-new-key") }
            app.restore()
            assertEquals(0, constructors)
            assertEquals(1, store.keyCheckedReads)
            assertEquals(0, store.keyWrites)
            assertEquals(saved, store.savedFiles())
            assertEquals(0, store.logoutCalls)
            store.keyReadFailure = false
            app.restore()
            assertEquals(1, constructors)
            assertEquals("synthetic-device-key", app.device!!.keyMaterial)
        }
    }

    @Test
    fun savedStartupAfterConstructorFailureRetriesOriginalKey() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            val calls = mutableListOf<String?>()
            Sdk.factories[app.space] = { key -> calls += key; error("synthetic peer-pin storage failure") }
            app.restore()
            assertEquals(saved, store.savedFiles())
            assertEquals(0, store.logoutCalls)
            assertNull(app.device)
            Sdk.factories[app.space] = { key -> calls += key; DeviceLocal(app.space, key!!) }
            app.restore()
            assertEquals(listOf("synthetic-device-key", "synthetic-device-key"), calls)
            assertEquals("synthetic-device-key", app.device!!.keyMaterial)
            assertEquals(saved, store.savedFiles())
        }
    }

    @Test
    fun acceptedLoginConstructorFailureRetainsNewCredentialsAndOriginalKey() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            reject(app.device!!)
            app.login("synthetic-new-admin")
            Sdk.factories[app.space] = { error("synthetic construction failure") }
            assertEquals(ClientSessionStartResult.Failed(LoginStartupFailure.DEVICE_LOCAL_CREATION_FAILED), app.startClient(clientJwt()))
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            drain()
            assertRetained(app, store, saved, api)
            assertEquals("synthetic-device-key", store.deviceLocalKeyMaterial)
            assertEquals("synthetic-new-admin", store.byJwt)
            assertEquals(clientJwt(), store.byClientJwt)
        }
    }

    @Test
    fun healthyMissingAndStoredKeyConstructionRemainUsable() {
        listOf(false, true).forEach { missing ->
            LocalState().use { store ->
                if (missing) store.deviceLocalKeyMaterial = null
                val app = MainApplicationFixture(store)
                val calls = mutableListOf<String?>()
                Sdk.factories[app.space] = { key -> calls += key; DeviceLocal(app.space, key ?: "synthetic-first-install-key") }
                app.restore()
                val expected = if (missing) "synthetic-first-install-key" else "synthetic-device-key"
                assertEquals(listOf(if (missing) null else expected), calls)
                assertEquals(expected, app.device!!.keyMaterial)
                assertEquals(expected, store.deviceLocalKeyMaterial)
                assertEquals(0, store.logoutCalls)
                assertEquals("synthetic-route", app.device!!.connectLocation)
            }
        }
    }
}
