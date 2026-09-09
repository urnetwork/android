package com.bringyour.network.ui.upgrade

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.components.UpgradeScreenHeader
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.sdk.Sdk

/**
 * Get Pro: the title and the same plan picker the onboarding plan step shows
 * (the welcome offer included while it is active; this screen never issues
 * it), so every plan surface offers the same deal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .tabletReadableColumn()
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                UpgradeScreenHeader()
                Spacer(modifier = Modifier.height(32.dp))
                SubscriptionOptions(
                    planViewModel = planViewModel,
                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                    surface = Sdk.OfferSurfaceAccount,
                    createSolanaPaymentIntent = createSolanaPaymentIntent,
                    onSolanaUriOpened = { reference ->
                        setPendingSolanaSubscriptionReference(reference)
                        navController.popBackStack()
                    },
                    onStripePaymentSuccess = onStripePaymentSuccess,
                    isCheckingSolanaTransaction = isCheckingSolanaTransaction
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
