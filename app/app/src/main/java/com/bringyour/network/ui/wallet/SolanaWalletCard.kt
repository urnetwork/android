package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.network.utils.SolanaAddress
import com.bringyour.network.utils.formatDecimalString

// the overflow next to "Connect Bittensor wallet" and on the connected Bittensor wallet
const val EARNINGS_WALLET_OVERFLOW_TAG = "earnings-wallet-overflow"
const val EARNINGS_CONNECT_SOLANA_ITEM_TAG = "earnings-connect-solana"

// the Solana payout wallet card and its overflow
const val EARNINGS_SOLANA_WALLET_CARD_TAG = "earnings-solana-wallet"
const val EARNINGS_SOLANA_WALLET_OVERFLOW_TAG = "earnings-solana-wallet-overflow"
const val EARNINGS_REMOVE_SOLANA_ITEM_TAG = "earnings-remove-solana"
const val EARNINGS_USDC_WAITING_TAG = "earnings-usdc-waiting"

data class OverflowItem(
    val text: String,
    val icon: Painter?,
    val onClick: () -> Unit,
    val testTag: String? = null,
)

/** The three-dot wallet menu. The button shows only an icon, so it carries [contentDescription]. */
@Composable
fun WalletOverflowMenu(
    contentDescription: String,
    items: List<OverflowItem>,
    modifier: Modifier = Modifier,
    testTag: String = EARNINGS_WALLET_OVERFLOW_TAG,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                tint = TextMuted
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MainTintedBackgroundBase
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.text, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    modifier = if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier,
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

/** "Connect Solana wallet", the item both Bittensor wallet states carry. */
@Composable
fun connectSolanaOverflowItem(onClick: () -> Unit): OverflowItem = OverflowItem(
    text = stringResource(id = R.string.connect_solana_wallet),
    icon = painterResource(id = R.drawable.solana_logo),
    onClick = onClick,
    testTag = EARNINGS_CONNECT_SOLANA_ITEM_TAG,
)

/**
 * The legacy USDC payout wallet under the Bittensor wallet section: the card when the
 * network has a payout wallet; otherwise, with USDC waiting and [showWaitingLine] (the
 * Bittensor wallet is connected, so there is no button row to carry the line), the
 * "N USDC waiting" line. Nothing before the first load finished.
 */
@Composable
fun LegacyPayoutBlock(
    legacy: LegacyWalletUi,
    legacyLoaded: Boolean,
    showWaitingLine: Boolean,
    state: SolanaConnectState,
    onRemove: () -> Unit,
    onDismissState: () -> Unit,
) {
    if (!legacyLoaded) {
        return
    }
    val payoutWallet = legacy.payoutWallet
    if (payoutWallet != null) {
        Spacer(modifier = Modifier.height(16.dp))
        SolanaWalletCard(
            wallet = payoutWallet,
            pendingUsd = if (legacy.hasPending) legacy.pendingUsd else null,
            state = state,
            onRemove = onRemove,
            onDismissState = onDismissState
        )
    } else if (showWaitingLine && legacy.hasPending) {
        Spacer(modifier = Modifier.height(16.dp))
        UsdcWaitingLine(pendingUsd = legacy.pendingUsd)
    }
}

/** "3.87 USDC waiting" while no payout wallet is connected; the figure the email shows. */
@Composable
fun UsdcWaitingLine(
    pendingUsd: Double,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.usdc_waiting, formatDecimalString(pendingUsd, 2)),
        modifier = modifier.testTag(EARNINGS_USDC_WAITING_TAG),
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted
    )
}

/**
 * A failed connect, link or remove, worded the same on every platform: the reason when
 * there is one, otherwise "Something went wrong."
 */
@Composable
fun solanaFailureText(state: SolanaConnectState.Failed): String =
    state.detail?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.error_connecting_wallet_with_reason, it) }
        ?: stringResource(R.string.something_went_wrong)

/** The payout wallet, so always the default one. */
@Composable
fun SolanaWalletCard(
    wallet: LegacyWallet,
    pendingUsd: Double?,
    state: SolanaConnectState,
    onRemove: () -> Unit,
    onDismissState: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .testTag(EARNINGS_SOLANA_WALLET_CARD_TAG)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(100)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (wallet.chain == LegacyChain.POLYGON) R.drawable.polygon_logo else R.drawable.solana_logo
                    ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // android has no Polygon label; a Polygon payout wallet shows its address only
                    if (wallet.chain == LegacyChain.SOLANA) {
                        Text(
                            stringResource(id = R.string.solana_wallet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    DefaultWalletBadge()
                }
                Text(
                    SolanaAddress.short(wallet.address),
                    // the whole address for TalkBack, not the shortened form
                    modifier = Modifier.semantics { contentDescription = wallet.address },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            WalletOverflowMenu(
                contentDescription = stringResource(id = R.string.wallet_options),
                items = listOf(
                    OverflowItem(
                        text = stringResource(id = R.string.remove),
                        icon = null,
                        onClick = onRemove,
                        testTag = EARNINGS_REMOVE_SOLANA_ITEM_TAG,
                    )
                ),
                testTag = EARNINGS_SOLANA_WALLET_OVERFLOW_TAG
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(id = R.string.usdc_payouts_until_migration),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        if (pendingUsd != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.usdc_waiting, formatDecimalString(pendingUsd, 2)),
                modifier = Modifier.testTag(EARNINGS_USDC_WAITING_TAG),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        val (status, color) = when (state) {
            is SolanaConnectState.Linked -> stringResource(id = R.string.payout_wallet_updated) to Green
            is SolanaConnectState.Failed -> solanaFailureText(state) to Red
            else -> null to TextMuted
        }
        if (status != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                status,
                modifier = Modifier.clickable(onClickLabel = stringResource(id = R.string.cancel)) { onDismissState() },
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
private fun DefaultWalletBadge() {
    Box(
        modifier = Modifier
            .background(Color(0x0AFFFFFF), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            stringResource(id = R.string.default_wallet).uppercase(),
            style = TextStyle(
                fontSize = 16.sp,
                fontFamily = ppNeueBitBold
            ),
            color = TextMuted
        )
    }
}

/** Remove the payout wallet: the server holds USDC payouts until another wallet is connected. */
@Composable
fun RemoveSolanaWalletDialog(
    visible: Boolean,
    removing: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    URDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(id = R.string.remove_wallet),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.remove_wallet_holds_payouts),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                URInlineErrorText(error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = onConfirm,
                style = ButtonStyle.WARNING,
                enabled = !removing,
                isProcessing = removing
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.remove), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE,
                enabled = !removing,
                modifier = Modifier.fillMaxWidth()
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
            }
        }
    }
}

@Preview
@Composable
private fun SolanaWalletCardPreview() {
    URNetworkTheme {
        Column(
            modifier = Modifier
                .background(Black)
                .padding(16.dp)
        ) {
            SolanaWalletCard(
                wallet = LegacyWallet(
                    SampleLegacyWalletSource.SAMPLE_WALLET_ID,
                    SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS,
                    LegacyChain.SOLANA,
                    hasSeekerToken = false
                ),
                pendingUsd = SampleLegacyWalletSource.SAMPLE_USDC_WAITING,
                state = SolanaConnectState.Linked(
                    LegacyWallet(
                        SampleLegacyWalletSource.SAMPLE_WALLET_ID,
                        SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS,
                        LegacyChain.SOLANA,
                        hasSeekerToken = false
                    )
                ),
                onRemove = {},
                onDismissState = {}
            )
        }
    }
}
