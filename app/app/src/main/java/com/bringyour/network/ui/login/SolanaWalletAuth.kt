package com.bringyour.network.ui.login

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthWalletChallengeArgs
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SolanaWalletFailure
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.publickey.SolanaPublicKey
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val logTag = "SolanaWalletAuth"

data class SolanaSignedChallenge(
    val publicKey: String,
    val message: String,
    val signature: String
)

sealed class SolanaChallengeSignResult {
    data class Success(val signed: SolanaSignedChallenge) : SolanaChallengeSignResult()
    // the wallet did not sign: the sdk's reading of why, shown with solanaWalletFailureText
    data class WalletFailed(val failure: SolanaWalletFailure) : SolanaChallengeSignResult()
    // the server's challenge could not be fetched, so no wallet was asked: not a wallet failure
    object ChallengeUnavailable : SolanaChallengeSignResult()
}

// thrown in the transact block when the wallet authorized no account; transact
// hands a RuntimeException back as the TransactionResult.Failure's exception
private class SolanaWalletNoAccountException : IllegalStateException("Wallet did not return an account")

/**
 * Fetches a fresh, server-issued wallet-auth challenge and has the user's
 * Solana wallet sign it via Mobile Wallet Adapter's raw message-signing API
 * (`signMessagesDetached`), not the "Sign In With Solana" convenience API —
 * SIWS wraps the message in a multi-field canonical format that the
 * server's strict challenge parser does not accept.
 *
 * Call this fresh for every login or create-network attempt.
 * The server marks a challenge used the moment it is checked, whether
 * the check succeeds or fails, so a signed message/signature pair must
 * never be reused across two server calls.
 *
 * A wallet that did not sign is read by the sdk (Sdk.solanaWalletFailureFor),
 * as on every app: no wallet app answered is the app's no_wallet, a wallet
 * that authorized no account the app's no_account, and any other failure the
 * wallet's Mobile Wallet Adapter JSON-RPC error code ("" when there is none).
 * The wallet's own English is only logged.
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
    } ?: run {
        Log.i(logTag, "Could not fetch a wallet sign-in challenge from the server: $challengeFetchFailureCause")
        return SolanaChallengeSignResult.ChallengeUnavailable
    }

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
            ?: throw SolanaWalletNoAccountException()
        signMessagesDetached(
            arrayOf(messageTemplate.toByteArray(Charsets.UTF_8)),
            arrayOf(account.publicKey)
        )
    }

    // `source` and `code` as Sdk.solanaWalletFailureFor reads them; Mobile Wallet
    // Adapter does not say which wallet answered, so the sdk names "Solana"
    val walletFailed: (source: String, code: String, detail: String, e: Throwable?) -> SolanaChallengeSignResult = { source, code, detail, e ->
        val failure = Sdk.solanaWalletFailureFor(source, code, "")
        Log.i(logTag, "Wallet did not sign: ${failure.failure} (source $source, code \"$code\"): $detail", e)
        SolanaChallengeSignResult.WalletFailed(failure)
    }

    return when (result) {
        is TransactionResult.Success -> {
            val signatureBytes = result.successPayload?.messages?.firstOrNull()?.signatures?.firstOrNull()
            val account = result.authResult.accounts.firstOrNull()
            when {
                account == null -> {
                    walletFailed(Sdk.SolanaWalletFailureSourceApp, Sdk.SolanaWalletFailureNoAccount, "no account", null)
                }
                signatureBytes == null -> {
                    walletFailed(Sdk.SolanaWalletFailureSourceMobileWalletAdapter, "", "no signature", null)
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
        is TransactionResult.NoWalletFound -> {
            walletFailed(Sdk.SolanaWalletFailureSourceApp, Sdk.SolanaWalletFailureNoWallet, result.message, null)
        }
        is TransactionResult.Failure -> {
            // transact hands a wallet's JSON-RPC error back inside an ExecutionException,
            // so the cause chain is searched (stopping at a cycle)
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            val causes = generateSequence<Throwable>(result.e) { it.cause }.takeWhile { seen.add(it) }
            when (val cause = causes.firstOrNull { it is SolanaWalletNoAccountException || it is JsonRpc20Client.JsonRpc20RemoteException }) {
                is SolanaWalletNoAccountException -> {
                    walletFailed(Sdk.SolanaWalletFailureSourceApp, Sdk.SolanaWalletFailureNoAccount, result.message, result.e)
                }
                is JsonRpc20Client.JsonRpc20RemoteException -> {
                    walletFailed(Sdk.SolanaWalletFailureSourceMobileWalletAdapter, cause.code.toString(), result.message, result.e)
                }
                // no wallet code (a lost association, a timeout, the wallet closed): the sdk reads "" as wallet_error
                else -> {
                    walletFailed(Sdk.SolanaWalletFailureSourceMobileWalletAdapter, "", result.message, result.e)
                }
            }
        }
    }
}
