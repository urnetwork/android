package com.bringyour.network.analytics

import android.net.Uri

/**
 * A campaign email landing link opened the app: `https://ur.io/c?onboarding=<step>`
 * (the app link the ur.io landing page redirects to after logging the click)
 * or `ur://onboarding/<step>`, where step is connect | widgets | offer |
 * feedback. The feedback step may carry the feedback token and the rating or
 * reason chosen in the email (`t=<token>&r=n|why=x`).
 *
 * The step is handed to the app as a widget-style route (MainApplication
 * .widgetRoute, observed by MainNavHost) so it works whether the app was
 * cold-started for the link or was already running.
 */
data class OnboardingLink(
    val step: String,
    val feedbackPrefill: FeedbackPrefill? = null,
) {
    /** The MainNavHost route for the step. */
    val route: String get() = ROUTE_PREFIX + step

    companion object {
        const val STEP_CONNECT = "connect"
        const val STEP_WIDGETS = "widgets"
        const val STEP_OFFER = "offer"
        const val STEP_FEEDBACK = "feedback"
        const val ROUTE_PREFIX = "onboarding_"

        private val STEPS = setOf(STEP_CONNECT, STEP_WIDGETS, STEP_OFFER, STEP_FEEDBACK)

        fun parse(uri: Uri): OnboardingLink? {
            val step = when {
                uri.scheme == "ur" && uri.host == "onboarding" ->
                    uri.pathSegments.firstOrNull()
                uri.scheme == "https" && uri.host == "ur.io" && uri.path == "/c" ->
                    uri.getQueryParameter("onboarding")
                else -> null
            }?.lowercase()?.takeIf { it in STEPS } ?: return null
            val prefill = if (step == STEP_FEEDBACK) {
                uri.getQueryParameter("t")?.takeIf { it.isNotBlank() }?.let { token ->
                    FeedbackPrefill(
                        token = token,
                        rating = uri.getQueryParameter("r")?.toIntOrNull()?.coerceIn(0, 5) ?: 0,
                        reason = uri.getQueryParameter("why") ?: "",
                    )
                }
            } else {
                null
            }
            return OnboardingLink(step, prefill)
        }

        /** The step for a MainNavHost route made by [route]; null for other routes. */
        fun stepForRoute(route: String): String? =
            route.removePrefix(ROUTE_PREFIX).takeIf { route.startsWith(ROUTE_PREFIX) && it in STEPS }
    }
}
