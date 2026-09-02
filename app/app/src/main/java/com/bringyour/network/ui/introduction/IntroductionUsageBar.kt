package com.bringyour.network.ui.introduction

import com.bringyour.network.utils.formatByteCountCompact
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.UsageBar
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionUsageBar(
    navController: NavHostController,
    dismiss: () -> Unit,
    usedBytes: Long,
    pendingBytes: Long,
    availableBytes: Long,
    meanReliabilityWeight: Double,
    totalReferrals: Long,
    dailyByteCount: Long
) {

    Scaffold(
        topBar = {
            IntroductionTopBar(step = 2, onSkip = dismiss, onBack = { navController.popBackStack() })
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


                // Spacer(modifier = Modifier.height(32.dp))

                Text(
                    stringResource(id = R.string.your_bandwidth),
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.you_get_free_data_every_day),
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
                    dailyByteCount = dailyByteCount,
                    showReferrals = false
                )

                if (0L < dailyByteCount) {

                    Spacer(modifier = Modifier.height(32.dp))

                    // the free allowance the server grants (pro.yml free.data per
                    // data_period), never a number typed into the app
                    Text(
                        stringResource(id = R.string.default_daily_data, formatDailyAllowance(dailyByteCount)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

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

/**
 * The daily allowance for prose: whole gibibytes without decimals ("30 GiB"),
 * anything else in the compact form.
 */
private fun formatDailyAllowance(byteCount: Long): String {
    val gib = 1024L * 1024L * 1024L
    return if (0L < byteCount && byteCount % gib == 0L) {
        "${byteCount / gib} GiB"
    } else {
        formatByteCountCompact(byteCount)
    }
}
