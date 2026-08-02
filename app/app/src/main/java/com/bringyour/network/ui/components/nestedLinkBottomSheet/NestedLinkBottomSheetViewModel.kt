package com.bringyour.network.ui.components.nestedLinkBottomSheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.ui.connect.FilterLocationsState
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.LocationsViewController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NestedLinkBottomSheetViewModel @Inject constructor(
    private val deviceManager: DeviceManager
): ViewModel(), DefaultLifecycleObserver {

    private var locationsVc: LocationsViewController? = null
    private var filteredLocationsSub: com.bringyour.sdk.Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, LocationsViewController>(
            open = { openLocationsViewController(it) },
            close = { device, vc -> closeLocationsViewController(device, vc) },
        )

    private val _filterLocationsState = MutableStateFlow(FilterLocationsState.Loading)
    val filterLocationsState: StateFlow<FilterLocationsState> = _filterLocationsState.asStateFlow()

    val searchLocationResults = mutableStateListOf<ConnectLocation>()

    var targetLinkOpened by mutableStateOf(false)
        private set

    val setTargetLinkOpened: (Boolean) -> Unit = { opened ->
        targetLinkOpened = opened
    }

    var targetLink by mutableStateOf<String?>(null)
        private set

    val setTargetLink: (String?) -> Unit = { link ->
        targetLink = link
        setTargetLinkOpened(false)
    }

    var promptComplete by mutableStateOf(false)
        private set

    val setPromptComplete: (Boolean) -> Unit = { complete ->
        promptComplete = complete
    }

    private val makeConnectLocationCollection: (ConnectLocationList) -> Collection<ConnectLocation> = { list ->
        val locations = mutableListOf<ConnectLocation>()
        val n = list.len()

        for (i in 0 until n) {
            locations.add(list.get(i))
        }

        locations
    }

    val filterLocations:(String) -> Unit = { search ->
        locationsVc?.filterLocations(search)
    }

    private fun addFilteredLocationsListener(vc: LocationsViewController) {
        filteredLocationsSub = vc.addFilteredLocationsListener { filteredLocation, state ->
            viewModelScope.launch {
                if (locationsVc !== vc) {
                    return@launch
                }

                val newResults = mutableListOf<ConnectLocation>()

                filteredLocation?.let {
                    newResults.addAll(makeConnectLocationCollection(it.bestMatches))
                    newResults.addAll(makeConnectLocationCollection(it.devices))
                }

                Snapshot.withMutableSnapshot {
                    searchLocationResults.clear()
                    searchLocationResults.addAll(newResults)
                }

                FilterLocationsState.fromString(state)?.let {
                    _filterLocationsState.value = it
                }
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
                _filterLocationsState.value = FilterLocationsState.Loading
                Snapshot.withMutableSnapshot {
                    searchLocationResults.clear()
                }
                controllerOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun openLocationsViewController(device: DeviceLocal): LocationsViewController {
        val vc = device.openLocationsViewController()
        locationsVc = vc
        addFilteredLocationsListener(vc)
        vc.start()
        return vc
    }

    private fun closeLocationsViewController(
        device: DeviceLocal,
        vc: LocationsViewController,
    ) {
        filteredLocationsSub?.close()
        filteredLocationsSub = null
        vc.stop()
        device.closeViewController(vc)
        if (locationsVc === vc) {
            locationsVc = null
        }
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }

}
