package com.bringyour.network.ui.login

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element

/**
 * The sign-in screen's notice about the sign-out that brought the user there
 * (REVOKE-UI-FINAL.md §5): only the sdk's trusted session-revoked cause has
 * one, a sign-in screen takes it once, and the app's own sign-out and a new
 * sign-in drop it. In memory: no sdk, clock or device.
 */
class SignInNoticeTest {

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
                .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'") }
        }

        private val stringNames: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        private fun englishText(id: Int) = english[stringNames[id]] ?: error("no english string $id")
    }

    @Test
    fun onlyTheTrustedSessionRevokedCauseHasANotice() {
        assertEquals("session_revoked", Sdk.AuthLogoutCauseSessionRevoked)
        assertEquals(SignInNotice.SignedOutRemotely, SignInNotice.forAuthLogoutCause(Sdk.AuthLogoutCauseSessionRevoked))
        // the sdk's "" is every other sign-out: a generic rejection, the app's
        // own, and a sign-out of this session from Account -> Sessions
        for (cause in listOf("", null, "SESSION_REVOKED", " session_revoked", "session_revoked ", "revoked", "client_removed")) {
            assertNull("$cause", SignInNotice.forAuthLogoutCause(cause))
        }
    }

    @Test
    fun theNoticeSaysThisSessionWasSignedOutFromAnotherDevice() {
        assertEquals(R.string.sessions_signed_out_remotely, SignInNotice.SignedOutRemotely.messageRes)
        assertEquals("This session was signed out from another device.", englishText(SignInNotice.SignedOutRemotely.messageRes))
        // the dialog's one button
        assertEquals("Close", englishText(R.string.close))
    }

    @Test
    fun aSessionSignedOutFromAnotherDeviceIsToldOnce() {
        val notices = SignInNotices()
        assertNull(notices.waiting.value)

        notices.authLoggedOut(Sdk.AuthLogoutCauseSessionRevoked)
        assertEquals(SignInNotice.SignedOutRemotely, notices.waiting.value)
        // the first sign-in screen takes it
        assertEquals(SignInNotice.SignedOutRemotely, notices.take())
        // and any later one has nothing to show
        assertNull(notices.waiting.value)
        assertNull(notices.take())
    }

    @Test
    fun aSignOutWithoutTheTrustedCauseShowsNothingNew() {
        val notices = SignInNotices()
        // a generic rejection, or this session signed out from Sessions
        notices.authLoggedOut("")
        assertNull(notices.waiting.value)
        assertNull(notices.take())

        // a later sign-out without the cause replaces one not shown yet
        notices.authLoggedOut(Sdk.AuthLogoutCauseSessionRevoked)
        notices.authLoggedOut("")
        assertNull(notices.take())
    }

    @Test
    fun theAppsOwnSignOutAndANewSignInDropTheNotice() {
        val notices = SignInNotices()
        // MainApplication.logout: the user signed out
        notices.authLoggedOut(Sdk.AuthLogoutCauseSessionRevoked)
        notices.clear()
        assertNull(notices.waiting.value)
        assertNull(notices.take())

        // MainApplication.initDevice: signed in again before a sign-in screen showed it
        notices.authLoggedOut(Sdk.AuthLogoutCauseSessionRevoked)
        notices.clear()
        assertNull(notices.take())
    }
}
