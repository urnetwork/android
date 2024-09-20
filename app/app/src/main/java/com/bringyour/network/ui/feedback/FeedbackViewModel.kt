package com.bringyour.network.ui.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.bringyour.client.FeedbackViewController
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
): ViewModel() {

    private var feedbackVc: FeedbackViewController? = null

    var feedbackMsg by mutableStateOf(TextFieldValue())
        private set

    var isSendingFeedback by mutableStateOf(false)
        private set

    val setFeedbackMsg: (TextFieldValue) -> Unit = { msg ->
        feedbackMsg = msg
    }

    val sendFeedback:() -> Unit = {
        if (feedbackMsg.text.isNotEmpty()) {
            feedbackVc?.sendFeedback(feedbackMsg.text)
        }
    }

    val addIsSendingListener = {
        feedbackVc?.addIsSendingFeedbackListener { isSending ->
            isSendingFeedback = isSending
        }
    }

    init {
        val byDevice = byDeviceManager.byDevice

        feedbackVc = byDevice?.openFeedbackViewController()

        addIsSendingListener()
    }

    override fun onCleared() {
        super.onCleared()

        feedbackVc?.let {
            byDeviceManager.byDevice?.closeViewController(it)
        }
    }

}