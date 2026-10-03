package com.bringyour.network.ui.login

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The verify screen's "code sent" toast was a hard-coded English literal, so
 * every locale saw English. It must come from the localization store, which
 * generates a translated resource for every locale.
 *
 * Reads the module's own sources and generated string resources; no device.
 */
class VerifyCodeSentToastLocalizedTest {

    private val key = "verification_code_sent_2"
    private val res = File("src/main/res")

    private fun strings(dir: File): Map<String, String> {
        val file = File(dir, "strings.xml")
        if (!file.exists()) {
            return mapOf()
        }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `code sent toast is not a hard-coded literal`() {
        val source = File("src/main/java/com/bringyour/network/ui/login/LoginVerify.kt").readText()
        assertFalse(
            "LoginVerify.kt shows a hard-coded toast",
            Regex("""Toast\.makeText\([^,]+,\s*"""").containsMatchIn(source),
        )
        assertTrue(
            "LoginVerify.kt does not use R.string.$key for the toast",
            source.contains("R.string.$key"),
        )
    }

    @Test
    fun `code sent toast is translated in every locale`() {
        val english = strings(File(res, "values"))[key]
        assertEquals("Verification code sent.", english)

        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        for (locale in locales) {
            val value = strings(locale)[key]
            assertTrue("${locale.name} has no $key", !value.isNullOrEmpty())
            assertNotEquals("${locale.name} $key is English", english, value)
        }
    }
}
