package com.bringyour.network

import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 1698: an Android invitee who installed from a referral link
 * never had the code applied. The Play install referrer is read on the first
 * signed-out launch, but the read was gated on DeviceManager.canRefer, which
 * is the device's flag and so always false while signed out.
 */
class InstallReferrerCheckTest {

    private class MemoryStore : InstallReferrerCheck.Store {
        var checked = false
        override fun isChecked(): Boolean = checked
        override fun markChecked() {
            checked = true
        }
    }

    // the referrer ur.io/c puts on the Play link for code AB12CD, as Play hands it back
    private val referrer = "https://ur.io/c?bonus=AB12CD"

    @Test
    fun aSignedOutFirstLaunchReadsTheReferrer() {
        val check = InstallReferrerCheck(MemoryStore())
        assertTrue(check.shouldCheck(signedIn = false))
    }

    @Test
    fun aSignedInLaunchDoesNotRead() {
        val check = InstallReferrerCheck(MemoryStore())
        assertFalse(check.shouldCheck(signedIn = true))
    }

    @Test
    fun theReferrerIsReadOncePerInstall() {
        val store = MemoryStore()
        val check = InstallReferrerCheck(store)
        check.finish(InstallReferrerResponse.OK)
        assertFalse(check.shouldCheck(signedIn = false))
        // a later logout must not pre-fill the old code again
        assertFalse(InstallReferrerCheck(store).shouldCheck(signedIn = false))
    }

    @Test
    fun anUnreachableServiceIsAskedAgainNextLaunch() {
        for (code in listOf(InstallReferrerResponse.SERVICE_UNAVAILABLE, InstallReferrerResponse.SERVICE_DISCONNECTED)) {
            val store = MemoryStore()
            InstallReferrerCheck(store).finish(code)
            assertTrue("$code", InstallReferrerCheck(store).shouldCheck(signedIn = false))
        }
    }

    @Test
    fun aFinalAnswerWithoutAReferrerEndsTheCheck() {
        for (code in listOf(
            InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
            InstallReferrerResponse.DEVELOPER_ERROR,
            InstallReferrerResponse.PERMISSION_ERROR,
        )) {
            val store = MemoryStore()
            InstallReferrerCheck(store).finish(code)
            assertFalse("$code", InstallReferrerCheck(store).shouldCheck(signedIn = false))
        }
    }

    @Test
    fun aReferralLinkReferrerYieldsItsCode() {
        assertEquals("AB12CD", InstallReferrerCheck.referralCode(referrer))
        // left encoded once
        assertEquals("AB12CD", InstallReferrerCheck.referralCode("https%3A%2F%2Fur.io%2Fc%3Fbonus%3DAB12CD"))
        // older, longer codes pass through for the server to validate
        assertEquals("9f1c-22ab", InstallReferrerCheck.referralCode("https://ur.io/c?bonus=9f1c-22ab"))
        assertEquals("AB12CD", InstallReferrerCheck.referralCode("https://ur.io/c?target=x&bonus=AB12CD"))
    }

    @Test
    fun otherReferrersYieldNothing() {
        assertNull(InstallReferrerCheck.referralCode(null))
        assertNull(InstallReferrerCheck.referralCode(""))
        // Play's default when the listing link had no referrer
        assertNull(InstallReferrerCheck.referralCode("utm_source=google-play&utm_medium=organic"))
        assertNull(InstallReferrerCheck.referralCode("http://ur.io/c?bonus=AB12CD"))
        assertNull(InstallReferrerCheck.referralCode("https://evil.example/c?bonus=AB12CD"))
        assertNull(InstallReferrerCheck.referralCode("https://ur.io.evil.example/c?bonus=AB12CD"))
        assertNull(InstallReferrerCheck.referralCode("https://ur.io/o/connect?bonus=AB12CD"))
        assertNull(InstallReferrerCheck.referralCode("https://ur.io/c?bonus="))
        assertNull(InstallReferrerCheck.referralCode("https://ur.io/c?bonus=%3Cscript%3E"))
        assertNull(InstallReferrerCheck.referralCode("https://ur.io/c?bonus=${"A".repeat(65)}"))
    }

    @Test
    fun onlyTheCodeIsTakenFromTheReferrer() {
        // anyone can put a referrer on a Play link: an auth code in it is ignored
        assertNull(InstallReferrerCheck.referralCode("https://ur.io/c?auth_code=secret"))
        assertEquals("AB12CD", InstallReferrerCheck.referralCode("https://ur.io/c?auth_code=secret&bonus=AB12CD"))
    }
}
