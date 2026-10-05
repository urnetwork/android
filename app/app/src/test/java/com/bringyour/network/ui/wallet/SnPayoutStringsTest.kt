package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The SN payout line's strings are in every locale, the USDC line is labeled
 * as the final USDC payout, and no payout string fixes a duration: the times
 * come from the epoch schedule.
 *
 * Reads the module's generated string resources; no device.
 */
class SnPayoutStringsTest {

    private val res = File("src/main/res")

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val english = strings(File(res, "values"))

    @Test
    fun `the payout line strings are translated in every locale`() {
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())

        val missing = mutableListOf<String>()
        for (key in listOf(
            "sn_payout_schedule",
            "sn_payout_schedule_times",
            "set_coldkey_to_get_paid",
            "set_coldkey",
            "usdc_waiting",
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

    @Test
    fun `the times line takes the epoch end, claim open and expiry`() {
        assertEquals(
            "This epoch ends %1\$s. Claim your share from %2\$s until %3\$s.",
            english["sn_payout_schedule_times"]
        )
    }

    @Test
    fun `the usdc line is labeled as the final USDC payout`() {
        assertEquals("Final USDC payout: %1\$s USDC waiting", english["usdc_waiting"])
    }

    @Test
    fun `no payout string fixes the claim window`() {
        for (key in listOf("sn_payout_schedule", "sn_payout_schedule_times", "claims_open_after_finalization")) {
            val value = english[key]!!
            assertFalse("$key fixes a duration: $value", Regex("\\d+ (hours?|days?|weeks?|epochs?)").containsMatchIn(value))
        }
        assertFalse("payouts_amount_threshold" in english)
    }
}
