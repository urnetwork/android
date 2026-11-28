package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.ProFlightClock
import com.bringyour.network.ui.components.ProSunglassesFlight
import com.bringyour.network.ui.components.proFlightPixelation
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import kotlinx.serialization.Serializable

enum class OverlayMode {
    Upgrade,
    FeedbackSubmitted,
    Onboarding,
    // a purchase Play accepted but has not completed -- awaiting approval or an
    // out-of-band payment. Distinct from Upgrade, which means it actually went through.
    PurchasePending,
    // one-time crowning celebration when the network's first referral lands
    ReferralCelebration,
}

// The overlays' navigation graph: an empty start, and one dialog destination
// per overlay above it (android#128).
@Serializable
internal sealed class OverlayRoute {
    @Serializable object None : OverlayRoute()
    @Serializable object Upgrade : OverlayRoute()
    @Serializable object FeedbackSubmitted : OverlayRoute()
    @Serializable object Onboarding : OverlayRoute()
    @Serializable object PurchasePending : OverlayRoute()
    @Serializable object ReferralCelebration : OverlayRoute()
}

private fun overlayRoute(mode: OverlayMode): OverlayRoute = when (mode) {
    OverlayMode.Upgrade -> OverlayRoute.Upgrade
    OverlayMode.FeedbackSubmitted -> OverlayRoute.FeedbackSubmitted
    OverlayMode.Onboarding -> OverlayRoute.Onboarding
    OverlayMode.PurchasePending -> OverlayRoute.PurchasePending
    OverlayMode.ReferralCelebration -> OverlayRoute.ReferralCelebration
}

// Full screen and edge to edge, like the screen under it. Back is the
// overlay's own (OverlayBackground's BackHandler runs its dismiss), so the
// dialog does not dismiss itself.
private val overlayDialogProperties = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

/**
 * The full-screen overlays, as dialog destinations of their own NavHost
 * (android#128; they were AnimatedVisibility layers over the app, shown by
 * OverlayViewModel's overlay state). OverlayViewModel.launch asks for one;
 * it shows in place of one that shows, as before, so only one is up at a
 * time. Its close button and Back slide it out and pop it. The back stack
 * keeps it across a configuration change.
 *
 * Placed where the overlay layer was, above the intro and the main screens.
 * Its start destination draws nothing, so the screens under it take every
 * touch and Back while no overlay shows.
 */
@Composable
fun FullScreenOverlay(
    overlayViewModel: OverlayViewModel,
    referralCode: String?,
    // server-confirmed subscription (`currentSubscription != null`). Gates the
    // Upgrade overlay's copy: processing-shaped until the server confirms.
    planUpgradeConfirmed: Boolean = false,
    // the account the Upgrade overlay's plan is on (Sdk.planAccountName); empty shows none
    planAccountName: String = "",
    // how many referrals the ReferralCelebration overlay is celebrating
    referralCelebrationJoined: Long = 1L,
    onReferralCelebrationDismissed: () -> Unit = {},
    // The Pro celebration flight, which a purchase starts with the Upgrade
    // overlay: it flies above every overlay with the overlay pixelated under
    // it. The overlay is a window above the app's own flight layer now, so its
    // dialog draws the flight too. null: no flight (the login).
    proFlightClock: ProFlightClock? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(overlayViewModel, navController) {
        overlayViewModel.overlayRequests.collect { mode ->
            navController.navigate(overlayRoute(mode)) {
                popUpTo<OverlayRoute.None>()
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = OverlayRoute.None,
    ) {
        composable<OverlayRoute.None> {}

        // First-referral crowning celebration
        dialog<OverlayRoute.ReferralCelebration>(dialogProperties = overlayDialogProperties) {
            OverlayDialog(
                pop = { navController.popBackStack<OverlayRoute.ReferralCelebration>(inclusive = true) },
            ) { dismiss ->
                ReferralCelebrationOverlay(
                    joinedCount = referralCelebrationJoined,
                    referralCode = referralCode,
                    onDismiss = {
                        onReferralCelebrationDismissed()
                        dismiss()
                    }
                )
            }
        }

        // Purchase awaiting approval overlay
        dialog<OverlayRoute.PurchasePending>(dialogProperties = overlayDialogProperties) {
            OverlayDialog(
                pop = { navController.popBackStack<OverlayRoute.PurchasePending>(inclusive = true) },
            ) { dismiss ->
                PurchasePendingOverlay(
                    onDismiss = dismiss
                )
            }
        }

        // Feedback submitted overlay
        dialog<OverlayRoute.FeedbackSubmitted>(dialogProperties = overlayDialogProperties) {
            OverlayDialog(
                pop = { navController.popBackStack<OverlayRoute.FeedbackSubmitted>(inclusive = true) },
            ) { dismiss ->
                FeedbackSubmittedOverlay(
                    onDismiss = dismiss
                )
            }
        }

        // Onboarding overlay
        // todo - this is not being used
        dialog<OverlayRoute.Onboarding>(dialogProperties = overlayDialogProperties) {
            OverlayDialog(
                pop = { navController.popBackStack<OverlayRoute.Onboarding>(inclusive = true) },
            ) { _ ->
                OnboardingOverlay()
            }
        }

        // Plan upgrade
        dialog<OverlayRoute.Upgrade>(dialogProperties = overlayDialogProperties) {
            OverlayDialog(
                pop = { navController.popBackStack<OverlayRoute.Upgrade>(inclusive = true) },
            ) { dismiss ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = if (proFlightClock != null) {
                            Modifier.fillMaxSize().proFlightPixelation(proFlightClock)
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        PlanUpgradedOverlay(
                            confirmed = planUpgradeConfirmed,
                            onDismiss = dismiss,
                            planAccountName = planAccountName,
                        )
                    }
                    if (proFlightClock != null) {
                        ProSunglassesFlight(clock = proFlightClock)
                    }
                }
            }
        }
    }
}

/**
 * One overlay in its dialog window. It slides up and fades in, as it did over
 * the app; `dismiss` slides it down and fades it out, then `pop` takes its
 * destination off the back stack. The window does not dim what is under it:
 * the overlay covers the screen itself.
 */
@Composable
private fun OverlayDialog(
    pop: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(0f)
    }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    // popped once the exit animation is done
    LaunchedEffect(visibleState.isIdle, visibleState.currentState, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            pop()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
    ) {
        content {
            visibleState.targetState = false
        }
    }
}
