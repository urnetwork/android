package com.bringyour.network.ui.components.nestedLinkBottomSheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationList
import com.bringyour.sdk.LocationsViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.ui.connect.FilterLocationsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NestedLinkBottomSheetViewModel @Inject constructor(
    private val deviceManager: DeviceManager
): ViewModel() {

    private var locationsVc: LocationsViewController? = null

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

    private val addFilteredLocationsListener = {

        locationsVc?.let { vc ->
            vc.addFilteredLocationsListener { filteredLocation, state ->
                viewModelScope.launch {

                    searchLocationResults.clear()

                    filteredLocation?.let {
                        searchLocationResults.addAll(makeConnectLocationCollection(it.bestMatches))
                        searchLocationResults.addAll(makeConnectLocationCollection(it.devices))
                    }

                    FilterLocationsState.fromString(state)?.let {
                        _filterLocationsState.value = it
                    }
                }
            }
        }
    }

    init {

        locationsVc = deviceManager.device?.openLocationsViewController()

        addFilteredLocationsListener()

        locationsVc?.start()
    }

    override fun onCleared() {
        super.onCleared()

        locationsVc?.let {
            deviceManager.device?.closeViewController(it)
        }
    }

}