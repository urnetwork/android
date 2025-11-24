package com.bringyour.network.ui.feedback

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.FeedbackViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.FeedbackSendArgs
import com.bringyour.sdk.FeedbackSendNeeds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private var feedbackVc: FeedbackViewController? = null

    var feedbackMsg by mutableStateOf(TextFieldValue())
        private set

    private var isSendingFeedback = false

    var starCount by mutableIntStateOf(0)
        private set

    val setStarCount: (Int) -> Unit = { count ->
        starCount = count
        validateIsSendEnabled()
    }

    var promptSolanaReview by mutableStateOf(false)
        private set

    val setPromptSolanaReview: (Boolean) -> Unit = {
        promptSolanaReview = it
    }

    val setFeedbackMsg: (TextFieldValue) -> Unit = { msg ->
        feedbackMsg = msg
        validateIsSendEnabled()
    }

    var isSendEnabled by mutableStateOf(false)
        private set

    private val _includeLogs = MutableStateFlow<Boolean>(false)
    val includeLogs: StateFlow<Boolean> = _includeLogs.asStateFlow()

    val toggleIncludeLogs: () -> Unit = {
        val currentIncludeLogs = _includeLogs.value
        _includeLogs.value = !currentIncludeLogs
    }

    val validateIsSendEnabled = {
        isSendEnabled = !isSendingFeedback && (feedbackMsg.text.isNotEmpty() || starCount > 0)
    }

    val sendFeedback:() -> Unit = {

        if (!isSendingFeedback) {
            isSendingFeedback = true

            val feedbackArgs = FeedbackSendArgs()
            val needs = FeedbackSendNeeds()
            needs.other = feedbackMsg.text
            feedbackArgs.starCount = starCount.toLong()
            feedbackArgs.needs = needs

            deviceManager.device?.api?.sendFeedback(feedbackArgs) { result, err ->

                if (err != null) {
                    Log.i(TAG, "error sending feedback: ${err.message}")
                    return@sendFeedback
                }

                if (_includeLogs.value) {

                    Log.i(TAG, "feedback id is: ${result.feedbackId.string()}")

                    /**
                     * upload logs
                     */
                    deviceManager.device?.uploadLogs(result.feedbackId.string()) { result, err ->

                        if (err != null) {
                            Log.i(TAG, "error uploading logs: ${err.message}")
                        }

                        viewModelScope.launch {
                            isSendingFeedback = false
                            validateIsSendEnabled()
                        }

                    }
                } else {

                    /**
                     * not uploading logs, continue
                     */
                    viewModelScope.launch {
                        isSendingFeedback = false
                        validateIsSendEnabled()
                    }

                }

            }

        }

    }

    val addIsSendingListener = {
        feedbackVc?.addIsSendingFeedbackListener { isSending ->
            isSendingFeedback = isSending
            validateIsSendEnabled()
        }
    }

    init {
        feedbackVc = deviceManager.device?.openFeedbackViewController()

        addIsSendingListener()
    }

    override fun onCleared() {
        super.onCleared()

        feedbackVc?.let {
            deviceManager.device?.closeViewController(it)
        }
    }

}