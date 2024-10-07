package com.bringyour.network.ui.components.overlays

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeAnimatedMainOverlayViewModel @Inject constructor(): ViewModel() {

    var animationRun by mutableStateOf(false)
        private set

    val setAnimationRun: (Boolean) -> Unit = { hasRun ->
        animationRun = hasRun
    }

    init {
        Log.i("WelcomeAnimatedMainOverlayViewModel", "init")
    }

}