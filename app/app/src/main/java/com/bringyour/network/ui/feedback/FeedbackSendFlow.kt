package com.bringyour.network.ui.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

/** Where the feedback form's send is. */
enum class FeedbackSendStatus {
    Idle,
    Sending,
    Sent,
    Failed,
}

/**
 * Sends one feedback. `done` runs once, on any thread, with whether the send
 * failed. Returns false, without calling `done`, when there is no api to send
 * with.
 */
fun interface FeedbackSender {
    fun send(text: String, starCount: Int, done: (failed: Boolean) -> Unit): Boolean
}

/**
 * The feedback form and its send. The form used to show its thank-you overlay
 * and clear the message as soon as Send was tapped, before the request
 * finished, so a failed send was lost without a word and Send showed no
 * progress. Now Send is disabled and reads as sending while the request is
 * out, the overlay (`onSent`) and the clear wait for success, and a failure
 * keeps the message and shows an error, so Send is the retry.
 *
 * State is compose snapshot state, read by the feedback screen. Mutate it on
 * the main thread only; `post` moves a send's completion there.
 */
class FeedbackSendFlow(
    private val sender: FeedbackSender,
    private val post: (() -> Unit) -> Unit,
) {
    var message by mutableStateOf(TextFieldValue())
        private set

    var starCount by mutableIntStateOf(0)
        private set

    var status by mutableStateOf(FeedbackSendStatus.Idle)
        private set

    // the SDK feedback view controller's own send flag
    var controllerSending by mutableStateOf(false)

    // derived on every read, so it follows a send the moment it starts
    val isSendEnabled: Boolean
        get() = status != FeedbackSendStatus.Sending &&
            !controllerSending &&
            (message.text.isNotEmpty() || 0 < starCount)

    fun updateMessage(value: TextFieldValue) {
        if (value.text != message.text) {
            clearOutcome()
        }
        message = value
    }

    fun updateStarCount(count: Int) {
        clearOutcome()
        starCount = count
    }

    // an edit clears the last outcome, but not a send in flight
    private fun clearOutcome() {
        if (status != FeedbackSendStatus.Sending) {
            status = FeedbackSendStatus.Idle
        }
    }

    fun submit(onSent: () -> Unit) {
        if (!isSendEnabled) {
            return
        }
        status = FeedbackSendStatus.Sending
        val started = sender.send(message.text, starCount) { failed ->
            post {
                if (failed) {
                    status = FeedbackSendStatus.Failed
                } else {
                    status = FeedbackSendStatus.Sent
                    message = TextFieldValue()
                    starCount = 0
                    onSent()
                }
            }
        }
        if (!started) {
            status = FeedbackSendStatus.Failed
        }
    }
}
