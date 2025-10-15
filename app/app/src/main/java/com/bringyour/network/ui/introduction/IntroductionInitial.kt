package com.bringyour.network.ui.introduction

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffBlack
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
    isCheckingSolanaTransaction: Boolean
) {

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
                "Welcome to URnetwork",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "URnetwork is the most local and most private network on the planet. With over 20x usable cities of other leading VPNs, and 100x fewer users per IP address. Unlock all the fun in the world and all the privacy without compromises.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
//                    .padding(horizontal = 16.dp)
                    .background(
                        OffBlack,
                        RoundedCornerShape(12.dp)
                    )
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                SubscriptionOptions(
                    planViewModel = planViewModel,
                    createSolanaPaymentIntent = createSolanaPaymentIntent,
                    onSolanaUriOpened = { reference ->
                        Log.i(TAG, "solana reference is: $reference")
                        setPendingSolanaSubscriptionReference(reference)
//                        navController.popBackStack()
                    },
                    onStripePaymentSuccess = onStripePaymentSuccess,
                    isCheckingSolanaTransaction = isCheckingSolanaTransaction
                )

            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("or")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
//                    .padding(horizontal = 16.dp)
                    .background(
                        OffBlack,
                        RoundedCornerShape(12.dp)
                    )
                    .fillMaxWidth()
                    .padding(16.dp),

            ) {

                Text(
                    "Participate in the network and get free access to the community edition.",
                    style = TopBarTitleTextStyle,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "URnetwork is powered by a patented protocol that keeps everyone safe and secure.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(onClick = {
                    navController.navigate(IntroRoute.IntroductionUsageBar)
                }) { btnStyle ->
                    Text("Participate", style = btnStyle)
                }
            }

        }
    }

}