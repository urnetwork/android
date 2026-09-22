package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsSettingsUiTest {

    @Test
    fun defaultStateHasAllFlagsDisabled() {
        val ui = DnsSettingsUi()
        assertFalse(ui.dohEnabled)
        assertFalse(ui.unencryptedDnsEnabled)
        assertFalse(ui.localDnsEnabled)
        assertFalse(ui.localDnsFallbackEnabled)
        assertTrue(ui.remoteDohUrlsIpv4.isEmpty())
        assertTrue(ui.remoteDnsIpv4.isEmpty())
    }

    @Test
    fun dohEnabledWhenRemoteOrLocalDohSet() {
        assertFalse(DnsSettingsUi().dohEnabled)
        assertTrue(DnsSettingsUi(enableRemoteDoh = true).dohEnabled)
        assertTrue(DnsSettingsUi(enableLocalDoh = true).dohEnabled)
        assertTrue(DnsSettingsUi(enableRemoteDoh = true, enableLocalDoh = true).dohEnabled)
    }

    @Test
    fun unencryptedDnsEnabledWhenRemoteOrLocalDnsSet() {
        assertFalse(DnsSettingsUi().unencryptedDnsEnabled)
        assertTrue(DnsSettingsUi(enableRemoteDns = true).unencryptedDnsEnabled)
        assertTrue(DnsSettingsUi(enableLocalDns = true).unencryptedDnsEnabled)
        assertTrue(DnsSettingsUi(enableRemoteDns = true, enableLocalDns = true).unencryptedDnsEnabled)
    }

    @Test
    fun localDnsEnabledWhenLocalDohOrLocalDnsSet() {
        assertFalse(DnsSettingsUi().localDnsEnabled)
        // Remote flags should not trigger localDnsEnabled
        assertFalse(DnsSettingsUi(enableRemoteDoh = true).localDnsEnabled)
        assertFalse(DnsSettingsUi(enableRemoteDns = true).localDnsEnabled)

        assertTrue(DnsSettingsUi(enableLocalDoh = true).localDnsEnabled)
        assertTrue(DnsSettingsUi(enableLocalDns = true).localDnsEnabled)
    }

    @Test
    fun localDnsFallbackEnabledMatchesEnableFallback() {
        assertFalse(DnsSettingsUi(enableFallback = false).localDnsFallbackEnabled)
        assertTrue(DnsSettingsUi(enableFallback = true).localDnsFallbackEnabled)
    }

    @Test
    fun regionalSuggestionGeneratesId() {
        val suggestion = RegionalDnsSuggestionUi(
            countryCode = "us",
            name = "Cloudflare",
            ipv4 = "1.1.1.1",
        )
        assertEquals("us-1.1.1.1", suggestion.id)
    }
}
