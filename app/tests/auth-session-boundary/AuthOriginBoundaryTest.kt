package com.bringyour.network

import android.os.Handler
import android.os.Looper
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Deterministic application-boundary ownership tests; queues are stepped, never slept. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthOriginBoundaryTest {
    private fun drain() { Looper.isMain = true; while (Handler.queue.isNotEmpty()) Handler.queue.removeFirst().invoke() }
    private fun clientJwt() = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(
        """{"client_id":"synthetic-client"}""".toByteArray()) + ".signature"

    @Test fun heldOuterResultCannotMintOwnershipInAnotherSpace() {
        LocalState().use { original -> LocalState().use { replacement ->
            val app = MainApplicationFixture(original)
            app.restore()
            val request = app.beginLoginRequest()!!
            val heldResult = { app.authenticateNetworkSession(request, "old-result", false) {} }
            val next = NetworkSpace(AsyncLocalState(replacement))
            app.replaceSpace(next)
            val saved = replacement.savedFiles()
            val api = next.api!!.byJwt
            heldResult()
            assertEquals(saved, replacement.savedFiles())
            assertEquals(api, next.api.byJwt)
            assertNull(app.clientReply)
        } }
    }

    @Test fun sameBytesNewRequestRetiresHeldOuterResult() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val old = app.beginLoginRequest()!!
            assertTrue(app.login("synthetic-admin-credential"))
            val current = app.loginStartupState.value
            val saved = store.savedFiles()
            app.authenticateNetworkSession(old, "retired-result", false) {}
            assertEquals(saved, store.savedFiles())
            assertSame(current, app.loginStartupState.value)
            assertNull(app.clientReply)
        }
    }

    @Test fun queuedResultRechecksItsRequestOnMain() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            var deliveries = 0
            Looper.isMain = false
            app.dispatchLoginResult(request) { deliveries += 1 }
            Looper.isMain = true
            app.beginLoginRequest()
            drain()
            assertEquals(0, deliveries)
        }
    }

    @Test fun delayedSessionHelperCannotRebindAtDispatch() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            Looper.isMain = false
            app.authenticateNetworkSession(request, "queued-admin", false) {}
            Looper.isMain = true
            app.login("new-owner")
            val saved = store.savedFiles()
            drain()
            assertEquals(saved, store.savedFiles())
            assertNull(app.clientReply)
        }
    }

    @Test fun delayedClientHelperRejectsSupersededOwner() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.persistNetworkSession(request, "old-admin", false) {}
            app.beginLoginRequest()
            var completion: LoginClientCompletion? = null
            app.authenticateLoginClient(request) { completion = it }
            assertEquals(LoginClientCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT), completion)
            assertNull(app.clientReply)
        }
    }

    @Test fun staleInstantPersistenceCannotWriteNewOwnerButStillAnswersItsCaller() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.login("current-admin")
            val saved = store.savedFiles()
            var persisted: Boolean? = null
            app.persistNetworkSession(request, "retired-one-time-admin", true) { persisted = it }
            assertEquals(false, persisted)
            assertEquals(saved, store.savedFiles())
        }
    }

    @Test fun authoritativeSelectionRetiresResultsBeforeSelectionQueueDrains() {
        LocalState().use { original -> LocalState().use { replacement ->
            val app = MainApplicationFixture(original)
            val request = app.beginLoginRequest()!!
            app.networkSpaceManager.activeNetworkSpace = NetworkSpace(AsyncLocalState(replacement))
            val saved = original.savedFiles()
            app.authenticateNetworkSession(request, "old-result", false) {}
            assertEquals(saved, original.savedFiles())
            assertNull(app.beginLoginRequest())
        } }
    }

    @Test fun staleQueuedSelectionCannotRestoreAnEarlierSpace() {
        LocalState().use { original -> LocalState().use { replacement ->
            val app = MainApplicationFixture(original)
            val next = NetworkSpace(AsyncLocalState(replacement))
            app.selectedCallback(app.space.wrapper())
            app.networkSpaceManager.activeNetworkSpace = next
            assertTrue(app.selectedNow(next.wrapper()))
            val accepted = app.networkSpaceManagerProvider.current
            drain()
            assertSame(accepted, app.networkSpaceManagerProvider.current)
            assertTrue(app.networkSpaceManagerProvider.current!!.same(next))
        } }
    }

    @Test fun duplicateNativeSpaceWrapperPreservesPendingAttemptAndServingDevice() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val serving = app.device
            val request = app.beginLoginRequest()!!
            val attempt = app.loginStartupState.value
            val clearCalls = app.deviceManager.clearCalls
            assertFalse(app.selectedNow(app.space.wrapper()))
            assertSame(attempt, app.loginStartupState.value)
            assertSame(serving, app.device)
            assertEquals(clearCalls, app.deviceManager.clearCalls)
            assertTrue(app.isCurrentLoginRequest(request))
        }
    }

    @Test fun equalKeyDifferentNativeSpaceReplacesAndRetiresOldRequest() {
        LocalState().use { original -> LocalState().use { replacement ->
            val app = MainApplicationFixture(original)
            val request = app.beginLoginRequest()!!
            val next = NetworkSpace(AsyncLocalState(replacement), key = app.space.key)
            app.networkSpaceManager.activeNetworkSpace = next
            assertTrue(app.selectedNow(next.wrapper()))
            assertFalse(app.isCurrentLoginRequest(request))
            assertTrue(app.networkSpaceManagerProvider.current!!.same(next))
        } }
    }

    @Test fun failedAdminWriteDoesNotRunTolerantKeyCleanup() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val serving = app.device
            val saved = store.savedFiles()
            val api = app.api!!.byJwt
            store.adminWriteFailure = true
            store.keyReadFailure = true
            assertFalse(app.login("replacement-admin"))
            assertEquals(saved, store.savedFiles())
            assertEquals(api, app.api!!.byJwt)
            assertSame(serving, app.device)
            assertEquals(0, store.logoutCalls)
            assertEquals(0, store.keyClears)
        }
    }

    @Test fun emptyAdminInputDoesNotEraseHealthySavedState() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val saved = store.savedFiles()
            assertFalse(app.login(""))
            assertEquals(saved, store.savedFiles())
            assertEquals(0, store.logoutCalls)
        }
    }

    @Test fun healthyOwnedResponsePersistsAndBecomesReady() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            var completion: LoginClientCompletion? = null
            app.dispatchLoginResult(request) {
                app.authenticateNetworkSession(request, "accepted-admin", true) { completion = it }
            }
            app.clientReply!!(AuthClientWireResult("synthetic-client", clientJwt()), null)
            drain()
            assertEquals(LoginClientCompletion.Ready("synthetic-client"), completion)
            assertTrue(app.isCurrentLoginRequest(request))
            assertEquals("accepted-admin", store.byJwt)
            assertEquals(clientJwt(), store.byClientJwt)
            assertTrue(store.canPromptIntroFunnel)
            assertNotNull(app.device)
        }
    }

    @Test fun delayedReadyNavigationRetainsOwnerButCannotOutliveNextAttempt() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.authenticateNetworkSession(request, "accepted-admin", false) {}
            app.clientReply!!(AuthClientWireResult("synthetic-client", clientJwt()), null)
            assertTrue(app.isCurrentLoginRequest(request))
            app.beginLoginRequest()
            assertFalse(app.isCurrentLoginRequest(request))
        }
    }

    @Test fun currentFailureCanShowItsErrorAndCannotStartAnotherStage() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            store.adminWriteFailure = true
            app.authenticateNetworkSession(request, "failed-admin", false) {}
            assertTrue(app.ownsLoginRequest(request))
            assertFalse(app.isCurrentLoginRequest(request))
            var delivered = false
            app.dispatchLoginResult(request) { delivered = true }
            assertFalse(delivered)
        }
    }

    @Test fun explicitLogoutRetiresHeldRequestAndStillDeletesCredentials() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.logout()
            app.authenticateNetworkSession(request, "resurrected-admin", false) {}
            assertTrue(store.savedFiles().isEmpty())
            assertEquals(1, store.logoutCalls)
            assertNull(app.api!!.byJwt)
        }
    }
    @Test fun duplicateQueuedOuterResultAdmitsOneSessionAndOneUiEffect() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            var uiEffects = 0
            Looper.isMain = false
            repeat(2) {
                app.dispatchLoginResult(request) {
                    uiEffects += 1
                    app.authenticateNetworkSession(request, "accepted-admin", false) {}
                }
            }
            drain()
            assertEquals(1, uiEffects)
            assertEquals(1, app.clientRequests)
        }
    }

    @Test fun duplicateDirectHelperCannotAllocateASecondClient() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            Looper.isMain = false
            repeat(2) { app.authenticateNetworkSession(request, "accepted-admin", false) {} }
            drain()
            assertEquals(1, app.clientRequests)
        }
    }

    @Test fun nestedOwnedParseContinuesOnceAdmittedOuterResult() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.dispatchLoginResult(request) {
                app.dispatchLoginContinuation(request) {
                    app.authenticateNetworkSession(request, "accepted-admin", false) {}
                }
            }
            assertEquals(1, app.clientRequests)
        }
    }

    @Test fun nestedParseAfterReplacementCannotPersistOldResult() {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            app.dispatchLoginResult(request) {}
            app.login("replacement-admin")
            val saved = store.savedFiles()
            app.dispatchLoginContinuation(request) { app.authenticateNetworkSession(request, "old-admin", false) {} }
            assertEquals(saved, store.savedFiles())
            assertEquals(0, app.clientRequests)
        }
    }

    @Test fun realWelcomeDelayRejectsSupersededReadyRequest() = withVirtualMain { scheduler ->
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val request = app.beginLoginRequest()!!
            val activity = LoginActivity(app)
            activity.finishAuthenticatedLoginAfterWelcome(request)
            scheduler.runCurrent()
            app.beginLoginRequest()
            scheduler.advanceUntilIdle()
            assertEquals(0, activity.finishes)
        }
    }

    @Test fun realQueuedFinishRejectsLogoutAndHealthyWelcomeStillFinishes() = withVirtualMain { scheduler ->
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val activity = LoginActivity(app)
            val old = app.beginLoginRequest()!!
            activity.finishAuthenticatedLoginNow(old)
            app.logout()
            scheduler.runCurrent()
            assertEquals(0, activity.finishes)
            val current = app.beginLoginRequest()!!
            activity.finishAuthenticatedLoginAfterWelcome(current)
            scheduler.advanceUntilIdle()
            assertEquals(1, activity.finishes)
        }
    }

    @Test fun actualInstantResultPreservesOneTimePhraseAfterStalePersistence() = withVirtualMain { scheduler ->
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val instant = InstantFixture(app.networkSpaceManagerProvider)
            instant.createOwned(app.beginLoginRequest()!!) { _, _, _, complete -> complete(false) }
            app.api!!.createReply!!(NetworkCreateResult("synthetic-recovery-phrase", CreatedNetwork("created-admin")), null)
            scheduler.advanceUntilIdle()
            assertEquals("synthetic-recovery-phrase", instant._seedphrase.value)
            assertNotNull(instant._error.value)
        }
    }

    @Test fun actualInstantResultSurvivesClearedViewModelAndIgnoresDuplicateReply() = withVirtualMain { scheduler ->
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            val instant = InstantFixture(app.networkSpaceManagerProvider)
            var persists = 0
            instant.createOwned(app.beginLoginRequest()!!) { _, _, _, complete -> persists += 1; complete(true) }
            instant.viewModelScope.cancel()
            repeat(2) { app.api!!.createReply!!(NetworkCreateResult("synthetic-recovery-phrase", CreatedNetwork("created-admin")), null) }
            scheduler.advanceUntilIdle()
            assertEquals(1, persists)
            assertEquals("synthetic-recovery-phrase", instant._seedphrase.value)
            assertFalse(instant._inProgress.value)
        }
    }

    @Test fun acceptedAdminSurvivesOldDeviceRejectionWhenLaterSettingsWriteFails() = acceptedAdminFailure("settings")
    @Test fun acceptedAdminSurvivesOldDeviceRejectionWhenLaterApiUpdateFails() = acceptedAdminFailure("api")
    private fun acceptedAdminFailure(stage: String) {
        LocalState().use { store ->
            val app = MainApplicationFixture(store)
            app.restore()
            val serving = app.device!!
            serving.authListeners.single().invoke()
            if (stage == "settings") store.introWriteFailure = true else app.api!!.authWriteFailure = true
            assertFalse(app.login("accepted-replacement"))
            store.introWriteFailure = false
            app.api!!.authWriteFailure = false
            val accepted = store.savedFiles()
            drain()
            assertEquals(accepted, store.savedFiles())
            assertEquals("accepted-replacement", store.byJwt)
            assertEquals(0, store.logoutCalls)
            assertSame(serving, app.device)
        }
    }

    private fun withVirtualMain(test: (TestCoroutineScheduler) -> Unit) {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        try { test(scheduler) } finally { Dispatchers.resetMain() }
    }

}
