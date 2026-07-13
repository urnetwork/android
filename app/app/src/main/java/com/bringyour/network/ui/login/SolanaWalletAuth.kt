package com.bringyour.network.ui.login

import android.net.Uri
import android.util.Base64
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthWalletChallengeArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class SolanaSignedChallenge(
    val publicKey: String,
    val message: String,
    val signature: String
)

sealed class SolanaChallengeSignResult {
    data class Success(val signed: SolanaSignedChallenge) : SolanaChallengeSignResult()
    object NoWalletFound : SolanaChallengeSignResult()
    data class Failure(val error: Throwable) : SolanaChallengeSignResult()
}

/**
 * Fetches a fresh, server-issued wallet-auth challenge and has the user's
 * Solana wallet sign it via Mobile Wallet Adapter's raw message-signing API
 * (`signMessagesDetached`), NOT the "Sign In With Solana" convenience API —
 * SIWS wraps the message in a multi-field canonical format that the
 * server's strict challenge parser does not accept.
 *
 * IMPORTANT: call this fresh for every login or create-network attempt.
 * The server marks a challenge used the moment it is checked, whether
 * the check succeeds or fails, so a signed message/signature pair must
 * never be reused across two server calls.
 */
suspend fun requestAndSignSolanaChallenge(
    activityResultSender: ActivityResultSender,
    api: Api,
): SolanaChallengeSignResult {

    val challengeArgs = AuthWalletChallengeArgs()
    challengeArgs.blockchain = "solana"

    var challengeFetchFailureCause: String? = null

    val messageTemplate = suspendCancellableCoroutine<String?> { cont ->
        api.authWalletChallenge(challengeArgs) { result, err ->
            if (!cont.isActive) {
                return@authWalletChallenge
            }
            when {
                err != null -> {
                    challengeFetchFailureCause = err.message
                    cont.resume(null)
                }
                result == null -> {
                    challengeFetchFailureCause = "empty result"
                    cont.resume(null)
                }
                result.error != null -> {
                    challengeFetchFailureCause = result.error.message
                    cont.resume(null)
                }
                else -> cont.resume(result.messageTemplate)
            }
        }
    } ?: return SolanaChallengeSignResult.Failure(
        Exception("Could not fetch a wallet sign-in challenge from the server: $challengeFetchFailureCause")
    )

    val solanaUri = Uri.parse("https://ur.io")
    val iconUri = Uri.parse("favicon.ico")
    val identityName = "URnetwork"

    val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = solanaUri,
            iconUri = iconUri,
            identityName = identityName,
        ),
    )
    walletAdapter.blockchain = Solana.Mainnet

    val result = walletAdapter.transact(activityResultSender) { authResult ->
        val account = authResult.accounts.firstOrNull()
            ?: throw IllegalStateException("Wallet did not return an account")
        signMessagesDetached(
            arrayOf(messageTemplate.toByteArray(Charsets.UTF_8)),
            arrayOf(account.publicKey)
        )
    }

    return when (result) {
        is TransactionResult.Success -> {
            val signatureBytes = result.successPayload?.messages?.firstOrNull()?.signatures?.firstOrNull()
            val account = result.authResult.accounts.firstOrNull()
            when {
                signatureBytes == null -> {
                    SolanaChallengeSignResult.Failure(Exception("Wallet did not return a signature"))
                }
                account == null -> {
                    SolanaChallengeSignResult.Failure(Exception("Wallet did not return an account"))
                }
                else -> {
                    val publicKey = SolanaPublicKey(account.publicKey).base58()
                    val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

                    SolanaChallengeSignResult.Success(
                        SolanaSignedChallenge(
                            publicKey = publicKey,
                            message = messageTemplate,
                            signature = signatureBase64
                        )
                    )
                }
            }
        }
        is TransactionResult.NoWalletFound -> SolanaChallengeSignResult.NoWalletFound
        is TransactionResult.Failure -> SolanaChallengeSignResult.Failure(result.e)
    }
}
