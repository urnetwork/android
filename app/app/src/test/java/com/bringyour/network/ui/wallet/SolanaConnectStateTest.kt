package com.bringyour.network.ui.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaConnectStateTest {

    private val wallet = LegacyWallet(
        "wallet-sol", "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM", LegacyChain.SOLANA, hasSeekerToken = false
    )

    @Test
    fun `busy is true exactly while a wallet app, a link or a remove is in flight`() {
        val busy = listOf(
            SolanaConnectState.ConnectingApp,
            SolanaConnectState.Linking(wallet.address),
            SolanaConnectState.Removing,
        )
        val notBusy = listOf(
            SolanaConnectState.Idle,
            SolanaConnectState.Linked(wallet),
            SolanaConnectState.NoWalletApp,
            SolanaConnectState.Failed(null),
            SolanaConnectState.Failed("boom"),
        )

        busy.forEach { assertTrue("$it", it.busy) }
        notBusy.forEach { assertFalse("$it", it.busy) }
    }

    @Test
    fun `only an ok or looks new address can continue`() {
        assertTrue(AddressValidation.Ok.canContinue)
        assertTrue(AddressValidation.LooksNew(null).canContinue)

        listOf(
            AddressValidation.Empty,
            AddressValidation.InvalidSyntax,
            AddressValidation.Checking,
            AddressValidation.Blocked(null),
            AddressValidation.Unavailable(null),
        ).forEach { assertFalse("$it", it.canContinue) }
    }
}
