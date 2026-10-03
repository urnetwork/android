package com.bringyour.network.ui.login

import android.net.Uri

/**
 * Why the server did not send a verification code: the sdk's
 * AuthVerifySendError, kept sdk-free so it can be carried in the verify route
 * and tested on the JVM.
 */
data class VerifySendError(
    val code: String,
    val message: String,
    // seconds until a new code can be requested; zero when not known
    val retryAfterSeconds: Long,
)

/**
 * What the verify screen says about the verification code. The server used to
 * drop send failures and rate limits, so the screen said a code was sent when
 * none was; it now reports them and the screen must not claim a sent code.
 */
sealed class VerifySendNotice {
    object Sent : VerifySendNotice()

    // the email/SMS send failed, or the request did not reach the server
    object SendFailed : VerifySendNotice()

    // too many attempts; a new code can be requested in `minutes`
    data class RateLimited(val minutes: Int) : VerifySendNotice()

    // the server's own reason, for a code this app does not know
    data class ServerMessage(val message: String) : VerifySendNotice()

    companion object {
        const val CODE_SEND_FAILED = "verify_send_failed"
        const val CODE_RATE_LIMITED = "verify_rate_limited"

        /**
         * `transportError` is any request failure (no api, network error,
         * non-200 answer). `sendError` is the server's send error, null when a
         * code was sent.
         */
        fun from(transportError: Boolean, sendError: VerifySendError?): VerifySendNotice = when {
            transportError -> SendFailed
            sendError == null -> Sent
            sendError.code == CODE_RATE_LIMITED && 0 < sendError.retryAfterSeconds ->
                RateLimited(maxOf(1L, (sendError.retryAfterSeconds + 59) / 60).toInt())
            sendError.code == CODE_SEND_FAILED -> SendFailed
            sendError.message.isNotEmpty() -> ServerMessage(sendError.message)
            else -> SendFailed
        }
    }
}

/** The verify screen route, carrying the send error of the login or sign-up that opened it. */
fun verifyRoute(userAuth: String, sendError: VerifySendError?): String {
    val route = "verify/${Uri.encode(userAuth)}"
    if (sendError == null) {
        return route
    }
    return "$route?sendErrorCode=${Uri.encode(sendError.code)}" +
        "&sendErrorMessage=${Uri.encode(sendError.message)}" +
        "&sendErrorRetryAfterSeconds=${sendError.retryAfterSeconds}"
}
