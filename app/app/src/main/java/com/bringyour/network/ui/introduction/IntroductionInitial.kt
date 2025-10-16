package com.bringyour.network.ui.introduction

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
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
                stringResource(id = R.string.welcome_to_urnetwork),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(id = R.string.urnetwork_intro_description),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
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
                        setPendingSolanaSubscriptionReference(reference)
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
                Text(stringResource(id = R.string.or))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .background(
                        OffBlack,
                        RoundedCornerShape(12.dp)
                    )
                    .fillMaxWidth()
                    .padding(16.dp),

            ) {

                Text(
                    stringResource(id = R.string.participate_intro),
                    style = TopBarTitleTextStyle,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.participate_intro_details),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(onClick = {
                    navController.navigate(IntroRoute.IntroductionUsageBar)
                }) { btnStyle ->
                    Text(stringResource(id = R.string.participate), style = btnStyle)
                }
            }

        }
    }

}