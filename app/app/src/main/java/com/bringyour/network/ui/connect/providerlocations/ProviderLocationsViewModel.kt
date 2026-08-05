package com.bringyour.network.ui.connect.providerlocations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One connected provider, as rendered by the globe and the list. The sdk
 * returns these sorted oldest-connected first.
 */
data class ProviderLocationRow(
    val clientId: String,
    val country: String,
    val countryCode: String,
    val region: String,
    val city: String,
    val hasLocation: Boolean,
    // the coordinates to plot: the city centroid when known, else the region
    // centroid. null when the provider has no coordinates at all
    val lat: Double?,
    val lon: Double?,
    val connectedSinceMillis: Long,
) {
    val plottable: Boolean get() = lat != null && lon != null
}

@HiltViewModel
class ProviderLocationsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private val _providerLocations = MutableStateFlow<List<ProviderLocationRow>>(listOf())
    val providerLocations: StateFlow<List<ProviderLocationRow>> = _providerLocations.asStateFlow()

    private val _selectedClientId = MutableStateFlow<String?>(null)
    val selectedClientId: StateFlow<String?> = _selectedClientId.asStateFlow()

    private var sub: Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            attach(device)
        }
        attach(deviceManager.device)
    }

    private fun attach(device: DeviceLocal?) {
        sub?.close()
        sub = device?.addConnectedProviderLocationChangeListener {
            viewModelScope.launch { refresh() }
        }
        viewModelScope.launch { refresh() }
    }

    /**
     * Re-reads the current connected providers. The sdk re-emits fresh proxies
     * on every event, so the rows are compared by value and only assigned when
     * something actually changed (the same discipline the connect grid uses).
     */
    fun refresh() {
        val locations = deviceManager.device?.connectedProviderLocations
        val rows = mutableListOf<ProviderLocationRow>()
        if (locations != null) {
            for (i in 0 until locations.len()) {
                val location = locations.get(i)
                val lat: Double?
                val lon: Double?
                when {
                    location.hasCityCoordinates -> {
                        lat = location.cityLat
                        lon = location.cityLon
                    }
                    location.hasRegionCoordinates -> {
                        lat = location.regionLat
                        lon = location.regionLon
                    }
                    else -> {
                        lat = null
                        lon = null
                    }
                }
                rows.add(
                    ProviderLocationRow(
                        clientId = location.clientId?.idStr ?: "",
                        country = location.country,
                        countryCode = location.countryCode,
                        region = location.region,
                        city = location.city,
                        hasLocation = location.hasLocation,
                        lat = lat,
                        lon = lon,
                        connectedSinceMillis = location.connectedSinceMillis,
                    )
                )
            }
        }

        if (rows != _providerLocations.value) {
            _providerLocations.value = rows
        }
        // drop a selection whose provider left the window
        val selected = _selectedClientId.value
        if (selected != null && rows.none { it.clientId == selected }) {
            _selectedClientId.value = null
        }
    }

    fun select(clientId: String?) {
        _selectedClientId.value = clientId
    }

    /**
     * Drops the provider from the connection and stops it being re-discovered
     * for the rest of this connection. The row disappears when the sdk reports
     * the window change; the local list is trimmed first so the swipe does not
     * appear to snap back while that round trip happens.
     */
    fun removeProvider(clientId: String) {
        _providerLocations.value = _providerLocations.value.filter { it.clientId != clientId }
        if (_selectedClientId.value == clientId) {
            _selectedClientId.value = null
        }
        val id = runCatching { Sdk.parseId(clientId) }.getOrNull() ?: return
        deviceManager.device?.removeConnectedProvider(id)
    }

    /**
     * The oldest connected provider that has coordinates — the mock-location
     * target. The list is already sorted oldest first.
     */
    fun oldestPlottable(): ProviderLocationRow? =
        _providerLocations.value.firstOrNull { it.plottable }

    override fun onCleared() {
        super.onCleared()
        sub?.close()
        sub = null
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
    }
}
