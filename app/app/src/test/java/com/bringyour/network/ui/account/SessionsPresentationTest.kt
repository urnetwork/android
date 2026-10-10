package com.bringyour.network.ui.account

import com.bringyour.network.R
import java.io.File
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
 * Account -> Sessions as data (REVOKE-UI-FINAL.md §3-§5, §10): the device and
 * method labels, the country color, the row lines in English, the body states,
 * the sign out actions and the confirmation text, from snapshots of a fake
 * controller. The platform's date formats are replaced by tagged stand-ins so
 * the tests show which time feeds which text.
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

    private fun loaded(
        vararg sessions: SessionEntry,
        actions: List<SessionActionState> = listOf(),
        bulkAction: SessionActionState? = null,
        legacyCoverage: String = "complete",
    ) = SessionsSnapshot.Initial.copy(
        sessions = sessions.toList(),
        currentSessionId = sessions.firstOrNull { it.current }?.sessionId,
        legacyCoverage = legacyCoverage,
        loaded = true,
        actions = actions,
        bulkAction = bulkAction,
    )

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

        // sign-in required uses the generic failure; the app's logout flow takes over
        val signIn = SessionErrorFlags(retryable = false, signInRequired = true, unsupported = false)
        assertEquals(SessionsBody.LoadFailed, sessionsUi(SessionsSnapshot.Initial.copy(error = signIn), nowMillis).body)

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
    fun signOutOfAllOthersNeedsTheCurrentSessionAndAnother() {
        assertNotNull(sessionsUi(loaded(session(0, current = true), session(1)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(session(0, current = true)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(session(0), session(1)), nowMillis).signOutOthers)
        assertNull(sessionsUi(loaded(), nowMillis).signOutOthers)
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
