package com.bringyour.network.ui.wallet

import com.bringyour.network.utils.SolanaAddress
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * Legacy USDC payout wallets.
 *
 * USDC payouts continue until the migration to Bittensor completes, so a network that
 * is owed USDC keeps a way to connect the Solana wallet it is paid to. The payout
 * wallet is a server-side account wallet; it reaches the app only through
 * [LegacyWalletSource], the seam to the SDK.
 */

/** The chains a USDC payout wallet can be on. Bittensor (TAO) rows are never payout wallets. */
enum class LegacyChain(val sdkValue: String) {
    SOLANA("SOL"),
    POLYGON("MATIC");

    companion object {
        // the spellings the server's ParseBlockchain accepts; TAO and anything else is not a payout chain
        fun fromSdk(value: String): LegacyChain? = when (value.trim().uppercase(Locale.US)) {
            "SOL", "SOLANA" -> SOLANA
            "MATIC", "POLY", "POLYGON" -> POLYGON
            else -> null
        }
    }
}

data class LegacyWallet(
    val walletId: String,
    val address: String,
    val chain: LegacyChain,
    val hasSeekerToken: Boolean,
) {
    companion object {
        /**
         * One `GET /account/wallets` row as a legacy payout wallet, or null for a row that
         * is not one: a Circle programmable wallet, a Bittensor (TAO) or unknown chain, or
         * a row without an id.
         */
        fun fromRow(
            walletId: String?,
            circleWalletId: String?,
            blockchain: String,
            address: String,
            hasSeekerToken: Boolean,
        ): LegacyWallet? {
            if (walletId.isNullOrBlank() || !circleWalletId.isNullOrEmpty()) {
                return null
            }
            val chain = LegacyChain.fromSdk(blockchain) ?: return null
            return LegacyWallet(walletId, address.trim(), chain, hasSeekerToken)
        }
    }
}

/** One `GET /account/payments` row. A held payment (no payout wallet when planned) has no wallet. */
data class LegacyPayment(
    val paymentId: String,
    val walletId: String?,
    val payoutUsd: Double,
    val tokenAmount: Double,
    val completed: Boolean,
    val canceled: Boolean,
    val completeMillis: Long?,
)

data class LegacyWalletUi(
    val wallets: List<LegacyWallet> = emptyList(),
    val payoutWalletId: String? = null,
    val payments: List<LegacyPayment> = emptyList(),
) {
    /**
     * The connected wallet: the Solana (or Polygon) wallet the server pays USDC to. A
     * Solana row that is not the payout wallet, such as the row a Seeker verification
     * leaves, is not connected.
     */
    val payoutWallet: LegacyWallet?
        get() = payoutWalletId?.let { id -> wallets.firstOrNull { it.walletId == id } }

    /** USDC not sent yet: every payment neither completed nor canceled, held ones included */
    val pendingUsd: Double
        get() = payments.filter { !it.completed && !it.canceled }.sumOf { it.payoutUsd }

    /** true when the pending figure reads at least 0.01 with two decimals */
    val hasPending: Boolean
        get() = MIN_SHOWN_PENDING_USD <= pendingUsd

    companion object {
        // anything less would read "0.00 USDC waiting"
        const val MIN_SHOWN_PENDING_USD = 0.005
    }
}

/** The connect, link and remove flow of the Solana payout wallet. */
sealed class SolanaConnectState {
    object Idle : SolanaConnectState()
    // the wallet app (Mobile Wallet Adapter) is in front
    object ConnectingApp : SolanaConnectState()
    data class Validating(val address: String) : SolanaConnectState()
    data class Linking(val address: String) : SolanaConnectState()
    data class Linked(val wallet: LegacyWallet) : SolanaConnectState()
    object NoWalletApp : SolanaConnectState()
    data class Failed(val detail: String?) : SolanaConnectState()
    object Removing : SolanaConnectState()
    object Removed : SolanaConnectState()

    val busy: Boolean
        get() = this is ConnectingApp || this is Validating || this is Linking || this is Removing
}

enum class SolanaSheetStep {
    CHOOSE,
    MANUAL,
}

class LegacyWalletUnavailableException(message: String = "No signed-in device") : Exception(message)

/**
 * The SDK seam for legacy payout wallets. Every call is one URnetwork API request; ids
 * cross the seam as strings.
 */
interface LegacyWalletSource {

    /** false without a device; nothing legacy renders */
    val available: Boolean

    /** `GET /account/wallets`, legacy payout wallets only (see [LegacyWallet.fromRow]) */
    suspend fun wallets(): Result<List<LegacyWallet>>

    /** `GET /account/payout-wallet`; null when the network has none */
    suspend fun payoutWalletId(): Result<String?>

    /** `GET /account/payments` */
    suspend fun payments(): Result<List<LegacyPayment>>

    /** local syntax check; never touches the network */
    fun validateSolanaSyntax(address: String): Boolean

    /** `POST /wallet/validate-address` for chain SOL */
    suspend fun validateAddress(address: String): Result<Boolean>

    /**
     * `POST /account/wallet` for a Solana USDC wallet; returns the wallet id. The server
     * stores an address once per network and sets the payout wallet only when the
     * network has none.
     */
    suspend fun addSolanaWallet(address: String): Result<String>

    /** `POST /account/payout-wallet` */
    suspend fun setPayoutWallet(walletId: String): Result<Unit>

    /** `POST /account/wallets/remove`: deactivates the wallet and clears it as the payout wallet */
    suspend fun removeWallet(walletId: String): Result<Unit>
}

/**
 * Create (or re-activate) the Solana wallet, then make it the payout wallet unless the
 * server already did. The server sets the payout wallet on create only when the network
 * has none, and a network can hold another payout wallet or a Seeker verification row;
 * a payout wallet that cannot be read is set anyway, which the server accepts again.
 */
suspend fun linkSolanaWallet(source: LegacyWalletSource, address: String): Result<String> {
    val walletId = source.addSolanaWallet(address).getOrElse { return Result.failure(it) }
    if (source.payoutWalletId().getOrNull() != walletId) {
        source.setPayoutWallet(walletId).onFailure { return Result.failure(it) }
    }
    return Result.success(walletId)
}

/** No device (signed out): nothing legacy. */
object NoLegacyWalletSource : LegacyWalletSource {
    override val available: Boolean = false
    override suspend fun wallets(): Result<List<LegacyWallet>> = Result.success(emptyList())
    override suspend fun payoutWalletId(): Result<String?> = Result.success(null)
    override suspend fun payments(): Result<List<LegacyPayment>> = Result.success(emptyList())
    override fun validateSolanaSyntax(address: String): Boolean = SolanaAddress.isValidSyntax(address)
    override suspend fun validateAddress(address: String): Result<Boolean> =
        Result.failure(LegacyWalletUnavailableException())
    override suspend fun addSolanaWallet(address: String): Result<String> =
        Result.failure(LegacyWalletUnavailableException())
    override suspend fun setPayoutWallet(walletId: String): Result<Unit> =
        Result.failure(LegacyWalletUnavailableException())
    override suspend fun removeWallet(walletId: String): Result<Unit> =
        Result.failure(LegacyWalletUnavailableException())
}

/**
 * Debug-only in-memory legacy wallets (Developer screen switches), so the connect,
 * manual entry, card and remove states can be exercised without a wallet app or a
 * USDC balance. Behaves like the server: the USDC mint is not a valid payout address,
 * an address is stored once, and create sets the payout wallet only when there is none.
 */
class SampleLegacyWalletSource(
    startConnected: Boolean,
    pendingUsd: Double,
    // how long each call takes, like a round trip; tests pass 0
    private val delayMillis: Long = 600,
) : LegacyWalletSource {

    private val lock = Any()
    private val now = System.currentTimeMillis()
    private val dayMillis = 24L * 60 * 60 * 1000
    private var nextWalletNumber = 1

    private val walletList = mutableListOf<LegacyWallet>()
    private var payoutId: String? = null
    private val paymentList = mutableListOf<LegacyPayment>()

    init {
        if (startConnected) {
            walletList.add(LegacyWallet(SAMPLE_WALLET_ID, SAMPLE_SOLANA_ADDRESS, LegacyChain.SOLANA, hasSeekerToken = false))
            payoutId = SAMPLE_WALLET_ID
            paymentList.add(
                LegacyPayment("sample-payment-paid", SAMPLE_WALLET_ID, 12.40, 12.40, true, false, now - 7 * dayMillis)
            )
        }
        if (0.0 < pendingUsd) {
            // planned while the network had no payout wallet, so held
            paymentList.add(LegacyPayment("sample-payment-held", null, pendingUsd, 0.0, false, false, null))
        }
    }

    override val available: Boolean = true

    override suspend fun wallets(): Result<List<LegacyWallet>> {
        delay(delayMillis)
        return Result.success(synchronized(lock) { walletList.toList() })
    }

    override suspend fun payoutWalletId(): Result<String?> {
        delay(delayMillis)
        return Result.success(synchronized(lock) { payoutId })
    }

    override suspend fun payments(): Result<List<LegacyPayment>> {
        delay(delayMillis)
        return Result.success(synchronized(lock) { paymentList.toList() })
    }

    override fun validateSolanaSyntax(address: String): Boolean = SolanaAddress.isValidSyntax(address)

    override suspend fun validateAddress(address: String): Result<Boolean> {
        delay(delayMillis)
        return Result.success(isServerValid(address.trim()))
    }

    override suspend fun addSolanaWallet(address: String): Result<String> {
        delay(delayMillis)
        val a = address.trim()
        if (!isServerValid(a)) {
            return Result.failure(IllegalArgumentException("invalid wallet address"))
        }
        val walletId = synchronized(lock) {
            val id = walletList.firstOrNull { it.address == a }?.walletId
                ?: "sample-wallet-${nextWalletNumber++}".also {
                    walletList.add(LegacyWallet(it, a, LegacyChain.SOLANA, hasSeekerToken = false))
                }
            if (payoutId == null) {
                payoutId = id
            }
            id
        }
        return Result.success(walletId)
    }

    override suspend fun setPayoutWallet(walletId: String): Result<Unit> {
        delay(delayMillis)
        return synchronized(lock) {
            if (walletList.none { it.walletId == walletId }) {
                Result.failure(IllegalStateException("Wallet must be an active wallet owned by the network."))
            } else {
                payoutId = walletId
                Result.success(Unit)
            }
        }
    }

    override suspend fun removeWallet(walletId: String): Result<Unit> {
        delay(delayMillis)
        return synchronized(lock) {
            if (!walletList.removeAll { it.walletId == walletId }) {
                Result.failure(IllegalStateException("The wallet was not removed."))
            } else {
                if (payoutId == walletId) {
                    payoutId = null
                }
                Result.success(Unit)
            }
        }
    }

    private fun isServerValid(address: String): Boolean =
        SolanaAddress.isValidSyntax(address) && address != USDC_MINT

    companion object {
        const val SAMPLE_WALLET_ID = "sample-wallet-0"
        const val SAMPLE_SOLANA_ADDRESS = "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM"

        // the server refuses the USDC mint as a payout address
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

        // the "3.87 USDC waiting" email
        const val SAMPLE_USDC_WAITING = 3.87
    }
}
