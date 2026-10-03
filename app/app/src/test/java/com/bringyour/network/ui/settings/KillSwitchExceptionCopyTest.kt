package com.bringyour.network.ui.settings

import com.bringyour.network.vpnIpv6CaptureRoutes
import com.bringyour.network.vpnRouteContains
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kill switch exception disclosure must match what the VPN builder
 * installs. The tunnel captures ::/0 minus the local scopes (VpnRoutes.kt), so
 * the disclosure must not tell users that IPv6 bypasses the VPN (it said so
 * after the dual-stack tunnel landed). SMTP on TCP port 25 is still a
 * deliberate local route (connect ip_smtp_policy.go) and stays disclosed.
 */
class KillSwitchExceptionCopyTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val SETTINGS_SCREEN =
            "src/main/java/com/bringyour/network/ui/settings/SettingsScreen.kt"

        private fun stringValue(locale: String, name: String): String? {
            val dir = if (locale == "en") "values" else "values-$locale"
            val xml = moduleFile("src/main/res/$dir/strings.xml").readText()
            return Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
        }
    }

    @Test
    fun theTunnelCapturesPublicIpv6() {
        val routes = vpnIpv6CaptureRoutes()
        for (address in listOf("2606:4700:4700::1111", "2a00:1450:4001:80b::200e")) {
            assertTrue(
                "$address is not captured",
                routes.any { vpnRouteContains(it, address) },
            )
        }
    }

    @Test
    fun theDisclosureDoesNotSayIpv6BypassesTheVpn() {
        val screen = moduleFile(SETTINGS_SCREEN).readText()
        assertFalse(
            "the settings screen still shows the IPv6 bypass disclosure",
            screen.contains("R.string.kill_switch_exception_detail)"),
        )
        assertTrue(screen.contains("R.string.kill_switch_exception_smtp_detail"))

        val english = stringValue("en", "kill_switch_exception_smtp_detail")
        assertNotNull("kill_switch_exception_smtp_detail is missing", english)
        assertFalse(english!!.contains("IPv6 is not routed through URnetwork"))
        assertTrue(english.contains("IPv6 is routed through URnetwork like IPv4"))
        // the port 25 exception is real and stays disclosed
        assertTrue(english.contains("SMTP on TCP port 25 bypasses the VPN"))
    }

    @Test
    fun theCorrectedDisclosureIsLocalized() {
        for (locale in listOf("ar", "de", "es", "ru", "zh")) {
            assertNotNull(
                "$locale translation missing",
                stringValue(locale, "kill_switch_exception_smtp_detail"),
            )
        }
    }
}
