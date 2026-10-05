package com.bringyour.network.ui.components

import com.bringyour.network.ui.components.referral.ReferralCodeField
import com.bringyour.network.ui.components.referral.ReferralCodeVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 1698: invitees missed the referral code entry, a muted link
 * under Continue. Sign-up now shows the optional field above Continue, and
 * what the typed text means is pinned here.
 */
class ReferralCodeFieldTest {

    @Test
    fun theCodeIsTrimmedAndUpperCased() {
        assertEquals("AB12CD", ReferralCodeField.normalize("  ab12cd \n"))
        assertEquals("9F1C-22AB", ReferralCodeField.normalize("9f1c-22ab"))
        assertEquals("", ReferralCodeField.normalize("   "))
    }

    @Test
    fun onlyACodeIsChecked() {
        assertTrue(ReferralCodeField.shouldCheck(" AB12CD"))
        assertFalse(ReferralCodeField.shouldCheck(""))
        assertFalse(ReferralCodeField.shouldCheck("  "))
    }

    @Test
    fun theServersAnswerIsTheVerdict() {
        assertEquals(ReferralCodeVerdict.Valid, ReferralCodeField.verdictOf(valid = true, capped = false))
        assertEquals(ReferralCodeVerdict.Invalid, ReferralCodeField.verdictOf(valid = false, capped = false))
        assertEquals(ReferralCodeVerdict.Capped, ReferralCodeField.verdictOf(valid = true, capped = true))
        assertEquals(ReferralCodeVerdict.CheckFailed, ReferralCodeField.verdictOf(valid = null, capped = false))
    }

    @Test
    fun signUpCarriesATypedCode() {
        for (verdict in listOf(
            ReferralCodeVerdict.Valid,
            // Continue right after typing, or the check did not answer: the
            // server checks the code again on create
            ReferralCodeVerdict.Unchecked,
            ReferralCodeVerdict.Checking,
            ReferralCodeVerdict.CheckFailed,
        )) {
            assertEquals("$verdict", "AB12CD", ReferralCodeField.createCode(" ab12cd ", verdict))
        }
    }

    @Test
    fun signUpDropsACodeTheServerRejected() {
        assertNull(ReferralCodeField.createCode("AB12CD", ReferralCodeVerdict.Invalid))
        assertNull(ReferralCodeField.createCode("AB12CD", ReferralCodeVerdict.Capped))
    }

    @Test
    fun anEmptyFieldIsNoCode() {
        assertNull(ReferralCodeField.createCode("", ReferralCodeVerdict.Unchecked))
        assertNull(ReferralCodeField.createCode("   ", ReferralCodeVerdict.Valid))
    }
}
