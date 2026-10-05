package com.bringyour.network.ui.components

import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.ReferralCodeChecker
import com.bringyour.network.ui.components.referral.ReferralCodeInputController
import com.bringyour.network.ui.components.referral.ReferralCodeVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 1698: the sign-up screens' always-visible referral field.
 * Typing checks the code, an answer for text that changed since is dropped,
 * and the create call carries the code unless the server rejected it.
 */
class ReferralCodeInputControllerTest {

    /** Holds every check open until the test answers it. */
    private class HeldChecker(private val available: Boolean = true) : ReferralCodeChecker {
        val asked = mutableListOf<String>()
        val answers = mutableListOf<(Boolean?, Boolean) -> Unit>()

        override fun check(code: String, done: (valid: Boolean?, capped: Boolean) -> Unit): Boolean {
            if (!available) {
                return false
            }
            asked += code
            answers += done
            return true
        }
    }

    // no pause and an inline dispatcher: a typed code is checked at once
    private fun controller(checker: ReferralCodeChecker) =
        ReferralCodeInputController(CoroutineScope(Dispatchers.Unconfined), checker, checkDelayMillis = 0)

    @Test
    fun typingChecksTheNormalizedCode() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue(" ab12cd "))
        assertEquals(listOf("AB12CD"), checker.asked)
        assertTrue(input.isValidating)

        checker.answers.single()(true, false)
        assertTrue(input.isValid)
        assertEquals("AB12CD", input.createCode)
        assertNull(input.supportingTextRes)
    }

    @Test
    fun continueRightAfterTypingCarriesTheCode() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("AB12CD"))
        // the check has not answered yet
        assertEquals(ReferralCodeVerdict.Checking, input.verdict)
        assertEquals("AB12CD", input.createCode)
    }

    @Test
    fun aCheckThatDidNotAnswerStillCarriesTheCode() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("AB12CD"))
        checker.answers.single()(null, false)
        assertEquals(ReferralCodeVerdict.CheckFailed, input.verdict)
        assertEquals(R.string.something_went_wrong, input.supportingTextRes)
        assertEquals("AB12CD", input.createCode)

        // no api to ask at all
        val offline = controller(HeldChecker(available = false))
        offline.setCode(TextFieldValue("AB12CD"))
        assertEquals(ReferralCodeVerdict.CheckFailed, offline.verdict)
        assertEquals("AB12CD", offline.createCode)
    }

    @Test
    fun aRejectedCodeIsShownAndNotCarried() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("AB12CD"))
        checker.answers.single()(false, false)
        assertTrue(input.isRejected)
        assertEquals(R.string.invalid_referral_code, input.supportingTextRes)
        assertNull(input.createCode)

        input.setCode(TextFieldValue("ZZ99ZZ"))
        checker.answers.last()(true, true)
        assertTrue(input.isCapped)
        assertEquals(R.string.referral_code_capped, input.supportingTextRes)
        assertNull(input.createCode)
    }

    @Test
    fun anAnswerForChangedTextIsDropped() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("AB12CD"))
        input.setCode(TextFieldValue("ZZ99ZZ"))
        assertEquals(listOf("AB12CD", "ZZ99ZZ"), checker.asked)

        // the first code's answer lands after the user typed the second
        checker.answers[0](true, false)
        assertFalse(input.isValid)
        assertEquals(ReferralCodeVerdict.Checking, input.verdict)

        checker.answers[1](false, false)
        assertEquals(ReferralCodeVerdict.Invalid, input.verdict)
    }

    @Test
    fun anEmptyFieldIsNotChecked() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("   "))
        assertTrue(checker.asked.isEmpty())
        assertNull(input.createCode)
        assertFalse(input.isRejected)
    }

    @Test
    fun retypingTheSameCodeDoesNotCheckAgain() {
        val checker = HeldChecker()
        val input = controller(checker)
        input.setCode(TextFieldValue("AB12CD"))
        checker.answers.single()(true, false)
        input.setCode(TextFieldValue("ab12cd "))
        assertEquals(1, checker.asked.size)
        assertTrue(input.isValid)
    }

    @Test
    fun aCodeALinkFilledInIsCheckedAtOnce() {
        val checker = HeldChecker()
        val input = ReferralCodeInputController(CoroutineScope(Dispatchers.Unconfined), checker)
        input.setCode(TextFieldValue("AB12CD"))
        // the typing pause has not run out
        assertTrue(checker.asked.isEmpty())
        input.check {}
        assertEquals(listOf("AB12CD"), checker.asked)
    }
}
