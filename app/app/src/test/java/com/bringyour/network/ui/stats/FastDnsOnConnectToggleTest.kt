package com.bringyour.network.ui.stats

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The host-network dns fallback is an opt-in shown as "Fast DNS on connect", with a
 * description that discloses it can reveal lookups to the local network. Reads the
 * generated english resources from the module, so no device or robolectric is needed.
 */
class FastDnsOnConnectToggleTest {

    private fun stringName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun englishString(id: Int): String {
        val name = stringName(id)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            if (element.getAttribute("name") == name) {
                return element.textContent.replace("\\'", "'")
            }
        }
        throw AssertionError("missing english string $name")
    }

    @Test
    fun toggleIsLabeledFastDnsOnConnect() {
        assertEquals("fast_dns_on_connect", stringName(FastDnsOnConnectToggle.labelRes))
        assertEquals("Fast DNS on connect", englishString(FastDnsOnConnectToggle.labelRes))
    }

    @Test
    fun descriptionDisclosesLocalNetworkExposure() {
        val description = englishString(FastDnsOnConnectToggle.descriptionRes)
        assertTrue(description, description.contains("can reveal your lookups to the local network"))
        assertTrue(description, description.contains("When off, DNS only resolves through the tunnel"))
    }

    @Test
    fun toggleIsOffWithoutSettingsAndByDefault() {
        assertFalse(FastDnsOnConnectToggle.isOn(null))
        assertFalse(FastDnsOnConnectToggle.isOn(DnsSettingsUi()))
        assertFalse(DnsSettingsUi().fastDnsOnConnectEnabled)
    }

    @Test
    fun toggleMapsToSdkEnableFallbackOnly() {
        val base = DnsSettingsUi(enableRemoteDoh = true, remoteDohUrlsIpv4 = listOf("https://doh.example/dns-query"))
        val on = FastDnsOnConnectToggle.toggled(base)
        assertTrue(on.enableFallback)
        assertTrue(FastDnsOnConnectToggle.isOn(on))
        assertEquals(base, on.copy(enableFallback = false))
        val off = FastDnsOnConnectToggle.toggled(on)
        assertFalse(off.enableFallback)
        assertEquals(base, off)
    }
}
