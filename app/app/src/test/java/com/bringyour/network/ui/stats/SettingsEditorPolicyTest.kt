package com.bringyour.network.ui.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DNS and transport editors' Update is a compare and set (owner report
 * 2026-10-16, as the Apple app's editors): it applies only while the settings
 * the editor opened on are still in force, and never from settings that were
 * unknown, so a blank or default form cannot overwrite the real resolver.
 */
class SettingsEditorPolicyTest {

    private val resolver = DnsSettingsUi(enableRemoteDoh = true, remoteDohUrlsIpv4 = listOf("https://doh.example/dns-query"))

    @Test
    fun anEditOfTheSettingsInForceApplies() {
        assertTrue(settingsEditApplies(resolver, resolver.copy()))
    }

    @Test
    fun anEditOfSettingsThatChangedMeanwhileDoesNotApply() {
        // the connect sheet's Fast DNS toggle, or a new device, changed them
        assertFalse(settingsEditApplies(resolver, resolver.copy(enableFallback = true)))
    }

    @Test
    fun unknownSettingsNeverApply() {
        // a form opened while the settings were unknown, or none known now
        assertFalse(settingsEditApplies(null, resolver))
        assertFalse(settingsEditApplies(resolver, null))
        assertFalse(settingsEditApplies<DnsSettingsUi>(null, null))
    }
}
