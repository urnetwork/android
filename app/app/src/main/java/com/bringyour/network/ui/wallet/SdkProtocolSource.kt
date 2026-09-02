package com.bringyour.network.ui.wallet

import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SnClaimCallback
import com.bringyour.sdk.SnError
import com.bringyour.sdk.SnWallet
import com.bringyour.sdk.SnWalletChangeListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SnProtocolException(val code: String?, message: String) : Exception(message)

/**
 * The SDK-backed protocol source. The wallet, gas key and claims live in the SDK on
 * the device; claims go from the SDK straight to the settlement vault contract. Points
 * history and head eligibility come from the URnetwork API through the SDK.
 */
class SdkProtocolSource private constructor(
    private val device: DeviceLocal,
) : EarningsProtocolSource {

    companion object {
        fun create(device: DeviceLocal): EarningsProtocolSource = SdkProtocolSource(device)

        private fun SnWallet.toState() = SnWalletState(
            coldkeySs58 = coldkeySs58,
            clientId = clientId.ifBlank { null },
            setAtMillis = setAtMillis,
        )

        private fun SnError?.toException(): SnProtocolException? =
            this?.let { SnProtocolException(it.code.ifBlank { null }, it.message) }
    }

    override val available: Boolean = true

    // DefaultSnChainSettings ships without the vault/coordinator/noId; the merged view is
    // filled from GET /sn/epoch once per source before the first vault read
    @Volatile private var chainSettingsSynced = false

    private suspend fun ensureChainSettings() {
        if (chainSettingsSynced) {
            return
        }
        if (device.snChainSettings?.isConfigured == true) {
            chainSettingsSynced = true
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            device.syncSnChainSettings { _, _ ->
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
        chainSettingsSynced = device.snChainSettings?.isConfigured == true
    }

    override fun currentWallet(): SnWalletState? = device.snWallet?.toState()

    override fun addWalletListener(listener: (SnWalletState?) -> Unit): () -> Unit {
        val sub = device.addSnWalletChangeListener(object : SnWalletChangeListener {
            override fun snWalletChanged(wallet: SnWallet?) {
                listener(wallet?.toState())
            }
        })
        return { sub.close() }
    }

    override fun validateSs58(address: String): Boolean = Sdk.validateSs58(address)

    override suspend fun validateWallet(address: String): Result<WalletValidation> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.snValidateWallet(address) { result, err ->
                if (!continuation.isActive) {
                    return@snValidateWallet
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty validation")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> continuation.resume(
                        Result.success(
                            WalletValidation(
                                validSyntax = result.validSyntax,
                                existsOnChain = result.existsOnChain,
                                banned = result.banned,
                                message = result.message.ifBlank { null },
                            )
                        )
                    )
                }
            }
        }

    override suspend fun fetchWallet(): Result<SnWalletState?> =
        suspendCancellableCoroutine { continuation ->
            device.syncSnWallet { result, err ->
                if (!continuation.isActive) {
                    return@syncSnWallet
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result?.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> continuation.resume(Result.success(device.snWallet?.toState()))
                }
            }
        }

    override suspend fun connectWallet(address: String, signature: String, message: String): Result<SnWalletState> =
        suspendCancellableCoroutine { continuation ->
            device.connectSnWallet(address, signature, message) { result, err ->
                if (!continuation.isActive) {
                    return@connectSnWallet
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty connect")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    result.wallet == null -> continuation.resume(Result.failure(IllegalStateException("no wallet")))
                    else -> continuation.resume(Result.success(result.wallet.toState()))
                }
            }
        }

    override fun gasKey(): SnGasKeyState? = device.snGasKey?.let { SnGasKeyState(it.address, it.mirrorSs58) }

    override suspend fun gasBalance(): Result<SnGasBalanceState> {
        ensureChainSettings()
        return suspendCancellableCoroutine { continuation ->
            device.snGasBalance { result, err ->
                if (!continuation.isActive) {
                    return@snGasBalance
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty balance")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> continuation.resume(Result.success(SnGasBalanceState(result.wei, result.tao)))
                }
            }
        }
    }

    override suspend fun claims(): Result<ClaimsSnapshot> {
        ensureChainSettings()
        return suspendCancellableCoroutine { continuation ->
            device.snClaims { result, err ->
                if (!continuation.isActive) {
                    return@snClaims
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty claims")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> {
                        val list = result.claims
                        val n = list?.len() ?: 0
                        val claims = (0 until n).map { i ->
                            val c = list.get(i)
                            EpochClaim(
                                epoch = c.epoch,
                                shareBps = c.shareBps,
                                amountRao = c.amountRao,
                                status = EpochClaimStatus.fromString(c.status),
                                claimOpenBlock = c.claimOpenBlock,
                                expiryBlock = c.expiryBlock,
                                txHash = c.txHash.ifBlank { null },
                            )
                        }
                        continuation.resume(Result.success(ClaimsSnapshot(claims, result.totalClaimableRao)))
                    }
                }
            }
        }
    }

    override fun claim(epochs: List<Long>, onEvent: (ClaimEvent) -> Unit) {
        val list = Sdk.newInt64List()
        epochs.forEach { list.add(it) }
        device.snClaim(list, object : SnClaimCallback {
            override fun sent(epoch: Long, txHash: String?) {
                onEvent(ClaimEvent.Sent(epoch, txHash ?: ""))
            }

            override fun confirmed(epoch: Long, txHash: String?, amountRao: Long) {
                onEvent(ClaimEvent.Confirmed(epoch, txHash ?: "", amountRao))
            }

            override fun failed(epoch: Long, message: String?) {
                onEvent(ClaimEvent.Failed(epoch, message ?: ""))
            }

            override fun done() {
                onEvent(ClaimEvent.Done)
            }
        })
    }

    override suspend fun accountEpochs(): Result<List<AccountEpoch>> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.accountEpochs { result, err ->
                if (!continuation.isActive) {
                    return@accountEpochs
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty epochs")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> {
                        val list = result.epochs
                        val n = list?.len() ?: 0
                        continuation.resume(
                            Result.success(
                                (0 until n).map { i ->
                                    val e = list.get(i)
                                    AccountEpoch(e.epoch, e.startMillis, e.endMillis, e.points, e.shareBps)
                                }
                            )
                        )
                    }
                }
            }
        }

    override suspend fun head(): Result<SnHeadState?> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.snHead { result, err ->
                if (!continuation.isActive) {
                    return@snHead
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.success(null))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> continuation.resume(
                        Result.success(
                            SnHeadState(
                                eligible = result.eligible,
                                score = result.score,
                                floor = result.floor,
                                rankEstimate = result.rankEstimate,
                                cutoff = result.cutoff,
                                bound = result.bound,
                                hotkey = result.hotkey.ifBlank { null },
                                uid = result.uid,
                                rank = result.rank,
                                epoch = result.epoch,
                                source = result.source,
                            )
                        )
                    )
                }
            }
        }

    override fun formatAlpha(rao: Long): String = Sdk.formatAlpha(rao)

    override fun formatShareBps(shareBps: Long): String = Sdk.formatShareBps(shareBps)

    override fun shortSs58(address: String): String = Sdk.shortSs58(address)

    override fun explorerTxUrl(txHash: String): String =
        device.snChainSettings?.explorerUrlForTx(txHash)?.ifBlank { null }
            ?: String.format(EarningsFormat.EXPLORER_TX_URL, txHash)
}
