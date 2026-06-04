package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationList
import com.bringyour.sdk.LocationsViewController
import com.bringyour.network.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationsListViewModel @Inject constructor(
    private val deviceManager: DeviceManager
): ViewModel() {

    private var locationsVc: LocationsViewController? = null
    private var filteredLocationsSub: com.bringyour.sdk.Sub? = null

    private val _filterLocationsState = MutableStateFlow(FilterLocationsState.Loading)
    val filterLocationsState: StateFlow<FilterLocationsState> = _filterLocationsState.asStateFlow()

    val connectCountries = mutableStateListOf<ConnectLocation>()

    var searchQueryTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var currentSearchQuery by mutableStateOf("")
        private set

    val setSearchQueryTextFieldValue: (TextFieldValue) -> Unit = {
        searchQueryTextFieldValue = it
    }

    val devices = mutableStateListOf<ConnectLocation>()

    val regions = mutableStateListOf<ConnectLocation>()

    val cities = mutableStateListOf<ConnectLocation>()

    // when searching, items with matchDistance of 0
    val bestSearchMatches = mutableStateListOf<ConnectLocation>()

    val refreshLocations: () -> Unit = {
        locationsVc?.filterLocations(currentSearchQuery)
    }

    val filterLocations:(String) -> Unit = { search ->

        if (
            // we don't want to re-run the same query, if we're not in an error state
            (search != currentSearchQuery && _filterLocationsState.value != FilterLocationsState.Error) ||
            // but if we're in an error state, run the query
            _filterLocationsState.value == FilterLocationsState.Error)
        {
            currentSearchQuery = search
            locationsVc?.filterLocations(search)
        }
    }

    val getLocationColor: (String) -> Color = { color ->
        val hex = locationsVc?.getColorHex(color)
        if (hex != null) {
            try {
                Color(android.graphics.Color.parseColor("#$hex"))
            } catch (_: IllegalArgumentException) {
                Color.White
            }
        } else {
            Color.White
        }
    }

    private val makeConnectLocationCollection: (ConnectLocationList) -> Collection<ConnectLocation> = { list ->
        val locations = mutableListOf<ConnectLocation>()
        val n = list.len()

        for (i in 0 until n) {
            locations.add(list.get(i))
        }

        locations
    }

    private val addFilteredLocationsListener = {

        locationsVc?.let { vc ->
            filteredLocationsSub = vc.addFilteredLocationsListener { filteredLocation, state ->
                viewModelScope.launch {

                    val newBestSearchMatches = mutableListOf<ConnectLocation>()
                    val newConnectCountries = mutableListOf<ConnectLocation>()
                    val newDevices = mutableListOf<ConnectLocation>()
                    val newCities = mutableListOf<ConnectLocation>()
                    val newRegions = mutableListOf<ConnectLocation>()

                    filteredLocation?.let {
                        newBestSearchMatches.addAll(makeConnectLocationCollection(it.bestMatches))
                        newConnectCountries.addAll(makeConnectLocationCollection(it.countries))
                        newDevices.addAll(makeConnectLocationCollection(it.devices))
                        newCities.addAll(makeConnectLocationCollection(it.cities))
                        newRegions.addAll(makeConnectLocationCollection(it.regions))
                    }

                    Snapshot.withMutableSnapshot {
                        bestSearchMatches.clear()
                        bestSearchMatches.addAll(newBestSearchMatches)
                        connectCountries.clear()
                        connectCountries.addAll(newConnectCountries)
                        devices.clear()
                        devices.addAll(newDevices)
                        cities.clear()
                        cities.addAll(newCities)
                        regions.clear()
                        regions.addAll(newRegions)
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

        viewModelScope.launch {
            locationsVc?.start()
        }
    }

    override fun onCleared() {
        super.onCleared()

        filteredLocationsSub?.close()
        filteredLocationsSub = null

        locationsVc?.let {
            deviceManager.device?.closeViewController(it)
        }
    }
}

enum class FilterLocationsState {
    Loading,
    Loaded,
    Error;

    companion object {
        fun fromString(value: String): FilterLocationsState? {
            return when (value.uppercase()) {
                "LOCATIONS_LOADING" -> Loading
                "LOCATIONS_LOADED" -> Loaded
                "LOCATIONS_ERROR" -> Error
                else -> null
            }
        }
    }
}