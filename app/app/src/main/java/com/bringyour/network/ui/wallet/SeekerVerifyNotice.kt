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

    // the server's own reason, for an error code this app does not know
    data class ServerMessage(val message: String) : SeekerVerifyNotice()

    companion object {
        const val WALLET_SUFFIX_LENGTH = 7

        const val CODE_TOKEN_NOT_FOUND = "seeker_token_not_found"
        const val CODE_INVALID_SIGNATURE = "seeker_invalid_signature"
        const val CODE_LOOKUP_FAILED = "seeker_lookup_failed"

        /** The notice for a signing step that cannot go on to the server, or null once signed. */
        fun fromSign(outcome: SeekerSignOutcome): SeekerVerifyNotice? = when (outcome) {
            SeekerSignOutcome.Signed -> null
            SeekerSignOutcome.NoWalletApp -> NoWalletApp
            SeekerSignOutcome.Failed, SeekerSignOutcome.NoSignature -> Failed
        }

        /**
         * The notice for a verification request, or null when the wallet was
         * verified. `requestFailed` covers both no api and a transport error.
         * A known `serverCode` picks the localized notice; the server's English
         * message is the fallback for an unknown or missing (older server) code.
         */
        fun fromVerifyResult(
            requestFailed: Boolean,
            success: Boolean,
            serverMessage: String?,
            walletAddress: String,
            serverCode: String? = null,
        ): SeekerVerifyNotice? = when {
            requestFailed -> Failed
            success -> null
            serverCode == CODE_TOKEN_NOT_FOUND -> NotHolder(walletAddress.takeLast(WALLET_SUFFIX_LENGTH))
            serverCode == CODE_INVALID_SIGNATURE || serverCode == CODE_LOOKUP_FAILED -> Failed
            !serverMessage.isNullOrBlank() -> ServerMessage(serverMessage)
            else -> NotHolder(walletAddress.takeLast(WALLET_SUFFIX_LENGTH))
        }
    }
}
