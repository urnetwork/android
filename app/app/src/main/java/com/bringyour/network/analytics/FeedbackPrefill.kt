package com.bringyour.network.analytics

/**
 * What a campaign feedback link (ur.io/f/<token>?r=n|why=x) carried into the
 * app: the token, and the rating or reason already chosen in the email. The
 * feedback screen resolves the token with the server for the pre-fill and
 * reports the reason with the submitted feedback.
 */
data class FeedbackPrefill(
    val token: String,
    val rating: Int,
    val reason: String,
)
