package com.bringyour.network.ui.wallet

import com.bringyour.network.ui.leaderboard.ProtocolTopMe
import com.bringyour.network.ui.leaderboard.protocolTopMeOf

import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SnClaimCallback
import com.bringyour.sdk.SnClaimRelayAccountList
import com.bringyour.sdk.SnClaimStatusResult
import com.bringyour.sdk.SnCtfWinList
import com.bringyour.sdk.SnError
import com.bringyour.sdk.SnLedgerClaimCallback
import com.bringyour.sdk.SnLedgerUnavailablePoolList
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

        // an error leaves the balances unavailable, never zero
        private fun snClaimStatusOf(result: SnClaimStatusResult) = SnClaimStatusState(
            relayState = result.relayState,
            coldkeySs58 = result.coldkeySs58,
            action = result.action,
            reason = result.reason,
            claimableRao = result.claimableRao,
            valueTaoRao = result.valueTaoRao,
            pendingRao = result.pendingRao,
            claimNowAvailable = result.claimNowAvailable,
            claimNowReason = result.claimNowReason,
            claimNowNextAtMillis = result.claimNowNextAtMillis,
            selfClaimAvailable = result.selfClaimAvailable,
            selfClaimNeedsGas = result.selfClaimNeedsGas,
            automaticThresholdRao = result.automaticThresholdRao,
            transferFloorRao = result.transferFloorRao,
            automaticThresholdTaoRao = result.automaticThresholdTaoRao,
            transferFloorTaoRao = result.transferFloorTaoRao,
            claimNowPeriodDays = result.claimNowPeriodDays,
            showsTile = result.showsTile,
            showsClaimed = result.showsClaimed,
            claimTxHash = result.claimTxHash,
            claimTxUrl = result.claimTxUrl,
            claimAmountRao = result.claimAmountRao,
            accounts = snClaimRelayAccountsOf(result.accounts),
            unavailablePools = snLedgerUnavailablePoolsOf(result.unavailablePools),
            unavailable = result.error != null,
        )

        private fun snClaimRelayAccountsOf(list: SnClaimRelayAccountList?): List<SnClaimRelayAccountState> {
            if (list == null) {
                return emptyList()
            }
            return (0 until list.len()).map { i ->
                val a = list.get(i)
                SnClaimRelayAccountState(
                    coldkeySs58 = a.coldkeySs58,
                    noId = a.noId,
                    claimableRao = a.claimableRao,
                    claimTxHash = a.claimTxHash,
                    claimTxUrl = a.claimTxUrl,
                    claimAmountRao = a.claimAmountRao,
                    kind = a.kind,
                )
            }
        }

        // the pools a ledger or relay answer could not read: never nil from the sdk, empty for a whole answer
        private fun snLedgerUnavailablePoolsOf(list: SnLedgerUnavailablePoolList?): List<SnLedgerPool> {
            if (list == null) {
                return emptyList()
            }
            return (0 until list.len()).map { i ->
                val p = list.get(i)
                SnLedgerPool(
                    noId = p.noId,
                    kind = p.kind,
                )
            }
        }

        // the wins of a GET /sn/ctf/client answer, in the sdk's order: never nil
        // from the sdk, so an answer without them could not be read
        private fun snCtfWinsOf(list: SnCtfWinList?): List<SnCtfWinState> {
            if (list == null) {
                throw IllegalStateException("a capture the flag answer without its unclaimed wins")
            }
            return (0 until list.len()).map { i ->
                val w = list.get(i)
                SnCtfWinState(
                    role = w.role,
                    country = w.country,
                )
            }
        }
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
                        val schedule = result.schedule?.let {
                            SnEpochScheduleState(
                                epoch = it.epoch,
                                endMillis = it.endMillis,
                                claimOpenMillis = it.claimOpenMillis,
                                expiryMillis = it.expiryMillis,
                                epochBlocks = it.epochBlocks,
                            )
                        }
                        continuation.resume(Result.success(ClaimsSnapshot(claims, result.totalClaimableRao, schedule)))
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

    override suspend fun ledgerAccounts(): Result<SnLedgerAccounts> {
        ensureChainSettings()
        return suspendCancellableCoroutine { continuation ->
            device.snLedgerAccounts { result, err ->
                if (!continuation.isActive) {
                    return@snLedgerAccounts
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty ledger accounts")))
                    result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))
                    else -> {
                        val list = result.accounts
                        val n = list?.len() ?: 0
                        continuation.resume(
                            Result.success(
                                SnLedgerAccounts(
                                    accounts = (0 until n).map { i ->
                                        val a = list.get(i)
                                        SnLedgerAccountState(
                                            noId = a.noId,
                                            index = a.index,
                                            finalizedRao = a.finalizedRao,
                                            claimableRao = a.claimableRao,
                                            paidRao = a.paidRao,
                                            pending = a.pending,
                                            pendingRao = a.pendingRao,
                                            kind = a.kind,
                                        )
                                    },
                                    // the sdk asks with partial=true: a pool it could not read is
                                    // named here, beside the accounts of the others
                                    unavailablePools = snLedgerUnavailablePoolsOf(result.unavailablePools),
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // "" until the release configures vault2 (before activation)
    override fun ledgerClaimConfigured(): Boolean = !device.snChainSettings?.vault2Address.isNullOrBlank()

    override fun ledgerClaim(onEvent: (SnLedgerClaimEvent) -> Unit) {
        device.snLedgerClaim(object : SnLedgerClaimCallback {
            override fun sent(noId: Long, index: Long, txHash: String?) {
                onEvent(SnLedgerClaimEvent.Sent(noId, index, txHash ?: ""))
            }

            override fun confirmed(noId: Long, index: Long, txHash: String?, amountRao: Long) {
                onEvent(SnLedgerClaimEvent.Confirmed(noId, index, txHash ?: "", amountRao))
            }

            override fun failed(noId: Long, index: Long, message: String?) {
                onEvent(SnLedgerClaimEvent.Failed(noId, index, message ?: ""))
            }

            override fun done() {
                onEvent(SnLedgerClaimEvent.Done)
            }
        })
    }

    override suspend fun consentStatus(): Result<SnConsentStatusState> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.snNetworkConsentStatus { result, err ->
                if (!continuation.isActive) {
                    return@snNetworkConsentStatus
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty consent status")))
                    else -> continuation.resume(Result.success(SnConsentStatusState(result.active, result.coldkeySs58, result.fromEpoch)))
                }
            }
        }

    // false on a device provisioned with only a client token, and once the server rejected the network's
    override fun hasNetworkCredential(): Boolean = device.api?.hasNetworkCredential() == true

    override suspend fun consentSession(walletId: String, coldkeySs58: String): Result<BittensorProofSession> {
        val api = device.api ?: return Result.failure(IllegalStateException("no api"))
        return startBittensorConsentSession(api, walletId, coldkeySs58)
    }

    override suspend fun consentSubmit(coldkeySs58: String, message: String, signature: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.snNetworkConsentSubmit(coldkeySs58, message, signature) { result, err ->
                if (!continuation.isActive) {
                    return@snNetworkConsentSubmit
                }
                when {
                    err != null -> continuation.resume(Result.failure(err))
                    result == null -> continuation.resume(Result.failure(IllegalStateException("empty consent submit")))
                    // signature_mismatch: a well-formed signature that is not the coldkey's
                    result.error != null -> continuation.resume(
                        Result.failure(SnProtocolException(result.error.code.ifBlank { null }, result.error.message))
                    )
                    else -> continuation.resume(Result.success(Unit))
                }
            }
        }

    override suspend fun claimStatus(): SnClaimStatusState? =
        suspendCancellableCoroutine { continuation ->
            device.snClaimStatus { result, _ ->
                // copied off the gomobile objects here: an exception thrown out
                // of the callback cannot cross JNI and aborts the process
                val status = runCatching { result?.let { snClaimStatusOf(it) } }.getOrNull()
                if (continuation.isActive) {
                    continuation.resume(status)
                }
            }
        }

    override suspend fun claimNow(coldkeySs58: String): SnClaimNowOutcome =
        suspendCancellableCoroutine { continuation ->
            device.snClaimNow(coldkeySs58) { result, err ->
                val outcome = runCatching {
                    when {
                        err != null -> SnClaimNowOutcome.Failed(err.message ?: "")
                        result == null -> SnClaimNowOutcome.Failed("")
                        result.error != null -> SnClaimNowOutcome.Failed("${result.error.code}: ${result.error.message}")
                        result.requested -> SnClaimNowOutcome.Requested
                        else -> SnClaimNowOutcome.Refused(result.reason, result.nextAtMillis)
                    }
                }.getOrElse { SnClaimNowOutcome.Failed(it.message ?: "") }
                if (continuation.isActive) {
                    continuation.resume(outcome)
                }
            }
        }

    override suspend fun ctfWins(): Result<SnCtfWinsAnswer> =
        suspendCancellableCoroutine { continuation ->
            // the sdk asks with the network's credential, and with the client
            // token when that read fails, on a transport whose 401 never signs
            // the user out; an older server's 401 is no wins
            device.snCtfClient { result, err ->
                // copied off the gomobile objects here: an exception thrown out
                // of the callback cannot cross JNI and aborts the process. An
                // error (ctf_unavailable: a read that failed) or an answer that
                // cannot be read is a failure, which keeps the lines drawn. The
                // answer's client is "" for the network's answer, set for one
                // client's (SnCtfWinsLines: a network's answer beats a client's)
                val wins: Result<SnCtfWinsAnswer> = runCatching {
                    when {
                        err != null -> throw err
                        result == null -> throw IllegalStateException("no capture the flag answer")
                        result.error != null -> throw result.error.toException()!!
                        else -> SnCtfWinsAnswer(snCtfWinsOf(result.unclaimedWins), result.clientId.orEmpty())
                    }
                }
                if (continuation.isActive) {
                    continuation.resume(wins)
                }
            }
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

    override suspend fun topMe(): Result<ProtocolTopMe?> =
        suspendCancellableCoroutine { continuation ->
            val api = device.api
            if (api == null) {
                continuation.resume(Result.failure(IllegalStateException("no api")))
                return@suspendCancellableCoroutine
            }
            api.snTopMe { result, err ->
                // copied off the gomobile objects here: an exception thrown out of
                // the callback cannot cross JNI and aborts the process
                val answer: Result<ProtocolTopMe?> = runCatching {
                    if (err != null) {
                        throw err
                    }
                    result?.let(::protocolTopMeOf)
                }
                if (continuation.isActive) {
                    continuation.resume(answer)
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
