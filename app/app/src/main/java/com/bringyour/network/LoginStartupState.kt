package com.bringyour.network

import com.bringyour.network.ui.login.VerifySendError
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Stable, non-secret stages that describe one login attempt. */
enum class LoginStartupStage(val wireValue: String) {
    PASSWORD_AUTH("password-auth"),
    NETWORK_SESSION_PERSISTENCE("network-session-persistence"),
    AUTH_CLIENT("auth-client"),
    CLIENT_ALLOCATION("client-allocation"),
    CLIENT_JWT_VALIDATION("client-jwt-validation"),
    CLIENT_JWT_PERSISTENCE("client-jwt-persistence"),
    DEVICE_LOCAL_INITIALIZATION("device-local-initialization"),
    READY("ready"),
    LOGOUT("logout"),
}

/** Bounded failure vocabulary; raw credentials, JWTs, IDs, and server text never enter status. */
enum class LoginStartupFailure(val wireValue: String) {
    PASSWORD_AUTH_REQUEST_FAILED("password-auth-request-failed"),
    PASSWORD_AUTH_REJECTED("password-auth-rejected"),
    PASSWORD_AUTH_RESULT_INVALID("password-auth-result-invalid"),
    NETWORK_SESSION_PERSISTENCE_FAILED("network-session-persistence-failed"),
    AUTH_CLIENT_UNAVAILABLE("auth-client-unavailable"),
    AUTH_CLIENT_REQUEST_FAILED("auth-client-request-failed"),
    AUTH_CLIENT_REJECTED("auth-client-rejected"),
    AUTH_CLIENT_LIMIT_EXCEEDED("auth-client-limit-exceeded"),
    AUTH_CLIENT_UPGRADE_REQUIRED("auth-client-upgrade-required"),
    AUTH_CLIENT_RESULT_MISSING("auth-client-result-missing"),
    AUTH_CLIENT_ID_MISSING("auth-client-id-missing"),
    AUTH_CLIENT_ID_INVALID("auth-client-id-invalid"),
    CLIENT_JWT_MISSING("client-jwt-missing"),
    CLIENT_JWT_INVALID("client-jwt-invalid"),
    CLIENT_JWT_ID_MISMATCH("client-jwt-id-mismatch"),
    CLIENT_JWT_PERSISTENCE_FAILED("client-jwt-persistence-failed"),
    DEVICE_NETWORK_SPACE_MISSING("device-network-space-missing"),
    DEVICE_LOCAL_STATE_MISSING("device-local-state-missing"),
    DEVICE_INSTANCE_ID_MISSING("device-instance-id-missing"),
    DEVICE_LOCAL_CREATION_FAILED("device-local-creation-failed"),
    DEVICE_LOCAL_CONFIGURATION_FAILED("device-local-configuration-failed"),
    AUTH_LOGOUT("auth-logout"),
    STALE_ATTEMPT("stale-attempt"),
}

sealed class LoginStartupState {
    abstract val attemptId: Long?
    abstract val clientId: String?

    object Idle : LoginStartupState() {
        override val attemptId: Long? = null
        override val clientId: String? = null
    }

    data class Pending(
        override val attemptId: Long,
        val stage: LoginStartupStage,
        override val clientId: String? = null,
    ) : LoginStartupState()

    data class Failed(
        override val attemptId: Long,
        val stage: LoginStartupStage,
        val failure: LoginStartupFailure,
        override val clientId: String? = null,
    ) : LoginStartupState()

    data class Ready(
        override val attemptId: Long,
        override val clientId: String,
    ) : LoginStartupState()

    data class LoggedOut(
        override val attemptId: Long?,
        override val clientId: String?,
    ) : LoginStartupState()

    val terminal: Boolean
        get() = this is Failed || this is Ready || this is LoggedOut
}

data class LoginClientAllocation(
    val attemptId: Long,
    val clientId: String,
)

/**
 * Process-owned login state. Allocation listeners are synchronous: once
 * [recordAllocation] returns, observers have had the opportunity to take
 * cleanup ownership before DeviceLocal construction begins. Only the current
 * attempt's two possible IDs are retained for late-listener replay; live
 * listeners still receive allocations from stale callbacks so they can own
 * cleanup without accumulating process-lifetime history here.
 */
class LoginStartupTracker {
    private companion object {
        const val MAX_REPLAY_ALLOCATIONS = 2
    }

    private val lock = Any()
    private var generation = 0L
    private val replayAllocations = ArrayDeque<LoginClientAllocation>()
    private val allocationListeners = mutableListOf<AllocationListener>()
    private val mutableState = MutableStateFlow<LoginStartupState>(LoginStartupState.Idle)

    val state: StateFlow<LoginStartupState> = mutableState.asStateFlow()

    fun begin(stage: LoginStartupStage): Long {
        synchronized(lock) {
            generation += 1
            replayAllocations.clear()
            mutableState.value = LoginStartupState.Pending(generation, stage)
            return generation
        }
    }

    fun ensureAttempt(stage: LoginStartupStage): Long {
        synchronized(lock) {
            val current = mutableState.value
            if (current is LoginStartupState.Pending) {
                mutableState.value = current.copy(stage = stage)
                return current.attemptId
            }
        }
        return begin(stage)
    }

    fun isActive(attemptId: Long): Boolean = synchronized(lock) {
        val current = mutableState.value
        current.attemptId == attemptId && current is LoginStartupState.Pending
    }

    fun pending(attemptId: Long, stage: LoginStartupStage): Boolean = synchronized(lock) {
        val current = mutableState.value
        if (current.attemptId != attemptId || current !is LoginStartupState.Pending) {
            return@synchronized false
        }
        mutableState.value = current.copy(stage = stage)
        true
    }

    fun recordAllocation(attemptId: Long, clientId: String) {
        val allocation = LoginClientAllocation(attemptId, clientId)
        val listeners: List<AllocationListener>
        synchronized(lock) {
            val current = mutableState.value
            if (current.attemptId == attemptId && current is LoginStartupState.Pending) {
                mutableState.value = current.copy(
                    stage = LoginStartupStage.CLIENT_ALLOCATION,
                    clientId = current.clientId ?: clientId,
                )
                if (!replayAllocations.contains(allocation)) {
                    while (replayAllocations.size >= MAX_REPLAY_ALLOCATIONS) {
                        replayAllocations.removeFirst()
                    }
                    replayAllocations.addLast(allocation)
                }
            }
            listeners = allocationListeners.toList()
        }
        listeners.forEach { it.deliver(allocation) }
    }

    fun fail(
        attemptId: Long,
        stage: LoginStartupStage,
        failure: LoginStartupFailure,
        clientId: String? = null,
    ): Boolean = synchronized(lock) {
        val current = mutableState.value
        if (current.attemptId != attemptId || current !is LoginStartupState.Pending) {
            return@synchronized false
        }
        mutableState.value = LoginStartupState.Failed(
            attemptId = attemptId,
            stage = stage,
            failure = failure,
            clientId = clientId ?: current.clientId,
        )
        true
    }

    fun ready(attemptId: Long, clientId: String): Boolean = synchronized(lock) {
        val current = mutableState.value
        if (current.attemptId != attemptId || current !is LoginStartupState.Pending) {
            return@synchronized false
        }
        mutableState.value = LoginStartupState.Ready(attemptId, clientId)
        true
    }

    fun authLoggedOut() {
        synchronized(lock) {
            val current = mutableState.value
            if (current !is LoginStartupState.Pending && current !is LoginStartupState.Ready) {
                return
            }
            val attemptId = current.attemptId ?: return
            mutableState.value = LoginStartupState.Failed(
                attemptId = attemptId,
                stage = LoginStartupStage.LOGOUT,
                failure = LoginStartupFailure.AUTH_LOGOUT,
                clientId = current.clientId,
            )
        }
    }

    fun loggedOut() {
        synchronized(lock) {
            val current = mutableState.value
            mutableState.value = LoginStartupState.LoggedOut(current.attemptId, current.clientId)
        }
    }

    fun addAllocationListener(listener: (LoginClientAllocation) -> Unit): () -> Unit {
        val entry = AllocationListener(listener)
        val replay: List<LoginClientAllocation>
        synchronized(lock) {
            allocationListeners.add(entry)
            replay = replayAllocations.toList()
        }
        replay.forEach(entry::deliver)
        return {
            entry.close()
            synchronized(lock) { allocationListeners.remove(entry) }
        }
    }

    private class AllocationListener(
        private val callback: (LoginClientAllocation) -> Unit,
    ) {
        private var active = true
        private var lastAllocation: LoginClientAllocation? = null

        fun deliver(allocation: LoginClientAllocation) = synchronized(this) {
            if (active && allocation != lastAllocation) {
                lastAllocation = allocation
                callback(allocation)
            }
        }

        fun close() = synchronized(this) {
            active = false
        }
    }
}

internal sealed class ClientJwtIdentity {
    data class Valid(val clientId: String) : ClientJwtIdentity()
    object Invalid : ClientJwtIdentity()

    companion object {
        private val clientIdPattern = Regex("[A-Za-z0-9._-]+")

        fun decode(jwt: String): ClientJwtIdentity {
            val parts = jwt.split('.')
            if (parts.size != 3) return Invalid
            return try {
                val payload = Base64.getUrlDecoder().decode(parts[1]).toString(Charsets.UTF_8)
                val clientId = Json.parseToJsonElement(payload)
                    .jsonObject["client_id"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty()
                if (clientId.isNotEmpty() && clientIdPattern.matches(clientId)) {
                    Valid(clientId)
                } else {
                    Invalid
                }
            } catch (_: Exception) {
                Invalid
            }
        }

        fun validClientId(clientId: String?): String? =
            clientId?.takeIf { it.isNotEmpty() && clientIdPattern.matches(it) }
    }
}

internal data class AuthClientWireResult(
    val clientId: String?,
    val byClientJwt: String?,
    val failure: LoginStartupFailure? = null,
    val failureMessage: String? = null,
)

internal fun interface AuthClientRequester {
    fun request(callback: (AuthClientWireResult?, String?) -> Unit)
}

internal sealed class ClientSessionStartResult {
    object Ready : ClientSessionStartResult()
    data class Failed(val failure: LoginStartupFailure) : ClientSessionStartResult()
}

internal fun interface ClientSessionStarter {
    fun start(byClientJwt: String): ClientSessionStartResult
}

internal sealed class LoginClientCompletion {
    data class Ready(val clientId: String) : LoginClientCompletion()
    data class Failed(
        val failure: LoginStartupFailure,
        val message: String? = null,
        val clientId: String? = null,
    ) : LoginClientCompletion()
}

internal data class PasswordAuthWireResult(
    val byJwt: String? = null,
    val verificationUserAuth: String? = null,
    // set when verification is required but no code was sent
    val verificationSendError: VerifySendError? = null,
    val failure: LoginStartupFailure? = null,
    val failureMessage: String? = null,
)

internal fun interface PasswordAuthRequester {
    fun request(
        userAuth: String,
        password: String,
        callback: (PasswordAuthWireResult?, String?) -> Unit,
    )
}

internal fun interface NetworkSessionAuthenticator {
    fun authenticate(
        byJwt: String,
        newNetwork: Boolean,
        completion: (LoginClientCompletion) -> Unit,
    )
}

internal sealed class PasswordLoginCompletion {
    data class Ready(val clientId: String) : PasswordLoginCompletion()
    data class VerificationRequired(
        val userAuth: String,
        // set when no code was sent; the verify screen must not say one was
        val sendError: VerifySendError? = null,
    ) : PasswordLoginCompletion()
    data class Failed(
        val failure: LoginStartupFailure,
        val message: String? = null,
    ) : PasswordLoginCompletion()
}

/** Owns password-auth completion after the initiating composition is gone. */
internal class PasswordLoginCoordinator(
    private val tracker: LoginStartupTracker,
    private val requester: PasswordAuthRequester,
    private val sessionAuthenticator: NetworkSessionAuthenticator,
    private val dispatch: (() -> Unit) -> Unit,
) {
    fun authenticate(
        userAuth: String,
        password: String,
        completion: (PasswordLoginCompletion) -> Unit,
    ): Long {
        val attemptId = tracker.begin(LoginStartupStage.PASSWORD_AUTH)
        val completed = AtomicBoolean(false)
        try {
            requester.request(userAuth, password) { result, requestError ->
                if (!completed.compareAndSet(false, true)) return@request
                dispatch {
                    complete(attemptId, result, requestError, completion)
                }
            }
        } catch (_: Throwable) {
            if (completed.compareAndSet(false, true)) {
                dispatch {
                    fail(
                        attemptId,
                        LoginStartupFailure.PASSWORD_AUTH_REQUEST_FAILED,
                        null,
                        completion,
                    )
                }
            }
        }
        return attemptId
    }

    private fun complete(
        attemptId: Long,
        result: PasswordAuthWireResult?,
        requestError: String?,
        completion: (PasswordLoginCompletion) -> Unit,
    ) {
        if (!tracker.isActive(attemptId)) {
            completion(PasswordLoginCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT))
            return
        }
        if (requestError != null) {
            fail(
                attemptId,
                LoginStartupFailure.PASSWORD_AUTH_REQUEST_FAILED,
                requestError,
                completion,
            )
            return
        }
        if (result == null) {
            fail(
                attemptId,
                LoginStartupFailure.PASSWORD_AUTH_RESULT_INVALID,
                null,
                completion,
            )
            return
        }
        result.failure?.let { failure ->
            fail(attemptId, failure, result.failureMessage, completion)
            return
        }
        result.verificationUserAuth?.takeIf(String::isNotEmpty)?.let { verificationUserAuth ->
            completion(
                PasswordLoginCompletion.VerificationRequired(
                    verificationUserAuth,
                    result.verificationSendError,
                )
            )
            return
        }
        val byJwt = result.byJwt?.takeIf(String::isNotEmpty)
        if (byJwt == null) {
            fail(
                attemptId,
                LoginStartupFailure.PASSWORD_AUTH_RESULT_INVALID,
                null,
                completion,
            )
            return
        }

        val sessionCompleted = AtomicBoolean(false)
        try {
            sessionAuthenticator.authenticate(
                byJwt,
                false,
                sessionResult@{ sessionCompletion ->
                    if (!sessionCompleted.compareAndSet(false, true)) return@sessionResult
                    completion(
                        when (sessionCompletion) {
                            is LoginClientCompletion.Ready ->
                                PasswordLoginCompletion.Ready(sessionCompletion.clientId)
                            is LoginClientCompletion.Failed ->
                                PasswordLoginCompletion.Failed(
                                    sessionCompletion.failure,
                                    sessionCompletion.message,
                                )
                        },
                    )
                },
            )
        } catch (_: Throwable) {
            if (!sessionCompleted.compareAndSet(false, true)) return
            val state = tracker.state.value
            val failure = (state as? LoginStartupState.Failed)?.failure
                ?: LoginStartupFailure.NETWORK_SESSION_PERSISTENCE_FAILED
            val stage = (state as? LoginStartupState.Pending)?.stage
                ?: LoginStartupStage.NETWORK_SESSION_PERSISTENCE
            tracker.fail(attemptId, stage, failure)
            completion(PasswordLoginCompletion.Failed(failure))
        }
    }

    private fun fail(
        attemptId: Long,
        failure: LoginStartupFailure,
        message: String?,
        completion: (PasswordLoginCompletion) -> Unit,
    ) {
        if (tracker.fail(attemptId, LoginStartupStage.PASSWORD_AUTH, failure)) {
            completion(PasswordLoginCompletion.Failed(failure, message))
        } else {
            completion(PasswordLoginCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT))
        }
    }
}

/** Coordinates one auth-client response without depending on an Activity lifecycle. */
internal class LoginClientCoordinator(
    private val tracker: LoginStartupTracker,
    private val requester: AuthClientRequester,
    private val starter: ClientSessionStarter,
    private val dispatch: (() -> Unit) -> Unit,
) {
    fun authenticate(attemptId: Long, completion: (LoginClientCompletion) -> Unit) {
        if (!tracker.pending(attemptId, LoginStartupStage.AUTH_CLIENT)) {
            completion(LoginClientCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT))
            return
        }

        val completed = AtomicBoolean(false)
        try {
            requester.request { result, requestError ->
                if (!completed.compareAndSet(false, true)) return@request
                dispatch {
                    complete(attemptId, result, requestError, completion)
                }
            }
        } catch (error: Throwable) {
            if (completed.compareAndSet(false, true)) {
                dispatch {
                    fail(
                        attemptId,
                        LoginStartupStage.AUTH_CLIENT,
                        LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED,
                        error.message,
                        null,
                        completion,
                    )
                }
            }
        }
    }

    private fun complete(
        attemptId: Long,
        result: AuthClientWireResult?,
        requestError: String?,
        completion: (LoginClientCompletion) -> Unit,
    ) {
        val rawServerId = result?.clientId
        val serverId = ClientJwtIdentity.validClientId(rawServerId)
        val jwt = result?.byClientJwt.orEmpty()
        val jwtIdentity = if (jwt.isEmpty()) ClientJwtIdentity.Invalid else ClientJwtIdentity.decode(jwt)
        val jwtId = (jwtIdentity as? ClientJwtIdentity.Valid)?.clientId

        // A response may contain an allocated ID together with an error. Take
        // cleanup ownership before classifying any later branch.
        serverId?.let { tracker.recordAllocation(attemptId, it) }
        if (jwtId != null && jwtId != serverId) tracker.recordAllocation(attemptId, jwtId)
        val ownedClientId = serverId ?: jwtId

        if (requestError != null) {
            fail(
                attemptId,
                LoginStartupStage.AUTH_CLIENT,
                LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED,
                requestError,
                ownedClientId,
                completion,
            )
            return
        }
        if (result == null) {
            fail(
                attemptId,
                LoginStartupStage.AUTH_CLIENT,
                LoginStartupFailure.AUTH_CLIENT_RESULT_MISSING,
                null,
                ownedClientId,
                completion,
            )
            return
        }
        result.failure?.let { failure ->
            fail(
                attemptId,
                LoginStartupStage.AUTH_CLIENT,
                failure,
                result.failureMessage,
                ownedClientId,
                completion,
            )
            return
        }
        if (rawServerId != null && serverId == null) {
            fail(
                attemptId,
                LoginStartupStage.CLIENT_ALLOCATION,
                LoginStartupFailure.AUTH_CLIENT_ID_INVALID,
                null,
                ownedClientId,
                completion,
            )
            return
        }
        if (serverId == null) {
            fail(
                attemptId,
                LoginStartupStage.CLIENT_ALLOCATION,
                LoginStartupFailure.AUTH_CLIENT_ID_MISSING,
                null,
                ownedClientId,
                completion,
            )
            return
        }
        if (jwt.isEmpty()) {
            fail(
                attemptId,
                LoginStartupStage.CLIENT_JWT_VALIDATION,
                LoginStartupFailure.CLIENT_JWT_MISSING,
                null,
                serverId,
                completion,
            )
            return
        }
        if (jwtId == null) {
            fail(
                attemptId,
                LoginStartupStage.CLIENT_JWT_VALIDATION,
                LoginStartupFailure.CLIENT_JWT_INVALID,
                null,
                serverId,
                completion,
            )
            return
        }
        if (jwtId != serverId) {
            fail(
                attemptId,
                LoginStartupStage.CLIENT_JWT_VALIDATION,
                LoginStartupFailure.CLIENT_JWT_ID_MISMATCH,
                null,
                serverId,
                completion,
            )
            return
        }
        if (!tracker.pending(attemptId, LoginStartupStage.CLIENT_JWT_PERSISTENCE)) {
            completion(LoginClientCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT, clientId = serverId))
            return
        }

        val startResult = try {
            starter.start(jwt)
        } catch (_: Throwable) {
            ClientSessionStartResult.Failed(LoginStartupFailure.DEVICE_LOCAL_CONFIGURATION_FAILED)
        }
        when (startResult) {
            ClientSessionStartResult.Ready -> {
                if (tracker.ready(attemptId, serverId)) {
                    completion(LoginClientCompletion.Ready(serverId))
                } else {
                    val failure = (tracker.state.value as? LoginStartupState.Failed)?.failure
                        ?: LoginStartupFailure.STALE_ATTEMPT
                    completion(LoginClientCompletion.Failed(failure, clientId = serverId))
                }
            }
            is ClientSessionStartResult.Failed -> fail(
                attemptId,
                stageFor(startResult.failure),
                startResult.failure,
                null,
                serverId,
                completion,
            )
        }
    }

    private fun fail(
        attemptId: Long,
        stage: LoginStartupStage,
        failure: LoginStartupFailure,
        message: String?,
        clientId: String?,
        completion: (LoginClientCompletion) -> Unit,
    ) {
        if (tracker.fail(attemptId, stage, failure, clientId)) {
            completion(LoginClientCompletion.Failed(failure, message, clientId))
        } else {
            completion(
                LoginClientCompletion.Failed(
                    LoginStartupFailure.STALE_ATTEMPT,
                    clientId = clientId,
                ),
            )
        }
    }

    private fun stageFor(failure: LoginStartupFailure): LoginStartupStage = when (failure) {
        LoginStartupFailure.CLIENT_JWT_PERSISTENCE_FAILED -> LoginStartupStage.CLIENT_JWT_PERSISTENCE
        LoginStartupFailure.AUTH_LOGOUT -> LoginStartupStage.LOGOUT
        else -> LoginStartupStage.DEVICE_LOCAL_INITIALIZATION
    }
}
