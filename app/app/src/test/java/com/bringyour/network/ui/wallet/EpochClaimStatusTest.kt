package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The SDK's claim statuses as Earnings reads them (support inbox 8126). The SDK
 * reports an epoch the vault finalized without a payout root as "not-finalized";
 * the history reads it as No payout published, so a status this build does not
 * know must not land there. The count under the unclaimed total counts claimable
 * epochs, so its string says claimable, not finalized.
 *
 * Plain JVM: reads the module's generated string resources; no device.
 */
class EpochClaimStatusTest {

    private val res = File("src/main/res")

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val english = strings(File(res, "values"))

    @Test
    fun `each SDK status reads as its own state`() {
        assertEquals(EpochClaimStatus.OPEN, EpochClaimStatus.fromString("open"))
        assertEquals(EpochClaimStatus.CLAIMABLE, EpochClaimStatus.fromString("claimable"))
        assertEquals(EpochClaimStatus.CLAIMED, EpochClaimStatus.fromString("claimed"))
        assertEquals(EpochClaimStatus.EXPIRED, EpochClaimStatus.fromString("expired"))
        assertEquals(EpochClaimStatus.NOT_FINALIZED, EpochClaimStatus.fromString("not-finalized"))
    }

    @Test
    fun `a status this build does not know is not read as finalized without a payout`() {
        assertEquals(EpochClaimStatus.OPEN, EpochClaimStatus.fromString("pending-review"))
        assertEquals(EpochClaimStatus.OPEN, EpochClaimStatus.fromString(""))
    }

    @Test
    fun `an epoch finalized without a payout reads No payout published`() {
        assertEquals("No payout published", english["sn_status_not_finalized"])
    }

    @Test
    fun `the count under the unclaimed total says claimable epochs`() {
        assertEquals("Claimable epochs: %1\$d", english["claim_across_epochs"])
    }

    @Test
    fun `both strings are translated in every locale`() {
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = mutableListOf<String>()
        for (key in listOf("sn_status_not_finalized", "claim_across_epochs")) {
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
