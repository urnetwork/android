package com.bringyour.network.location

import com.bringyour.network.DeviceManager
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds MockLocationController with tunnel lifecycle and exit provider location updates
 * from the SDK DeviceLocal instance across the entire application lifecycle.
 */
@Singleton
class MockLocationFeeder @Inject constructor(
    private val deviceManager: DeviceManager,
    private val controller: MockLocationController,
) {
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var connectSub: Sub? = null
    private var providerSub: Sub? = null

    fun start() {
        if (removeDeviceChangeListener != null) return
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            attach(device)
        }
        attach(deviceManager.device)
    }

    private fun attach(device: DeviceLocal?) {
        connectSub?.close()
        connectSub = null
        providerSub?.close()
        providerSub = null

        if (device != null) {
            connectSub = device.addConnectChangeListener { enabled ->
                controller.onTunnelChanged(enabled)
            }
            providerSub = device.addConnectedProviderLocationChangeListener {
                pushTarget(device)
            }
            controller.onTunnelChanged(device.connectEnabled)
            pushTarget(device)
        } else {
            controller.onTunnelChanged(false)
            controller.onTargetChanged(null)
        }
    }

    private fun pushTarget(device: DeviceLocal) {
        controller.onTunnelChanged(device.connectEnabled)
        val locations = device.connectedProviderLocations
        var target: MockLocationTarget? = null
        if (locations != null) {
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
}
