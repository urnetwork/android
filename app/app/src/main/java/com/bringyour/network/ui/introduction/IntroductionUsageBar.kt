package com.bringyour.network.ui.introduction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.UsageBar
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionUsageBar(
    navController: NavHostController,
    usedBytes: Long,
    pendingBytes: Long,
    availableBytes: Long,
    meanReliabilityWeight: Double,
    totalReferrals: Long,
) {

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
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        bottomBar = {

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "URnetwork",
                        modifier = Modifier.size(128.dp),

                    )
                }

                // Spacer(modifier = Modifier.height(32.dp))

                Text(
                    stringResource(id = R.string.boost_bandwidth_title),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.boost_bandwidth_details),
                    style = NeueBitLargeTextStyle,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(32.dp))

                UsageBar(
                    usedBytes = usedBytes,
                    pendingBytes = pendingBytes,
                    availableBytes = availableBytes,
                    meanReliabilityWeight = meanReliabilityWeight,
                    totalReferrals = totalReferrals,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    stringResource(id = R.string.default_bandwidth_earn_more_by, 10),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                BulletPoint(text = stringResource(id = R.string.earn_more_bullet_stay_connected, "+100"))

                Spacer(modifier = Modifier.height(16.dp))

                BulletPoint(text = stringResource(id = R.string.earn_more_bullet_refer, "30"))

            }

            Column {
                Spacer(modifier = Modifier.height(16.dp))

                URButton(onClick = {
                    navController.navigate(IntroRoute.IntroductionSettings)
                }) { btnStyle ->
                    Text(
                        stringResource(id = R.string.next),
                        style = btnStyle
                    )
                }
            }

        }
    }

}