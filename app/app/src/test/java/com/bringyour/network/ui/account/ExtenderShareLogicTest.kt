package com.bringyour.network.ui.account

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extender account section's decisions (EXTENDER.md K6, K7): what the
 * share screen passes to the sdk, what the import screen does with each kind
 * of decoded payload, and how the settings form reads the defaults.
 */
class ExtenderShareLogicTest {

    private fun decode(
        ok: Boolean = true,
        errorKey: String = "",
        networkHost: String = "bringyour.com",
        foreignHost: Boolean = false,
        count: Int = 6,
        hasSettings: Boolean = false,
        settingsHost: String = "",
    ) = ExtenderDecodeUi(
        ok = ok,
        errorKey = errorKey,
        networkHost = networkHost,
        foreignHost = foreignHost,
        count = count,
        hasSettings = hasSettings,
        settingsHost = settingsHost,
    )

    @Test
    fun theShareScreenPassesItsSwitchToTheSdkAndShowsWhatComesBack() {
        val asked = mutableListOf<Boolean>()
        val build: (Boolean) -> ExtenderShareUi = { includeSettings ->
            asked.add(includeSettings)
            ExtenderShareUi(
                text = "ur-ext:1:payload",
                count = 12,
                includesSettings = includeSettings,
            )
        }

        val without = extenderShareFor(false, build)
        val with = extenderShareFor(true, build)

        assertEquals(listOf(false, true), asked)
        assertFalse(without.includesSettings)
        assertTrue(with.includesSettings)
        // nothing is re-derived from the payload
        assertEquals(12, with.count)
        assertEquals("ur-ext:1:payload", with.text)
        assertTrue(with.present)
    }

    @Test
    fun aSpaceWithNothingToShareHasNoPayload() {
        val share = extenderShareFor(true) { ExtenderShareUi() }

        assertFalse(share.present)
        assertEquals(0, share.count)
    }

    @Test
    fun anUndecodablePayloadIsInvalid() {
        assertEquals(
            ExtenderImportStep.Invalid,
            extenderImportStep(decode(ok = false, errorKey = "import_extenders_invalid"), false),
        )
        // nothing scanned yet is the same state
        assertEquals(ExtenderImportStep.Invalid, extenderImportStep(null, false))
        assertEquals(ExtenderImportStep.Invalid, extenderImportStep(null, true))
    }

    @Test
    fun ownNetworkAddressesImportOutright() {
        assertEquals(ExtenderImportStep.Ready, extenderImportStep(decode(), false))
        // asking for settings a payload does not carry changes nothing
        assertEquals(ExtenderImportStep.Ready, extenderImportStep(decode(), true))
    }

    @Test
    fun aForeignPayloadIsRefusedWithoutItsSettings() {
        val foreign = decode(networkHost = "other.example", foreignHost = true)

        assertEquals(
            ExtenderImportStep.ForeignHost("other.example"),
            extenderImportStep(foreign, false),
        )
        // a payload with no settings block can never be taken, however the
        // switch stands -- and the switch is not even offered for it
        assertEquals(
            ExtenderImportStep.ForeignHost("other.example"),
            extenderImportStep(foreign, true),
        )
        assertFalse(extenderUseSettingsOffered(foreign))
    }

    @Test
    fun aForeignPayloadWithSettingsIsConfirmedFirst() {
        val foreign = decode(
            networkHost = "other.example",
            foreignHost = true,
            hasSettings = true,
            settingsHost = "extender.other.example",
        )

        assertTrue(extenderUseSettingsOffered(foreign))
        // the switch off still refuses it
        assertEquals(
            ExtenderImportStep.ForeignHost("other.example"),
            extenderImportStep(foreign, false),
        )
        // and the switch on asks about the operator host before replacing this
        // space's
        assertEquals(
            ExtenderImportStep.ConfirmSettings("extender.other.example"),
            extenderImportStep(foreign, true),
        )
    }

    @Test
    fun ownNetworkSettingsAreConfirmedToo() {
        val own = decode(hasSettings = true, settingsHost = "extender.bringyour.com")

        assertTrue(extenderUseSettingsOffered(own))
        assertEquals(ExtenderImportStep.Ready, extenderImportStep(own, false))
        assertEquals(
            ExtenderImportStep.ConfirmSettings("extender.bringyour.com"),
            extenderImportStep(own, true),
        )
    }

    @Test
    fun theSwitchIsNotOfferedForAPayloadThatDidNotDecode() {
        assertFalse(extenderUseSettingsOffered(null))
        assertFalse(extenderUseSettingsOffered(decode(ok = false, hasSettings = true)))
    }

    @Test
    fun sdkErrorKeysMapToTheirMessage() {
        assertEquals(
            R.string.import_extenders_foreign_host,
            extenderImportErrorResId("import_extenders_foreign_host"),
        )
        assertEquals(
            R.string.import_extenders_invalid,
            extenderImportErrorResId("import_extenders_invalid"),
        )
        // a key this build does not know still says something
        assertEquals(R.string.import_extenders_invalid, extenderImportErrorResId(""))
        assertEquals(R.string.import_extenders_invalid, extenderImportErrorResId("later_error"))
    }

    @Test
    fun theHostsFieldRoundTripsOnePerLine() {
        val hosts = listOf("extender.example", "192.0.2.1", "2001:db8::1")

        assertEquals("extender.example\n192.0.2.1\n2001:db8::1", extenderHostsText(hosts))
        assertEquals(hosts, extenderHostsFromText(extenderHostsText(hosts)))
    }

    @Test
    fun blankLinesAndPaddingAreNotHosts() {
        assertEquals(
            listOf("extender.example", "192.0.2.1"),
            extenderHostsFromText("  extender.example \n\n\t192.0.2.1\n   \n"),
        )
        assertEquals(listOf<String>(), extenderHostsFromText(""))
        assertEquals("", extenderHostsText(listOf()))
    }

    @Test
    fun aDefaultSettingShowsAnEmptyFieldAndAnOverrideShowsItself() {
        val defaults = ExtenderSettingsUi(
            dnsName = "extender.bringyour.com",
            dnsNameDefault = true,
            gossipUrl = "wss://gossip.bringyour.com",
            gossipUrlDefault = true,
        )

        // an empty field means the default, which the form shows as the
        // placeholder instead
        assertEquals("", defaults.dnsNameField)
        assertEquals("", defaults.gossipUrlField)

        val configured = defaults.copy(
            dnsName = "extender.example",
            dnsNameDefault = false,
            gossipUrl = "wss://gossip.example",
            gossipUrlDefault = false,
        )

        assertEquals("extender.example", configured.dnsNameField)
        assertEquals("wss://gossip.example", configured.gossipUrlField)
    }

    @Test
    fun onlyADefaultIsOfferedAsThePlaceholder() {
        val defaults = ExtenderSettingsUi(
            dnsName = "extender.bringyour.com",
            dnsNameDefault = true,
            gossipUrl = "wss://gossip.bringyour.com",
            gossipUrlDefault = true,
        )

        assertEquals("extender.bringyour.com", defaults.dnsNamePlaceholder)
        assertEquals("wss://gossip.bringyour.com", defaults.gossipUrlPlaceholder)

        // an overridden field reports the override as its effective value, so
        // there is no default to name until it is cleared and saved
        val configured = defaults.copy(
            dnsName = "extender.example",
            dnsNameDefault = false,
            gossipUrl = "wss://gossip.example",
            gossipUrlDefault = false,
        )

        assertEquals("", configured.dnsNamePlaceholder)
        assertEquals("", configured.gossipUrlPlaceholder)
    }

    @Test
    fun aPrivateExtenderIsConfiguredByItsAddress() {
        assertFalse(ExtenderPrivateUi().configured)
        assertFalse(ExtenderPrivateUi(secret = "s").configured)
        assertTrue(ExtenderPrivateUi(ip = "192.0.2.1", secret = "s").configured)
    }
}
