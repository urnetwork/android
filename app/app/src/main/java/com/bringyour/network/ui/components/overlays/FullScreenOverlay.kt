package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bringyour.client.Sub
import com.bringyour.network.MainApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

enum class OverlayMode {
    GuestMode,
    Upgrade,
    Refer,
    FeedbackSubmitted,
    Onboarding,
    TransferSubmitted;

    companion object {
        fun fromString(value: String): OverlayMode? {
            return when (value.lowercase()) {
                "guest_mode" -> GuestMode
                "upgrade" -> Upgrade
                "refer" -> Refer
                "feedback_submitted" -> FeedbackSubmitted
                "onboarding" -> Onboarding
                "transfer_submitted" -> TransferSubmitted
                else -> null
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            GuestMode -> "guest_mode"
            Upgrade -> "upgrade"
            Refer -> "refer"
            FeedbackSubmitted -> "feedback_submitted"
            Onboarding -> "onboarding"
            TransferSubmitted -> "transfer_submitted"
        }
    }
}

@Composable
fun FullScreenOverlay() {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc
    val subs = remember { mutableListOf<Sub>() }
    var overlayMode by remember { mutableStateOf<OverlayMode?>(null) }

    val addOverlayModeListener = {
        if (overlayVc != null) {
            subs.add(overlayVc.addOverlayModeListener { modeStr ->
                runBlocking(Dispatchers.Main.immediate) {
                    overlayMode = OverlayMode.fromString(modeStr)
                }
            })
        }
    }

    DisposableEffect(Unit) {

        addOverlayModeListener()

        onDispose {
            subs.forEach { sub ->
                sub.close()
            }
            subs.clear()
        }

    }

    AnimatedVisibility(
        visible = overlayMode == OverlayMode.GuestMode,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
    ) {

        GuestModeOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }

    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Refer,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
    ) {

        ReferOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }
}