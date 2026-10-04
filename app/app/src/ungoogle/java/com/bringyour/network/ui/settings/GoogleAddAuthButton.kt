package com.bringyour.network.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.launchGoogleOAuth
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.AddAuthArgs

/**
 * Adds Google on the github build, which is deliberately de-Googled (no
 * play-services-auth): Google's web flow in a Custom Tab, started as an add
 * attempt, like the sheet's Apple option. The return comes back through the
 * LoginActivity (ur://oauth/google), which hands it to the sheet
 * (SsoAddSignInReturns), and the sheet adds it with /auth/add-auth; `addAuth`
 * and `onAdded` are used by the Play services flavors' button only.
 */
@Composable
fun GoogleAddAuthButton(
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    isAddingAuth: Boolean,
    onAdded: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current

    Text(
        stringResource(id = R.string.sign_in_with_google_to_add_it),
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted
    )
    Spacer(modifier = Modifier.height(12.dp))
    URButton(
        onClick = {
            val apiUrl = (context.applicationContext as? MainApplication)
                ?.networkSpaceManagerProvider?.getNetworkSpace()?.apiUrl
            if (!launchGoogleOAuth(context, apiUrl, SSO_OAUTH_PURPOSE_ADD)) {
                onError(context.getString(R.string.login_error))
            }
        },
        enabled = !isAddingAuth,
        isProcessing = isAddingAuth
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.sign_in_with_google), style = buttonTextStyle)
    }
}
