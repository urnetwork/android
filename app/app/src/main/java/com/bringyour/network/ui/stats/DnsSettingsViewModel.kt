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
import com.bringyour.sdk.DnsPreset
import com.bringyour.sdk.DnsResolverSettings
import com.bringyour.sdk.DnsResolverStatus
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject



/**
 * Publishes the device dns resolver settings, what the sdk shows for them
 * (connect ADBLOCK3RD F1, F2: the picks, the summary with its pill, and the
 * status of the selected preset with this connection's blocked count), and
 * applies edits.
 *
 * Every save starts from an sdk helper on the settings in force, never from a
 * fresh DnsResolverSettings, so the selection (PresetId, PresetPersonalId),
 * the mask and any field this app does not model survive it: a pick goes
 * through DnsResolverSettingsWithPreset, Fast DNS on connect through
 * DnsResolverSettingsWithFallback, and the custom editor through
 * DnsResolverSettingsAsCustom.
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

    /**
     * the settings in force as Custom's editor edits them: the lists and
     * toggles their selection resolves with (DnsResolverSettingsAsCustom)
     */
    var settings by mutableStateOf<DnsSettingsUi?>(null)
        private set

    /**
     * whether the current device has reported its resolver settings since it
     * was set. False while the first report is pending, so the connect sheet
     * shows a placeholder rather than "unavailable"; a device that reports no
     * settings (null) still counts as reported. True with no device at all,
     * since nothing will ever report.
     */
    var reported by mutableStateOf(false)
        private set

    var regionalServers by mutableStateOf<List<RegionalDnsSuggestionUi>>(listOf())
        private set

    /**
     * the picker's rows in the sdk's order, for the connected country, whose
     * regional recommendation is the last pick when it has one
     */
    var picks by mutableStateOf<List<DnsPickUi>>(listOf())
        private set

    /** what the sdk shows for the settings in force; null while they are unknown */
    var selection by mutableStateOf<DnsSelectionUi?>(null)
        private set

    /** the selected preset, for its disclosure and links; null unless a preset is selected */
    var selectedPreset by mutableStateOf<DnsPresetUi?>(null)
        private set

    /**
     * the status line, the verification and the blocked count of the
     * selected preset; empty for every other selection
     */
    var status by mutableStateOf(DnsStatusUi())
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
     * the selected preset's name, which the Fast DNS note says doesn't filter
     * while connecting and strict Private DNS overrides (dnsPresetName); null
     * unless a preset is selected
     */
    val selectedPresetName: DnsPresetNameUi?
        get() = selection?.takeIf { it.kind == DNS_PICK_KIND_PRESET }?.name?.takeIf { it.key.isNotEmpty() }

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

    private fun toPresetUi(preset: DnsPreset): DnsPresetUi {
        return DnsPresetUi(
            presetId = preset.presetId.orEmpty(),
            providerName = preset.providerName.orEmpty(),
            name = DnsPresetNameUi(key = preset.nameKey.orEmpty(), provider = preset.nameProvider.orEmpty()),
            jurisdiction = preset.jurisdiction.orEmpty(),
            privacyUrl = preset.privacyUrl.orEmpty(),
            helpUrl = preset.helpUrl.orEmpty(),
            copyKey = preset.copyKey.orEmpty(),
            personalIdRequired = preset.personalIdRequired,
            personalIdExample = preset.personalIdExample.orEmpty(),
            personalIdHelpUrl = preset.personalIdHelpUrl.orEmpty(),
            personalIdWhereKey = preset.personalIdWhereKey.orEmpty(),
            personalIdFormatKey = preset.personalIdFormatKey.orEmpty(),
            quotaNoteKey = preset.quotaNoteKey.orEmpty(),
        )
    }

    init {
        refresh(deviceManager.device)

        processLifecycle.addObserver(this)
        subscriptionOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                refresh(device)
                reported = device == null
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
        // the settings, the resolver's status and the exit's country, which
        // decides the regional pick and the pill, each change what shows
        val changed: () -> Unit = {
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    update(device)
                }
            }
        }
        subs.add(device.addDnsResolverSettingsChangeListener { changed() })
        subs.add(device.addDnsResolverStatusChangeListener { changed() })
        subs.add(device.addConnectLocationChangeListener { changed() })
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
        reported = true
        refresh(device)
    }

    // the device's settings, else the persisted ones, which also stand in
    // while the device's dns upgrade mux is disabled
    private fun currentSdkSettings(): DnsResolverSettings? {
        return deviceManager.device?.dnsResolverSettings
            ?: deviceManager.asyncLocalState?.localState?.dnsResolverSettings
    }

    /**
     * Reads the settings in force and what the sdk shows for them: Custom's
     * editor's lists, the summary, the selected preset, the picks for the
     * connected country, and the status with this connection's blocked count.
     */
    private fun refresh(device: DeviceLocal?) {
        val sdkSettings = device?.dnsResolverSettings
            ?: deviceManager.asyncLocalState?.localState?.dnsResolverSettings
        val countryCode = connectedCountryCode ?: ""

        settings = sdkSettings?.let { toUi(Sdk.dnsResolverSettingsAsCustom(it) ?: it) }

        val summary = sdkSettings?.let { Sdk.describeDnsResolverSettings(it, countryCode) }
        val nextSelection = summary?.let {
            DnsSelectionUi(
                kind = it.kind.orEmpty(),
                presetId = it.presetId.orEmpty(),
                name = DnsPresetNameUi(key = it.nameKey.orEmpty(), provider = it.nameProvider.orEmpty()),
                providerName = it.providerName.orEmpty(),
                summaryKey = it.summaryKey.orEmpty(),
                dropped = it.dropped,
                droppedName = DnsPresetNameUi(key = it.droppedNameKey.orEmpty(), provider = it.droppedNameProvider.orEmpty()),
                pillKey = it.pillKey.orEmpty(),
                pillCountryCode = it.pillCountryCode.orEmpty(),
            )
        }
        selection = nextSelection
        selectedPreset = nextSelection?.takeIf { it.kind == DNS_PICK_KIND_PRESET }?.let { selected ->
            Sdk.getDnsPreset(selected.presetId)?.let { toPresetUi(it) }
        }

        val nextPicks = mutableListOf<DnsPickUi>()
        val list = Sdk.getDnsPicks(countryCode)
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val pick = list.get(i) ?: continue
                nextPicks.add(
                    DnsPickUi(
                        kind = pick.kind.orEmpty(),
                        presetId = pick.presetId.orEmpty(),
                        groupKey = pick.groupKey.orEmpty(),
                        labelKey = pick.labelKey.orEmpty(),
                        copyKey = pick.copyKey.orEmpty(),
                        countryCode = pick.countryCode.orEmpty(),
                        unencrypted = pick.unencrypted,
                        preset = pick.preset?.let { toPresetUi(it) },
                    )
                )
            }
        }
        picks = nextPicks

        status = statusUi(sdkSettings, device?.dnsResolverStatus, nextSelection?.presetId)
    }

    // what the sdk shows for the settings in force and the device's resolver
    // status. The blocked count shows only while the status describes the
    // preset in force (dnsShownBlockedCount): never another preset's count
    // after a change, and nothing without a device
    private fun statusUi(
        sdkSettings: DnsResolverSettings?,
        resolverStatus: DnsResolverStatus?,
        selectionPresetId: String?,
    ): DnsStatusUi {
        val summary = sdkSettings?.let { Sdk.describeDnsResolverStatus(it, resolverStatus) }
            ?: return DnsStatusUi()
        return DnsStatusUi(
            statusKey = summary.statusKey.orEmpty(),
            name = DnsPresetNameUi(key = summary.nameKey.orEmpty(), provider = summary.nameProvider.orEmpty()),
            providerName = summary.providerName.orEmpty(),
            statusDetailKey = summary.statusDetailKey.orEmpty(),
            verificationKey = summary.verificationKey.orEmpty(),
            verificationUrl = summary.verificationUrl.orEmpty(),
            verificationLinkKey = summary.verificationLinkKey.orEmpty(),
            showUseDefault = summary.showUseDefault,
            showChangeLocation = summary.showChangeLocation,
            countsBlocked = summary.showBlockedCount,
            blockedCount = dnsShownBlockedCount(
                countsBlocked = summary.showBlockedCount,
                blockedCount = summary.blockedCount,
                statusPresetId = resolverStatus?.presetId,
                selectionPresetId = selectionPresetId,
            ),
        )
    }

    /**
     * Reads the status again, with this connection's blocked count, for the
     * screen that shows the count: the count changes without a status
     * callback. The status names the preset it counts for, so a count of
     * another preset never shows under the selected one.
     */
    fun refreshBlockedCount() {
        val device = deviceManager.device
        status = statusUi(currentSdkSettings(), device?.dnsResolverStatus, selection?.presetId)
    }

    /**
     * The name of a country the sdk names by its code: the connected
     * location's name for its country, else the code.
     */
    fun countryName(countryCode: String): String {
        val name = connectedCountryName
        return if (name != null && countryCode == connectedCountryCode) name else countryCode.uppercase()
    }

    /**
     * Selects a pick on the settings in force (DnsResolverSettingsWithPreset):
     * URnetwork's default, a preset, a personal preset with its id, or the
     * regional recommendation, keeping Fast DNS on connect and the mask. False
     * when the sdk refuses the pick or nothing took the settings.
     */
    fun selectPick(presetId: String, personalId: String = ""): Boolean {
        val sdkSettings = Sdk.dnsResolverSettingsWithPreset(currentSdkSettings(), presetId, personalId)
            ?: return false
        return applySettings(sdkSettings)
    }

    /**
     * The sdk's error id of what was typed or pasted as a personal preset's
     * id (ValidateDnsPresetPersonalId), empty when it is valid.
     */
    fun personalIdErrorId(presetId: String, input: String): String {
        return Sdk.validateDnsPresetPersonalId(presetId, input).orEmpty()
    }

    /**
     * Selects a personal preset with the id the sdk reads out of what was
     * typed or pasted: a bare id, the provider's DoH url or its Private DNS
     * hostname (NormalizeDnsPresetPersonalId).
     */
    fun selectPersonalPreset(presetId: String, input: String): Boolean {
        return selectPick(presetId, Sdk.normalizeDnsPresetPersonalId(presetId, input) ?: input)
    }

    /**
     * The id saved with a personal preset, for its field to start from when
     * it opens (GetDnsPresetPersonalIdForEdit, ADBLOCK3RD decision 21): the
     * id in force while the settings select that preset, else empty. The
     * field is the one place it shows.
     */
    fun personalIdOf(presetId: String): String {
        return Sdk.getDnsPresetPersonalIdForEdit(currentSdkSettings(), presetId).orEmpty()
    }

    /**
     * Turns Fast DNS on connect on or off on the settings in force, keeping
     * the selection (DnsResolverSettingsWithFallback). Nothing applies while
     * the settings are unknown. True when it applied.
     */
    fun setFastDnsOnConnect(enabled: Boolean): Boolean {
        val current = currentSdkSettings() ?: return false
        val sdkSettings = Sdk.dnsResolverSettingsWithFallback(current, enabled) ?: return false
        return applySettings(sdkSettings)
    }

    /**
     * Applies Custom's editor's `newSettings`, edited from `original`, only
     * while `original` is still the settings in force ([settingsEditApplies]);
     * otherwise publishes the settings in force, for the editor to re-seed
     * from, and applies nothing. True when it applied.
     */
    fun apply(newSettings: DnsSettingsUi, original: DnsSettingsUi): Boolean {
        // the settings in force as custom settings: the lists their selection
        // resolves with, the selection cleared, and every other field kept,
        // the mask and Fast DNS on connect included. Never a fresh struct,
        // which drops every field the editor does not model
        val sdkSettings = currentSdkSettings()?.let { Sdk.dnsResolverSettingsAsCustom(it) }
        val current = sdkSettings?.let { toUi(it) }
        if (sdkSettings == null || !settingsEditApplies(original, current)) {
            settings = current
            return false
        }
        sdkSettings.enableRemoteDoh = newSettings.enableRemoteDoh
        sdkSettings.enableLocalDoh = newSettings.enableLocalDoh
        sdkSettings.enableRemoteDns = newSettings.enableRemoteDns
        sdkSettings.enableLocalDns = newSettings.enableLocalDns
        sdkSettings.remoteDohUrlsIpv4 = listToSdkStringList(newSettings.remoteDohUrlsIpv4)
        sdkSettings.remoteDohUrlsIpv6 = listToSdkStringList(newSettings.remoteDohUrlsIpv6)
        sdkSettings.localDohUrlsIpv4 = listToSdkStringList(newSettings.localDohUrlsIpv4)
        sdkSettings.localDohUrlsIpv6 = listToSdkStringList(newSettings.localDohUrlsIpv6)
        sdkSettings.remoteDnsIpv4 = listToSdkStringList(newSettings.remoteDnsIpv4)
        sdkSettings.remoteDnsIpv6 = listToSdkStringList(newSettings.remoteDnsIpv6)
        sdkSettings.localDnsIpv4 = listToSdkStringList(newSettings.localDnsIpv4)
        sdkSettings.localDnsIpv6 = listToSdkStringList(newSettings.localDnsIpv6)
        // Fast DNS on connect is its own toggle (setFastDnsOnConnect), so it
        // stays as the settings in force have it
        return applySettings(sdkSettings)
    }

    // one write, to the device or to the persisted settings; see
    // DeviceManager.applyDnsResolverSettings. False when nothing took the
    // settings, which leaves what is in force showing
    private fun applySettings(sdkSettings: DnsResolverSettings): Boolean {
        if (!deviceManager.applyDnsResolverSettings(sdkSettings)) {
            return false
        }
        refresh(deviceManager.device)
        return true
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        subscriptionOwner.close()
        super.onCleared()
    }
}
