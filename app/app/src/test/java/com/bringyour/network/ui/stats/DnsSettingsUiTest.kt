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
        assertFalse(ui.fastDnsOnConnectEnabled)
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
    fun fastDnsOnConnectEnabledMatchesEnableFallback() {
        assertFalse(DnsSettingsUi(enableFallback = false).fastDnsOnConnectEnabled)
        assertTrue(DnsSettingsUi(enableFallback = true).fastDnsOnConnectEnabled)
    }

    @Test
    fun regionalSuggestionGeneratesId() {
        val suggestion = RegionalDnsSuggestionUi(
            countryCode = "us",
            name = "Example resolver",
            ipv4 = "192.0.2.53",
        )
        assertEquals("us-192.0.2.53", suggestion.id)
    }
}
