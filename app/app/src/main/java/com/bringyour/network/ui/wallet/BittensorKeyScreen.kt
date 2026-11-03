package com.bringyour.network.ui.wallet

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.FoldSafeDialog
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

/*
 * The Key screen, the same for both flows (apple/BTEASY.md, and the approved
 * mocks' flows 1 and 2): the words hidden by default, Tap to reveal, Hide and
 * Copy once they show, the not-a-wallet text always, Keep beside the one
 * destructive action, both behind a check of two of the words. What deleting
 * means, and the check, replace the screen's content rather than cover it, so
 * the disclaimer shows once. The sdk's flow decides all of that; this draws
 * it and adds what only Android has:
 *   - the screen's window is FLAG_SECURE (SecureFlagPolicy.SecureOn), so
 *     screenshots, recordings, casting and the recents thumbnail leave it out;
 *   - the words hide when the app pauses;
 *   - a stored key's words, and its deletion, ask for BiometricPrompt with the
 *     device credential as the fallback;
 *   - Copy is a sensitive clip, cleared after 60 s;
 *   - the two-word fields learn nothing (BittensorKeyWordField).
 * The words are in the composition only while they show, as plain Text with
 * no selection, so assistive tech can't read them while hidden. Android tells
 * an app of neither a screenshot nor a recording of a secure window, so the
 * screen has no screenshot warning.
 */

const val EARNINGS_KEY_REVEAL_TAG = "earnings-key-reveal"
const val EARNINGS_KEY_WORDS_TAG = "earnings-key-words"
const val EARNINGS_KEY_KEEP_TAG = "earnings-key-keep"
const val EARNINGS_KEY_DELETE_TAG = "earnings-key-delete"
const val EARNINGS_KEY_CONFIRM_TAG = "earnings-key-confirm"
const val EARNINGS_KEY_WORD_1_TAG = "earnings-key-word-1"
const val EARNINGS_KEY_WORD_2_TAG = "earnings-key-word-2"

/**
 * The Key screen while the flow has it open, as a screen of its own in a
 * secure window. Back goes back a step, then closes; leaving calls
 * CloseScreen, which on Save now discards the key. Nothing leaves while a
 * consent is being signed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BittensorKeyScreenDialog(
    model: BittensorKeyModel,
    shortSs58: (String) -> String,
) {
    val screen by model.screen.collectAsState()
    if (!screen.open) {
        return
    }
    val deleteExplained by model.deleteExplained.collectAsState()

    // the words hide whenever the app leaves the foreground
    LifecycleResumeEffect(model) {
        onPauseOrDispose { model.leftForeground() }
    }

    val onBack: () -> Unit = {
        when {
            screen.busy -> {}
            screen.confirming != Sdk.BittensorLocalKeyConfirmNone -> model.cancelConfirmation()
            deleteExplained -> model.cancelDelete()
            else -> model.closeScreen()
        }
    }

    // a screen shown as a dialog, in a window no capture can read
    FoldSafeDialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
        fullScreen = true,
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(id = R.string.bittensor_key_screen_title),
                            style = TopBarTitleTextStyle
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !screen.busy) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                )
            },
            containerColor = Black,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .tabletReadableColumn()
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // one at a time, so the disclaimer shows once
                when {
                    screen.confirming != Sdk.BittensorLocalKeyConfirmNone ->
                        key(screen.confirming) {
                            BittensorKeyConfirmation(model = model, screen = screen)
                        }
                    deleteExplained -> BittensorKeyDeleteExplanation(model = model, screen = screen, shortSs58 = shortSs58)
                    else -> BittensorKeyMainContent(model = model, screen = screen, shortSs58 = shortSs58)
                }
            }
        }
    }
}

/** The authenticator the screen's actions use: over this screen's activity. */
@Composable
private fun rememberBittensorDeviceAuthenticator(): BittensorDeviceAuthenticating {
    val context = LocalContext.current
    val title = stringResource(id = R.string.bittensor_key_screen_title)
    return remember(context, title) {
        object : BittensorDeviceAuthenticating {
            override suspend fun authenticate(): Boolean = BittensorDeviceAuthenticator.authenticate(context, title)
        }
    }
}

/** The icon of a control that asks for device authentication: a biometric, or the screen lock. */
@Composable
private fun rememberAuthenticationIcon(): ImageVector {
    val context = LocalContext.current
    return remember(context) {
        if (BittensorDeviceAuthenticator.usesBiometric(context)) Icons.Filled.Fingerprint else Icons.Filled.Lock
    }
}

@Composable
private fun BittensorKeyMainContent(
    model: BittensorKeyModel,
    screen: BittensorKeyScreenInfo,
    shortSs58: (String) -> String,
) {
    val words by model.words.collectAsState()
    val copied by model.copied.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authenticator = rememberBittensorDeviceAuthenticator()
    val pasteboard = remember(context) { BittensorKeyClipboard(context) }

    BittensorKeyScreenBanner(model = model, screen = screen)

    Text(
        stringResource(id = R.string.bittensor_key_wording_not_wallet),
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (screen.address.isNotEmpty()) {
        Text(
            stringResource(id = R.string.bittensor_key_words_label, shortSs58(screen.address)),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (screen.revealed) {
        BittensorKeyWordGrid(words = words)
        Spacer(modifier = Modifier.height(8.dp))
        BittensorKeyHideCountdown(untilMillis = screen.revealedUntilMillis)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            URButton(
                onClick = { model.hide() },
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.weight(1f),
            ) { buttonTextStyle ->
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.bittensor_key_hide), style = buttonTextStyle)
            }
            URButton(
                onClick = { model.copy(pasteboard) },
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.weight(1f),
            ) { buttonTextStyle ->
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = if (copied) R.string.copied else R.string.copy), style = buttonTextStyle)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.bittensor_key_copy_warning_2),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    } else {
        BittensorKeyRevealCover(
            needsAuthentication = screen.needsAuthentication,
            enabled = !screen.hiddenByCapture,
            onReveal = {
                scope.launch { model.tapToReveal(authenticator) }
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    BittensorKeyNotice(icon = Icons.Filled.Warning, tint = Amber) {
        Text(
            stringResource(id = R.string.bittensor_key_wording_control),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        stringResource(id = R.string.bittensor_key_not_seed_2),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )

    if (!screen.offersRetry) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(id = R.string.bittensor_key_saved_question),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (screen.offersKeep) {
            URButton(
                onClick = { model.confirmKeep() },
                style = ButtonStyle.SECONDARY,
                enabled = !screen.busy,
                modifier = Modifier.testTag(EARNINGS_KEY_KEEP_TAG),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.bittensor_key_keep_action), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        // the one destructive action, a coral outline beside the safe choice
        URButton(
            onClick = { model.explainDelete() },
            style = ButtonStyle.WARNING,
            enabled = !screen.busy,
            modifier = Modifier.testTag(EARNINGS_KEY_DELETE_TAG),
        ) { buttonTextStyle ->
            Text(stringResource(id = R.string.bittensor_key_delete_action), style = buttonTextStyle)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    BittensorFundsDisclaimerText(disclaimer = screen.fundsDisclaimer, boxed = true)

    Spacer(modifier = Modifier.height(16.dp))
    BittensorKeyTextAction(
        text = stringResource(id = if (screen.saveNow) R.string.cancel else R.string.close),
        enabled = !screen.busy,
        onClick = { model.closeScreen() },
    )
}

/** Signing, a refused consent, or the Save now notice. */
@Composable
private fun BittensorKeyScreenBanner(model: BittensorKeyModel, screen: BittensorKeyScreenInfo) {
    val failure = bittensorKeyFailureText(screen.failureCode, screen.failureMessage)
    when {
        screen.busy -> {
            BittensorKeyNotice(progress = true) {
                Text(stringResource(id = R.string.bittensor_key_signing), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        screen.offersRetry -> {
            BittensorKeyNotice(icon = Icons.Filled.Warning, tint = Red) {
                Column {
                    if (failure != null) {
                        Text(failure, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    URButton(onClick = { model.retry() }, isProcessing = screen.busy) { buttonTextStyle ->
                        Text(stringResource(id = R.string.retry), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.bittensor_key_save_now_retry),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        else -> {
            if (screen.saveNow) {
                BittensorKeyNotice(icon = Icons.Filled.Info, tint = BlueMedium) {
                    Text(stringResource(id = R.string.bittensor_key_save_now_banner), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (failure != null) {
                Text(failure, style = MaterialTheme.typography.bodyMedium, color = Red)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Tap to reveal: the cover over the words, with the lock or biometric a stored key asks for. */
@Composable
private fun BittensorKeyRevealCover(
    needsAuthentication: Boolean,
    enabled: Boolean,
    onReveal: () -> Unit,
) {
    val authenticationIcon = rememberAuthenticationIcon()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MainTintedBackgroundBase)
            .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onReveal)
            .testTag(EARNINGS_KEY_REVEAL_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (needsAuthentication) authenticationIcon else Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.bittensor_key_reveal),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The twelve words, numbered. Text only, so nothing selects them. */
@Composable
private fun BittensorKeyWordGrid(words: List<String>) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag(EARNINGS_KEY_WORDS_TAG)
    ) {
        val columns = if (480.dp <= maxWidth) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            words.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (index, word) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MainTintedBackgroundBase, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}.",
                                modifier = Modifier.width(28.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextMuted,
                            )
                            Text(
                                word,
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                            )
                        }
                    }
                    // a short last row keeps the columns aligned
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** "Hides again in 0:58", counting down to when the words hide by themselves. */
@Composable
private fun BittensorKeyHideCountdown(untilMillis: Long) {
    if (untilMillis <= 0) {
        return
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }
    val seconds = maxOf(0L, ceil((untilMillis - nowMillis) / 1000.0).toLong())
    Text(
        stringResource(
            id = R.string.bittensor_key_hides_in,
            String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )
}

/**
 * What deleting means, before anything happens: no way back, where earnings
 * keep going, what only the phrase can do, and convert ending. On Save now
 * the same step sets up payouts first. Continue asks for two of the words.
 */
@Composable
private fun BittensorKeyDeleteExplanation(
    model: BittensorKeyModel,
    screen: BittensorKeyScreenInfo,
    shortSs58: (String) -> String,
) {
    val address = shortSs58(screen.address)
    Text(
        stringResource(id = if (screen.saveNow) R.string.bittensor_key_delete_save_now_title else R.string.bittensor_key_delete_title_2),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (screen.saveNow) {
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_save_now_bullet_address, address))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_delete_bullet_no_recovery))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_save_now_bullet_phrase))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_save_now_bullet_convert))
    } else {
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_delete_bullet_no_recovery))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_delete_bullet_address, address))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_delete_bullet_phrase))
        BittensorKeyBullet(stringResource(id = R.string.bittensor_key_delete_bullet_convert))
    }

    if (screen.warnsTop200) {
        Spacer(modifier = Modifier.height(8.dp))
        BittensorKeyNotice(icon = Icons.Filled.WorkspacePremium, tint = ReferralGold) {
            Text(stringResource(id = R.string.bittensor_key_confirm_top200), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    BittensorKeyWording()

    Spacer(modifier = Modifier.height(16.dp))
    BittensorFundsDisclaimerText(disclaimer = screen.fundsDisclaimer, boxed = true)

    Spacer(modifier = Modifier.height(16.dp))
    URButton(
        onClick = { model.confirmDelete() },
        style = ButtonStyle.DESTRUCTIVE,
        modifier = Modifier.testTag(EARNINGS_KEY_CONFIRM_TAG),
    ) { buttonTextStyle ->
        Text(stringResource(id = R.string.continue_txt), style = buttonTextStyle)
    }
    Spacer(modifier = Modifier.height(8.dp))
    BittensorKeyTextAction(
        text = stringResource(id = R.string.cancel),
        enabled = true,
        onClick = { model.cancelDelete() },
    )
}

/**
 * Two of the words, which prove the phrase is saved: before the key goes, or
 * before it signs on Save now, or to keep it. Each field says as it's typed
 * whether its word is wrong, and the button acts only with both right.
 * Deleting a stored key asks for device authentication at the button. The
 * typed words are kept for this step only, never in saved state.
 */
@Composable
private fun BittensorKeyConfirmation(
    model: BittensorKeyModel,
    screen: BittensorKeyScreenInfo,
) {
    var firstWord by remember { mutableStateOf("") }
    var secondWord by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val authenticator = rememberBittensorDeviceAuthenticator()
    val authenticationIcon = rememberAuthenticationIcon()
    val keeps = screen.confirming == Sdk.BittensorLocalKeyConfirmKeep
    val check = remember(firstWord, secondWord, screen.confirming) { model.checkWords(firstWord, secondWord) }
    val submit: () -> Unit = {
        val first = firstWord
        val second = secondWord
        scope.launch { model.submitWords(first, second, authenticator) }
    }

    Text(
        stringResource(id = if (keeps) R.string.bittensor_key_keep_title_2 else R.string.bittensor_key_enter_two_words),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(12.dp))
    if (keeps) {
        Text(
            stringResource(id = if (screen.saveNow) R.string.bittensor_key_keep_save_now_body else R.string.bittensor_key_keep_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.bittensor_key_keep_prompt), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(stringResource(id = R.string.bittensor_key_enter_two_words_body), style = MaterialTheme.typography.bodyMedium)
    }

    Spacer(modifier = Modifier.height(16.dp))
    BittensorKeyWordEntry(
        position = screen.firstWordPosition,
        value = firstWord,
        onValueChange = { firstWord = it },
        state = check.first,
        testTag = EARNINGS_KEY_WORD_1_TAG,
    )
    Spacer(modifier = Modifier.height(12.dp))
    BittensorKeyWordEntry(
        position = screen.secondWordPosition,
        value = secondWord,
        onValueChange = { secondWord = it },
        state = check.second,
        testTag = EARNINGS_KEY_WORD_2_TAG,
    )

    if (screen.wordsRejected) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(id = R.string.bittensor_key_words_rejected), style = MaterialTheme.typography.bodyMedium, color = Red)
    }
    bittensorKeyFailureText(screen.failureCode, screen.failureMessage)?.let { failure ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(failure, style = MaterialTheme.typography.bodyMedium, color = Red)
    }

    Spacer(modifier = Modifier.height(16.dp))
    BittensorFundsDisclaimerText(disclaimer = screen.fundsDisclaimer, boxed = true)

    Spacer(modifier = Modifier.height(16.dp))
    if (keeps) {
        URButton(
            onClick = submit,
            enabled = check.ready,
            isProcessing = screen.busy,
            modifier = Modifier.testTag(EARNINGS_KEY_CONFIRM_TAG),
        ) { buttonTextStyle ->
            Text(stringResource(id = R.string.bittensor_key_keep_confirm_2), style = buttonTextStyle)
        }
    } else {
        URButton(
            onClick = submit,
            style = ButtonStyle.DESTRUCTIVE,
            enabled = check.ready,
            isProcessing = screen.busy,
            modifier = Modifier.testTag(EARNINGS_KEY_CONFIRM_TAG),
        ) { buttonTextStyle ->
            if (screen.confirmNeedsAuthentication) {
                Icon(authenticationIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                stringResource(id = if (screen.saveNow) R.string.bittensor_key_save_now_delete_confirm else R.string.bittensor_key_delete_confirm),
                style = buttonTextStyle,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    BittensorKeyTextAction(
        text = stringResource(id = R.string.cancel),
        enabled = !screen.busy,
        onClick = { model.cancelConfirmation() },
    )
}

/** One of the two words: its number, the quiet field, and whether what's typed can't be it. */
@Composable
private fun BittensorKeyWordEntry(
    position: Long,
    value: String,
    onValueChange: (String) -> Unit,
    // a BittensorLocalKeyWord*
    state: String,
    testTag: String,
) {
    val label = stringResource(id = R.string.bittensor_key_word_number, position)
    Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    Spacer(modifier = Modifier.height(4.dp))
    BittensorKeyWordField(label = label, value = value, onValueChange = onValueChange, testTag = testTag)
    if (state == Sdk.BittensorLocalKeyWordWrong) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.bittensor_key_word_wrong, position),
            style = MaterialTheme.typography.bodySmall,
            color = Red,
        )
    }
}

/**
 * A field for one word of the recovery phrase that the keyboard learns
 * nothing from and suggests nothing in: a visible-password field with no
 * suggestions and no personalized learning, so keyboards keep no history of
 * it and offer no word. No autofill service sees it, and its text is not in
 * saved state. Compose's text fields can't ask for all of these, so this is
 * the platform's EditText.
 */
@Composable
private fun BittensorKeyWordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val radius = with(density) { 8.dp.toPx() }
    val stroke = with(density) { 1.dp.toPx() }.toInt().coerceAtLeast(1)
    val padding = with(density) { 12.dp.toPx() }.toInt()
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        factory = { context ->
            EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_DONE or
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                maxLines = 1
                typeface = Typeface.MONOSPACE
                setTextColor(Color.White.toArgb())
                setHintTextColor(TextMuted.toArgb())
                hint = label
                contentDescription = label
                background = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(MainTintedBackgroundBase.toArgb())
                    setStroke(stroke, TextFaint.toArgb())
                }
                setPadding(padding, padding, padding, padding)
                doAfterTextChanged { currentOnValueChange(it?.toString().orEmpty()) }
            }
        },
        update = { field ->
            if (field.text.toString() != value) {
                field.setText(value)
            }
            field.hint = label
            field.contentDescription = label
        },
    )
}

/** The not-a-wallet text, the plan's words, in view on what deleting means. */
@Composable
private fun BittensorKeyWording() {
    Text(stringResource(id = R.string.bittensor_key_wording_not_wallet), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(12.dp))
    Text(stringResource(id = R.string.bittensor_key_wording_control), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun BittensorKeyBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A notice in a tinted box: an icon or a spinner, then its text. */
@Composable
private fun BittensorKeyNotice(
    icon: ImageVector? = null,
    tint: Color = BlueMedium,
    progress: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (progress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = TextMuted,
                trackColor = TextFaint,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

/** Close, Cancel: a quiet text action at the foot of a step. */
@Composable
private fun BittensorKeyTextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        color = TextMuted,
    )
}

// how often the countdown reads the clock
private const val COUNTDOWN_TICK_MILLIS = 1_000L
