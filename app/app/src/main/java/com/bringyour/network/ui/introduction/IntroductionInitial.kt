package com.bringyour.network.ui.introduction

import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.upgrade.SubscriptionOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionInitial(
    navController: NavHostController,
    dismiss: () -> Unit,
    planViewModel: PlanViewModel,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
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
            IntroductionTopBar(step = 1, onSkip = dismiss)
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

                IntroTraveller()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    stringResource(id = R.string.welcome_to_urnetwork),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.intro_verifiable_encryption),
                    style = NeueBitLargeTextStyle,
                    textAlign = TextAlign.Start
                )

                // room for the plan box's halo and pill, and air between it and the tagline
                Spacer(modifier = Modifier.height(52.dp))

                SubscriptionOptions(
                    planViewModel = planViewModel,
                    createSolanaPaymentIntent = createSolanaPaymentIntent,
                    onSolanaUriOpened = { reference ->
                        setPendingSolanaSubscriptionReference(reference)
                    },
                    onStripePaymentSuccess = onStripePaymentSuccess,
                    isCheckingSolanaTransaction = isCheckingSolanaTransaction
                )
            }

            /**
             * The other ways in, as quiet links at the bottom: the screen is
             * about starting the free trial.
             */
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = {
                        navController.navigate(IntroRoute.IntroductionUsageBar)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text(
                        stringResource(id = R.string.community_edition),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                TextButton(
                    onClick = {
                        isPresentingRedeemTransferBalanceSheet = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text(
                        stringResource(id = R.string.redeem_balance_code),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

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
