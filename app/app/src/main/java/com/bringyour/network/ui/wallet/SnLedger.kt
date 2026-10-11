package com.bringyour.network.ui.wallet

import com.bringyour.network.R
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
 */

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
)

/** The v2 accounts after a read. */
sealed class SnLedgerLoad {
    object Loading : SnLedgerLoad()

    /** the ledger could not be read or its answer was refused (ledger_unavailable) */
    object Unavailable : SnLedgerLoad()

    /** one account per pool; empty when the ledger has none for the coldkey */
    data class Loaded(val accounts: List<SnLedgerAccountState>) : SnLedgerLoad()
}

/** One account in the Settlement v2 tile: Claimable first, then Paid and Earned. */
data class SnLedgerAccountLines(
    val noId: Long,
    val claimableRao: Long,
    val paidRao: Long,
    val earnedRao: Long,
    // "Pending: +X, awaiting finalization", X = pending less finalized; null when nothing is pending
    val pendingIncreaseRao: Long?,
)

/** What the Settlement v2 tile shows. */
data class SnLedgerTile(
    // null while loading and when unavailable
    val accounts: List<SnLedgerAccountLines>?,
    val unavailable: Boolean,
    // the Claim action, once vault2's address is configured
    val showClaim: Boolean,
    val claimEnabled: Boolean,
)

fun SnLedgerAccountState.lines() = SnLedgerAccountLines(
    noId = noId,
    claimableRao = claimableRao,
    paidRao = paidRao,
    earnedRao = finalizedRao,
    pendingIncreaseRao = (pendingRao - finalizedRao).takeIf { pending && 0 < it },
)

/**
 * The tile, or null to hide it. Before vault2's address is configured (before
 * activation) there is no Claim, and the tile shows only balances the ledger
 * returned. Once it is configured the tile is always there: a spinner while
 * loading, "Unavailable" when the read failed, and Claim enabled only while
 * the loaded accounts have something to claim.
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
            )
        }
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
}

/** One account of a claim: the SDK sends one transaction per account. */
data class SnLedgerClaimProgress(
    val noId: Long,
    val index: Long,
    // the claimable when the claim starts; the vault's Claimed amount once confirmed
    val amountRao: Long,
    val status: SnLedgerClaimStatus,
    val txHash: String?,
    // the SDK's "<code>: <detail>"
    val message: String?,
)

/** The claim dialog of settlement v2. */
sealed class SnLedgerClaimDialogState {
    object Loading : SnLedgerClaimDialogState()
    data class Ready(
        val accounts: List<SnLedgerAccountState>,
        val totalRao: Long,
        val gasKey: SnGasKeyState?,
        val gasTao: Double?,
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
 * accounts with something to claim, or the gas top-up when the gas key cannot
 * pay for a claim.
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
        SnLedgerClaimDialogState.Ready(claimable, total, gasKey, gasTao)
    }
}

/** A claim starts with a pending row for each account it claims. */
fun snLedgerClaimStart(accounts: List<SnLedgerAccountState>): List<SnLedgerClaimProgress> =
    accounts.filter { 0 < it.claimableRao }.map {
        SnLedgerClaimProgress(it.noId, it.index, it.claimableRao, SnLedgerClaimStatus.PENDING, null, null)
    }

/**
 * The rows after one event. `already_claimed` is not a failure: a third
 * party's claim paid the same coldkey. A failure that is not about one
 * account (no wallet, v2 claims not configured, the ledger or the chain
 * unreachable) comes with pool 0 and fails every row not sent. An event for
 * an account without a row (the SDK reads the accounts again) adds its row.
 */
fun snLedgerClaimProgressAfter(
    progress: List<SnLedgerClaimProgress>,
    event: SnLedgerClaimEvent,
): List<SnLedgerClaimProgress> {
    if (event is SnLedgerClaimEvent.Failed && event.noId == 0L) {
        return progress.map {
            if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = SnLedgerClaimStatus.FAILED, message = event.message) else it
        }
    }
    val (noId, index) = when (event) {
        is SnLedgerClaimEvent.Sent -> event.noId to event.index
        is SnLedgerClaimEvent.Confirmed -> event.noId to event.index
        is SnLedgerClaimEvent.Failed -> event.noId to event.index
        SnLedgerClaimEvent.Done -> return progress
    }
    val i = progress.indexOfFirst { it.noId == noId && it.index == index }
    val row = progress.getOrNull(i) ?: SnLedgerClaimProgress(noId, index, 0, SnLedgerClaimStatus.PENDING, null, null)
    val updated = when (event) {
        is SnLedgerClaimEvent.Sent -> row.copy(status = SnLedgerClaimStatus.SENT, txHash = event.txHash)
        is SnLedgerClaimEvent.Confirmed ->
            row.copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = event.txHash, amountRao = event.amountRao)
        is SnLedgerClaimEvent.Failed -> row.copy(
            status = if (EarningsViewModel.claimFailureCode(event.message) == EarningsViewModel.SN_CODE_ALREADY_CLAIMED) {
                SnLedgerClaimStatus.ALREADY_CLAIMED
            } else {
                SnLedgerClaimStatus.FAILED
            },
            message = event.message,
        )
        SnLedgerClaimEvent.Done -> row
    }
    return if (i < 0) progress + updated else progress.toMutableList().also { it[i] = updated }
}

/**
 * The dialog after Done: the gas top-up when every account failed for want of
 * gas, with the gas key's balance read again by [gasTao]; else the rows with
 * the confirmed total. A row the SDK never reported on was not sent.
 */
suspend fun snLedgerClaimFinished(
    progress: List<SnLedgerClaimProgress>,
    gasKey: SnGasKeyState?,
    gasTao: suspend () -> Double,
): SnLedgerClaimDialogState {
    val settled = progress.map {
        if (it.status == SnLedgerClaimStatus.PENDING) it.copy(status = SnLedgerClaimStatus.FAILED) else it
    }
    val allNeedGas = settled.isNotEmpty() && settled.all {
        it.status == SnLedgerClaimStatus.FAILED &&
            EarningsViewModel.claimFailureCode(it.message) == EarningsViewModel.SN_CODE_NEEDS_GAS
    }
    if (allNeedGas) {
        return SnLedgerClaimDialogState.NeedsGas(gasKey, gasTao(), settled.sumOf { it.amountRao })
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

    /** A new device (or none): drop what the last one read. */
    fun reset(source: EarningsProtocolSource) {
        this.source = source
        _load.value = SnLedgerLoad.Loading
        _claimConfigured.value = false
        _dialog.value = null
        claiming = null
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
            onSuccess = { SnLedgerLoad.Loaded(it) },
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

    /** The dialog's Claim: the SDK claims every account with something to claim. */
    fun claim() {
        val ready = _dialog.value as? SnLedgerClaimDialogState.Ready ?: return
        if (ready.accounts.isEmpty() || claiming != null) {
            return
        }
        val s = source
        val progress = snLedgerClaimStart(ready.accounts)
        claiming = progress
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
            val next = snLedgerClaimProgressAfter(progress, event)
            claiming = next
            if (_dialog.value is SnLedgerClaimDialogState.Sending) {
                _dialog.value = SnLedgerClaimDialogState.Sending(next)
            }
            return
        }
        claiming = null
        val finished = snLedgerClaimFinished(progress, s.gasKey()) { s.gasBalance().getOrNull()?.tao ?: 0.0 }
        if (s === source && _dialog.value is SnLedgerClaimDialogState.Sending) {
            _dialog.value = finished
        }
        refresh()
    }
}
