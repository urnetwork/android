package com.bringyour.network.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.bringyour.network.ui.components.overlays.OverlayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


@HiltViewModel
class OverlayViewModel @Inject constructor(
): ViewModel() {

    private val _overlayModeState = MutableStateFlow<OverlayMode?>(null)
    val overlayModeState: StateFlow<OverlayMode?> = _overlayModeState.asStateFlow()


    val launch: (OverlayMode?) -> Unit = { mode ->
        _overlayModeState.value = mode
    }

    // The Pro celebration flight (ProSunglassesFlight). Each launch bumps the
    // sequence so a new flight starts even while one is in the air; the host
    // clears it when the flight it rendered ends, never a later one.
    private val _sunglassesFlightSequence = MutableStateFlow(0L)
    val sunglassesFlightSequence: StateFlow<Long> = _sunglassesFlightSequence.asStateFlow()

    fun launchSunglassesFlight() {
        _sunglassesFlightSequence.value += 1
    }

    fun finishSunglassesFlight(sequence: Long) {
        if (_sunglassesFlightSequence.value == sequence) {
            _sunglassesFlightSequence.value = 0L
        }
    }
    
}