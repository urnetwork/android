package com.bringyour.network.ui.excluded_exit_locations

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.TAG
import com.bringyour.network.ui.connect.FilterLocationsState
import com.bringyour.network.ui.connect.locationKey
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.LocalState
import com.bringyour.sdk.LocationsViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A search's places that can be excluded, grouped as the location list groups them. */
data class ExcludedExitLocationSearch(
    val countries: List<ConnectLocation> = listOf(),
    val regions: List<ConnectLocation> = listOf(),
    val cities: List<ConnectLocation> = listOf(),
    // the search has answered, so no places means none were found
    val loaded: Boolean = false,
)

/**
 * The excluded exit locations screen: the places a connection's picks avoid
 * (LocalState.getExcludedExitLocations), the newest first, and a search of the
 * places to add. The list is the signed-in space's local state; it is read and
 * changed off the main thread, one change at a time, in order, and a change
 * the sdk refuses (past its most) is logged and leaves the list as it is. The
 * search runs on a locations view controller of its own, opened while the app
 * is in front as the location list's is, so the location list's search stays
 * as the user left it. Main thread otherwise.
 */
@HiltViewModel
class ExcludedExitLocationsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private var locationsVc: LocationsViewController? = null
    private var filteredLocationsSub: Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, LocationsViewController>(
            open = { openLocationsViewController(it) },
            close = { device, vc -> closeLocationsViewController(device, vc) },
        )

    // the device the list is of
    private var device: DeviceLocal? = null
    private val editLock = Mutex()

    private val _excludedLocations = MutableStateFlow<List<ConnectLocation>>(listOf())
    val excludedLocations: StateFlow<List<ConnectLocation>> = _excludedLocations.asStateFlow()

    // the excluded places' ids (locationKey), for the search's rows
    private val _excludedKeys = MutableStateFlow<Set<String>>(setOf())
    val excludedKeys: StateFlow<Set<String>> = _excludedKeys.asStateFlow()

    private val _search = MutableStateFlow(ExcludedExitLocationSearch())
    val search: StateFlow<ExcludedExitLocationSearch> = _search.asStateFlow()

    var searchQueryTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    // the last query searched; the screen shows the search for one that is not empty
    var currentSearchQuery by mutableStateOf("")
        private set

    val setSearchQueryTextFieldValue: (TextFieldValue) -> Unit = {
        searchQueryTextFieldValue = it
    }

    /** Searches the places, as the location list does. */
    val filterLocations: (String) -> Unit = { query ->
        currentSearchQuery = query
        // the last search's places are not this one's
        _search.value = ExcludedExitLocationSearch()
        locationsVc?.filterLocations(query)
    }

    /** Excludes the place; `changed` runs on the main thread once the list holds it. */
    fun exclude(location: ConnectLocation, changed: () -> Unit) {
        update(changed) { it.addExcludedExitLocation(location) }
    }

    /** Stops excluding the place; `changed` runs on the main thread after. */
    fun remove(location: ConnectLocation, changed: () -> Unit) {
        update(changed) { it.removeExcludedExitLocation(location) }
    }

    // makes the change in the device's local state, then publishes the list it
    // holds, unless another device came meanwhile; `changed` follows a change
    // the sdk took
    private fun update(changed: () -> Unit = {}, change: ((LocalState) -> Unit)? = null) {
        val device = this.device ?: return
        val localState = device.networkSpace?.asyncLocalState?.localState ?: return
        viewModelScope.launch {
            val (applied, locations) = editLock.withLock {
                withContext(Dispatchers.IO) {
                    val applied = change?.let { edit ->
                        runCatching { edit(localState) }
                            .onFailure { Log.i(TAG, "[exclusions]could not change the excluded exit locations: ${it.message}") }
                            .isSuccess
                    } ?: false
                    applied to toList(localState.excludedExitLocations)
                }
            }
            if (this@ExcludedExitLocationsViewModel.device !== device) {
                return@launch
            }
            _excludedLocations.value = locations
            _excludedKeys.value = locations.map { locationKey(it) }.toSet()
            if (applied) {
                changed()
            }
        }
    }

    private fun toList(list: ConnectLocationList?): List<ConnectLocation> =
        if (list == null) listOf() else (0 until list.len()).mapNotNull { list.get(it) }

    private fun openLocationsViewController(device: DeviceLocal): LocationsViewController {
        val vc = device.openLocationsViewController()
        locationsVc = vc
        filteredLocationsSub = vc.addFilteredLocationsListener { filteredLocations, state ->
            // only what can be excluded: countries, regions and cities. A
            // place the query names exactly is a best match rather than in
            // its type's list, so it joins its type's list first.
            val bestMatches = toList(filteredLocations?.bestMatches)
            fun places(type: String, list: ConnectLocationList?): List<ConnectLocation> =
                (bestMatches.filter { it.locationType == type } + toList(list))
                    .filter { Sdk.canExcludeExitLocation(it) }
            val search = ExcludedExitLocationSearch(
                countries = places(Sdk.LocationTypeCountry, filteredLocations?.countries),
                regions = places(Sdk.LocationTypeRegion, filteredLocations?.regions),
                cities = places(Sdk.LocationTypeCity, filteredLocations?.cities),
                loaded = FilterLocationsState.fromString(state) == FilterLocationsState.Loaded,
            )
            viewModelScope.launch {
                if (locationsVc === vc) {
                    _search.value = search
                }
            }
        }
        vc.start()
        if (currentSearchQuery.isNotEmpty()) {
            vc.filterLocations(currentSearchQuery)
        }
        return vc
    }

    private fun closeLocationsViewController(device: DeviceLocal, vc: LocationsViewController) {
        filteredLocationsSub?.close()
        filteredLocationsSub = null
        vc.stop()
        device.closeViewController(vc)
        if (locationsVc === vc) {
            locationsVc = null
        }
    }

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                controllerOwner.setDevice(device)
                // the list is the signed-in space's
                if (this@ExcludedExitLocationsViewModel.device !== device) {
                    this@ExcludedExitLocationsViewModel.device = device
                    _excludedLocations.value = listOf()
                    _excludedKeys.value = setOf()
                    _search.value = ExcludedExitLocationSearch()
                    update()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
