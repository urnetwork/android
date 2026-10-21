package com.bringyour.network.ui.upgrade

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/**
 * The payment method row (mmm/ZCASH-CONTRACT.md §6.1): Card · USDC · Zcash ·
 * Monero, the coins the server lists only, labelled "Pay with" for TalkBack.
 * A coin whose last start was refused as unavailable says so under the row
 * and stays selectable, so a tap on its button retries.
 *
 * Four segments share a phone's width, so the selected one shows no check
 * mark: its fill marks it, and TalkBack reads it as selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodRow(
    methods: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    unavailable: Boolean,
) {
    val rowLabel = stringResource(id = R.string.pay_method_label)
    Column(modifier = Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = rowLabel }
        ) {
            methods.forEachIndexed { index, method ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = methods.size
                    ),
                    onClick = { onSelect(method) },
                    selected = method == selected,
                    icon = {},
                    label = {
                        Text(
                            stringResource(id = paymentMethodLabel(method)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
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

/** A method's name on the row and in "Pay with {method}". */
@StringRes
fun paymentMethodLabel(method: String): Int = when (method) {
    Sdk.CryptoAssetUsdcSolana -> R.string.pay_method_usdc
    Sdk.CryptoAssetZec -> R.string.pay_method_zcash
    Sdk.CryptoAssetXmr -> R.string.pay_method_monero
    else -> R.string.pay_method_card
}

/** The invoice's first line, the network the coin is sent on: "USDC on Solana", "Zcash, shielded", "Monero". */
@StringRes
fun cryptoNetworkLine(asset: String): Int = when (asset) {
    Sdk.CryptoAssetZec -> R.string.crypto_network_zcash
    Sdk.CryptoAssetXmr -> R.string.crypto_network_monero
    else -> R.string.crypto_network_usdc
}
