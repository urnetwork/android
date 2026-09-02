package com.bringyour.network.acceptance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction

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
