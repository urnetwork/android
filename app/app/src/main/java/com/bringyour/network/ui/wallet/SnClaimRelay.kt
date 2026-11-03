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
 * relay claims for its consent, while it reports one claimable or pending.
 */
fun snLedgerTileWithRelay(hasWallet: Boolean, ledgerTile: SnLedgerTile?, claimStatus: SnClaimStatusState?): SnLedgerTile? {
    if (hasWallet && ledgerTile != null) {
        return ledgerTile
    }
    if (claimStatus == null || claimStatus.unavailable || claimStatus.claimableRao + claimStatus.pendingRao <= 0) {
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

/** The tile says who claims and why, in place of its own Claim: the relay answered and has something to say. */
fun snClaimRelaySpeaks(relay: SnClaimStatusState?): Boolean =
    relay != null && !relay.unavailable && (relay.action != Sdk.SnClaimActionNone || 0 < relay.claimableRao)
