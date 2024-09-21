package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.Client.LocationTypeCountry
import com.bringyour.client.ConnectLocation
import com.bringyour.client.LocationsViewController
import com.bringyour.client.Sub
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LocationsListViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager
): ViewModel() {

    private var locationsVc: LocationsViewController? = null

    var initialListLoaded by mutableStateOf(false)
        private set

    val connectCountries = mutableStateListOf<ConnectLocation>()

    val promotedLocations = mutableStateListOf<ConnectLocation>()

    private val subs = mutableListOf<Sub>()

    val getLocations = {
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

            locations.forEach { location ->
                providerCount += location.providerCount

                if (location.promoted) {
                    promotedLocations.add(location)
                }

                if (location.locationType == LocationTypeCountry) {
                    connectCountries.add(location)
                }
            }

            if (!initialListLoaded) {
                initialListLoaded = true
            }

        } else {
            connectCountries.clear()
        }
    }

    val filterLocations:(String) -> Unit = { search ->
        locationsVc?.filterLocations(search)
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

    init {

        val byDevice = byDeviceManager.byDevice
        locationsVc = byDevice?.openLocationsViewController()

        addFilteredLocationsListener()

        locationsVc?.start()
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