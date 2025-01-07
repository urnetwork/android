package com.bringyour.network.ui.feedback

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.bringyour.sdk.FeedbackViewController
import com.bringyour.network.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private var feedbackVc: FeedbackViewController? = null

    var feedbackMsg by mutableStateOf(TextFieldValue())
        private set

    private var isSendingFeedback = false

    val setFeedbackMsg: (TextFieldValue) -> Unit = { msg ->
        feedbackMsg = msg
        Log.i("FeedbackViewModel", "setFeedbackMsg: ${msg.text}")
        validateIsSendEnabled()
    }

    var isSendEnabled by mutableStateOf(false)
        private set

    val validateIsSendEnabled = {
        isSendEnabled = !isSendingFeedback && feedbackMsg.text.isNotEmpty()
    }

    val sendFeedback:() -> Unit = {
        if (feedbackMsg.text.isNotEmpty()) {
            feedbackVc?.sendFeedback(feedbackMsg.text)
        }
    }

    val addIsSendingListener = {
        feedbackVc?.addIsSendingFeedbackListener { isSending ->
            isSendingFeedback = isSending
            validateIsSendEnabled()
        }
    }

    init {
        feedbackVc = deviceManager.vcManager?.openFeedbackViewController()

        addIsSendingListener()
    }

    override fun onCleared() {
        super.onCleared()

        feedbackVc?.let {
            deviceManager.vcManager?.closeViewController(it)
        }
    }

}