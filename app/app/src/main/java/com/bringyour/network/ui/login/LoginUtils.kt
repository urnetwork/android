package com.bringyour.network.ui.login
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.bringyour.network.BuildConfig
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthWalletChallengeArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.coroutines.resume

private const val BITTENSOR_SIGN_REDIRECT_LINK = "ur://bittensor-sign-message"
const val BITTENSOR_SIGN_PURPOSE_LOGIN = "login"
const val BITTENSOR_SIGN_PURPOSE_CREATE = "create"
// attach a coldkey to the earnings screen (signed by the wallet, verified by the server)
const val BITTENSOR_SIGN_PURPOSE_CONNECT = "connect"

/**
 * Fetch a single-use, server-issued challenge. A wallet address is supplied for
 * the create-network hop so a callback from a different wallet cannot be used.
 */
suspend fun requestBittensorChallenge(api: Api, walletAddress: String? = null): Result<String> {
    val challengeArgs = AuthWalletChallengeArgs()
    challengeArgs.blockchain = "TAO"
    if (!walletAddress.isNullOrBlank()) {
        challengeArgs.walletAddress = walletAddress
    }

    return suspendCancellableCoroutine { continuation ->
        api.authWalletChallenge(challengeArgs) { result, err ->
            if (!continuation.isActive) {
                return@authWalletChallenge
            }

            when {
                err != null -> continuation.resume(Result.failure(err))
                result == null -> continuation.resume(
                    Result.failure(IllegalStateException("Wallet challenge response was empty"))
                )
                result.error != null -> continuation.resume(
                    Result.failure(IllegalStateException(result.error.message))
                )
                result.messageTemplate.isNullOrBlank() -> continuation.resume(
                    Result.failure(IllegalStateException("Wallet challenge message was empty"))
                )
                else -> continuation.resume(Result.success(result.messageTemplate))
            }
        }
    }
}

/**
 * Opens the ur.io wallet-connect bridge to sign a message with a Bittensor wallet.
 * The bridge redirects back to the app as
 * `ur://bittensor-sign-message?address=<ss58>&signature=<0xhex>&message=...&purpose=...`
 * (or `?errorCode=...&errorMessage=...`), which is handled by the LoginActivity. The
 * `connect` purpose is forwarded to the MainActivity for the earnings screen.
 */
fun launchBittensorSignMessage(
    context: Context,
    message: String,
    purpose: String,
): Boolean {
    require(message.isNotBlank()) { "A server-issued wallet challenge is required" }
    require(
        purpose == BITTENSOR_SIGN_PURPOSE_LOGIN ||
                purpose == BITTENSOR_SIGN_PURPOSE_CREATE ||
                purpose == BITTENSOR_SIGN_PURPOSE_CONNECT
    ) {
        "Unknown Bittensor signing purpose"
    }

    // the WalletConnect Cloud project id (local.properties) lets the bridge
    // pair with a wallet app; without it the bridge uses injected wallets only
    val walletConnectProjectId = BuildConfig.WALLETCONNECT_PROJECT_ID
    val walletConnectParam = if (walletConnectProjectId.isNotEmpty()) {
        "&wc_project_id=${Uri.encode(walletConnectProjectId)}"
    } else {
        ""
    }

    val uri = Uri.parse(
        "https://ur.io/wallet-connect" +
                "?provider=bittensor" +
                "&method=signMessage" +
                "&message=${Uri.encode(message)}" +
                "&purpose=${Uri.encode(purpose)}" +
                "&redirect_link=${Uri.encode(BITTENSOR_SIGN_REDIRECT_LINK)}" +
                walletConnectParam
    )

    return try {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, uri)
        true
    } catch (e: Exception) {
        // fall back to a plain browser intent if custom tabs are unavailable
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            Log.i("LoginUtils", "unable to launch bittensor sign message: ${e.message}")
            false
        }
    }
}

// ── the ur.io sso bridge: Apple (and Google where there is no native flow) ──
// The same browser round trip as the Bittensor sign-in: a Custom Tab opens
// https://ur.io/sso, the page runs the site's own Apple / Google sign-in, and
// redirects back to `ur://sso?provider=…&auth_jwt=…&state=…` (or `&error=…`),
// which the LoginActivity turns into the same /auth/login call the Google
// button makes. `state` ties the return to this launch; `nonce` must come back
// inside the identity token, so a token minted elsewhere cannot be replayed.
const val SSO_REDIRECT_LINK = "ur://sso"
const val SSO_PROVIDER_APPLE = "apple"
const val SSO_PROVIDER_GOOGLE = "google"
private const val SSO_PREFS = "sso_bridge"
private const val SSO_MAX_AGE_MILLIS = 10 * 60 * 1000L

class PendingSso(
    val provider: String,
    val state: String,
    val nonce: String,
    val createdMillis: Long,
)

/**
 * One attempt at a time, kept in preferences rather than memory: the browser
 * round trip can outlive this process, and the return must still be matched.
 */
object SsoBridgeSession {
    private fun token(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun begin(context: Context, provider: String): PendingSso {
        val pending = PendingSso(provider, token(), token(), System.currentTimeMillis())
        context.getSharedPreferences(SSO_PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", pending.provider)
            .putString("state", pending.state)
            .putString("nonce", pending.nonce)
            .putLong("created", pending.createdMillis)
            .apply()
        return pending
    }

    /** The pending attempt for `state`, consumed; null when it does not match or is stale. */
    fun take(context: Context, state: String?): PendingSso? {
        if (state.isNullOrEmpty()) return null
        val prefs = context.getSharedPreferences(SSO_PREFS, Context.MODE_PRIVATE)
        val pending = PendingSso(
            prefs.getString("provider", "") ?: "",
            prefs.getString("state", "") ?: "",
            prefs.getString("nonce", "") ?: "",
            prefs.getLong("created", 0L),
        )
        prefs.edit().clear().apply()
        if (pending.state.isEmpty() || pending.state != state) return null
        if (System.currentTimeMillis() - pending.createdMillis > SSO_MAX_AGE_MILLIS) return null
        return pending
    }
}

/** Opens the ur.io sso bridge for `provider`; false when no browser could be opened. */
fun launchSsoBridge(context: Context, provider: String): Boolean {
    require(provider == SSO_PROVIDER_APPLE || provider == SSO_PROVIDER_GOOGLE) { "Unknown sso provider" }
    val pending = SsoBridgeSession.begin(context, provider)
    val uri = Uri.parse(
        "https://ur.io/sso" +
                "?provider=${Uri.encode(provider)}" +
                "&redirect_link=${Uri.encode(SSO_REDIRECT_LINK)}" +
                "&state=${Uri.encode(pending.state)}" +
                "&nonce=${Uri.encode(pending.nonce)}"
    )
    return launchInBrowser(context, uri)
}

/** The claims of an identity token (no signature check: the server verifies it). */
fun ssoJwtPayload(jwt: String): JSONObject? {
    val parts = jwt.split(".")
    if (parts.size < 2) return null
    return try {
        val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(payload, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}

private fun launchInBrowser(context: Context, uri: Uri): Boolean {
    return try {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, uri)
        true
    } catch (e: Exception) {
        // fall back to a plain browser intent if custom tabs are unavailable
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            Log.i("LoginUtils", "unable to open the browser: ${e.message}")
            false
        }
    }
}

/**
 * Used on LoginInitial on individual build flavors
 */
fun handleLoginFlow(
    networkJwt: String,
    scope: CoroutineScope,
    appLogin: (String) -> Unit,
    authClientAndFinish: (
        callback: (String?) -> Unit,
    ) -> Unit,
    onErr: () -> Unit,
    onContentVisibilityChange: (Boolean) -> Unit,
    onWelcomeOverlayVisibilityChange: (Boolean) -> Unit,
) {
    scope.launch {
        appLogin(networkJwt)

        onContentVisibilityChange(false)

        delay(500)

        onWelcomeOverlayVisibilityChange(true)

        delay(2250)

        authClientAndFinish { error ->
            if (error != null) {
                Log.i(TAG, "auth client and finish err: $error")
                onErr()
            }
        }
    }
}
