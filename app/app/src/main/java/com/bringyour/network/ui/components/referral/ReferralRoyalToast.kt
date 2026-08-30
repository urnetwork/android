package com.bringyour.network.ui.components.referral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.ReferralGoldInk
import com.bringyour.network.ui.theme.ReferralGoldPale

/**
 * Non-blocking gold toast for referrals after the first: slides in from the
 * top with the mini king frog. Tapping opens the refer flow; it is dismissed
 * by its owner after a few seconds.
 */
@Composable
fun ReferralRoyalToast(
    visible: Boolean,
    text: String,
    onClick: () -> Unit,
) {

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(visible) {
        if (visible) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 480.dp)
                    .shadow(8.dp, RoundedCornerShape(100), ambientColor = ReferralGold, spotColor = ReferralGold)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ReferralGoldPale, ReferralGold)
                        ),
                        shape = RoundedCornerShape(100)
                    )
                    .clickable { onClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReferralFrog(size = 28.dp, bob = false)

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = ReferralGoldInk
                )
            }
        }
    }
}
