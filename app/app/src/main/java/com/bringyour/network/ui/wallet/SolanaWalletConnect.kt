package com.bringyour.network.ui.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey

sealed class SolanaWalletConnectResult {
    data class Success(val address: String) : SolanaWalletConnectResult()
    object NoWalletFound : SolanaWalletConnectResult()
    // the library's own reason: a decline, a back-out before connecting, a timeout
    data class Failure(val message: String, val error: Exception) : SolanaWalletConnectResult()
}

/**
 * Asks a Solana wallet app on the device (Mobile Wallet Adapter: the built-in wallet on
 * Saga and Seeker, or Phantom, Solflare, ...) for its account. Authorize only, no message
 * signing: `POST /account/wallet` takes no signature. The public key is the USDC payout
 * address.
 *
 * Restored from `connectSolanaWallet` of the Payout Wallets screen removed in a2241948.
 */
suspend fun connectSolanaWalletAddress(activityResultSender: ActivityResultSender): SolanaWalletConnectResult {
    val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://ur.io"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "URnetwork",
        ),
    )
    walletAdapter.blockchain = Solana.Mainnet

    return when (val result = walletAdapter.connect(activityResultSender)) {
        is TransactionResult.Success -> {
            val account = result.authResult.accounts.firstOrNull()
            if (account == null) {
                val reason = "Wallet did not return an account"
                SolanaWalletConnectResult.Failure(reason, IllegalStateException(reason))
            } else {
                SolanaWalletConnectResult.Success(SolanaPublicKey(account.publicKey).base58())
            }
        }
        is TransactionResult.NoWalletFound -> SolanaWalletConnectResult.NoWalletFound
        is TransactionResult.Failure -> SolanaWalletConnectResult.Failure(result.message, result.e)
    }
}
