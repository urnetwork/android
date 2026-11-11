package com.bringyour.network.ui.wallet

import com.bringyour.sdk.Sdk

/*
 * Claims through URnetwork's claim relay (apple/BTEASY.md, Claim relay, and
 * the approved mocks' flow 4). URnetwork claims every consented network's
 * settlement v2 balance and pays the gas: by itself once a balance is worth
 * the automatic threshold, and on Claim now, at most once in a period for
 * each coldkey. Small amounts wait and say why. The device's own claim with
 * its gas key stays as the fallback. The sdk decides all of it
 * (SnClaimStatus); the screens only word it.
 */

/**
 * What the relay says of the network's v2 balance (sdk SnClaimStatusResult):
 * who claims it, why it is still pending, and what Claim now can do. Amounts
 * are rao of SN25α unless they say TAO.
 */
data class SnClaimStatusState(
    // the relay's state, sdk SnClaimRelayStateUnavailable without an answer
    val relayState: String = "",
    val coldkeySs58: String = "",
    // sdk SnClaimAction*: what the main control does
    val action: String = "",
    // sdk SnClaimReason*: why the balance is pending
    val reason: String = "",
    val claimableRao: Long = 0,
    val valueTaoRao: Long = 0,
    val pendingRao: Long = 0,
    val claimNowAvailable: Boolean = false,
    // sdk SnClaimNowReason*, or an SnClaimReason*, while Claim now can't act
    val claimNowReason: String = "",
    val claimNowNextAtMillis: Long = 0,
    // the device's own claim with its gas key can claim this now
    val selfClaimAvailable: Boolean = false,
    val selfClaimNeedsGas: Boolean = false,
    // at the relay's last price, 0 without one
    val automaticThresholdRao: Long = 0,
    // the runtime's transfer floor at the relay's last price, 0 without one: an
    // account under it reads Below the claim minimum, since no claim can move it
    val transferFloorRao: Long = 0,
    // rao of TAO
    val automaticThresholdTaoRao: Long = 0,
    val transferFloorTaoRao: Long = 0,
    val claimNowPeriodDays: Long = 0,
    // a network with no wallet on this device shows the Settlement v2 tile
    // exactly while this is true
    val showsTile: Boolean = false,
    // the claimed state: URnetwork's claim is the latest and nothing is
    // claimable now, with its transaction, the explorer's page of it ("" without
    // one) and what it paid
    val showsClaimed: Boolean = false,
    val claimTxHash: String = "",
    val claimTxUrl: String = "",
    val claimAmountRao: Long = 0,
    // each of the network's accounts, one per coldkey and pool, including a
    // wallet's it replaced, whose balance URnetwork keeps claiming. The top
    // level adds up the primary coldkey's, so Claim now claims all its pools
    val accounts: List<SnClaimRelayAccountState> = emptyList(),
    // the pools whose balances could not be read just now (sdk
    // UnavailablePools): a row each, unavailable, never zero; the top level
    // leaves them out
    val unavailablePools: List<SnLedgerPool> = emptyList(),
    // the balances could not be read: unavailable, never zero
    val unavailable: Boolean = false,
) {
    /**
     * URnetwork is the one claiming: it is, or it will once a balance or a new
     * day allows. Otherwise the device's own claim is the way for now.
     */
    val relayClaims: Boolean
        get() = reason != Sdk.SnClaimReasonRelayNotRunning && reason != Sdk.SnClaimReasonRelayPaused
}

/**
 * One of the network's accounts in one of the operator's pools, as the relay
 * sees it (sdk SnClaimRelayAccount). Amounts are rao of SN25α.
 */
data class SnClaimRelayAccountState(
    val coldkeySs58: String = "",
    val noId: Long = 0,
    val claimableRao: Long = 0,
    // the account's latest claim when URnetwork sent it: the transaction, the
    // explorer's page of it ("" without one) and what it paid; "" and 0 for a
    // claim anyone else sent, or none
    val claimTxHash: String = "",
    val claimTxUrl: String = "",
    val claimAmountRao: Long = 0,
    // sdk SnLedgerPoolKind*, which names the row
    val kind: String = snLedgerPoolKindOf(noId),
) {
    /**
     * The claim sheet's row reads Claimed, with what URnetwork's claim paid
     * and its transaction: that claim is the latest and nothing is claimable
     * since. The sdk's ShowsClaimed rule, which it decides for the primary
     * account only, applied to each row.
     */
    val showsClaimed: Boolean
        get() = claimTxHash.isNotEmpty() && claimableRao <= 0

    val pool: SnLedgerPool
        get() = SnLedgerPool(noId, kind)
}

/**
 * The claim sheet's rows: each account with something to claim, Pending, and
 * each URnetwork claimed with nothing claimable since, Claimed. The pools the
 * relay could not read follow them (SnClaimStatusState.unavailablePools).
 */
fun snClaimRelayRows(status: SnClaimStatusState): List<SnClaimRelayAccountState> =
    status.accounts.filter { 0 < it.claimableRao || it.showsClaimed }

/** Every pool of the relay's answer, which names its rows (snLedgerPoolName). */
fun snClaimRelayPools(status: SnClaimStatusState): List<SnLedgerPool> =
    status.accounts.map { it.pool } + status.unavailablePools

/** What a row of the relay's pools says where its pill sits. */
enum class SnClaimRelayRowStatus {
    PENDING,
    // sn_ledger_pool_under_floor: under the transfer floor at the relay's last
    // price, so no claim, URnetwork's or the device's, can move it yet
    UNDER_FLOOR,
    CLAIMED,
}

fun snClaimRelayRowStatus(row: SnClaimRelayAccountState, transferFloorRao: Long): SnClaimRelayRowStatus = when {
    row.showsClaimed -> SnClaimRelayRowStatus.CLAIMED
    0 < transferFloorRao && 0 < row.claimableRao && row.claimableRao < transferFloorRao -> SnClaimRelayRowStatus.UNDER_FLOOR
    else -> SnClaimRelayRowStatus.PENDING
}

/**
 * The tile's rows for a network with no wallet on this device, under the
 * claimable it adds up: the primary coldkey's accounts with something to
 * claim, once there are two or one of capture the flag's, and beside any pool
 * the relay could not read. With one operator pool and nothing unavailable the
 * claimable says it all, as before.
 */
fun snClaimRelayTileRows(status: SnClaimStatusState): List<SnClaimRelayAccountState> {
    val rows = status.accounts.filter { it.coldkeySs58 == status.coldkeySs58 && 0 < it.claimableRao }
    return if (status.unavailablePools.isNotEmpty() || snLedgerNamesPools(rows.map { it.pool })) rows else emptyList()
}

/** The answer to a Claim now tap (sdk SnClaimNowResult). */
sealed class SnClaimNowOutcome {
    /** URnetwork sends it first, within the day's budget */
    object Requested : SnClaimNowOutcome()

    /** not taken: an SnClaimNowReason* or SnClaimReason*, and when the coldkey's limit ends for a tap the limit refused */
    data class Refused(val reason: String, val nextAtMillis: Long) : SnClaimNowOutcome()

    /** the sdk's "code: message", for claimsReadFailureText */
    data class Failed(val message: String) : SnClaimNowOutcome()
}

/**
 * The Settlement v2 tile with the relay: the ledger of this device's wallet
 * as before, with the relay's transfer floor marking the accounts under it,
 * or, for a network with no wallet on this device, the balance the relay
 * claims for its consent, while the sdk says the tile shows.
 */
fun snLedgerTileWithRelay(hasWallet: Boolean, ledgerTile: SnLedgerTile?, claimStatus: SnClaimStatusState?): SnLedgerTile? {
    if (hasWallet && ledgerTile != null) {
        return if (claimStatus == null || claimStatus.unavailable) ledgerTile else ledgerTile.withTransferFloor(claimStatus.transferFloorRao)
    }
    if (claimStatus == null || !claimStatus.showsTile) {
        return null
    }
    return SnLedgerTile(
        accounts = null,
        unavailable = false,
        showClaim = false,
        claimEnabled = false,
        relayClaimableRao = claimStatus.claimableRao,
    )
}

/**
 * The tile says who claims and why, in place of its own Claim: the relay
 * answered and has something to say, a balance to claim or URnetwork's claim.
 */
fun snClaimRelaySpeaks(relay: SnClaimStatusState?): Boolean =
    relay != null && !relay.unavailable &&
        (relay.showsClaimed || relay.action != Sdk.SnClaimActionNone || 0 < relay.claimableRao)
