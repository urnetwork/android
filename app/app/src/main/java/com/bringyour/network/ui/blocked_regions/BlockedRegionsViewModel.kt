package com.bringyour.network.ui.blocked_regions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.BlockedLocation
import com.bringyour.sdk.Id
import com.bringyour.sdk.NetworkBlockLocationArgs
import com.bringyour.sdk.NetworkUnblockLocationArgs
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedRegionsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private val _blockedRegions = MutableStateFlow<List<BlockedLocation>>(emptyList())
    val blockedRegions: StateFlow<List<BlockedLocation>> = _blockedRegions.asStateFlow()

    private val _isFetchingLocations = MutableStateFlow<Boolean>(false)
    val isFetchingLocations: StateFlow<Boolean> = _isFetchingLocations.asStateFlow()

    private val _isProcessing = MutableStateFlow<Boolean>(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _displayBottomSheet = MutableStateFlow<Boolean>(false)
    val displayBottonSheet: StateFlow<Boolean> = _displayBottomSheet.asStateFlow()

    val setDisplayBottomSheet: (Boolean) -> Unit = {
        _displayBottomSheet.value = it
    }

    val fetchBlockedRegions: () -> Unit = {

        if (!_isFetchingLocations.value) {

            _isFetchingLocations.value = true

            deviceManager.device?.api?.getNetworkBlockedLocations { result, error ->

                if (error != null) {
                    Log.i(TAG, "error fetching blocked locations: ${error.message}")

                    viewModelScope.launch {
                        _isFetchingLocations.value = false
                    }

                    return@getNetworkBlockedLocations
                }

                if (result.blockedLocations != null) {

                    val locations = mutableListOf<BlockedLocation>()
                    val n = result.blockedLocations.len()

                    for (i in 0 until n) {
                        val location = result.blockedLocations.get(i)
                        locations.add(result.blockedLocations.get(i))
                    }

                    locations.sortBy { it.locationName.lowercase() }

                    viewModelScope.launch {
                        _blockedRegions.value = locations
                        _isFetchingLocations.value = false
                    }

                } else {
                    viewModelScope.launch {
                        _isFetchingLocations.value = false
                    }
                }

            }

        }

    }

    val blockRegion: (Id, String, String) -> Unit = { id, name, countryCode ->

        if (!_isProcessing.value && !isInList(id)) {

            _isProcessing.value = true

            val blockLocationArgs = NetworkBlockLocationArgs()
            blockLocationArgs.locationId = id

            deviceManager.device?.api?.networkBlockLocation(blockLocationArgs) { result, error ->

                if (error != null) {
                    Log.i(TAG, "error blocking region: ${error.message}")

                    viewModelScope.launch {
                        _isProcessing.value = false
                    }

                    return@networkBlockLocation
                }

                if (result.error != null) {
                    Log.i(TAG, "result error blocking region: ${result.error.message}")

                    viewModelScope.launch {
                        _isProcessing.value = false
                    }

                    return@networkBlockLocation
                }

                viewModelScope.launch {

                    // add to list
                    val blockedLocation = BlockedLocation()
                    blockedLocation.locationId = id
                    blockedLocation.locationName = name
                    blockedLocation.locationType = Sdk.LocationTypeCountry
                    blockedLocation.countryCode = countryCode

                    var locations = _blockedRegions.value + blockedLocation
                    locations = locations.sortedBy { it.locationName.lowercase() }
                    _blockedRegions.value = locations

                    _isProcessing.value = false
                }

            }

        }

    }

    val unblockLocation: (Id) -> Unit = { id ->

        if (isInList(id)) {

            // immediately remove from the list
            removeFromList(id)

            val args = NetworkUnblockLocationArgs()
            args.locationId = id
            deviceManager.device?.api?.networkUnblockLocation(args) { result, error ->

                if (error != null) {
                    Log.i(TAG, "error unblocking location: ${error.message}")
                    return@networkUnblockLocation
                }

                if (result.error != null) {
                    Log.i(TAG, "result error unblocking location: ${result.error.message}")
                    return@networkUnblockLocation
                }

            }

        }

    }

    val isInList: (Id) -> Boolean = { id ->
        _blockedRegions.value.any { it.locationId.cmp(id).toInt() == 0 }
    }

    val removeFromList: (Id) -> Unit = { id ->
        _blockedRegions.value = _blockedRegions.value.filter { it.locationId.cmp(id).toInt() != 0 }
    }

    init {
        fetchBlockedRegions()
    }

}