package com.bringyour.network.ui.wallet

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import kotlinx.coroutines.delay

/**
 * The claim dialog. The SDK builds, signs and sends the claim from the device's gas
 * key straight to the vault contract; this only shows where that stands.
 */
@Composable
fun ClaimDialog(
    state: ClaimDialogState?,
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

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                null -> {}
                ClaimDialogState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                    }
                }
                ClaimDialogState.NoWallet -> {
                    Text(
                        stringResource(id = R.string.connect_wallet_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogButtons(primary = stringResource(id = R.string.close), onPrimary = onDismiss)
                }
                is ClaimDialogState.Ready -> ReadyContent(
                    state = state,
                    onDismiss = onDismiss,
                    onClaim = onClaim,
                    formatAlpha = formatAlpha,
                    shortSs58 = shortSs58
                )
                is ClaimDialogState.NeedsGas -> NeedsGasContent(
                    state = state,
                    onDismiss = onDismiss,
                    onRetry = onRetry,
                    formatAlpha = formatAlpha
                )
                is ClaimDialogState.Sending -> ProgressContent(
                    progress = state.progress,
                    finished = false,
                    confirmedRao = 0,
                    onDismiss = onDismiss,
                    formatAlpha = formatAlpha,
                    explorerTxUrl = explorerTxUrl
                )
                is ClaimDialogState.Finished -> ProgressContent(
                    progress = state.progress,
                    finished = true,
                    confirmedRao = state.confirmedRao,
                    onDismiss = onDismiss,
                    formatAlpha = formatAlpha,
                    explorerTxUrl = explorerTxUrl
                )
                is ClaimDialogState.Unavailable -> {
                    Text(
                        stringResource(id = R.string.chain_rpc_unreachable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                    if (!state.detail.isNullOrBlank() && state.detail != stringResource(id = R.string.chain_rpc_unreachable)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            state.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
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
private fun ReadyContent(
    state: ClaimDialogState.Ready,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    formatAlpha: (Long) -> String,
    shortSs58: (String) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.claims.isEmpty()) {
            Text(
                stringResource(id = R.string.claims_open_after_finalization),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtons(primary = stringResource(id = R.string.close), onPrimary = onDismiss)
            return@Column
        }

        Text(
            formatAlpha(state.totalRao),
            style = TextStyle(
                fontFamily = gravityCondensedFamily,
                fontWeight = FontWeight(900),
                fontSize = 40.sp,
                color = Color.White
            )
        )
        Text(
            stringResource(id = R.string.claim_across_epochs, state.claims.size),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))

        state.claims.forEach { claim ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.epoch_row_title, claim.epoch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    formatAlpha(claim.amountRao),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = TextFaint)
        Spacer(modifier = Modifier.height(12.dp))

        state.gasKey?.let { gasKey ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.gas_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    listOfNotNull(
                        shortSs58(gasKey.mirrorSs58),
                        state.gasTao?.let { "${EarningsFormat.tao(it)} TAO" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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

@Composable
private fun NeedsGasContent(
    state: ClaimDialogState.NeedsGas,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    formatAlpha: (Long) -> String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(id = R.string.add_tao_for_gas),
            style = MaterialTheme.typography.bodyLarge,
            color = Amber
        )

        Spacer(modifier = Modifier.height(8.dp))

        val mirror = state.gasKey?.mirrorSs58 ?: ""
        Text(
            stringResource(
                id = R.string.send_tao_to_mirror,
                EarningsFormat.tao(EarningsViewModel.SUGGESTED_GAS_TAO),
                mirror
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        if (mirror.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            CopyRow(value = mirror)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(id = R.string.gas_key),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(
                "${EarningsFormat.tao(state.gasTao)} TAO",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(id = R.string.unclaimed),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(
                formatAlpha(state.totalRao),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(id = R.string.claim_sends_from_device),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
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

@Composable
private fun ProgressContent(
    progress: List<ClaimProgress>,
    finished: Boolean,
    confirmedRao: Long,
    onDismiss: () -> Unit,
    formatAlpha: (Long) -> String,
    explorerTxUrl: (String) -> String,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        progress.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(id = R.string.epoch_row_title, p.epoch),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        formatAlpha(p.amountRao),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    if (p.status == ClaimProgressStatus.FAILED && !p.message.isNullOrBlank()) {
                        Text(
                            claimFailureText(p.epoch, p.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Red
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (p.status) {
                        ClaimProgressStatus.PENDING -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                        ClaimProgressStatus.SENT -> {
                            Text(
                                stringResource(id = R.string.claim_sent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BlueMedium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = BlueMedium,
                                trackColor = TextFaint,
                                strokeWidth = 2.dp
                            )
                        }
                        ClaimProgressStatus.CONFIRMED -> {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(id = R.string.claim_confirmed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Green
                            )
                        }
                        ClaimProgressStatus.FAILED -> {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(id = R.string.claim_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Red
                            )
                        }
                    }
                    p.txHash?.let { txHash ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = txHash,
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, explorerTxUrl(txHash).toUri()))
                                }
                        )
                    }
                }
            }
        }

        if (finished) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = TextFaint)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.claim_confirmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    formatAlpha(confirmedRao),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DialogButtons(
            primary = stringResource(id = R.string.close),
            onPrimary = onDismiss
        )
    }
}

/** The SDK reports "<code>: <reason>"; known codes have a string of the same name. */
@Composable
private fun claimFailureText(epoch: Long, message: String): String =
    when (EarningsViewModel.claimFailureCode(message)) {
        EarningsViewModel.SN_CODE_EXPIRED -> stringResource(id = R.string.claims_for_epoch_expired, epoch)
        EarningsViewModel.SN_CODE_NEEDS_GAS -> stringResource(id = R.string.add_tao_for_gas)
        EarningsViewModel.SN_CODE_ALREADY_CLAIMED -> stringResource(id = R.string.claim_confirmed)
        EarningsViewModel.SN_CODE_RPC_UNREACHABLE -> stringResource(id = R.string.chain_rpc_unreachable)
        else -> message.substringAfter(':').trim().ifEmpty { message }
    }

@Composable
private fun CopyRow(value: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(8.dp))
            .clickable {
                clipboard.setText(AnnotatedString(value))
                copied = true
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = if (copied) Green else BlueMedium,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(id = if (copied) R.string.copied else R.string.copy),
                style = MaterialTheme.typography.bodySmall,
                color = if (copied) Green else BlueMedium
            )
        }
    }
}

@Composable
private fun DialogButtons(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        URButton(onClick = onPrimary) { buttonTextStyle ->
            Text(primary, style = buttonTextStyle)
        }
        if (secondary != null) {
            Spacer(modifier = Modifier.height(8.dp))
            URButton(onClick = onSecondary, style = ButtonStyle.OUTLINE) { buttonTextStyle ->
                Text(secondary, style = buttonTextStyle)
            }
        }
    }
}

@Preview
@Composable
private fun ClaimDialogReadyPreview() {
    URNetworkTheme {
        Box {
            ClaimDialog(
                state = ClaimDialogState.Ready(
                    claims = listOf(
                        EpochClaim(1218, 71, 3_241_000_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
                        EpochClaim(1217, 64, 2_905_500_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
                    ),
                    totalRao = 6_146_500_000,
                    gasKey = SnGasKeyState(SampleProtocolSource.SAMPLE_GAS_ADDRESS, SampleProtocolSource.SAMPLE_GAS_MIRROR),
                    gasTao = 0.124
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

@Preview
@Composable
private fun ClaimDialogNeedsGasPreview() {
    URNetworkTheme {
        Box {
            ClaimDialog(
                state = ClaimDialogState.NeedsGas(
                    gasKey = SnGasKeyState(SampleProtocolSource.SAMPLE_GAS_ADDRESS, SampleProtocolSource.SAMPLE_GAS_MIRROR),
                    gasTao = 0.0,
                    totalRao = 6_146_500_000
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
