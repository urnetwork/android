package com.bringyour.network.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.AddAuthArgs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun GoogleAddAuthButton(
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    isAddingAuth: Boolean,
    onAdded: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current

    val googleClientId = stringResource(id = R.string.google_client_id)
    val googleSignInClient = remember(googleClientId) {
        val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, googleSignInOpts)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                onError(context.getString(R.string.could_not_get_google_id_token))
                return@rememberLauncherForActivityResult
            }
            val args = AddAuthArgs()
            args.authJwt = idToken
            args.authJwtType = "google"
            addAuth(
                args,
                {
                    Toast.makeText(context, context.getString(R.string.google_sign_in_method_added), Toast.LENGTH_SHORT).show()
                    onAdded()
                },
                { msg -> onError(msg) }
            )
        } catch (e: ApiException) {
            onError(context.getString(R.string.error_signing_in_with_google))
        }
    }

    Text(
        stringResource(id = R.string.sign_in_with_google_to_add_it),
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted
    )
    Spacer(modifier = Modifier.height(12.dp))
    URButton(
        onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
        enabled = !isAddingAuth
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.sign_in_with_google), style = buttonTextStyle)
    }
}
