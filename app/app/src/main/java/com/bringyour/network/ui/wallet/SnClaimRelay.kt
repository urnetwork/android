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
    // each of the network's accounts, including a wallet's it replaced, whose
    // balance URnetwork keeps claiming
    val accounts: List<SnClaimRelayAccountState> = emptyList(),
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
 * One of the network's accounts in the operator's pool, as the relay sees it
 * (sdk SnClaimRelayAccount). Amounts are rao of SN25α.
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
) {
    /**
     * The claim sheet's row reads Claimed, with what URnetwork's claim paid
     * and its transaction: that claim is the latest and nothing is claimable
     * since. The sdk's ShowsClaimed rule, which it decides for the primary
     * account only, applied to each row.
     */
    val showsClaimed: Boolean
        get() = claimTxHash.isNotEmpty() && claimableRao <= 0
}

/**
 * The claim sheet's rows: each account with something to claim, Pending, and
 * each URnetwork claimed with nothing claimable since, Claimed.
 */
fun snClaimRelayRows(status: SnClaimStatusState): List<SnClaimRelayAccountState> =
    status.accounts.filter { 0 < it.claimableRao || it.showsClaimed }

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
 * as before, or, for a network with no wallet on this device, the balance the
 * relay claims for its consent, while the sdk says the tile shows.
 */
fun snLedgerTileWithRelay(hasWallet: Boolean, ledgerTile: SnLedgerTile?, claimStatus: SnClaimStatusState?): SnLedgerTile? {
    if (hasWallet && ledgerTile != null) {
        return ledgerTile
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
