package com.bringyour.network.ui.feedback

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feedback form's send, driven by a fake sender that completes only when
 * the test says so. Reported defect: a failed send still showed the thank-you
 * overlay and cleared the message, and Send gave no sign of progress.
 */
class FeedbackSendFlowTest {

    /** Records sends and holds each completion until the test releases it. */
    private class HeldSender(val available: Boolean = true) : FeedbackSender {
        val sends = mutableListOf<Pair<String, Int>>()
        val completions = mutableListOf<(Boolean) -> Unit>()

        override fun send(text: String, starCount: Int, done: (failed: Boolean) -> Unit): Boolean {
            if (!available) {
                return false
            }
            sends.add(text to starCount)
            completions.add(done)
            return true
        }
    }

    private fun flow(sender: FeedbackSender) =
        FeedbackSendFlow(sender = sender, post = { it() })

    @Test
    fun aFailedSendDoesNotConfirmAndKeepsTheMessage() {
        val sender = HeldSender()
        val flow = flow(sender)
        flow.updateMessage(TextFieldValue("the app is great"))
        flow.updateStarCount(4)
        var confirmations = 0

        flow.submit { confirmations++ }
        sender.completions.single()(true)

        assertEquals(0, confirmations)
        assertEquals("the app is great", flow.message.text)
        assertEquals(4, flow.starCount)
        assertEquals(FeedbackSendStatus.Failed, flow.status)
        // the kept message makes Send the retry
        assertTrue(flow.isSendEnabled)
    }

    @Test
    fun theConfirmationWaitsForTheSendToSucceed() {
        val sender = HeldSender()
        val flow = flow(sender)
        flow.updateMessage(TextFieldValue("hello"))
        var confirmations = 0

        flow.submit { confirmations++ }

        assertEquals(0, confirmations)
        assertEquals("hello", flow.message.text)

        sender.completions.single()(false)

        assertEquals(1, confirmations)
        assertEquals("", flow.message.text)
        assertEquals(0, flow.starCount)
        assertEquals(FeedbackSendStatus.Sent, flow.status)
    }

    @Test
    fun sendIsDisabledAndShowsSendingWhileTheRequestIsOut() {
        val sender = HeldSender()
        val flow = flow(sender)
        flow.updateMessage(TextFieldValue("hello"))

        flow.submit {}

        assertEquals(FeedbackSendStatus.Sending, flow.status)
        assertFalse(flow.isSendEnabled)

        // a second tap while sending sends nothing more
        flow.submit {}
        assertEquals(1, sender.sends.size)
    }

    @Test
    fun noApiIsAFailureNotASilentNoOp() {
        val flow = flow(HeldSender(available = false))
        flow.updateMessage(TextFieldValue("hello"))
        var confirmations = 0

        flow.submit { confirmations++ }

        assertEquals(0, confirmations)
        assertEquals("hello", flow.message.text)
        assertEquals(FeedbackSendStatus.Failed, flow.status)
    }

    @Test
    fun anEditClearsTheErrorButNotASendInFlight() {
        val sender = HeldSender()
        val flow = flow(sender)
        flow.updateMessage(TextFieldValue("hello"))
        flow.submit {}
        sender.completions.single()(true)

        flow.updateMessage(TextFieldValue("hello again"))
        assertEquals(FeedbackSendStatus.Idle, flow.status)

        flow.submit {}
        flow.updateStarCount(5)
        assertEquals(FeedbackSendStatus.Sending, flow.status)
    }
}
