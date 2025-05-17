package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.ReferralCodeViewModel

enum class OverlayMode {
    GuestMode,
    Upgrade,
    Refer,
    FeedbackSubmitted,
    Onboarding,
    OnboardingGuestMode,
}

@Composable
fun FullScreenOverlay(
    overlayViewModel: OverlayViewModel,
    referralCodeViewModel: ReferralCodeViewModel?
) {

    val enterTransition = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
    val exitTransition = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()

    val overlayMode = overlayViewModel.overlayModeState.collectAsState().value

    // You're in Guest mode overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.GuestMode,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        GuestModeOverlay(
            onDismiss = {
                overlayViewModel.launch(null)
            }
        )
    }

    // Refer overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Refer,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        if (referralCodeViewModel != null) {
            ReferOverlay(
                referralCodeViewModel = referralCodeViewModel,
                onDismiss = {
                    overlayViewModel.launch(null)
                }
            )
        }
    }

    // Feedback submitted overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.FeedbackSubmitted,
        enter = enterTransition,
        exit = exitTransition,
    ) {
        FeedbackSubmittedOverlay(
            onDismiss = {
                overlayViewModel.launch(null)
            }
        )
    }

    // Onboarding overlay
    // todo - this is not being used
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Onboarding,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        OnboardingOverlay()
    }

    // Plan upgrade
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Upgrade,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        PlanUpgradedOverlay(
            onDismiss = {
                overlayViewModel.launch(null)
            }
        )
    }

}
