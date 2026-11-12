package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * Settlement v2, shown beside the v1 claims, which stay as they are. From its
 * switch epoch an operator pool settles in vault2: the coldkey has one account
 * per pool, a cumulative balance that never expires. The SDK reads the
 * accounts from the operator's ledger (SnLedgerAccounts) and claims them from
 * the device's gas key (SnLedgerClaim) once the release configures vault2's
 * address. Balances that cannot be read are unavailable, never zero.
 *
 * From epoch 8 an operator has a second pool, capture the flag's (sn
 * CAPTURETHEFLAG.md §10). Its account is claimed from vault2 like the pool's
 * and shows as its own row, named by its kind; one claim takes both, in one
 * transaction (the sdk batches it). A pool the ledger could not read just now
 * is unavailable in its own row, beside the others.
 */

/** A pool's kind for an entry that names none, as the sdk reads it: capture the flag's ids start at 2^32. */
fun snLedgerPoolKindOf(noId: Long): String =
    if (Sdk.SnLedgerCtfPoolIdBase <= noId) Sdk.SnLedgerPoolKindCtf else Sdk.SnLedgerPoolKindPool

/** One of the operator's v2 pools: its no_id and its kind (sdk SnLedgerPoolKind*). */
data class SnLedgerPool(
    val noId: Long,
    val kind: String = snLedgerPoolKindOf(noId),
) {
    val isCtf: Boolean
        get() = kind == Sdk.SnLedgerPoolKindCtf
}

/** The coldkey's v2 account in one operator pool, as the SDK's SnLedgerAccount. Amounts are rao. */
data class SnLedgerAccountState(
    val noId: Long,
    val index: Long,
    // the cumulative in the latest finalized root
    val finalizedRao: Long,
    // finalized less paid: what a claim would transfer now
    val claimableRao: Long,
    // already transferred to the coldkey
    val paidRao: Long,
    // a committed root that is not finalized yet holds the account, at pendingRao
    val pending: Boolean,
    val pendingRao: Long,
    // sdk SnLedgerPoolKind*: the operator's pool or its capture the flag pool.
    // The sdk reads an entry of a server that predates kinds as a pool
    val kind: String = snLedgerPoolKindOf(noId),
) {
    val pool: SnLedgerPool
        get() = SnLedgerPool(noId, kind)
}

/** One read of the coldkey's v2 accounts (sdk SnLedgerAccountsResult). */
data class SnLedgerAccounts(
    // one per pool the ledger could read and holds an account of the coldkey
    val accounts: List<SnLedgerAccountState>,
    // the pools it could not read just now (sdk UnavailablePools); whether the
    // coldkey has an account there is unknown
    val unavailablePools: List<SnLedgerPool> = emptyList(),
)

/** The v2 accounts after a read. */
sealed class SnLedgerLoad {
    object Loading : SnLedgerLoad()

    /** the ledger could not be read or its answer was refused (ledger_unavailable) */
    object Unavailable : SnLedgerLoad()

    /**
     * one account per pool; empty when the ledger has none for the coldkey.
     * A pool the ledger could not read is unavailable, beside the others
     */
    data class Loaded(
        val accounts: List<SnLedgerAccountState>,
        val unavailablePools: List<SnLedgerPool> = emptyList(),
    ) : SnLedgerLoad()
}

/** Every pool of a read, which names its rows (snLedgerPoolName); none before one. */
fun snLedgerPools(load: SnLedgerLoad): List<SnLedgerPool> =
    (load as? SnLedgerLoad.Loaded)?.let { loaded -> loaded.accounts.map { it.pool } + loaded.unavailablePools }.orEmpty()

// ---- pool names

/**
 * What a pool's row is called (CAPTURETHEFLAG.md §10): capture the flag's
 * account by its own name, and the one operator pool beside it as the provider
 * pool. Every other pool, and every pool while no capture the flag pool is in
 * view, keeps "Pool {no_id}", as before capture the flag.
 */
sealed class SnLedgerPoolName {
    /** sn_ledger_pool */
    data class Pool(val noId: Long) : SnLedgerPoolName()

    /** sn_ledger_provider_pool */
    object ProviderPool : SnLedgerPoolName()

    /** sn_ctf_pool */
    object CaptureTheFlag : SnLedgerPoolName()
}

/** The name of [pool] among the pools in view, which include it. */
fun snLedgerPoolName(pool: SnLedgerPool, inView: Collection<SnLedgerPool>): SnLedgerPoolName {
    if (pool.isCtf) {
        return SnLedgerPoolName.CaptureTheFlag
    }
    val ctfInView = inView.any { it.isCtf }
    val operatorPools = (inView + pool).filter { !it.isCtf }.map { it.noId }.distinct()
    return if (ctfInView && operatorPools.size == 1) SnLedgerPoolName.ProviderPool else SnLedgerPoolName.Pool(pool.noId)
}

/**
 * Each row says which pool it is once there is more than one, or one of
 * capture the flag's; a lone operator pool needs no name, as before.
 */
fun snLedgerNamesPools(pools: Collection<SnLedgerPool>): Boolean =
    1 < pools.size || pools.any { it.isCtf }

/**
 * Claim both (sn_ledger_claim_both): the one claim takes the operator pool's
 * account and capture the flag's together, in one transaction, while exactly
 * those two have something to claim.
 */
fun snLedgerClaimsBoth(claimable: Collection<SnLedgerPool>): Boolean =
    claimable.size == 2 && claimable.count { it.isCtf } == 1

/**
 * The claim relay's transfer floor, in rao of α at its last price (0 without
 * one), as the screens apply it. Below the claim minimum is capture the flag's
 * status, for accounts often that small: it shows while a capture the flag
 * pool is in view, and a server without one reads as before. The floor is an
 * estimate at the relay's last price, so it names a status and the Claim both
 * label and never stops a claim, whose own preflight decides.
 */
fun snLedgerTransferFloor(pools: Collection<SnLedgerPool>, transferFloorRao: Long): Long =
    if (pools.any { it.isCtf }) transferFloorRao else 0

/**
 * The accounts with something to claim at or above the transfer floor
 * (snLedgerTransferFloor, 0 for none): what Claim both counts. One under it
 * reads Below the claim minimum.
 */
fun snLedgerClaimTakes(accounts: List<SnLedgerAccountState>, transferFloorRao: Long): List<SnLedgerAccountState> =
    accounts.filter { 0 < it.claimableRao && (transferFloorRao <= 0 || transferFloorRao <= it.claimableRao) }

// ---- the tile

/** One account in the Settlement v2 tile: Claimable first, then Paid and Earned. */
data class SnLedgerAccountLines(
    val noId: Long,
    val claimableRao: Long,
    val paidRao: Long,
    val earnedRao: Long,
    // "Pending: +X, awaiting finalization", X = pending less finalized; null when nothing is pending
    val pendingIncreaseRao: Long?,
    // sdk SnLedgerPoolKind*
    val kind: String = snLedgerPoolKindOf(noId),
    // Below the claim minimum: something to claim, under the runtime's transfer
    // floor at the relay's last price, so no claim can move it yet
    val underFloor: Boolean = false,
) {
    val pool: SnLedgerPool
        get() = SnLedgerPool(noId, kind)
}

/** What the Settlement v2 tile shows. */
data class SnLedgerTile(
    // null while loading and when unavailable
    val accounts: List<SnLedgerAccountLines>?,
    val unavailable: Boolean,
    // the Claim action, once vault2's address is configured
    val showClaim: Boolean,
    val claimEnabled: Boolean,
    // a network with no wallet on this device: what the claim relay says is
    // claimable for its consent, in place of the accounts (snLedgerTileWithRelay)
    val relayClaimableRao: Long? = null,
    // the pools the ledger could not read just now: a row each, "Unavailable
    // right now", never zero, under the accounts
    val unavailablePools: List<SnLedgerPool> = emptyList(),
    // the Claim action reads Claim both (snLedgerClaimsBoth)
    val claimBoth: Boolean = false,
) {
    /** every pool the tile shows a row of, which names them */
    val pools: List<SnLedgerPool>
        get() = accounts.orEmpty().map { it.pool } + unavailablePools
}

fun SnLedgerAccountState.lines() = SnLedgerAccountLines(
    noId = noId,
    claimableRao = claimableRao,
    paidRao = paidRao,
    earnedRao = finalizedRao,
    pendingIncreaseRao = (pendingRao - finalizedRao).takeIf { pending && 0 < it },
    kind = kind,
)

/**
 * The tile, or null to hide it. Before vault2's address is configured (before
 * activation) there is no Claim, and the tile shows only balances the ledger
 * returned. Once it is configured the tile is always there: a spinner while
 * loading, "Unavailable" when the read failed, and Claim enabled only while
 * the loaded accounts have something to claim. A pool the ledger could not
 * read shows as unavailable in its own row, and never hides the others.
 */
fun snLedgerTile(load: SnLedgerLoad, claimConfigured: Boolean): SnLedgerTile? = when (load) {
    SnLedgerLoad.Loading ->
        if (claimConfigured) SnLedgerTile(null, unavailable = false, showClaim = true, claimEnabled = false) else null
    SnLedgerLoad.Unavailable ->
        if (claimConfigured) SnLedgerTile(null, unavailable = true, showClaim = true, claimEnabled = false) else null
    is SnLedgerLoad.Loaded ->
        if (!claimConfigured && load.accounts.isEmpty()) {
            null
        } else {
            SnLedgerTile(
                accounts = load.accounts.map { it.lines() },
                unavailable = false,
                showClaim = claimConfigured,
                claimEnabled = claimConfigured && 0 < load.accounts.sumOf { it.claimableRao },
                unavailablePools = load.unavailablePools,
                claimBoth = claimConfigured && snLedgerClaimsBoth(load.accounts.filter { 0 < it.claimableRao }.map { it.pool }),
            )
        }
}

/**
 * The tile with the claim relay's transfer floor, in rao of α at its last
 * price (0 without one), while a capture the flag pool is in view
 * (snLedgerTransferFloor): an account with something to claim under it reads
 * Below the claim minimum, and Claim both needs both accounts above it.
 * Claim itself stays: the claim's own preflight decides.
 */
fun SnLedgerTile.withTransferFloor(transferFloorRao: Long): SnLedgerTile {
    val floor = snLedgerTransferFloor(pools, transferFloorRao)
    if (floor <= 0 || accounts == null) {
        return this
    }
    val lines = accounts.map { it.copy(underFloor = 0 < it.claimableRao && it.claimableRao < floor) }
    return copy(
        accounts = lines,
        claimBoth = claimBoth && snLedgerClaimsBoth(lines.filter { 0 < it.claimableRao && !it.underFloor }.map { it.pool }),
    )
}

/**
 * The capture the flag slot this device won last (sdk SnCtfWin, from GET
 * /sn/ctf/client): its role, sdk SnCtfRole*, and its country, a lowercase
 * ISO 3166-1 code.
 */
data class SnCtfWinState(
    val role: String,
    val country: String,
)

/**
 * The tile's line for a win: one of the country's five slots of its role,
 * never the flag, which takes all ten. The role is in the key, since a
 * translated role word cannot be dropped into one sentence. Null, no line, for
 * a role this build does not know or a win without a country.
 */
fun snCtfWonRes(win: SnCtfWinState): Int? = when {
    win.country.isBlank() -> null
    win.role == Sdk.SnCtfRoleProvider -> R.string.sn_ctf_won_provider_slot
    win.role == Sdk.SnCtfRoleExtender -> R.string.sn_ctf_won_extender_slot
    else -> null
}

// ---- claim

/** SnLedgerClaimCallback's progress; events arrive on an arbitrary thread. */
sealed class SnLedgerClaimEvent {
    data class Sent(val noId: Long, val index: Long, val txHash: String) : SnLedgerClaimEvent()
    data class Confirmed(val noId: Long, val index: Long, val txHash: String, val amountRao: Long) : SnLedgerClaimEvent()
    data class Failed(val noId: Long, val index: Long, val message: String) : SnLedgerClaimEvent()
    object Done : SnLedgerClaimEvent()
}

enum class SnLedgerClaimStatus {
    PENDING,
    SENT,
    CONFIRMED,
    // a third party claimed the account first; vault2 paid the same coldkey
    ALREADY_CLAIMED,
    FAILED,
    // not a failure of the claim, which could not take the account: its
    // balance is under the runtime's transfer floor (not_claimable,
    // AmountTooLow), Below the claim minimum, and waits in vault2
    UNDER_FLOOR,
    // its pool's ledger could not be read just now (ledger_unavailable for
    // the pool): Unavailable right now, and the other pools were claimed
    POOL_UNAVAILABLE,
}

/**
 * One account of a claim. With two or more the SDK sends them in one batch
 * transaction, and each account's row carries that transaction.
 */
data class SnLedgerClaimProgress(
    val noId: Long,
    val index: Long,
    // the claimable when the claim starts; the vault's Claimed amount once confirmed
    val amountRao: Long,
    val status: SnLedgerClaimStatus,
    val txHash: String?,
    // the SDK's "<code>: <detail>"
    val message: String?,
    // sdk SnLedgerPoolKind*, which names the row
    val kind: String = snLedgerPoolKindOf(noId),
) {
    val pool: SnLedgerPool
        get() = SnLedgerPool(noId, kind)

    /**
     * The row has an amount to show: the claimable it started with, or the
     * vault's once confirmed. A row the SDK added, or a pool that could not
     * be read, has none, and shows none rather than a zero.
     */
    val amountKnown: Boolean
        get() = 0 < amountRao
}

/** The claim dialog of settlement v2. */
sealed class SnLedgerClaimDialogState {
    object Loading : SnLedgerClaimDialogState()
    data class Ready(
        val accounts: List<SnLedgerAccountState>,
        val totalRao: Long,
        val gasKey: SnGasKeyState?,
        val gasTao: Double?,
        // the pools the ledger could not read just now, listed as unavailable
        // under the accounts: the total leaves them out
        val unavailablePools: List<SnLedgerPool> = emptyList(),
    ) : SnLedgerClaimDialogState()
    data class NeedsGas(
        val gasKey: SnGasKeyState?,
        val gasTao: Double,
        val totalRao: Long,
    ) : SnLedgerClaimDialogState()
    data class Sending(val progress: List<SnLedgerClaimProgress>) : SnLedgerClaimDialogState()
    data class Finished(val progress: List<SnLedgerClaimProgress>, val confirmedRao: Long) : SnLedgerClaimDialogState()
    object Unavailable : SnLedgerClaimDialogState()
}

/**
 * The dialog once the accounts and the gas balance are read, as for v1: the
 * accounts with something to claim, with the pools the ledger could not read
 * listed as unavailable, or the gas top-up when the gas key cannot pay for a
 * claim.
 */
fun snLedgerClaimDialog(load: SnLedgerLoad, gasKey: SnGasKeyState?, gasTao: Double?): SnLedgerClaimDialogState {
    if (load !is SnLedgerLoad.Loaded) {
        return SnLedgerClaimDialogState.Unavailable
    }
    val claimable = load.accounts.filter { 0 < it.claimableRao }
    val total = claimable.sumOf { it.claimableRao }
    return if (claimable.isNotEmpty() && gasTao != null && gasTao < EarningsViewModel.MIN_GAS_TAO) {
        SnLedgerClaimDialogState.NeedsGas(gasKey, gasTao, total)
    } else {
        SnLedgerClaimDialogState.Ready(claimable, total, gasKey, gasTao, load.unavailablePools)
    }
}

/**
 * A claim starts with a pending row for each account it claims, and an
 * unavailable row for each pool the dialog's read could not read, so none of
 * them leaves the dialog; the SDK reads the ledger again and says what it
 * finds there.
 */
fun snLedgerClaimStart(
    accounts: List<SnLedgerAccountState>,
    unavailablePools: List<SnLedgerPool> = emptyList(),
): List<SnLedgerClaimProgress> =
    accounts.filter { 0 < it.claimableRao }.map {
        SnLedgerClaimProgress(it.noId, it.index, it.claimableRao, SnLedgerClaimStatus.PENDING, null, null, it.kind)
    } + unavailablePools.map {
        SnLedgerClaimProgress(it.noId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, null, it.kind)
    }

/**
 * The rows after one event. `already_claimed` is not a failure: a third
 * party's claim paid the same coldkey. A failure that is not about one
 * account (no wallet, v2 claims not configured, the ledger or the chain
 * unreachable) comes with pool 0 and fails every row not sent. A pool the
 * ledger could not read comes as ledger_unavailable with its pool and index 0
 * (its account is unknown): that pool's rows not sent are unavailable, the
 * others claim on. While a capture the flag pool is in view, the read's
 * [pools] as the dialog showed them or the claim's own rows, an account under
 * the transfer floor (not_claimable, AmountTooLow) is not a failure either:
 * it waits. An event for an account without a row (the SDK reads the accounts
 * again) takes the row of its pool that was unavailable at the dialog's read,
 * else adds its own.
 */
fun snLedgerClaimProgressAfter(
    progress: List<SnLedgerClaimProgress>,
    event: SnLedgerClaimEvent,
    pools: Collection<SnLedgerPool> = emptyList(),
): List<SnLedgerClaimProgress> {
    if (event is SnLedgerClaimEvent.Failed && event.noId == 0L) {
        return progress.map {
            if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = SnLedgerClaimStatus.FAILED, message = event.message) else it
        }
    }
    if (event is SnLedgerClaimEvent.Failed && event.index == 0L &&
        EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_LEDGER_UNAVAILABLE
    ) {
        if (progress.none { it.noId == event.noId }) {
            return progress + SnLedgerClaimProgress(event.noId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, event.message)
        }
        return progress.map {
            if (it.noId == event.noId &&
                (it.status == SnLedgerClaimStatus.PENDING || it.status == SnLedgerClaimStatus.POOL_UNAVAILABLE)
            ) {
                it.copy(status = SnLedgerClaimStatus.POOL_UNAVAILABLE, message = event.message)
            } else {
                it
            }
        }
    }
    val (noId, index) = when (event) {
        is SnLedgerClaimEvent.Sent -> event.noId to event.index
        is SnLedgerClaimEvent.Confirmed -> event.noId to event.index
        is SnLedgerClaimEvent.Failed -> event.noId to event.index
        SnLedgerClaimEvent.Done -> return progress
    }
    // the row of the account, else its pool's that the dialog's read could not read
    val i = progress.indexOfFirst { it.noId == noId && it.index == index }.takeIf { 0 <= it }
        ?: progress.indexOfFirst { it.noId == noId && it.status == SnLedgerClaimStatus.POOL_UNAVAILABLE && it.txHash == null }
    val row = progress.getOrNull(i)?.copy(index = index)
        ?: SnLedgerClaimProgress(noId, index, 0, SnLedgerClaimStatus.PENDING, null, null)
    val ctfInView = (pools + progress.map { it.pool } + row.pool).any { it.isCtf }
    val updated = when (event) {
        is SnLedgerClaimEvent.Sent -> row.copy(status = SnLedgerClaimStatus.SENT, txHash = event.txHash)
        is SnLedgerClaimEvent.Confirmed ->
            row.copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = event.txHash, amountRao = event.amountRao)
        is SnLedgerClaimEvent.Failed -> row.copy(
            status = when {
                EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_ALREADY_CLAIMED ->
                    SnLedgerClaimStatus.ALREADY_CLAIMED
                ctfInView && snLedgerClaimUnderFloor(event.message) -> SnLedgerClaimStatus.UNDER_FLOOR
                else -> SnLedgerClaimStatus.FAILED
            },
            message = event.message,
        )
        SnLedgerClaimEvent.Done -> row
    }
    return if (i < 0) progress + updated else progress.toMutableList().also { it[i] = updated }
}

/**
 * The SDK's own read of the ledger answered: an event about an account or a
 * pool, or nothing to claim in any pool, which it never says while a pool is
 * unavailable. Its other failures with pool 0 stop the claim before the read.
 */
fun snLedgerClaimReadLedger(event: SnLedgerClaimEvent): Boolean = when (event) {
    is SnLedgerClaimEvent.Sent, is SnLedgerClaimEvent.Confirmed -> true
    is SnLedgerClaimEvent.Failed ->
        event.noId != 0L || EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_NOT_CLAIMABLE
    SnLedgerClaimEvent.Done -> false
}

/**
 * The rows at Done. A pool the dialog could not read keeps its unavailable
 * row while the claim stopped before the SDK's own read; once the read
 * answered ([ledgerRead]) and said nothing of that pool, the pool was read
 * and held nothing to claim, and its row goes.
 */
fun snLedgerClaimSettled(progress: List<SnLedgerClaimProgress>, ledgerRead: Boolean): List<SnLedgerClaimProgress> =
    if (!ledgerRead) progress else progress.filterNot { it.status == SnLedgerClaimStatus.POOL_UNAVAILABLE && it.message == null }

/**
 * The SDK's refusal of an account under the runtime's transfer floor:
 * "not_claimable: AmountTooLow: …", the runtime's error for it.
 */
fun snLedgerClaimUnderFloor(message: String): Boolean =
    EarningsViewModel.claimFailureCode(message) == EarningsViewModel.SN_CODE_NOT_CLAIMABLE &&
        message.contains("AmountTooLow")

/**
 * The dialog after Done: the gas top-up when every account the claim could
 * take failed for want of gas, with the gas key's balance read again by
 * [gasTao]; else the rows with the confirmed total. A row the SDK never
 * reported on was not sent. An account under the floor or in a pool that
 * could not be read was never the claim's to take, so it leaves the top-up
 * to the others.
 */
suspend fun snLedgerClaimFinished(
    progress: List<SnLedgerClaimProgress>,
    gasKey: SnGasKeyState?,
    gasTao: suspend () -> Double,
): SnLedgerClaimDialogState {
    val settled = progress.map {
        if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = SnLedgerClaimStatus.FAILED) else it
    }
    val taken = settled.filter {
        it.status != SnLedgerClaimStatus.UNDER_FLOOR && it.status != SnLedgerClaimStatus.POOL_UNAVAILABLE
    }
    val allNeedGas = taken.isNotEmpty() && taken.all {
        it.status == SnLedgerClaimStatus.FAILED &&
            EarningsViewModel.claimFailureCode(it.message) == EarningsViewModel.SN_CODE_NEEDS_GAS
    }
    if (allNeedGas) {
        return SnLedgerClaimDialogState.NeedsGas(gasKey, gasTao(), taken.sumOf { it.amountRao })
    }
    return SnLedgerClaimDialogState.Finished(
        settled,
        settled.filter { it.status == SnLedgerClaimStatus.CONFIRMED }.sumOf { it.amountRao },
    )
}

/**
 * The string a failed account reads, for the codes that have one; null for
 * the others, which show the SDK's detail.
 */
fun snLedgerClaimFailureRes(message: String?): Int? = when (EarningsViewModel.claimFailureCode(message)) {
    EarningsViewModel.SN_CODE_NEEDS_GAS -> R.string.add_tao_for_gas
    EarningsViewModel.SN_CODE_RPC_UNREACHABLE -> R.string.chain_rpc_unreachable
    else -> null
}

/** The SDK's detail after "<code>: ", or the whole message without one. */
fun snLedgerClaimFailureDetail(message: String): String =
    message.substringAfter(':').trim().ifEmpty { message }

// ---- the tile and its dialog

/**
 * The Settlement v2 tile's accounts and its claim dialog. The earnings view
 * model owns one and points it at the device's protocol source; the SDK reads
 * and claims, this keeps where that stands. A claim in flight outlives its
 * dialog: reopening shows it rather than starting another, and the accounts
 * are read again after its Done.
 */
class SnLedgerFlow(
    private val scope: CoroutineScope,
    private val hasWallet: () -> Boolean,
) {
    private var source: EarningsProtocolSource = NoProtocolSource

    private val _load = MutableStateFlow<SnLedgerLoad>(SnLedgerLoad.Loading)
    val load: StateFlow<SnLedgerLoad> = _load.asStateFlow()

    private val _claimConfigured = MutableStateFlow(false)
    val claimConfigured: StateFlow<Boolean> = _claimConfigured.asStateFlow()

    private val _dialog = MutableStateFlow<SnLedgerClaimDialogState?>(null)
    val dialog: StateFlow<SnLedgerClaimDialogState?> = _dialog.asStateFlow()

    // the claim in flight; null between claims
    private var claiming: List<SnLedgerClaimProgress>? = null

    // the pools in view when it started, as the dialog showed them, which
    // decide its rows' statuses as they decided the dialog's
    private var claimingPools: List<SnLedgerPool> = emptyList()

    // the SDK's own read of the ledger answered (snLedgerClaimReadLedger)
    private var claimingRead = false

    /** A new device (or none): drop what the last one read. */
    fun reset(source: EarningsProtocolSource) {
        this.source = source
        _load.value = SnLedgerLoad.Loading
        _claimConfigured.value = false
        _dialog.value = null
        claiming = null
        claimingPools = emptyList()
        claimingRead = false
    }

    /** Reads the accounts; without a wallet there are none to read. */
    suspend fun refresh() {
        val s = source
        if (!s.available || !hasWallet()) {
            _load.value = SnLedgerLoad.Loading
            return
        }
        // the release pins vault2, so a configured tile shows its spinner during the first read
        _claimConfigured.value = s.ledgerClaimConfigured()
        val load = s.ledgerAccounts().fold(
            onSuccess = { SnLedgerLoad.Loaded(it.accounts, it.unavailablePools) },
            onFailure = { SnLedgerLoad.Unavailable },
        )
        if (s === source) {
            _load.value = load
        }
    }

    /** The tile's Claim: the dialog, or the claim still in flight. */
    fun open() {
        claiming?.let {
            _dialog.value = SnLedgerClaimDialogState.Sending(it)
            return
        }
        _dialog.value = SnLedgerClaimDialogState.Loading
        scope.launch { loadDialog() }
    }

    fun close() {
        _dialog.value = null
    }

    /** Retry from the gas top-up or an unavailable read. */
    fun retry() {
        if (_dialog.value == null || claiming != null) {
            return
        }
        _dialog.value = SnLedgerClaimDialogState.Loading
        scope.launch { loadDialog() }
    }

    private suspend fun loadDialog() {
        val s = source
        var gasTao: Double? = null
        coroutineScope {
            launch { refresh() }
            launch { gasTao = s.gasBalance().getOrNull()?.tao }
        }
        if (s !== source || _dialog.value != SnLedgerClaimDialogState.Loading) {
            return
        }
        _dialog.value = snLedgerClaimDialog(_load.value, s.gasKey(), gasTao)
    }

    /**
     * The dialog's Claim: the SDK claims every account with something to
     * claim, the pool's and capture the flag's in one transaction.
     */
    fun claim() {
        val ready = _dialog.value as? SnLedgerClaimDialogState.Ready ?: return
        if (ready.accounts.isEmpty() || claiming != null) {
            return
        }
        val s = source
        val progress = snLedgerClaimStart(ready.accounts, ready.unavailablePools)
        claiming = progress
        claimingPools = (snLedgerPools(_load.value) + ready.accounts.map { it.pool } + ready.unavailablePools).distinct()
        claimingRead = false
        _dialog.value = SnLedgerClaimDialogState.Sending(progress)
        s.ledgerClaim { event ->
            scope.launch { onEvent(s, event) }
        }
    }

    private suspend fun onEvent(s: EarningsProtocolSource, event: SnLedgerClaimEvent) {
        val progress = claiming
        if (s !== source || progress == null) {
            return
        }
        if (event != SnLedgerClaimEvent.Done) {
            claimingRead = claimingRead || snLedgerClaimReadLedger(event)
            val next = snLedgerClaimProgressAfter(progress, event, claimingPools)
            claiming = next
            if (_dialog.value is SnLedgerClaimDialogState.Sending) {
                _dialog.value = SnLedgerClaimDialogState.Sending(next)
            }
            return
        }
        claiming = null
        val settled = snLedgerClaimSettled(progress, claimingRead)
        val finished = snLedgerClaimFinished(settled, s.gasKey()) { s.gasBalance().getOrNull()?.tao ?: 0.0 }
        if (s === source && _dialog.value is SnLedgerClaimDialogState.Sending) {
            _dialog.value = finished
        }
        refresh()
    }
}
