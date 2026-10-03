package com.bringyour.network.ui.login

/**
 * When a new code (or reset link) can be requested after the server refused one
 * for too many attempts. The send button stays disabled until `retryAtMillis`,
 * and the rate-limit notice counts down the minutes left. Times come from the
 * caller's clock so the logic is testable.
 */
data class ResendCooldown(val retryAtMillis: Long) {

    fun remainingSeconds(nowMillis: Long): Long =
        maxOf(0L, (retryAtMillis - nowMillis + 999) / 1000)

    fun canResend(nowMillis: Long): Boolean = remainingSeconds(nowMillis) == 0L

    /**
     * The notice to show at `nowMillis`: the rate limit with the minutes left
     * (rounded up, at least one), or `Sent` (nothing to show) once a new code
     * can be requested.
     */
    fun notice(nowMillis: Long): VerifySendNotice {
        val remainingSeconds = remainingSeconds(nowMillis)
        if (remainingSeconds == 0L) {
            return VerifySendNotice.Sent
        }
        return VerifySendNotice.RateLimited(maxOf(1L, (remainingSeconds + 59) / 60).toInt())
    }

    companion object {
        /** The cooldown a send error starts at `nowMillis`, or null when it is not a timed rate limit. */
        fun after(sendError: VerifySendError?, nowMillis: Long): ResendCooldown? {
            if (sendError == null ||
                sendError.code != VerifySendNotice.CODE_RATE_LIMITED ||
                sendError.retryAfterSeconds <= 0
            ) {
                return null
            }
            return ResendCooldown(nowMillis + sendError.retryAfterSeconds * 1000)
        }
    }
}

/** This notice at `nowMillis`: a rate limit with a cooldown counts down and then clears. */
fun VerifySendNotice.at(cooldown: ResendCooldown?, nowMillis: Long): VerifySendNotice =
    if (this is VerifySendNotice.RateLimited && cooldown != null) cooldown.notice(nowMillis) else this
