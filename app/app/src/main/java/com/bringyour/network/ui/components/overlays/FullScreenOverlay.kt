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
    OnboardingGuestMode,
    TransferSubmitted;

    companion object {
        fun fromString(value: String): OverlayMode? {
            return when (value.lowercase()) {
                "guest_mode" -> GuestMode
                "upgrade" -> Upgrade
                "refer" -> Refer
                "feedback_submitted" -> FeedbackSubmitted
                "onboarding" -> Onboarding
                "onboarding_guest_mode" -> OnboardingGuestMode
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
            OnboardingGuestMode -> "onboarding_guest_mode"
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
    val enterTransition = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
    val exitTransition = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()

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

    // Guest mode overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.GuestMode,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        GuestModeOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }

    // Refer overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Refer,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        ReferOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }

    // Feedback submitted overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.FeedbackSubmitted,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        FeedbackSubmittedOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }

    // Onboarding overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Onboarding,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        OnboardingOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }

    // Onboarding guest mode overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.OnboardingGuestMode,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        OnboardingGuestModeOverlay(
            onDismiss = {
                overlayMode = null
            }
        )
    }
}
