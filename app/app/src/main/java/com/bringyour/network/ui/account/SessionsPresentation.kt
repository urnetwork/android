package com.bringyour.network.ui.account

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.widgets.render.WidgetColors

/*
 * Account -> Sessions, as data (server/session/REVOKE-UI-FINAL.md §3-§5): the
 * sdk's ClientSessionSnapshot copied off its gomobile objects, and what the
 * screen shows for it. Everything here is plain data and functions, so the
 * JVM tests cover it without the sdk's native library; SessionsViewModel
 * copies the gomobile snapshot in and SessionsScreen renders the result.
 *
 * Errors are carried only as their flags: the screen never shows the
 * server's message.
 */

/** The sdk's SessionLastUsed: the server's last observed authenticated use. */
data class SessionLastUse(
    // UTC Unix seconds
    val unixTimeSeconds: Long,
    // location names; empty is unknown
    val city: String,
    val region: String,
    val country: String,
    // lower-case ISO alpha-2, or empty
    val countryCode: String,
    // android, ios, macos, windows, linux, web, cli, server or unknown
    val deviceType: String,
    // empty is unknown
    val appVersion: String,
)

/** The sdk's NetworkSessionInfo, as far as the screen reads it. */
data class SessionEntry(
    val sessionId: String,
    val current: Boolean,
    val kind: String,
    // CreateTime in Unix millis, null when the server sent none
    val createTimeMillis: Long?,
    // null when the server has no observed use
    val lastUse: SessionLastUse?,
)

/** The flags of the sdk's ClientSessionError. */
data class SessionErrorFlags(
    val retryable: Boolean,
    val signInRequired: Boolean,
    val unsupported: Boolean,
)

/** The sdk's ClientSessionAction: one sign out, of a session or of all others. */
data class SessionActionState(
    // null for the bulk action
    val sessionId: String?,
    val loading: Boolean,
    // accepted (202) or retrying; stays until the controller confirms enforcement
    val pending: Boolean,
    val error: SessionErrorFlags?,
) {
    val running get() = loading || pending
}

/** The sdk's ClientSessionSnapshot. */
data class SessionsSnapshot(
    // in the controller's display order: current first, then the most recent use
    val sessions: List<SessionEntry>,
    val currentSessionId: String?,
    // "partial" while older sign-ins are outside the list's coverage
    val legacyCoverage: String,
    val loaded: Boolean,
    val loading: Boolean,
    val refreshing: Boolean,
    val supported: Boolean,
    val bulkAction: SessionActionState?,
    val actions: List<SessionActionState>,
    val error: SessionErrorFlags?,
) {
    companion object {
        // no controller yet, or one that has not loaded: the never-loaded state
        val Initial = SessionsSnapshot(
            sessions = listOf(),
            currentSessionId = null,
            legacyCoverage = "",
            loaded = false,
            loading = false,
            refreshing = false,
            supported = true,
            bulkAction = null,
            actions = listOf(),
            error = null,
        )
    }
}

/** The device label and logo of a session's last observed use (§3.2). */
enum class SessionDevice(@get:StringRes val labelRes: Int, @get:DrawableRes val iconRes: Int) {
    Android(R.string.sessions_device_android, R.drawable.device_android),
    Ios(R.string.sessions_device_ios, R.drawable.device_apple),
    Macos(R.string.sessions_device_macos, R.drawable.device_apple),
    Windows(R.string.sessions_device_windows, R.drawable.device_windows),
    Linux(R.string.sessions_device_linux, R.drawable.device_linux),
    Web(R.string.sessions_device_web, R.drawable.device_web),
    Cli(R.string.sessions_device_cli, R.drawable.device_cli),
    Server(R.string.sessions_device_server, R.drawable.device_server),
    Unknown(R.string.sessions_device_unknown, R.drawable.device_unknown);

    companion object {
        // unknown, empty and any other type are the unknown device
        fun fromType(deviceType: String?): SessionDevice = when (deviceType) {
            "android" -> Android
            "ios" -> Ios
            "macos" -> Macos
            "windows" -> Windows
            "linux" -> Linux
            "web" -> Web
            "cli" -> Cli
            "server" -> Server
            else -> Unknown
        }
    }
}

/** How a session signed in, for the kinds the server mints (§3.2). */
enum class SessionMethod(@get:StringRes val labelRes: Int) {
    Password(R.string.sessions_kind_password),
    Verify(R.string.sessions_kind_verify),
    Apple(R.string.sessions_kind_apple),
    Google(R.string.sessions_kind_google),
    Sso(R.string.sessions_kind_sso),
    Wallet(R.string.sessions_kind_wallet),
    Seedphrase(R.string.sessions_kind_seedphrase),
    Signup(R.string.sessions_kind_signup),
    AuthCode(R.string.sessions_kind_auth_code),
    DeviceAdopt(R.string.sessions_kind_device_adopt),
    ApiKeyClient(R.string.sessions_kind_api_key_client);

    companion object {
        // legacy, legacy_proxy and any other kind show no method
        fun fromKind(kind: String?): SessionMethod? = when (kind) {
            "password" -> Password
            "verify" -> Verify
            "apple" -> Apple
            "google" -> Google
            "sso" -> Sso
            "wallet" -> Wallet
            "seedphrase" -> Seedphrase
            "signup" -> Signup
            "auth_code" -> AuthCode
            "device_adopt" -> DeviceAdopt
            "api_key_client" -> ApiKeyClient
            else -> null
        }
    }
}

// the row shows this many characters of the session id
internal const val SESSION_SHORT_ID_LENGTH = 8

// the palette's unknown-country blue, as on the provider globe and the widgets
internal const val SESSION_UNKNOWN_COUNTRY_ARGB = 0xFF0099FF.toInt()

/**
 * The country circle's color: the sdk's color for the code ([colorHex] is
 * Sdk.getColorHex), and the unknown-country blue for an empty code or a
 * color that does not parse.
 */
internal fun sessionCountryArgb(countryCode: String, colorHex: (String) -> String): Int {
    if (countryCode.isEmpty()) {
        return SESSION_UNKNOWN_COUNTRY_ARGB
    }
    val hex = runCatching { colorHex(countryCode.lowercase()) }.getOrNull().orEmpty()
    return WidgetColors.parseHex(hex, SESSION_UNKNOWN_COUNTRY_ARGB)
}

/** One session row, as shown (§3). */
data class SessionRowUi(
    // unique per row, for the lazy list
    val key: String,
    // the full id, which the copy puts on the clipboard
    val sessionId: String,
    // the first characters of the id, on the last line
    val shortId: String,
    val current: Boolean,
    val device: SessionDevice,
    // empty when unknown
    val appVersion: String,
    // city, region and country, without the empty ones
    val placeParts: List<String>,
    val countryCode: String,
    // Unix millis of the last observed use, null when there is none
    val lastUsedMillis: Long?,
    val createTimeMillis: Long?,
    // null for the legacy kinds
    val method: SessionMethod?,
    // its sign out is loading or pending: progress, and no control
    val signingOut: Boolean,
    // its last sign out failed
    val actionFailed: Boolean,
) {
    val place get() = placeParts.joinToString(", ")
}

/** What the screen shows in place of, or around, the rows (§5). */
enum class SessionsBody {
    // never loaded
    Progress,
    // the first load failed: the message and Try again
    LoadFailed,
    // the server has no session list yet
    Unsupported,
    // loaded, and no sessions
    Empty,
    Rows,
}

/** Sign out all other sessions, when it is offered. */
data class SignOutOthersUi(
    val signingOut: Boolean,
    val failed: Boolean,
)

data class SessionsUi(
    val body: SessionsBody,
    val rows: List<SessionRowUi>,
    // the controller's refresh: the pull indicator, over the rows
    val refreshing: Boolean,
    // a refresh failed and the rows are the last list
    val refreshFailed: Boolean,
    // null when there is no current session plus another
    val signOutOthers: SignOutOthersUi?,
    // legacy coverage is partial
    val legacyNote: Boolean,
    // the time the relative times are counted from
    val nowMillis: Long,
) {
    companion object {
        fun initial(nowMillis: Long) = sessionsUi(SessionsSnapshot.Initial, nowMillis)
    }
}

/**
 * Lazy list keys: the session id when it names one row, so a row's swipe state
 * stays with its session when another row goes; otherwise unique by index.
 */
internal fun sessionRowKeys(sessionIds: List<String>): List<String> {
    val counts = sessionIds.groupingBy { it }.eachCount()
    return sessionIds.mapIndexed { index, sessionId ->
        if (sessionId.isNotEmpty() && counts[sessionId] == 1) {
            "session:$sessionId"
        } else {
            indexedLazyListKey("session", index, sessionId)
        }
    }
}

/** The screen for a snapshot of the controller, with relative times counted from [nowMillis]. */
fun sessionsUi(snapshot: SessionsSnapshot, nowMillis: Long): SessionsUi {
    val keys = sessionRowKeys(snapshot.sessions.map { it.sessionId })
    val rows = snapshot.sessions.mapIndexed { index, session ->
        val lastUse = session.lastUse
        val action = snapshot.actions.lastOrNull { it.sessionId == session.sessionId }
        val signingOut = action?.running == true
        SessionRowUi(
            key = keys[index],
            sessionId = session.sessionId,
            shortId = session.sessionId.take(SESSION_SHORT_ID_LENGTH),
            current = session.current,
            device = SessionDevice.fromType(lastUse?.deviceType),
            appVersion = lastUse?.appVersion.orEmpty(),
            placeParts = listOfNotNull(lastUse?.city, lastUse?.region, lastUse?.country)
                .filter { it.isNotBlank() },
            countryCode = lastUse?.countryCode.orEmpty(),
            // the server reports seconds; no time is no observed use
            lastUsedMillis = lastUse?.unixTimeSeconds?.takeIf { 0 < it }?.let { it * 1000 },
            createTimeMillis = session.createTimeMillis,
            method = SessionMethod.fromKind(session.kind),
            signingOut = signingOut,
            actionFailed = !signingOut && action?.error != null,
        )
    }
    val body = when {
        !snapshot.supported -> SessionsBody.Unsupported
        // a retry of a failed first load shows progress again
        !snapshot.loaded && snapshot.loading -> SessionsBody.Progress
        !snapshot.loaded && snapshot.error != null -> SessionsBody.LoadFailed
        !snapshot.loaded -> SessionsBody.Progress
        rows.isEmpty() -> SessionsBody.Empty
        else -> SessionsBody.Rows
    }
    val bulk = snapshot.bulkAction
    val bulkRunning = bulk?.running == true
    val offersSignOutOthers = body == SessionsBody.Rows &&
        rows.any { it.current } && rows.any { !it.current }
    return SessionsUi(
        body = body,
        rows = if (body == SessionsBody.Rows) rows else listOf(),
        // the progress body is the first load's own indicator
        refreshing = body != SessionsBody.Progress && (snapshot.refreshing || snapshot.loading),
        refreshFailed = snapshot.loaded && snapshot.supported && snapshot.error != null,
        signOutOthers = if (offersSignOutOthers) {
            SignOutOthersUi(signingOut = bulkRunning, failed = !bulkRunning && bulk?.error != null)
        } else {
            null
        },
        legacyNote = snapshot.loaded && snapshot.supported && snapshot.legacyCoverage == "partial",
        nowMillis = nowMillis,
    )
}

/**
 * The localized pieces of the screen's text: catalog strings, and the
 * platform's formats of times in the user's zone (SessionsScreen's are
 * Context.getString, the shared relativeTime and DateUtils).
 */
interface SessionsText {
    fun string(@StringRes id: Int, vararg args: Any): String

    // the last use from now: "now", the span ago up to 7 days, then the date
    fun lastUsed(timeMillis: Long, nowMillis: Long): String

    // a sign-in date: month and day, and the year when it is not this year
    fun signedIn(timeMillis: Long): String

    // the full date and time, which assistive tech reads for either of the above
    fun dateTime(timeMillis: Long): String
}

/**
 * A row's three lines (§3) and what assistive tech reads for them, which has
 * the full date and time in place of each relative time and date.
 */
data class SessionRowText(
    // device and app version
    val device: String,
    // location and last use
    val use: String,
    val spokenUse: String,
    // created date, method and short id
    val signIn: String,
    val spokenSignIn: String,
    // the row's screen reader action
    val signOutAction: String,
)

// between the parts of a line
private const val PART_SEPARATOR = " · "

fun sessionRowText(row: SessionRowUi, nowMillis: Long, text: SessionsText): SessionRowText {
    val deviceLabel = text.string(row.device.labelRes)
    val useLine = { lastUsed: (Long) -> String ->
        listOf(
            row.place,
            row.lastUsedMillis?.let { text.string(R.string.sessions_last_used, lastUsed(it)) }
                ?: text.string(R.string.sessions_last_use_unavailable),
        ).filter { it.isNotEmpty() }.joinToString(PART_SEPARATOR)
    }
    val signInLine = { signedIn: (Long) -> String ->
        listOfNotNull(
            row.createTimeMillis?.let { text.string(R.string.sessions_signed_in, signedIn(it)) },
            row.method?.let { text.string(it.labelRes) },
            text.string(R.string.sessions_id, row.shortId),
        ).joinToString(PART_SEPARATOR)
    }
    return SessionRowText(
        device = listOf(deviceLabel, row.appVersion)
            .filter { it.isNotEmpty() }
            .joinToString(PART_SEPARATOR),
        use = useLine { text.lastUsed(it, nowMillis) },
        spokenUse = useLine { text.dateTime(it) },
        signIn = signInLine { text.signedIn(it) },
        spokenSignIn = signInLine { text.dateTime(it) },
        signOutAction = text.string(R.string.sessions_sign_out_accessibility, deviceLabel),
    )
}

/** The body of the confirmation before a session's sign out (§4). */
fun sessionConfirmBody(row: SessionRowUi, text: SessionsText): String {
    val deviceLabel = text.string(row.device.labelRes)
    return when {
        row.current -> text.string(R.string.sessions_confirm_self_body)
        row.place.isNotEmpty() -> text.string(R.string.sessions_confirm_body, deviceLabel, row.place)
        else -> text.string(R.string.sessions_confirm_body_no_place, deviceLabel)
    }
}
