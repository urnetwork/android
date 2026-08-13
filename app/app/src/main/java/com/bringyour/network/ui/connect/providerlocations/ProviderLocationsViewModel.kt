package com.bringyour.network.ui.connect.providerlocations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.ProviderLocationsViewController
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One connected provider, as rendered by the globe and the list. The rows
 * arrive in the sdk view controller's display order — west to east about the
 * providers' centroid, then the ones with no coordinates — so the list reads
 * left to right in the order the globe's wheel steps through.
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
    private var selectedSub: Sub? = null
    // the scroll/selection logic lives in the sdk's shared
    // ProviderLocationsViewController; the vc must be closed on the device
    // that opened it, so the owner is tracked across device changes
    private var vc: ProviderLocationsViewController? = null
    private var vcDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            attach(device)
        }
        attach(deviceManager.device)
    }

    private fun attach(device: DeviceLocal?) {
        sub?.close()
        selectedSub?.close()
        detachViewController()
        vcDevice = device
        vc = device?.openProviderLocationsViewController()
        sub = device?.addConnectedProviderLocationChangeListener {
            viewModelScope.launch { refresh() }
        }
        selectedSub = vc?.addSelectedProviderLocationChangeListener {
            viewModelScope.launch { refreshSelection() }
        }
        viewModelScope.launch {
            refresh()
            refreshSelection()
        }
    }

    private fun detachViewController() {
        val openVc = vc ?: return
        vc = null
        vcDevice?.closeProviderLocationsViewController(openVc)
        vcDevice = null
    }

    /**
     * Re-reads the current connected providers, from the view controller
     * rather than the device: same window, in the shared display order, and
     * read from the controller so the rows and the selection always come from
     * one snapshot. The sdk re-emits fresh proxies
     * on every event, so the rows are compared by value and only assigned when
     * something actually changed (the same discipline the connect grid uses).
     */
    fun refresh() {
        val locations = vc?.providerLocations
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
    }

    // The vc keeps the selection pointed at a connected provider — the longest
    // connected one by default, the nearest one when the selected provider
    // leaves — so this only mirrors its state ("" = no providers at all).
    private fun refreshSelection() {
        _selectedClientId.value = vc?.selectedClientId?.takeIf { it.isNotEmpty() }
    }

    fun select(clientId: String?) {
        vc?.setSelectedClientId(clientId ?: "")
    }

    /**
     * Moves the globe's wheel selection by [steps] providers, positive east.
     * The order (west to east relative to the providers' centroid) and the
     * clamping at the wheel's ends live in the sdk view controller, shared by
     * every platform.
     */
    fun step(steps: Int) {
        vc?.stepSelection(steps)
    }

    /**
     * Drops the provider from the connection and stops it being re-discovered
     * for the rest of this connection. The row disappears when the sdk reports
     * the window change; the local list is trimmed first so the swipe does not
     * appear to snap back while that round trip happens.
     *
     * The vc moves the selection to the nearest provider when the removed one
     * was selected, so there is nothing to clear here.
     */
    fun removeProvider(clientId: String) {
        _providerLocations.value = _providerLocations.value.filter { it.clientId != clientId }
        vc?.removeProvider(clientId)
    }

    override fun onCleared() {
        super.onCleared()
        sub?.close()
        sub = null
        selectedSub?.close()
        selectedSub = null
        detachViewController()
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
    }
}
