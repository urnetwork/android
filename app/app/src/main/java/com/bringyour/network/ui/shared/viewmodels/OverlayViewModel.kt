package com.bringyour.network.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.bringyour.network.ui.components.overlays.OverlayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject


@HiltViewModel
class OverlayViewModel @Inject constructor(
): ViewModel() {

    // The overlays are dialog destinations of their own navigation graph
    // (FullScreenOverlay, android#128): its back stack holds what shows, and
    // the overlay's close button or Back pops it. A launch is only a request
    // to show one, kept until FullScreenOverlay takes it.
    private val overlayRequestChannel = Channel<OverlayMode>(Channel.BUFFERED)
    val overlayRequests: Flow<OverlayMode> = overlayRequestChannel.receiveAsFlow()

    val launch: (OverlayMode) -> Unit = { mode ->
        overlayRequestChannel.trySend(mode)
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