package com.bringyour.network.ui.login

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * What the sign-in screen says about the sign-out that brought the user there
 * (server session/REVOKE-UI-FINAL.md §5). Only a cause the sdk trusts has a
 * notice; every other sign-out, the app's own included, shows nothing new.
 */
enum class SignInNotice(@get:StringRes val messageRes: Int) {
    // the server confirmed that another device signed this session out
    SignedOutRemotely(R.string.sessions_signed_out_remotely);

    companion object {
        /**
         * The notice for an AuthLogout whose device reported [cause]
         * (Device.getAuthLogoutCause, read in the listener). The sdk reports ""
         * for every sign-out it has no trusted cause for, among them the app's
         * own and a sign-out of this session from Account -> Sessions.
         */
        fun forAuthLogoutCause(cause: String?): SignInNotice? = when (cause) {
            Sdk.AuthLogoutCauseSessionRevoked -> SignedOutRemotely
            else -> null
        }
    }
}

/**
 * The sign-in screen's notice, held by the app (MainApplication.signInNotices)
 * from the sdk's AuthLogout until a sign-in screen takes it (LoginNavHost), so
 * it shows once, on whichever sign-in screen opens next. The app's own sign-out
 * and a new sign-in drop it.
 */
class SignInNotices {
    private val pending = MutableStateFlow<SignInNotice?>(null)

    /** The notice waiting for a sign-in screen, if any. */
    val waiting: StateFlow<SignInNotice?> = pending.asStateFlow()

    /**
     * The sdk signed this app out and its device reported [cause]: the notice
     * for that sign-out, if it has one, replaces any earlier one.
     */
    fun authLoggedOut(cause: String?) {
        pending.value = SignInNotice.forAuthLogoutCause(cause)
    }

    /** The user signed out, or signed in again: there is nothing to say. */
    fun clear() {
        pending.value = null
    }

    /** The waiting notice, for the one sign-in screen that shows it. */
    fun take(): SignInNotice? = pending.getAndUpdate { null }
}

/** The notice over the sign-in screen, until the user closes it. */
@Composable
fun SignInNoticeAlert(
    notice: SignInNotice,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(text = stringResource(id = notice.messageRes))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.close))
            }
        },
    )
}
