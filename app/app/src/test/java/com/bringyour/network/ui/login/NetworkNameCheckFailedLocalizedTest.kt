package com.bringyour.network.ui.login

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The create-network name line for a failed availability check
 * (`network_name_check_failed`) was added to the english resources by hand and
 * never to the localization store, so every locale showed it in English and
 * the next store generation would have dropped it.
 *
 * Reads the module's generated string resources; no device.
 */
class NetworkNameCheckFailedLocalizedTest {

    private val res = File("src/main/res")
    private val key = "network_name_check_failed"

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `name check failure line is translated in every locale`() {
        val english = strings(File(res, "values"))[key]
        // raw resource text: the apostrophe stays escaped
        assertEquals("Couldn\\'t check availability. You can still continue.", english)
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
}
