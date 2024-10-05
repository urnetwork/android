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
    
}