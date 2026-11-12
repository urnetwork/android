package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
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
 * The tile while the session's v2 claim is in flight: Claim opens the claim's
 * progress (SnLedgerFlow.open) whatever the last read says, as on every app
 * (2026-11-12), since a read during the claim may find nothing left to claim.
 * Without Claim (before vault2's address, or the relay speaking in its place)
 * nothing changes.
 */
fun SnLedgerTile.withClaimInFlight(inFlight: Boolean): SnLedgerTile =
    if (inFlight && showClaim) copy(claimEnabled = true) else this

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
 * One good answer of GET /sn/ctf/client (sdk SnCtfClientResult): the wins not
 * claimed yet, newest first, and the client it answered for (ClientId). That
 * is "" for the network's answer, whose wins span all of the network's
 * clients, and set for one client's answer: the sdk's fallback to the client
 * token after the network's read failed, or a server before the network's
 * answer, which lists that client's wins alone.
 */
data class SnCtfWinsAnswer(
    val wins: List<SnCtfWinState>,
    val clientId: String = "",
) {
    /** the network's answer, which lists every one of its clients' wins */
    val network: Boolean
        get() = clientId.isEmpty()
}

/**
 * The wins the won lines are drawn from (every app, 2026-11-12). Reads
 * overlap (a refresh's beside a claim's), and their answers land in any
 * order. The lines show the newest read's good answer, by the order the reads
 * started: an answer replaces them unless they already show a newer read's,
 * so an older answer that lands late never replaces a newer one. A read that
 * failed or could not be read (null) changes nothing, so it never blanks the
 * lines, nor keeps an older read's good answer still to land from showing.
 *
 * A network's answer beats a client's (every app, 2026-11-12): a client's
 * answer lists one client's wins, so once a network's answer has shown since
 * the last reset (a sign-in, or another device), a client's answer is a read
 * that failed and replaces nothing. It shows only while no network's answer
 * has, and a network's answer replaces it, whichever read started first.
 * Only a good answer that no longer lists a win, or a reset, takes a line
 * away; after a reset no answer of a read from before it shows, and there are
 * no lines until the first answer. As Linux's snledger::WinsLines.
 */
class SnCtfWinsLines {
    // the newest read's number
    private var started = 0L

    // the reads up to this one started before the last reset
    private var resetAt = 0L

    // the read whose good answer the lines show
    private var shown = 0L

    // the lines are a network's answer
    private var network = false

    var wins: List<SnCtfWinState> = emptyList()
        private set

    /** A read starts: its number, which its answer carries. */
    fun start(): Long = ++started

    /** The answer of read [read], null for a read that failed: true when the lines are now its. */
    fun apply(read: Long, answer: SnCtfWinsAnswer?): Boolean {
        if (answer == null || read <= resetAt) {
            return false
        }
        val replaces = if (answer.network) !network || shown < read else !network && shown < read
        if (!replaces) {
            return false
        }
        shown = read
        network = answer.network
        wins = answer.wins
        return true
    }

    /** A sign-in or another device: no lines, and no answer of a read from before shows. */
    fun reset() {
        resetAt = started
        shown = started
        network = false
        wins = emptyList()
    }
}

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
    /**
     * A claim in flight: its rows, and the figure, what the claim is moving
     * (snLedgerClaimMovingRao); 0 for none, never a zero
     */
    data class Sending(val progress: List<SnLedgerClaimProgress>, val movingRao: Long = 0) : SnLedgerClaimDialogState()
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
 * The figure while a claim runs (every app, 2026-11-12): what the claim is
 * moving, the sum of the accounts it sends whose amount is known and not
 * under the transfer floor it started with ([transferFloorRao], 0 for none),
 * the amount its Claim named. An account under the floor, one in a pool that
 * could not be read and one whose amount is not known are no part of it; 0,
 * which shows no figure, never a zero, with none of them. [start] is the
 * claim's rows when it starts (snLedgerClaimStart).
 */
fun snLedgerClaimMovingRao(start: List<SnLedgerClaimProgress>, transferFloorRao: Long): Long =
    start.filter {
        it.status == SnLedgerClaimStatus.PENDING && it.amountKnown && !snLedgerUnderFloor(it.amountRao, transferFloorRao)
    }.sumOf { it.amountRao }

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
 * SDK never reported on was not sent. An account the claim could not take
 * leaves the top-up, its total and its decision to the others: one in a pool
 * that could not be read, one the runtime refused under its floor, and one
 * the gas key fell short for whose amount is under [transferFloorRao], the
 * floor the claim started with (0 for none), as the dialog listed it and as
 * the claim's amount left it out. With none left to take, the rows say what
 * each is, and there is no top-up for nothing. An account under the floor
 * that came to anything else (claimed, claimed already, failed) is still the
 * claim's to show: its row, beside the others'.
 */
suspend fun snLedgerClaimFinished(
    progress: List<SnLedgerClaimProgress>,
    gasKey: SnGasKeyState?,
    transferFloorRao: Long = 0,
    gasTao: suspend () -> Double,
): SnLedgerClaimDialogState {
    val settled = progress.map {
        if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = SnLedgerClaimStatus.FAILED) else it
    }
    // a row still claimable carries the claimable it started with
    val taken = settled.filter {
        it.status != SnLedgerClaimStatus.UNDER_FLOOR && it.status != SnLedgerClaimStatus.POOL_UNAVAILABLE &&
            !(it.status == SnLedgerClaimStatus.STILL_CLAIMABLE && snLedgerUnderFloor(it.amountRao, transferFloorRao))
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

// ---- the claims in flight, the session's

/**
 * The claims in flight, the v2 claim (SnLedgerClaim) and the v1 claim
 * (SnClaim), and the reads of the wins after a v2 claim's Done: the signed-in
 * session's, not a screen's, a dialog's or a view model's (every app,
 * 2026-11-12). The app holds one for the process, on the process's own scope,
 * so the SDK's events and their Done land here whatever became of the dialog
 * or the activity. A dialog dismissed during a claim and opened again shows
 * it; an activity that ends during a claim (Back from the root on Android 8 to
 * 11, a swipe-away while the VPN service keeps the process) and comes back
 * finds the claim still in flight, never starts a second one of its kind
 * while the SDK still claims, and gets its Done and what follows it.
 *
 * It keeps the session's won lines too (SnCtfWinsLines): a network's answer
 * beats a client's since the sign-in, and a read that fails keeps them, so an
 * activity that comes back during the sign-in finds them as the last good
 * read left them.
 *
 * A view model, and its v2 flow (SnLedgerFlow), attach for their session, the
 * signed-in device, and draw what this holds. One that attaches for another
 * session (a sign-out, or another device) ends the claims, the reads and the
 * won lines of the one before: none of those claims' events, nor any read's
 * answer, counts any more. Main thread.
 */
@Singleton
class SnClaimHolder internal constructor(
    private val scope: CoroutineScope,
    // the wait between the reads of the wins after a claim (the tests' clock)
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    /** What a listener attached for the claims' session hears, on the main thread; each hears its part. */
    interface Listener {
        /** the v2 claim's rows moved; [movingRao] is its figure */
        fun claimMoved(rows: List<SnLedgerClaimProgress>, movingRao: Long) {}

        /** its Done: the rows settled (snLedgerClaimSettled), and the transfer floor the claim started with */
        fun claimDone(settled: List<SnLedgerClaimProgress>, transferFloorRao: Long) {}

        /** a read of the wins: at a v2 claim's Done, and on the apps' schedule after one that paid */
        fun readWins() {}

        /** the v1 claim's rows moved */
        fun epochClaimMoved(rows: List<ClaimProgress>) {}

        /** the v1 claim's Done: its rows settled (claimProgressSettled) */
        fun epochClaimDone(settled: List<ClaimProgress>) {}
    }

    /** The claim in flight as its dialog draws it: its rows and its figure. */
    data class InFlight(val rows: List<SnLedgerClaimProgress>, val movingRao: Long)

    private class Claim(
        val session: Any,
        val transferFloorRao: Long,
        val movingRao: Long,
        var rows: List<SnLedgerClaimProgress>,
        // the SDK's own read of the ledger answered (snLedgerClaimReadLedger)
        var read: Boolean = false,
    )

    private class EpochClaim(
        val session: Any,
        var rows: List<ClaimProgress>,
    )

    private class Attached(val listener: Listener, val session: Any?)

    // the v2 claim in flight; null between claims
    private var claim: Claim? = null

    // the v1 claim in flight; null between claims
    private var epochClaim: EpochClaim? = null

    private val attached = mutableListOf<Attached>()

    // the reads of the wins after the last claim that paid, and the session they are for
    private var winsAfterClaim: Job? = null
    private var winsSession: Any? = null

    // the won lines (GET /sn/ctf/client), newest first, of the session they are for
    private val winsLines = SnCtfWinsLines()
    private var linesSession: Any? = null
    private val _wins = MutableStateFlow<List<SnCtfWinState>>(emptyList())

    /** The won lines of the session last attached: empty after a sign-in until a read answers. */
    val wins: StateFlow<List<SnCtfWinState>> = _wins.asStateFlow()

    /**
     * [listener], of [session] (null: no one signed in), hears of the
     * session's claims from now on. The claims, or reads after one, of
     * another session end here for good: that session signed out, or the
     * device changed. Returns the detach, for the listener's end or its next
     * session.
     */
    fun attach(listener: Listener, session: Any?): () -> Unit {
        if (claim?.session !== session) {
            claim = null
        }
        if (epochClaim?.session !== session) {
            epochClaim = null
        }
        if (winsSession !== session) {
            winsAfterClaim?.cancel()
            winsAfterClaim = null
            winsSession = null
        }
        if (linesSession !== session) {
            // a sign-in, or another device: no lines, and no read from before shows
            linesSession = session
            winsLines.reset()
            _wins.value = emptyList()
        }
        val entry = Attached(listener, session)
        attached += entry
        return { attached.remove(entry) }
    }

    /** A read of the won lines starts: its number, which its answer carries (applyWins). */
    fun startWinsRead(): Long = winsLines.start()

    /**
     * The answer of read [read] for [session], null for a read that failed:
     * the lines take it by SnCtfWinsLines' rules, and only while [session] is
     * still the one they are for.
     */
    fun applyWins(session: Any?, read: Long, answer: SnCtfWinsAnswer?) {
        if (session !== linesSession) {
            return
        }
        if (winsLines.apply(read, answer)) {
            _wins.value = winsLines.wins
        }
    }

    /** The v2 claim of [session] in flight; null between claims. */
    fun inFlight(session: Any?): InFlight? =
        claim?.takeIf { session != null && it.session === session }?.let { InFlight(it.rows, it.movingRao) }

    /** The v1 claim of [session] in flight, its rows; null between claims. */
    fun epochClaimInFlight(session: Any?): List<ClaimProgress>? =
        epochClaim?.takeIf { session != null && it.session === session }?.rows

    /**
     * A claim of either kind of [session] is in flight. The gas key sends one
     * claim at a time, as on Apple, Windows and Linux: neither kind starts
     * while one is in flight, and each dialog's Claim waits for the other
     * kind's. The SDK checks each claim's gas against the gas key's balance on
     * its own, not less what a claim in flight may spend, and an SDK from
     * before the gas key's turns reads each claim's nonce without waiting for
     * the other's broadcast, so two claims at once could take the same nonce.
     */
    fun claiming(session: Any?): Boolean = inFlight(session) != null || epochClaimInFlight(session) != null

    /**
     * Claims [epochs] for [session] with [source] (SnClaim), from the rows it
     * starts with. False, and no claim, while a claim of either kind of the
     * session is in flight (claiming).
     */
    fun startEpochClaim(session: Any, source: EarningsProtocolSource, epochs: List<Long>, rows: List<ClaimProgress>): Boolean {
        if (claiming(session)) {
            return false
        }
        val started = EpochClaim(session, rows)
        epochClaim = started
        source.claim(epochs) { event ->
            scope.launch { onEpochEvent(started, event) }
        }
        return true
    }

    private fun onEpochEvent(of: EpochClaim, event: ClaimEvent) {
        if (epochClaim !== of) {
            return
        }
        if (event != ClaimEvent.Done) {
            of.rows = claimProgressAfter(of.rows, event)
            listeners(of.session).forEach { it.epochClaimMoved(of.rows) }
            return
        }
        epochClaim = null
        val settled = claimProgressSettled(of.rows)
        listeners(of.session).forEach { it.epochClaimDone(settled) }
    }

    /**
     * Claims every v2 account for [session] with [source] (SnLedgerClaim),
     * from the rows it starts with ([rows], snLedgerClaimStart) and the
     * transfer floor they were listed by. False, and no claim, while a claim
     * of either kind of the session is in flight: one at a time, as the gas
     * key sends one (claiming).
     */
    fun start(session: Any, source: EarningsProtocolSource, rows: List<SnLedgerClaimProgress>, transferFloorRao: Long): Boolean {
        if (claiming(session)) {
            return false
        }
        val started = Claim(session, transferFloorRao, snLedgerClaimMovingRao(rows, transferFloorRao), rows)
        claim = started
        source.ledgerClaim { event ->
            scope.launch { onEvent(started, event) }
        }
        return true
    }

    private fun onEvent(of: Claim, event: SnLedgerClaimEvent) {
        if (claim !== of) {
            return
        }
        if (event != SnLedgerClaimEvent.Done) {
            of.read = of.read || snLedgerClaimReadLedger(event)
            of.rows = snLedgerClaimProgressAfter(of.rows, event)
            listeners(of.session).forEach { it.claimMoved(of.rows, of.movingRao) }
            return
        }
        claim = null
        val settled = snLedgerClaimSettled(of.rows, of.read)
        listeners(of.session).forEach { it.claimDone(settled, of.transferFloorRao) }
        // a claim may have paid capture the flag's wins, whose lines go once
        // the server reads them claimed
        readWinsAfterClaim(of.session, snLedgerClaimPaid(settled))
    }

    /**
     * After a claim's Done the wins are read at once, on their own. When the
     * claim paid an account they are read again at each of
     * [SN_CTF_WINS_READS_AFTER_CLAIM_MS] after Done, since the read at Done
     * can still list a win the claim paid; each read goes on its own, in the
     * flow's scope, so a slow answer moves none of the later reads. The next
     * claim that paid starts the reads over, and one that paid nothing leaves
     * them to run. They read through the flows attached at their times: none
     * while the activity is gone, and the flow of one that came back reads
     * those still to come. A sign-out or another device ends them (attach).
     */
    private fun readWinsAfterClaim(session: Any, paid: Boolean) {
        if (!paid) {
            readWins(session)
            return
        }
        winsAfterClaim?.cancel()
        winsSession = session
        winsAfterClaim = scope.launch {
            readWins(session)
            var afterDone = 0L
            for (at in SN_CTF_WINS_READS_AFTER_CLAIM_MS) {
                sleep(at - afterDone)
                afterDone = at
                readWins(session)
            }
        }
    }

    private fun readWins(session: Any) {
        listeners(session).forEach { it.readWins() }
    }

    // a copy: a listener may detach while it hears
    private fun listeners(session: Any): List<Listener> =
        attached.filter { it.session === session }.map { it.listener }
}

// ---- the tile and its dialog

/**
 * The Settlement v2 tile's accounts, its won lines and its claim dialog. The
 * earnings view model owns one and points it at the device's protocol source
 * and its session (reset); the SDK reads and claims, this keeps where that
 * stands. A claim in flight is the session's, not the flow's (the app's
 * SnClaimHolder, [claims]): it outlives its dialog, the screen and the
 * view model with its activity. Opening the dialog during it shows it rather
 * than starting another, its events and its Done land in whichever flow of
 * the session is there, and the accounts and the wins are read again after
 * its Done. Only another device or a sign-out (a reset for another session)
 * drops it.
 */
class SnLedgerFlow(
    private val scope: CoroutineScope,
    private val hasWallet: () -> Boolean,
    // the claim relay's transfer floor in rao of α at its last price, 0
    // without an answer: the dialog offers no claim for an account under it
    private val transferFloorRao: () -> Long = { 0 },
    // the claim in flight and the reads of the wins after its Done: the
    // process's in the app, so that they outlive the view model
    private val claims: SnClaimHolder = SnClaimHolder(scope),
) {
    private var source: EarningsProtocolSource = NoProtocolSource

    // the session the flow draws, the signed-in device; null for none
    private var session: Any? = null

    // the holder's word of the session's claims, until the next reset or the flow's end
    private var detach: (() -> Unit)? = null

    private val _load = MutableStateFlow<SnLedgerLoad>(SnLedgerLoad.Loading)
    val load: StateFlow<SnLedgerLoad> = _load.asStateFlow()

    // the capture the flag slots won and not claimed yet (GET /sn/ctf/client),
    // newest first, a line each on the tile: the session's (the holder's), as
    // the newest read's good answer listed them, a network's over a client's,
    // so a view model of a new activity finds them; empty after a sign-in or
    // another device until the first answer
    val wins: StateFlow<List<SnCtfWinState>> = claims.wins

    private val _claimConfigured = MutableStateFlow(false)
    val claimConfigured: StateFlow<Boolean> = _claimConfigured.asStateFlow()

    private val _dialog = MutableStateFlow<SnLedgerClaimDialogState?>(null)
    val dialog: StateFlow<SnLedgerClaimDialogState?> = _dialog.asStateFlow()

    // the session's v2 claim is in flight: the tile's Claim opens it
    // (SnLedgerTile.withClaimInFlight)
    private val _claimInFlight = MutableStateFlow(false)
    val claimInFlight: StateFlow<Boolean> = _claimInFlight.asStateFlow()

    private val listener = object : SnClaimHolder.Listener {
        override fun claimMoved(rows: List<SnLedgerClaimProgress>, movingRao: Long) {
            _claimInFlight.value = true
            if (_dialog.value is SnLedgerClaimDialogState.Sending) {
                _dialog.value = SnLedgerClaimDialogState.Sending(rows, movingRao)
            }
        }

        override fun claimDone(settled: List<SnLedgerClaimProgress>, transferFloorRao: Long) {
            _claimInFlight.value = false
            scope.launch { finishClaim(settled, transferFloorRao) }
        }

        override fun readWins() {
            scope.launch { refreshWins() }
        }
    }

    init {
        // the flow ends with its scope, the view model's: the holder tells it nothing more
        scope.coroutineContext.job.invokeOnCompletion {
            detach?.invoke()
            detach = null
        }
    }

    /**
     * A new device (or none: signed out), or this flow's first: drop what the
     * last one read, and hear of [session]'s claims. A claim in flight of the
     * same session (the device, which a view model of a new activity finds
     * again) goes on, and so do its won lines; one of another session ends,
     * with the reads of the wins after it and its lines.
     */
    fun reset(source: EarningsProtocolSource, session: Any? = source) {
        this.source = source
        this.session = session
        _load.value = SnLedgerLoad.Loading
        _claimConfigured.value = false
        _dialog.value = null
        detach?.invoke()
        detach = claims.attach(listener, session)
        _claimInFlight.value = claims.inFlight(session) != null
    }

    /**
     * Reads the wins not claimed yet, with or without a wallet: they are the
     * network's. The lines show the newest read's good answer, by the order
     * the reads started, and a network's answer beats a client's (every app's
     * rules, 2026-11-12; SnCtfWinsLines). A read that failed or could not be
     * read changes nothing, so it never blanks them, nor keeps an older
     * read's good answer still to land from showing; nor does a client's
     * answer once a network's has shown. Only an answer that no longer lists
     * a win, a sign-out or another device (reset) takes a line away.
     */
    suspend fun refreshWins() {
        val s = source
        val owner = session
        val read = claims.startWinsRead()
        val answer = s.ctfWins()
        if (s !== source) {
            return
        }
        claims.applyWins(owner, read, answer.getOrNull())
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

    /** The tile's Claim: the dialog, or the session's claim still in flight. */
    fun open() {
        claims.inFlight(session)?.let {
            _dialog.value = SnLedgerClaimDialogState.Sending(it.rows, it.movingRao)
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
        if (_dialog.value == null || claims.inFlight(session) != null) {
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
     * no claim the runtime would take. Never while a claim of the session is
     * in flight, whichever dialog started it, nor while its v1 claim is: the
     * gas key sends one claim at a time, and the dialog's Claim waits for it
     * (SnClaimHolder.claiming).
     */
    fun claim() {
        val ready = _dialog.value as? SnLedgerClaimDialogState.Ready ?: return
        val owner = session ?: return
        if (ready.takes.isEmpty() || claims.claiming(owner)) {
            return
        }
        val floorRao = ready.transferFloorRao
        val progress = snLedgerClaimStart(ready.accounts, ready.unavailablePools, floorRao)
        // the dialog shows the claim before the SDK's first event, which may come at once
        _dialog.value = SnLedgerClaimDialogState.Sending(progress, snLedgerClaimMovingRao(progress, floorRao))
        _claimInFlight.value = true
        claims.start(owner, source, progress, floorRao)
    }

    // the session's claim is done: its dialog, if it shows the claim, then the
    // accounts read again; [floorRao] is the floor the claim started with
    private suspend fun finishClaim(settled: List<SnLedgerClaimProgress>, floorRao: Long) {
        val s = source
        val finished = snLedgerClaimFinished(settled, s.gasKey(), floorRao) { s.gasBalance().getOrNull()?.tao ?: 0.0 }
        if (s !== source) {
            return
        }
        if (_dialog.value is SnLedgerClaimDialogState.Sending) {
            _dialog.value = finished
        }
        refresh()
    }
}
