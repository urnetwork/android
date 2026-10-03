package com.bringyour.network.ui.stats

import com.bringyour.network.R

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
    val fastDnsOnConnectEnabled: Boolean
        get() = FastDnsOnConnectToggle.isOn(this)
}

/**
 * The opt-in "Fast DNS on connect" toggle, backed by the sdk enableFallback: it races a
 * resolver over the host's local network while the tunnel's dns starts, which can reveal
 * lookups to the local network. Off by default and whenever the device has not reported
 * settings, so dns resolves only through the tunnel unless the user turns it on.
 */
object FastDnsOnConnectToggle {
    val labelRes: Int = R.string.fast_dns_on_connect
    val descriptionRes: Int = R.string.fast_dns_on_connect_description

    fun isOn(settings: DnsSettingsUi?): Boolean = settings?.enableFallback == true

    fun toggled(settings: DnsSettingsUi): DnsSettingsUi =
        settings.copy(enableFallback = !settings.enableFallback)
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
