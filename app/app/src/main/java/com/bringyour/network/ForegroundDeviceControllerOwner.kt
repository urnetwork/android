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

/**
 * A [ForegroundDeviceControllerOwner] for a polling controller that one screen
 * shows. The controller is open only while it is enabled (and the device is
 * set and the app is in the foreground), and it is started only while the
 * screen is visible. A hidden screen stops it without closing it, so the last
 * snapshot is there at once when the screen comes back; disabling it or going
 * to the background closes it.
 */
internal class VisibleDeviceControllerOwner<D : Any, C : Any>(
    open: (D) -> C,
    close: (D, C) -> Unit,
    private val start: (C) -> Unit,
    private val stop: (C) -> Unit,
) {
    private var startedController: C? = null
    private val owner = ForegroundDeviceControllerOwner<D, C>(
        open = open,
        close = { device, controller ->
            // the close stops the controller itself
            if (startedController === controller) {
                startedController = null
            }
            close(device, controller)
        },
    )
    private var foreground = false
    private var enabled = false
    private var visible = false

    val controller: C?
        get() = owner.controller

    /** The device the controller opens on; another device closes the old one's. */
    fun setDevice(nextDevice: D?) {
        owner.setDevice(nextDevice)
        reconcile()
    }

    /** Whether the app is in the foreground; the background closes the controller. */
    fun setForeground(nextForeground: Boolean) {
        foreground = nextForeground
        reconcile()
    }

    /** Whether the screen's feature is on; off closes the controller. */
    fun setEnabled(nextEnabled: Boolean) {
        enabled = nextEnabled
        reconcile()
    }

    /** Whether the screen shows; hidden stops the controller and keeps it open. */
    fun setVisible(nextVisible: Boolean) {
        visible = nextVisible
        reconcile()
    }

    /** Closes the controller and forgets the device, when the owner itself ends. */
    fun close() {
        foreground = false
        enabled = false
        visible = false
        owner.close()
    }

    /** Opens, starts, stops or closes the controller to match the state. */
    private fun reconcile() {
        owner.setForeground(foreground && enabled)
        val openController = owner.controller
        if (openController != null && visible) {
            if (startedController !== openController) {
                startedController = openController
                start(openController)
            }
        } else {
            startedController?.let {
                startedController = null
                stop(it)
            }
        }
    }
}
