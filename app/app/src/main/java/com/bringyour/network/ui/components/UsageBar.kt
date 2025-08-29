package com.bringyour.network.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
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
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun UsageBar(
    usedBytes: Long,
    pendingBytes: Long,
    availableBytes: Long
) {
    
    val totalBytes = usedBytes + pendingBytes + availableBytes
    val cornerRadius = 6.dp

    val usedColor = BlueMedium
    val pendingColor = Red
    val availableColor = TextFaint

    fun minNonZeroBytes(bytes: Long): Long {

        if (bytes.toInt() == 0) {
             // if 0, we don't display that part of the bar in the chart
            return bytes
        }

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
                if (usedBytes > 0) {
                    Box(
                        modifier = Modifier
                            .weight(minNonZeroBytes(usedBytes).toFloat() / totalBytes)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = cornerRadius,
                                    bottomStart = cornerRadius,
                                    topEnd = if ((pendingBytes + availableBytes).toInt() == 0) cornerRadius else 0.dp,
                                    bottomEnd = if ((pendingBytes + availableBytes).toInt() == 0) cornerRadius else 0.dp,
                                )
                            )
                            .background(usedColor)
                    )
                }

                // pending
                if (pendingBytes > 0) {
                    Box(
                        modifier = Modifier
                            .weight(minNonZeroBytes(pendingBytes).toFloat() / totalBytes)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = if (usedBytes.toInt() == 0) cornerRadius else 0.dp,
                                    bottomStart = if (usedBytes.toInt() == 0) cornerRadius else 0.dp,
                                    topEnd = if (availableBytes.toInt() == 0) cornerRadius else 0.dp,
                                    bottomEnd = if (availableBytes.toInt() == 0) cornerRadius else 0.dp
                                )
                            )
                            .background(pendingColor)
                    )
                }

                // available
                if (availableBytes > 0) {
                    Box(
                        modifier = Modifier
                            .weight(minNonZeroBytes(availableBytes).toFloat() / totalBytes)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart = if ((pendingBytes + usedBytes).toInt() == 0) cornerRadius else 0.dp,
                                    bottomStart = if ((pendingBytes + usedBytes).toInt() == 0) cornerRadius else 0.dp,
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
                    availableBytes = 60_000
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * no available bytes
                 */
                UsageBar(
                    usedBytes = 30_000,
                    pendingBytes = 20_000,
                    availableBytes = 0
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all used
                 */
                UsageBar(
                    usedBytes = 30_000,
                    pendingBytes = 0,
                    availableBytes = 0
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all pending
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 30_000,
                    availableBytes = 0
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * all available
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 0,
                    availableBytes = 30_000
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * no used, but has pending and available
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 15_000,
                    availableBytes = 30_000
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * used and available, no pending
                 */
                UsageBar(
                    usedBytes = 15_000,
                    pendingBytes = 0,
                    availableBytes = 30_000
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * loading
                 */
                UsageBar(
                    usedBytes = 0,
                    pendingBytes = 0,
                    availableBytes = 0
                )
            }
        }
    }
}
