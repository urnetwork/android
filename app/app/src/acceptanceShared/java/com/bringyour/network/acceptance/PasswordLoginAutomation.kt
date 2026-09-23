package com.bringyour.network.acceptance

internal const val PASSWORD_LOGIN_USER_TAG = "acceptance.password.user"
internal const val PASSWORD_LOGIN_NEXT_TAG = "acceptance.password.next"
internal const val PASSWORD_LOGIN_INPUT_TAG = "acceptance.password.input"
internal const val PASSWORD_LOGIN_SUBMIT_TAG = "acceptance.password.submit"
internal const val PASSWORD_LOGIN_DISCOVERY_ERROR_TAG = "acceptance.password.discovery-error"

internal enum class PasswordLoginStage(val wireValue: String) {
    USER_FORM("auth-user-form"),
    DISCOVERY("auth-discovery"),
    PASSWORD_SUBMIT("auth-password-submit"),
}

internal enum class PasswordLoginFailure(val wireValue: String) {
    UI_ACTION_FAILED("ui-action-failed"),
    DISCOVERY_FAILED("auth-discovery-failed"),
    DISCOVERY_TIMEOUT("auth-discovery-timeout"),
    DISCOVERY_OBSERVATION_FAILED("auth-discovery-observation-failed"),
}

/** Finite, credential-free evidence captured before instrumentation teardown. */
internal data class PasswordLoginDiscoveryEvidence(
    val userFormVisible: Boolean,
    val passwordFormVisible: Boolean,
    val errorVisible: Boolean,
)

internal class PasswordLoginFailureException(
    val stage: PasswordLoginStage,
    val failure: PasswordLoginFailure,
    val evidence: PasswordLoginDiscoveryEvidence? = null,
    cause: Throwable? = null,
) : AssertionError("Password login failed at ${stage.wireValue}: ${failure.wireValue}", cause)

internal enum class PasswordLoginDiscoveryState {
    PENDING,
    PASSWORD_FORM,
    FAILED,
}

/**
 * An API discovery error is terminal for this attempt. Never read/record its
 * text, retry credentials, or call an error screen a successful password form.
 */
internal fun passwordLoginDiscoveryState(
    evidence: PasswordLoginDiscoveryEvidence,
): PasswordLoginDiscoveryState = when {
    evidence.errorVisible -> PasswordLoginDiscoveryState.FAILED
    evidence.passwordFormVisible -> PasswordLoginDiscoveryState.PASSWORD_FORM
    else -> PasswordLoginDiscoveryState.PENDING
}

private inline fun passwordLoginStep(stage: PasswordLoginStage, action: () -> Unit) {
    try {
        action()
    } catch (error: PasswordLoginFailureException) {
        throw error
    } catch (error: Throwable) {
        throw PasswordLoginFailureException(stage, PasswordLoginFailure.UI_ACTION_FAILED, cause = error)
    }
}

/** Drives the app-owned password form through its stable semantics contract. */
internal interface PasswordLoginUi {
    fun waitForTag(tag: String, timeoutMillis: Long)

    fun replaceTagText(tag: String, value: String)

    fun performEnabledTagClick(tag: String, timeoutMillis: Long)
}

/**
 * Completes both password-login screens without depending on viewport layout,
 * accessibility class names, translated labels, or screen coordinates.
 */
internal fun performPasswordLogin(
    ui: PasswordLoginUi,
    user: String,
    password: String,
    uiTimeoutMillis: Long,
    authTimeoutMillis: Long,
) {
    passwordLoginStep(PasswordLoginStage.USER_FORM) {
        ui.waitForTag(PASSWORD_LOGIN_USER_TAG, uiTimeoutMillis)
        ui.replaceTagText(PASSWORD_LOGIN_USER_TAG, user)
    }
    passwordLoginStep(PasswordLoginStage.DISCOVERY) {
        ui.performEnabledTagClick(PASSWORD_LOGIN_NEXT_TAG, authTimeoutMillis)
        ui.waitForTag(PASSWORD_LOGIN_INPUT_TAG, authTimeoutMillis)
    }
    passwordLoginStep(PasswordLoginStage.PASSWORD_SUBMIT) {
        ui.replaceTagText(PASSWORD_LOGIN_INPUT_TAG, password)
        ui.performEnabledTagClick(PASSWORD_LOGIN_SUBMIT_TAG, authTimeoutMillis)
    }
}
