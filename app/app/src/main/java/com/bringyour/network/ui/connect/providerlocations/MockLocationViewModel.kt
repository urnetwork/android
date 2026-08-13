package com.bringyour.network.ui.connect.providerlocations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.location.MockLocationController
import com.bringyour.network.location.MockLocationState
import com.bringyour.network.location.MockLocationTarget
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Feeds the mock location controller from the connected provider window: the
 * target is the oldest connected provider that has coordinates. The controller
 * owns all Android location state; this only translates sdk events.
 */
@HiltViewModel
class MockLocationViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val controller: MockLocationController,
) : ViewModel() {

    val state: StateFlow<MockLocationState> = controller.state

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
            viewModelScope.launch { pushTarget() }
        }
        viewModelScope.launch {
            controller.onTunnelChanged(device?.connectEnabled == true)
            pushTarget()
        }
    }

    private fun pushTarget() {
        val device = deviceManager.device
        controller.onTunnelChanged(device?.connectEnabled == true)

        val locations = device?.connectedProviderLocations
        var target: MockLocationTarget? = null
        if (locations != null) {
            // Read from the DEVICE, not the provider-locations view controller:
            // the device getter is the raw window, still sorted oldest
            // connected first, while the controller reorders it west to east
            // for the list and the globe. Take the first one that actually has
            // coordinates.
            for (i in 0 until locations.len()) {
                val location = locations.get(i)
                val lat: Double
                val lon: Double
                when {
                    location.hasCityCoordinates -> {
                        lat = location.cityLat
                        lon = location.cityLon
                    }
                    location.hasRegionCoordinates -> {
                        lat = location.regionLat
                        lon = location.regionLon
                    }
                    else -> continue
                }
                val label = listOf(location.city, location.region, location.country)
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString(", ")
                target = MockLocationTarget(
                    clientId = location.clientId?.idStr ?: "",
                    label = label,
                    lat = lat,
                    lon = lon,
                )
                break
            }
        }
        controller.onTargetChanged(target)
    }

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        pushTarget()
    }

    fun refreshEligibility() {
        controller.refreshEligibility()
    }

    override fun onCleared() {
        super.onCleared()
        sub?.close()
        sub = null
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
    }
}
