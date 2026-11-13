package com.bringyour.network.ui.components.redeemTransferBalanceCode

import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing a code into the redeem sheet (GIFTOFPRIVACY.md 2.6 and 9.1, call 2):
 * the cards print it in groups of four with spaces, and the server accepts it
 * typed that way, so Redeem enables on the code normalized (whitespace,
 * hyphens, dashes and dots out, upper case) at the SDK's 26 characters. The
 * code itself goes to the server as typed.
 *
 * The SDK's rule is native, so it runs here as this AAR has it: the trimmed
 * length is Sdk.BalanceCodeLength (a constant, read without the library).
 */
class BalanceCodeEntryTest {

    private val sdkFormatRule = { code: String -> code.trim().length.toLong() == Sdk.BalanceCodeLength }

    private fun enablesRedeem(typed: String) = isTypedBalanceCodeValid(typed, sdkFormatRule)

    @Test
    fun theCardsGroupingEnablesRedeem() {
        // as printed: groups of four with spaces, 32 characters
        assertTrue(enablesRedeem("ABCD EFGH IJKL MNOP QRST UVWX YZ"))
        // and the other ways it gets typed or pasted
        for (typed in listOf(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcd efgh ijkl mnop qrst uvwx yz",
            "ABCD-EFGH-IJKL-MNOP-QRST-UVWX-YZ",
            "ABCD.EFGH.IJKL.MNOP.QRST.UVWX.YZ",
            "ABCD–EFGH—IJKL MNOP\tQRST\nUVWX YZ",
            "  ABCD EFGH IJKL MNOP QRST UVWX YZ  ",
        )) {
            assertTrue(typed, enablesRedeem(typed))
        }
    }

    @Test
    fun aCodeOfAnyOtherLengthDoesNot() {
        for (typed in listOf(
            "",
            "    ",
            "- . -",
            // 25 and 27 characters, grouped or not
            "ABCD EFGH IJKL MNOP QRST UVWX Y",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZA",
            "ABCD EFGH IJKL MNOP QRST UVWX YZA",
        )) {
            assertFalse(typed, enablesRedeem(typed))
        }
    }

    @Test
    fun theSdksRuleSeesTheCodeNormalized() {
        val seen = mutableListOf<String>()
        isTypedBalanceCodeValid("abcd efgh-ijkl.mnop qrst uvwx yz") { code -> seen.add(code); true }
        assertEquals(listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ"), seen)
    }
}
