package com.bringyour.network.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWalletUiTest {

    private val solana = LegacyWallet(
        "wallet-sol", "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM", LegacyChain.SOLANA, hasSeekerToken = false
    )

    // the SOL row a Seeker verification leaves; not a payout wallet by itself
    private val seekerRow = LegacyWallet(
        "wallet-seeker", "Bhhbz5CgN4oFEwZvgAS8Pt5SqEubkoQqp8PCcZzJNq9R", LegacyChain.SOLANA, hasSeekerToken = true
    )

    private val polygon = LegacyWallet(
        "wallet-matic", "0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a596877665544", LegacyChain.POLYGON, hasSeekerToken = false
    )

    private fun payment(
        id: String,
        usd: Double,
        completed: Boolean = false,
        canceled: Boolean = false,
        walletId: String? = null,
    ) = LegacyPayment(id, walletId, usd, 0.0, completed, canceled, null)

    @Test
    fun `the payout wallet is the wallet whose id is the payout id`() {
        val ui = LegacyWalletUi(listOf(seekerRow, solana, polygon), payoutWalletId = "wallet-sol")

        assertEquals(solana, ui.payoutWallet)
    }

    @Test
    fun `a polygon payout wallet is still shown as connected`() {
        val ui = LegacyWalletUi(listOf(solana, polygon), payoutWalletId = "wallet-matic")

        assertEquals(polygon, ui.payoutWallet)
    }

    @Test
    fun `a solana row that is not the payout wallet is not connected`() {
        assertNull(LegacyWalletUi(listOf(seekerRow), payoutWalletId = null).payoutWallet)
        assertNull(LegacyWalletUi(listOf(seekerRow), payoutWalletId = "wallet-other").payoutWallet)
    }

    @Test
    fun `a payout id without a legacy wallet row is not connected`() {
        // for example a payout row whose wallet the list dropped
        assertNull(LegacyWalletUi(listOf(solana), payoutWalletId = "wallet-tao").payoutWallet)
    }

    @Test
    fun `tao and circle rows are ignored`() {
        assertNull(LegacyWallet.fromRow("wallet-tao", "", "TAO", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", false))
        assertNull(LegacyWallet.fromRow("wallet-circle", "circle-1", "MATIC", "0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a596877665544", false))
        assertNull(LegacyWallet.fromRow("wallet-unknown", null, "ETHEREUM", "0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a596877665544", false))
        assertNull(LegacyWallet.fromRow(null, null, "SOL", solana.address, false))
    }

    @Test
    fun `a solana row becomes a legacy wallet`() {
        assertEquals(
            seekerRow,
            LegacyWallet.fromRow("wallet-seeker", "", "SOL", " ${seekerRow.address} ", true)
        )
        assertEquals(polygon, LegacyWallet.fromRow("wallet-matic", null, "MATIC", polygon.address, false))
    }

    @Test
    fun `pending sums only payments neither completed nor canceled`() {
        val ui = LegacyWalletUi(
            payments = listOf(
                payment("held", 3.50),
                payment("scheduled", 0.37, walletId = "wallet-sol"),
                payment("paid", 12.40, completed = true, walletId = "wallet-sol"),
                payment("canceled", 5.00, canceled = true),
            )
        )

        assertEquals(3.87, ui.pendingUsd, 1e-9)
        assertTrue(ui.hasPending)
    }

    @Test
    fun `nothing is pending below what two decimals can show`() {
        assertFalse(LegacyWalletUi().hasPending)
        assertFalse(LegacyWalletUi(payments = listOf(payment("dust", 0.004))).hasPending)
        assertTrue(LegacyWalletUi(payments = listOf(payment("cent", 0.005))).hasPending)
    }

    @Test
    fun `chains map from the sdk blockchain names`() {
        assertEquals(LegacyChain.SOLANA, LegacyChain.fromSdk("SOL"))
        assertEquals(LegacyChain.SOLANA, LegacyChain.fromSdk("sol"))
        assertEquals(LegacyChain.POLYGON, LegacyChain.fromSdk("MATIC"))
        assertNull(LegacyChain.fromSdk("TAO"))
        assertNull(LegacyChain.fromSdk(""))
    }
}
