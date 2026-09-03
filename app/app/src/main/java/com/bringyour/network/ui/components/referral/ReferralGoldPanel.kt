package com.bringyour.network.ui.components.referral

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.ReferralGoldInk
import com.bringyour.network.ui.theme.ReferralGoldLight
import com.bringyour.network.ui.theme.ReferralGoldPale
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay

/**
 * The ur.io referral panel (react/src/components/ReferralPanel.jsx, its
 * narrow layout) in Compose: a gold-washed rounded surface with a pulsing
 * aura, the bobbing king frog, kicker / heading / detail copy, the dashed
 * code pill with its gold copy button, a gold share button and a status
 * line. Once the network has a referral the panel is crowned: royal heading,
 * brighter border, faster pulse and the crown line. Shown on the onboarding
 * referral page.
 */
@Composable
fun ReferralGoldPanel(
    referralCode: String?,
    totalReferrals: Long,
    modifier: Modifier = Modifier,
) {
    val crowned = 0L < totalReferrals
    val terms = LocalReferralTerms.current
    val reducedMotion = rememberReducedMotion()

    // the site's aura: opacity .55 -> .9 and scale 1 -> 1.06 over 5s (3.4s crowned)
    val pulse = if (!reducedMotion) {
        val transition = rememberInfiniteTransition(label = "referral-panel-aura")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (crowned) 1700 else 2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "referral-panel-aura-pulse"
        ).value
    } else {
        0.5f
    }

    val shape = RoundedCornerShape(20.dp)
    val borderAlpha = if (crowned) 0.75f else 0.4f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = ReferralGold.copy(alpha = 0.6f),
                spotColor = ReferralGold.copy(alpha = 0.6f)
            )
            .clip(shape)
            .drawBehind {
                // opaque ground so the aura never shows what is behind the panel
                drawRect(Black)
                drawRect(ReferralGold.copy(alpha = 0.04f))
                // the top-left gold wash
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ReferralGold.copy(alpha = 0.16f),
                            ReferralGold.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.width * 1.3f
                    )
                )
                // the pulsing aura
                val auraAlpha = 0.55f + 0.35f * pulse
                val auraRadius = size.maxDimension * 0.7f * (1f + 0.06f * pulse)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ReferralGold.copy(alpha = 0.22f * auraAlpha),
                            ReferralGold.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = center
                )
            }
            .border(1.dp, ReferralGold.copy(alpha = borderAlpha), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            ReferralFrog(size = 108.dp)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                stringResource(id = R.string.referrals).uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = ReferralGold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                if (crowned) {
                    stringResource(id = R.string.referral_royalty)
                } else {
                    stringResource(id = R.string.referral_panel_heading)
                },
                style = TopBarTitleTextStyle.copy(lineHeight = 28.sp),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                stringResource(id = R.string.referral_panel_detail, terms.bonusGibPerDay),
                style = MaterialTheme.typography.bodyMedium,
                color = BlueLight.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (!referralCode.isNullOrBlank()) {

                Text(
                    stringResource(id = R.string.your_referral_code).uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = BlueLight.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                ReferralGoldCodeCopyPill(code = referralCode)

                Spacer(modifier = Modifier.height(12.dp))

                GoldShareButton(
                    shareMessage = stringResource(id = R.string.referral_share_message, referralCode),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ReferralGoldLight,
                    trackColor = TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ReferralProgressBar(
                count = totalReferrals,
                max = terms.maxReferrals
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (crowned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👑", fontSize = 18.sp)

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        pluralStringResource(
                            id = R.plurals.referral_crowned_congrats,
                            count = totalReferrals.toInt(),
                            totalReferrals.toInt(),
                            terms.earnedGibPerDay(totalReferrals)
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ReferralGoldLight
                    )
                }
            } else {
                Text(
                    stringResource(id = R.string.referral_panel_none),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = BlueLight.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Referrals toward the code's cap (the SDK's referral terms, never a literal):
 * a thin gold track that fills per friend, with "joined / cap" under it, and
 * the used-up line once the cap is reached. Shared by the onboarding page and
 * the Refer and earn screen so the two never drift.
 */
@Composable
fun ReferralProgressBar(
    count: Long,
    max: Int,
    modifier: Modifier = Modifier,
) {
    val cap = max.coerceAtLeast(1)
    val joined = count.coerceIn(0L, cap.toLong()).toInt()
    val capped = cap <= joined
    val reducedMotion = rememberReducedMotion()
    val target = joined.toFloat() / cap
    val fraction = if (reducedMotion) {
        target
    } else {
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "referral-progress"
        ).value
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100))
                .background(ReferralGold.copy(alpha = 0.18f))
        ) {
            if (0f < fraction) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(ReferralGold, ReferralGoldLight)),
                            RoundedCornerShape(100)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (capped) stringResource(id = R.string.referral_code_capped) else "$joined / $cap",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = if (capped) ReferralGoldLight else BlueLight.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The site's code pill: the code in a dark dashed-gold pill with the gold
 * gradient Copy button inside it. Tapping either copies; the button turns
 * green and reads "Copied!" for a moment.
 */
@Composable
fun ReferralGoldCodeCopyPill(
    code: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val copy = {
        clipboardManager.setText(AnnotatedString(code))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copied = true
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
            .clickable { copy() }
            .padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            code,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = ReferralGoldLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 10.dp)
        )

        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = if (copied) {
                            listOf(Color(0xFFB7F7C6), Green)
                        } else {
                            listOf(ReferralGoldPale, ReferralGold)
                        }
                    ),
                    shape = RoundedCornerShape(100)
                )
                .clickable { copy() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ink = if (copied) Color(0xFF08240F) else ReferralGoldInk

            if (copied) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            Text(
                stringResource(id = if (copied) R.string.copied else R.string.copy),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ink
            )
        }
    }
}

@Preview
@Composable
private fun ReferralGoldPanelPreview() {
    URNetworkTheme {
        Box(modifier = Modifier.background(Black).padding(16.dp)) {
            ReferralGoldPanel(
                referralCode = "asdlfkjsldkfjsdf",
                totalReferrals = 0
            )
        }
    }
}

@Preview
@Composable
private fun ReferralGoldPanelCrownedPreview() {
    URNetworkTheme {
        Box(modifier = Modifier.background(Black).padding(16.dp)) {
            ReferralGoldPanel(
                referralCode = "asdlfkjsldkfjsdf",
                totalReferrals = 4
            )
        }
    }
}
