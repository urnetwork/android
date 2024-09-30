package com.bringyour.network.ui.shared.viewmodels

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
        if (byDeviceManager.byDevice?.stats?.userSuccess == true) {
            _promptReview.value = true
        }
    }

    val resetPromptReview = {
        _promptReview.value = false
    }

}