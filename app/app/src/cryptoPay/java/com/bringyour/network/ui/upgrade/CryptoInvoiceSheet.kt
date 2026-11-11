package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.components.FoldSafeModalBottomSheet
import com.bringyour.network.ui.components.rememberFoldSafeSheetState
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTextBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The crypto invoice (mmm/ZCASH-CONTRACT.md §6.2, mmm/BTCPAY.md §17.6),
 * rendered from the sdk's state ([CryptoPayment.view] decides what shows), top
 * to bottom: the network the coin is sent on; the QR code of the sdk's QR text
 * and Open in wallet, only for a uri that pays the address shown; the exact
 * amount and the address with Copy, the memo, and the coin's network-only
 * warning; the quote's countdown with Refresh quote; the status steps; the
 * wallets that work; Pay another way and Close.
 *
 * A coin without a payment link (TAO) has no Open in wallet: its QR code
 * holds the address alone, with a note that says so, and "Send exactly this
 * amount to this address." stands in its place, over the amount and the
 * address with their own Copy amount and Copy address.
 *
 * Hosted above the screen that opened it (CryptoInvoiceHost), so leaving that
 * screen doesn't end a payment. The controller polls and counts down only
 * while the sheet shows with the app in front ([setActive]): no background
 * checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoInvoiceSheet(
    invoice: CryptoInvoiceSnapshot,
    // the sdk's CheckCryptoPaymentUri(asset, uri, address)
    uriPasses: Boolean,
    // the coin has a payment uri scheme (the sdk's CryptoPaymentUriScheme): TAO has none
    hasPaymentLink: Boolean,
    // the sdk's CryptoWalletSuggestions(asset)
    wallets: List<String>,
    setActive: (Boolean) -> Unit,
    requote: () -> Unit,
    payAnotherWay: () -> Unit,
    close: () -> Unit,
) {
    val view = remember(invoice, uriPasses, hasPaymentLink) { CryptoPayment.view(invoice, uriPasses, hasPaymentLink) }
    val sheetState = rememberFoldSafeSheetState()

    LifecycleResumeEffect(Unit) {
        setActive(true)
        onPauseOrDispose { setActive(false) }
    }

    FoldSafeModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // 1. the network first: USDC is on a dozen chains, and only this one arrives
            Text(
                cryptoNetworkLineText(invoice.asset),
                style = MaterialTheme.typography.headlineSmall
            )

            if (view.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TextMuted, trackColor = TextFaint)
                }
            }

            if (view.unavailable) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(
                        id = R.string.crypto_invoice_unavailable,
                        cryptoAssetName(invoice.asset)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (view.failed) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(id = R.string.payment_not_completed),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (view.review) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(id = R.string.crypto_status_review),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // 2. the QR code, and the wallet on this device for a uri that pays the address below
            view.qrText?.let { text ->
                Spacer(modifier = Modifier.height(24.dp))
                PaymentQrImage(text = text)
                // a coin without a payment link: the code holds the address alone
                if (view.noPaymentLink) {
                    cryptoNoPaymentLinkNoteText(invoice.asset)?.let { note ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
            view.payUri?.let { uri ->
                Spacer(modifier = Modifier.height(16.dp))
                OpenInWalletButton(uri = uri)
            }
            if (view.noPaymentLink && view.amount) {
                // where Open in wallet would be: the amount and the address go into the wallet by hand
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(id = R.string.crypto_invoice_send_exactly_to_address),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 3. what to send, and where
            if (view.amount) {
                Spacer(modifier = Modifier.height(24.dp))
                InvoiceCopyField(
                    label = stringResource(id = R.string.crypto_invoice_send_exactly),
                    // the exact decimal string with its ticker ("0.072387 XMR"); Copy takes the number
                    shown = "${invoice.amount} ${invoice.ticker}",
                    copied = invoice.amount,
                    copyLabel = if (view.noPaymentLink) R.string.crypto_invoice_copy_amount else R.string.crypto_invoice_copy,
                )
            }
            if (view.address) {
                Spacer(modifier = Modifier.height(16.dp))
                InvoiceCopyField(
                    label = stringResource(id = R.string.crypto_invoice_to_address),
                    shown = invoice.address,
                    copied = invoice.address,
                    monospace = true,
                    copyLabel = if (view.noPaymentLink) R.string.crypto_invoice_copy_address else R.string.crypto_invoice_copy,
                )
            }
            if (view.memo) {
                Spacer(modifier = Modifier.height(16.dp))
                InvoiceCopyField(
                    label = stringResource(id = R.string.crypto_invoice_memo),
                    shown = invoice.memo,
                    copied = invoice.memo,
                    monospace = true,
                )
            }
            if (view.address) {
                // scanning the QR picks the chain; a transfer sent by hand from an exchange does not
                cryptoNetworkWarningText(invoice.asset)?.let { warning ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            // 4. the quote's time, and a new one
            if (view.countdown != null || view.expired || view.refresh) {
                Spacer(modifier = Modifier.height(24.dp))
                view.countdown?.let { countdown ->
                    Text(
                        stringResource(id = R.string.crypto_invoice_expires_in, countdown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                if (view.expired) {
                    Text(
                        stringResource(id = R.string.crypto_invoice_quote_expired),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (view.refresh) {
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(
                        onClick = requote,
                        style = ButtonStyle.OUTLINE,
                        isProcessing = view.refreshing
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.crypto_invoice_refresh_quote),
                            style = buttonTextStyle
                        )
                    }
                }
            }

            // 5. where the payment is
            if (view.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                InvoiceSteps(steps = view.steps)
            }
            view.remaining?.let { remaining ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.crypto_invoice_remaining, "$remaining ${invoice.ticker}"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 6. what to pay it with
            if (view.wallets && wallets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(id = R.string.crypto_invoice_wallets, wallets.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            if (view.zcashExchangeNote) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.crypto_invoice_zcash_exchange_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            // 7. the ways out
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (view.payAnotherWay) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (view.payAnotherWay) {
                    TextButton(onClick = payAnotherWay) {
                        Text(stringResource(id = R.string.crypto_invoice_pay_another_way))
                    }
                }
                TextButton(onClick = close) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
    }
}

private val QR_SIZE = 220.dp

/** The invoice's QR text (the payment uri, or a TAO address) as a QR code on white, encoded off the main thread. */
@Composable
private fun PaymentQrImage(text: String) {
    val sizePx = with(LocalDensity.current) { QR_SIZE.roundToPx() }
    val qr by produceState<Bitmap?>(initialValue = null, text, sizePx) {
        value = withContext(Dispatchers.Default) { paymentQrBitmap(text, sizePx) }
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(QR_SIZE + 16.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            qr?.let {
                // the network line above names what it pays; the amount and address below say it in text
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Opens the payment uri in a wallet on this device. With no app for it the
 * QR code stays for a wallet on another device, and nothing alarming is said.
 */
@Composable
private fun OpenInWalletButton(uri: String) {
    val uriHandler = LocalUriHandler.current
    URButton(
        onClick = {
            try {
                uriHandler.openUri(uri)
            } catch (e: Exception) {
                // no wallet here for this coin: the code above is the way to pay
            }
        }
    ) { buttonTextStyle ->
        Text(
            stringResource(id = R.string.crypto_invoice_open_wallet),
            style = buttonTextStyle
        )
    }
}

/**
 * A labelled value with its copy button ([copyLabel]: Copy, or Copy amount and
 * Copy address on an invoice that has both), which reads Copied for a moment
 * after a tap.
 */
@Composable
private fun InvoiceCopyField(
    label: String,
    shown: String,
    copied: String,
    monospace: Boolean = false,
    @StringRes copyLabel: Int = R.string.crypto_invoice_copy,
) {
    val clipboardManager = LocalClipboardManager.current
    var justCopied by remember(copied) { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(COPIED_MILLIS)
            justCopied = false
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shown,
                // an address wraps: every character of it matters
                style = if (monospace) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.titleMedium
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copied))
                    justCopied = true
                }
            ) {
                Text(
                    stringResource(id = if (justCopied) R.string.crypto_invoice_copied else copyLabel)
                )
            }
        }
    }
}

private const val COPIED_MILLIS = 2_000L

/**
 * The status steps as the sdk lays them out (CryptoPaymentSteps): waiting →
 * payment seen → confirming n of N → paid, or for USDC waiting → paid, the
 * current one marked and the ones the payment is past (or skipped) done.
 */
@Composable
private fun InvoiceSteps(steps: List<CryptoInvoiceStep>) {
    Column {
        steps.forEach { step ->
            val current = step.progress == Sdk.CryptoPaymentStepCurrent
            val reached = current || step.progress == Sdk.CryptoPaymentStepDone
            val text = when (step.status) {
                Sdk.CryptoPaymentStatusSeen -> stringResource(id = R.string.crypto_status_seen)
                Sdk.CryptoPaymentStatusConfirming -> stringResource(
                    id = R.string.crypto_status_confirming,
                    step.confirmations,
                    step.confirmationsRequired
                )
                Sdk.CryptoPaymentStatusPaid -> stringResource(id = R.string.crypto_status_paid)
                else -> stringResource(id = R.string.crypto_status_waiting)
            }
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                current -> BlueMedium
                                reached -> TextMuted
                                else -> TextFaint
                            }
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        current -> MainTextBase
                        reached -> TextMuted
                        else -> TextFaint
                    }
                )
            }
        }
    }
}
