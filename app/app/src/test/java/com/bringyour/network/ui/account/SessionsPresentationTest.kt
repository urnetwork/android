package com.bringyour.network.ui.account

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Account -> Sessions as data (REVOKE-UI-FINAL.md §3-§5, §10, and the list at
 * scale of session1118 C.md §5 C): the device and method labels, the country
 * color, the row lines in English, the body states, Show more, the client
 * groups with their counts and row labels, the sign out actions and the
 * confirmation text, from snapshots of a fake controller. The platform's date
 * formats are replaced by tagged stand-ins so the tests show which time feeds
 * which text; counts use the US integer format.
 */
class SessionsPresentationTest {

    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        // the english catalog, by resource name
        private val english: Map<String, String> by lazy {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(moduleFile("src/main/res/values/strings.xml")).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }
                .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'").replace("\\\"", "\"") }
        }

        private val stringNames: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        private fun name(id: Int) = stringNames[id] ?: error("no string resource $id")
    }

    /** The english strings, with the dates and times as tagged stand-ins. */
    private object EnglishText : SessionsText {
        override fun string(id: Int, vararg args: Any): String =
            String.format(Locale.ROOT, english[name(id)] ?: error("no english ${name(id)}"), *args)

        override fun lastUsed(timeMillis: Long, nowMillis: Long) = "<${(nowMillis - timeMillis) / 1000}s ago>"

        override fun signedIn(timeMillis: Long) = "<date $timeMillis>"

        override fun dateTime(timeMillis: Long) = "<date and time $timeMillis>"

        override fun count(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
    }

    private val nowMillis = 1_800_000_000_000L

    private fun lastUse(
        unixTimeSeconds: Long = nowMillis / 1000 - 300,
        city: String = "Chicago",
        region: String = "Illinois",
        country: String = "United States",
        countryCode: String = "us",
        deviceType: String = "android",
        appVersion: String = "2026.10.8-1067",
    ) = SessionLastUse(unixTimeSeconds, city, region, country, countryCode, deviceType, appVersion)

    private fun session(
        index: Int,
        current: Boolean = false,
        kind: String = "google",
        createTimeMillis: Long? = 1_799_000_000_000L,
        lastUse: SessionLastUse? = lastUse(),
    ) = SessionEntry(
        sessionId = "01a1f3c2-0000-4000-8000-00000000000$index",
        current = current,
        kind = kind,
        createTimeMillis = createTimeMillis,
        lastUse = lastUse,
    )

    // a loaded snapshot as the sdk lays it out; without groups Sign out all
    // other sessions signs out every sign-in but the current one
    private fun loaded(
        vararg sessions: SessionEntry,
        actions: List<SessionActionState> = listOf(),
        bulkAction: SessionActionState? = null,
        legacyCoverage: String = "complete",
        groups: List<SessionGroupEntry> = listOf(),
        otherSessionCount: Long = sessions.count { !it.current }.toLong(),
    ) = SessionsSnapshot.Initial.copy(
        sessions = sessions.toList(),
        sessionCount = sessions.size.toLong(),
        groups = groups,
        otherSessionCount = otherSessionCount,
        currentSessionId = sessions.firstOrNull { it.current }?.sessionId,
        legacyCoverage = legacyCoverage,
        loaded = true,
        actions = actions,
        bulkAction = bulkAction,
    )

    // a client's own session: a hosted proxy's, say, which last used the server
    private fun clientSession(index: Int, kind: String = "legacy_proxy") = session(
        10 + index,
        kind = kind,
        lastUse = lastUse(deviceType = "server", appVersion = ""),
    )

    private fun group(
        kind: String,
        count: Long,
        expanded: Boolean = false,
        sessions: List<SessionEntry> = listOf(),
        hasMore: Boolean = false,
    ) = SessionGroupEntry(kind = kind, count = count, expanded = expanded, sessions = sessions, hasMore = hasMore)

    private fun row(snapshot: SessionsSnapshot, index: Int = 0) = sessionsUi(snapshot, nowMillis).rows[index]

    @Test
    fun everyDeviceTypeHasItsLabelAndLogo() {
        val expected = mapOf(
            "android" to Triple(SessionDevice.Android, R.string.sessions_device_android, R.drawable.device_android),
            "ios" to Triple(SessionDevice.Ios, R.string.sessions_device_ios, R.drawable.device_apple),
            "macos" to Triple(SessionDevice.Macos, R.string.sessions_device_macos, R.drawable.device_apple),
            "windows" to Triple(SessionDevice.Windows, R.string.sessions_device_windows, R.drawable.device_windows),
            "linux" to Triple(SessionDevice.Linux, R.string.sessions_device_linux, R.drawable.device_linux),
            "web" to Triple(SessionDevice.Web, R.string.sessions_device_web, R.drawable.device_web),
            "cli" to Triple(SessionDevice.Cli, R.string.sessions_device_cli, R.drawable.device_cli),
            "server" to Triple(SessionDevice.Server, R.string.sessions_device_server, R.drawable.device_server),
            "unknown" to Triple(SessionDevice.Unknown, R.string.sessions_device_unknown, R.drawable.device_unknown),
        )
        for ((type, labelled) in expected) {
            val device = SessionDevice.fromType(type)
            assertEquals(type, labelled.first, device)
            assertEquals(type, labelled.second, device.labelRes)
            assertEquals(type, labelled.third, device.iconRes)
        }
        // empty, other and a missing last use are the unknown device
        for (type in listOf("", "tv", "Android", null)) {
            assertEquals("$type", SessionDevice.Unknown, SessionDevice.fromType(type))
        }
        assertEquals(SessionDevice.Unknown, row(loaded(session(0, lastUse = null))).device)
    }

    @Test
    fun everyMintedKindHasItsMethodAndLegacyKindsHaveNone() {
        val expected = mapOf(
            "password" to R.string.sessions_kind_password,
            "verify" to R.string.sessions_kind_verify,
            "apple" to R.string.sessions_kind_apple,
            "google" to R.string.sessions_kind_google,
            "sso" to R.string.sessions_kind_sso,
            "wallet" to R.string.sessions_kind_wallet,
            "seedphrase" to R.string.sessions_kind_seedphrase,
            "signup" to R.string.sessions_kind_signup,
            "auth_code" to R.string.sessions_kind_auth_code,
            "device_adopt" to R.string.sessions_kind_device_adopt,
            "api_key_client" to R.string.sessions_kind_api_key_client,
        )
        for ((kind, labelRes) in expected) {
            assertEquals(kind, labelRes, SessionMethod.fromKind(kind)?.labelRes)
        }
        assertEquals(SessionMethod.entries.size, expected.size)
        for (kind in listOf("legacy", "legacy_proxy", "", "guest", null)) {
            assertNull("$kind", SessionMethod.fromKind(kind))
        }
    }

    @Test
    fun theCountryCircleTakesTheSdkColorAndFallsBackToTheUnknownBlue() {
        val asked = mutableListOf<String>()
        val colorHex = { code: String ->
            asked.add(code)
            if (code == "us") "BAC5B3" else "not a color"
        }
        assertEquals(0xFFBAC5B3.toInt(), sessionCountryArgb("us", colorHex))
        // the palette is keyed by lower-case codes
        assertEquals(0xFFBAC5B3.toInt(), sessionCountryArgb("US", colorHex))
        assertEquals(listOf("us", "us"), asked)

        // an empty code is the unknown country, without asking the sdk
        assertEquals(SESSION_UNKNOWN_COUNTRY_ARGB, sessionCountryArgb("", colorHex))
        assertEquals(2, asked.size)
        // a color that does not parse, or no color at all
        assertEquals(SESSION_UNKNOWN_COUNTRY_ARGB, sessionCountryArgb("zz", colorHex))
        assertEquals(SESSION_UNKNOWN_COUNTRY_ARGB, sessionCountryArgb("zz") { error("sdk unavailable") })
        assertEquals(0xFF0099FF.toInt(), SESSION_UNKNOWN_COUNTRY_ARGB)
    }

    @Test
    fun theRowShowsTheFirstEightCharactersAndCopiesTheFullId() {
        val row = row(loaded(session(1)))
        assertEquals("01a1f3c2-0000-4000-8000-000000000001", row.sessionId)
        assertEquals("01a1f3c2", row.shortId)
        assertTrue(sessionRowText(row, nowMillis, EnglishText).signIn.endsWith("ID 01a1f3c2"))
    }

    @Test
    fun lastUseIsInUnixSeconds() {
        val row = row(loaded(session(0, lastUse = lastUse(unixTimeSeconds = 1_799_999_700L))))
        assertEquals(1_799_999_700_000L, row.lastUsedMillis)
        assertEquals("Chicago, Illinois, United States · Last used <300s ago>", sessionRowText(row, nowMillis, EnglishText).use)

        // no time is no observed use
        assertNull(row(loaded(session(0, lastUse = lastUse(unixTimeSeconds = 0)))).lastUsedMillis)
    }

    @Test
    fun theThreeLinesOfARow() {
        val text = sessionRowText(row(loaded(session(0))), nowMillis, EnglishText)
        assertEquals("Android · 2026.10.8-1067", text.device)
        assertEquals("Chicago, Illinois, United States · Last used <300s ago>", text.use)
        assertEquals("Signed in <date 1799000000000> · Google · ID 01a1f3c2", text.signIn)
        // assistive tech hears the full date and time in place of each relative time and date
        assertEquals(
            "Chicago, Illinois, United States · Last used <date and time 1799999700000>",
            text.spokenUse,
        )
        assertEquals("Signed in <date and time 1799000000000> · Google · ID 01a1f3c2", text.spokenSignIn)
        assertEquals("Sign out Android", text.signOutAction)
    }

    @Test
    fun missingPartsAreOmitted() {
        // no version, no region, a legacy kind and no creation time
        val sparse = row(
            loaded(
                session(
                    0,
                    kind = "legacy",
                    createTimeMillis = null,
                    lastUse = lastUse(region = "", appVersion = "", deviceType = "ios"),
                )
            )
        )
        val text = sessionRowText(sparse, nowMillis, EnglishText)
        assertEquals("iOS", text.device)
        assertEquals("Chicago, United States · Last used <300s ago>", text.use)
        assertEquals("ID 01a1f3c2", text.signIn)

        // no location at all
        val nowhere = row(loaded(session(0, lastUse = lastUse(city = "", region = "", country = "", countryCode = ""))))
        assertEquals("Last used <300s ago>", sessionRowText(nowhere, nowMillis, EnglishText).use)
        assertEquals("", nowhere.countryCode)

        // no observed use
        val unused = row(loaded(session(0, lastUse = null)))
        val unusedText = sessionRowText(unused, nowMillis, EnglishText)
        assertEquals("Unknown device", unusedText.device)
        assertEquals("Last use unavailable", unusedText.use)
        assertEquals("Last use unavailable", unusedText.spokenUse)
    }

    @Test
    fun longPlacesAndVersionsStayWhole() {
        // the row wraps; nothing is cut
        val city = "Example City With A Name Long Enough To Wrap Across Two Lines Of The Row"
        val version = "2026.10.8-1067+build.with.a.very.long.suffix-play"
        val text = sessionRowText(row(loaded(session(0, lastUse = lastUse(city = city, appVersion = version)))), nowMillis, EnglishText)
        assertEquals("Android · $version", text.device)
        assertTrue(text.use.startsWith("$city, Illinois"))
    }

    @Test
    fun theBodyFollowsTheSnapshot() {
        // never loaded
        assertEquals(SessionsBody.Progress, sessionsUi(SessionsSnapshot.Initial, nowMillis).body)
        val loading = SessionsSnapshot.Initial.copy(loading = true)
        assertEquals(SessionsBody.Progress, sessionsUi(loading, nowMillis).body)
        assertFalse(sessionsUi(loading, nowMillis).refreshing)

        // a failed first load, and its retry
        val retryable = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        val failed = SessionsSnapshot.Initial.copy(error = retryable)
        assertEquals(SessionsBody.LoadFailed, sessionsUi(failed, nowMillis).body)
        assertEquals(SessionsBody.Progress, sessionsUi(failed.copy(loading = true), nowMillis).body)

        // sign-in required has its own state, not the load failure
        val signIn = SessionErrorFlags(retryable = false, signInRequired = true, unsupported = false)
        assertEquals(SessionsBody.SignInRequired, sessionsUi(SessionsSnapshot.Initial.copy(error = signIn), nowMillis).body)

        // the server has no list yet
        val unsupported = SessionErrorFlags(retryable = false, signInRequired = false, unsupported = true)
        val unsupportedUi = sessionsUi(SessionsSnapshot.Initial.copy(supported = false, error = unsupported), nowMillis)
        assertEquals(SessionsBody.Unsupported, unsupportedUi.body)
        assertFalse(unsupportedUi.refreshFailed)
        assertFalse(unsupportedUi.legacyNote)

        // loaded
        assertEquals(SessionsBody.Empty, sessionsUi(loaded(), nowMillis).body)
        val rows = sessionsUi(loaded(session(0, current = true), session(1)), nowMillis)
        assertEquals(SessionsBody.Rows, rows.body)
        assertEquals(2, rows.rows.size)
    }

    @Test
    fun aRefreshKeepsTheRowsAndAFailedOneSaysSo() {
        val snapshot = loaded(session(0, current = true), session(1))
        val refreshing = sessionsUi(snapshot.copy(refreshing = true), nowMillis)
        assertTrue(refreshing.refreshing)
        assertEquals(2, refreshing.rows.size)
        assertFalse(refreshing.refreshFailed)

        val retryable = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        val failed = sessionsUi(snapshot.copy(error = retryable), nowMillis)
        assertEquals(SessionsBody.Rows, failed.body)
        assertEquals(2, failed.rows.size)
        assertTrue(failed.refreshFailed)
        assertFalse(failed.refreshing)
    }

    @Test
    fun signOutOfAllOthersNeedsTheCurrentSessionAndAnotherItSignsOut() {
        assertNotNull(sessionsUi(loaded(session(0, current = true), session(1)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(session(0, current = true)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(session(0), session(1)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(), nowMillis).signOutOthers)

        // the sdk's count decides, not the rows: besides this session only hosted
        // proxies, which the sign out keeps
        val proxies = listOf(group(Sdk.ClientSessionGroupHostedProxies, count = 3))
        assertNull(
            sessionsUi(loaded(session(0, current = true), groups = proxies, otherSessionCount = 0), nowMillis).signOutOthers
        )
        // an API client in a collapsed group, which shows no row, is signed out
        val apiClients = listOf(group(Sdk.ClientSessionGroupApiClients, count = 1))
        assertNotNull(
            sessionsUi(loaded(session(0, current = true), groups = apiClients, otherSessionCount = 1), nowMillis).signOutOthers
        )
    }

    @Test
    fun signOutOfAllOthersSaysWhenHostedProxiesStaySignedIn() {
        val current = session(0, current = true)
        val plain = sessionsUi(loaded(current, session(1)), nowMillis).signOutOthers!!
        assertFalse(plain.keepsHostedProxies)
        assertEquals(R.string.sessions_confirm_others_body, plain.confirmBodyRes)

        val withProxies = sessionsUi(
            loaded(
                current,
                session(1),
                groups = listOf(
                    group(Sdk.ClientSessionGroupHostedProxies, count = 40),
                    group(Sdk.ClientSessionGroupApiClients, count = 2),
                ),
                otherSessionCount = 3,
            ),
            nowMillis,
        ).signOutOthers!!
        assertTrue(withProxies.keepsHostedProxies)
        assertEquals(R.string.sessions_confirm_others_body_keeps_proxies, withProxies.confirmBodyRes)
        assertEquals(
            "Every other session in this list will be signed out, except hosted proxies. This session stays signed in. Anyone who knows your sign-in details can still sign in again.",
            EnglishText.string(withProxies.confirmBodyRes),
        )

        // other groups alone keep the plain body
        val withoutProxies = sessionsUi(
            loaded(current, groups = listOf(group(Sdk.ClientSessionGroupPairedDevices, count = 2)), otherSessionCount = 2),
            nowMillis,
        ).signOutOthers!!
        assertEquals(R.string.sessions_confirm_others_body, withoutProxies.confirmBodyRes)
    }

    @Test
    fun everyGroupKindHasItsHeaderAndRowLabel() {
        // the sdk's group kinds (client_session_view_controller.go)
        assertEquals("sign_ins", Sdk.ClientSessionGroupSignIns)
        assertEquals("hosted_proxies", Sdk.ClientSessionGroupHostedProxies)
        assertEquals("api_clients", Sdk.ClientSessionGroupApiClients)
        assertEquals("paired_devices", Sdk.ClientSessionGroupPairedDevices)
        assertEquals("other_clients", Sdk.ClientSessionGroupOtherClients)

        val expected = mapOf(
            Sdk.ClientSessionGroupHostedProxies to Triple(SessionClientGroup.HostedProxies, "Hosted proxies (%1\$s)", "Hosted proxy"),
            Sdk.ClientSessionGroupApiClients to Triple(SessionClientGroup.ApiClients, "API clients (%1\$s)", "API client"),
            Sdk.ClientSessionGroupPairedDevices to Triple(SessionClientGroup.PairedDevices, "Paired devices (%1\$s)", "Paired device"),
            Sdk.ClientSessionGroupOtherClients to Triple(SessionClientGroup.OtherClients, "Other clients (%1\$s)", "Other client"),
        )
        for ((kind, labelled) in expected) {
            val group = SessionClientGroup.fromKind(kind)
            assertEquals(kind, labelled.first, group)
            assertEquals(kind, labelled.second, english[name(group.headerRes)])
            assertEquals(kind, labelled.third, EnglishText.string(group.rowLabelRes))
        }
        assertEquals(SessionClientGroup.entries.size, expected.size)
        assertEquals(R.string.manage_devices_proxy, SessionClientGroup.HostedProxies.rowLabelRes)
        // a kind this app does not know yet is another client
        for (kind in listOf("", "proxies", null)) {
            assertEquals("$kind", SessionClientGroup.OtherClients, SessionClientGroup.fromKind(kind))
        }
    }

    @Test
    fun aGroupHeaderShowsItsCountWithTheLocalesGroupingAndPlusWhenTruncated() {
        val groups = listOf(
            group(Sdk.ClientSessionGroupHostedProxies, count = 1204),
            group(Sdk.ClientSessionGroupApiClients, count = 3),
        )
        val ui = sessionsUi(loaded(session(0, current = true), groups = groups), nowMillis)
        assertEquals(
            listOf("Hosted proxies (1,204)", "API clients (3)"),
            ui.groups.map { sessionGroupHeader(it, EnglishText) },
        )

        // the controller read only part of the list: the counts are lower bounds
        val truncated = sessionsUi(loaded(session(0, current = true), groups = groups).copy(truncated = true), nowMillis)
        assertEquals(
            listOf("Hosted proxies (1,204+)", "API clients (3+)"),
            truncated.groups.map { sessionGroupHeader(it, EnglishText) },
        )

        // the platform's grouping, not a fixed one
        val german = object : SessionsText by EnglishText {
            override fun count(value: Long): String = NumberFormat.getIntegerInstance(Locale.GERMANY).format(value)
        }
        assertEquals("Hosted proxies (1.204+)", sessionGroupHeader(truncated.groups[0], german))
    }

    @Test
    fun aCollapsedGroupShowsItsHeaderOnlyAndAnOpenOneItsRows() {
        val proxies = listOf(clientSession(0), clientSession(1))
        val collapsed = sessionsUi(
            loaded(session(0, current = true), groups = listOf(group(Sdk.ClientSessionGroupHostedProxies, count = 120))),
            nowMillis,
        ).groups.single()
        assertEquals(Sdk.ClientSessionGroupHostedProxies, collapsed.kind)
        assertFalse(collapsed.expanded)
        assertEquals(listOf<SessionRowUi>(), collapsed.rows)
        assertFalse(collapsed.hasMore)

        val open = sessionsUi(
            loaded(
                session(0, current = true),
                groups = listOf(
                    group(Sdk.ClientSessionGroupHostedProxies, count = 120, expanded = true, sessions = proxies, hasMore = true),
                ),
            ),
            nowMillis,
        ).groups.single()
        assertTrue(open.expanded)
        assertEquals(proxies.map { it.sessionId }, open.rows.map { it.sessionId })
        assertTrue(open.hasMore)
        assertEquals(listOf(SessionClientGroup.HostedProxies, SessionClientGroup.HostedProxies), open.rows.map { it.group })
    }

    @Test
    fun aGroupsRowsTakeTheGroupsLabelOnLineOneAndInTheConfirmation() {
        val apiClient = clientSession(0, kind = "api_key_client")
            .copy(lastUse = lastUse(deviceType = "cli", appVersion = "1.4.0"))
        val pairedDevice = clientSession(1, kind = "device_adopt")
        val other = clientSession(2, kind = "legacy")
        val ui = sessionsUi(
            loaded(
                session(0, current = true),
                groups = listOf(
                    group(Sdk.ClientSessionGroupApiClients, count = 1, expanded = true, sessions = listOf(apiClient)),
                    group(Sdk.ClientSessionGroupPairedDevices, count = 1, expanded = true, sessions = listOf(pairedDevice)),
                    group(Sdk.ClientSessionGroupOtherClients, count = 1, expanded = true, sessions = listOf(other)),
                ),
                otherSessionCount = 3,
            ),
            nowMillis,
        )
        val (apiRow, deviceRow, otherRow) = ui.groups.map { it.rows.single() }

        // the app version is still appended when known; the device logo stays
        val apiText = sessionRowText(apiRow, nowMillis, EnglishText)
        assertEquals("API client · 1.4.0", apiText.device)
        assertEquals("Sign out API client", apiText.signOutAction)
        assertEquals(SessionDevice.Cli, apiRow.device)
        assertEquals("Paired device", sessionRowText(deviceRow, nowMillis, EnglishText).device)
        assertEquals("Other client", sessionRowText(otherRow, nowMillis, EnglishText).device)

        // the same label names the session in the confirmation
        assertEquals(
            "API client in Chicago, Illinois, United States will be signed out.",
            sessionConfirmBody(apiRow, EnglishText),
        )
        // the device sign-ins keep their device label
        assertEquals("Android · 2026.10.8-1067", sessionRowText(ui.rows[0], nowMillis, EnglishText).device)
        assertNull(ui.rows[0].group)
    }

    @Test
    fun aHostedProxysConfirmationSaysItStopsWorking() {
        val proxy = clientSession(0)
        val row = sessionsUi(
            loaded(
                session(0, current = true),
                groups = listOf(group(Sdk.ClientSessionGroupHostedProxies, count = 1, expanded = true, sessions = listOf(proxy))),
                otherSessionCount = 0,
            ),
            nowMillis,
        ).groups.single().rows.single()
        assertEquals("Hosted proxy", sessionRowText(row, nowMillis, EnglishText).device)
        assertEquals(
            "This hosted proxy will stop working. Create a new one to replace it.",
            sessionConfirmBody(row, EnglishText),
        )
        assertEquals("Sign out Hosted proxy", sessionRowText(row, nowMillis, EnglishText).signOutAction)
    }

    @Test
    fun aGroupsRowShowsItsSignOutLikeASignIn() {
        // actions are keyed by session id, wherever the session is listed
        val proxies = listOf(clientSession(0), clientSession(1), clientSession(2))
        val error = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        val ui = sessionsUi(
            loaded(
                session(0, current = true),
                groups = listOf(group(Sdk.ClientSessionGroupHostedProxies, count = 3, expanded = true, sessions = proxies)),
                actions = listOf(
                    SessionActionState(proxies[0].sessionId, loading = true, pending = false, error = null),
                    SessionActionState(proxies[1].sessionId, loading = false, pending = false, error = error),
                ),
                otherSessionCount = 0,
            ),
            nowMillis,
        )
        val rows = ui.groups.single().rows
        assertEquals(listOf(true, false, false), rows.map { it.signingOut })
        assertEquals(listOf(false, true, false), rows.map { it.actionFailed })
        // and the screen finds the row to confirm by its id
        assertEquals(rows[1], ui.row(proxies[1].sessionId))
        assertEquals(ui.rows[0], ui.row(ui.rows[0].sessionId))
        assertNull(ui.row("not-listed"))
    }

    @Test
    fun showMoreFollowsTheSnapshot() {
        val sessions = arrayOf(session(0, current = true), session(1))
        assertFalse(sessionsUi(loaded(*sessions), nowMillis).rowsHaveMore)
        assertTrue(sessionsUi(loaded(*sessions).copy(sessionCount = 120, sessionsHasMore = true), nowMillis).rowsHaveMore)

        // a group offers more only while open
        val closedWithMore = group(Sdk.ClientSessionGroupApiClients, count = 80, expanded = false, hasMore = true)
        assertFalse(sessionsUi(loaded(*sessions, groups = listOf(closedWithMore)), nowMillis).groups.single().hasMore)

        // nothing is offered in place of the list
        val signIn = SessionErrorFlags(retryable = false, signInRequired = true, unsupported = false)
        val refused = sessionsUi(
            loaded(*sessions, groups = listOf(closedWithMore)).copy(sessionsHasMore = true, error = signIn),
            nowMillis,
        )
        assertFalse(refused.rowsHaveMore)
        assertEquals(listOf<SessionGroupUi>(), refused.groups)
        assertEquals("Show more", EnglishText.string(R.string.sessions_show_more))
    }

    @Test
    fun theListIsEmptyOnlyWithoutSignInsAndGroups() {
        assertEquals(SessionsBody.Empty, sessionsUi(loaded(), nowMillis).body)
        // client sessions alone are a list
        val onlyClients = sessionsUi(loaded(groups = listOf(group(Sdk.ClientSessionGroupApiClients, count = 2))), nowMillis)
        assertEquals(SessionsBody.Rows, onlyClients.body)
        assertEquals(listOf<SessionRowUi>(), onlyClients.rows)
        assertEquals(1, onlyClients.groups.size)
        // and no sign out of the others without a current session
        assertNull(onlyClients.signOutOthers)
    }

    @Test
    fun rowKeysAreUniqueAcrossTheSections() {
        val proxies = listOf(clientSession(0), clientSession(1))
        val ui = sessionsUi(
            loaded(
                session(0, current = true),
                session(1),
                groups = listOf(
                    group(Sdk.ClientSessionGroupHostedProxies, count = 2, expanded = true, sessions = proxies, hasMore = true),
                    group(Sdk.ClientSessionGroupApiClients, count = 5),
                ),
            ).copy(sessionsHasMore = true),
            nowMillis,
        )
        val keys = ui.rows.map { it.key } + ui.groups.flatMap { group -> listOf(group.key, group.showMoreKey) + group.rows.map { it.key } } +
            listOf("sign-ins-more", "sign-out-others", "last-used-help", "legacy-note", "refresh-failed")
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("session-hosted_proxies:${proxies[0].sessionId}", ui.groups[0].rows[0].key)
    }

    @Test
    fun aRunningSignOutShowsProgressAndAFailedOneShowsItsError() {
        val sessions = arrayOf(session(0, current = true), session(1), session(2), session(3))
        val error = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        val ui = sessionsUi(
            loaded(
                *sessions,
                actions = listOf(
                    SessionActionState(sessions[1].sessionId, loading = true, pending = false, error = null),
                    // accepted (202): pending until the controller confirms
                    SessionActionState(sessions[2].sessionId, loading = false, pending = true, error = error),
                    SessionActionState(sessions[3].sessionId, loading = false, pending = false, error = error),
                ),
                bulkAction = SessionActionState(null, loading = false, pending = true, error = null),
            ),
            nowMillis,
        )
        assertEquals(listOf(false, true, true, false), ui.rows.map { it.signingOut })
        assertEquals(listOf(false, false, false, true), ui.rows.map { it.actionFailed })
        assertEquals(SignOutOthersUi(signingOut = true, failed = false), ui.signOutOthers)

        val failedBulk = sessionsUi(
            loaded(*sessions, bulkAction = SessionActionState(null, loading = false, pending = false, error = error)),
            nowMillis,
        )
        assertEquals(SignOutOthersUi(signingOut = false, failed = true), failedBulk.signOutOthers)
    }

    @Test
    fun aFailedSignOutOfAllOthersSaysSoUnderItsButton() {
        val sessions = arrayOf(session(0, current = true), session(1), session(2))
        // not retried by the controller: the action is neither loading nor pending
        val error = SessionErrorFlags(retryable = false, signInRequired = false, unsupported = false)
        val failed = sessionsUi(
            loaded(*sessions, bulkAction = SessionActionState(null, loading = false, pending = false, error = error)),
            nowMillis,
        ).signOutOthers!!
        assertEquals(R.string.sessions_sign_out_others_failed, failed.errorRes)
        assertEquals("Couldn't sign out the other sessions. Try again.", EnglishText.string(failed.errorRes!!))
        // the button stays for the retry
        assertFalse(failed.signingOut)

        // the retry runs: progress and no error
        val retrying = sessionsUi(
            loaded(*sessions, bulkAction = SessionActionState(null, loading = true, pending = false, error = error)),
            nowMillis,
        ).signOutOthers!!
        assertTrue(retrying.signingOut)
        assertNull(retrying.errorRes)

        // never attempted, or done
        assertNull(sessionsUi(loaded(*sessions), nowMillis).signOutOthers!!.errorRes)
        assertNull(
            sessionsUi(
                loaded(*sessions, bulkAction = SessionActionState(null, loading = false, pending = false, error = null)),
                nowMillis,
            ).signOutOthers!!.errorRes
        )
    }

    @Test
    fun signInRequiredSaysToSignInAgain() {
        val signIn = SessionErrorFlags(retryable = false, signInRequired = true, unsupported = false)

        // before the first list
        val first = sessionsUi(SessionsSnapshot.Initial.copy(error = signIn), nowMillis)
        assertEquals(SessionsBody.SignInRequired, first.body)
        assertEquals(R.string.sessions_sign_in_required, first.body.messageRes)
        assertEquals("Sign in again to manage sessions.", EnglishText.string(first.body.messageRes!!))

        // and over a list that can no longer be managed: no rows, no sign out
        // of the others, no refresh failure and no notes
        val sessions = arrayOf(session(0, current = true), session(1))
        val over = sessionsUi(loaded(*sessions, legacyCoverage = "partial").copy(error = signIn), nowMillis)
        assertEquals(SessionsBody.SignInRequired, over.body)
        assertEquals(listOf<SessionRowUi>(), over.rows)
        assertNull(over.signOutOthers)
        assertFalse(over.refreshFailed)
        assertFalse(over.legacyNote)

        // the sdk reports it until a new sign-in, through the reloads too
        assertEquals(SessionsBody.SignInRequired, sessionsUi(SessionsSnapshot.Initial.copy(loading = true, error = signIn), nowMillis).body)
        assertEquals(SessionsBody.SignInRequired, sessionsUi(loaded(*sessions).copy(refreshing = true, error = signIn), nowMillis).body)
    }

    @Test
    fun aSessionSignedOutFromAnotherDeviceSaysSoOnlyForTheTrustedCause() {
        val revoked = SessionErrorFlags(retryable = false, signInRequired = true, sessionRevoked = true, unsupported = false)
        val ui = sessionsUi(SessionsSnapshot.Initial.copy(error = revoked), nowMillis)
        assertEquals(SessionsBody.SignedOutRemotely, ui.body)
        assertEquals(
            "This session was signed out from another device.",
            EnglishText.string(ui.body.messageRes!!),
        )
        assertEquals(
            SessionsBody.SignedOutRemotely,
            sessionsUi(loaded(session(0, current = true)).copy(error = revoked), nowMillis).body,
        )

        // a generic rejection names no cause
        val generic = revoked.copy(sessionRevoked = false)
        assertEquals(SessionsBody.SignInRequired, sessionsUi(SessionsSnapshot.Initial.copy(error = generic), nowMillis).body)
        // and the cause means nothing without a sign-in required
        val notSignIn = revoked.copy(signInRequired = false, retryable = true)
        assertEquals(SessionsBody.LoadFailed, sessionsUi(SessionsSnapshot.Initial.copy(error = notSignIn), nowMillis).body)
    }

    @Test
    fun everyBodyShowsItsOwnMessage() {
        val expected = mapOf(
            SessionsBody.Progress to null,
            SessionsBody.LoadFailed to "Couldn't load sessions.",
            SessionsBody.SignInRequired to "Sign in again to manage sessions.",
            SessionsBody.SignedOutRemotely to "This session was signed out from another device.",
            SessionsBody.Unsupported to "Sessions aren't available yet.",
            SessionsBody.Empty to "No active sessions",
            SessionsBody.Rows to null,
        )
        assertEquals(SessionsBody.entries.toSet(), expected.keys)
        for ((body, message) in expected) {
            assertEquals("$body", message, body.messageRes?.let { EnglishText.string(it) })
        }
    }

    @Test
    fun thePartialLegacyNoteIsHonest() {
        assertTrue(sessionsUi(loaded(session(0), legacyCoverage = "partial"), nowMillis).legacyNote)
        // with an empty list too: older sign-ins are what is missing
        assertTrue(sessionsUi(loaded(legacyCoverage = "partial"), nowMillis).legacyNote)
        assertFalse(sessionsUi(loaded(session(0), legacyCoverage = "complete"), nowMillis).legacyNote)
        // the controller's empty snapshot says partial before it loads
        assertFalse(sessionsUi(SessionsSnapshot.Initial.copy(legacyCoverage = "partial"), nowMillis).legacyNote)
        assertEquals(
            "Sign-ins from older app versions appear here once they renew. To end every sign-in, change your sign-in details.",
            EnglishText.string(R.string.sessions_legacy_note),
        )
    }

    @Test
    fun rowKeysStayWithTheirSession() {
        val keys = sessionRowKeys(listOf("a", "b", "c"))
        assertEquals(listOf("session:a", "session:b", "session:c"), keys)
        // the key of b is the same once a is gone, so b keeps its swipe state
        assertEquals(listOf("session:b", "session:c"), sessionRowKeys(listOf("b", "c")))

        // duplicate and missing ids stay unique
        val odd = sessionRowKeys(listOf("a", "a", "", ""))
        assertEquals(odd.size, odd.toSet().size)
    }

    @Test
    fun theConfirmationNamesTheSession() {
        val snapshot = loaded(
            session(0, current = true),
            session(1, lastUse = lastUse(deviceType = "windows")),
            session(2, lastUse = lastUse(city = "", region = "", country = "", deviceType = "linux")),
        )
        val ui = sessionsUi(snapshot, nowMillis)
        assertEquals(
            "This is the session you're using. This app will be signed out.",
            sessionConfirmBody(ui.rows[0], EnglishText),
        )
        assertEquals(
            "Windows in Chicago, Illinois, United States will be signed out.",
            sessionConfirmBody(ui.rows[1], EnglishText),
        )
        assertEquals("Linux will be signed out.", sessionConfirmBody(ui.rows[2], EnglishText))
        assertEquals("Sign out this session?", EnglishText.string(R.string.sessions_confirm_title))
        assertEquals("Sign out all other sessions?", EnglishText.string(R.string.sessions_confirm_others_title))
        // never "all devices signed out"
        assertFalse(EnglishText.string(R.string.sessions_confirm_others_body).contains("all devices"))
    }
}
