package com.bringyour.network.ui.wallet

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The payout key's part of the payout card, and the banner above Earnings,
 * drawn from what the sdk's flow says to show (BittensorKeyCardInfo). The
 * words are the plan's (apple/BTEASY.md, Wording) in short form: the card
 * holds the explanation, so it is read before the one tap, and no screen
 * comes between the tap and the result. The states the bodies here don't
 * draw keep the wallet card's lines (SnConsentBlock).
 */

const val EARNINGS_KEY_BANNER_TAG = "earnings-key-banner"
const val EARNINGS_KEY_SET_UP_TAG = "earnings-key-set-up"
const val EARNINGS_KEY_SAVE_FIRST_TAG = "earnings-key-save-first"
const val EARNINGS_KEY_USE_WALLET_TAG = "earnings-key-use-wallet"
const val EARNINGS_KEY_FINISH_SETUP_TAG = "earnings-key-finish-setup"
const val EARNINGS_KEY_BACK_UP_TAG = "earnings-key-back-up"
const val EARNINGS_KEY_FUNDS_DISCLAIMER_TAG = "earnings-key-funds-disclaimer"
const val EARNINGS_KEY_CREATE_HERE_TAG = "earnings-key-create-here"

/** What the card's key part can do. */
data class BittensorKeyCardActions(
    val setUp: () -> Unit = {},
    val saveFirst: () -> Unit = {},
    val useWallet: () -> Unit = {},
    val finishSetup: () -> Unit = {},
    val openKeyScreen: () -> Unit = {},
    val reload: () -> Unit = {},
)

/** The words for a failure the flow reports, null for none. */
@Composable
fun bittensorKeyFailureText(code: String, message: String): String? = when (code) {
    "" -> null
    EarningsViewModel.SN_CODE_CHALLENGE_EXPIRED -> stringResource(id = R.string.sn_consent_expired)
    EarningsViewModel.SN_CODE_SIGNATURE_MISMATCH -> stringResource(id = R.string.sn_consent_failed)
    EarningsViewModel.SN_CODE_NETWORK_OWNER_ONLY,
    Sdk.BittensorLocalKeyFailureNotAllowed -> stringResource(id = R.string.sn_consent_owner_only)
    Sdk.BittensorLocalKeyFailureGenerate -> stringResource(id = R.string.bittensor_key_failure_generate)
    Sdk.BittensorLocalKeyFailureStore -> stringResource(id = R.string.bittensor_key_failure_store)
    Sdk.BittensorLocalKeyFailureStoreUnavailable -> stringResource(id = R.string.bittensor_key_failure_keychain)
    Sdk.BittensorLocalKeyFailureDelete -> stringResource(id = R.string.bittensor_key_failure_delete)
    // the card warns first, so this reads only if a warning was skipped
    Sdk.BittensorLocalKeyFailureReplacementNotConfirmed -> stringResource(id = R.string.bittensor_key_replace_message)
    else -> message.ifEmpty { stringResource(id = R.string.bittensor_key_failure_consent) }
}

/** The store key of the owner's disclaimer for the sdk's id (BittensorFundsDisclaimer*), null for none. */
fun bittensorFundsDisclaimerRes(disclaimer: String): Int? = when (disclaimer) {
    Sdk.BittensorFundsDisclaimerFull -> R.string.bittensor_funds_disclaimer
    Sdk.BittensorFundsDisclaimerFundsOnly -> R.string.bittensor_funds_disclaimer_funds_only
    else -> null
}

/**
 * The owner's disclaimer (apple/BTEASY.md, Wording), in plain view on every
 * screen of the easy flows, once a screen: the whole text where the key is in
 * this app's secure storage, its last sentence alone where it isn't, nothing
 * elsewhere. The sdk says which, so every app shows the same. Plain text,
 * never behind a link or a disclosure.
 */
@Composable
fun BittensorFundsDisclaimerText(
    disclaimer: String,
    modifier: Modifier = Modifier,
    // in its own box, at the foot of a screen of its own
    boxed: Boolean = false,
) {
    val textRes = bittensorFundsDisclaimerRes(disclaimer) ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (boxed) {
                    Modifier
                        .background(MainTintedBackgroundBase, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {}
            .testTag(EARNINGS_KEY_FUNDS_DISCLAIMER_TAG),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.GppGood,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(id = textRes),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}

/**
 * The card with no consent in force: the one tap and its two smaller links,
 * or Finish setup for a key already here, or the store's refusal (the mocks'
 * 1a, 1b and 3i).
 */
@Composable
fun BittensorKeyNoConsentBody(
    key: BittensorKeyCardInfo,
    actions: BittensorKeyCardActions,
) {
    when (key.state) {
        Sdk.BittensorLocalKeyStateSaveLaterNotLinked -> {
            BittensorKeyCardTitle(stringResource(id = R.string.bittensor_key_card_not_linked))
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_finish_body))
            BittensorKeyFailureLine(key)
            if (key.offersFinishSetup) {
                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = actions.finishSetup,
                    isProcessing = key.busy,
                    modifier = Modifier.testTag(EARNINGS_KEY_FINISH_SETUP_TAG),
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.bittensor_key_finish_setup), style = buttonTextStyle)
                }
            }
            if (key.offersWallet) {
                BittensorKeyCardLink(
                    text = stringResource(id = R.string.bittensor_key_use_wallet),
                    onClick = actions.useWallet,
                    enabled = !key.busy,
                    testTag = EARNINGS_KEY_USE_WALLET_TAG,
                )
            }
        }
        Sdk.BittensorLocalKeyStateStoreUnavailable -> BittensorKeyStoreUnavailable(onReload = actions.reload)
        else -> {
            BittensorKeyCardTitle(stringResource(id = R.string.bittensor_key_card_none))
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_card_short))
            BittensorKeyFailureLine(key)
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = actions.setUp,
                isProcessing = key.busy,
                modifier = Modifier.testTag(EARNINGS_KEY_SET_UP_TAG),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.bittensor_key_set_up), style = buttonTextStyle)
            }
            if (key.busy) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.bittensor_key_setting_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            BittensorKeyCardLink(
                text = stringResource(id = R.string.bittensor_key_save_first),
                onClick = actions.saveFirst,
                enabled = !key.busy,
                testTag = EARNINGS_KEY_SAVE_FIRST_TAG,
            )
            if (key.offersWallet) {
                BittensorKeyCardLink(
                    text = stringResource(id = R.string.bittensor_key_use_wallet),
                    onClick = actions.useWallet,
                    enabled = !key.busy,
                    testTag = EARNINGS_KEY_USE_WALLET_TAG,
                )
            }
        }
    }
}

/** The states [BittensorKeyActiveBody] draws for a consent in force. */
fun bittensorKeyActiveBodyDraws(key: BittensorKeyCardInfo): Boolean = when (key.state) {
    Sdk.BittensorLocalKeyStateSaveLaterLinked,
    Sdk.BittensorLocalKeyStateSavedAndDeleted,
    Sdk.BittensorLocalKeyStateElsewhere -> true
    else -> false
}

/**
 * The card with a consent in force, by where its key is (the mocks' 1c, 1i,
 * 1l, 2h, 3a and 3d). A wallet network and a second device share one card:
 * nothing tells them apart, since the server doesn't record how a key was
 * made. A linked key keeps a small Change wallet link, so the network can
 * still move to a wallet.
 */
@Composable
fun BittensorKeyActiveBody(
    key: BittensorKeyCardInfo,
    coldkeySs58: String,
    shortSs58: (String) -> String,
    actions: BittensorKeyCardActions,
    // the session can choose the payout wallet
    changesWallet: Boolean,
    changeWalletEnabled: Boolean,
    changingWallet: Boolean,
    onChangeWallet: () -> Unit,
) {
    when (key.state) {
        Sdk.BittensorLocalKeyStateSaveLaterLinked -> {
            BittensorKeyPayoutsTitle(key)
            BittensorKeyAddressRow(address = coldkeySs58, shortSs58 = shortSs58)
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_every_device))
            Spacer(modifier = Modifier.height(8.dp))
            if (key.keySaved) {
                BittensorKeyStatusRow(text = stringResource(id = R.string.bittensor_key_status_saved), dot = Green)
                BittensorKeyCardLink(
                    text = stringResource(id = R.string.bittensor_key_view),
                    onClick = actions.openKeyScreen,
                    testTag = EARNINGS_KEY_BACK_UP_TAG,
                )
            } else {
                BittensorKeyStatusRow(text = stringResource(id = R.string.bittensor_key_status_unsaved), dot = Amber)
                Spacer(modifier = Modifier.height(12.dp))
                URButton(
                    onClick = actions.openKeyScreen,
                    style = ButtonStyle.SECONDARY,
                    modifier = Modifier.testTag(EARNINGS_KEY_BACK_UP_TAG),
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.bittensor_key_back_up), style = buttonTextStyle)
                }
            }
            BittensorKeyFailureLine(key)
            if (changesWallet) {
                BittensorKeyCardLink(
                    text = stringResource(id = R.string.sn_consent_change),
                    onClick = onChangeWallet,
                    enabled = changeWalletEnabled,
                )
            }
        }
        Sdk.BittensorLocalKeyStateSavedAndDeleted -> {
            BittensorKeyPayoutsTitle(key)
            BittensorKeyAddressRow(address = coldkeySs58, shortSs58 = shortSs58)
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_every_device))
            Spacer(modifier = Modifier.height(8.dp))
            BittensorKeyStatusRow(text = stringResource(id = R.string.bittensor_key_no_longer_here), icon = Icons.Filled.KeyOff)
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_deleted_body))
            BittensorKeyFailureLine(key)
            if (changesWallet) {
                BittensorKeyChangeWalletButton(enabled = changeWalletEnabled, processing = changingWallet, onClick = onChangeWallet)
            }
        }
        else -> {
            // elsewhere: a wallet, or a key on another device; nothing here tells them apart
            BittensorKeyCardTitle(stringResource(id = R.string.sn_consent_active, shortSs58(coldkeySs58)))
            Spacer(modifier = Modifier.height(8.dp))
            BittensorKeyStatusRow(text = stringResource(id = R.string.bittensor_key_elsewhere), icon = Icons.Filled.Key)
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_elsewhere_body))
            BittensorKeyFailureLine(key)
            if (changesWallet) {
                BittensorKeyChangeWalletButton(enabled = changeWalletEnabled, processing = changingWallet, onClick = onChangeWallet)
            }
        }
    }
}

/**
 * What the card adds under "Earnings are paid to" in the states the active
 * body leaves to the wallet card: a key here the consent doesn't pay, and a
 * store that can't answer.
 */
@Composable
fun BittensorKeyActiveLines(
    key: BittensorKeyCardInfo,
    actions: BittensorKeyCardActions,
) {
    when (key.state) {
        Sdk.BittensorLocalKeyStateSaveLaterNotLinked -> {
            Spacer(modifier = Modifier.height(8.dp))
            BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_card_other_consent))
            if (key.offersFinishSetup) {
                Spacer(modifier = Modifier.height(12.dp))
                URButton(
                    onClick = actions.finishSetup,
                    style = ButtonStyle.SECONDARY,
                    isProcessing = key.busy,
                    modifier = Modifier.testTag(EARNINGS_KEY_FINISH_SETUP_TAG),
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.bittensor_key_finish_setup), style = buttonTextStyle)
                }
            }
            if (key.offersKeyScreen) {
                BittensorKeyCardLink(
                    text = stringResource(id = if (key.keySaved) R.string.bittensor_key_view else R.string.bittensor_key_back_up),
                    onClick = actions.openKeyScreen,
                    testTag = EARNINGS_KEY_BACK_UP_TAG,
                )
            }
        }
        Sdk.BittensorLocalKeyStateStoreUnavailable -> BittensorKeyStoreUnavailable(onReload = actions.reload)
        else -> {}
    }
    BittensorKeyFailureLine(key)
}

/** While the key's phrase isn't known to be saved: stays until it is, and leads Earnings. */
@Composable
fun BittensorKeyBackupBanner(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MainTintedBackgroundBase)
            .border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
            .testTag(EARNINGS_KEY_BANNER_TAG),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(id = R.string.bittensor_key_back_up),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(id = R.string.bittensor_key_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
        )
    }
}

@Composable
private fun BittensorKeyPayoutsTitle(key: BittensorKeyCardInfo) {
    BittensorKeyCardTitle(
        stringResource(id = if (key.payoutsStartNextEpoch) R.string.bittensor_key_payouts_start else R.string.bittensor_key_payouts_on)
    )
}

@Composable
private fun BittensorKeyChangeWalletButton(enabled: Boolean, processing: Boolean, onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    URButton(
        onClick = onClick,
        style = ButtonStyle.OUTLINE,
        enabled = enabled,
        isProcessing = processing,
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.sn_consent_change), style = buttonTextStyle)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(id = R.string.sn_consent_change_note),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )
}

/** The card's title line. */
@Composable
fun BittensorKeyCardTitle(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun BittensorKeyCardBody(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
    )
}

/** A smaller link, as the wallet section's "Enter address manually". */
@Composable
fun BittensorKeyCardLink(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String = "",
) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) BlueMedium else TextMuted,
    )
}

/** The address the consent pays, with Copy. Not a secret. */
@Composable
fun BittensorKeyAddressRow(address: String, shortSs58: (String) -> String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(ADDRESS_COPIED_SHOWN_MILLIS)
            copied = false
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Verified,
            contentDescription = null,
            tint = BlueMedium,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            shortSs58(address),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText(address, address).toClipEntry())
                }
                copied = true
            },
        ) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = stringResource(id = R.string.copy),
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Where the key is, beside a colored dot or an icon. */
@Composable
fun BittensorKeyStatusRow(
    text: String,
    dot: Color? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dot, CircleShape)
            )
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun BittensorKeyStoreUnavailable(onReload: () -> Unit) {
    BittensorKeyCardBody(stringResource(id = R.string.bittensor_key_failure_keychain))
    BittensorKeyCardLink(
        text = stringResource(id = R.string.retry),
        onClick = onReload,
    )
}

@Composable
fun BittensorKeyFailureLine(key: BittensorKeyCardInfo) {
    val text = bittensorKeyFailureText(key.failureCode, key.failureMessage) ?: return
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Red,
    )
}

/**
 * The payout key on this device as the wallet card shows it, while no wallet
 * is read: its address, and whether the network's consent pays it yet. Null
 * without a stored key.
 */
data class BittensorWalletCardLocalKey(
    val address: String,
    val linked: Boolean,
) {
    companion object {
        fun of(card: BittensorKeyCardInfo): BittensorWalletCardLocalKey? =
            if (card.storedAddress.isEmpty()) {
                null
            } else {
                BittensorWalletCardLocalKey(card.storedAddress, card.state == Sdk.BittensorLocalKeyStateSaveLaterLinked)
            }
    }
}

/**
 * The warning before a new key replaces the payout wallet (the mocks' 3c):
 * from the next epoch, and earlier earnings stay on the old wallet, which the
 * message names. It is never the default.
 */
@Composable
fun BittensorKeyReplaceDialog(
    oldAddress: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    URDialog(
        visible = true,
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(id = R.string.bittensor_key_replace_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.bittensor_key_replace_message_2, oldAddress),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = onConfirm,
                style = ButtonStyle.WARNING,
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.bittensor_key_replace_confirm), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
            }
        }
    }
}

/**
 * Change wallet's offer of a key on this device, above the wallets in the
 * consent's chooser (the mocks' 3b), while the card offers one: never the
 * default, and it goes through the replace warning. It carries the
 * change_wallet place's disclaimer.
 */
@Composable
fun BittensorKeyChangeWalletOffer(
    fundsDisclaimer: String,
    onCreateKey: () -> Unit,
) {
    Text(
        stringResource(id = R.string.bittensor_key_change_wallet_body),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(16.dp))
    URButton(
        onClick = onCreateKey,
        modifier = Modifier.testTag(EARNINGS_KEY_CREATE_HERE_TAG),
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.bittensor_key_create_here), style = buttonTextStyle)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(id = R.string.bittensor_key_create_here_body),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )
    Spacer(modifier = Modifier.height(12.dp))
    BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer, boxed = true)
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        stringResource(id = R.string.bittensor_key_or_choose_wallet),
        style = MaterialTheme.typography.bodyLarge,
    )
}

// how long the address row's Copy reads as done
private const val ADDRESS_COPIED_SHOWN_MILLIS = 1_800L
