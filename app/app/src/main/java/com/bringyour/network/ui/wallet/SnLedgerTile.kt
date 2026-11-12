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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.bringyour.network.ui.connection_region.localizedCountryName
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.utils.Ss58
import com.bringyour.sdk.Sdk

/**
 * The Settlement v2 tile, under the v1 Unclaimed tile: per account Claimable
 * first, then Paid, Earned and what a pending root adds; "Unavailable" when
 * the ledger cannot be read, never zero; Claim once vault2 is configured.
 * From capture the flag each pool's account is its own block, named by its
 * kind, a pool the ledger could not read is "Unavailable right now" in its
 * own, beside the others, and an account under the transfer floor says so.
 * Claim reads Claim both while it takes the pool's account and capture the
 * flag's together. URnetwork's claim relay claims it for every consented
 * network: while the relay has something to say (`relay`), who claims and
 * why a balance waits, or what URnetwork's claim paid and where, take the
 * Claim's place, and for a network with no wallet on this device the tile
 * shows what the relay says is claimable, with the pools that make it up
 * once there is more than one. A capture the flag slot this device won
 * (`ctfWin`) is a line of its own. The first claim tile on Earnings carries
 * the claim card's disclaimer.
 */
@Composable
fun SnLedgerTileBlock(
    tile: SnLedgerTile,
    onClaim: () -> Unit,
    formatAlpha: (Long) -> String,
    relay: SnClaimStatusState? = null,
    // the relay's claimed state names the coldkey its claim paid
    shortSs58: (String) -> String = { Ss58.short(it) },
    // Claim now's dialog
    onClaimNow: () -> Unit = {},
    // the device's own claim, with its gas key
    onSelfClaim: () -> Unit = {},
    // a BittensorFundsDisclaimer*
    fundsDisclaimer: String = Sdk.BittensorFundsDisclaimerNone,
    // the capture the flag slot this device won last
    ctfWin: SnCtfWinState? = null,
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
            tile.relayClaimableRao != null -> {
                // with none of the coldkey's pools read there is no total, only the unavailable rows
                if (relay == null || snClaimRelayTotalKnown(relay)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.sn_ledger_claimable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Text(
                        formatAlpha(tile.relayClaimableRao),
                        style = TextStyle(
                            fontFamily = gravityCondensedFamily,
                            fontWeight = FontWeight(900),
                            fontSize = 40.sp,
                            color = Color.White
                        )
                    )
                }
                relay?.let {
                    SnClaimRelayTilePools(status = it, formatAlpha = formatAlpha, shortSs58 = shortSs58)
                }
            }
            tile.accounts == null -> LedgerSpinner()
            else -> {
                val pools = tile.pools
                val namesPools = snLedgerNamesPools(pools)
                tile.accounts.forEach { account ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (namesPools) {
                        Text(
                            snLedgerPoolNameText(snLedgerPoolName(account.pool, pools)),
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
                    if (account.underFloor) {
                        SnClaimRelayPill(stringResource(id = R.string.sn_ledger_pool_under_floor))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
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
                // a pool the ledger could not read: unavailable in place of its balances, never zero
                tile.unavailablePools.forEach { pool ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (namesPools) {
                        Text(
                            snLedgerPoolNameText(snLedgerPoolName(pool, pools)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        stringResource(id = R.string.sn_ledger_pool_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                }
            }
        }

        ctfWin?.let { win ->
            snCtfWonRes(win)?.let { res ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // the country's own name in the reader's language, bare, as the store's wording expects
                    Text(
                        stringResource(id = res, localizedCountryName(win.country)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (relay != null && snClaimRelaySpeaks(relay)) {
            Spacer(modifier = Modifier.height(16.dp))
            SnClaimRelaySection(
                status = relay,
                onClaimNow = onClaimNow,
                onSelfClaim = onSelfClaim,
                formatAlpha = formatAlpha,
                shortSs58 = shortSs58,
                inDialog = false,
            )
        } else if (tile.showClaim) {
            Spacer(modifier = Modifier.height(16.dp))
            URButton(onClick = onClaim, enabled = tile.claimEnabled) { buttonTextStyle ->
                Text(
                    stringResource(id = if (tile.claimBoth) R.string.sn_ledger_claim_both else R.string.claim),
                    style = buttonTextStyle
                )
            }
        }

        if (bittensorFundsDisclaimerRes(fundsDisclaimer) != null) {
            Spacer(modifier = Modifier.height(16.dp))
            BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer)
        }
    }
}

/** A pool's name (snLedgerPoolName) in the reader's language. */
@Composable
fun snLedgerPoolNameText(name: SnLedgerPoolName): String = when (name) {
    is SnLedgerPoolName.Pool -> stringResource(id = R.string.sn_ledger_pool, name.noId)
    SnLedgerPoolName.ProviderPool -> stringResource(id = R.string.sn_ledger_provider_pool)
    SnLedgerPoolName.CaptureTheFlag -> stringResource(id = R.string.sn_ctf_pool)
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
 * failure) until Done. Each row is named as the tile names its pool; the
 * pools the ledger could not read are listed as unavailable, and an account
 * the claim could not take, under the transfer floor or in a pool that could
 * not be read, says so rather than Failed. One claim takes the pool's account
 * and capture the flag's together: Claim both.
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
    // every pool of the latest read (snLedgerPools), which names the rows as the tile does
    pools: List<SnLedgerPool> = emptyList(),
    // the claim relay's transfer floor in rao of α, 0 without one: an account under it waits
    transferFloorRao: Long = 0,
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
                    pools = pools,
                    transferFloorRao = transferFloorRao,
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
                    pools = pools,
                    confirmedRao = null,
                    onDismiss = onDismiss,
                    formatAlpha = formatAlpha,
                    explorerTxUrl = explorerTxUrl
                )
                is SnLedgerClaimDialogState.Finished -> LedgerProgressContent(
                    progress = state.progress,
                    pools = pools,
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
    pools: List<SnLedgerPool>,
    transferFloorRao: Long,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    formatAlpha: (Long) -> String,
    shortSs58: (String) -> String,
) {
    val inView = (pools + state.accounts.map { it.pool } + state.unavailablePools).distinct()
    // the accounts above the transfer floor, while capture the flag is in view; an estimate
    // that names a status and Claim both, and never stops the claim, whose preflight decides
    val above = snLedgerClaimTakes(state.accounts, snLedgerTransferFloor(inView, transferFloorRao))
    Column(modifier = Modifier.fillMaxWidth()) {
        // with nothing read to claim and a pool unread there is no total, only its row
        if (state.accounts.isNotEmpty() || state.unavailablePools.isEmpty()) {
            Text(
                formatAlpha(state.totalRao),
                style = TextStyle(
                    fontFamily = gravityCondensedFamily,
                    fontWeight = FontWeight(900),
                    fontSize = 40.sp,
                    color = Color.White
                )
            )
        }

        if (state.accounts.isEmpty()) {
            if (state.unavailablePools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LedgerUnavailableRows(state.unavailablePools, inView)
            }
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtons(primary = stringResource(id = R.string.close), onPrimary = onDismiss)
            return@Column
        }

        Spacer(modifier = Modifier.height(12.dp))

        LedgerAccountRows(state.accounts, above, inView, formatAlpha)
        // the total leaves out the pools that could not be read
        LedgerUnavailableRows(state.unavailablePools, inView)

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
            // one claim, one transaction, for the pool's account and capture the flag's
            primary = if (snLedgerClaimsBoth(above.map { it.pool })) {
                stringResource(id = R.string.sn_ledger_claim_both)
            } else {
                stringResource(id = R.string.claim_amount_button, formatAlpha(state.totalRao))
            },
            onPrimary = onClaim,
            secondary = stringResource(id = R.string.cancel),
            onSecondary = onDismiss
        )
    }
}

/** Each account with what it holds to claim; one under the transfer floor says Below the claim minimum. */
@Composable
private fun LedgerAccountRows(
    accounts: List<SnLedgerAccountState>,
    above: List<SnLedgerAccountState>,
    inView: List<SnLedgerPool>,
    formatAlpha: (Long) -> String,
) {
    accounts.forEach { account ->
        LedgerAmountRow(
            snLedgerPoolNameText(snLedgerPoolName(account.pool, inView)),
            formatAlpha(account.claimableRao)
        )
        if (account !in above) {
            Text(
                stringResource(id = R.string.sn_ledger_pool_under_floor),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/** Each pool the ledger could not read, unavailable in place of an amount, never zero. */
@Composable
private fun LedgerUnavailableRows(unavailablePools: List<SnLedgerPool>, inView: List<SnLedgerPool>) {
    unavailablePools.forEach { pool ->
        LedgerAmountRow(
            snLedgerPoolNameText(snLedgerPoolName(pool, inView)),
            stringResource(id = R.string.sn_ledger_pool_unavailable),
            valueColor = TextMuted
        )
    }
}

/** confirmedRao: the total once Done, null while sending. */
@Composable
private fun LedgerProgressContent(
    progress: List<SnLedgerClaimProgress>,
    pools: List<SnLedgerPool>,
    confirmedRao: Long?,
    onDismiss: () -> Unit,
    formatAlpha: (Long) -> String,
    explorerTxUrl: (String) -> String,
) {
    val inView = (pools + progress.map { it.pool }).distinct()
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
                        snLedgerPoolNameText(snLedgerPoolName(p.pool, inView)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // a row the sdk added, or a pool that could not be read, has no amount to show, never a zero
                    if (p.amountKnown) {
                        Text(
                            formatAlpha(p.amountRao),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    if (p.status == SnLedgerClaimStatus.FAILED && !p.message.isNullOrBlank()) {
                        Text(
                            snLedgerClaimFailureRes(p.message)?.let { stringResource(id = it) }
                                ?: snLedgerClaimFailureDetail(p.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Red
                        )
                    }
                    // not a failure: what the account is, where a claim's status would be
                    if (p.status == SnLedgerClaimStatus.UNDER_FLOOR || p.status == SnLedgerClaimStatus.POOL_UNAVAILABLE) {
                        Text(
                            stringResource(
                                id = if (p.status == SnLedgerClaimStatus.UNDER_FLOOR) {
                                    R.string.sn_ledger_pool_under_floor
                                } else {
                                    R.string.sn_ledger_pool_unavailable
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                val status = when (p.status) {
                    SnLedgerClaimStatus.PENDING -> ClaimProgressStatus.PENDING
                    SnLedgerClaimStatus.SENT -> ClaimProgressStatus.SENT
                    // claimed by a third party, to the same coldkey
                    SnLedgerClaimStatus.CONFIRMED, SnLedgerClaimStatus.ALREADY_CLAIMED -> ClaimProgressStatus.CONFIRMED
                    SnLedgerClaimStatus.FAILED -> ClaimProgressStatus.FAILED
                    // said under the amount instead
                    SnLedgerClaimStatus.UNDER_FLOOR, SnLedgerClaimStatus.POOL_UNAVAILABLE -> null
                }
                if (status != null) {
                    ClaimStatusTrailing(
                        status = status,
                        txHash = p.txHash,
                        explorerTxUrl = explorerTxUrl
                    )
                }
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
                    listOf(
                        SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, true, 5_870_000_000),
                        SnLedgerAccountState(Sdk.SnLedgerCtfPoolIdBase + 1, 3, 96_051_320, 96_051_320, 0, false, 0),
                    )
                ),
                claimConfigured = true
            )!!,
            onClaim = {},
            formatAlpha = { EarningsFormat.alpha(it) },
            ctfWin = SnCtfWinState(Sdk.SnCtfRoleProvider, "is"),
        )
    }
}

@Preview
@Composable
private fun SnLedgerTilePoolUnavailablePreview() {
    URNetworkTheme {
        SnLedgerTileBlock(
            tile = snLedgerTile(
                SnLedgerLoad.Loaded(
                    listOf(SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, false, 0)),
                    unavailablePools = listOf(SnLedgerPool(Sdk.SnLedgerCtfPoolIdBase + 1)),
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
                // the pool's account and capture the flag's in one batch transaction
                state = SnLedgerClaimDialogState.Sending(
                    listOf(
                        SnLedgerClaimProgress(1, 42, 4_120_000_000, SnLedgerClaimStatus.SENT, "0x8f1c", null),
                        SnLedgerClaimProgress(Sdk.SnLedgerCtfPoolIdBase + 1, 3, 96_051_320, SnLedgerClaimStatus.SENT, "0x8f1c", null),
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
