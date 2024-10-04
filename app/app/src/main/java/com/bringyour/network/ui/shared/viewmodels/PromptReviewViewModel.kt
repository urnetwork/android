package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PromptReviewViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
): ViewModel() {

    private val _promptReview = MutableStateFlow(false)
    val promptReview: StateFlow<Boolean> = _promptReview

    val checkTriggerPromptReview = {
        Log.i("PromptReviewViewModel", "check trigger prompt review")
        if (byDeviceManager.byDevice?.shouldShowRatingDialog == true) {
            Log.i("PromptReviewViewModel", "prompt the review")
            _promptReview.value = true
        }
    }

    val resetPromptReview = {
        _promptReview.value = false
        byDeviceManager.canShowRatingDialog = false
    }

}