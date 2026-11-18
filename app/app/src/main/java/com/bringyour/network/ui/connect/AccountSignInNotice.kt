package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The account's sign-in ended while this device stays signed in (server
 * session/REVOKE-FINAL.md, credential rejection; REVOKE-UI-FINAL.md §5): the
 * server rejected the account credential, the network credential the app uses
 * for the account's own requests (Account > Sessions among them), while this
 * device's client credential belongs to another session. The sdk has already
 * cleared the account credential and kept the device, and its api reports
 * AccountSignInRequired, not AuthLogout. So nothing signs out or disconnects:
 * the main screen says that the account needs a new sign-in, from the
 * listener's cause, until the user closes it, signs out or signs in again.
 * The Sessions screen keeps its own sign-in-required state.
 */

/** The sdk's AccountSignInRequired, as the notice says it. */
data class AccountSignInRequired(
    // the server confirmed that another device signed the account's sign-in out
    val signedOutRemotely: Boolean,
) {
    /** The notice's sentences, in order: why, when the sdk trusts the cause, then what to do. */
    val messageIds: List<Int>
        get() = listOfNotNull(
            R.string.sessions_signed_out_remotely.takeIf { signedOutRemotely },
            R.string.sessions_sign_in_required,
        )

    /** The notice's text: its sentences as one paragraph. */
    fun message(string: (Int) -> String): String = messageIds.joinToString(" ") { string(it) }

    companion object {
        /**
         * The notice for [cause], the api's Api.getAccountSignInRequiredCause read
         * in the listener: only Sdk.AuthLogoutCauseSessionRevoked says why; every
         * other cause has the generic sign-in required words.
         */
        fun forCause(cause: String?): AccountSignInRequired =
            AccountSignInRequired(signedOutRemotely = cause == Sdk.AuthLogoutCauseSessionRevoked)
    }
}

/**
 * The main screen's notice, held by the app (MainApplication.accountSignInNotices)
 * from the sdk's AccountSignInRequired until the user closes it, signs out or
 * signs in again. Main-thread confined.
 */
class AccountSignInNotices {
    private val shown = MutableStateFlow<AccountSignInRequired?>(null)

    /** The notice the main screen shows, if any. */
    val notice: StateFlow<AccountSignInRequired?> = shown.asStateFlow()

    /** The sdk reported AccountSignInRequired with [cause]: its notice replaces any earlier one. */
    fun accountSignInRequired(cause: String?) {
        shown.value = AccountSignInRequired.forCause(cause)
    }

    /** The user closed the notice, signed out, or signed in again. */
    fun clear() {
        shown.value = null
    }
}

/**
 * The notice on the main screen, with the connect buttons' other notices:
 * why the account's sign-in ended when the sdk trusts the cause, that a new
 * sign-in is needed to manage sessions, and a close. Nothing here signs out or
 * disconnects.
 */
@Composable
fun AccountSignInNotice() {
    val application = LocalContext.current.applicationContext as? MainApplication ?: return
    val notice by application.accountSignInNotices.notice.collectAsState()
    val current = notice ?: return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // the catalog's words for the configuration's locale
    val message = remember(current, context, configuration) { current.message(context::getString) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            // a message that wraps keeps the close at its first line
            verticalAlignment = Alignment.Top
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("acceptance.account_sign_in_notice")
            )
            // small; Compose still gives it a 48 dp touch target
            IconButton(
                onClick = { application.accountSignInNotices.clear() },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("acceptance.account_sign_in_notice_close")
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(id = R.string.close),
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
