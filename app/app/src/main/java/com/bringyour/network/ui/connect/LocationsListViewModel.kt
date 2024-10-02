package com.bringyour.network.ui.connect

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.Client.LocationTypeCity
import com.bringyour.client.Client.LocationTypeCountry
import com.bringyour.client.Client.LocationTypeRegion
import com.bringyour.client.ConnectLocation
import com.bringyour.client.LocationsViewController
import com.bringyour.client.Sub
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationsListViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager
): ViewModel() {

    private var locationsVc: LocationsViewController? = null

    private val _locationsState = MutableStateFlow(FetchLocationsState.Loading)
    val locationsState: StateFlow<FetchLocationsState> = _locationsState.asStateFlow()

    val connectCountries = mutableStateListOf<ConnectLocation>()

    val promotedLocations = mutableStateListOf<ConnectLocation>()

    val devices = mutableStateListOf<ConnectLocation>()

    val regions = mutableStateListOf<ConnectLocation>()

    val cities = mutableStateListOf<ConnectLocation>()

    private val subs = mutableListOf<Sub>()

    private val getLocations = {
        val exportedLocations = locationsVc?.locations
        if (exportedLocations != null) {
            val locations = mutableListOf<ConnectLocation>()
            val n = exportedLocations.len()

            for (i in 0 until n) {
                locations.add(exportedLocations.get(i))
            }

            var providerCount = 0

            connectCountries.clear()
            promotedLocations.clear()
            devices.clear()
            cities.clear()
            regions.clear()

            locations.forEach { location ->
                providerCount += location.providerCount

                if (location.promoted) {
                    promotedLocations.add(location)
                }

                if (location.locationType == LocationTypeCountry) {
                    connectCountries.add(location)
                }

                if (location.locationType == LocationTypeCity) {
                    cities.add(location)
                }

                if (location.locationType == LocationTypeRegion) {
                    regions.add(location)
                }

                if (location.isDevice) {
                    devices.add(location)
                }

            }

            if (_locationsState.value != FetchLocationsState.Loaded) {
                setLocationsState(FetchLocationsState.Loaded)
            }

        } else {
            connectCountries.clear()
        }
    }

    val filterLocations:(String) -> Unit = { search ->
        locationsVc?.filterLocations(search)
        if (_locationsState.value != FetchLocationsState.Loading) {
            setLocationsState(FetchLocationsState.Loading)
        }
    }

    val getLocationColor: (String) -> Color = { color ->
        val hex = locationsVc?.getColorHex(color)
        Color(android.graphics.Color.parseColor("#$hex"))
    }

    private val addFilteredLocationsListener = {

        locationsVc?.let { vc ->
            vc.addFilteredLocationsListener {
                    getLocations()
            }
        }

    }

    private val setLocationsState: (FetchLocationsState) -> Unit = { state ->
        _locationsState.value = state
    }

    private val addFetchLocationsErrorListener = {

        locationsVc?.let { vc ->
            vc.addFilteredLocationsErrorListener { errorExists ->
                if (errorExists) {
                    setLocationsState(FetchLocationsState.Error)
                }
            }
        }

    }

    init {

        val byDevice = byDeviceManager.byDevice
        locationsVc = byDevice?.openLocationsViewController()

        addFilteredLocationsListener()
        addFetchLocationsErrorListener()

        viewModelScope.launch {
            locationsVc?.start()
        }
    }

    override fun onCleared() {
        super.onCleared()

        subs.forEach { sub ->
            sub.close()
        }
        subs.clear()

        locationsVc?.let {
            byDeviceManager.byDevice?.closeViewController(it)
        }
    }
}

enum class FetchLocationsState {
    Loading,
    Loaded,
    Error
}