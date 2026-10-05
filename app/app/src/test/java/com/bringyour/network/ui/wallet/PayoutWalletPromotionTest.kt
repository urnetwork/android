package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Removing the payout wallet makes another active Solana or Polygon wallet of the
 * network the payout wallet when there is one (server fix/remove-wallet-promote).
 * The card then says where payouts go: "Payouts now go to <short address>."
 * (promotedPayoutWallet picks the wallet; payouts_now_go_to is the line).
 */
class PayoutWalletPromotionTest {

    private val removed = LegacyWallet(
        "wallet-sol", "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM", LegacyChain.SOLANA, hasSeekerToken = false
    )

    private val promoted = LegacyWallet(
        "wallet-sol-2", "Bhhbz5CgN4oFEwZvgAS8Pt5SqEubkoQqp8PCcZzJNq9R", LegacyChain.SOLANA, hasSeekerToken = true
    )

    private val polygon = LegacyWallet(
        "wallet-matic", "0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a596877665544", LegacyChain.POLYGON, hasSeekerToken = false
    )

    @Test
    fun `removing the payout wallet names the wallet that took its place`() {
        val after = LegacyWalletUi(listOf(promoted, polygon), payoutWalletId = "wallet-sol-2")

        assertEquals(promoted, promotedPayoutWallet(removed.walletId, removed.walletId, after))
    }

    @Test
    fun `a promoted polygon wallet is named too`() {
        val after = LegacyWalletUi(listOf(polygon), payoutWalletId = "wallet-matic")

        assertEquals(polygon, promotedPayoutWallet(removed.walletId, removed.walletId, after))
    }

    @Test
    fun `nothing is said when no wallet took its place`() {
        // an older server, or no other Solana or Polygon wallet: no payout wallet now
        assertNull(promotedPayoutWallet(removed.walletId, removed.walletId, LegacyWalletUi(listOf(polygon))))
        // a payout read that still names the removed wallet
        assertNull(
            promotedPayoutWallet(
                removed.walletId,
                removed.walletId,
                LegacyWalletUi(listOf(removed, polygon), payoutWalletId = removed.walletId),
            )
        )
    }

    @Test
    fun `nothing is said when the removed wallet was not the payout wallet`() {
        val after = LegacyWalletUi(listOf(promoted), payoutWalletId = "wallet-sol-2")

        assertNull(promotedPayoutWallet(removed.walletId, "wallet-sol-2", after))
        assertNull(promotedPayoutWallet(removed.walletId, null, after))
    }

    @Test
    fun `nothing is said for a payout wallet the card cannot show`() {
        // a payout id without a legacy wallet row, such as a Bittensor row
        val after = LegacyWalletUi(listOf(polygon), payoutWalletId = "wallet-tao")

        assertNull(promotedPayoutWallet(removed.walletId, removed.walletId, after))
    }

    @Test
    fun `the promoted state is not busy`() {
        assertFalse(SolanaConnectState.Promoted(promoted).busy)
    }

    // ---- the line

    private val res = File("src/main/res")

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `the line names the address in every locale`() {
        val english = strings(File(res, "values"))["payouts_now_go_to"]
        assertEquals("Payouts now go to %1\$s.", english)

        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = mutableListOf<String>()
        for (locale in locales) {
            val value = strings(locale)["payouts_now_go_to"]
            if (value.isNullOrEmpty()) {
                missing.add(locale.name)
                continue
            }
            assertNotEquals("${locale.name} is English", english, value)
            assertTrue("${locale.name} drops the address: $value", value.contains("%1\$s"))
        }
        assertTrue("not translated: $missing", missing.isEmpty())
    }

    @Test
    fun `the card shows the line for a promoted wallet`() {
        val card = File("src/main/java/com/bringyour/network/ui/wallet/SolanaWalletCard.kt").readText()
        val promotedCase = card.indexOf("is SolanaConnectState.Promoted ->")
        assertTrue("the card has no promoted line", 0 <= promotedCase)
        assertTrue(
            "the promoted line is not payouts_now_go_to with the short address",
            card.indexOf("R.string.payouts_now_go_to, SolanaAddress.short(state.wallet.address)", promotedCase) > promotedCase,
        )
    }
}
