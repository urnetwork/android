package com.bringyour.network.ui.stats

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DNS and transport editors open only on the settings in force and
 * update by compare and set (owner report 2026-10-16), read from the source,
 * since the screens and their view models need a device: no blank or default
 * form while the settings are unknown, the Update passes the settings it was
 * edited from, the view model checks them (settingsEditApplies,
 * SettingsEditorPolicyTest) before it writes, and a refused Update re-seeds.
 */
class SettingsEditorWiringTest {

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/ui/stats/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing $start", 0 <= from)
        val to = source.indexOf(end, from)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun theDnsEditorOpensOnlyOnTheSettingsInForce() {
        val screen = source("DnsSettingsScreen.kt")
        val seed = between(screen, "    LaunchedEffect(dnsSettingsViewModel.settings) {", "\n    }\n")
        assertFalse(seed, seed.contains("DnsSettingsUi()"))
        assertTrue(seed, seed.contains("if (current != null"))
        assertTrue(screen.contains("if (dnsSettingsViewModel.apply(edited, editedFrom)) {"))
    }

    @Test
    fun theDnsUpdateComparesBeforeItWrites() {
        val apply = between(source("DnsSettingsViewModel.kt"), "    fun apply(newSettings: DnsSettingsUi, original: DnsSettingsUi): Boolean {", "\n    }\n")
        val check = apply.indexOf("if (!settingsEditApplies(original, current)) {")
        val write = apply.indexOf("deviceManager.applyDnsResolverSettings(sdkSettings)")
        assertTrue(apply, 0 <= check && check < write)
    }

    @Test
    fun theTransportEditorOpensOnlyOnThePolicyInForce() {
        val screen = source("TransportSettingsScreen.kt")
        val seed = between(screen, "    LaunchedEffect(currentSettings) {", "\n    }\n")
        assertFalse(seed, seed.contains("defaultSettings"))
        assertTrue(seed, seed.contains("currentSettings != null"))
        assertTrue(screen.contains("transportSettingsViewModel.apply(it.sdk, kind, editedFrom)"))
    }

    @Test
    fun theTransportUpdateComparesBeforeItWrites() {
        val apply = between(source("TransportSettingsViewModel.kt"), "    fun apply(sdkSettings: TransportSettings, kind: TransportSettingsKind, original: TransportSettingsUi): Boolean {", "\n    }\n")
        val check = apply.indexOf("if (!settingsEditApplies(original, current)) {")
        val write = apply.indexOf("device.transportSettings = sdkSettings")
        assertTrue(apply, 0 <= check && check < write)
    }
}
