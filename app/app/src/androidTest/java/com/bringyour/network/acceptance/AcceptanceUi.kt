package com.bringyour.network.acceptance

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag

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
