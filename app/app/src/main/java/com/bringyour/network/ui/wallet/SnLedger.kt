package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * Below the claim minimum (sn_ledger_pool_under_floor): something to claim,
 * under the claim relay's transfer floor in rao of α at its last price (0
 * without one). The runtime refuses a claim under its floor (AmountTooLow), so
 * no claim can move the account yet. It holds for any pool's account, alone or
 * beside others, on any server (owner, 2026-11-11), as the sdk's own
 * SelfClaimAvailable already decides; without a price the claim's own
 * preflight decides.
 */
fun snLedgerUnderFloor(claimableRao: Long, transferFloorRao: Long): Boolean =
    0 < transferFloorRao && 0 < claimableRao && claimableRao < transferFloorRao

/**
 * The accounts a claim can move: something to claim, and not under the
 * transfer floor (0 for none). They are what Claim and its amount count, and
 * Claim both; one under the floor reads Below the claim minimum, and no claim
 * is offered for it.
 */
fun snLedgerClaimTakes(accounts: List<SnLedgerAccountState>, transferFloorRao: Long): List<SnLedgerAccountState> =
    accounts.filter { 0 < it.claimableRao && !snLedgerUnderFloor(it.claimableRao, transferFloorRao) }

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
 * price (0 without one): each account with something to claim under it reads
 * Below the claim minimum, whatever pools are in view. Claim is offered only
 * while an account can move, so never for a claim the runtime refuses, and
 * reads Claim both while the accounts that can move are exactly the pool's and
 * capture the flag's, as the dialog decides it from the same accounts
 * (Ready.takes): a second operator pool's account under the floor leaves Claim
 * both to the other two.
 */
fun SnLedgerTile.withTransferFloor(transferFloorRao: Long): SnLedgerTile {
    if (transferFloorRao <= 0 || accounts == null) {
        return this
    }
    val lines = accounts.map { it.copy(underFloor = snLedgerUnderFloor(it.claimableRao, transferFloorRao)) }
    val moves = lines.filter { 0 < it.claimableRao && !it.underFloor }
    return copy(
        accounts = lines,
        claimEnabled = claimEnabled && moves.isNotEmpty(),
        claimBoth = showClaim && snLedgerClaimsBoth(moves.map { it.pool }),
    )
}

/**
 * A capture the flag slot won and not claimed yet (sdk SnCtfWin, one of
 * SnCtfClientResult.UnclaimedWins from GET /sn/ctf/client): its role, sdk
 * SnCtfRole*, and its country, a lowercase ISO 3166-1 code. The tile draws a
 * line for each, newest first (owner, 2026-11-11: every unclaimed win).
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

/**
 * The tile's won lines: one per unclaimed win, in the sdk's order (newest
 * first, providers before extenders within an epoch), each its string and its
 * country's code. A win without a line (snCtfWonRes) is left out.
 */
fun snCtfWonLines(wins: List<SnCtfWinState>): List<Pair<Int, String>> =
    wins.mapNotNull { win -> snCtfWonRes(win)?.let { res -> res to win.country } }

/**
 * When the wins are read again after a claim that paid an account, in ms
 * after its Done, beside the read at Done (every app's schedule). The SDK's
 * Done comes once the claim is in a block, but the server says a win is
 * claimed only once vault2's claimed at the finalized head covers it, so the
 * read at Done can still list it.
 */
val SN_CTF_WINS_READS_AFTER_CLAIM_MS: List<Long> = listOf(30_000, 60_000, 120_000)

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
    // the gas key could not pay for its claim (needs_gas): nothing was sent
    // for it, so it is still claimable once the gas key is topped up. When
    // the gas key can pay for one claim but not the batch, the sdk claims the
    // largest account first (owner, 2026-11-11), and this is each other
    STILL_CLAIMABLE,
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
        // every account with something to claim, each a row
        val accounts: List<SnLedgerAccountState>,
        // what the claim moves: the accounts at or above the transfer floor
        val totalRao: Long,
        val gasKey: SnGasKeyState?,
        val gasTao: Double?,
        // the pools the ledger could not read just now, listed as unavailable
        // under the accounts: the total leaves them out
        val unavailablePools: List<SnLedgerPool> = emptyList(),
        // the claim relay's transfer floor when the dialog opened, 0 without
        // one: an account under it reads Below the claim minimum
        val transferFloorRao: Long = 0,
    ) : SnLedgerClaimDialogState() {
        /** the accounts the claim moves; with none there is no Claim, only Close */
        val takes: List<SnLedgerAccountState>
            get() = snLedgerClaimTakes(accounts, transferFloorRao)
    }
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
 * claim. The total and the gas are for what the claim can move: an account
 * under the transfer floor (0 for none) is listed, Below the claim minimum,
 * and asks for no claim and no gas.
 */
fun snLedgerClaimDialog(
    load: SnLedgerLoad,
    gasKey: SnGasKeyState?,
    gasTao: Double?,
    transferFloorRao: Long = 0,
): SnLedgerClaimDialogState {
    if (load !is SnLedgerLoad.Loaded) {
        return SnLedgerClaimDialogState.Unavailable
    }
    val claimable = load.accounts.filter { 0 < it.claimableRao }
    val takes = snLedgerClaimTakes(claimable, transferFloorRao)
    val total = takes.sumOf { it.claimableRao }
    return if (takes.isNotEmpty() && gasTao != null && gasTao < EarningsViewModel.MIN_GAS_TAO) {
        SnLedgerClaimDialogState.NeedsGas(gasKey, gasTao, total)
    } else {
        SnLedgerClaimDialogState.Ready(claimable, total, gasKey, gasTao, load.unavailablePools, transferFloorRao.coerceAtLeast(0))
    }
}

/**
 * A claim starts with a pending row for each account it moves, a row Below
 * the claim minimum for each the dialog listed under the transfer floor (0
 * for none), and an unavailable row for each pool the dialog's read could not
 * read, so none of them leaves the dialog; the SDK reads the ledger again and
 * says what it finds there, and its preflight decides an account near the
 * floor.
 */
fun snLedgerClaimStart(
    accounts: List<SnLedgerAccountState>,
    unavailablePools: List<SnLedgerPool> = emptyList(),
    transferFloorRao: Long = 0,
): List<SnLedgerClaimProgress> =
    accounts.filter { 0 < it.claimableRao }.map {
        val status = if (snLedgerUnderFloor(it.claimableRao, transferFloorRao)) {
            SnLedgerClaimStatus.UNDER_FLOOR
        } else {
            SnLedgerClaimStatus.PENDING
        }
        SnLedgerClaimProgress(it.noId, it.index, it.claimableRao, status, null, null, it.kind)
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
 * others claim on. An account under the runtime's transfer floor
 * (not_claimable, AmountTooLow) is not a failure either, on any server: it
 * waits, Below the claim minimum. An event for an account without a row (the
 * SDK reads the accounts again) takes the row of its pool that was
 * unavailable at the dialog's read, else adds its own.
 */
fun snLedgerClaimProgressAfter(
    progress: List<SnLedgerClaimProgress>,
    event: SnLedgerClaimEvent,
): List<SnLedgerClaimProgress> {
    if (event is SnLedgerClaimEvent.Failed && event.noId == 0L) {
        // nothing was sent for a row short of gas: still claimable, not failed
        val status = if (snLedgerClaimNeedsGas(event.message)) SnLedgerClaimStatus.STILL_CLAIMABLE else SnLedgerClaimStatus.FAILED
        return progress.map {
            if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = status, message = event.message) else it
        }
    }
    if (event is SnLedgerClaimEvent.Failed && event.index == 0L &&
        EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_LEDGER_UNAVAILABLE
    ) {
        if (progress.none { it.noId == event.noId }) {
            return progress + SnLedgerClaimProgress(event.noId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, event.message)
        }
        // the pool's rows not sent, whatever the dialog's read said of them
        return progress.map {
            if (it.noId == event.noId && it.txHash == null &&
                (it.status == SnLedgerClaimStatus.PENDING || it.status == SnLedgerClaimStatus.UNDER_FLOOR ||
                    it.status == SnLedgerClaimStatus.POOL_UNAVAILABLE)
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
    val updated = when (event) {
        is SnLedgerClaimEvent.Sent -> row.copy(status = SnLedgerClaimStatus.SENT, txHash = event.txHash)
        is SnLedgerClaimEvent.Confirmed ->
            row.copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = event.txHash, amountRao = event.amountRao)
        is SnLedgerClaimEvent.Failed -> row.copy(
            status = when {
                EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_ALREADY_CLAIMED ->
                    SnLedgerClaimStatus.ALREADY_CLAIMED
                snLedgerClaimUnderFloor(event.message) -> SnLedgerClaimStatus.UNDER_FLOOR
                // the gas key could not pay for it: the sdk sent nothing for it, so it is still
                // claimable, even after a batch that did not attempt it
                snLedgerClaimNeedsGas(event.message) -> SnLedgerClaimStatus.STILL_CLAIMABLE
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
 * The claim paid an account: confirmed, or claimed already by a third party,
 * whose claim vault2 paid to the same coldkey. Capture the flag's wins it paid
 * read as claimed once the finalized head covers it.
 */
fun snLedgerClaimPaid(progress: List<SnLedgerClaimProgress>): Boolean =
    progress.any { it.status == SnLedgerClaimStatus.CONFIRMED || it.status == SnLedgerClaimStatus.ALREADY_CLAIMED }

/**
 * The SDK's refusal of an account under the runtime's transfer floor:
 * "not_claimable: AmountTooLow: …", the runtime's error for it.
 */
fun snLedgerClaimUnderFloor(message: String): Boolean =
    EarningsViewModel.claimFailureCode(message) == EarningsViewModel.SN_CODE_NOT_CLAIMABLE &&
        message.contains("AmountTooLow")

/**
 * The SDK's word that the gas key cannot pay for a claim: "needs_gas: …".
 * It says so before sending, so the account is still claimable.
 */
fun snLedgerClaimNeedsGas(message: String?): Boolean =
    EarningsViewModel.claimFailureCode(message) == EarningsViewModel.SN_CODE_NEEDS_GAS

/**
 * The dialog after Done: the gas top-up when the gas key could pay for none
 * of the accounts the claim could take, with its balance read again by
 * [gasTao]; else the rows with the confirmed total, where an account the gas
 * key could not pay for beside the others reads still claimable. A row the
 * SDK never reported on was not sent. An account under the floor or in a
 * pool that could not be read was never the claim's to take, so it leaves
 * the top-up to the others.
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
    val allNeedGas = taken.isNotEmpty() && taken.all { it.status == SnLedgerClaimStatus.STILL_CLAIMABLE }
    if (allNeedGas) {
        return SnLedgerClaimDialogState.NeedsGas(gasKey, gasTao(), taken.sumOf { it.amountRao })
    }
    return SnLedgerClaimDialogState.Finished(
        settled,
        settled.filter { it.status == SnLedgerClaimStatus.CONFIRMED }.sumOf { it.amountRao },
    )
}

/**
 * What a claim's row says in place of a claim's status, for an account the
 * claim did not move and that did not fail: Below the claim minimum, its pool
 * Unavailable right now, or still Claimable (sn_ledger_claimable) with the
 * gas top-up's hint under it. Null for the others.
 */
fun snLedgerClaimRowNoteRes(status: SnLedgerClaimStatus): Int? = when (status) {
    SnLedgerClaimStatus.UNDER_FLOOR -> R.string.sn_ledger_pool_under_floor
    SnLedgerClaimStatus.POOL_UNAVAILABLE -> R.string.sn_ledger_pool_unavailable
    SnLedgerClaimStatus.STILL_CLAIMABLE -> R.string.sn_ledger_claimable
    else -> null
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
 * The Settlement v2 tile's accounts, its won lines and its claim dialog. The
 * earnings view model owns one and points it at the device's protocol source;
 * the SDK reads and claims, this keeps where that stands. A claim in flight
 * outlives its dialog: reopening shows it rather than starting another, and
 * the accounts and the wins are read again after its Done.
 */
class SnLedgerFlow(
    private val scope: CoroutineScope,
    private val hasWallet: () -> Boolean,
    // the claim relay's transfer floor in rao of α at its last price, 0
    // without an answer: the dialog offers no claim for an account under it
    private val transferFloorRao: () -> Long = { 0 },
    // the wait between the reads of the wins after a claim (the tests' clock)
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private var source: EarningsProtocolSource = NoProtocolSource

    private val _load = MutableStateFlow<SnLedgerLoad>(SnLedgerLoad.Loading)
    val load: StateFlow<SnLedgerLoad> = _load.asStateFlow()

    // the capture the flag slots won and not claimed yet (GET /sn/ctf/client),
    // newest first, a line each on the tile; empty without one or without an
    // answer
    private val _wins = MutableStateFlow<List<SnCtfWinState>>(emptyList())
    val wins: StateFlow<List<SnCtfWinState>> = _wins.asStateFlow()

    // the reads of the wins after the last claim's Done (readWinsAfterClaim)
    private var winsAfterClaim: Job? = null

    private val _claimConfigured = MutableStateFlow(false)
    val claimConfigured: StateFlow<Boolean> = _claimConfigured.asStateFlow()

    private val _dialog = MutableStateFlow<SnLedgerClaimDialogState?>(null)
    val dialog: StateFlow<SnLedgerClaimDialogState?> = _dialog.asStateFlow()

    // the claim in flight; null between claims
    private var claiming: List<SnLedgerClaimProgress>? = null

    // the SDK's own read of the ledger answered (snLedgerClaimReadLedger)
    private var claimingRead = false

    /** A new device (or none: signed out): drop what the last one read, and stop reading its wins. */
    fun reset(source: EarningsProtocolSource) {
        this.source = source
        _load.value = SnLedgerLoad.Loading
        _claimConfigured.value = false
        _dialog.value = null
        claiming = null
        claimingRead = false
        winsAfterClaim?.cancel()
        winsAfterClaim = null
        _wins.value = emptyList()
    }

    /**
     * Reads the wins not claimed yet, with or without a wallet: they are the
     * network's. Each read replaces the lines; one that failed draws none.
     */
    suspend fun refreshWins() {
        val s = source
        val wins = s.ctfWins()
        if (s === source) {
            _wins.value = wins
        }
    }

    /**
     * After a claim's Done the wins are read at once, on their own. When the
     * claim paid an account they are read again at each of
     * [SN_CTF_WINS_READS_AFTER_CLAIM_MS] after Done, since the read at Done
     * can still list a win the claim paid; each read goes on its own, so a
     * slow answer moves none of the later reads. The next claim that paid
     * starts the reads over, and one that paid nothing leaves them to run. A
     * new device or a sign-out (reset) stops them, and so does the end of the
     * scope, the view model's.
     */
    private fun readWinsAfterClaim(paid: Boolean) {
        if (!paid) {
            scope.launch { refreshWins() }
            return
        }
        winsAfterClaim?.cancel()
        winsAfterClaim = scope.launch {
            launch { refreshWins() }
            var afterDone = 0L
            for (at in SN_CTF_WINS_READS_AFTER_CLAIM_MS) {
                sleep(at - afterDone)
                afterDone = at
                launch { refreshWins() }
            }
        }
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
        _dialog.value = snLedgerClaimDialog(_load.value, s.gasKey(), gasTao, transferFloorRao())
    }

    /**
     * The dialog's Claim: the SDK claims every account with something to
     * claim, the pool's and capture the flag's in one transaction. Only while
     * an account can move: with every one under the transfer floor there is
     * no claim the runtime would take.
     */
    fun claim() {
        val ready = _dialog.value as? SnLedgerClaimDialogState.Ready ?: return
        if (ready.takes.isEmpty() || claiming != null) {
            return
        }
        val s = source
        val progress = snLedgerClaimStart(ready.accounts, ready.unavailablePools, ready.transferFloorRao)
        claiming = progress
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
            val next = snLedgerClaimProgressAfter(progress, event)
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
        if (s === source) {
            // a claim may have paid capture the flag's wins, whose lines go
            // once the server reads them claimed
            readWinsAfterClaim(snLedgerClaimPaid(settled))
        }
        refresh()
    }
}
