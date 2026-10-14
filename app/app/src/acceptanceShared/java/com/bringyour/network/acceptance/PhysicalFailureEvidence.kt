package com.bringyour.network.acceptance

import com.bringyour.network.LoginStartupStage
import com.bringyour.network.LoginStartupState

/** Typed at the observed startup failure, not inferred from later application state. */
internal class LoginStartupFailureException(val state: LoginStartupState.Failed) :
    IllegalStateException("login startup failed at ${state.stage.wireValue}: ${state.failure.wireValue}")

internal class CleanupLedgerFailureException(cause: Throwable) :
    IllegalStateException("client cleanup ownership could not be persisted", cause)

internal class PhysicalLoginLoggedOutException : IllegalStateException("login terminated during logout")

/** Only the physical login deadline creates this error; shared timeout behavior is unchanged. */
internal class PhysicalLoginStartupTimeout(state: LoginStartupState, val timeoutMillis: Long) :
    AssertionError("Timed out waiting for authenticated DeviceLocal; login state=${state.javaClass.simpleName}") {
    val stage: LoginStartupStage? = when (state) {
        is LoginStartupState.Pending -> state.stage
        is LoginStartupState.Failed -> state.stage
        else -> null
    }
}

internal enum class PhysicalFailureOperation { EGRESS_PROBE }

/** Immutable, credential-free values only; never retain mutable status JSON or application state. */
internal data class PhysicalFailureSnapshot(
    val phase: String,
    val errorType: String,
    val timeUnixMs: Long,
    val elapsedRealtimeMs: Long,
    val commandElapsedMs: Long?,
    val stage: String?,
    val failure: String?,
    val timeoutMillis: Long?,
    val loginUiBeforeTeardown: PasswordLoginDiscoveryEvidence?,
    val publicReadiness: PhysicalPublicReadinessSnapshot? = null,
) {
    fun statusFields(): Map<String, Any> = buildMap {
        put("errorType", errorType)
        put("failurePhase", phase)
        put("failureTimeUnixMs", timeUnixMs)
        put("failureElapsedRealtimeMs", elapsedRealtimeMs)
        commandElapsedMs?.let { put("commandElapsedMs", it) }
        stage?.let { put("stage", it) }
        failure?.let { put("failure", it) }
        timeoutMillis?.let { put("timeoutMs", it) }
        publicReadiness?.let { put("publicReadiness", it.statusFields()) }
        loginUiBeforeTeardown?.let {
            put("loginUiBeforeTeardown", mapOf(
                "userFormVisible" to it.userFormVisible,
                "passwordFormVisible" to it.passwordFormVisible,
                "discoveryErrorVisible" to it.errorVisible,
            ))
        }
    }
}

/** One instrumentation-thread owner; repeated catches of the same throwable keep its first evidence. */
internal class PhysicalFailureEvidence {
    private var retainedError: Throwable? = null
    private var retainedSnapshot: PhysicalFailureSnapshot? = null

    fun capture(
        error: Throwable,
        phase: String,
        timeUnixMs: Long,
        elapsedRealtimeMs: Long,
        commandElapsedMs: Long? = null,
        operation: PhysicalFailureOperation? = null,
    ): PhysicalFailureSnapshot {
        if (retainedError === error) return checkNotNull(retainedSnapshot)
        var stage: String? = null
        var failure: String? = null
        var timeoutMillis: Long? = null
        var loginUi: PasswordLoginDiscoveryEvidence? = null
        var publicReadiness: PhysicalPublicReadinessSnapshot? = null
        when (error) {
            is PhysicalWaitTimeout -> {
                stage = error.stage.wireValue
                failure = "wait-timeout"
                timeoutMillis = error.timeoutMillis
                publicReadiness = error.publicReadiness
            }
            is CleanupLedgerFailureException -> {
                stage = "client-allocation"
                failure = "cleanup-ledger-persistence-failed"
            }
            is PasswordLoginFailureException -> {
                stage = error.stage.wireValue
                failure = error.failure.wireValue
                loginUi = error.evidence?.copy()
            }
            is LoginStartupFailureException -> {
                stage = error.state.stage.wireValue
                failure = error.state.failure.wireValue
            }
            is PhysicalLoginStartupTimeout -> {
                stage = error.stage?.wireValue ?: "startup"
                failure = "startup-timeout"
                timeoutMillis = error.timeoutMillis
            }
            is PhysicalLoginLoggedOutException -> {
                stage = "logout"
                failure = "auth-logout"
            }
            else -> if (operation == PhysicalFailureOperation.EGRESS_PROBE) {
                stage = "egress-probe"
                failure = "egress-probe-failed"
            }
        }
        val snapshot = PhysicalFailureSnapshot(
            phase = phase,
            errorType = error.javaClass.simpleName,
            timeUnixMs = timeUnixMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            commandElapsedMs = commandElapsedMs,
            stage = stage,
            failure = failure,
            timeoutMillis = timeoutMillis,
            loginUiBeforeTeardown = loginUi,
            publicReadiness = publicReadiness,
        )
        retainedError = error
        retainedSnapshot = snapshot
        return snapshot
    }
}
