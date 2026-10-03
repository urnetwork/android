package com.bringyour.network

import com.bringyour.network.ui.login.VerifySendError
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginStartupStateTest {
    private val clientId = "00000000-0000-0000-0000-000000000008"

    private fun jwt(id: String = clientId): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("{}", "{\"client_id\":\"$id\"}", "signature")
            .joinToString(".") { encoder.encodeToString(it.toByteArray()) }
    }

    @Test
    fun `password and network persistence failures are typed terminal states`() {
        listOf(
            LoginStartupStage.PASSWORD_AUTH to LoginStartupFailure.PASSWORD_AUTH_REJECTED,
            LoginStartupStage.NETWORK_SESSION_PERSISTENCE to
                LoginStartupFailure.NETWORK_SESSION_PERSISTENCE_FAILED,
        ).forEach { (stage, failure) ->
            val tracker = LoginStartupTracker()
            val attempt = tracker.begin(stage)

            assertTrue(tracker.fail(attempt, stage, failure))
            val state = tracker.state.value as LoginStartupState.Failed
            assertEquals(stage, state.stage)
            assertEquals(failure, state.failure)
            assertTrue(state.terminal)
            assertFalse(tracker.fail(attempt, stage, failure))
        }
    }

    @Test
    fun `password result reaches ready after UI observer detaches`() {
        val tracker = LoginStartupTracker()
        val requester = DeferredPasswordRequester()
        val dispatches = ArrayDeque<() -> Unit>()
        val uiCompletions = mutableListOf<PasswordLoginCompletion>()
        var uiAttached = true
        val coordinator = PasswordLoginCoordinator(
            tracker = tracker,
            requester = requester,
            sessionAuthenticator = NetworkSessionAuthenticator { _, newNetwork, completion ->
                assertFalse(newNetwork)
                val attemptId = tracker.state.value.attemptId ?: error("missing attempt")
                assertTrue(
                    tracker.pending(attemptId, LoginStartupStage.NETWORK_SESSION_PERSISTENCE),
                )
                assertTrue(tracker.pending(attemptId, LoginStartupStage.AUTH_CLIENT))
                assertTrue(tracker.ready(attemptId, clientId))
                completion(LoginClientCompletion.Ready(clientId))
            },
            dispatch = { dispatches.addLast(it) },
        )

        val attemptId = coordinator.authenticate("user@example.com", "password") {
            if (uiAttached) uiCompletions += it
        }
        uiAttached = false
        requester.complete(PasswordAuthWireResult(byJwt = "network-jwt"), null)

        assertEquals(
            LoginStartupState.Pending(attemptId, LoginStartupStage.PASSWORD_AUTH),
            tracker.state.value,
        )
        dispatches.removeFirst().invoke()
        assertEquals(LoginStartupState.Ready(attemptId, clientId), tracker.state.value)
        assertTrue(uiCompletions.isEmpty())
    }

    @Test
    fun `password session callback and exception complete once`() {
        val tracker = LoginStartupTracker()
        val completions = mutableListOf<PasswordLoginCompletion>()
        val coordinator = PasswordLoginCoordinator(
            tracker = tracker,
            requester = PasswordAuthRequester { _, _, callback ->
                callback(PasswordAuthWireResult(byJwt = "network-jwt"), null)
            },
            sessionAuthenticator = NetworkSessionAuthenticator { _, _, completion ->
                val attemptId = tracker.state.value.attemptId ?: error("missing attempt")
                assertTrue(tracker.ready(attemptId, clientId))
                completion(LoginClientCompletion.Ready(clientId))
                completion(
                    LoginClientCompletion.Failed(LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED),
                )
                error("exception after session callback")
            },
            dispatch = { it() },
        )
        val attemptId = coordinator.authenticate("user@example.com", "password") {
            completions += it
        }

        assertEquals(listOf(PasswordLoginCompletion.Ready(clientId)), completions)
        assertEquals(LoginStartupState.Ready(attemptId, clientId), tracker.state.value)
    }

    @Test
    fun `password response boundaries publish exact failures`() {
        val cases = listOf(
            PasswordCase(
                result = null,
                requestError = "offline",
                failure = LoginStartupFailure.PASSWORD_AUTH_REQUEST_FAILED,
            ),
            PasswordCase(
                result = null,
                requestError = null,
                failure = LoginStartupFailure.PASSWORD_AUTH_RESULT_INVALID,
            ),
            PasswordCase(
                result = PasswordAuthWireResult(
                    failure = LoginStartupFailure.PASSWORD_AUTH_REJECTED,
                    failureMessage = "rejected",
                ),
                requestError = null,
                failure = LoginStartupFailure.PASSWORD_AUTH_REJECTED,
            ),
            PasswordCase(
                result = PasswordAuthWireResult(),
                requestError = null,
                failure = LoginStartupFailure.PASSWORD_AUTH_RESULT_INVALID,
            ),
        )

        cases.forEach { case ->
            val tracker = LoginStartupTracker()
            var completion: PasswordLoginCompletion? = null
            PasswordLoginCoordinator(
                tracker = tracker,
                requester = PasswordAuthRequester { _, _, callback ->
                    callback(case.result, case.requestError)
                },
                sessionAuthenticator = NetworkSessionAuthenticator { _, _, _ ->
                    error("must not authenticate session")
                },
                dispatch = { it() },
            ).authenticate("user@example.com", "password") { completion = it }

            assertEquals(
                case.failure,
                (completion as PasswordLoginCompletion.Failed).failure,
            )
            assertEquals(
                case.failure,
                (tracker.state.value as LoginStartupState.Failed).failure,
            )
        }
    }

    @Test
    fun `password requester exception is terminal without a callback`() {
        val tracker = LoginStartupTracker()
        var completion: PasswordLoginCompletion? = null
        PasswordLoginCoordinator(
            tracker = tracker,
            requester = PasswordAuthRequester { _, _, _ -> error("request panic") },
            sessionAuthenticator = NetworkSessionAuthenticator { _, _, _ ->
                error("must not authenticate session")
            },
            dispatch = { it() },
        ).authenticate("user@example.com", "password") { completion = it }

        assertEquals(
            LoginStartupFailure.PASSWORD_AUTH_REQUEST_FAILED,
            (completion as PasswordLoginCompletion.Failed).failure,
        )
        assertEquals(
            LoginStartupFailure.PASSWORD_AUTH_REQUEST_FAILED,
            (tracker.state.value as LoginStartupState.Failed).failure,
        )
    }

    @Test
    fun `password verification response does not require a network result`() {
        val tracker = LoginStartupTracker()
        var completion: PasswordLoginCompletion? = null
        PasswordLoginCoordinator(
            tracker = tracker,
            requester = PasswordAuthRequester { _, _, callback ->
                callback(
                    PasswordAuthWireResult(verificationUserAuth = "verified@example.com"),
                    null,
                )
            },
            sessionAuthenticator = NetworkSessionAuthenticator { _, _, _ ->
                error("must not authenticate session")
            },
            dispatch = { it() },
        ).authenticate("user@example.com", "password") { completion = it }

        assertEquals(
            PasswordLoginCompletion.VerificationRequired("verified@example.com"),
            completion,
        )
    }

    @Test
    fun `password verification response carries the send error to the verify screen`() {
        val sendError = VerifySendError(
            code = "verify_rate_limited",
            message = "Too many recent sign-in attempts. Please try again in 5 minutes.",
            retryAfterSeconds = 300,
        )
        var completion: PasswordLoginCompletion? = null
        PasswordLoginCoordinator(
            tracker = LoginStartupTracker(),
            requester = PasswordAuthRequester { _, _, callback ->
                callback(
                    PasswordAuthWireResult(
                        verificationUserAuth = "verified@example.com",
                        verificationSendError = sendError,
                    ),
                    null,
                )
            },
            sessionAuthenticator = NetworkSessionAuthenticator { _, _, _ ->
                error("must not authenticate session")
            },
            dispatch = { it() },
        ).authenticate("user@example.com", "password") { completion = it }

        assertEquals(
            PasswordLoginCompletion.VerificationRequired("verified@example.com", sendError),
            completion,
        )
    }

    @Test
    fun `allocation listeners replay once and stop after removal`() {
        val tracker = LoginStartupTracker()
        val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        tracker.recordAllocation(attempt, clientId)
        val allocations = mutableListOf<String>()
        val remove = tracker.addAllocationListener { allocations += it.clientId }

        tracker.recordAllocation(attempt, clientId)
        remove()
        tracker.recordAllocation(
            tracker.begin(LoginStartupStage.AUTH_CLIENT),
            "00000000-0000-0000-0000-000000000009",
        )

        assertEquals(listOf(clientId), allocations)
    }

    @Test
    fun `allocation replay retires completed attempts without losing live ownership`() {
        val tracker = LoginStartupTracker()
        val liveAllocations = mutableListOf<LoginClientAllocation>()
        tracker.addAllocationListener { liveAllocations += it }

        repeat(128) { index ->
            val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
            tracker.recordAllocation(attempt, "client-$index")
        }

        val replayedAllocations = mutableListOf<LoginClientAllocation>()
        tracker.addAllocationListener { replayedAllocations += it }
        assertEquals(128, liveAllocations.size)
        assertEquals(listOf(liveAllocations.last()), replayedAllocations)
    }

    @Test
    fun `allocation is owned before client persistence and ready`() {
        val tracker = LoginStartupTracker()
        val attempt = tracker.begin(LoginStartupStage.PASSWORD_AUTH)
        val order = mutableListOf<String>()
        tracker.addAllocationListener { order += "own:${it.clientId}" }
        val coordinator = coordinator(
            tracker,
            AuthClientWireResult(clientId, jwt()),
            start = {
                order += "start"
                ClientSessionStartResult.Ready
            },
        )
        var completion: LoginClientCompletion? = null

        coordinator.authenticate(attempt) { completion = it }

        assertEquals(listOf("own:$clientId", "start"), order)
        assertEquals(LoginClientCompletion.Ready(clientId), completion)
        assertEquals(LoginStartupState.Ready(attempt, clientId), tracker.state.value)
    }

    @Test
    fun `auth client response boundaries publish exact failures`() {
        val cases = listOf(
            AuthCase(null, "offline", LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED),
            AuthCase(null, null, LoginStartupFailure.AUTH_CLIENT_RESULT_MISSING),
            AuthCase(
                AuthClientWireResult(null, null, LoginStartupFailure.AUTH_CLIENT_REJECTED),
                null,
                LoginStartupFailure.AUTH_CLIENT_REJECTED,
            ),
            AuthCase(
                AuthClientWireResult(null, jwt()),
                null,
                LoginStartupFailure.AUTH_CLIENT_ID_MISSING,
            ),
            AuthCase(
                AuthClientWireResult("bad/id", jwt()),
                null,
                LoginStartupFailure.AUTH_CLIENT_ID_INVALID,
            ),
            AuthCase(
                AuthClientWireResult(clientId, null),
                null,
                LoginStartupFailure.CLIENT_JWT_MISSING,
            ),
            AuthCase(
                AuthClientWireResult(clientId, "not-a-jwt"),
                null,
                LoginStartupFailure.CLIENT_JWT_INVALID,
            ),
            AuthCase(
                AuthClientWireResult(clientId, jwt("00000000-0000-0000-0000-000000000009")),
                null,
                LoginStartupFailure.CLIENT_JWT_ID_MISMATCH,
            ),
        )

        cases.forEach { case ->
            val tracker = LoginStartupTracker()
            val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
            var completion: LoginClientCompletion? = null
            coordinator(tracker, case.result, case.requestError)
                .authenticate(attempt) { completion = it }

            assertEquals(
                case.failure,
                (completion as LoginClientCompletion.Failed).failure,
            )
            assertEquals(case.failure, (tracker.state.value as LoginStartupState.Failed).failure)
        }
    }

    @Test
    fun `synchronous requester exception is one terminal auth request failure`() {
        val tracker = LoginStartupTracker()
        val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        var completion: LoginClientCompletion? = null
        LoginClientCoordinator(
            tracker,
            AuthClientRequester { throw IllegalStateException("request") },
            ClientSessionStarter { error("must not start") },
            dispatch = { it() },
        ).authenticate(attempt) { completion = it }

        assertEquals(
            LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED,
            (completion as LoginClientCompletion.Failed).failure,
        )
        assertEquals(
            LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED,
            (tracker.state.value as LoginStartupState.Failed).failure,
        )
    }

    @Test
    fun `server and JWT allocations remain owned on mismatch and rejection`() {
        val tracker = LoginStartupTracker()
        val allocations = mutableListOf<String>()
        tracker.addAllocationListener { allocations += it.clientId }
        val first = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        coordinator(
            tracker,
            AuthClientWireResult(
                clientId,
                jwt("00000000-0000-0000-0000-000000000009"),
            ),
        ).authenticate(first) {}
        val second = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        coordinator(
            tracker,
            AuthClientWireResult(
                "00000000-0000-0000-0000-000000000010",
                null,
                LoginStartupFailure.AUTH_CLIENT_LIMIT_EXCEEDED,
            ),
        ).authenticate(second) {}

        assertEquals(
            listOf(
                clientId,
                "00000000-0000-0000-0000-000000000009",
                "00000000-0000-0000-0000-000000000010",
            ),
            allocations,
        )
    }

    @Test
    fun `persistence and DeviceLocal failures retain allocation and stage`() {
        listOf(
            LoginStartupFailure.CLIENT_JWT_PERSISTENCE_FAILED to
                LoginStartupStage.CLIENT_JWT_PERSISTENCE,
            LoginStartupFailure.DEVICE_NETWORK_SPACE_MISSING to
                LoginStartupStage.DEVICE_LOCAL_INITIALIZATION,
            LoginStartupFailure.DEVICE_LOCAL_STATE_MISSING to
                LoginStartupStage.DEVICE_LOCAL_INITIALIZATION,
            LoginStartupFailure.DEVICE_INSTANCE_ID_MISSING to
                LoginStartupStage.DEVICE_LOCAL_INITIALIZATION,
            LoginStartupFailure.DEVICE_LOCAL_CREATION_FAILED to
                LoginStartupStage.DEVICE_LOCAL_INITIALIZATION,
            LoginStartupFailure.DEVICE_LOCAL_CONFIGURATION_FAILED to
                LoginStartupStage.DEVICE_LOCAL_INITIALIZATION,
        ).forEach { (failure, stage) ->
            val tracker = LoginStartupTracker()
            val allocations = mutableListOf<String>()
            tracker.addAllocationListener { allocations += it.clientId }
            val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)

            coordinator(
                tracker,
                AuthClientWireResult(clientId, jwt()),
                start = { ClientSessionStartResult.Failed(failure) },
            ).authenticate(attempt) {}

            assertEquals(listOf(clientId), allocations)
            val state = tracker.state.value as LoginStartupState.Failed
            assertEquals(stage, state.stage)
            assertEquals(failure, state.failure)
            assertEquals(clientId, state.clientId)
        }
    }

    @Test
    fun `unexpected session starter exception is typed and retains allocation`() {
        val tracker = LoginStartupTracker()
        val allocations = mutableListOf<String>()
        tracker.addAllocationListener { allocations += it.clientId }
        val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        var completion: LoginClientCompletion? = null

        coordinator(
            tracker,
            AuthClientWireResult(clientId, jwt()),
            start = { throw IllegalStateException("configuration") },
        ).authenticate(attempt) { completion = it }

        assertEquals(listOf(clientId), allocations)
        assertEquals(
            LoginStartupFailure.DEVICE_LOCAL_CONFIGURATION_FAILED,
            (completion as LoginClientCompletion.Failed).failure,
        )
    }

    @Test
    fun `stale response owns allocation but cannot start or replace active state`() {
        val tracker = LoginStartupTracker()
        val allocations = mutableListOf<String>()
        tracker.addAllocationListener { allocations += it.clientId }
        val request = DeferredRequester()
        var starts = 0
        val coordinator = LoginClientCoordinator(
            tracker,
            request,
            ClientSessionStarter {
                starts += 1
                ClientSessionStartResult.Ready
            },
            dispatch = { it() },
        )
        val staleAttempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        var completion: LoginClientCompletion? = null
        coordinator.authenticate(staleAttempt) { completion = it }
        val currentAttempt = tracker.begin(LoginStartupStage.PASSWORD_AUTH)

        request.complete(AuthClientWireResult(clientId, jwt()), null)

        assertEquals(listOf(clientId), allocations)
        assertEquals(0, starts)
        assertEquals(LoginStartupFailure.STALE_ATTEMPT, (completion as LoginClientCompletion.Failed).failure)
        assertEquals(
            LoginStartupState.Pending(currentAttempt, LoginStartupStage.PASSWORD_AUTH),
            tracker.state.value,
        )
    }

    @Test
    fun `stale error callback cannot replace a newer attempt`() {
        val tracker = LoginStartupTracker()
        val request = DeferredRequester()
        val coordinator = LoginClientCoordinator(
            tracker,
            request,
            ClientSessionStarter { error("must not start") },
            dispatch = { it() },
        )
        val staleAttempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        var completion: LoginClientCompletion? = null
        coordinator.authenticate(staleAttempt) { completion = it }
        val currentAttempt = tracker.begin(LoginStartupStage.PASSWORD_AUTH)

        request.complete(null, "offline")

        assertEquals(
            LoginStartupFailure.STALE_ATTEMPT,
            (completion as LoginClientCompletion.Failed).failure,
        )
        assertEquals(
            LoginStartupState.Pending(currentAttempt, LoginStartupStage.PASSWORD_AUTH),
            tracker.state.value,
        )
    }

    @Test
    fun `duplicate callback starts once and logout is terminal`() {
        val tracker = LoginStartupTracker()
        val request = DeferredRequester()
        var starts = 0
        val coordinator = LoginClientCoordinator(
            tracker,
            request,
            ClientSessionStarter {
                starts += 1
                ClientSessionStartResult.Ready
            },
            dispatch = { it() },
        )
        val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        coordinator.authenticate(attempt) {}

        request.complete(AuthClientWireResult(clientId, jwt()), null)
        request.complete(AuthClientWireResult(clientId, jwt()), null)
        tracker.authLoggedOut()

        assertEquals(1, starts)
        assertTrue(tracker.state.value is LoginStartupState.Failed)
        val state = tracker.state.value as LoginStartupState.Failed
        assertEquals(LoginStartupStage.LOGOUT, state.stage)
        assertEquals(LoginStartupFailure.AUTH_LOGOUT, state.failure)
        assertTrue(state.terminal)
        assertFalse(LoginStartupState.Idle.terminal)
    }

    @Test
    fun `explicit logout cannot be overwritten by a late auth logout callback`() {
        val tracker = LoginStartupTracker()
        tracker.begin(LoginStartupStage.PASSWORD_AUTH)
        tracker.loggedOut()
        val loggedOut = tracker.state.value

        tracker.authLoggedOut()

        assertEquals(loggedOut, tracker.state.value)
    }

    @Test
    fun `stored key creation tries bounded fallback branches`() {
        val calls = mutableListOf<String?>()
        var cleared = 0
        val recovered = createDeviceWithStoredKey(
            storedKey = "retained",
            clearStoredKey = { cleared += 1 },
            create = {
                calls += it
                if (it == null) "device" else null
            },
        )
        assertEquals(DeviceCreationResult.Created("device"), recovered)
        assertEquals(listOf("retained", null), calls)
        assertEquals(1, cleared)

        val failed = createDeviceWithStoredKey<String, String>(
            storedKey = null,
            clearStoredKey = { error("must not clear") },
            create = { throw IllegalStateException("creation") },
        )
        assertEquals(
            DeviceCreationResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CREATION_FAILED),
            failed,
        )
    }

    @Test
    fun `partial DeviceLocal configuration is closed on failure only`() {
        var closes = 0
        val failed = configureCreatedDevice(
            configure = { throw IllegalStateException("configuration") },
            closePartialState = { closes += 1 },
        )
        assertEquals(
            DeviceConfigurationResult.Failed(
                DeviceInitFailure.DEVICE_LOCAL_CONFIGURATION_FAILED,
            ),
            failed,
        )
        assertEquals(1, closes)

        val configured = configureCreatedDevice(
            configure = { "configured" },
            closePartialState = { closes += 1 },
        )
        assertEquals(DeviceConfigurationResult.Configured("configured"), configured)
        assertEquals(1, closes)
    }

    private fun coordinator(
        tracker: LoginStartupTracker,
        result: AuthClientWireResult?,
        requestError: String? = null,
        start: () -> ClientSessionStartResult = { ClientSessionStartResult.Ready },
    ) = LoginClientCoordinator(
        tracker,
        AuthClientRequester { callback -> callback(result, requestError) },
        ClientSessionStarter { start() },
        dispatch = { it() },
    )

    private data class AuthCase(
        val result: AuthClientWireResult?,
        val requestError: String?,
        val failure: LoginStartupFailure,
    )

    private data class PasswordCase(
        val result: PasswordAuthWireResult?,
        val requestError: String?,
        val failure: LoginStartupFailure,
    )

    private class DeferredRequester : AuthClientRequester {
        private lateinit var callback: (AuthClientWireResult?, String?) -> Unit

        override fun request(callback: (AuthClientWireResult?, String?) -> Unit) {
            this.callback = callback
        }

        fun complete(result: AuthClientWireResult?, error: String?) {
            callback(result, error)
        }
    }

    private class DeferredPasswordRequester : PasswordAuthRequester {
        private lateinit var callback: (PasswordAuthWireResult?, String?) -> Unit

        override fun request(
            userAuth: String,
            password: String,
            callback: (PasswordAuthWireResult?, String?) -> Unit,
        ) {
            this.callback = callback
        }

        fun complete(result: PasswordAuthWireResult?, error: String?) {
            callback(result, error)
        }
    }
}
