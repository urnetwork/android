package com.bringyour.network.ui.components.referral

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.ReferralGoldInk
import com.bringyour.network.ui.theme.ReferralGoldLight
import com.bringyour.network.ui.theme.ReferralGoldPale
import com.bringyour.network.utils.isTv

/**
 * The referral king-frog gold system, matching the ur.io referral panel:
 * the crowned frog mascot, a pulsing gold aura, gold code pill and gold
 * gradient buttons. Used by the referral celebration moments only, so gold
 * here always means "referral royalty".
 */

/**
 * Animations are skipped when the user has animations disabled system-wide
 * (animator duration scale 0), the platform's reduced-motion signal.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * The crowned frog, gently bobbing like on ur.io (translate + slight tilt).
 */
@Composable
fun ReferralFrog(
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    bob: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()

    val progress = if (bob && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "frog-bob")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "frog-bob-progress"
        ).value
    } else {
        0f
    }

    Image(
        painter = painterResource(id = R.drawable.referral_frog),
        contentDescription = stringResource(id = R.string.referral_royalty),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = -5.dp.toPx() * progress
                rotationZ = -1f + 2f * progress
            }
    )
}

/**
 * Soft pulsing gold aura, drawn behind whatever it wraps.
 */
@Composable
fun GoldAura(
    modifier: Modifier = Modifier,
    pulseMillis: Int = 5000,
    content: @Composable () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()

    val pulse = if (!reducedMotion) {
        val transition = rememberInfiniteTransition(label = "gold-aura")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(pulseMillis / 2),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gold-aura-pulse"
        ).value
    } else {
        0.5f
    }

    val alpha = 0.55f + 0.35f * pulse
    val scale = 1f + 0.06f * pulse

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ReferralGold.copy(alpha = 0.28f * alpha),
                                ReferralGold.copy(alpha = 0f)
                            )
                        )
                    )
                }
        )
        content()
    }
}

/**
 * The referral code in a dark pill with a dashed gold border (the site's code
 * pill). Tapping copies the code.
 */
@Composable
fun ReferralGoldCodePill(
    code: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1800)
            copied = false
        }
    }

    Row(
        modifier = modifier
            .background(
                Color(0x59000000),
                shape = RoundedCornerShape(100)
            )
            .drawBehind {
                drawRoundRect(
                    color = ReferralGold.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    ),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
            .clickable {
                clipboardManager.setText(AnnotatedString(code))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                copied = true
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            code,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = ReferralGoldLight,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 12.dp)
        )

        if (copied) {
            Text(
                stringResource(id = R.string.copied),
                style = MaterialTheme.typography.bodyMedium,
                color = ReferralGoldLight
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.content_copy),
                contentDescription = stringResource(id = R.string.copy),
                tint = ReferralGoldLight,
                modifier = Modifier.width(16.dp)
            )
        }
    }
}

/**
 * Gold gradient pill button (the site's copy/sign-in button treatment) that
 * opens the system share sheet with the given message.
 */
@Composable
fun GoldShareButton(
    shareMessage: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(ReferralGoldPale, ReferralGold)
                ),
                shape = RoundedCornerShape(100)
            )
            .clickable {
                shareText(context, shareMessage)
            }
            .padding(horizontal = 30.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(id = R.string.share),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = ReferralGoldInk
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(id = R.drawable.icon_share),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ReferralGoldInk
        )
    }
}

fun shareText(context: Context, text: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}

/**
 * The "royal welcome" moment shown inside the referral sheets when a code is
 * accepted: frog in a gold aura plus confirmation copy.
 */
@Composable
fun RoyalWelcomeContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GoldAura(
                modifier = Modifier.size(160.dp),
                pulseMillis = 3400
            ) {
                ReferralFrog(size = 108.dp)
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            Text(
                stringResource(id = R.string.referral_royal_welcome),
                style = MaterialTheme.typography.headlineMedium,
                color = ReferralGoldLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            Text(
                stringResource(id = R.string.referral_royal_welcome_detail, REFERRAL_BONUS_GIB_PER_DAY),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The daily GiB each side of a verified referral earns, for life
 * (pro.yml referral: bonus_per_referral / referred_bonus over a 24h period).
 */
const val REFERRAL_BONUS_GIB_PER_DAY = 3

/**
 * The max referrals a network is paid for (pro.yml referral.max_referrals).
 */
const val REFERRAL_MAX_REFERRALS = 20

/**
 * Small gold confirmation chip shown in the signup forms once a referral code
 * has been applied.
 */
@Composable
fun ReferralAppliedChip(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                ReferralGold.copy(alpha = 0.12f),
                shape = RoundedCornerShape(100)
            )
            .drawBehind {
                drawRoundRect(
                    color = ReferralGold.copy(alpha = 0.4f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReferralFrog(size = 24.dp, bob = false)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            stringResource(id = R.string.referral_bonus_applied),
            style = MaterialTheme.typography.bodySmall,
            color = ReferralGoldLight
        )
    }
}

/**
 * Full-screen scaffold for the gold overlays: black ground, close button,
 * back/escape dismissal and TV focus, matching OverlayBackground behavior but
 * with the gold system instead of a background photo.
 */
@Composable
fun GoldOverlayScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                // intercept all touch events so taps cannot fall through to the
                // screen behind the overlay
            }
            .focusGroup()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {

        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {

            content()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Overlay",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (isTv()) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
