package com.bringyour.network.ui.login

import android.content.Context
import com.bringyour.network.R
import com.bringyour.sdk.Sdk

/**
 * A Google sign-in that Credential Manager failed (the GetCredentialException
 * of signInWithGoogle, android#271), read by the sdk: writes the exception's
 * type to the sdk log that feedback uploads (Sdk.logGoogleSignInFailure), and
 * answers the message to show (Sdk.googleSignInFailureMessage), the localized
 * sign-in error followed by the type's short name, or null when the user
 * closed the account picker. The type tells the causes apart; no_credential,
 * for one, is a device with no Google account the sheet could offer.
 *
 * Takes the type rather than the exception, so that it needs nothing of
 * Credential Manager and builds in every flavor.
 */
fun googleSignInFailureMessage(context: Context, credentialType: String): String? {
    Sdk.logGoogleSignInFailure(Sdk.GoogleSignInFailureSourceAndroidCredential, credentialType, 0L)
    val message = Sdk.googleSignInFailureMessage(
        Sdk.GoogleSignInFailureSourceAndroidCredential,
        credentialType,
        0L,
        context.getString(R.string.error_signing_in_with_google),
    )
    return message.ifEmpty { null }
}
