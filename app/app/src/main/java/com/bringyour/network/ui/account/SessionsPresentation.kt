package com.bringyour.network.ui.account

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.widgets.render.WidgetColors
import com.bringyour.sdk.Sdk

/*
 * Account -> Sessions, as data (server/session/REVOKE-UI-FINAL.md §3-§5): the
 * sdk's ClientSessionSnapshot copied off its gomobile objects, and what the
 * screen shows for it. Everything here is plain data and functions, so the
 * JVM tests cover it without the sdk's native library; SessionsViewModel
 * copies the gomobile snapshot in and SessionsScreen renders the result.
 *
 * A network's list can hold thousands of sessions (every hosted proxy, API
 * client and paired device has its own). The sdk's controller reads all of
 * them, groups them and counts them; its snapshot carries only the rows on
 * screen: the device sign-ins a page at a time, then one collapsible group
 * per kind of client with its count, whose rows it carries only while the
 * group is open. This file maps that snapshot to the screen and decides
 * nothing the sdk decides (which session goes where, the counts, what Sign
 * out all other sessions signs out).
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
    // with signInRequired: the server confirmed that another device signed
    // this session out (the sdk's trusted session-revoked cause)
    val sessionRevoked: Boolean = false,
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

/** The sdk's ClientSessionGroup: one kind of client's sessions, below the device sign-ins. */
data class SessionGroupEntry(
    // Sdk.ClientSessionGroup*, which SetGroupExpanded and ShowMoreSessions take
    val kind: String,
    // all the group's sessions; a lower bound while the snapshot is truncated
    val count: Long,
    // opened with SetGroupExpanded
    val expanded: Boolean,
    // while expanded, the rows to show in display order; empty while collapsed
    val sessions: List<SessionEntry>,
    // expanded, with rows past sessions: Show more
    val hasMore: Boolean,
)

/** The sdk's ClientSessionSnapshot. */
data class SessionsSnapshot(
    // the device sign-ins to show, in the controller's display order: current
    // first, then the most recent use
    val sessions: List<SessionEntry>,
    // all the device sign-ins, shown or not; a lower bound while truncated
    val sessionCount: Long,
    // more device sign-ins than sessions: Show more
    val sessionsHasMore: Boolean,
    // the client sessions, one group per kind of client in display order; a
    // kind without sessions has no group
    val groups: List<SessionGroupEntry>,
    // the sessions Sign out all other sessions signs out: every listed one but
    // the current session and the hosted proxies (the sdk decides which)
    val otherSessionCount: Long,
    // the list was longer than the controller reads: the counts are lower bounds
    val truncated: Boolean,
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
            sessionCount = 0,
            sessionsHasMore = false,
            groups = listOf(),
            otherSessionCount = 0,
            truncated = false,
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

/**
 * A client group below the device sign-ins, by the sdk's group kind: its
 * header's label, which takes the count, and the label its rows show in place
 * of the device label (also the confirmation's and the screen reader's name
 * for the row).
 */
enum class SessionClientGroup(@get:StringRes val headerRes: Int, @get:StringRes val rowLabelRes: Int) {
    HostedProxies(R.string.sessions_group_hosted_proxies, R.string.manage_devices_proxy),
    ApiClients(R.string.sessions_group_api_clients, R.string.sessions_client_api_key),
    PairedDevices(R.string.sessions_group_paired_devices, R.string.sessions_client_device),
    OtherClients(R.string.sessions_group_other_clients, R.string.sessions_client_other);

    companion object {
        // other_clients, and a kind this app does not know yet, are other clients
        fun fromKind(kind: String?): SessionClientGroup = when (kind) {
            Sdk.ClientSessionGroupHostedProxies -> HostedProxies
            Sdk.ClientSessionGroupApiClients -> ApiClients
            Sdk.ClientSessionGroupPairedDevices -> PairedDevices
            else -> OtherClients
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
    // the client group it is listed in; null for a device sign-in
    val group: SessionClientGroup? = null,
) {
    val place get() = placeParts.joinToString(", ")

    // line 1's name for the row, which the confirmation and the screen reader
    // use too: the group's row label, else the device's
    @get:StringRes
    val labelRes: Int get() = group?.rowLabelRes ?: device.labelRes
}

/** A client group of the list (§5 C): a header with its count, and its rows while open. */
data class SessionGroupUi(
    // the sdk's group kind, which SetGroupExpanded and ShowMoreSessions take
    val kind: String,
    val group: SessionClientGroup,
    // the group's sessions
    val count: Long,
    // the count is a lower bound: the header shows it with "+"
    val countIsLowerBound: Boolean,
    val expanded: Boolean,
    // while expanded, the rows the sdk shows; empty while collapsed
    val rows: List<SessionRowUi>,
    // expanded, with rows past these: Show more
    val hasMore: Boolean,
) {
    // the lazy list keys of the header and of Show more
    val key get() = "group:$kind"
    val showMoreKey get() = "group-more:$kind"
}

/**
 * What the screen shows in place of, or around, the rows (§5), with the
 * message it shows in their place.
 */
enum class SessionsBody(@get:StringRes val messageRes: Int?) {
    // never loaded
    Progress(null),
    // the first load failed: the message and Try again
    LoadFailed(R.string.sessions_load_failed),
    // the account's sign-in was rejected: nothing here works until the user
    // signs in again, which the app's logout flow leads to. Generic wording:
    // it names no cause
    SignInRequired(R.string.sessions_sign_in_required),
    // the same, when the sdk reports the trusted cause: another device signed
    // this session out
    SignedOutRemotely(R.string.sessions_signed_out_remotely),
    // the server has no session list yet
    Unsupported(R.string.sessions_unsupported),
    // loaded, with no device sign-in and no client group
    Empty(R.string.sessions_empty),
    Rows(null),
}

/** Sign out all other sessions, when it is offered. */
data class SignOutOthersUi(
    val signingOut: Boolean,
    // the last sign out of the others failed; the button stays for a retry,
    // which goes through the controller again
    val failed: Boolean,
    // a hosted proxies group is listed, which the sign out keeps
    val keepsHostedProxies: Boolean = false,
) {
    // the line under the button
    @get:StringRes
    val errorRes: Int? get() = if (failed) R.string.sessions_sign_out_others_failed else null

    // the confirmation's body, which says when the hosted proxies stay signed in
    @get:StringRes
    val confirmBodyRes: Int
        get() = if (keepsHostedProxies) {
            R.string.sessions_confirm_others_body_keeps_proxies
        } else {
            R.string.sessions_confirm_others_body
        }
}

data class SessionsUi(
    val body: SessionsBody,
    // the device sign-ins shown
    val rows: List<SessionRowUi>,
    // more device sign-ins than rows: Show more under them
    val rowsHaveMore: Boolean = false,
    // the client groups below the device sign-ins, in display order
    val groups: List<SessionGroupUi> = listOf(),
    // the controller's refresh: the pull indicator, over the rows
    val refreshing: Boolean,
    // a refresh failed and the rows are the last list
    val refreshFailed: Boolean,
    // null unless there is a current session and another session it signs out
    val signOutOthers: SignOutOthersUi?,
    // legacy coverage is partial
    val legacyNote: Boolean,
    // the time the relative times are counted from
    val nowMillis: Long,
) {
    /** The shown row of a session, a device sign-in or in an open group; actions are keyed by session id. */
    fun row(sessionId: String): SessionRowUi? =
        rows.firstOrNull { it.sessionId == sessionId }
            ?: groups.firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.sessionId == sessionId } }

    companion object {
        fun initial(nowMillis: Long) = sessionsUi(SessionsSnapshot.Initial, nowMillis)
    }
}

/**
 * Lazy list keys: the session id when it names one row of its section, so a
 * row's swipe state stays with its session when another row goes; otherwise
 * unique by index. [section] keeps one section's keys apart from another's.
 */
internal fun sessionRowKeys(sessionIds: List<String>, section: String = "session"): List<String> {
    val counts = sessionIds.groupingBy { it }.eachCount()
    return sessionIds.mapIndexed { index, sessionId ->
        if (sessionId.isNotEmpty() && counts[sessionId] == 1) {
            "$section:$sessionId"
        } else {
            indexedLazyListKey(section, index, sessionId)
        }
    }
}

/** The screen for a snapshot of the controller, with relative times counted from [nowMillis]. */
fun sessionsUi(snapshot: SessionsSnapshot, nowMillis: Long): SessionsUi {
    // the last action of each session, a device sign-in or a group's row
    val sessionActions = snapshot.actions.associateBy { it.sessionId }
    val rowsOf = { sessions: List<SessionEntry>, section: String, group: SessionClientGroup? ->
        val keys = sessionRowKeys(sessions.map { it.sessionId }, section)
        sessions.mapIndexed { index, session ->
            val lastUse = session.lastUse
            val action = sessionActions[session.sessionId]
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
                group = group,
            )
        }
    }
    val rows = rowsOf(snapshot.sessions, "session", null)
    val groups = snapshot.groups.map { entry ->
        val group = SessionClientGroup.fromKind(entry.kind)
        SessionGroupUi(
            kind = entry.kind,
            group = group,
            count = entry.count,
            countIsLowerBound = snapshot.truncated,
            expanded = entry.expanded,
            // the sdk carries rows only while the group is open
            rows = if (entry.expanded) rowsOf(entry.sessions, "session-${entry.kind}", group) else listOf(),
            hasMore = entry.expanded && entry.hasMore,
        )
    }
    val error = snapshot.error
    val body = when {
        // the sdk reports this until a new sign-in, loaded or not, and through
        // the reloads that follow: the last list can no longer be managed
        error?.signInRequired == true -> if (error.sessionRevoked) {
            SessionsBody.SignedOutRemotely
        } else {
            SessionsBody.SignInRequired
        }
        !snapshot.supported -> SessionsBody.Unsupported
        // a retry of a failed first load shows progress again
        !snapshot.loaded && snapshot.loading -> SessionsBody.Progress
        !snapshot.loaded && error != null -> SessionsBody.LoadFailed
        !snapshot.loaded -> SessionsBody.Progress
        // no device sign-in and no client group
        snapshot.sessionCount <= 0 && rows.isEmpty() && groups.isEmpty() -> SessionsBody.Empty
        else -> SessionsBody.Rows
    }
    // the loaded list, empty or not
    val listed = body == SessionsBody.Rows || body == SessionsBody.Empty
    val bulk = snapshot.bulkAction
    val bulkRunning = bulk?.running == true
    // the sdk counts what the sign out signs out: the hosted proxies stay
    val offersSignOutOthers = body == SessionsBody.Rows &&
        rows.any { it.current } && 0 < snapshot.otherSessionCount
    return SessionsUi(
        body = body,
        rows = if (body == SessionsBody.Rows) rows else listOf(),
        rowsHaveMore = body == SessionsBody.Rows && snapshot.sessionsHasMore,
        groups = if (body == SessionsBody.Rows) groups else listOf(),
        // the progress body is the first load's own indicator
        refreshing = body != SessionsBody.Progress && (snapshot.refreshing || snapshot.loading),
        refreshFailed = listed && error != null,
        signOutOthers = if (offersSignOutOthers) {
            SignOutOthersUi(
                signingOut = bulkRunning,
                failed = !bulkRunning && bulk?.error != null,
                keepsHostedProxies = groups.any { it.group == SessionClientGroup.HostedProxies },
            )
        } else {
            null
        },
        legacyNote = listed && snapshot.legacyCoverage == "partial",
        nowMillis = nowMillis,
    )
}

/**
 * A group's header (§5 C): its label with the count in the locale's digit
 * grouping, and "+" when the count is a lower bound, e.g. "Hosted proxies (1,204+)".
 */
fun sessionGroupHeader(group: SessionGroupUi, text: SessionsText): String {
    val count = text.count(group.count) + if (group.countIsLowerBound) "+" else ""
    return text.string(group.group.headerRes, count)
}

/**
 * The localized pieces of the screen's text: catalog strings, the platform's
 * formats of times in the user's zone, and of counts (SessionsScreen's are
 * Context.getString, the shared relativeTime, DateUtils and NumberFormat).
 */
interface SessionsText {
    fun string(@StringRes id: Int, vararg args: Any): String

    // the last use from now: "now", the span ago up to 7 days, then the date
    fun lastUsed(timeMillis: Long, nowMillis: Long): String

    // a sign-in date: month and day, and the year when it is not this year
    fun signedIn(timeMillis: Long): String

    // the full date and time, which assistive tech reads for either of the above
    fun dateTime(timeMillis: Long): String

    // a count with the locale's digit grouping
    fun count(value: Long): String
}

/**
 * A row's three lines (§3) and what assistive tech reads for them, which has
 * the full date and time in place of each relative time and date.
 */
data class SessionRowText(
    // device, or the group's row label, and app version
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
    val deviceLabel = text.string(row.labelRes)
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

/**
 * The body of the confirmation before a session's sign out (§4, §5 C): a
 * hosted proxy stops working; any other session is named by its row's label.
 */
fun sessionConfirmBody(row: SessionRowUi, text: SessionsText): String {
    val deviceLabel = text.string(row.labelRes)
    return when {
        row.current -> text.string(R.string.sessions_confirm_self_body)
        row.group == SessionClientGroup.HostedProxies -> text.string(R.string.sessions_confirm_body_hosted_proxy)
        row.place.isNotEmpty() -> text.string(R.string.sessions_confirm_body, deviceLabel, row.place)
        else -> text.string(R.string.sessions_confirm_body_no_place, deviceLabel)
    }
}
