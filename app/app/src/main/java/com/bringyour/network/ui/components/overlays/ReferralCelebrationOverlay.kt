package com.bringyour.network.ui.components.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.GoldAura
import com.bringyour.network.ui.components.referral.GoldOverlayScaffold
import com.bringyour.network.ui.components.referral.GoldShareButton
import com.bringyour.network.ui.components.referral.REFERRAL_BONUS_GIB_PER_DAY
import com.bringyour.network.ui.components.referral.ReferralFrog
import com.bringyour.network.ui.components.referral.ReferralGoldCodePill
import com.bringyour.network.ui.theme.ReferralGoldLight
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * One-time crowning celebration for the referrer: shown the first time a
 * friend joins with their code (later referrals get the gold toast instead).
 * Same king frog and gold aura as the ur.io referral panel.
 */
@Composable
fun ReferralCelebrationOverlay(
    joinedCount: Long,
    referralCode: String?,
    onDismiss: () -> Unit,
) {

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    GoldOverlayScaffold(
        onDismiss = onDismiss
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 512.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // the royal frog is crowned in a fast gold aura -- the
                    // site's "royal" pulse cadence
                    GoldAura(
                        modifier = Modifier.size(240.dp),
                        pulseMillis = 3400
                    ) {
                        ReferralFrog(size = 144.dp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(id = R.string.referral_royalty),
                        style = MaterialTheme.typography.headlineLarge,
                        color = ReferralGoldLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        pluralStringResource(
                            id = R.plurals.referral_celebration_detail,
                            count = joinedCount.toInt(),
                            joinedCount.toInt(),
                            REFERRAL_BONUS_GIB_PER_DAY
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (!referralCode.isNullOrEmpty()) {

                        ReferralGoldCodePill(
                            code = referralCode,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GoldShareButton(
                            shareMessage = stringResource(
                                id = R.string.referral_share_message,
                                referralCode
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ReferralCelebrationOverlayPreview() {
    URNetworkTheme {
        ReferralCelebrationOverlay(
            joinedCount = 1,
            referralCode = "ABC123",
            onDismiss = {}
        )
    }
}
