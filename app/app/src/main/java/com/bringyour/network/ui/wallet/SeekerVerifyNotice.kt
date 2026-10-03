package com.bringyour.network.ui.wallet

/** How the wallet app answered the request to sign the Seeker verification message. */
enum class SeekerSignOutcome {
    Signed,
    // no Mobile Wallet Adapter wallet app on the device
    NoWalletApp,
    // the wallet app declined, timed out or failed
    Failed,
    // the wallet app answered without a signature
    NoSignature,
}

/**
 * What the user is told when a Seeker token verification does not succeed.
 * Settings shows it in a snackbar. A missing wallet app, a wallet app error
 * and an unreachable server used to be only logged, so Claim did nothing
 * visible.
 */
sealed class SeekerVerifyNotice {
    object NoWalletApp : SeekerVerifyNotice()

    // the wallet app failed, or the server could not be reached
    object Failed : SeekerVerifyNotice()

    // the server checked the wallet and found no Seeker or Saga token
    data class NotHolder(val walletSuffix: String) : SeekerVerifyNotice()

    // the server's own reason (bad signature, token lookup failed, ...)
    data class ServerMessage(val message: String) : SeekerVerifyNotice()

    companion object {
        const val WALLET_SUFFIX_LENGTH = 7

        /** The notice for a signing step that cannot go on to the server, or null once signed. */
        fun fromSign(outcome: SeekerSignOutcome): SeekerVerifyNotice? = when (outcome) {
            SeekerSignOutcome.Signed -> null
            SeekerSignOutcome.NoWalletApp -> NoWalletApp
            SeekerSignOutcome.Failed, SeekerSignOutcome.NoSignature -> Failed
        }

        /**
         * The notice for a verification request, or null when the wallet was
         * verified. `requestFailed` covers both no api and a transport error.
         */
        fun fromVerifyResult(
            requestFailed: Boolean,
            success: Boolean,
            serverMessage: String?,
            walletAddress: String,
        ): SeekerVerifyNotice? = when {
            requestFailed -> Failed
            success -> null
            !serverMessage.isNullOrBlank() -> ServerMessage(serverMessage)
            else -> NotHolder(walletAddress.takeLast(WALLET_SUFFIX_LENGTH))
        }
    }
}
