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

// ── Sign in with Apple: Apple's OAuth web flow in a Custom Tab ──
// Apple has no Android SDK. The app opens Apple's authorize page in a Custom
// Tab; Apple posts the result to the api's /auth/apple/callback, which hands it
// straight back through `ur://oauth/apple?state=…&id_token=…` (or `&error=…`),
// handled by the LoginActivity like any other `ur://` link, and turned into the
// same /auth/login call the Google button makes. `state` ties the return to
// this launch (it also carries the platform claim the callback reads to pick
// the `ur://` scheme); `nonce` must come back inside the identity token, so a
// token minted elsewhere cannot be replayed. The server verifies the token's
// signature and audience (the Services ID) in /auth/login.
const val APPLE_OAUTH_AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize"
// The Apple Services ID: the web flow's client id, the one ur.io signs in with
const val APPLE_OAUTH_SERVICES_ID = "network.ur.service"
const val APPLE_OAUTH_CALLBACK_PATH = "/auth/apple/callback"
const val APPLE_OAUTH_RETURN_SCHEME = "ur"
const val APPLE_OAUTH_RETURN_HOST = "oauth"
const val APPLE_OAUTH_RETURN_PATH = "/apple"
const val APPLE_OAUTH_PLATFORM = "android"
const val AUTH_JWT_TYPE_APPLE = "apple"
private const val APPLE_OAUTH_PREFS = "apple_oauth"
private const val APPLE_OAUTH_MAX_AGE_MILLIS = 10 * 60 * 1000L

class PendingAppleOAuth(
    val state: String,
    val nonce: String,
    val createdMillis: Long,
)

/** base64url without padding, the encoding of the state and of the random tokens. */
private fun base64Url(bytes: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * The state of one attempt: base64url of `{"platform":"android","token":…}`.
 * Opaque to Apple; the api callback reads the platform claim to pick the
 * return scheme (`ur://` here), everything else is the random token.
 */
fun appleOAuthState(token: String): String =
    base64Url("{\"platform\":\"$APPLE_OAUTH_PLATFORM\",\"token\":\"$token\"}".toByteArray(Charsets.UTF_8))

/** Apple's authorize url for one attempt; `apiUrl` is the api origin the callback lives on. */
fun appleOAuthAuthorizeUrl(apiUrl: String, state: String, nonce: String): String {
    fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    val redirectUri = apiUrl.trimEnd('/') + APPLE_OAUTH_CALLBACK_PATH
    return APPLE_OAUTH_AUTHORIZE_URL +
            "?client_id=${enc(APPLE_OAUTH_SERVICES_ID)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&response_type=${enc("code id_token")}" +
            "&response_mode=form_post" +
            "&scope=${enc("name email")}" +
            "&state=${enc(state)}" +
            "&nonce=${enc(nonce)}"
}

/**
 * One attempt at a time, kept in preferences rather than memory: the browser
 * round trip can outlive this process, and the return must still be matched.
 */
object AppleOAuthSession {
    private fun token(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun begin(context: Context): PendingAppleOAuth {
        val pending = PendingAppleOAuth(appleOAuthState(token()), token(), System.currentTimeMillis())
        context.getSharedPreferences(APPLE_OAUTH_PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", pending.state)
            .putString("nonce", pending.nonce)
            .putLong("created", pending.createdMillis)
            .apply()
        return pending
    }

    /** The pending attempt for `state`, consumed; null when it does not match or is stale. */
    fun take(context: Context, state: String?): PendingAppleOAuth? {
        if (state.isNullOrEmpty()) return null
        val prefs = context.getSharedPreferences(APPLE_OAUTH_PREFS, Context.MODE_PRIVATE)
        val pending = PendingAppleOAuth(
            prefs.getString("state", "") ?: "",
            prefs.getString("nonce", "") ?: "",
            prefs.getLong("created", 0L),
        )
        prefs.edit().clear().apply()
        if (pending.state.isEmpty() || pending.state != state) return null
        if (System.currentTimeMillis() - pending.createdMillis > APPLE_OAUTH_MAX_AGE_MILLIS) return null
        return pending
    }
}

/** `ur://oauth/apple?…`: the callback's return for this app. */
fun isAppleOAuthReturn(uri: Uri): Boolean =
    uri.scheme == APPLE_OAUTH_RETURN_SCHEME && uri.host == APPLE_OAUTH_RETURN_HOST && uri.path == APPLE_OAUTH_RETURN_PATH

/**
 * Opens Apple's sign-in in a Custom Tab; false when the api origin is unknown
 * or no browser could be opened.
 */
fun launchAppleOAuth(context: Context, apiUrl: String?): Boolean {
    if (apiUrl.isNullOrEmpty()) {
        Log.i("LoginUtils", "apple sign-in: no api url for the callback")
        return false
    }
    val pending = AppleOAuthSession.begin(context)
    return launchInBrowser(context, Uri.parse(appleOAuthAuthorizeUrl(apiUrl, pending.state, pending.nonce)))
}

/**
 * The display name from the `user` JSON Apple sends with the FIRST
 * authorization only: `{"name":{"firstName":…,"lastName":…},"email":…}`.
 * Empty when absent or unreadable.
 */
fun appleOAuthUserName(user: String?): String {
    if (user.isNullOrEmpty()) return ""
    return try {
        val name = JSONObject(user).optJSONObject("name") ?: return ""
        listOf(name.optString("firstName"), name.optString("lastName"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    } catch (e: Exception) {
        ""
    }
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
