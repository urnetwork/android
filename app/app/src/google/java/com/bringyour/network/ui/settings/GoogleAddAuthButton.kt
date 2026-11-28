package com.bringyour.network.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.login.GoogleIdSignIn
import com.bringyour.network.ui.login.signInWithGoogle
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.AddAuthArgs
import kotlinx.coroutines.launch

@Composable
fun GoogleAddAuthButton(
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    isAddingAuth: Boolean,
    onAdded: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current

    val googleClientId = stringResource(id = R.string.google_client_id)
    val scope = rememberCoroutineScope()

    // Google's account picker through Credential Manager (GoogleIdSignIn.kt),
    // then the account's ID token added as a sign-in method
    val googleSignIn: () -> Unit = {
        scope.launch {
            when (val signIn = signInWithGoogle(context, googleClientId)) {
                is GoogleIdSignIn.Signed -> {
                    val args = AddAuthArgs()
                    args.authJwt = signIn.token.idToken
                    args.authJwtType = "google"
                    addAuth(
                        args,
                        {
                            Toast.makeText(context, context.getString(R.string.google_sign_in_method_added), Toast.LENGTH_SHORT).show()
                            onAdded()
                        },
                        { msg -> onError(msg) }
                    )
                }
                is GoogleIdSignIn.Failed -> signIn.message?.let { onError(it) }
            }
        }
    }

    Text(
        stringResource(id = R.string.sign_in_with_google_to_add_it),
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted
    )
    Spacer(modifier = Modifier.height(12.dp))
    URButton(
        onClick = googleSignIn,
        enabled = !isAddingAuth
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.sign_in_with_google), style = buttonTextStyle)
    }
}
