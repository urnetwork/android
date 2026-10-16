package com.bringyour.network.ui.connect

import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.ContractStatus
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub

/**
 * What the connect screen's view model uses of the sdk device and its connect
 * view controller. The sdk's classes are final and need the native library,
 * so the view model reaches them through these two interfaces, and its tests
 * run it on a fake sdk (ConnectViewModelTest). The members are the sdk's own,
 * one call each ([SdkConnectDevice], [SdkConnectController]); listeners report
 * on the sdk's threads, as the sdk's do.
 */
internal interface ConnectDevice {
    /**
     * The sdk device this is, the same object for every wrapper of it: what
     * the connect command queue keys a device's commands on (ConnectCommands).
     */
    val identity: Any

    val connectEnabled: Boolean
    val tunnelStarted: Boolean
    val contractStatus: ContractStatus?

    fun addTunnelChangeListener(listener: (tunnelStarted: Boolean) -> Unit): Sub
    fun addContractStatusChangeListener(listener: () -> Unit): Sub

    fun openConnectViewController(): ConnectController

    /** Closes a controller this device opened. */
    fun closeViewController(controller: ConnectController)
}

/** The sdk's connect view controller, as [ConnectDevice] describes. */
internal interface ConnectController {
    val connectionStatus: String?
    val grid: ConnectGrid?
    val selectedLocation: ConnectLocation?

    fun addGridListener(listener: () -> Unit): Sub
    fun addConnectionStatusListener(listener: () -> Unit): Sub
    fun addSelectedLocationListener(listener: () -> Unit): Sub

    fun start()
    fun stop()

    fun connect(location: ConnectLocation)
    fun connectBestAvailable()
    fun disconnect()
}

/** The sdk device the device manager publishes. */
internal class SdkConnectDevice(val device: DeviceLocal) : ConnectDevice {
    override val identity: Any get() = device
    override val connectEnabled: Boolean get() = device.connectEnabled
    override val tunnelStarted: Boolean get() = device.tunnelStarted
    override val contractStatus: ContractStatus? get() = device.contractStatus

    override fun addTunnelChangeListener(listener: (tunnelStarted: Boolean) -> Unit): Sub =
        device.addTunnelChangeListener { listener(it) }

    override fun addContractStatusChangeListener(listener: () -> Unit): Sub =
        device.addContractStatusChangeListener { listener() }

    override fun openConnectViewController(): ConnectController =
        SdkConnectController(device.openConnectViewController())

    override fun closeViewController(controller: ConnectController) {
        // a controller this device opened is always the sdk's
        device.closeViewController((controller as SdkConnectController).controller)
    }
}

/** A connect view controller an [SdkConnectDevice] opened. */
internal class SdkConnectController(val controller: ConnectViewController) : ConnectController {
    override val connectionStatus: String? get() = controller.connectionStatus
    override val grid: ConnectGrid? get() = controller.grid
    override val selectedLocation: ConnectLocation? get() = controller.selectedLocation

    override fun addGridListener(listener: () -> Unit): Sub =
        controller.addGridListener { listener() }

    override fun addConnectionStatusListener(listener: () -> Unit): Sub =
        controller.addConnectionStatusListener { listener() }

    override fun addSelectedLocationListener(listener: () -> Unit): Sub =
        controller.addSelectedLocationListener { listener() }

    override fun start() = controller.start()
    override fun stop() = controller.stop()

    override fun connect(location: ConnectLocation) = controller.connect(location)
    override fun connectBestAvailable() = controller.connectBestAvailable()
    override fun disconnect() = controller.disconnect()
}
