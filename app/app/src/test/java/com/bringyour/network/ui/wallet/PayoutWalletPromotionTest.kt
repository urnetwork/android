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
 * (promotedPayoutWallet picks the wallet; payouts_now_go_to is the line). Before
 * the removal, the confirmation says payouts move to another such wallet or are
 * held while there is none (remove_wallet_moves_or_holds_payouts).
 */
class PayoutWalletPromotionTest {

    private val removed = LegacyWallet(
        "wallet-sol", "SyntheticRemovedPayoutWa11etForTests1111111", LegacyChain.SOLANA, hasSeekerToken = false
    )

    private val promoted = LegacyWallet(
        "wallet-sol-2", "SyntheticPromotedPayoutWa11etForTests111111", LegacyChain.SOLANA, hasSeekerToken = true
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

    // ---- the remove confirmation, before the removal

    private val confirmation = "USDC payouts move to another of your Solana or Polygon wallets, or are held until you connect one."

    @Test
    fun `the remove confirmation says payouts move to another wallet or are held`() {
        // which wallet takes over is the server's choice (the card lists only the payout
        // wallet), and with none left payouts are held: one line for both
        val english = strings(File(res, "values"))
        assertEquals(confirmation, english["remove_wallet_moves_or_holds_payouts"])
        // the retired line said payouts are always held
        assertNull(english["remove_wallet_holds_payouts"])
    }

    @Test
    fun `the remove confirmation is translated in every locale`() {
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            assertNull("${locale.name} keeps the retired line", values["remove_wallet_holds_payouts"])
            val value = values["remove_wallet_moves_or_holds_payouts"]
            if (value.isNullOrEmpty()) {
                missing.add(locale.name)
                continue
            }
            assertNotEquals("${locale.name} is English", confirmation, value)
            for (name in listOf("USDC", "Solana", "Polygon")) {
                assertTrue("${locale.name} drops $name: $value", value.contains(name))
            }
        }
        assertTrue("not translated: $missing", missing.isEmpty())
    }

    @Test
    fun `the remove dialog shows the confirmation`() {
        val card = File("src/main/java/com/bringyour/network/ui/wallet/SolanaWalletCard.kt").readText()
        val dialog = card.indexOf("fun RemoveSolanaWalletDialog(")
        assertTrue("the card has no remove dialog", 0 <= dialog)
        assertTrue(
            "the remove dialog does not show remove_wallet_moves_or_holds_payouts",
            card.indexOf("stringResource(id = R.string.remove_wallet_moves_or_holds_payouts)", dialog) > dialog,
        )
        assertFalse("the card still shows the retired line", card.contains("remove_wallet_holds_payouts"))
    }
}
