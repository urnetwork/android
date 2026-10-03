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

    // the sdk calls behind the model
    private val source: BlockedRegionsSource<BlockedLocation> = object : BlockedRegionsSource<BlockedLocation> {
        override fun fetch(done: (Result<List<BlockedLocation>>) -> Unit): Boolean {
            val api = deviceManager.device?.api ?: return false
            api.getNetworkBlockedLocations { result, error ->
                if (error != null) {
                    Log.i(TAG, "error fetching blocked locations: ${error.message}")
                    done(Result.failure(error))
                    return@getNetworkBlockedLocations
                }
                val blockedLocations = result?.blockedLocations
                if (blockedLocations == null) {
                    // no list in the reply: keep what is shown
                    done(Result.success(model.regions.value))
                    return@getNetworkBlockedLocations
                }
                val locations = mutableListOf<BlockedLocation>()
                for (i in 0 until blockedLocations.len()) {
                    locations.add(blockedLocations.get(i))
                }
                done(Result.success(locations))
            }
            return true
        }

        override fun block(location: BlockedLocation, done: (failed: Boolean) -> Unit): Boolean {
            val api = deviceManager.device?.api ?: return false
            val args = NetworkBlockLocationArgs()
            args.locationId = location.locationId
            api.networkBlockLocation(args) { result, error ->
                val failed = error != null || result?.error != null
                if (failed) {
                    Log.i(TAG, "error blocking region: ${error?.message ?: result?.error?.message}")
                }
                done(failed)
            }
            return true
        }

        override fun unblock(location: BlockedLocation, done: (failed: Boolean) -> Unit): Boolean {
            val api = deviceManager.device?.api ?: return false
            val args = NetworkUnblockLocationArgs()
            args.locationId = location.locationId
            api.networkUnblockLocation(args) { result, error ->
                val failed = error != null || result?.error != null
                if (failed) {
                    Log.i(TAG, "error unblocking location: ${error?.message ?: result?.error?.message}")
                }
                done(failed)
            }
            return true
        }
    }

    private val model: BlockedRegionsModel<BlockedLocation> = BlockedRegionsModel(
        source = source,
        post = { block -> viewModelScope.launch { block() } },
        nameOf = { it.locationName },
        isSame = { a, b -> a.locationId.cmp(b.locationId).toInt() == 0 },
    )

    val blockedRegions: StateFlow<List<BlockedLocation>> = model.regions

    val isFetchingLocations: StateFlow<Boolean> = model.isFetching

    val isProcessing: StateFlow<Boolean> = model.isProcessing

    val notice: StateFlow<BlockedRegionsNotice?> = model.notice

    val clearNotice: () -> Unit = {
        model.clearNotice()
    }

    private val _displayBottomSheet = MutableStateFlow<Boolean>(false)
    val displayBottonSheet: StateFlow<Boolean> = _displayBottomSheet.asStateFlow()

    val setDisplayBottomSheet: (Boolean) -> Unit = {
        _displayBottomSheet.value = it
    }

    val fetchBlockedRegions: () -> Unit = {
        model.fetch()
    }

    val blockRegion: (Id, String, String) -> Unit = { id, name, countryCode ->
        val blockedLocation = BlockedLocation()
        blockedLocation.locationId = id
        blockedLocation.locationName = name
        blockedLocation.locationType = Sdk.LocationTypeCountry
        blockedLocation.countryCode = countryCode
        model.block(blockedLocation)
    }

    val unblockLocation: (Id) -> Unit = { id ->
        blockedRegions.value
            .find { it.locationId.cmp(id).toInt() == 0 }
            ?.let { model.unblock(it) }
    }

    init {
        fetchBlockedRegions()
    }

}
