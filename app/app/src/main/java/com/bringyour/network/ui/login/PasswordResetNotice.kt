package com.bringyour.network.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R

/**
 * What a password reset screen says about the reset link it asked for. The
 * server reports a link it did not send with the verify send error (with
 * `result_errors`); the screens used to ignore the result and say the link was
 * sent. `transportError` is any request failure, `sendError` the server's send
 * error, null when the link was sent.
 */
fun passwordResetNotice(transportError: Boolean, sendError: VerifySendError?): VerifySendNotice =
    VerifySendNotice.from(transportError, sendError)

/** The text for a password reset notice, or null when the link was sent. */
@Composable
fun passwordResetNoticeText(notice: VerifySendNotice): String? = when (notice) {
    VerifySendNotice.Sent -> null
    VerifySendNotice.SendFailed -> stringResource(id = R.string.error_sending_password_reset_link)
    is VerifySendNotice.RateLimited -> pluralStringResource(
        id = R.plurals.reset_link_rate_limited,
        count = notice.minutes,
        notice.minutes,
    )
    is VerifySendNotice.ServerMessage -> notice.message
}
