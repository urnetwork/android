package com.bringyour.network.ui.wallet

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.sdk.Sdk
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * The claim relay on Earnings (the mocks' flow 4): under the Settlement v2
 * tile's balances, who claims and why a balance is still pending, Claim now
 * with its limit, or, while the relay isn't claiming, the device's own claim
 * with its gas key; and the claim dialog Claim now opens. The sdk's claim
 * status decides every line here (SnClaimStatus); these only word it.
 */

const val EARNINGS_CLAIM_RELAY_CLAIM_NOW_TAG = "earnings-claim-relay-claim-now"
const val EARNINGS_CLAIM_RELAY_SELF_CLAIM_TAG = "earnings-claim-relay-self-claim"
const val EARNINGS_CLAIM_RELAY_CLAIMED_TAG = "earnings-claim-relay-claimed"

/** Who claims, why the balance is still pending, and on the tile Claim now, or the device's own claim while the relay isn't claiming. */
@Composable
fun SnClaimRelaySection(
    status: SnClaimStatusState,
    onClaimNow: () -> Unit,
    onSelfClaim: () -> Unit,
    // in the claim dialog, which has its own Claim now
    inDialog: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (status.relayClaims) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = null,
                    tint = BlueMedium,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(id = R.string.sn_claim_relay_for_you),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        if (0 < status.claimableRao) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                SnClaimRelayPill(stringResource(id = R.string.pending_data_key))
                Spacer(modifier = Modifier.width(8.dp))
                SnClaimRelayReason(status = status, modifier = Modifier.weight(1f))
            }
        }
        if (!inDialog) {
            Spacer(modifier = Modifier.height(16.dp))
            if (status.relayClaims) {
                URButton(
                    onClick = onClaimNow,
                    style = ButtonStyle.SECONDARY,
                    enabled = status.claimNowAvailable,
                    modifier = Modifier.testTag(EARNINGS_CLAIM_RELAY_CLAIM_NOW_TAG),
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.sn_claim_relay_claim_now), style = buttonTextStyle)
                }
                SnClaimNowNote(status = status, eachColdkey = false)
            } else {
                URButton(onClick = {}, style = ButtonStyle.SECONDARY, enabled = false) { buttonTextStyle ->
                    Text(stringResource(id = R.string.sn_claim_relay_claim_now), style = buttonTextStyle)
                }
                SnClaimRelayNote(stringResource(id = R.string.sn_claim_relay_waits_for_relay))
                if (status.selfClaimAvailable) {
                    BittensorKeyCardLink(
                        text = stringResource(id = R.string.sn_claim_relay_self_claim),
                        onClick = onSelfClaim,
                        testTag = EARNINGS_CLAIM_RELAY_SELF_CLAIM_TAG,
                    )
                }
            }
        }
    }
}

/** Why a balance is still pending (sdk SnClaimReason*). */
@Composable
fun SnClaimRelayReason(status: SnClaimStatusState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        when (status.reason) {
            Sdk.SnClaimReasonUnderThreshold -> {
                SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_under_threshold))
                if (0 < status.automaticThresholdTaoRao) {
                    if (0 < status.automaticThresholdRao) {
                        SnClaimRelayReasonDetail(
                            stringResource(
                                id = R.string.sn_claim_relay_under_threshold_body,
                                EarningsFormat.taoAmount(status.automaticThresholdTaoRao),
                                EarningsFormat.approx(status.automaticThresholdRao),
                            )
                        )
                    } else {
                        SnClaimRelayReasonDetail(
                            stringResource(
                                id = R.string.sn_claim_relay_under_threshold_no_price,
                                EarningsFormat.taoAmount(status.automaticThresholdTaoRao),
                            )
                        )
                    }
                }
            }
            Sdk.SnClaimReasonQueued -> SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_queued))
            Sdk.SnClaimReasonCapReached -> SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_cap))
            Sdk.SnClaimReasonRefused -> SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_refused))
            Sdk.SnClaimReasonRelayNotRunning -> {
                SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_not_running))
                SnClaimRelayReasonDetail(stringResource(id = R.string.sn_claim_relay_waits))
            }
            Sdk.SnClaimReasonRelayPaused -> {
                SnClaimRelayReasonTitle(stringResource(id = R.string.sn_claim_relay_paused))
                SnClaimRelayReasonDetail(stringResource(id = R.string.sn_claim_relay_waits))
            }
            else -> {}
        }
    }
}

/** Under Claim now: who pays and how often, or why it can't be tapped now (sdk SnClaimNowReason*). */
@Composable
fun SnClaimNowNote(status: SnClaimStatusState, eachColdkey: Boolean) {
    val text = if (status.claimNowAvailable) {
        when {
            status.claimNowPeriodDays <= 0 -> null
            eachColdkey -> stringResource(id = R.string.sn_claim_relay_claim_now_paid_each, status.claimNowPeriodDays)
            else -> stringResource(id = R.string.sn_claim_relay_claim_now_paid, status.claimNowPeriodDays)
        }
    } else {
        when (status.claimNowReason) {
            Sdk.SnClaimNowReasonUnderFloor ->
                if (0 < status.transferFloorTaoRao) {
                    stringResource(id = R.string.sn_claim_relay_under_floor, EarningsFormat.taoAmount(status.transferFloorTaoRao))
                } else {
                    null
                }
            Sdk.SnClaimNowReasonRequested -> stringResource(id = R.string.sn_claim_relay_requested)
            Sdk.SnClaimNowReasonLimit ->
                if (0 < status.claimNowNextAtMillis) {
                    stringResource(id = R.string.sn_claim_relay_again_on, snClaimNowDate(status.claimNowNextAtMillis))
                } else {
                    null
                }
            else -> null
        }
    }
    text?.let { SnClaimRelayNote(it) }
}

/** The day Claim now is available again, in the reader's own form. */
@Composable
private fun snClaimNowDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

@Composable
private fun SnClaimRelayNote(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )
}

@Composable
private fun SnClaimRelayReasonTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun SnClaimRelayReasonDetail(text: String) {
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
    )
}

@Composable
private fun SnClaimRelayPill(text: String) {
    Text(
        text,
        modifier = Modifier
            .background(TextMuted.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * The claimed state (the mocks' 4d): URnetwork's claim is the latest and
 * nothing is claimable now. What it paid, and its transaction on the
 * explorer, as the sdk's claim status names them.
 */
@Composable
private fun SnClaimRelayClaimed(
    status: SnClaimStatusState,
    formatAlpha: (Long) -> String,
    explorerTxUrl: (String) -> String,
) {
    val context = LocalContext.current
    val txUrl = status.claimTxUrl.ifEmpty { if (status.claimTxHash.isEmpty()) "" else explorerTxUrl(status.claimTxHash) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Green.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .border(1.dp, Green.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag(EARNINGS_CLAIM_RELAY_CLAIMED_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(id = R.string.claim_confirmed),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Green,
        )
        if (0 < status.claimAmountRao) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                formatAlpha(status.claimAmountRao),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (txUrl.isNotEmpty()) {
            Text(
                stringResource(id = R.string.transaction),
                modifier = Modifier
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, txUrl.toUri()))
                    }
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = BlueMedium,
            )
        }
    }
}

/**
 * Claim now, from the Settlement v2 tile: the balance, where it lands, why
 * it's still pending, and Claim now or why it can't act, with the device's
 * own claim as the fallback, and once URnetwork has claimed it, what it paid
 * with its transaction. It shows the claim sheet's disclaimer (sdk place
 * claim_sheet): claims land on the consent's coldkey, wherever its key is.
 */
@Composable
fun ClaimRelayDialog(
    visible: Boolean,
    status: SnClaimStatusState?,
    outcome: SnClaimNowOutcome?,
    claimingNow: Boolean,
    fundsDisclaimer: String,
    onClaimNow: () -> Unit,
    onSelfClaim: () -> Unit,
    onDismiss: () -> Unit,
    formatAlpha: (Long) -> String,
    shortSs58: (String) -> String,
    // the explorer's page of a transaction, where the sdk names none
    explorerTxUrl: (String) -> String = { "" },
) {
    URDialog(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(id = R.string.claim_alpha_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(id = R.string.sn_ledger_title),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (status != null && !status.unavailable) {
                Text(
                    stringResource(id = R.string.sn_ledger_claimable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    formatAlpha(status.claimableRao),
                    style = TextStyle(
                        fontFamily = gravityCondensedFamily,
                        fontWeight = FontWeight(900),
                        fontSize = 40.sp,
                        color = Color.White
                    )
                )
                if (status.coldkeySs58.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.sn_claim_relay_lands_on, shortSs58(status.coldkeySs58)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (status.showsClaimed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SnClaimRelayClaimed(status = status, formatAlpha = formatAlpha, explorerTxUrl = explorerTxUrl)
                }
                Spacer(modifier = Modifier.height(12.dp))
                SnClaimRelaySection(status = status, onClaimNow = {}, onSelfClaim = onSelfClaim, inDialog = true)
                Spacer(modifier = Modifier.height(16.dp))
                when (outcome) {
                    SnClaimNowOutcome.Requested -> {
                        Text(
                            stringResource(id = R.string.sn_claim_relay_requested),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        URButton(onClick = {}, enabled = false) { buttonTextStyle ->
                            Text(stringResource(id = R.string.sn_claim_relay_queued_button), style = buttonTextStyle)
                        }
                    }
                    is SnClaimNowOutcome.Refused -> SnClaimNowNote(
                        status = status.copy(
                            claimNowAvailable = false,
                            claimNowReason = outcome.reason,
                            claimNowNextAtMillis = outcome.nextAtMillis,
                        ),
                        eachColdkey = true,
                    )
                    is SnClaimNowOutcome.Failed -> Text(
                        claimsReadFailureText(outcome.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                    null -> if (status.relayClaims) {
                        URButton(
                            onClick = onClaimNow,
                            enabled = status.claimNowAvailable,
                            isProcessing = claimingNow,
                            modifier = Modifier.testTag(EARNINGS_CLAIM_RELAY_CLAIM_NOW_TAG),
                        ) { buttonTextStyle ->
                            Text(stringResource(id = R.string.sn_claim_relay_claim_now), style = buttonTextStyle)
                        }
                        SnClaimNowNote(status = status, eachColdkey = true)
                    } else {
                        SnClaimRelayNote(stringResource(id = R.string.sn_claim_relay_waits_for_relay))
                    }
                }
                if (status.selfClaimAvailable) {
                    BittensorKeyCardLink(
                        text = stringResource(id = R.string.sn_claim_relay_self_claim),
                        onClick = onSelfClaim,
                        testTag = EARNINGS_CLAIM_RELAY_SELF_CLAIM_TAG,
                    )
                }
            } else {
                Text(
                    stringResource(id = R.string.sn_ledger_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer, boxed = true)
            Spacer(modifier = Modifier.height(16.dp))
            URButton(onClick = onDismiss, style = ButtonStyle.OUTLINE) { buttonTextStyle ->
                Text(stringResource(id = R.string.close), style = buttonTextStyle)
            }
        }
    }
}
