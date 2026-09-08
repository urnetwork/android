package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.ui.components.UpgradeScreenHeader
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {
    // the store offers load at launch; repeat the query here when that one
    // failed, so the picker shows the yearly plan Play has for this account
    LaunchedEffect(Unit) {
        planViewModel.ensureStoreOffersLoaded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("")},
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .tabletReadableColumn()
                .verticalScroll(rememberScrollState())
        ) {
            // The same plan picker onboarding shows: yearly is the highlighted
            // default with the free trial, monthly the quiet alternative with
            // no trial. This screen used to render its own monthly-only price
            // row with a "Start free trial" button, so Get Pro offered a
            // different (and wrong) deal than onboarding.
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                UpgradeScreenHeader()

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
            }
        }
    }

}

@Preview
@Composable
private fun UpgradeScreenContentPreview() {

    URNetworkTheme {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            UpgradeScreenHeader()
            Spacer(modifier = Modifier.height(32.dp))
            SubscriptionOptions(
                upgrade = {},
                upgradeInProgress = false,
                monthlyCostFormatted = FALLBACK_MONTHLY_PRICE,
                yearlyCostFormatted = FALLBACK_YEARLY_PRICE
            )
        }
    }
}
