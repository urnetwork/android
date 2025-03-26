//package com.bringyour.network.ui.shared.viewmodels
//
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import com.bringyour.network.DeviceManager
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import javax.inject.Inject
//
//@HiltViewModel
//class PromptReviewViewModel @Inject constructor(
//    private val deviceManager: DeviceManager,
//): ViewModel() {
//
//    val checkTriggerPromptReview = {
//        Log.i("PromptReviewViewModel", "check trigger prompt review")
//        deviceManager.device?.shouldShowRatingDialog == true
//    }
//
//    val enablePromptReview = {
//        deviceManager.canShowRatingDialog = true
//    }
//
//    val disablePromptReview = {
//        deviceManager.canShowRatingDialog = false
//    }
//
//}