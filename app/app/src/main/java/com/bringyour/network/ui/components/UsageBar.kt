package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.formatBalanceBytes
import kotlin.math.min

@Composable
fun UsageBar(
    usedBytes: Long,
    pendingBytes: Long,
    availableBytes: Long,
    meanReliabilityWeight: Double,
    totalReferrals: Long,
    dailyByteCount: Long
) {
    
    val totalBytes = usedBytes + pendingBytes + availableBytes
    val cornerRadius = 6.dp

    val usedColor = BlueMedium
    val pendingColor = Red
    val availableColor = TextFaint

    fun minNonZeroBytes(bytes: Long): Long {

        val minNonZeroBytes = totalBytes * 0.015

        if (bytes < minNonZeroBytes) {
            // if it's less than the min bytes
            // we inflate the number so it doesn't look too small
            return minNonZeroBytes.toLong()
        }

        // all good
        return bytes
    }

    Column {

        // bar
        Row(modifier = Modifier
            .height(12.dp)
            .fillMaxWidth()
        ) {

            if (totalBytes <= 0) {
                /**
                 * Loading
                 */
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(TextFaint.copy(alpha = 0.3f))
                )
            } else {
                /**
                 * Initialized
                 */

                // used
                Box(
                    modifier = Modifier
                        .weight(minNonZeroBytes(usedBytes).toFloat() / totalBytes)
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(
                                topStart = cornerRadius,
                                bottomStart = cornerRadius,
                                topEnd = if (usedBytes == totalBytes) cornerRadius else 0.dp,
                                bottomEnd = if (usedBytes == totalBytes) cornerRadius else 0.dp,
                            )
                        )
                        .background(usedColor)
                )


                /**
                 * pending
                 * hide if all data is used for the day
                 */
                if (usedBytes != totalBytes) {

                    Box(
                        modifier = Modifier
                            .weight(minNonZeroBytes(pendingBytes).toFloat() / totalBytes)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 0.dp,
                                    bottomStart = 0.dp,
                                    topEnd = if (availableBytes == 0L) cornerRadius else 0.dp,
                                    bottomEnd = if (availableBytes == 0L) cornerRadius else 0.dp
                                )
                            )
                            .background(pendingColor)
                    )
                }


                // available
                if (availableBytes > 0) {
                    Box(
                        modifier = Modifier
                            .weight(availableBytes.toFloat() / totalBytes)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 0.dp,
                                    bottomStart = 0.dp,
                                    topEnd = cornerRadius,
                                    bottomEnd = cornerRadius
                                )
                            )
                            .background(availableColor)
                    )
                }

            }

        }

        Spacer(modifier = Modifier.height(4.dp))

        /**
         * Keys
         */
        Row {

            // used
            ChartKey(
                label = stringResource(id = R.string.used_data_key),
                color = usedColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // pending
            ChartKey(
                label = stringResource(id = R.string.pending_data_key),
                color = pendingColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // available
            ChartKey(
                label = stringResource(id = R.string.available_data_key),
                color = availableColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        /**
         * Data breakdown
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(id = R.string.daily_data_balance_label),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            
            Text(
                formatBalanceBytes(dailyByteCount),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        /**
         * reliability
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.reliability_with_value, meanReliabilityWeight),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(
                stringResource(R.string.reliability_bonus, min(meanReliabilityWeight * 100, 100.0)),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        /**
         * referrals
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.total_referral_count, totalReferrals),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(

                stringResource(R.string.referral_bonus, totalReferrals * 30),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@Preview
@Composable
private fun UsageBarPreview() {

    URNetworkTheme {
        Scaffold { innerPadding ->
            Column(modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
            ) {
                UsageBar(
                    usedBytes = 30_000,
                    pendingBytes = 20_000,
                    availableBytes = 60_000,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * no available bytes
                 */
                UsageBar(
                    usedBytes = 30_000,
                    pendingBytes = 20_000,
                    availableBytes = 0,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all used
                 */
                UsageBar(
                    usedBytes = 30_000,
                    pendingBytes = 0,
                    availableBytes = 0,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all pending
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 30_000,
                    availableBytes = 0,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all available
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 0,
                    availableBytes = 30_000,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * no used, but has pending and available
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 15_000,
                    availableBytes = 30_000,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * used and available, no pending
                 */
                UsageBar(
                    usedBytes = 15_000,
                    pendingBytes = 0,
                    availableBytes = 30_000,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * loading
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 0,
                    availableBytes = 0,
                    meanReliabilityWeight = 0.1,
                    totalReferrals = 1,
                    dailyByteCount = 100_00
                )
            }
        }
    }
}
