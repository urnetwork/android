package com.bringyour.network.ui.wallet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug sample source keeps the server's rules the Earnings screen depends on. */
class SampleLegacyWalletSourceTest {

    private val address = "Bhhbz5CgN4oFEwZvgAS8Pt5SqEubkoQqp8PCcZzJNq9R"

    private fun source(startConnected: Boolean = false) =
        SampleLegacyWalletSource(startConnected = startConnected, pendingUsd = 0.0, delayMillis = 0)

    @Test
    fun `the usdc mint is refused`() = runBlocking<Unit> {
        val source = source()

        assertEquals(false, source.validateAddress(SampleLegacyWalletSource.USDC_MINT).getOrNull())
        assertTrue(source.addSolanaWallet(SampleLegacyWalletSource.USDC_MINT).isFailure)
        assertTrue(source.wallets().getOrThrow().isEmpty())
    }

    @Test
    fun `the same address returns the same wallet`() = runBlocking<Unit> {
        val source = source()

        val first = source.addSolanaWallet(address).getOrThrow()
        val second = source.addSolanaWallet(" $address ").getOrThrow()

        assertEquals(first, second)
        assertEquals(1, source.wallets().getOrThrow().size)
    }

    @Test
    fun `create sets the payout wallet only when there is none`() = runBlocking<Unit> {
        val empty = source()
        val created = empty.addSolanaWallet(address).getOrThrow()
        assertEquals(created, empty.payoutWalletId().getOrThrow())

        val connected = source(startConnected = true)
        connected.addSolanaWallet(address).getOrThrow()
        assertEquals(SampleLegacyWalletSource.SAMPLE_WALLET_ID, connected.payoutWalletId().getOrThrow())
    }

    @Test
    fun `remove clears the payout wallet`() = runBlocking<Unit> {
        val source = source(startConnected = true)

        assertTrue(source.removeWallet(SampleLegacyWalletSource.SAMPLE_WALLET_ID).isSuccess)

        assertNull(source.payoutWalletId().getOrThrow())
        assertFalse(source.wallets().getOrThrow().any { it.walletId == SampleLegacyWalletSource.SAMPLE_WALLET_ID })
    }
}
