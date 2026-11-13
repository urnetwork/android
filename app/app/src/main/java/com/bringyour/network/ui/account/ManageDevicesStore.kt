package com.bringyour.network.ui.account

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bringyour.sdk.Api
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.ManageDevicesListener
import com.bringyour.sdk.ManageDevicesSignInRetry
import com.bringyour.sdk.ManageDevicesSnapshot
import com.bringyour.sdk.ManageDevicesViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub

private const val TAG = "ManageDevicesStore"

// how long "Connecting this device" shows after a removal added it again
private const val CONNECTING_MILLIS = 10_000L

/** One device or hosted proxy of the list. */
data class ManagedDeviceUi(
    val clientId: String,
    // a hosted proxy, else a device
    val proxy: Boolean,
    // the device's name, else its description; may be empty
    val name: String,
    // system and model; may be empty
    val spec: String,
    // the device the app runs on: no Remove
    val thisDevice: Boolean,
    // connected now, so it takes a slot
    val connected: Boolean,
    // of its last sign-in; 0 when unknown
    val lastSeenMillis: Long,
)

/**
 * A sign-in the network's client limit refused, with what runs it again: the
 * sign-in's api and network token, and whether the sign-in created the
 * network (MainApplication.offerSignInClientLimit).
 */
data class SignInClientLimit(
    val api: Api,
    val networkJwt: String,
    val newNetwork: Boolean,
    // UR Pro lifts the limit: the refusal's prompt was upgrade
    val offersUpgrade: Boolean,
    // numbers the offers, so a retry answers only its own
    val sequence: Long,
)

/** One read of the controller. */
data class ManageDevicesUi(
    // this device first, then the connected ones, then the latest seen
    val devices: List<ManagedDeviceUi> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    // the client a removal is in flight for
    val removingClientId: String? = null,
    // a Sdk.ManageDevicesError constant; empty after a success
    val errorId: String = "",
    // the removals that freed a slot and added this device again
    val addRetryCount: Long = 0,
    // the network's Pro features in use, from the same proxies: a
    // Sdk.ProFeaturesPrompt constant, empty for none
    val proFeaturesPrompt: String = "",
) {
    val signInRequired: Boolean get() = errorId == Sdk.ManageDevicesErrorSignInRequired
    val loadFailed: Boolean get() = errorId == Sdk.ManageDevicesErrorLoadFailed
    val removeFailed: Boolean get() = errorId == Sdk.ManageDevicesErrorRemoveFailed
}

/**
 * The device list ("Manage devices"): the network's devices and hosted
 * proxies, each removable, so a user at the client limit frees a slot for
 * this device. Opened from the client limit notice, from a sign-in the limit
 * refused, and from Settings.
 *
 * The sdk's ManageDevicesViewController decides everything: what the list
 * holds and its order, the network credential every request carries (never a
 * client token), adding this device again after a removal (it ends the
 * device's client limit hold, or calls the sign-in's retry), and the network's
 * Pro features in use that the list shows, from the proxies it reads. This
 * store only mirrors the controller's snapshots into compose state on the main
 * thread, forwards the screen's intents, holds the confirmation a removal
 * waits for, and says when the network's proxies may have changed. Main
 * thread; the controller's listener posts to it.
 */
class ManageDevicesStore private constructor(
    private val open: () -> ManageDevicesViewController?,
    private val release: (ManageDevicesViewController) -> Unit,
    // the network's proxies may have changed: the list removed a hosted
    // proxy, or closed. Main thread.
    private val proxiesMayHaveChanged: () -> Unit,
) {
    var ui by mutableStateOf(ManageDevicesUi())
        private set
    // the removal waiting for the user's answer
    var confirmation by mutableStateOf<ManagedDeviceUi?>(null)
        private set
    // a removal just added this device again; clears after a few seconds
    var connecting by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var controller: ManageDevicesViewController? = null
    private var listenerSub: Sub? = null
    private var closed = false
    private val clearConnecting = Runnable { connecting = false }

    /** Opens, subscribes to and starts the controller the first time; a later call reloads. */
    fun start() {
        if (closed) return
        controller?.let {
            it.refresh()
            return
        }
        val opened = runCatching { open() }
            .onFailure { Log.i(TAG, "open failed: ${it.message}") }
            .getOrNull() ?: return
        controller = opened
        listenerSub = opened.addManageDevicesListener(ManageDevicesListener {
            mainHandler.post { update() }
        })
        opened.start()
        update()
    }

    fun refresh() {
        if (closed) return
        controller?.refresh()
    }

    /** A row's Remove: asks first. Never this device, and not while another removal runs. */
    fun requestRemove(device: ManagedDeviceUi) {
        if (closed || device.thisDevice || ui.removingClientId != null) return
        confirmation = device
    }

    /** The user confirmed the removal the dialog presented. */
    fun confirmRemove(device: ManagedDeviceUi) {
        confirmation = null
        val current = controller ?: return
        if (closed) return
        val clientId = try {
            Sdk.parseId(device.clientId)
        } catch (e: Exception) {
            Log.w(TAG, "not a client id", e)
            return
        }
        current.removeDevice(clientId)
        update()
    }

    fun cancelRemove() {
        confirmation = null
    }

    /**
     * Drops the listener, then closes the controller. Final. Whatever the
     * list changed, the network's proxies are read again beside it.
     */
    fun close() {
        if (closed) return
        closed = true
        listenerSub?.close()
        listenerSub = null
        controller?.let { runCatching { release(it) } }
        controller = null
        confirmation = null
        mainHandler.removeCallbacks(clearConnecting)
        proxiesMayHaveChanged()
    }

    /**
     * Reads the controller's newest snapshot rather than the one a listener
     * was handed, so a late delivery never replaces a newer one.
     */
    private fun update() {
        if (closed) return
        val current = controller ?: return
        val next = manageDevicesUiFromSdk(current.snapshot)
        if (next.addRetryCount > ui.addRetryCount) {
            connecting = true
            mainHandler.removeCallbacks(clearConnecting)
            mainHandler.postDelayed(clearConnecting, CONNECTING_MILLIS)
        }
        // a hosted proxy this list removed: the network's Pro features in use
        // may have gone with it. A failed removal leaves it listed.
        val removedProxy = ui.removingClientId?.let { removing ->
            next.removingClientId == null &&
                ui.devices.any { it.clientId == removing && it.proxy } &&
                next.devices.none { it.clientId == removing }
        } == true
        if (next != ui) {
            ui = next
        }
        if (removedProxy) {
            proxiesMayHaveChanged()
        }
    }

    companion object {
        /**
         * Opened from the signed-in device, which owns the controller: after a
         * removal it ends this device's client limit hold.
         * [proxiesMayHaveChanged] runs after a hosted proxy's removal and when
         * the list closes, on the main thread.
         */
        fun forDevice(device: DeviceLocal, proxiesMayHaveChanged: () -> Unit): ManageDevicesStore = ManageDevicesStore(
            open = { device.openManageDevicesViewController() },
            release = { device.closeManageDevicesViewController(it) },
            proxiesMayHaveChanged = proxiesMayHaveChanged,
        )

        /**
         * Opened for a sign-in the client limit refused: there is no device
         * yet, so every request carries the sign-in's network token, and after
         * a removal [retry] runs the sign-in again, on the main thread. Nothing
         * else reads the network's proxies before the sign-in completes.
         */
        fun forSignIn(api: Api, networkJwt: String, retry: () -> Unit): ManageDevicesStore {
            val mainHandler = Handler(Looper.getMainLooper())
            return ManageDevicesStore(
                open = {
                    Sdk.newSignInManageDevicesViewController(
                        api,
                        networkJwt,
                        ManageDevicesSignInRetry { mainHandler.post { retry() } },
                    )
                },
                release = { it.close() },
                proxiesMayHaveChanged = {},
            )
        }
    }
}

/** Copies the gomobile snapshot into the screen's data. */
private fun manageDevicesUiFromSdk(snapshot: ManageDevicesSnapshot?): ManageDevicesUi {
    snapshot ?: return ManageDevicesUi()
    val devices = mutableListOf<ManagedDeviceUi>()
    snapshot.devices?.let { list ->
        for (i in 0 until list.len()) {
            val device = list.get(i) ?: continue
            val clientId = device.clientId?.toString()
            if (clientId.isNullOrEmpty()) continue
            devices.add(
                ManagedDeviceUi(
                    clientId = clientId,
                    proxy = device.kind == Sdk.ManagedDeviceKindProxy,
                    name = device.name.orEmpty(),
                    spec = device.spec.orEmpty(),
                    thisDevice = device.thisDevice,
                    connected = device.connected,
                    lastSeenMillis = device.lastSeenTime,
                )
            )
        }
    }
    return ManageDevicesUi(
        devices = devices,
        loaded = snapshot.loaded,
        loading = snapshot.loading,
        removingClientId = snapshot.removingClientId?.toString(),
        errorId = snapshot.errorId.orEmpty(),
        addRetryCount = snapshot.addRetryCount,
        proFeaturesPrompt = snapshot.proFeatures?.prompt.orEmpty(),
    )
}
