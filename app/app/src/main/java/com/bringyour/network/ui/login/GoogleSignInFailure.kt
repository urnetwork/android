package com.bringyour.network.ui.login

import android.content.Context
import com.bringyour.network.R
import com.bringyour.sdk.Sdk

/**
 * A Google sign-in that Google Play services failed (the ApiException of
 * GoogleSignIn.getSignedInAccountFromIntent), read by the sdk: writes the status
 * code to the sdk log that feedback uploads (Sdk.logGoogleSignInFailure), and
 * answers the message to show (Sdk.googleSignInFailureMessage), the localized
 * sign-in error followed by the code, or null when the user closed the account
 * picker. The code tells the causes apart; 10, for one, is an OAuth client or
 * signing certificate the Google project does not know.
 *
 * Takes the status code rather than the exception, so that every flavor can
 * call it, the ones without Google Play services included.
 */
fun googleSignInFailureMessage(context: Context, statusCode: Int): String? {
    val code = statusCode.toLong()
    Sdk.logGoogleSignInFailure(Sdk.GoogleSignInFailureSourceAndroid, "", code)
    val message = Sdk.googleSignInFailureMessage(
        Sdk.GoogleSignInFailureSourceAndroid,
        "",
        code,
        context.getString(R.string.error_signing_in_with_google),
    )
    return message.ifEmpty { null }
}
