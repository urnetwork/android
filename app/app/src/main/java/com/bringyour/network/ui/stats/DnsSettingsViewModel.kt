package com.bringyour.network.ui.stats

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
import com.bringyour.network.utils.listToSdkStringList
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.DnsResolverSettings
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editable snapshot of the device dns resolver settings
 */
data class DnsSettingsUi(
    val enableRemoteDoh: Boolean = false,
    val enableLocalDoh: Boolean = false,
    val enableRemoteDns: Boolean = false,
    val enableLocalDns: Boolean = false,
    val enableFallback: Boolean = false,
    val remoteDohUrlsIpv4: List<String> = listOf(),
    val remoteDohUrlsIpv6: List<String> = listOf(),
    val localDohUrlsIpv4: List<String> = listOf(),
    val localDohUrlsIpv6: List<String> = listOf(),
    val remoteDnsIpv4: List<String> = listOf(),
    val remoteDnsIpv6: List<String> = listOf(),
    val localDnsIpv4: List<String> = listOf(),
    val localDnsIpv6: List<String> = listOf(),
) {
    /**
     * summary states shown in the connect sheet
     */
    val dohEnabled: Boolean
        get() = enableRemoteDoh || enableLocalDoh
    val unencryptedDnsEnabled: Boolean
        get() = enableRemoteDns || enableLocalDns
    val localDnsEnabled: Boolean
        get() = enableLocalDoh || enableLocalDns
    val localDnsFallbackEnabled: Boolean
        get() = enableFallback
}

/**
 * A well known regional dns server suggestion
 */
data class RegionalDnsSuggestionUi(
    val countryCode: String,
    val name: String,
    val ipv4: String,
) {
    val id: String
        get() = "$countryCode-$ipv4"
}

/**
 * Publishes the device dns resolver settings and applies edits
 */
@HiltViewModel
class DnsSettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val subs = mutableListOf<Sub>()
    private var subscribedDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val subscriptionOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, Unit>(
            open = { openDeviceSubscription(it) },
            close = { device, _ -> closeDeviceSubscription(device) },
        )

    var settings by mutableStateOf<DnsSettingsUi?>(null)
        private set

    var regionalServers by mutableStateOf<List<RegionalDnsSuggestionUi>>(listOf())
        private set

    /**
     * the country code of the connected location, used to mark suggestions
     */
    val connectedCountryCode: String?
        get() = deviceManager.device?.connectLocation?.countryCode?.lowercase()?.ifEmpty { null }

    /**
     * the connected country name, for the recommendation message
     */
    val connectedCountryName: String?
        get() = deviceManager.device?.connectLocation?.country?.ifEmpty { null }

    /**
     * the recommended settings when the connected country has a recommendation
     * (the strong-privacy defaults are known not to work there), or null
     */
    val recommendedSettings: DnsSettingsUi?
        get() {
            val code = connectedCountryCode ?: return null
            return Sdk.getRecommendedDnsResolverSettings(code)?.let { toUi(it) }
        }

    /**
     * the default, most secure settings (encrypted DNS over HTTPS)
     */
    val defaultSettings: DnsSettingsUi?
        get() = Sdk.getDefaultDnsResolverSettings()?.let { toUi(it) }

    private fun toUi(settings: DnsResolverSettings): DnsSettingsUi {
        return DnsSettingsUi(
            enableRemoteDoh = settings.enableRemoteDoh,
            enableLocalDoh = settings.enableLocalDoh,
            enableRemoteDns = settings.enableRemoteDns,
            enableLocalDns = settings.enableLocalDns,
            enableFallback = settings.enableFallback,
            remoteDohUrlsIpv4 = sdkStringListToList(settings.remoteDohUrlsIpv4),
            remoteDohUrlsIpv6 = sdkStringListToList(settings.remoteDohUrlsIpv6),
            localDohUrlsIpv4 = sdkStringListToList(settings.localDohUrlsIpv4),
            localDohUrlsIpv6 = sdkStringListToList(settings.localDohUrlsIpv6),
            remoteDnsIpv4 = sdkStringListToList(settings.remoteDnsIpv4),
            remoteDnsIpv6 = sdkStringListToList(settings.remoteDnsIpv6),
            localDnsIpv4 = sdkStringListToList(settings.localDnsIpv4),
            localDnsIpv6 = sdkStringListToList(settings.localDnsIpv6),
        )
    }

    init {
        processLifecycle.addObserver(this)
        subscriptionOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                settings = null
                subscriptionOwner.setDevice(device)
            }
        }

        val servers = mutableListOf<RegionalDnsSuggestionUi>()
        val list = Sdk.getRegionalDnsServers()
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val server = list.get(i) ?: continue
                servers.add(
                    RegionalDnsSuggestionUi(
                        countryCode = server.countryCode,
                        name = server.name,
                        ipv4 = server.ipv4,
                    )
                )
            }
        }
        regionalServers = servers
    }

    override fun onStart(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(false)
    }

    private fun openDeviceSubscription(device: DeviceLocal) {
        subscribedDevice = device
        subs.add(device.addDnsResolverSettingsChangeListener {
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    update(device)
                }
            }
        })
        update(device)
    }

    private fun closeDeviceSubscription(device: DeviceLocal) {
        subs.forEach { it.close() }
        subs.clear()
        if (subscribedDevice === device) {
            subscribedDevice = null
        }
    }

    private fun update(device: DeviceLocal) {
        val sdkSettings = device.dnsResolverSettings
        settings = if (sdkSettings != null) {
            DnsSettingsUi(
                enableRemoteDoh = sdkSettings.enableRemoteDoh,
                enableLocalDoh = sdkSettings.enableLocalDoh,
                enableRemoteDns = sdkSettings.enableRemoteDns,
                enableLocalDns = sdkSettings.enableLocalDns,
                enableFallback = sdkSettings.enableFallback,
                remoteDohUrlsIpv4 = sdkStringListToList(sdkSettings.remoteDohUrlsIpv4),
                remoteDohUrlsIpv6 = sdkStringListToList(sdkSettings.remoteDohUrlsIpv6),
                localDohUrlsIpv4 = sdkStringListToList(sdkSettings.localDohUrlsIpv4),
                localDohUrlsIpv6 = sdkStringListToList(sdkSettings.localDohUrlsIpv6),
                remoteDnsIpv4 = sdkStringListToList(sdkSettings.remoteDnsIpv4),
                remoteDnsIpv6 = sdkStringListToList(sdkSettings.remoteDnsIpv6),
                localDnsIpv4 = sdkStringListToList(sdkSettings.localDnsIpv4),
                localDnsIpv6 = sdkStringListToList(sdkSettings.localDnsIpv6),
            )
        } else {
            null
        }
    }

    fun apply(newSettings: DnsSettingsUi) {
        val device = deviceManager.device ?: return
        val sdkSettings = DnsResolverSettings()
        sdkSettings.enableRemoteDoh = newSettings.enableRemoteDoh
        sdkSettings.enableLocalDoh = newSettings.enableLocalDoh
        sdkSettings.enableRemoteDns = newSettings.enableRemoteDns
        sdkSettings.enableLocalDns = newSettings.enableLocalDns
        sdkSettings.enableFallback = newSettings.enableFallback
        sdkSettings.remoteDohUrlsIpv4 = listToSdkStringList(newSettings.remoteDohUrlsIpv4)
        sdkSettings.remoteDohUrlsIpv6 = listToSdkStringList(newSettings.remoteDohUrlsIpv6)
        sdkSettings.localDohUrlsIpv4 = listToSdkStringList(newSettings.localDohUrlsIpv4)
        sdkSettings.localDohUrlsIpv6 = listToSdkStringList(newSettings.localDohUrlsIpv6)
        sdkSettings.remoteDnsIpv4 = listToSdkStringList(newSettings.remoteDnsIpv4)
        sdkSettings.remoteDnsIpv6 = listToSdkStringList(newSettings.remoteDnsIpv6)
        sdkSettings.localDnsIpv4 = listToSdkStringList(newSettings.localDnsIpv4)
        sdkSettings.localDnsIpv6 = listToSdkStringList(newSettings.localDnsIpv6)
        device.dnsResolverSettings = sdkSettings
        update(device)
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        subscriptionOwner.close()
        super.onCleared()
    }
}
