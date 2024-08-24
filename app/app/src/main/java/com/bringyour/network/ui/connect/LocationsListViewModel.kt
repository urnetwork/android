package com.bringyour.network.ui.connect

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client.LocationTypeCountry
import com.bringyour.client.ConnectLocation
import com.bringyour.client.LocationsViewModel
import com.bringyour.client.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class LocationsListViewModel @Inject constructor(
    private val byDevice: BringYourDevice?
): ViewModel() {

    private var locationsVm = byDevice?.openLocationsViewModel()

    var totalProviderCount = mutableIntStateOf(0)
        private set

    val connectCountries = mutableStateListOf<ConnectLocation>()

    val promotedLocations = mutableStateListOf<ConnectLocation>()

    private val subs = mutableListOf<Sub>()

    val getLocations = {
        val exportedLocations = locationsVm?.locations
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

            totalProviderCount.intValue = providerCount
        } else {
            connectCountries.clear()
            totalProviderCount.intValue = 0
        }
    }

    val filterLocations:(String) -> Unit = { search ->
        locationsVm?.filterLocations(search)
    }

    val getLocationColor: (String) -> Color = { color ->
        val hex = locationsVm?.getColorHex(color)
        Color(android.graphics.Color.parseColor("#$hex"))
    }

    private val addFilteredLocationsListener = {

        if (locationsVm != null) {

            subs.add(locationsVm!!.addFilteredLocationsListener {
                runBlocking(Dispatchers.Main.immediate) {
                    getLocations()
                }
            })
        }
    }

    init {
        addFilteredLocationsListener()

        locationsVm?.start()
    }

    override fun onCleared() {
        super.onCleared()

        subs.forEach { sub ->
            sub.close()
        }
        subs.clear()

        locationsVm?.let {
            byDevice?.closeViewController(it)
        }
    }
}