package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reported defect: tapping Claim for the Seeker multiplier did nothing visible
 * when no wallet app was installed, the wallet app failed, or the server could
 * not be reached; those outcomes were only logged.
 */
class SeekerVerifyNoticeTest {

    private val address = "SyntheticWa11etAddressForTests1111111111111"

    @Test
    fun noWalletAppIsShown() {
        assertEquals(SeekerVerifyNotice.NoWalletApp, SeekerVerifyNotice.fromSign(SeekerSignOutcome.NoWalletApp))
    }

    @Test
    fun aWalletAppFailureIsShown() {
        assertEquals(SeekerVerifyNotice.Failed, SeekerVerifyNotice.fromSign(SeekerSignOutcome.Failed))
        assertEquals(SeekerVerifyNotice.Failed, SeekerVerifyNotice.fromSign(SeekerSignOutcome.NoSignature))
    }

    @Test
    fun aSignedMessageGoesOnToTheServer() {
        assertNull(SeekerVerifyNotice.fromSign(SeekerSignOutcome.Signed))
    }

    @Test
    fun anUnreachableServerIsShown() {
        val notice = SeekerVerifyNotice.fromVerifyResult(
            requestFailed = true,
            success = false,
            serverMessage = null,
            walletAddress = address,
        )
        assertEquals(SeekerVerifyNotice.Failed, notice)
    }

    @Test
    fun aWalletWithoutTheTokenNamesTheWallet() {
        val notice = SeekerVerifyNotice.fromVerifyResult(
            requestFailed = false,
            success = false,
            serverMessage = null,
            walletAddress = address,
        )
        assertEquals(SeekerVerifyNotice.NotHolder("1111111"), notice)
    }

    @Test
    fun theServerReasonIsShownAsIs() {
        val notice = SeekerVerifyNotice.fromVerifyResult(
            requestFailed = false,
            success = false,
            serverMessage = "signature verification failed",
            walletAddress = address,
        )
        assertEquals(SeekerVerifyNotice.ServerMessage("signature verification failed"), notice)
    }

    /**
     * Reported defect: the server's English reason was shown as is, so a
     * wallet without the token got an untranslated message. The error code
     * picks the localized notice.
     */
    @Test
    fun aTokenNotFoundCodeNamesTheWalletInsteadOfTheServerMessage() {
        val notice = SeekerVerifyNotice.fromVerifyResult(
            requestFailed = false,
            success = false,
            serverMessage = "No Seeker or Saga token found in this wallet.",
            walletAddress = address,
            serverCode = "seeker_token_not_found",
        )
        assertEquals(SeekerVerifyNotice.NotHolder("1111111"), notice)
    }

    @Test
    fun aSignatureOrLookupFailureCodeIsTheLocalizedFailure() {
        listOf("seeker_invalid_signature", "seeker_lookup_failed").forEach { code ->
            val notice = SeekerVerifyNotice.fromVerifyResult(
                requestFailed = false,
                success = false,
                serverMessage = "signature verification failed",
                walletAddress = address,
                serverCode = code,
            )
            assertEquals(code, SeekerVerifyNotice.Failed, notice)
        }
    }

    @Test
    fun anUnknownCodeFallsBackToTheServerMessage() {
        val notice = SeekerVerifyNotice.fromVerifyResult(
            requestFailed = false,
            success = false,
            serverMessage = "a new reason",
            walletAddress = address,
            serverCode = "seeker_new_reason",
        )
        assertEquals(SeekerVerifyNotice.ServerMessage("a new reason"), notice)
    }

    @Test
    fun aVerifiedWalletHasNoNotice() {
        assertNull(
            SeekerVerifyNotice.fromVerifyResult(
                requestFailed = false,
                success = true,
                serverMessage = null,
                walletAddress = address,
            )
        )
    }

    /**
     * The multiplier copy: a verified Seeker or Saga token doubles points and
     * the free daily data and referral data grants (server pro.yml
     * seeker.data_multiplier and subsidy seeker_holder_multiplier), so the app
     * must not say it applies to points only, nor imply free Pro.
     */
    @Test
    fun theMultiplierCopySaysItDoublesDataNotPointsOnly() {
        val strings = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
            File("app/app/src/main/res/values/strings.xml"),
        ).first { it.exists() }.readText()

        val benefit = Regex("<string name=\"seeker_multiplier_benefit\">([^<]*)</string>")
            .find(strings)?.groupValues?.get(1)
        assertNotNull("seeker_multiplier_benefit is missing", benefit)
        assertTrue(benefit!!.contains("free daily data"))
        assertTrue(benefit.contains("referral data"))
        assertFalse(benefit.contains("Pro"))
        assertFalse(
            "android still ships the points-only Seeker copy",
            strings.contains("name=\"seeker_points_only\""),
        )
    }
}
