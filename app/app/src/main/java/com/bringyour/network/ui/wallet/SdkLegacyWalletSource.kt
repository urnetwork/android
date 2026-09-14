package com.bringyour.network.ui.wallet

import com.bringyour.network.utils.SolanaAddress
import com.bringyour.sdk.Api
import com.bringyour.sdk.CreateAccountWalletArgs
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.RemoveWalletArgs
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SetPayoutWalletArgs
import com.bringyour.sdk.WalletValidateAddressArgs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LegacyWalletException(message: String) : Exception(message)

/**
 * The SDK-backed legacy wallet source: one URnetwork API call per method through the
 * device's `Api` (no view controller and no polling; the view model refreshes on device
 * bind, on resume, on pull to refresh and after each change). Ids are compared as
 * `Id.string()`, never with gomobile `Id` equality.
 */
class SdkLegacyWalletSource(
    private val device: DeviceLocal,
) : LegacyWalletSource {

    override val available: Boolean = true

    private suspend fun <T> call(request: (Api, (Result<T>) -> Unit) -> Unit): Result<T> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            request(api) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

    override suspend fun wallets(): Result<List<LegacyWallet>> = call { api, done ->
        api.getAccountWallets { result, err ->
            when {
                err != null -> done(Result.failure(err))
                result == null -> done(Result.failure(IllegalStateException("empty wallets")))
                else -> {
                    val list = result.wallets
                    val n = list?.len() ?: 0
                    val wallets = (0 until n).mapNotNull { i ->
                        val w = list.get(i)
                        LegacyWallet.fromRow(
                            walletId = w.walletId?.string(),
                            circleWalletId = w.circleWalletId,
                            blockchain = w.blockchain ?: "",
                            address = w.walletAddress ?: "",
                            hasSeekerToken = w.hasSeekerToken,
                        )
                    }
                    done(Result.success(wallets))
                }
            }
        }
    }

    override suspend fun payoutWalletId(): Result<String?> = call { api, done ->
        api.getPayoutWallet { result, err ->
            when {
                err != null -> done(Result.failure(err))
                result == null -> done(Result.failure(IllegalStateException("empty payout wallet")))
                // `wallet_id` is null when the network has no payout wallet
                else -> done(Result.success(result.walletId?.string()?.ifBlank { null }))
            }
        }
    }

    override suspend fun payments(): Result<List<LegacyPayment>> = call { api, done ->
        api.getAccountPayments { result, err ->
            val error = result?.error
            when {
                err != null -> done(Result.failure(err))
                result == null -> done(Result.failure(IllegalStateException("empty payments")))
                error != null -> done(Result.failure(LegacyWalletException(error.message)))
                else -> {
                    val list = result.accountPayments
                    val n = list?.len() ?: 0
                    val payments = (0 until n).map { i ->
                        val p = list.get(i)
                        LegacyPayment(
                            paymentId = p.paymentId?.string() ?: "",
                            walletId = p.walletId?.string()?.ifBlank { null },
                            payoutUsd = Sdk.nanoCentsToUsd(p.payout),
                            tokenAmount = p.tokenAmount,
                            completed = p.completed,
                            canceled = p.canceled,
                            completeMillis = if (p.completed) p.completeTime?.unixMilli() else null,
                        )
                    }
                    done(Result.success(payments))
                }
            }
        }
    }

    override fun validateSolanaSyntax(address: String): Boolean = SolanaAddress.isValidSyntax(address)

    override suspend fun validateAddress(address: String): Result<Boolean> = call { api, done ->
        val args = WalletValidateAddressArgs()
        args.address = address.trim()
        args.chain = LegacyChain.SOLANA.sdkValue
        api.walletValidateAddress(args) { result, err ->
            when {
                err != null -> done(Result.failure(err))
                result == null -> done(Result.failure(IllegalStateException("empty validation")))
                else -> done(Result.success(result.valid))
            }
        }
    }

    override suspend fun addSolanaWallet(address: String): Result<String> = call { api, done ->
        val args = CreateAccountWalletArgs()
        args.blockchain = LegacyChain.SOLANA.sdkValue
        args.walletAddress = address.trim()
        args.defaultTokenType = USDC_TOKEN_TYPE
        api.createAccountWallet(args) { result, err ->
            val walletId = result?.walletId?.string()?.ifBlank { null }
            when {
                err != null -> done(Result.failure(err))
                walletId == null -> done(Result.failure(IllegalStateException("no wallet id")))
                else -> done(Result.success(walletId))
            }
        }
    }

    override suspend fun setPayoutWallet(walletId: String): Result<Unit> {
        val id = try {
            Sdk.parseId(walletId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return call { api, done ->
            val args = SetPayoutWalletArgs()
            args.walletId = id
            api.setPayoutWallet(args) { _, err ->
                done(if (err != null) Result.failure(err) else Result.success(Unit))
            }
        }
    }

    override suspend fun removeWallet(walletId: String): Result<Unit> = call { api, done ->
        val args = RemoveWalletArgs()
        args.walletId = walletId
        api.removeWallet(args) { result, err ->
            when {
                err != null -> done(Result.failure(err))
                result == null -> done(Result.failure(IllegalStateException("empty remove")))
                !result.success -> done(
                    Result.failure(
                        LegacyWalletException(result.error?.message?.ifBlank { null } ?: "The wallet was not removed.")
                    )
                )
                else -> done(Result.success(Unit))
            }
        }
    }

    companion object {
        const val USDC_TOKEN_TYPE = "USDC"
    }
}
