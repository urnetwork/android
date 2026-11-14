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
 * its dialog look up every one of them. So are capture the flag's, from the
 * localizations store (sn CAPTURETHEFLAG.md §10), which the tile and the
 * dialogs look up.
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

    // capture the flag's claim flow
    private val ctfKeys = listOf(
        "sn_ctf_pool",
        "sn_ctf_won_extender_slot",
        "sn_ctf_won_provider_slot",
        "sn_ledger_claim_both",
        "sn_ledger_pool_unavailable",
        "sn_ledger_pool_under_floor",
        "sn_ledger_provider_pool",
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
        // the translated catalogs the store generates (ANDROID_LOCALE_DIR)
        assertEquals(19, locales.size)

        val missing = mutableListOf<String>()
        for (key in keys + ctfKeys) {
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

    @Test
    fun `capture the flag's strings are the store's`() {
        assertEquals("Capture the flag", english["sn_ctf_pool"])
        assertEquals("Provider pool", english["sn_ledger_provider_pool"])
        assertEquals("Below the claim minimum", english["sn_ledger_pool_under_floor"])
        assertEquals("Unavailable right now", english["sn_ledger_pool_unavailable"])
        assertEquals("Claim both", english["sn_ledger_claim_both"])
        // one of a country's slots, never its flag; the country's name goes in bare
        assertEquals("You won one of the %1\$s provider slots", english["sn_ctf_won_provider_slot"])
        assertEquals("You won one of the %1\$s extender slots", english["sn_ctf_won_extender_slot"])
    }

    @Test
    fun `the tile and the dialogs look up every capture the flag string`() {
        val source = listOf("SnLedgerTile.kt", "SnLedger.kt", "ClaimRelayDialog.kt").joinToString("\n") {
            File("src/main/java/com/bringyour/network/ui/wallet/$it").readText()
        }
        for (key in ctfKeys) {
            assertTrue("$key is not shown", source.contains("R.string.$key"))
        }
    }
}
