package com.bringyour.network.ui.login

import android.content.Context
import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SolanaWalletFailure

// the wallet a message names when the sdk names none, as the sdk does
// when it does not know which wallet answered
private const val genericSolanaWalletName = "Solana"

/**
 * The message for a Solana wallet that did not sign: signing in, the
 * create-network re-sign, and adding a Solana wallet as a sign-in method.
 * The sdk reads why (Sdk.solanaWalletFailureFor) and names the localizations
 * store key of the message every app shows for it, with the wallet name the
 * message takes, so the same failure reads the same on every app; this only
 * displays that key. Every key the sdk answers has its own R.string reference
 * here, which resource shrinking keeps. A key this build does not know (a
 * newer sdk) shows the generic wallet failure.
 */
fun solanaWalletFailureText(context: Context, failure: SolanaWalletFailure): String {
    // a message that takes a name always gets one
    val walletName = failure.walletName.ifEmpty { genericSolanaWalletName }
    return when (failure.messageKey) {
        Sdk.SolanaWalletFailureMessageUserRejected ->
            context.getString(R.string.bittensor_error_user_rejected)
        Sdk.SolanaWalletFailureMessageNoWallet ->
            context.getString(R.string.no_wallets_found_alert_content)
        Sdk.SolanaWalletFailureMessageWalletAppNotFound ->
            context.getString(R.string.couldnt_open_wallet)
        Sdk.SolanaWalletFailureMessageExtensionNotFound ->
            context.getString(R.string.bittensor_error_extension_not_found, walletName)
        Sdk.SolanaWalletFailureMessageNoAccount ->
            context.getString(R.string.bittensor_error_no_account, walletName)
        Sdk.SolanaWalletFailureMessageSessionNotFound ->
            context.getString(R.string.solana_wallet_error_session_not_found)
        Sdk.SolanaWalletFailureMessageWallet ->
            context.getString(R.string.bittensor_error_wallet_failed, walletName)
        else ->
            context.getString(R.string.bittensor_error_wallet_failed, walletName)
    }
}
