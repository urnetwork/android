package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.utils.listToSdkStringList
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.ExtenderSettings
import com.bringyour.sdk.ExtenderViewController
import com.bringyour.sdk.NetExtender
import com.bringyour.sdk.NetworkSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The extender section of the account screen (EXTENDER.md K6, K7): the three
 * edited settings of the space, the legacy private extender behind the
 * advanced expander, and the share and import payloads.
 *
 * Everything but the private extender goes through the sdk's
 * `ExtenderViewController`, which is one implementation of the payload and
 * the settings write for every app. The private extender is a network space
 * value with no controller of its own, so it takes the space manager's update
 * path, as the network server selector does.
 */
@HiltViewModel
class ExtendersViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
) : ViewModel(), DefaultLifecycleObserver {

    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, ExtenderViewController>(
            open = { openExtenderViewController(it) },
            close = { device, vc -> closeExtenderViewController(device, vc) },
        )

    /** The effective settings, or null while there is no device to read them from. */
    var settings by mutableStateOf<ExtenderSettingsUi?>(null)
        private set

    var privateExtender by mutableStateOf(ExtenderPrivateUi())
        private set

    /** Whether the form can be edited at all: signed out there is no device. */
    var editable by mutableStateOf(false)
        private set

    private var removeDeviceChangeListener: (() -> Unit)? = null

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        // the device is (re)created asynchronously (login, network change) and
        // this view model can be created first, so wire per device every time
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                controllerOwner.setDevice(device)
                loadPrivateExtender()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
        loadPrivateExtender()
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun openExtenderViewController(device: DeviceLocal): ExtenderViewController {
        val vc = device.openExtenderViewController()
        settings = settingsUi(vc.settings)
        editable = true
        return vc
    }

    private fun closeExtenderViewController(device: DeviceLocal, vc: ExtenderViewController) {
        editable = false
        device.closeViewController(vc)
    }

    /**
     * Saves the three settings and republishes what they resolved to. An empty
     * field means the derived default, so clearing a box is how a user goes
     * back to it; the sdk restarts the space's network client in place.
     */
    fun saveSettings(dnsName: String, gossipUrl: String, hosts: List<String>): Boolean {
        val vc = controllerOwner.controller ?: return false
        settings = settingsUi(vc.setSettings(dnsName.trim(), gossipUrl.trim(), listToSdkStringList(hosts)))
        return true
    }

    /**
     * Saves the legacy private extender, which overrides discovery outright.
     * A blank address clears it and hands discovery back.
     */
    fun savePrivateExtender(ip: String, secret: String): Boolean {
        val space = networkSpace() ?: return false
        val manager = networkSpaceManagerProvider.getNetworkSpaceManager() ?: return false
        val key = space.key ?: return false
        val trimmedIp = ip.trim()
        val trimmedSecret = secret.trim()
        try {
            manager.updateNetworkSpace(key) { values ->
                // the callback mutates a copy of the space's current values,
                // so nothing else -- the extender settings above included --
                // has to be restated here.
                //
                // a cleared extender is written as an empty address rather
                // than a missing block: the sdk reads an unparsable address as
                // no custom extender, which is exactly "cleared", and an
                // always-present block keeps this off the binding's null path.
                values.netExtender = NetExtender().apply {
                    this.ip = trimmedIp
                    this.secret = trimmedSecret
                }
            }
        } catch (e: Exception) {
            return false
        }
        loadPrivateExtender()
        return true
    }

    /** The share payload of this space (K7). */
    fun buildShare(includeSettings: Boolean): ExtenderShareUi {
        val vc = controllerOwner.controller ?: return ExtenderShareUi()
        val result = vc.buildShare(includeSettings) ?: return ExtenderShareUi()
        return ExtenderShareUi(
            text = result.text,
            count = result.count.toInt(),
            includesSettings = result.includesSettings,
        )
    }

    /** What a scanned, chosen or pasted payload turns out to be, applying nothing (K7). */
    fun decodeShare(text: String): ExtenderDecodeUi {
        val vc = controllerOwner.controller
            ?: return ExtenderDecodeUi(errorKey = IMPORT_ERROR_INVALID)
        val result = vc.decodeShare(text)
            ?: return ExtenderDecodeUi(errorKey = IMPORT_ERROR_INVALID)
        return ExtenderDecodeUi(
            ok = result.ok,
            errorKey = result.error,
            networkHost = result.networkHost,
            foreignHost = result.foreignHost,
            count = result.count.toInt(),
            hasSettings = result.hasSettings,
            settingsHost = result.settingsHost,
        )
    }

    /** Applies a payload (K7). */
    fun importShare(text: String, useSettings: Boolean): ExtenderImportUi {
        val vc = controllerOwner.controller
            ?: return ExtenderImportUi(errorKey = IMPORT_ERROR_INVALID)
        val result = vc.importShare(text, useSettings)
            ?: return ExtenderImportUi(errorKey = IMPORT_ERROR_INVALID)
        if (result.ok) {
            // an import with settings replaces the dns name and the gossip
            // url, so the form must not keep showing the old ones
            settings = settingsUi(vc.settings)
        }
        return ExtenderImportUi(
            ok = result.ok,
            errorKey = result.error,
            importedCount = result.importedCount.toInt(),
        )
    }

    private fun networkSpace(): NetworkSpace? =
        deviceManager.device?.networkSpace ?: networkSpaceManagerProvider.getNetworkSpace()

    private fun loadPrivateExtender() {
        val netExtender = networkSpace()?.netExtender
        privateExtender = ExtenderPrivateUi(
            ip = netExtender?.ip ?: "",
            secret = netExtender?.secret ?: "",
        )
    }

    private fun settingsUi(settings: ExtenderSettings?): ExtenderSettingsUi? {
        settings ?: return null
        return ExtenderSettingsUi(
            dnsName = settings.dnsName,
            dnsNameDefault = settings.dnsNameDefault,
            gossipUrl = settings.gossipUrl,
            gossipUrlDefault = settings.gossipUrlDefault,
            hosts = sdkStringListToList(settings.hosts),
            networkHost = settings.networkHost,
        )
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }

    companion object {
        // mirror of Sdk.ExtenderImportErrorInvalid, as a literal so this stays
        // off the native class's initializer
        const val IMPORT_ERROR_INVALID = "import_extenders_invalid"
    }
}
