package com.bringyour.network.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Settings entry to the ur.io cloud proxies (P150) reads the same as the
 * proxies row on iOS, macOS, Windows and Linux: the shared store keys
 * use_wireguard_socks_https_proxy and use_wireguard_socks_https_proxy_note.
 * The android-only "Use as a proxy instead (no VPN)" named no proxy type, read
 * as if the phone itself became a proxy, and its "no VPN" is not true of the
 * WireGuard option.
 *
 * Reads the module's generated string resources and the settings screen's
 * source; no device.
 */
class CloudProxyEntryCopyTest {

    private val titleKey = "use_wireguard_socks_https_proxy"
    private val noteKey = "use_wireguard_socks_https_proxy_note"

    // the android-only pair the shared keys replace
    private val retiredKeys = listOf("use_as_proxy_no_vpn", "use_as_proxy_no_vpn_detail")

    // gradle runs unit tests with the module directory as the working
    // directory; the other candidates cover runners that start a level up
    private fun moduleFile(path: String): File {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .firstOrNull { it.exists() }
        assertNotNull("$path not found", file)
        return file!!
    }

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    // the settings screen's `if (showCloudProxyEntry) { ... }` block
    private fun entrySource(): String {
        val screen = moduleFile(
            "src/main/java/com/bringyour/network/ui/settings/SettingsScreen.kt"
        ).readText()
        val start = screen.indexOf("if (showCloudProxyEntry) {")
        assertTrue("the cloud proxy entry is gone", 0 <= start)
        var depth = 0
        for (i in screen.indexOf('{', start) until screen.length) {
            when (screen[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return screen.substring(start, i + 1)
            }
        }
        throw AssertionError("the cloud proxy entry block is not closed")
    }

    @Test
    fun `entry shows the shared title and note`() {
        val entry = entrySource()
        val title = entry.indexOf("R.string.$titleKey)")
        val note = entry.indexOf("R.string.$noteKey)")
        assertTrue("the entry's title is not $titleKey", 0 <= title)
        assertTrue("the entry's note is not $noteKey", 0 <= note)
        assertTrue("the note does not follow the title", title < note)
        for (key in retiredKeys) {
            assertFalse("the entry still shows $key", entry.contains("R.string.$key)"))
        }
        // every word of the entry comes from the catalogs; its icon is decorative
        assertFalse(
            "the entry has an english-only content description",
            entry.contains("contentDescription = \""),
        )
    }

    @Test
    fun `english reads as on the other apps`() {
        val english = strings(moduleFile("src/main/res/values"))
        assertEquals("Use WireGuard / SOCKS / HTTPS proxy", english[titleKey])
        assertEquals("Opens ur.io in your browser. SOCKS and WireGuard need Pro.", english[noteKey])
        for (key in retiredKeys) {
            assertFalse("$key is still generated", english.containsKey(key))
        }
    }

    @Test
    fun `title and note are translated in every locale`() {
        val res = moduleFile("src/main/res")
        val english = strings(File(res, "values"))
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())

        // the proxy types, the site and the plan stay verbatim in every language
        val verbatim = mapOf(
            titleKey to listOf("WireGuard", "SOCKS", "HTTPS"),
            noteKey to listOf("ur.io", "SOCKS", "WireGuard", "Pro"),
        )
        val wrong = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            for ((key, names) in verbatim) {
                val value = values[key]
                when {
                    value.isNullOrEmpty() -> wrong.add("${locale.name} $key is missing")
                    value == english[key] -> wrong.add("${locale.name} $key is English")
                    names.any { !value.contains(it) } -> wrong.add("${locale.name} $key: $value")
                }
            }
        }
        assertTrue("$wrong", wrong.isEmpty())
    }
}
