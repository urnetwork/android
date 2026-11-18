package com.bringyour.network.ui.connect

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The main screen's notice when the account's sign-in ended while this
 * device stays signed in (the sdk's AccountSignInRequired; REVOKE-FINAL.md's
 * credential rejection, REVOKE-UI-FINAL.md §5): its words follow the
 * listener's cause, only the sdk's trusted session-revoked cause says why,
 * and it stays until the user closes it, signs out or signs in again. In
 * memory: no sdk, clock or device.
 */
class AccountSignInNoticeTest {

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
    fun theTrustedCauseSaysWhyThenWhatToDo() {
        assertEquals("session_revoked", Sdk.AuthLogoutCauseSessionRevoked)
        val notice = AccountSignInRequired.forCause(Sdk.AuthLogoutCauseSessionRevoked)
        assertTrue(notice.signedOutRemotely)
        assertEquals(listOf(R.string.sessions_signed_out_remotely, R.string.sessions_sign_in_required), notice.messageIds)
        assertEquals(
            "This session was signed out from another device. Sign in again to manage sessions.",
            notice.message { englishText(it) },
        )
    }

    @Test
    fun everyOtherCauseHasOnlyTheGenericWords() {
        // the sdk's "" is every rejection it has no trusted cause for
        for (cause in listOf("", null, "SESSION_REVOKED", " session_revoked", "session_revoked ", "revoked", "client_removed")) {
            val notice = AccountSignInRequired.forCause(cause)
            assertFalse("$cause", notice.signedOutRemotely)
            assertEquals("$cause", listOf(R.string.sessions_sign_in_required), notice.messageIds)
            assertEquals("Sign in again to manage sessions.", notice.message { englishText(it) })
        }
    }

    @Test
    fun theNoticeStaysUntilTheUserClosesIt() {
        val notices = AccountSignInNotices()
        assertNull(notices.notice.value)

        notices.accountSignInRequired(Sdk.AuthLogoutCauseSessionRevoked)
        assertEquals(AccountSignInRequired(signedOutRemotely = true), notices.notice.value)
        // nothing takes it: every look at the main screen shows it again
        assertEquals(AccountSignInRequired(signedOutRemotely = true), notices.notice.value)

        // the close
        notices.clear()
        assertNull(notices.notice.value)
    }

    @Test
    fun aLaterRejectionReplacesTheNotice() {
        val notices = AccountSignInNotices()
        notices.accountSignInRequired("")
        assertEquals(AccountSignInRequired(signedOutRemotely = false), notices.notice.value)
        notices.accountSignInRequired(Sdk.AuthLogoutCauseSessionRevoked)
        assertEquals(AccountSignInRequired(signedOutRemotely = true), notices.notice.value)
        notices.accountSignInRequired(null)
        assertEquals(AccountSignInRequired(signedOutRemotely = false), notices.notice.value)
    }

    @Test
    fun aSignOutOrANewSignInDropsTheNotice() {
        val notices = AccountSignInNotices()
        // MainApplication.logoutInternal: signed out, by the user or by the sdk
        notices.accountSignInRequired(Sdk.AuthLogoutCauseSessionRevoked)
        notices.clear()
        assertNull(notices.notice.value)

        // MainApplication.initDevice: signed in again
        notices.accountSignInRequired("")
        notices.clear()
        assertNull(notices.notice.value)
    }

    @Test
    fun theWordsAreTheCatalogsAndTheCloseIsNamed() {
        assertEquals("This session was signed out from another device.", englishText(R.string.sessions_signed_out_remotely))
        assertEquals("Sign in again to manage sessions.", englishText(R.string.sessions_sign_in_required))
        // the close's screen reader name
        assertEquals("Close", englishText(R.string.close))
    }
}
