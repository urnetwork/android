package com.bringyour.network

/**
 * Owns one presentation-only SDK controller for the current device while the
 * app process is foregrounded. It captures the device that opened the
 * controller and always closes through that same owner, even if DeviceManager
 * has already published a replacement.
 */
internal class ForegroundDeviceControllerOwner<D : Any, C : Any>(
    private val open: (D) -> C,
    private val close: (D, C) -> Unit,
) {
    private var device: D? = null
    private var foreground = false

    var controller: C? = null
        private set

    fun setDevice(nextDevice: D?) {
        if (device === nextDevice) {
            return
        }
        closeController()
        device = nextDevice
        reconcile()
    }

    fun setForeground(nextForeground: Boolean) {
        if (foreground == nextForeground) {
            return
        }
        foreground = nextForeground
        reconcile()
    }

    fun close() {
        foreground = false
        closeController()
        device = null
    }

    private fun reconcile() {
        if (!foreground) {
            closeController()
            return
        }
        if (controller == null) {
            device?.let { controller = open(it) }
        }
    }

    private fun closeController() {
        val ownedController = controller ?: return
        val owningDevice = device
        controller = null
        if (owningDevice != null) {
            close(owningDevice, ownedController)
        }
    }
}
