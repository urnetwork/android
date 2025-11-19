package com.bringyour.network.ui.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.bringyour.sdk.FeedbackViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.sdk.ConnectLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        feedbackVc?.sendFeedback(feedbackMsg.text, starCount.toLong())
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