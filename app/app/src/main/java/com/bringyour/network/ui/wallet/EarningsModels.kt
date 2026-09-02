package com.bringyour.network.ui.wallet

import com.bringyour.network.utils.Ss58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * Points-first earnings model.
 *
 * Points are URnetwork's own system and are always the headline. The UR protocol
 * (SN25α on the Bittensor subnet) is an opt-in layer that appears once a coldkey is
 * connected. Everything protocol-side reaches the app through
 * [EarningsProtocolSource], which is the only seam to the SDK.
 */

/** The coldkey alpha settles to; attached to this device's client id. */
data class SnWalletState(
    val coldkeySs58: String,
    val clientId: String?,
    val setAtMillis: Long,
)

/** The SDK-generated EVM key that only ever pays gas. Fund it by sending TAO to the mirror. */
data class SnGasKeyState(
    val address: String,
    val mirrorSs58: String,
)

data class SnGasBalanceState(
    val wei: String,
    val tao: Double,
)

enum class EpochClaimStatus {
    OPEN,
    CLAIMABLE,
    CLAIMED,
    EXPIRED,
    NOT_FINALIZED;

    companion object {
        fun fromString(value: String): EpochClaimStatus = when (value.lowercase()) {
            "open" -> OPEN
            "claimable" -> CLAIMABLE
            "claimed" -> CLAIMED
            "expired" -> EXPIRED
            else -> NOT_FINALIZED
        }
    }
}

data class EpochClaim(
    val epoch: Long,
    val shareBps: Long,
    val amountRao: Long,
    val status: EpochClaimStatus,
    val claimOpenBlock: Long,
    val expiryBlock: Long,
    val txHash: String?,
)

data class ClaimsSnapshot(
    val claims: List<EpochClaim>,
    val totalClaimableRao: Long,
)

/** One finalized epoch of the network's points history. */
data class AccountEpoch(
    val epoch: Long,
    val startMillis: Long,
    val endMillis: Long,
    val points: Double,
    val shareBps: Long,
)

/** Head miner (Top 200) eligibility and binding for this network. */
data class SnHeadState(
    val eligible: Boolean,
    val score: Double,
    val floor: Double,
    val rankEstimate: Long,
    val cutoff: Long,
    val bound: Boolean,
    val hotkey: String?,
    val uid: Long,
    val rank: Long,
    val epoch: Long,
    val source: String,
) {
    /** bound and within 15% of the eviction floor */
    val nearFloor: Boolean
        get() = bound && 0.0 < floor && score < floor * 1.15
}

data class WalletValidation(
    val validSyntax: Boolean,
    val existsOnChain: Boolean,
    val banned: Boolean,
    val message: String?,
)

sealed class ClaimEvent {
    data class Sent(val epoch: Long, val txHash: String) : ClaimEvent()
    data class Confirmed(val epoch: Long, val txHash: String, val amountRao: Long) : ClaimEvent()
    data class Failed(val epoch: Long, val message: String) : ClaimEvent()
    object Done : ClaimEvent()
}

class EarningsUnavailableException(message: String = "The UR protocol is not available in this build yet") :
    Exception(message)

/**
 * The SDK seam. Wallet, gas key and claims live in the SDK on the device; claims go
 * directly from the SDK to the settlement vault contract (no URnetwork API in the
 * alpha path). Points history and head eligibility come from the URnetwork API.
 */
interface EarningsProtocolSource {

    /** false while the SDK surface is not linked; the UI keeps points-only */
    val available: Boolean

    fun currentWallet(): SnWalletState?

    /** returns the remover */
    fun addWalletListener(listener: (SnWalletState?) -> Unit): () -> Unit

    /** local syntax check; never touches the network */
    fun validateSs58(address: String): Boolean

    /** unauthenticated server check: exists on chain, banned */
    suspend fun validateWallet(address: String): Result<WalletValidation>

    suspend fun fetchWallet(): Result<SnWalletState?>

    suspend fun connectWallet(address: String, signature: String, message: String): Result<SnWalletState>

    fun gasKey(): SnGasKeyState?

    suspend fun gasBalance(): Result<SnGasBalanceState>

    suspend fun claims(): Result<ClaimsSnapshot>

    /** events arrive on an arbitrary thread */
    fun claim(epochs: List<Long>, onEvent: (ClaimEvent) -> Unit)

    suspend fun accountEpochs(): Result<List<AccountEpoch>>

    suspend fun head(): Result<SnHeadState?>

    fun formatAlpha(rao: Long): String

    fun formatShareBps(shareBps: Long): String

    fun shortSs58(address: String): String

    fun explorerTxUrl(txHash: String): String
}

object EarningsFormat {
    const val RAO_PER_ALPHA = 1_000_000_000.0

    // mirrors the non-translatable `sn_alpha_symbol` store key
    const val ALPHA_SYMBOL = "SN25α"

    const val EXPLORER_TX_URL = "https://evm.taostats.io/tx/%s"

    fun alpha(rao: Long): String =
        String.format(Locale.US, "%.4f %s", rao / RAO_PER_ALPHA, ALPHA_SYMBOL)

    fun shareBps(shareBps: Long): String =
        String.format(Locale.US, "%.2f%%", shareBps / 100.0)

    fun points(points: Double): String =
        String.format(Locale.US, "%,.0f", points)

    fun tao(tao: Double): String =
        String.format(Locale.US, "%.4f", tao)
}

/** No device (signed out): points-only, nothing protocol-side. */
object NoProtocolSource : EarningsProtocolSource {
    override val available: Boolean = false
    override fun currentWallet(): SnWalletState? = null
    override fun addWalletListener(listener: (SnWalletState?) -> Unit): () -> Unit = {}
    override fun validateSs58(address: String): Boolean = Ss58.isValidSyntax(address)
    override suspend fun validateWallet(address: String): Result<WalletValidation> =
        Result.failure(EarningsUnavailableException())
    override suspend fun fetchWallet(): Result<SnWalletState?> = Result.success(null)
    override suspend fun connectWallet(address: String, signature: String, message: String): Result<SnWalletState> =
        Result.failure(EarningsUnavailableException())
    override fun gasKey(): SnGasKeyState? = null
    override suspend fun gasBalance(): Result<SnGasBalanceState> = Result.failure(EarningsUnavailableException())
    override suspend fun claims(): Result<ClaimsSnapshot> = Result.failure(EarningsUnavailableException())
    override fun claim(epochs: List<Long>, onEvent: (ClaimEvent) -> Unit) {
        epochs.forEach { onEvent(ClaimEvent.Failed(it, EarningsUnavailableException().message ?: "")) }
        onEvent(ClaimEvent.Done)
    }
    override suspend fun accountEpochs(): Result<List<AccountEpoch>> = Result.success(emptyList())
    override suspend fun head(): Result<SnHeadState?> = Result.success(null)
    override fun formatAlpha(rao: Long): String = EarningsFormat.alpha(rao)
    override fun formatShareBps(shareBps: Long): String = EarningsFormat.shareBps(shareBps)
    override fun shortSs58(address: String): String = Ss58.short(address)
    override fun explorerTxUrl(txHash: String): String = String.format(EarningsFormat.EXPLORER_TX_URL, txHash)
}

/**
 * Debug-only in-memory protocol data (Developer screen switch) so the wallet,
 * unclaimed, claim dialog and Top 200 states can be exercised without a chain.
 */
class SampleProtocolSource(
    private val gasUnfunded: Boolean,
    startConnected: Boolean = true,
) : EarningsProtocolSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = mutableListOf<(SnWalletState?) -> Unit>()
    private val now = System.currentTimeMillis()
    private val dayMillis = 24L * 60 * 60 * 1000

    private val wallet = MutableStateFlow(
        if (startConnected) SnWalletState(SAMPLE_COLDKEY, "sample-client", now - 9 * dayMillis) else null
    )

    private val claimList = mutableListOf(
        EpochClaim(1_218, 71, 3_241_000_000, EpochClaimStatus.CLAIMABLE, 5_012_000, 5_112_000, null),
        EpochClaim(1_217, 64, 2_905_500_000, EpochClaimStatus.CLAIMABLE, 5_004_800, 5_104_800, null),
        EpochClaim(1_216, 58, 2_640_000_000, EpochClaimStatus.CLAIMED, 4_997_600, 5_097_600, "0x8f1c3a0e5d2b7c9a4e6f1d2c3b4a5968778899aabbccddeeff00112233445566"),
        EpochClaim(1_215, 40, 1_812_250_000, EpochClaimStatus.EXPIRED, 4_990_400, 5_090_400, null),
        EpochClaim(1_219, 0, 0, EpochClaimStatus.OPEN, 5_019_200, 5_119_200, null),
    )

    override val available: Boolean = true

    override fun currentWallet(): SnWalletState? = wallet.value

    override fun addWalletListener(listener: (SnWalletState?) -> Unit): () -> Unit {
        synchronized(listeners) { listeners.add(listener) }
        return { synchronized(listeners) { listeners.remove(listener) } }
    }

    override fun validateSs58(address: String): Boolean = Ss58.isValidSyntax(address)

    override suspend fun validateWallet(address: String): Result<WalletValidation> {
        delay(600)
        val a = address.trim()
        return Result.success(
            WalletValidation(
                validSyntax = Ss58.isValidSyntax(a),
                existsOnChain = !a.endsWith("new", ignoreCase = true) && a != SAMPLE_NEW_COLDKEY,
                banned = a == SAMPLE_BANNED_COLDKEY,
                message = null,
            )
        )
    }

    override suspend fun fetchWallet(): Result<SnWalletState?> = Result.success(wallet.value)

    override suspend fun connectWallet(address: String, signature: String, message: String): Result<SnWalletState> {
        delay(900)
        val w = SnWalletState(address.trim(), "sample-client", System.currentTimeMillis())
        wallet.value = w
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it(w) }
        return Result.success(w)
    }

    override fun gasKey(): SnGasKeyState = SnGasKeyState(SAMPLE_GAS_ADDRESS, SAMPLE_GAS_MIRROR)

    override suspend fun gasBalance(): Result<SnGasBalanceState> {
        delay(300)
        return Result.success(
            if (gasUnfunded) SnGasBalanceState("0", 0.0) else SnGasBalanceState("124000000000000000", 0.124)
        )
    }

    override suspend fun claims(): Result<ClaimsSnapshot> {
        delay(400)
        if (wallet.value == null) {
            return Result.success(ClaimsSnapshot(emptyList(), 0))
        }
        val list = synchronized(claimList) { claimList.sortedByDescending { it.epoch } }
        return Result.success(
            ClaimsSnapshot(list, list.filter { it.status == EpochClaimStatus.CLAIMABLE }.sumOf { it.amountRao })
        )
    }

    override fun claim(epochs: List<Long>, onEvent: (ClaimEvent) -> Unit) {
        scope.launch {
            if (gasUnfunded) {
                delay(700)
                epochs.forEach { onEvent(ClaimEvent.Failed(it, "needs_gas: insufficient funds")) }
                onEvent(ClaimEvent.Done)
                return@launch
            }
            for (epoch in epochs) {
                val claim = synchronized(claimList) { claimList.firstOrNull { it.epoch == epoch } }
                if (claim == null || claim.status != EpochClaimStatus.CLAIMABLE) {
                    onEvent(ClaimEvent.Failed(epoch, "claims_for_epoch_expired: ClaimExpired"))
                    continue
                }
                delay(900)
                val txHash = "0x" + String.format(Locale.US, "%064x", epoch * 7_919L + 0x5a5a5aL)
                onEvent(ClaimEvent.Sent(epoch, txHash))
                delay(1_600)
                synchronized(claimList) {
                    val i = claimList.indexOfFirst { it.epoch == epoch }
                    if (0 <= i) {
                        claimList[i] = claimList[i].copy(status = EpochClaimStatus.CLAIMED, txHash = txHash)
                    }
                }
                onEvent(ClaimEvent.Confirmed(epoch, txHash, claim.amountRao))
            }
            onEvent(ClaimEvent.Done)
        }
    }

    override suspend fun accountEpochs(): Result<List<AccountEpoch>> {
        delay(350)
        val epochs = (0 until 8).map { i ->
            val epoch = 1_219L - i
            val end = now - i * dayMillis
            AccountEpoch(
                epoch = epoch,
                startMillis = end - dayMillis,
                endMillis = end,
                points = listOf(1_840.0, 2_210.0, 1_975.0, 1_620.0, 1_388.0, 990.0, 1_144.0, 730.0)[i],
                shareBps = listOf(0L, 71L, 64L, 58L, 40L, 33L, 37L, 21L)[i],
            )
        }
        return Result.success(epochs)
    }

    override suspend fun head(): Result<SnHeadState?> {
        delay(300)
        return Result.success(
            SnHeadState(
                eligible = true,
                score = 812.0,
                floor = 640.0,
                rankEstimate = 143,
                cutoff = 200,
                bound = false,
                hotkey = null,
                uid = 0,
                rank = 0,
                epoch = 1_219,
                source = "server",
            )
        )
    }

    override fun formatAlpha(rao: Long): String = EarningsFormat.alpha(rao)
    override fun formatShareBps(shareBps: Long): String = EarningsFormat.shareBps(shareBps)
    override fun shortSs58(address: String): String = Ss58.short(address)
    override fun explorerTxUrl(txHash: String): String = String.format(EarningsFormat.EXPLORER_TX_URL, txHash)

    companion object {
        const val SAMPLE_COLDKEY = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        const val SAMPLE_NEW_COLDKEY = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
        const val SAMPLE_BANNED_COLDKEY = "5FLSigC9HGRKVhB9FiEo4Y3koPsNmBmLJbpXg2mp1hXcS59Y"
        const val SAMPLE_GAS_ADDRESS = "0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a59687766554433221100"
        const val SAMPLE_GAS_MIRROR = "5DAAnrj7VHTznn2AWBemMuyBwZWs6FNFjdyVXUeYum3PTXFy"
    }
}

/** Debug switches (Developer screen). Read when the earnings view model binds a device. */
object EarningsDebugFlags {
    val version = MutableStateFlow(0)

    @Volatile var useSampleData: Boolean = false
        private set

    @Volatile var sampleGasUnfunded: Boolean = false
        private set

    // start the sample without a wallet, to exercise the connect and manual-entry flows
    @Volatile var sampleStartDisconnected: Boolean = false
        private set

    fun setUseSampleData(value: Boolean) {
        useSampleData = value
        version.value += 1
    }

    fun setSampleGasUnfunded(value: Boolean) {
        sampleGasUnfunded = value
        version.value += 1
    }

    fun setSampleStartDisconnected(value: Boolean) {
        sampleStartDisconnected = value
        version.value += 1
    }
}
