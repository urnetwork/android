package com.bringyour.network.ui.wallet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule the Solana wallet option hinges on: create (or re-activate) the wallet, then
 * make it the payout wallet unless the server already did, including when the payout
 * wallet cannot be read.
 */
class LinkSolanaWalletTest {

    private val address = "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM"

    /** Answers with fixed results and records the calls the link makes. */
    private class RecordingSource(
        val create: Result<String> = Result.success("wallet-new"),
        val payoutRead: Result<String?> = Result.success(null),
        val set: Result<Unit> = Result.success(Unit),
    ) : LegacyWalletSource {
        val calls = mutableListOf<String>()

        override val available: Boolean = true
        override suspend fun wallets(): Result<List<LegacyWallet>> = Result.success(emptyList())
        override suspend fun payments(): Result<List<LegacyPayment>> = Result.success(emptyList())
        override fun validateSolanaSyntax(address: String): Boolean = true
        override suspend fun validateAddress(address: String): Result<Boolean> = Result.success(true)
        override suspend fun removeWallet(walletId: String): Result<Unit> = Result.success(Unit)

        override suspend fun addSolanaWallet(address: String): Result<String> {
            calls.add("add $address")
            return create
        }

        override suspend fun payoutWalletId(): Result<String?> {
            calls.add("read payout")
            return payoutRead
        }

        override suspend fun setPayoutWallet(walletId: String): Result<Unit> {
            calls.add("set $walletId")
            return set
        }
    }

    @Test
    fun `a wallet the server already made the payout wallet is not set again`() = runBlocking<Unit> {
        val source = RecordingSource(payoutRead = Result.success("wallet-new"))

        val result = linkSolanaWallet(source, address)

        assertEquals("wallet-new", result.getOrNull())
        assertEquals(listOf("add $address", "read payout"), source.calls)
    }

    @Test
    fun `another payout wallet is replaced by the new wallet`() = runBlocking<Unit> {
        // a Seeker verification row or a legacy payout wallet the server keeps on create
        val source = RecordingSource(payoutRead = Result.success("wallet-other"))

        val result = linkSolanaWallet(source, address)

        assertEquals("wallet-new", result.getOrNull())
        assertEquals(listOf("add $address", "read payout", "set wallet-new"), source.calls)
    }

    @Test
    fun `a payout wallet that cannot be read is set anyway`() = runBlocking<Unit> {
        val source = RecordingSource(payoutRead = Result.failure(IllegalStateException("no api")))

        val result = linkSolanaWallet(source, address)

        assertEquals("wallet-new", result.getOrNull())
        assertEquals(listOf("add $address", "read payout", "set wallet-new"), source.calls)
    }

    @Test
    fun `a failed set fails the link`() = runBlocking<Unit> {
        val source = RecordingSource(
            payoutRead = Result.success("wallet-other"),
            set = Result.failure(IllegalStateException("Wallet must be an active wallet owned by the network.")),
        )

        val result = linkSolanaWallet(source, address)

        assertTrue(result.isFailure)
        assertEquals("Wallet must be an active wallet owned by the network.", result.exceptionOrNull()?.message)
        assertEquals(listOf("add $address", "read payout", "set wallet-new"), source.calls)
    }

    @Test
    fun `a failed create never sets the payout wallet`() = runBlocking<Unit> {
        val source = RecordingSource(create = Result.failure(IllegalArgumentException("invalid wallet address")))

        val result = linkSolanaWallet(source, address)

        assertEquals("invalid wallet address", result.exceptionOrNull()?.message)
        assertEquals(listOf("add $address"), source.calls)
    }
}
