package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.bringyour.client.Client
import com.bringyour.client.OverlayViewController
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.connect.FetchLocationsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


@HiltViewModel
class OverlayViewModel @Inject constructor(
): ViewModel() {

    // private var overlayViewController: OverlayViewController? = null

    private val _overlayModeState = MutableStateFlow<OverlayMode?>(null)
    val overlayModeState: StateFlow<OverlayMode?> = _overlayModeState.asStateFlow()


    val launch: (OverlayMode?) -> Unit = { mode ->
        _overlayModeState.value = mode
//        Log.i("OverlayViewModel", "about to launch: $mode")
//        Log.i("OverlayViewModel", "overlay view controller is $overlayViewController")
//        overlayViewController?.openOverlay(mode.toString())
    }

    init {
//        overlayViewController = Client.newOverlayViewController()
//        Log.i("OverlayViewModel", "overlay view controller is: $overlayViewController")
    }

}