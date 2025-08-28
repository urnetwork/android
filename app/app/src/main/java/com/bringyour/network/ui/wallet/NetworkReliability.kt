package com.bringyour.network.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ChartKey
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.sdkFloat64ListToArray
import com.bringyour.network.utils.sdkIntListToArray
import com.bringyour.sdk.ReliabilityWindow
import java.util.Locale

@Composable
fun NetworkReliability(
    reliabilityWindow: ReliabilityWindow?
) {

    Column {
        Box(
            modifier =
                Modifier
                    .background(
                        color = MainTintedBackgroundBase,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        bottom = 10.dp,
                        end = 16.dp
                    )
        ) {

            if (reliabilityWindow != null) {
                NetworkReliabilityChart(reliabilityWindow)
            } else {
                // loading indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = TextMuted
                    )
                }
            }
        }
    }

}

@Composable
private fun NetworkReliabilityChart(reliabilityWindow: ReliabilityWindow) {
    val weights = remember { mutableStateListOf<Double>() }
    val mean = remember { mutableDoubleStateOf(0.0) }
    val totalClients = remember { mutableStateListOf<Long>() }

    LaunchedEffect(reliabilityWindow) {
        mean.doubleValue = reliabilityWindow.meanReliabilityWeight
        weights.clear()
        if (reliabilityWindow.reliabilityWeights != null) {
            // performance is kind of bad rendering all points, so we subsample data
            val sampledWeights = sdkFloat64ListToArray(reliabilityWindow.reliabilityWeights)
                .filterIndexed { index, _ -> index % 4 == 0 }
            weights.addAll(sampledWeights)
        }

        totalClients.clear()
        if (reliabilityWindow.totalClientCounts != null) {
            val sampledClients = sdkIntListToArray(reliabilityWindow.totalClientCounts)
                .filterIndexed { index, _ -> index % 4 == 0 }
            totalClients.addAll(sampledClients)
        }
    }

    // Skip drawing if data is empty
    if (weights.isEmpty() || totalClients.isEmpty()) {
        return
    }

    val weightsColor = remember { Pink.copy(alpha = 0.6f) }
    val clientsColor = remember { Green }
    val gridLineColor = remember { TextMuted.copy(alpha = 0.5f) }
    val rightPadding = 50f

    val weightsData = remember(weights) { weights.map { it.toFloat() } }
    val clientsData = remember(totalClients) { totalClients.map { it.toFloat() } }

    val maxWeightValue = remember(weightsData) { weightsData.maxOrNull() ?: 1f }
    val minWeightValue = remember(weightsData) { weightsData.minOrNull() ?: 0f }
    val maxClientsValue = remember(clientsData) { clientsData.maxOrNull() ?: 1f }
    val minClientsValue = remember(clientsData) { clientsData.minOrNull() ?: 0f }

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#989898") // TextMuted
            textSize = 28f
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }

    Column {

        Text(
            stringResource(id = R.string.average_reliability),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Text("${String.format(Locale.US, "%,.2f", mean.doubleValue * 100)}%", style = HeadingLargeCondensed)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .graphicsLayer() // Enable hardware acceleration
        ) {

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val chartWidth = size.width - rightPadding

                val weightScaleFactor = if (maxWeightValue - minWeightValue == 0f) 1f
                else (size.height * 0.9f) / (maxWeightValue - minWeightValue)

                val clientsScaleFactor = if (maxClientsValue - minClientsValue == 0f) 1f
                else (size.height * 0.9f) / (maxClientsValue - minClientsValue)

                val pointSpacing = if (weightsData.size > 1) chartWidth / (weightsData.size - 1) else chartWidth
                val yStep = (maxWeightValue - minWeightValue) / 4

                // Draw grid lines
                for (i in 0..4) {
                    val yValue = minWeightValue + i * yStep
                    val yPosition = size.height - ((yValue - minWeightValue) * weightScaleFactor)

                    // Draw grid line at this position
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, yPosition),
                        end = Offset(chartWidth, yPosition),
                        strokeWidth = 1f
                    )

                    // Draw Y-axis label
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%.2f", yValue),
                        chartWidth + 10f,
                        yPosition + 8f,
                        textPaint
                    )
                }

                // Pre-calculate client points for better performance
                val clientPoints = mutableListOf<Offset>()
                clientsData.forEachIndexed { index, value ->
                    clientPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - ((value - minClientsValue) * clientsScaleFactor)
                    ))
                }

                // Pre-calculate weight points for better performance
                val weightPoints = mutableListOf<Offset>()
                weightsData.forEachIndexed { index, value ->
                    weightPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - ((value - minWeightValue) * weightScaleFactor)
                    ))
                }

                // Draw client lines in one pass
                for (i in 0 until clientPoints.size - 1) {
                    drawLine(
                        color = clientsColor,
                        start = clientPoints[i],
                        end = clientPoints[i + 1],
                        strokeWidth = 5f
                    )
                }

                // Draw weight lines in one pass
                for (i in 0 until weightPoints.size - 1) {
                    drawLine(
                        color = weightsColor,
                        start = weightPoints[i],
                        end = weightPoints[i + 1],
                        strokeWidth = 5f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row {

            // used
            ChartKey(
                label = stringResource(id = R.string.reliability_weight),
                color = weightsColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // pending
            ChartKey(
                label = stringResource(id = R.string.total_clients),
                color = clientsColor
            )
        }

    }
}