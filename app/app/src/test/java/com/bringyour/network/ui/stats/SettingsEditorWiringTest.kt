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
        val check = apply.indexOf("if (sdkSettings == null || !settingsEditApplies(original, current)) {")
        val write = apply.indexOf("return applySettings(sdkSettings)")
        assertTrue(apply, 0 <= check && check < write)
    }

    /**
     * The custom editor saved a fresh DnsResolverSettings built from its draft,
     * so every field the draft does not model was lost on Update: the mask,
     * and with the third-party presets (connect ADBLOCK3RD 3.8, E3) the
     * selection and the personal id. The save starts from the settings in
     * force as custom settings, and so do the editor's draft and its compare.
     */
    @Test
    fun theDnsUpdateStartsFromTheSettingsInForce() {
        val viewModel = source("DnsSettingsViewModel.kt")
        val apply = between(viewModel, "    fun apply(newSettings: DnsSettingsUi, original: DnsSettingsUi): Boolean {", "\n    }\n")
        assertTrue(apply, apply.contains("currentSdkSettings()?.let { Sdk.dnsResolverSettingsAsCustom(it) }"))
        assertTrue(apply, apply.contains("val current = sdkSettings?.let { toUi(it) }"))
        assertTrue(viewModel, viewModel.contains("settings = sdkSettings?.let { toUi(Sdk.dnsResolverSettingsAsCustom(it) ?: it) }"))
        // no save anywhere builds the struct from scratch
        assertFalse(viewModel, viewModel.contains("DnsResolverSettings()"))
    }

    /** A pick keeps Fast DNS on connect and the mask, and the toggle keeps the selection. */
    @Test
    fun everyDnsSaveStartsFromAnSdkHelper() {
        val viewModel = source("DnsSettingsViewModel.kt")
        val selectPick = between(viewModel, "    fun selectPick(presetId: String, personalId: String = \"\"): Boolean {", "\n    }\n")
        assertTrue(selectPick, selectPick.contains("Sdk.dnsResolverSettingsWithPreset(currentSdkSettings(), presetId, personalId)"))
        val fastDns = between(viewModel, "    fun setFastDnsOnConnect(enabled: Boolean): Boolean {", "\n    }\n")
        assertTrue(fastDns, fastDns.contains("Sdk.dnsResolverSettingsWithFallback(current, enabled)"))
        // nothing applies while the settings are unknown
        assertTrue(fastDns, fastDns.contains("val current = currentSdkSettings() ?: return false"))
    }

    /**
     * Update reported that it applied when nothing took the settings (connect
     * ADBLOCK3RD 3.8), so the editor took its draft for the settings in force.
     * It reports the write's own answer.
     */
    @Test
    fun theDnsUpdateReportsWhetherItApplied() {
        val viewModel = source("DnsSettingsViewModel.kt")
        val apply = between(viewModel, "    fun apply(newSettings: DnsSettingsUi, original: DnsSettingsUi): Boolean {", "\n    }\n")
        assertFalse(apply, apply.contains("return true"))
        val write = between(viewModel, "    private fun applySettings(sdkSettings: DnsResolverSettings): Boolean {", "\n    }\n")
        val refused = write.indexOf("if (!deviceManager.applyDnsResolverSettings(sdkSettings)) {")
        val applied = write.indexOf("return true")
        assertTrue(write, 0 <= refused && refused < applied)
        assertTrue(write, write.contains("return false"))
    }

    /** The screen's picks, Fast DNS and Update each go through the view model's sdk saves. */
    @Test
    fun theDnsScreenSavesThroughTheViewModel() {
        val screen = source("DnsSettingsScreen.kt")
        assertTrue(screen.contains("dnsSettingsViewModel.selectPick(presetId)"))
        assertTrue(screen.contains("dnsSettingsViewModel.selectPersonalPreset(preset.presetId, personalInput.text)"))
        assertTrue(screen.contains("dnsSettingsViewModel.setFastDnsOnConnect(enabled)"))
        assertFalse(screen, screen.contains("DnsResolverSettings("))
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
