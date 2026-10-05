package com.bringyour.network.ui.settings

import com.bringyour.network.ui.account.GuestConversion
import com.bringyour.network.ui.account.GuestConversionSession
import com.bringyour.network.ui.login.VerifySendError
import com.bringyour.network.ui.login.VerifySendNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The add-sign-in-method sheet. AddAuth adds an email or phone sign-in
 * unverified, and the sheet used to report it added right away; it must send a
 * code and count the sign-in as added only after authVerify. Google (and Apple)
 * SSO and wallet sign-ins are verified by their provider and add at once.
 * Every answer is delivered by hand and the clock is injected.
 */
class AddSignInFlowTest {

    private class FakeSession : AddSignInSession<String> {
        val calls = mutableListOf<String>()
        var addAuthError: String? = null
        val pendingSends = mutableListOf<(Boolean, VerifySendError?) -> Unit>()
        val pendingVerifies = mutableListOf<(String?) -> Unit>()

        override fun addAuth(args: String, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit) {
            calls += "addAuth:$args"
            addAuthError?.let { onError(AddAuthRefusal(it)) } ?: onSuccess()
        }

        override fun sendCode(userAuth: String, done: (Boolean, VerifySendError?) -> Unit) {
            calls += "sendCode:$userAuth"
            pendingSends += done
        }

        override fun verifyCode(userAuth: String, code: String, done: (String?) -> Unit) {
            calls += "verifyCode:$userAuth:$code"
            pendingVerifies += done
        }

        fun answerSend(transportError: Boolean = false, sendError: VerifySendError? = null) {
            pendingSends.removeAt(0)(transportError, sendError)
        }

        fun answerVerify(error: String? = null) {
            pendingVerifies.removeAt(0)(error)
        }
    }

    private class Clock(var nowMillis: Long = 1_000_000L)

    private val rateLimited = VerifySendError("verify_rate_limited", "Too many attempts", 120)

    private fun addEmail(
        flow: AddSignInFlow<String>,
        onAdded: () -> Unit = {},
        onError: (AddAuthRefusal) -> Unit = {},
    ) {
        flow.add(AddedSignInMethod.PASSWORD, "email-args", " user@example.com ", onAdded, onError)
    }

    @Test
    fun addedEmailIsNotAddedUntilVerified() {
        val session = FakeSession()
        val clock = Clock()
        val flow = AddSignInFlow(session) { clock.nowMillis }
        var added = 0

        addEmail(flow, onAdded = { added += 1 })

        assertEquals("an added email was reported added without verification", 0, added)
        assertEquals(AddSignInStep.ENTER_CODE, flow.step)
        assertEquals(listOf("addAuth:email-args", "sendCode:user@example.com"), session.calls)

        session.answerSend()
        assertEquals(VerifySendNotice.Sent, flow.sendNotice)
        assertTrue(flow.codeSent)
        assertEquals(0, added)

        flow.submitCode(" 123456 ")
        assertEquals(AddSignInStep.VERIFYING, flow.step)
        assertEquals("verifyCode:user@example.com:123456", session.calls.last())
        assertEquals(0, added)

        session.answerVerify()
        assertEquals(AddSignInStep.ADDED, flow.step)
        assertEquals(1, added)
    }

    @Test
    fun rejectedCodeKeepsTheSignInUnadded() {
        val session = FakeSession()
        val flow = AddSignInFlow(session) { 0L }
        var added = 0

        addEmail(flow, onAdded = { added += 1 })
        session.answerSend()
        flow.submitCode("000000")
        session.answerVerify("Invalid code.")

        assertEquals(0, added)
        assertEquals(AddSignInStep.ENTER_CODE, flow.step)
        assertEquals("Invalid code.", flow.verifyError)

        flow.submitCode("123456")
        assertNull(flow.verifyError)
        session.answerVerify()
        assertEquals(1, added)
    }

    @Test
    fun ssoAndWalletAddWithoutACode() {
        for (method in listOf(AddedSignInMethod.GOOGLE, AddedSignInMethod.WALLET)) {
            val session = FakeSession()
            val flow = AddSignInFlow(session) { 0L }
            var added = 0

            flow.add(method, "$method-args", "", { added += 1 }, {})

            assertEquals("$method must add without verification", 1, added)
            assertEquals(AddSignInStep.ADDED, flow.step)
            assertEquals(listOf("addAuth:$method-args"), session.calls)
        }
        assertFalse(AddedSignInMethod.GOOGLE.needsVerification)
        assertFalse(AddedSignInMethod.WALLET.needsVerification)
        assertTrue(AddedSignInMethod.PASSWORD.needsVerification)
    }

    @Test
    fun failedAddAuthSendsNoCode() {
        val session = FakeSession().apply { addAuthError = "Invalid password" }
        val flow = AddSignInFlow(session) { 0L }
        var error: String? = null
        var added = 0

        addEmail(flow, onAdded = { added += 1 }, onError = { error = it.message })

        assertEquals("Invalid password", error)
        assertEquals(0, added)
        assertEquals(AddSignInStep.ENTER, flow.step)
        assertEquals(listOf("addAuth:email-args"), session.calls)
    }

    @Test
    fun guestConversionReSignsAfterAddButIsAddedOnlyAfterVerify() {
        // the guest path: GuestConversion is the sheet's addAuth
        val calls = mutableListOf<String>()
        val conversion = GuestConversion(object : GuestConversionSession<String> {
            override fun addAuth(args: String, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit) {
                calls += "addAuth:$args"
                onSuccess()
            }
            override fun refreshJwt() { calls += "refreshJwt" }
            override fun logout() { calls += "logout" }
        })
        val session = FakeSession()
        val flow = AddSignInFlow(object : AddSignInSession<String> by session {
            override fun addAuth(args: String, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit) {
                conversion.addSignInMethod(args, onSuccess, onError)
            }
        }) { 0L }
        var added = 0

        addEmail(flow, onAdded = { added += 1 })

        // the network has a login method now, so the jwt is re-signed at once
        assertEquals(listOf("addAuth:email-args", "refreshJwt"), calls)
        assertEquals(0, added)
        session.answerSend()
        flow.submitCode("123456")
        session.answerVerify()
        assertEquals(1, added)
        assertFalse(calls.contains("logout"))
    }

    @Test
    fun sendErrorsAreNotReportedAsSent() {
        val cases = listOf(
            Triple(false, rateLimited, VerifySendNotice.RateLimited(2)),
            Triple(false, VerifySendError("verify_send_failed", "send failed", 0), VerifySendNotice.SendFailed),
            Triple(false, VerifySendError("verify_unknown", "Mailbox unavailable", 0), VerifySendNotice.ServerMessage("Mailbox unavailable")),
            Triple(true, null, VerifySendNotice.SendFailed),
        )
        for ((transportError, sendError, expected) in cases) {
            val session = FakeSession()
            val flow = AddSignInFlow(session) { 0L }

            addEmail(flow)
            session.answerSend(transportError, sendError)

            assertEquals(expected, flow.sendNotice)
            assertEquals(expected, flow.noticeNow())
            assertFalse("no code was sent", flow.codeSent)
            assertEquals(AddSignInStep.ENTER_CODE, flow.step)
        }
    }

    @Test
    fun failedSendCanBeRetriedAtOnce() {
        val session = FakeSession()
        val flow = AddSignInFlow(session) { 0L }

        addEmail(flow)
        assertFalse("no resend while the first send is in flight", flow.canResend())
        session.answerSend(transportError = true)

        assertTrue(flow.canResend())
        assertTrue(flow.resend())
        assertEquals(2, session.calls.count { it.startsWith("sendCode:") })
    }

    @Test
    fun rateLimitHoldsResendUntilTheRetryTime() {
        val session = FakeSession()
        val clock = Clock()
        val flow = AddSignInFlow(session) { clock.nowMillis }

        addEmail(flow)
        session.answerSend(sendError = rateLimited)

        assertFalse(flow.canResend())
        assertFalse(flow.resend())
        assertEquals(1, session.calls.count { it.startsWith("sendCode:") })
        assertEquals(120_000L, flow.resendWaitMillis())

        // the notice counts the minutes down
        clock.nowMillis += 61_000L
        assertEquals(VerifySendNotice.RateLimited(1), flow.noticeNow())
        assertFalse(flow.resend())

        clock.nowMillis += 59_000L
        assertTrue(flow.canResend())
        assertEquals(VerifySendNotice.Sent, flow.noticeNow())
        assertNull(flow.resendWaitMillis())
        assertTrue(flow.resend())
        assertEquals(2, session.calls.count { it.startsWith("sendCode:") })
    }

    @Test
    fun sentCodeHoldsResendBriefly() {
        val session = FakeSession()
        val clock = Clock()
        val flow = AddSignInFlow(session) { clock.nowMillis }

        addEmail(flow)
        session.answerSend()

        assertFalse(flow.canResend())
        clock.nowMillis += AddSignInFlow.RESEND_AFTER_SENT_MILLIS - 1
        assertFalse(flow.resend())
        clock.nowMillis += 1
        assertTrue(flow.resend())
    }

    @Test
    fun noCodeIsVerifiedWhileASendIsInFlight() {
        val session = FakeSession()
        val flow = AddSignInFlow(session) { 0L }

        addEmail(flow)
        flow.submitCode("123456")

        assertTrue(session.calls.none { it.startsWith("verifyCode:") })
    }
}
