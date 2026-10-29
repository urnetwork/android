package com.bringyour.network

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * What the VPN notification says in each status (vpnNotificationText), in
 * the app's own English: the words are read from the base resources, the
 * ones MainService reads, so the assertions are what a phone shows.
 */
class VpnNotificationTextTest {

    private companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start higher up
        fun file(path: String): File = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .first { it.isFile }

        /** The base strings.xml, its escapes resolved as Android resolves them. */
        val resources: Pair<Map<String, String>, Map<String, Map<String, String>>> by lazy {
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file("src/main/res/values/strings.xml"))
            fun Element.children(tag: String): List<Element> =
                getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
            val root = document.documentElement
            val strings = root.children("string").associate { it.getAttribute("name") to unescape(it.textContent) }
            val plurals = root.children("plurals").associate { plural ->
                plural.getAttribute("name") to plural.children("item")
                    .associate { it.getAttribute("quantity") to unescape(it.textContent) }
            }
            strings to plurals
        }

        fun unescape(raw: String): String {
            val text = if (2 <= raw.length && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                if (text[i] == '\\' && i + 1 < text.length) {
                    out.append(
                        when (val escaped = text[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> escaped
                        }
                    )
                    i += 2
                } else {
                    out.append(text[i])
                    i += 1
                }
            }
            return out.toString()
        }

        fun string(name: String): String = resources.first.getValue(name)

        /** The resources the notification's strings read, as MainService names them. */
        val stringNames = listOf(
            "app_name",
            "on",
            "vpn_notification_protected",
            "connecting_status_indicator",
            "conn_failed",
            "reconnect_tunnel_status_indicator",
            "slow_internet",
            "slow_internet_fallback_title",
            "slow_internet_still_connected",
            "slow_internet_upload_delay",
            "slow_internet_download_delay",
            "slow_internet_recovered",
            "slow_internet_recovered_detail",
        )
    }

    private val strings = object : VpnNotificationStrings {
        override val appName get() = string("app_name")
        override val on get() = string("on")
        override fun protectedAt(location: String) = String.format(string("vpn_notification_protected"), location)
        override fun providerCount(count: Int): String {
            val forms = resources.second.getValue("provider_count")
            // English: one, else other
            return String.format(forms.getValue(if (count == 1) "one" else "other"), count)
        }
        override val connecting get() = string("connecting_status_indicator")
        override val connectFailed get() = string("conn_failed")
        override val tunnelDisconnected get() = string("reconnect_tunnel_status_indicator")
        override val slowInternet get() = string("slow_internet")
        override val slowInternetFallback get() = string("slow_internet_fallback_title")
        override val slowInternetStillConnected get() = string("slow_internet_still_connected")
        override val uploadDelay get() = string("slow_internet_upload_delay")
        override val downloadDelay get() = string("slow_internet_download_delay")
        override val recovered get() = string("slow_internet_recovered")
        override val recoveredDetail get() = string("slow_internet_recovered_detail")
    }

    private fun text(
        connectRequested: Boolean = true,
        providerCount: Long = 3,
        // a window with providers is satisfied unless a test says otherwise
        minSatisfied: Boolean = 0 < providerCount,
        failed: Boolean = false,
        tunnelStarted: Boolean = true,
        name: String? = "Germany",
        slowInternetPhase: SlowInternetNoticePhase = SlowInternetNoticePhase.NONE,
        transportDelay: TransportDelayUi = TransportDelayUi.None,
    ) = vpnNotificationText(
        vpnNotificationPresentation(
            connectRequested = connectRequested,
            systemAlwaysOn = false,
            providerCount = providerCount,
            minSatisfied = minSatisfied,
            failed = failed,
            locationProviderCount = 0,
            tunnelStarted = tunnelStarted,
            locationName = name,
            city = null,
            region = null,
            country = null,
            bestAvailableLabel = string("best_available_provider"),
            slowInternetPhase = slowInternetPhase,
            transportDelay = transportDelay,
        ),
        strings,
    )

    private val slowDelay = TransportDelayUi(
        upQuality = 0.25,
        downQuality = 0.75,
        upBitsPerSecond = 48_000,
        downBitsPerSecond = 85_000,
        upQueueDelayMillis = 640,
        downQueueDelayMillis = 1_200,
    )

    @Test
    fun connectedSaysProtectedAndThePlaceOnOneLine() {
        assertEquals(VpnNotificationText("Protected · Germany", "3 providers", "3 providers"), text())
        assertEquals("1 provider", text(providerCount = 1).text)
    }

    @Test
    fun withNoPlaceChosenThePlaceIsTheBestAvailableProvider() {
        assertEquals("Protected · Best available provider", text(name = null).title)
    }

    @Test
    fun connectingSaysTheConnectScreensLineThenThePlace() {
        val connecting = VpnNotificationText("Connecting to providers", "Germany", "Germany")
        assertEquals(connecting, text(providerCount = 0))
        // the providers a forming window has added are not counted: the
        // line stays short, as the connect screen's does
        assertEquals(connecting, text(providerCount = 2, minSatisfied = false))
    }

    @Test
    fun aFailedConnectSaysSoThenThePlace() {
        assertEquals(
            VpnNotificationText("Couldn't connect", "Germany", "Germany"),
            text(providerCount = 0, failed = true),
        )
    }

    @Test
    fun theTunnelDownSaysSoThenThePlace() {
        assertEquals(
            VpnNotificationText("VPN tunnel disconnected 😓", "Germany", "Germany"),
            text(tunnelStarted = false),
        )
    }

    @Test
    fun theVpnUpWithNoConnectionSaysOn() {
        // providing, or the kill switch holding traffic: no place, no providers
        assertEquals(VpnNotificationText("URnetwork", "On", "On"), text(connectRequested = false, providerCount = 0))
    }

    @Test
    fun aSlowConnectionSwapsInTheSlowContent() {
        assertEquals(
            VpnNotificationText(
                title = "Slow internet",
                text = "URnetwork is still connected",
                bigText = "Upload delay 640 ms · 48 kb/s\nDownload delay 1.2 s · 85 kb/s",
            ),
            text(slowInternetPhase = SlowInternetNoticePhase.SLOW, transportDelay = slowDelay),
        )
    }

    @Test
    fun aSlowFallbackConnectionSaysSo() {
        assertEquals(
            "Using a slower fallback connection",
            text(
                slowInternetPhase = SlowInternetNoticePhase.SLOW,
                transportDelay = slowDelay.copy(fallback = true),
            ).title,
        )
    }

    @Test
    fun aSlowReadingWithNoDelayExpandsToItsText() {
        // a level with neither direction's delay has no lines to expand to
        val slow = text(
            slowInternetPhase = SlowInternetNoticePhase.SLOW,
            transportDelay = TransportDelayUi(downQuality = 0.5),
        )
        assertEquals("URnetwork is still connected", slow.bigText)
    }

    @Test
    fun backToNormalSaysSoBeforeTheNormalContentReturns() {
        assertEquals(
            VpnNotificationText("Internet back to normal", "Delays cleared", "Delays cleared"),
            text(slowInternetPhase = SlowInternetNoticePhase.RECOVERED),
        )
    }

    @Test
    fun theSlowContentNeverStandsInForAnotherStatus() {
        // the presentation decides (VpnNotificationPolicyTest); the words follow
        assertEquals(
            "Connecting to providers",
            text(providerCount = 0, slowInternetPhase = SlowInternetNoticePhase.SLOW, transportDelay = slowDelay).title,
        )
    }

    @Test
    fun theServiceReadsTheseStrings() {
        // the words above are the service's: its strings read the same resources
        val service = file("src/main/java/com/bringyour/network/MainService.kt").readText()
        val from = service.indexOf("    private val notificationStrings = object : VpnNotificationStrings {")
        assertTrue("missing notificationStrings", 0 <= from)
        val block = service.substring(from, service.indexOf("\n    }\n", from))
        for (name in stringNames) {
            assertTrue(name, block.contains("R.string.$name)") || block.contains("R.string.$name,"))
        }
        assertTrue(block, block.contains("R.plurals.provider_count"))
        val build = service.substring(
            service.indexOf("    private fun buildForegroundNotification("),
        ).let { it.substring(0, it.indexOf("\n    }\n")) }
        assertTrue(build, build.contains("vpnNotificationText(presentation, notificationStrings)"))
    }
}
