package com.bringyour.network.ui.upgrade

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTextBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/**
 * The payment method row (mmm/ZCASH-CONTRACT.md §6.1, mmm/BTCPAY.md §17.6):
 * Card, then the sdk's coin tabs in their order -- USDC on Solana, Zcash,
 * Monero, and "More coins" with the TAO, Bitcoin, ETH and SOL marks as four
 * separate marks with space between them, never one badge -- labelled "Pay
 * with" for TalkBack. The tabs wrap onto another line where a phone's width
 * does not hold them. A tab of several coins ("More coins") opens a list of
 * them under the row, each with the network it is sent on. The chosen
 * method's tab is the sdk's CryptoPaymentTabId of it ([selectedTabId]: "" for
 * the card).
 *
 * A tab or a coin that cannot be paid with now stays in its place, its label
 * greyed with "Temporarily unavailable", and cannot be chosen; its marks are
 * never dimmed. A coin whose last start was refused as unavailable says so
 * under the row and stays selectable, so a tap on its button retries.
 */
@Composable
fun PaymentMethodRow(
    tabs: List<CryptoPayTab>,
    selected: String,
    selectedTabId: String,
    onSelect: (String) -> Unit,
    unavailable: Boolean,
) {
    val rowLabel = stringResource(id = R.string.pay_method_label)
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = rowLabel },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MethodTab(
                selected = selectedTabId.isEmpty(),
                available = true,
                onClick = { onSelect(Sdk.PaymentMethodCard) },
            ) {
                MethodTabLabel(text = stringResource(id = R.string.pay_method_card), available = true)
            }
            tabs.forEach { tab ->
                // a tab of several coins is read by the coins behind it ("More coins: TAO, Bitcoin, ETH, SOL")
                val description = if (1 < tab.coins.size) {
                    stringResource(
                        id = R.string.pay_method_more_coins_a11y,
                        tab.coins.map { coin -> cryptoLabel(coin.labelKey, coin.label) }.joinToString(", ")
                    )
                } else {
                    null
                }
                MethodTab(
                    selected = tab.id == selectedTabId,
                    available = tab.available,
                    onClick = { CryptoPayment.tabChoice(tab, selected)?.let(onSelect) },
                    modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
                ) {
                    // each mark on its own, with clear space around it; none for a coin this app bundles no mark for
                    val marks = tab.iconKeys.mapNotNull { coinMarkIcon(it) }
                    if (marks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            marks.forEach { icon -> CoinMark(icon = icon, size = 16.dp) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    MethodTabLabel(text = cryptoLabel(tab.labelKey, tab.label), available = tab.available)
                }
            }
        }

        // the list a tab of several coins opens, titled with the tab's name
        tabs.firstOrNull { it.id == selectedTabId }?.takeIf { 1 < it.coins.size }?.let { tab ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                cryptoLabel(tab.labelKey, tab.label),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                tab.coins.forEach { coin ->
                    CoinChoiceRow(
                        coin = coin,
                        selected = coin.asset == selected,
                        onClick = { onSelect(coin.asset) },
                    )
                }
            }
        }

        if (unavailable) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.pay_method_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/** One tab of the row: outlined, filled while selected. One that cannot be chosen draws nothing dimmer but its label. */
@Composable
private fun MethodTab(
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(width = 1.dp, color = if (selected) BlueMedium else TextFaint, shape = shape)
            .background(if (selected) BlueMedium.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(selected = selected, enabled = available, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A tab's name, greyed with "Temporarily unavailable" under it while none of its coins can be paid with. */
@Composable
private fun MethodTabLabel(text: String, available: Boolean) {
    Column {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (available) MainTextBase else TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!available) {
            Text(
                stringResource(id = R.string.pay_method_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/**
 * A coin in the list a tab of several coins opens: its mark, its name, and
 * the network it is sent on. One that cannot be paid with now is greyed,
 * says "Temporarily unavailable", and cannot be chosen; its mark is the same.
 */
@Composable
private fun CoinChoiceRow(coin: CryptoPayCoin, selected: Boolean, onClick: () -> Unit) {
    val network = cryptoNetworkLineText(coin.asset)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, enabled = coin.available, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = coin.available)
        Spacer(modifier = Modifier.width(4.dp))
        coinMarkIcon(coin.iconKey)?.let { icon ->
            CoinMark(icon = icon, size = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                cryptoLabel(coin.labelKey, coin.label),
                style = MaterialTheme.typography.bodyMedium,
                color = if (coin.available) MainTextBase else TextFaint
            )
            Text(
                if (coin.available) network else "$network · ${stringResource(id = R.string.pay_method_unavailable)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/**
 * A coin's mark, at full contrast for the theme and never dimmed: Solana's
 * and Bittensor's brand rules allow their marks only in black or white, and
 * all four marks are shown the same way (mmm/BTCPAY.md §17.6).
 */
@Composable
private fun CoinMark(@DrawableRes icon: Int, size: Dp) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = null,
        tint = coinMarkTint(),
        modifier = Modifier.size(size)
    )
}

/** White on a dark theme, black on a light one. */
@Composable
private fun coinMarkTint(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black

/**
 * The mark this app bundles for a coin's icon key (the sdk's CryptoIconKey*),
 * or null: there is none for USDC, Zcash and Monero, whose tabs show their
 * names alone.
 */
@DrawableRes
private fun coinMarkIcon(iconKey: String): Int? = when (iconKey) {
    Sdk.CryptoIconKeyTao -> R.drawable.coin_tao
    Sdk.CryptoIconKeyBtc -> R.drawable.coin_btc
    Sdk.CryptoIconKeyEth -> R.drawable.coin_eth
    Sdk.CryptoIconKeySol -> R.drawable.coin_sol
    else -> null
}

/**
 * A coin's or a tab's name by its string id in the shared localizations (the
 * sdk's LabelKey), or the sdk's English ([label]) for an id this app has no
 * string for: a coin is never shown under another coin's name.
 */
@Composable
fun cryptoLabel(labelKey: String, label: String): String =
    cryptoLabelRes(labelKey)?.let { stringResource(id = it) } ?: label

@StringRes
private fun cryptoLabelRes(labelKey: String): Int? = when (labelKey) {
    "pay_method_usdc_solana" -> R.string.pay_method_usdc_solana
    "pay_method_zcash" -> R.string.pay_method_zcash
    "pay_method_monero" -> R.string.pay_method_monero
    "pay_method_tao" -> R.string.pay_method_tao
    "pay_method_bitcoin" -> R.string.pay_method_bitcoin
    "pay_method_eth" -> R.string.pay_method_eth
    "pay_method_sol" -> R.string.pay_method_sol
    "pay_method_more_coins" -> R.string.pay_method_more_coins
    else -> null
}

/**
 * A coin's name by its asset, as the row and the More coins list show it (the
 * sdk's CryptoAssetLabelKey, with CryptoAssetLabel as the English fallback):
 * "Pay with {method}" and the invoice's lines.
 */
@Composable
fun cryptoAssetName(asset: String): String {
    val keyAndLabel = remember(asset) { (Sdk.cryptoAssetLabelKey(asset) ?: "") to (Sdk.cryptoAssetLabel(asset) ?: "") }
    return cryptoLabel(keyAndLabel.first, keyAndLabel.second)
}

/**
 * The invoice's first line, the network the coin is sent on ("USDC on Solana",
 * "Zcash, shielded", "Monero", "Bittensor network", "Bitcoin network",
 * "Ethereum mainnet", "Solana (native SOL)"), also under each coin of the
 * More coins list; null for a coin this app has no line for.
 */
@StringRes
fun cryptoNetworkLine(asset: String): Int? = when (asset) {
    Sdk.CryptoAssetUsdcSolana -> R.string.crypto_network_usdc
    Sdk.CryptoAssetZec -> R.string.crypto_network_zcash
    Sdk.CryptoAssetXmr -> R.string.crypto_network_monero
    Sdk.CryptoAssetTao -> R.string.crypto_network_tao
    Sdk.CryptoAssetBtc -> R.string.crypto_network_bitcoin
    Sdk.CryptoAssetEth -> R.string.crypto_network_eth
    Sdk.CryptoAssetSol -> R.string.crypto_network_sol
    else -> null
}

/** The coin's network line, or its name for a coin this app has no line for (never another coin's network). */
@Composable
fun cryptoNetworkLineText(asset: String): String =
    cryptoNetworkLine(asset)?.let { stringResource(id = it) } ?: cryptoAssetName(asset)

/**
 * The coin's network-only warning, shown with the address: a transfer on
 * another network, or of another token, is not credited. Null for Zcash and
 * Monero, which have none.
 */
@StringRes
fun cryptoNetworkWarning(asset: String): Int? = when (asset) {
    Sdk.CryptoAssetUsdcSolana -> R.string.usdc_solana_network_only
    Sdk.CryptoAssetTao -> R.string.crypto_invoice_tao_network_only
    Sdk.CryptoAssetBtc -> R.string.crypto_invoice_bitcoin_network_only
    Sdk.CryptoAssetEth -> R.string.crypto_invoice_eth_network_only
    Sdk.CryptoAssetSol -> R.string.crypto_invoice_sol_network_only
    else -> null
}

/**
 * Why a coin without a payment link has no Open in wallet, and what its QR
 * code holds: TAO's note. Null for any other coin.
 */
@StringRes
fun cryptoNoPaymentLinkNote(asset: String): Int? = when (asset) {
    Sdk.CryptoAssetTao -> R.string.crypto_invoice_tao_no_payment_link
    else -> null
}
