package com.bringyour.network.ui.stats

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
