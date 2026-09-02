package com.bringyour.network.ui.introduction

import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.referral.LocalReferralTerms
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ChartKey
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.referral.ReferralGoldPanel
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * The last onboarding page: what a referral earns, how far along the paid
 * referrals the network is, and the refer-friends box in the referral
 * king-frog gold theme (the ur.io referral panel).
 */
@Composable
fun IntroductionReferral(
    navController: NavHostController,
    dismiss: () -> Unit,
    totalReferrals: Long,
    referralCode: String
) {

    val terms = LocalReferralTerms.current

    Scaffold(
        topBar = {
            IntroductionTopBar(step = 4, onSkip = dismiss, onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {


                Text(
                    stringResource(id = R.string.refer_friends_header),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.when_you_refer_a_friend),
                    style = NeueBitLargeTextStyle,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(16.dp))

                BulletPoint(stringResource(id = R.string.refer_friends_perks, terms.bonusGibPerDay.toString()))

                Spacer(modifier = Modifier.height(16.dp))

                BulletPoint(stringResource(id = R.string.refer_friends_they_get_data, terms.referredBonusGibPerDay.toString()))

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .background(
                            OffBlack,
                            RoundedCornerShape(12.dp)
                        )
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(id = R.string.refer_friends_header),
                            style = TopBarTitleTextStyle
                        )

                        Text(
                            "${totalReferrals}/${terms.maxReferrals}",
                            style = TopBarTitleTextStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ReferralBar(
                        totalReferrals = totalReferrals,
                        maxReferrals = terms.maxReferrals
                    )

                }

                Spacer(modifier = Modifier.height(24.dp))

                ReferralGoldPanel(
                    referralCode = referralCode,
                    totalReferrals = totalReferrals
                )

                Spacer(modifier = Modifier.height(24.dp))

            }

            URButton(onClick = {
                navController.navigate(IntroRoute.IntroductionQuickConnect)
            }) { btnStyle ->
                Text(
                    stringResource(id = R.string.next),
                    style = btnStyle
                )
            }

        }
    }
}

/**
 * Referrals earned out of the ones that pay, in referral gold.
 */
@Composable
fun ReferralBar(
    totalReferrals: Long,
    maxReferrals: Int = LocalReferralTerms.current.maxReferrals,
) {

    val maxReferrals = maxReferrals.toFloat()
    val cornerRadius = 6.dp
    val usedColor = ReferralGold
    val availableColor = TextFaint

    var displayReferralCount = totalReferrals.toFloat()

    if (displayReferralCount >= maxReferrals) {
        displayReferralCount = maxReferrals
    }

    val usedFraction = displayReferralCount / maxReferrals
    val availableFraction = 1f - usedFraction

    val minWeight = 0.0001f
    val safeUsedFraction = if (usedFraction <= 0f) minWeight else usedFraction
    val safeAvailableFraction = if (availableFraction <= 0f) minWeight else availableFraction


    Column {

        Row(modifier = Modifier
            .height(12.dp)
            .fillMaxWidth()
        ) {

            Box(
                modifier = Modifier
                    .weight(safeUsedFraction)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerRadius,
                            bottomStart = cornerRadius,
                            topEnd = if (displayReferralCount == maxReferrals) cornerRadius else 0.dp,
                            bottomEnd = if (displayReferralCount == maxReferrals) cornerRadius else 0.dp,
                        )
                    )
                    .background(usedColor)
            )

            Box(
                modifier = Modifier
                    .weight(safeAvailableFraction)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = if (displayReferralCount == 0f) cornerRadius else 0.dp,
                            bottomStart = if (displayReferralCount == 0f) cornerRadius else 0.dp,
                            topEnd = cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    )
                    .background(availableColor)
            )

        }

        Spacer(modifier = Modifier.height(4.dp))

        Row {

            // used
            ChartKey(
                label = stringResource(id = R.string.referrals),
                color = usedColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // available
            ChartKey(
                label = stringResource(id = R.string.available_data_key),
                color = availableColor
            )
        }
    }
}
