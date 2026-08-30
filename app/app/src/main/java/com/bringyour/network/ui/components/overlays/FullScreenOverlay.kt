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
    Upgrade,
    Refer,
    FeedbackSubmitted,
    Onboarding,
    // a purchase Play accepted but has not completed -- awaiting approval or an
    // out-of-band payment. Distinct from Upgrade, which means it actually went through.
    PurchasePending,
    // one-time crowning celebration when the network's first referral lands
    ReferralCelebration,
}

@Composable
fun FullScreenOverlay(
    overlayViewModel: OverlayViewModel,
    referralCode: String?,
    // server-confirmed subscription (`currentSubscription != null`). Gates the
    // Upgrade overlay's copy: processing-shaped until the server confirms.
    planUpgradeConfirmed: Boolean = false,
    totalReferralCount: Long = 0L,
    // how many referrals the ReferralCelebration overlay is celebrating
    referralCelebrationJoined: Long = 1L,
    onReferralCelebrationDismissed: () -> Unit = {},
) {

    val enterTransition = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
    val exitTransition = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()

    val overlayMode = overlayViewModel.overlayModeState.collectAsState().value

    // Refer overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.Refer,
        enter = enterTransition,
        exit = exitTransition,
    ) {

        if (referralCode != null) {
            ReferOverlay(
                referralCode = referralCode,
                totalReferrals = totalReferralCount,
                onDismiss = {
                    overlayViewModel.launch(null)
                }
            )
        }
    }

    // First-referral crowning celebration
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.ReferralCelebration,
        enter = enterTransition,
        exit = exitTransition,
    ) {
        ReferralCelebrationOverlay(
            joinedCount = referralCelebrationJoined,
            referralCode = referralCode,
            onDismiss = {
                onReferralCelebrationDismissed()
                overlayViewModel.launch(null)
            }
        )
    }

    // Purchase awaiting approval overlay
    AnimatedVisibility(
        visible = overlayMode == OverlayMode.PurchasePending,
        enter = enterTransition,
        exit = exitTransition,
    ) {
        PurchasePendingOverlay(
            onDismiss = {
                overlayViewModel.launch(null)
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
            confirmed = planUpgradeConfirmed,
            onDismiss = {
                overlayViewModel.launch(null)
            }
        )
    }

}
