package com.bringyour.network.ui.introduction

import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.referral.LocalReferralTerms
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.referral.ReferralGoldPanel
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle

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
    referralCode: String,
    referralCodeFailed: Boolean = false,
    retryReferralCode: () -> Unit = {},
) {

    val terms = LocalReferralTerms.current

    Scaffold(
        topBar = {
            IntroductionTopBar(step = 4, onSkip = dismiss, onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

                ReferralGoldPanel(
                    referralCode = referralCode,
                    totalReferrals = totalReferrals,
                    codeFailed = referralCodeFailed,
                    onRetryCode = retryReferralCode,
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
