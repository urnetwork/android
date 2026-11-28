package com.bringyour.network.ui.login

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/*
 * Sign in with Google through Credential Manager (androidx.credentials) and the
 * Google ID library, in place of Google Play services' deprecated GoogleSignIn
 * (android#271). The button flow (GetSignInWithGoogleOption) shows Google's
 * account picker on every sign-in and lets the user add an account, as the
 * GoogleSignIn picker did. It hands back the account's Google ID token, which
 * the server reads as before (auth jwt type "google").
 *
 * The same file is in each flavor with Google Play services (google,
 * solana_dapp, ethos_dapp); the others sign in with Google's web flow
 * (ungoogle GoogleAddAuthButton, LoginUtils launchSsoOAuth).
 */

/** A Google account's ID token, and the account's email address. */
data class GoogleIdToken(val idToken: String, val email: String?)

/** How a sign-in with Google ended. */
sealed interface GoogleIdSignIn {
    data class Signed(val token: GoogleIdToken) : GoogleIdSignIn

    /** `message` is what to show, null when the user closed the account picker. */
    data class Failed(val message: String?) : GoogleIdSignIn
}

/**
 * Asks Google for an ID token for `serverClientId` (the google_client_id
 * resource). `context` is the activity's, which Credential Manager shows its
 * sheet over. A failure is read by the sdk (googleSignInFailureMessage), and a
 * credential that is not a readable Google ID token is logged the same way and
 * says the token could not be got.
 */
suspend fun signInWithGoogle(context: Context, serverClientId: String): GoogleIdSignIn {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (e: GetCredentialException) {
        return GoogleIdSignIn.Failed(googleSignInFailureMessage(context, e.type))
    }
    val noToken: (String) -> GoogleIdSignIn = { type ->
        Sdk.logGoogleSignInFailure(Sdk.GoogleSignInFailureSourceAndroidCredential, type, 0L)
        GoogleIdSignIn.Failed(context.getString(R.string.could_not_get_google_id_token))
    }
    if (credential !is CustomCredential ||
        (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL &&
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
    ) {
        return noToken(credential.type)
    }
    val google = try {
        GoogleIdTokenCredential.createFrom(credential.data)
    } catch (e: GoogleIdTokenParsingException) {
        return noToken(Sdk.GoogleSignInCredentialParsingType)
    }
    return GoogleIdSignIn.Signed(GoogleIdToken(idToken = google.idToken, email = google.email ?: google.id))
}

/**
 * Forgets the account a sign-in last returned, so that the next one asks
 * again, as GoogleSignIn's signOut did when the login screen opened.
 */
suspend fun forgetGoogleSignIn(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: ClearCredentialException) {
        // nothing kept, or no provider to ask: nothing to forget
    }
}
