package com.bringyour.network.ui.connect

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Fixed IP keeps one exit for the session (connect stickyExit: no standing
 * spare and no hourly rotation), so the toggle says so in its subtitle
 * (`fixed_ip_subtitle`) instead of leaving users to expect a rotating exit.
 *
 * Reads the module's generated string resources and the connect drawer's
 * source; no device.
 */
class FixedIpSubtitleCopyTest {

    private val key = "fixed_ip_subtitle"

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

    @Test
    fun `subtitle says the exit stays for the session`() {
        val res = moduleFile("src/main/res")
        assertEquals(
            "Keeps one exit for the session; changes only if that provider goes offline.",
            strings(File(res, "values"))[key],
        )
    }

    @Test
    fun `subtitle is translated in every locale`() {
        val res = moduleFile("src/main/res")
        val english = strings(File(res, "values"))[key]
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())

        val missing = mutableListOf<String>()
        for (locale in locales) {
            val value = strings(locale)[key]
            if (value.isNullOrEmpty()) {
                missing.add(locale.name)
            } else {
                assertNotEquals("${locale.name} $key is English", english, value)
            }
        }
        assertTrue("not translated: $missing", missing.isEmpty())
    }

    @Test
    fun `fixed ip row shows the subtitle`() {
        val drawer = moduleFile(
            "src/main/java/com/bringyour/network/ui/connect/ConnectActions.kt"
        ).readText()
        val label = drawer.indexOf("R.string.fixed_ip)")
        val subtitle = drawer.indexOf("R.string.fixed_ip_subtitle)")
        val toggle = drawer.indexOf("toggleFixedIpSize()")
        assertTrue("the Fixed IP label is gone", 0 <= label)
        assertTrue("the Fixed IP row has no subtitle", 0 <= subtitle)
        // between the label and its switch, so it reads as the toggle's subtitle
        assertTrue("the subtitle is not part of the Fixed IP row", subtitle in label..toggle)
    }
}
