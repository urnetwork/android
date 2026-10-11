package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily

/**
 * The Settlement v2 tile, under the v1 Unclaimed tile: per account Claimable
 * first, then Paid, Earned and what a pending root adds; "Unavailable" when
 * the ledger cannot be read, never zero; Claim once vault2 is configured.
 */
@Composable
fun SnLedgerTileBlock(
    tile: SnLedgerTile,
    onClaim: () -> Unit,
    formatAlpha: (Long) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(id = R.string.sn_ledger_title),
            style = MaterialTheme.typography.bodyLarge
        )

        when {
            tile.unavailable -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.sn_ledger_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red
                )
            }
            tile.accounts == null -> LedgerSpinner()
            else -> tile.accounts.forEach { account ->
                Spacer(modifier = Modifier.height(8.dp))
                if (1 < tile.accounts.size) {
                    Text(
                        stringResource(id = R.string.sn_ledger_pool, account.noId),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    stringResource(id = R.string.sn_ledger_claimable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    formatAlpha(account.claimableRao),
                    style = TextStyle(
                        fontFamily = gravityCondensedFamily,
                        fontWeight = FontWeight(900),
                        fontSize = 40.sp,
                        color = Color.White
                    )
                )
                LedgerAmountRow(stringResource(id = R.string.sn_ledger_paid), formatAlpha(account.paidRao))
                LedgerAmountRow(stringResource(id = R.string.sn_ledger_earned), formatAlpha(account.earnedRao))
                account.pendingIncreaseRao?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(id = R.string.sn_ledger_pending, formatAlpha(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        if (tile.showClaim) {
            Spacer(modifier = Modifier.height(16.dp))
            URButton(onClick = onClaim, enabled = tile.claimEnabled) { buttonTextStyle ->
                Text(stringResource(id = R.string.claim), style = buttonTextStyle)
            }
        }
    }
}

@Composable
private fun LedgerAmountRow(label: String, amount: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(amount, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun LedgerSpinner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = TextMuted,
            trackColor = TextFaint,
            strokeWidth = 2.dp
        )
    }
}

/**
 * The v2 claim dialog, as v1's: the accounts to claim and the gas key, the
 * gas top-up when the gas key cannot pay, then each account's transaction
 * (Sent with its explorer link, Claimed with the vault's amount, or the
 * failure) until Done.
 */
@Composable
fun SnLedgerClaimDialog(
    state: SnLedgerClaimDialogState?,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    onRetry: () -> Unit,
    formatAlpha: (Long) -> String,
    shortSs58: (String) -> String,
    explorerTxUrl: (String) -> String,
) {
    URDialog(
        visible = state != null,
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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

            when (state) {
                null -> {}
                SnLedgerClaimDialogState.Loading -> LedgerSpinner()
                is SnLedgerClaimDialogState.Ready -> LedgerReadyContent(
                    state = state,
                    onDismiss = onDismiss,
                    onClaim = onClaim,
                    formatAlpha = formatAlpha,
                    shortSs58 = shortSs58
                )
                is SnLedgerClaimDialogState.NeedsGas -> NeedsGasContent(
                    state = ClaimDialogState.NeedsGas(state.gasKey, state.gasTao, state.totalRao),
                    onDismiss = onDismiss,
                    onRetry = onRetry,
                    formatAlpha = formatAlpha
                )
                is SnLedgerClaimDialogState.Sending -> LedgerProgressContent(
                    progress = state.progress,
                    confirmedRao = null,
                    onDismiss = onDismiss,
                    formatAlpha = formatAlpha,
                    explorerTxUrl = explorerTxUrl
                )
                is SnLedgerClaimDialogState.Finished -> LedgerProgressContent(
                    progress = state.progress,
                    confirmedRao = state.confirmedRao,
                    onDismiss = onDismiss,
                    formatAlpha = formatAlpha,
                    explorerTxUrl = explorerTxUrl
                )
                SnLedgerClaimDialogState.Unavailable -> {
                    Text(
                        stringResource(id = R.string.sn_ledger_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogButtons(
                        primary = stringResource(id = R.string.retry),
                        onPrimary = onRetry,
                        secondary = stringResource(id = R.string.close),
                        onSecondary = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerReadyContent(
    state: SnLedgerClaimDialogState.Ready,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    formatAlpha: (Long) -> String,
    shortSs58: (String) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            formatAlpha(state.totalRao),
            style = TextStyle(
                fontFamily = gravityCondensedFamily,
                fontWeight = FontWeight(900),
                fontSize = 40.sp,
                color = Color.White
            )
        )

        if (state.accounts.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtons(primary = stringResource(id = R.string.close), onPrimary = onDismiss)
            return@Column
        }

        Spacer(modifier = Modifier.height(12.dp))

        state.accounts.forEach { account ->
            LedgerAmountRow(
                stringResource(id = R.string.sn_ledger_pool, account.noId),
                formatAlpha(account.claimableRao)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = TextFaint)
        Spacer(modifier = Modifier.height(12.dp))

        state.gasKey?.let { gasKey ->
            LedgerAmountRow(
                stringResource(id = R.string.gas_key),
                listOfNotNull(
                    shortSs58(gasKey.mirrorSs58),
                    state.gasTao?.let { stringResource(id = R.string.tao_amount_linux, EarningsFormat.tao(it)) }
                ).joinToString(" · ")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            stringResource(id = R.string.claim_sends_from_device),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        DialogButtons(
            primary = stringResource(id = R.string.claim_amount_button, formatAlpha(state.totalRao)),
            onPrimary = onClaim,
            secondary = stringResource(id = R.string.cancel),
            onSecondary = onDismiss
        )
    }
}

/** confirmedRao: the total once Done, null while sending. */
@Composable
private fun LedgerProgressContent(
    progress: List<SnLedgerClaimProgress>,
    confirmedRao: Long?,
    onDismiss: () -> Unit,
    formatAlpha: (Long) -> String,
    explorerTxUrl: (String) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        progress.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(id = R.string.sn_ledger_pool, p.noId),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        formatAlpha(p.amountRao),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    if (p.status == SnLedgerClaimStatus.FAILED && !p.message.isNullOrBlank()) {
                        Text(
                            snLedgerClaimFailureRes(p.message)?.let { stringResource(id = it) }
                                ?: snLedgerClaimFailureDetail(p.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Red
                        )
                    }
                }
                ClaimStatusTrailing(
                    status = when (p.status) {
                        SnLedgerClaimStatus.PENDING -> ClaimProgressStatus.PENDING
                        SnLedgerClaimStatus.SENT -> ClaimProgressStatus.SENT
                        // claimed by a third party, to the same coldkey
                        SnLedgerClaimStatus.CONFIRMED, SnLedgerClaimStatus.ALREADY_CLAIMED -> ClaimProgressStatus.CONFIRMED
                        SnLedgerClaimStatus.FAILED -> ClaimProgressStatus.FAILED
                    },
                    txHash = p.txHash,
                    explorerTxUrl = explorerTxUrl
                )
            }
        }

        confirmedRao?.let {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = TextFaint)
            Spacer(modifier = Modifier.height(8.dp))
            LedgerAmountRow(stringResource(id = R.string.claim_confirmed), formatAlpha(it), valueColor = Green)
        }

        Spacer(modifier = Modifier.height(16.dp))

        DialogButtons(
            primary = stringResource(id = R.string.close),
            onPrimary = onDismiss
        )
    }
}

@Preview
@Composable
private fun SnLedgerTilePreview() {
    URNetworkTheme {
        SnLedgerTileBlock(
            tile = snLedgerTile(
                SnLedgerLoad.Loaded(
                    listOf(SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, true, 5_870_000_000))
                ),
                claimConfigured = true
            )!!,
            onClaim = {},
            formatAlpha = { EarningsFormat.alpha(it) }
        )
    }
}

@Preview
@Composable
private fun SnLedgerClaimDialogSendingPreview() {
    URNetworkTheme {
        Box {
            SnLedgerClaimDialog(
                state = SnLedgerClaimDialogState.Sending(
                    listOf(
                        SnLedgerClaimProgress(1, 42, 4_120_000_000, SnLedgerClaimStatus.SENT, "0x8f1c", null),
                        SnLedgerClaimProgress(2, 7, 310_000_000, SnLedgerClaimStatus.FAILED, null, "claim_failed: TooManyStakingHotkeys"),
                    )
                ),
                onDismiss = {},
                onClaim = {},
                onRetry = {},
                formatAlpha = { EarningsFormat.alpha(it) },
                shortSs58 = { com.bringyour.network.utils.Ss58.short(it) },
                explorerTxUrl = { "" }
            )
        }
    }
}
