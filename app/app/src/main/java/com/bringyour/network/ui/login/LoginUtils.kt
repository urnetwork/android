package com.bringyour.network.ui.login
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorReturnAction
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.bittensorWalletDisplayName
import androidx.browser.customtabs.CustomTabsIntent
import com.bringyour.network.BuildConfig
import com.bringyour.network.LoginClientCompletion
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
 *
 * Legacy: new sign-ins and wallet connects use the manual proof
 * (BittensorProofSheets). This only continues a sign-in that an earlier app
 * version started through the bridge (its create-network second signature).
 */
/** A proven bridge return as the ur://bittensor-sign-message uri the return handling reads. */
fun bittensorProofUri(proof: BittensorProof): Uri = Uri.parse(BITTENSOR_SIGN_REDIRECT_LINK).buildUpon()
    .appendQueryParameter("address", proof.address)
    .appendQueryParameter("signature", proof.signature)
    .appendQueryParameter("message", proof.message)
    .appendQueryParameter("purpose", proof.purpose)
    .appendQueryParameter("wallet", proof.walletId)
    .build()

/** A refused bridge return as an error uri, so the flow shows `message`. */
fun bittensorFailureUri(failed: BittensorReturnAction.Failed, message: String): Uri =
    Uri.parse(BITTENSOR_SIGN_REDIRECT_LINK).buildUpon()
        .appendQueryParameter("errorCode", failed.code)
        .appendQueryParameter("errorMessage", message)
        .appendQueryParameter("purpose", failed.purpose)
        .build()

/** The text for a refused bridge return, in this app's words where it has them. */
fun Context.bittensorRefusalMessage(failed: BittensorReturnAction.Failed): String =
    BittensorWallets.refusalText(
        failed.code,
        failed.detail,
        failed.bridgeCode,
        bittensorWalletDisplayName(failed.walletId),
    ) { res, walletName -> if (walletName == null) getString(res) else getString(res, walletName) }

/**
 * Opens a Bittensor bridge page (sdk BittensorWalletSession.bridgeUrl, e.g.
 * WalletConnect on ur.io/bittensor-connect) in a Custom Tab, falling back to
 * the browser. The page returns on ur://bittensor-sign-message.
 */
fun launchBittensorBridge(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    return try {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, uri)
        true
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            Log.i("LoginUtils", "unable to open the bittensor bridge: ${e.message}")
            false
        }
    }
}

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

// ── Sign in with Apple, and with Google in the browser: OAuth web flows in a Custom Tab ──
// Apple has no Android SDK, and the github (F-Droid) build carries no Play
// services, so neither has a native sign-in there. The app opens the
// provider's authorize page in a Custom Tab with the api as the redirect:
//
// - Apple posts the result to the api's /auth/apple/callback (form_post).
// - Google redirects an authorization code to the api's /auth/google/callback,
//   which exchanges it for the identity token with the ur.io web client's
//   secret (Google hands an identity token only to a server).
//
// Either callback hands the identity token straight back through
// `ur://oauth/<provider>?state=…&id_token=…` (or `&error=…`), handled by the
// LoginActivity like any other `ur://` link, and turned into the same
// /auth/login (or /auth/add-auth) call a native sign-in makes. `state` ties
// the return to this launch (it also carries the platform claim the callback
// reads to pick the `ur://` scheme); `nonce` must come back inside the
// identity token, so a token minted elsewhere cannot be replayed. The server
// verifies the token's signature and audience (Apple's Services ID, the
// Google web client id) in /auth/login and /auth/add-auth.
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
private const val SSO_OAUTH_MAX_AGE_MILLIS = 10 * 60 * 1000L

const val GOOGLE_OAUTH_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
// The ur.io web sign-in client (also the windows, linux and macos browser
// flows); the api's callback holds its secret. Not the Android client in
// google.xml, which only the Play services sign-in can use.
const val GOOGLE_OAUTH_WEB_CLIENT_ID = "338638865390-cg4m0t700mq9073smhn9do81mr640ig1.apps.googleusercontent.com"
const val GOOGLE_OAUTH_CALLBACK_PATH = "/auth/google/callback"
const val GOOGLE_OAUTH_RETURN_PATH = "/google"
const val AUTH_JWT_TYPE_GOOGLE = "google"
private const val GOOGLE_OAUTH_PREFS = "google_oauth"

/** A provider with a browser flow; `authJwtType` is the auth_jwt_type it signs in with. */
enum class SsoProvider(val authJwtType: String, val returnPath: String) {
    APPLE(AUTH_JWT_TYPE_APPLE, APPLE_OAUTH_RETURN_PATH),
    GOOGLE(AUTH_JWT_TYPE_GOOGLE, GOOGLE_OAUTH_RETURN_PATH),
}

/**
 * The login stack's leading sign-in buttons, in the play flavor's order:
 * Google then Apple where the build offers them (BRINGYOUR_BUNDLE_SSO_GOOGLE),
 * none otherwise. The browser-flow build (github) lays out its stack from this.
 */
fun loginSsoProviders(ssoGoogle: Boolean): List<SsoProvider> =
    if (ssoGoogle) listOf(SsoProvider.GOOGLE, SsoProvider.APPLE) else listOf()

// Why an attempt was started. The return comes back on the same link either
// way; the purpose decides who may take it. A login takes only a login
// attempt, and the add sheet only an add attempt, so an Apple ID or Google
// account being added to the signed-in network can never sign in as itself.
const val SSO_OAUTH_PURPOSE_LOGIN = "login"
const val SSO_OAUTH_PURPOSE_ADD = "add"

class PendingSsoOAuth(
    val state: String,
    val nonce: String,
    val createdMillis: Long,
    val purpose: String = SSO_OAUTH_PURPOSE_LOGIN,
)

/** base64url without padding, the encoding of the state and of the random tokens. */
private fun base64Url(bytes: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * The state of one attempt: base64url of `{"platform":"android","token":…}`.
 * Opaque to the provider; the api callback reads the platform claim to pick
 * the return scheme (`ur://` here), everything else is the random token.
 */
fun appleOAuthState(token: String): String =
    base64Url("{\"platform\":\"$APPLE_OAUTH_PLATFORM\",\"token\":\"$token\"}".toByteArray(Charsets.UTF_8))

private fun oauthQueryEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** Apple's authorize url for one attempt; `apiUrl` is the api origin the callback lives on. */
fun appleOAuthAuthorizeUrl(apiUrl: String, state: String, nonce: String): String {
    val redirectUri = apiUrl.trimEnd('/') + APPLE_OAUTH_CALLBACK_PATH
    return APPLE_OAUTH_AUTHORIZE_URL +
            "?client_id=${oauthQueryEncode(APPLE_OAUTH_SERVICES_ID)}" +
            "&redirect_uri=${oauthQueryEncode(redirectUri)}" +
            "&response_type=${oauthQueryEncode("code id_token")}" +
            "&response_mode=form_post" +
            "&scope=${oauthQueryEncode("name email")}" +
            "&state=${oauthQueryEncode(state)}" +
            "&nonce=${oauthQueryEncode(nonce)}"
}

/**
 * Google's authorize url for one attempt (the code flow; the api's callback
 * exchanges the code). The same request the desktop apps make, so the
 * callback url registered for the web client already covers it.
 */
fun googleOAuthAuthorizeUrl(apiUrl: String, state: String, nonce: String): String {
    val redirectUri = apiUrl.trimEnd('/') + GOOGLE_OAUTH_CALLBACK_PATH
    return GOOGLE_OAUTH_AUTHORIZE_URL +
            "?client_id=${oauthQueryEncode(GOOGLE_OAUTH_WEB_CLIENT_ID)}" +
            "&redirect_uri=${oauthQueryEncode(redirectUri)}" +
            "&response_type=code" +
            "&scope=${oauthQueryEncode("openid email profile")}" +
            "&state=${oauthQueryEncode(state)}" +
            "&nonce=${oauthQueryEncode(nonce)}" +
            "&prompt=select_account"
}

/** Where the one pending attempt of a provider is kept. */
interface SsoOAuthStore {
    fun load(): PendingSsoOAuth?
    fun save(pending: PendingSsoOAuth)
    fun clear()
}

/**
 * One attempt at a time per provider. [take] hands the attempt only to the
 * flow it was started for, and leaves another flow's attempt in place: a login
 * never consumes (or signs in with) the return of an add attempt, and the add
 * sheet never consumes a login's. Times come from `nowMillis` so tests inject
 * the clock.
 */
class SsoOAuthAttempts(
    private val store: SsoOAuthStore,
    private val nowMillis: () -> Long,
    private val token: () -> String,
) {
    fun begin(purpose: String): PendingSsoOAuth {
        val pending = PendingSsoOAuth(appleOAuthState(token()), token(), nowMillis(), purpose)
        store.save(pending)
        return pending
    }

    /** The purpose of the pending attempt for `state`, null when there is none. */
    fun purposeOf(state: String?): String? {
        if (state.isNullOrEmpty()) return null
        val pending = store.load() ?: return null
        return if (pending.state == state) pending.purpose else null
    }

    /**
     * The pending attempt for `state` started for `purpose`, consumed; null
     * when it does not match or is stale. An attempt for another purpose is
     * not consumed.
     */
    fun take(state: String?, purpose: String): PendingSsoOAuth? {
        if (state.isNullOrEmpty()) return null
        val pending = store.load() ?: return null
        if (pending.state.isEmpty() || pending.state != state) {
            store.clear()
            return null
        }
        if (pending.purpose != purpose) return null
        store.clear()
        if (nowMillis() - pending.createdMillis > SSO_OAUTH_MAX_AGE_MILLIS) return null
        return pending
    }

    /**
     * Drops the pending attempt, whatever its purpose: a sign-out ends every
     * attempt the app started, so a late return matches none.
     */
    fun clear() {
        store.clear()
    }
}

private fun ssoOAuthToken(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return base64Url(bytes)
}

/**
 * A provider's attempts kept in preferences rather than memory: the browser
 * round trip can outlive this process, and the return must still be matched.
 * Each provider has its own preferences, so an Apple attempt and a Google
 * attempt never replace each other.
 */
private fun ssoOAuthAttemptsInPrefs(context: Context, prefsName: String): SsoOAuthAttempts {
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val store = object : SsoOAuthStore {
        override fun load(): PendingSsoOAuth? {
            val state = prefs.getString("state", "") ?: ""
            if (state.isEmpty()) return null
            return PendingSsoOAuth(
                state,
                prefs.getString("nonce", "") ?: "",
                prefs.getLong("created", 0L),
                // an attempt saved before purposes were kept was a login
                prefs.getString("purpose", null) ?: SSO_OAUTH_PURPOSE_LOGIN,
            )
        }

        override fun save(pending: PendingSsoOAuth) {
            prefs.edit()
                .putString("state", pending.state)
                .putString("nonce", pending.nonce)
                .putLong("created", pending.createdMillis)
                .putString("purpose", pending.purpose)
                .apply()
        }

        override fun clear() {
            prefs.edit().clear().apply()
        }
    }
    return SsoOAuthAttempts(store, System::currentTimeMillis, ::ssoOAuthToken)
}

/** Apple's pending attempt. */
object AppleOAuthSession {
    fun attempts(context: Context): SsoOAuthAttempts = ssoOAuthAttemptsInPrefs(context, APPLE_OAUTH_PREFS)

    fun begin(context: Context, purpose: String = SSO_OAUTH_PURPOSE_LOGIN): PendingSsoOAuth =
        attempts(context).begin(purpose)

    /** The login's attempt for `state`, consumed; see [SsoOAuthAttempts.take]. */
    fun take(context: Context, state: String?, purpose: String = SSO_OAUTH_PURPOSE_LOGIN): PendingSsoOAuth? =
        attempts(context).take(state, purpose)
}

/** Google's pending browser attempt (the github flavor). */
object GoogleOAuthSession {
    fun attempts(context: Context): SsoOAuthAttempts = ssoOAuthAttemptsInPrefs(context, GOOGLE_OAUTH_PREFS)
}

/** A provider's pending attempts. */
fun ssoOAuthAttempts(context: Context, provider: SsoProvider): SsoOAuthAttempts = when (provider) {
    SsoProvider.APPLE -> AppleOAuthSession.attempts(context)
    SsoProvider.GOOGLE -> GoogleOAuthSession.attempts(context)
}

/** Who takes a browser sign-in return. */
enum class SsoOAuthReturnRoute {
    // the login screen (/auth/login)
    LOGIN,
    // the add sign-in method sheet (/auth/add-auth on the signed-in network)
    ADD_SIGN_IN,
}

/**
 * The route of a return, by the purpose of the attempt its state names.
 * Anything that is not a pending add attempt goes to the login, which refuses
 * a stale or forged state as before.
 */
fun ssoOAuthReturnRoute(attempts: SsoOAuthAttempts, state: String?): SsoOAuthReturnRoute =
    if (attempts.purposeOf(state) == SSO_OAUTH_PURPOSE_ADD) {
        SsoOAuthReturnRoute.ADD_SIGN_IN
    } else {
        SsoOAuthReturnRoute.LOGIN
    }

/**
 * The provider of a callback return (`ur://oauth/apple?…`, `ur://oauth/google?…`),
 * null for any other link. Pure, on the link's parts, so tests need no Uri.
 */
fun ssoOAuthReturnProvider(scheme: String?, host: String?, path: String?): SsoProvider? {
    if (scheme != APPLE_OAUTH_RETURN_SCHEME || host != APPLE_OAUTH_RETURN_HOST) return null
    return SsoProvider.entries.firstOrNull { it.returnPath == path }
}

fun ssoOAuthReturnProvider(uri: Uri): SsoProvider? = ssoOAuthReturnProvider(uri.scheme, uri.host, uri.path)

/** `ur://oauth/apple?…`: the callback's return for this app. */
fun isAppleOAuthReturn(uri: Uri): Boolean = ssoOAuthReturnProvider(uri) == SsoProvider.APPLE

/** `ur://oauth/google?…`: the Google callback's return for this app. */
fun isGoogleOAuthReturn(uri: Uri): Boolean = ssoOAuthReturnProvider(uri) == SsoProvider.GOOGLE

/** A return's parts, read off the link. */
data class SsoOAuthReturn(
    val state: String?,
    val idToken: String?,
    val error: String?,
)

fun ssoOAuthReturn(uri: Uri): SsoOAuthReturn = SsoOAuthReturn(
    state = uri.getQueryParameter("state"),
    idToken = uri.getQueryParameter("id_token"),
    error = uri.getQueryParameter("error"),
)

sealed class SsoLoginOutcome {
    // the identity token of this login's attempt, for /auth/login
    data class SignIn(val authJwt: String, val authJwtType: String) : SsoLoginOutcome()
    // the provider or callback reported an error, or the token is not this attempt's
    data class Failed(val error: String?) : SsoLoginOutcome()
    // no login attempt is waiting for this state (stale, forged, or another flow's)
    object NoAttempt : SsoLoginOutcome()
}

/**
 * Checks a return against the pending login attempt: the state must name it
 * (consumed here) and the token must carry its nonce. `nonceOf` reads the
 * token's nonce claim (the server verifies the signature). An add attempt's
 * return is never a sign-in.
 */
fun ssoLoginOutcome(
    provider: SsoProvider,
    attempts: SsoOAuthAttempts,
    ssoReturn: SsoOAuthReturn,
    nonceOf: (String) -> String?,
): SsoLoginOutcome {
    val pending = attempts.take(ssoReturn.state, SSO_OAUTH_PURPOSE_LOGIN) ?: return SsoLoginOutcome.NoAttempt
    val idToken = ssoReturn.idToken
    if (ssoReturn.error != null || idToken.isNullOrEmpty()) {
        return SsoLoginOutcome.Failed(ssoReturn.error)
    }
    if (nonceOf(idToken) != pending.nonce) {
        return SsoLoginOutcome.Failed(null)
    }
    return SsoLoginOutcome.SignIn(idToken, provider.authJwtType)
}

/**
 * Opens a provider's sign-in in a Custom Tab; false when the api origin is
 * unknown or no browser could be opened.
 */
fun launchSsoOAuth(
    context: Context,
    provider: SsoProvider,
    apiUrl: String?,
    purpose: String = SSO_OAUTH_PURPOSE_LOGIN,
): Boolean {
    if (apiUrl.isNullOrEmpty()) {
        Log.i("LoginUtils", "${provider.authJwtType} sign-in: no api url for the callback")
        return false
    }
    val pending = ssoOAuthAttempts(context, provider).begin(purpose)
    val authorizeUrl = when (provider) {
        SsoProvider.APPLE -> appleOAuthAuthorizeUrl(apiUrl, pending.state, pending.nonce)
        SsoProvider.GOOGLE -> googleOAuthAuthorizeUrl(apiUrl, pending.state, pending.nonce)
    }
    return launchInBrowser(context, Uri.parse(authorizeUrl))
}

/** Apple's sign-in in a Custom Tab; see [launchSsoOAuth]. */
fun launchAppleOAuth(context: Context, apiUrl: String?, purpose: String = SSO_OAUTH_PURPOSE_LOGIN): Boolean =
    launchSsoOAuth(context, SsoProvider.APPLE, apiUrl, purpose)

/** Google's sign-in in a Custom Tab, for the build without Play services; see [launchSsoOAuth]. */
fun launchGoogleOAuth(context: Context, apiUrl: String?, purpose: String = SSO_OAUTH_PURPOSE_LOGIN): Boolean =
    launchSsoOAuth(context, SsoProvider.GOOGLE, apiUrl, purpose)

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
    application: MainApplication?,
    finishAuthenticatedLoginAfterWelcome: () -> Unit,
    onErr: () -> Unit,
    onContentVisibilityChange: (Boolean) -> Unit,
    onWelcomeOverlayVisibilityChange: (Boolean) -> Unit,
) {
    val app = application
    if (app == null) {
        scope.launch { onErr() }
        return
    }
    app.authenticateNetworkSession(networkJwt, newNetwork = false) { completion ->
        if (completion is LoginClientCompletion.Ready) {
            finishAuthenticatedLoginAfterWelcome()
        }
        scope.launch {
            if (completion is LoginClientCompletion.Failed) {
                Log.i(TAG, "auth client and finish err: ${completion.message}")
                val visibility = loginRetryVisibility()
                onContentVisibilityChange(visibility.contentVisible)
                onWelcomeOverlayVisibilityChange(visibility.welcomeOverlayVisible)
                onErr()
                return@launch
            }

            onContentVisibilityChange(false)
            delay(500)
            onWelcomeOverlayVisibilityChange(true)
        }
    }
}
