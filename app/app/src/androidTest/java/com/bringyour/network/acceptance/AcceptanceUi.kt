package com.bringyour.network.acceptance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement

/** Binds the shared password-login contract to Compose semantics. */
internal class ComposePasswordLoginUi(
    private val compose: ComposeTestRule,
) : PasswordLoginUi {
    override fun waitForTag(tag: String, timeoutMillis: Long) {
        if (tag == PASSWORD_LOGIN_INPUT_TAG) {
            waitForPasswordDiscovery(timeoutMillis)
            return
        }
        try {
            compose.waitUntil(timeoutMillis) {
                compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Timed out waiting for UI tag $tag after ${timeoutMillis / 1_000}s",
                error,
            )
        }
    }

    private fun hasTag(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    /** Observe terminal errors while the app is still alive, not after cleanup. */
    private fun waitForPasswordDiscovery(timeoutMillis: Long) {
        var evidence: PasswordLoginDiscoveryEvidence? = null
        var result = PasswordLoginDiscoveryState.PENDING
        try {
            compose.waitUntil(timeoutMillis) {
                val observed = PasswordLoginDiscoveryEvidence(
                    userFormVisible = hasTag(PASSWORD_LOGIN_USER_TAG),
                    passwordFormVisible = hasTag(PASSWORD_LOGIN_INPUT_TAG),
                    errorVisible = hasTag(PASSWORD_LOGIN_DISCOVERY_ERROR_TAG),
                )
                evidence = observed
                result = passwordLoginDiscoveryState(observed)
                result != PasswordLoginDiscoveryState.PENDING
            }
        } catch (error: Throwable) {
            throw PasswordLoginFailureException(
                PasswordLoginStage.DISCOVERY,
                if (error is ComposeTimeoutException) PasswordLoginFailure.DISCOVERY_TIMEOUT
                else PasswordLoginFailure.DISCOVERY_OBSERVATION_FAILED,
                evidence,
                error,
            )
        }
        if (result == PasswordLoginDiscoveryState.FAILED) {
            throw PasswordLoginFailureException(
                PasswordLoginStage.DISCOVERY,
                PasswordLoginFailure.DISCOVERY_FAILED,
                evidence,
            )
        }
    }

    override fun replaceTagText(tag: String, value: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assertExists()
            .performTextReplacement(value)
    }

    override fun performEnabledTagClick(tag: String, timeoutMillis: Long) {
        compose.performEnabledSemanticsClick(tag, timeoutMillis)
    }
}

/**
 * Waits for the tagged control's enabled semantics before an acceptance action.
 * Text replacement can publish the node before its enabling recomposition; a
 * click during that interval is ignored by Compose and never reaches the app.
 */
internal fun ComposeTestRule.waitForEnabledTag(
    tag: String,
    timeoutMillis: Long,
    onDisabledObserved: (() -> Unit)? = null,
) {
    var disabledReported = false
    try {
        waitUntil(timeoutMillis) {
            val enabled = runCatching {
                onNodeWithTag(tag, useUnmergedTree = true)
                    .assertExists()
                    .assertIsEnabled()
                true
            }.getOrDefault(false)
            if (!enabled && !disabledReported) {
                disabledReported = true
                onDisabledObserved?.invoke()
            }
            enabled
        }
    } catch (error: Throwable) {
        throw AssertionError(
            "Timed out waiting for enabled UI tag $tag after ${timeoutMillis / 1_000}s",
            error,
        )
    }
}

/**
 * Invokes a form action through semantics and requires the control to
 * acknowledge that it handled the action. Android's performClick() injects a
 * tap at coordinates captured from the node. When the IME moves a form during
 * that handoff, the tap can miss and still return normally, leaving the test
 * waiting on a login that was never started.
 */
internal fun ComposeTestRule.performEnabledSemanticsClick(
    tag: String,
    timeoutMillis: Long,
) {
    waitForEnabledTag(tag, timeoutMillis)

    var accepted = false
    onNodeWithTag(tag, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.OnClick) { action ->
            accepted = action.invoke()
        }
    if (!accepted) {
        throw AssertionError("UI tag $tag did not accept its click action")
    }
}
