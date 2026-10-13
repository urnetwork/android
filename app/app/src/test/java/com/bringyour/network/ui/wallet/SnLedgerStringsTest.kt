package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Settlement v2 strings are in every locale the app ships (APPS-SPEC.md
 * UX-6), the pending line takes the amount the app formats, and the tile and
 * its dialog look up every one of them.
 *
 * Reads the module's string resources and sources; no device.
 */
class SnLedgerStringsTest {

    private val res = File("src/main/res")

    private val keys = listOf(
        "sn_ledger_claimable",
        "sn_ledger_earned",
        "sn_ledger_paid",
        "sn_ledger_pending",
        "sn_ledger_pool",
        "sn_ledger_title",
        "sn_ledger_unavailable",
    )

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val english = strings(File(res, "values"))

    @Test
    fun `the settlement v2 strings are translated in every locale`() {
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertEquals(18, locales.size)

        val missing = mutableListOf<String>()
        for (key in keys) {
            assertTrue("values has no $key", !english[key].isNullOrEmpty())
            for (locale in locales) {
                val value = strings(locale)[key]
                if (value.isNullOrEmpty()) {
                    missing.add("${locale.name}/$key")
                    continue
                }
                // "Pool" is the word several languages use for a pool
                if (key != "sn_ledger_pool") {
                    assertNotEquals("${locale.name} $key is English", english[key], value)
                }
                // the same placeholders as English
                assertEquals(
                    "${locale.name} $key placeholders",
                    Regex("%\\d\\$[sd]").findAll(english[key]!!).map { it.value }.toList(),
                    Regex("%\\d\\$[sd]").findAll(value).map { it.value }.toList(),
                )
            }
        }
        assertTrue("not translated: $missing", missing.isEmpty())
    }

    @Test
    fun `the pending line takes the formatted amount`() {
        assertEquals("Pending: +%1\$s, awaiting finalization", english["sn_ledger_pending"])
        assertEquals("Pool %1\$d", english["sn_ledger_pool"])
        assertEquals("Settlement v2", english["sn_ledger_title"])
        assertEquals("Unavailable", english["sn_ledger_unavailable"])
    }

    @Test
    fun `the tile and its dialog look up every settlement v2 string`() {
        val source = File("src/main/java/com/bringyour/network/ui/wallet/SnLedgerTile.kt").readText()
        for (key in keys) {
            assertTrue("$key is not shown", source.contains("R.string.$key"))
        }
    }
}
