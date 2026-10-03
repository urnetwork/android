package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Seeker verification strings the app shows were English in every locale:
 * the localization store had no translations for them, or had them but they
 * were never generated into the android resources.
 *
 * Reads the module's generated string resources; no device.
 */
class SeekerStringsLocalizedTest {

    private val res = File("src/main/res")

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `seeker strings are translated in every locale`() {
        val english = strings(File(res, "values"))
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())

        val missing = mutableListOf<String>()
        for (key in listOf(
            "seeker_verify_no_wallet_app",
            "seeker_multiplier_benefit",
            "seeker_token_not_found",
        )) {
            assertTrue("values has no $key", !english[key].isNullOrEmpty())
            for (locale in locales) {
                val value = strings(locale)[key]
                if (value.isNullOrEmpty()) {
                    missing.add("${locale.name}/$key")
                } else {
                    assertNotEquals("${locale.name} $key is English", english[key], value)
                }
            }
        }
        assertTrue("not translated: $missing", missing.isEmpty())
    }
}
