package com.bringyour.network.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ChartKey
import com.bringyour.network.ui.components.buttonTextStyle
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.sdkFloat64ListToArray
import com.bringyour.network.utils.sdkIntListToArray
import com.bringyour.sdk.CountryMultiplier
import com.bringyour.sdk.ReliabilityWindow
import java.util.Locale

@Composable
fun NetworkReliability(
    reliabilityWindow: ReliabilityWindow?
) {

    val countryMultipliers = remember { mutableStateListOf<CountryMultiplier>() }

    LaunchedEffect(reliabilityWindow) {
        val countryMultipliersList = reliabilityWindow?.countryMultipliers

        val n = countryMultipliersList?.len() ?: 0
        val list = mutableListOf<CountryMultiplier>()

        for (i in 0 until n) {

            val cm = countryMultipliersList?.get(i)

            if (cm != null && cm.reliabilityMultiplier > 1.0) {
                list.add(cm)
            }

        }

        countryMultipliers.clear()
        countryMultipliers.addAll(list)
    }

    Box(
        modifier =
            Modifier
                .background(
                    color = MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
    ) {

        if (reliabilityWindow != null) {

            Column {
                NetworkReliabilityChart(reliabilityWindow)

                if (countryMultipliers.count() > 0) {
                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(12.dp))

                    CountryMultipliers(
                        countryMultipliers
                    )

                }

            }

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

@Composable
private fun CountryMultipliers(
    countryMultipliers: List<CountryMultiplier>
) {

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            stringResource(id = R.string.country_multipliers),
            style = buttonTextStyle,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                stringResource(id = R.string.country),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Text(
                stringResource(id = R.string.multiplier),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

        }

        for (countryMultiplier in countryMultipliers) {

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    countryMultiplier.country,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "x${countryMultiplier.reliabilityMultiplier}",
                    style = MaterialTheme.typography.bodyMedium
                )

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

                // Pre-calculate client points
                val clientPoints = mutableListOf<Offset>()
                clientsData.forEachIndexed { index, value ->
                    clientPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - ((value - minClientsValue) * clientsScaleFactor)
                    ))
                }

                // Pre-calculate weight points
                val weightPoints = mutableListOf<Offset>()
                weightsData.forEachIndexed { index, value ->
                    weightPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - ((value - minWeightValue) * weightScaleFactor)
                    ))
                }

                // use Path instead of drawing individual lines
                if (clientPoints.size > 1) {
                    val clientPath = Path()
                    clientPath.moveTo(clientPoints[0].x, clientPoints[0].y)

                    for (i in 1 until clientPoints.size) {
                        clientPath.lineTo(clientPoints[i].x, clientPoints[i].y)
                    }

                    drawPath(
                        path = clientPath,
                        color = clientsColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // draw weights
                if (weightPoints.size > 1) {
                    val weightPath = Path()
                    weightPath.moveTo(weightPoints[0].x, weightPoints[0].y)

                    for (i in 1 until weightPoints.size) {
                        weightPath.lineTo(weightPoints[i].x, weightPoints[i].y)
                    }

                    drawPath(
                        path = weightPath,
                        color = weightsColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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