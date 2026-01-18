package com.bringyour.network.ui.introduction

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.redeemTransferBalanceCode.RedeemTransferBalanceCodeSheet
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle
import com.bringyour.network.ui.theme.NeueBitSmallTextStyle
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.upgrade.SubscriptionOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionInitial(
    navController: NavHostController,
    dismiss: () -> Unit,
    planViewModel: PlanViewModel,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    onRedeemTransferBalanceCodeSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    var isPresentingRedeemTransferBalanceSheet by remember { mutableStateOf(false) }
    val redeemTransferBalanceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(
                        onClick = dismiss
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            Text(
                stringResource(id = R.string.welcome_to_urnetwork),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(id = R.string.urnetwork_intro_description),
                style = NeueBitLargeTextStyle,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(32.dp))

            BulletPoint(stringResource(id = R.string.open_source_transparent))

            Spacer(modifier = Modifier.height(16.dp))

            BulletPoint(stringResource(id = R.string.low_user_ip_ratio))

            Spacer(modifier = Modifier.height(16.dp))

            BulletPoint(stringResource(id = R.string.trusted_by_private_networks, "100,000"))

            Spacer(modifier = Modifier.height(32.dp))

            SubscriptionOptions(
                planViewModel = planViewModel,
                createSolanaPaymentIntent = createSolanaPaymentIntent,
                onSolanaUriOpened = { reference ->
                    setPendingSolanaSubscriptionReference(reference)
                },
                onStripePaymentSuccess = onStripePaymentSuccess,
                isCheckingSolanaTransaction = isCheckingSolanaTransaction
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(stringResource(id = R.string.or))
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Community Edition link
             */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = TextMuted, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .clickable {
                        navController.navigate(IntroRoute.IntroductionUsageBar)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(id = R.string.community_edition),
                    style = TopBarTitleTextStyle,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.community_edition_details),
                    style = NeueBitSmallTextStyle,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Redeem balance code
             */

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = TextMuted, shape = RoundedCornerShape(12.dp))
                    .clickable {
                        isPresentingRedeemTransferBalanceSheet = true
                        // navController.navigate(IntroRoute.IntroductionUsageBar)
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(id = R.string.redeem_balance_code),
                    style = TopBarTitleTextStyle,
                    color = TextMuted
                )

            }

            Spacer(modifier = Modifier.height(32.dp))


            Text(
                stringResource(id = R.string.participate_intro_details),
                modifier = Modifier.fillMaxWidth(),
                style = TopBarTitleTextStyle,
                textAlign = TextAlign.Center
            )

            if (isPresentingRedeemTransferBalanceSheet) {
                RedeemTransferBalanceCodeSheet(
                    sheetState = redeemTransferBalanceSheetState,
                    setIsPresenting = {
                        isPresentingRedeemTransferBalanceSheet = it
                    },
                    onSuccess = {
                        onRedeemTransferBalanceCodeSuccess()
                    }
                )
            }

        }
    }

}