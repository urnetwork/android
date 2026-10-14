package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The payout consent's strings (CONSENT.md §5, C5) are the store's pinned
 * keys, in English as pinned and translated in every locale the app ships;
 * the active line takes the coldkey the app shortens; and the block looks up
 * every one of them.
 *
 * Reads the module's string resources and sources; no device.
 */
class SnConsentStringsTest {

    private val res = File("src/main/res")

    // CONSENT.md §5, with {coldkey} as the store lowers it for android
    private val pinned = mapOf(
        "sn_consent_title" to "Payout consent",
        "sn_consent_none" to "No payout wallet yet. Earnings aren't paid until you sign.",
        "sn_consent_body" to "Sign once with your Bittensor wallet so this network's earnings are paid to it.",
        "sn_consent_sign" to "Sign with wallet",
        "sn_consent_signing" to "Waiting for your wallet's signature…",
        "sn_consent_active" to "Earnings are paid to %1\$s",
        "sn_consent_change" to "Change wallet",
        "sn_consent_change_note" to "A new wallet takes effect from the next epoch.",
        "sn_consent_expired" to "The request expired. Try again.",
        "sn_consent_failed" to "The signature didn't match this wallet. Try again.",
        "sn_consent_owner_only" to "Only the network owner can choose the payout wallet.",
    )

    // the values of strings.xml, with android's resource escapes undone
    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { element ->
                element.getAttribute("name") to element.textContent
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\")
            }
    }

    private val english = strings(File(res, "values"))

    @Test
    fun `the consent strings are the pinned english`() {
        // C5
        for ((key, value) in pinned) {
            assertEquals(key, value, english[key])
        }
    }

    @Test
    fun `the consent strings are translated in every locale`() {
        // C5: every platform, from the localization store
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertEquals(18, locales.size)

        val missing = mutableListOf<String>()
        for (key in pinned.keys) {
            for (locale in locales) {
                val value = strings(locale)[key]
                if (value.isNullOrEmpty()) {
                    missing.add("${locale.name}/$key")
                    continue
                }
                assertNotEquals("${locale.name} $key is English", english[key], value)
                // the same placeholders as English: the active line's coldkey
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
    fun `the block looks up every consent string`() {
        // C4: each state's line
        val source = File("src/main/java/com/bringyour/network/ui/wallet/SnConsentTile.kt").readText() +
            File("src/main/java/com/bringyour/network/ui/wallet/SnConsent.kt").readText()
        for (key in pinned.keys) {
            assertTrue("$key is not shown", source.contains("R.string.$key"))
        }
        // the active line names the shortened coldkey
        assertTrue(source.contains("stringResource(id = R.string.sn_consent_active, shortSs58(tile.activeColdkeySs58))"))
    }
}
