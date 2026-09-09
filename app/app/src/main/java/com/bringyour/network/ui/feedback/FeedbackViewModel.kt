package com.bringyour.network.ui.feedback

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.TAG
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.FeedbackSendArgs
import com.bringyour.sdk.FeedbackSendNeeds
import com.bringyour.sdk.FeedbackViewController
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
): ViewModel(), DefaultLifecycleObserver {

    /**
     * The reason a campaign feedback link pre-selected ("what got in the way"
     * buttons in the not-activated email); reported with the feedback event.
     */
    var prefillReason by mutableStateOf("")
        private set

    /**
     * A campaign feedback link (ur.io/f/<token>?r=n|why=x) parked its values
     * on the application; resolve the token with the server and pre-fill the
     * rating or reason once, then drop the pending value.
     */
    fun consumeFeedbackPrefill() {
        val app = appContext as? com.bringyour.network.MainApplication ?: return
        val prefill = app.pendingFeedbackPrefill.value ?: return
        app.pendingFeedbackPrefill.value = null
        val api = deviceManager.device?.api ?: return
        api.onboardingFeedbackToken(prefill.token, prefill.rating.toLong(), prefill.reason) { result, err ->
            viewModelScope.launch {
                if (err != null || result == null || !result.ok) {
                    Log.i(TAG, "feedback prefill token not accepted: ${err?.message ?: result?.error}")
                    return@launch
                }
                if (0 < result.rating) {
                    setStarCount(result.rating.toInt())
                }
                prefillReason = result.reason ?: ""
            }
        }
    }

    private var feedbackVc: FeedbackViewController? = null
    private var isSendingSub: Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, FeedbackViewController>(
            open = { openFeedbackViewController(it) },
            close = { device, vc -> closeFeedbackViewController(device, vc) },
        )

    var feedbackMsg by mutableStateOf(TextFieldValue())
        private set

    private var isSendingFeedback by mutableStateOf(false)

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

    val sendFeedback:() -> Unit = sendFeedback@{

        val device = deviceManager.device
        if (!isSendingFeedback && device != null) {
            isSendingFeedback = true

            val feedbackArgs = FeedbackSendArgs()
            val needs = FeedbackSendNeeds()
            needs.other = feedbackMsg.text
            feedbackArgs.starCount = starCount.toLong()
            feedbackArgs.needs = needs

            val api = device.api
            if (api == null) {
                isSendingFeedback = false
                validateIsSendEnabled()
                return@sendFeedback
            }
            api.sendFeedback(feedbackArgs) { result, err ->

                if (err == null) {
                    com.bringyour.network.analytics.ClientEvents.feedbackSubmitted(
                        rating = starCount,
                        reason = prefillReason,
                        text = feedbackMsg.text,
                    )
                }

                if (err != null) {
                    Log.i(TAG, "error sending feedback: ${err.message}")
                    viewModelScope.launch {
                        isSendingFeedback = false
                        validateIsSendEnabled()
                    }
                    return@sendFeedback
                }

                if (_includeLogs.value) {

                    Log.i(TAG, "feedback id is: ${result.feedbackId.string()}")

                    /**
                     * upload logs
                     */
                    device.uploadLogs(result.feedbackId.string()) { _, uploadError ->

                        if (uploadError != null) {
                            Log.i(TAG, "error uploading logs: ${uploadError.message}")
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

    private fun addIsSendingListener(vc: FeedbackViewController) {
        isSendingSub = vc.addIsSendingFeedbackListener { isSending ->
            viewModelScope.launch {
                if (feedbackVc !== vc) {
                    return@launch
                }
                isSendingFeedback = isSending
                validateIsSendEnabled()
            }
        }
    }

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                controllerOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
        consumeFeedbackPrefill()
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun openFeedbackViewController(device: DeviceLocal): FeedbackViewController {
        val vc = device.openFeedbackViewController()
        feedbackVc = vc
        addIsSendingListener(vc)
        vc.start()
        return vc
    }

    private fun closeFeedbackViewController(
        device: DeviceLocal,
        vc: FeedbackViewController,
    ) {
        isSendingSub?.close()
        isSendingSub = null
        vc.stop()
        device.closeViewController(vc)
        if (feedbackVc === vc) {
            feedbackVc = null
        }
    }

    override fun onCleared() {
        isSendingFeedback = false
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }

}
