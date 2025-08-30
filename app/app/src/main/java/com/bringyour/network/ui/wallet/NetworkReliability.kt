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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ChartKey
import com.bringyour.network.ui.components.buttonTextStyle
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.OffWhite
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.sdkFloat64ListToArray
import com.bringyour.network.utils.sdkIntListToArray
import com.bringyour.sdk.CountryMultiplier
import com.bringyour.sdk.ReliabilityWindow
import java.util.Locale
import kotlin.math.ceil

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
                //  .padding(16.dp)
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

    val highlightMultiplierThreshold = 2.0

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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (countryMultiplier.reliabilityMultiplier >= highlightMultiplierThreshold) FontWeight.Bold else FontWeight.Normal,
                    color = if (countryMultiplier.reliabilityMultiplier >= highlightMultiplierThreshold) Green else OffWhite
                )

                Row {

                    Text(
                        "x${countryMultiplier.reliabilityMultiplier}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (countryMultiplier.reliabilityMultiplier >= highlightMultiplierThreshold) FontWeight.Bold else FontWeight.Normal,
                        color = if (countryMultiplier.reliabilityMultiplier >= highlightMultiplierThreshold) Green else OffWhite
                    )

                }

            }

        }

    }

}

@Composable
private fun NetworkReliabilityChart(reliabilityWindow: ReliabilityWindow) {
    val weights = remember { mutableStateListOf<Double>() }
    val averageWeightLine = remember { mutableStateListOf<Double>() }
    val mean = remember { mutableDoubleStateOf(0.0) }
    val clients = remember { mutableStateListOf<Long>() }

    LaunchedEffect(reliabilityWindow) {
        mean.doubleValue = reliabilityWindow.meanReliabilityWeight
        weights.clear()
        if (reliabilityWindow.reliabilityWeights != null) {
            // performance is kind of bad rendering all points, so we subsample data
            val sampledWeights = sdkFloat64ListToArray(reliabilityWindow.reliabilityWeights)
//                 .filterIndexed { index, _ -> index % 4 == 0 }
            weights.addAll(sampledWeights)
        }

        // Create average line with the same mean value for each point
        val arr = mutableListOf<Double>()
        for (i in 0 until weights.count()) {
            arr.add(mean.doubleValue)
        }
        averageWeightLine.clear()
        averageWeightLine.addAll(arr)

        clients.clear()
        if (reliabilityWindow.clientCounts != null) {
            val sampledClients = sdkIntListToArray(reliabilityWindow.clientCounts)
//                 .filterIndexed { index, _ -> index % 4 == 0 }
            clients.addAll(sampledClients)
        }
    }

    // Skip drawing if data is empty
    if (weights.isEmpty() || clients.isEmpty()) {
        return
    }

    val weightsColor = remember { Pink.copy(alpha = 0.6f) }
    val clientsColor = remember { Green }
    val gridLineColor = remember { TextMuted.copy(alpha = 0.5f) }
    val averageLineColor = remember { TextMuted }
    val rightPadding = 50f

    val weightsData = remember(weights) { weights.map { it.toFloat() } }
    val averageWeightsData = remember(averageWeightLine) { averageWeightLine.map { it.toFloat() } }
    val clientsData = remember(clients) { clients.map { it.toFloat() } }

    val maxClientsValue = remember(clientsData) { clientsData.maxOrNull() ?: 1f }

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

        Text("${String.format(Locale.US, "%,.2f", mean.doubleValue)}", style = HeadingLargeCondensed)

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

                // Use a single scale for both lines based on client count
                val maxClient = maxClientsValue.toInt()
                val niceStepSize = when {
                    maxClient <= 5 -> 1    // For values up to 5, use steps of 1
                    maxClient <= 10 -> 2   // For values up to 10, use steps of 2
                    maxClient <= 20 -> 5   // For values up to 20, use steps of 5
                    maxClient <= 50 -> 10  // For values up to 50, use steps of 10
                    maxClient <= 100 -> 20 // For values up to 100, use steps of 20
                    else -> ceil(maxClient / 5f).toInt() // For larger values, divide by 5
                }

                // Calculate steps based on the nice step size
                val maxClientRounded = ceil(maxClient / niceStepSize.toFloat()) * niceStepSize
                val steps = (maxClientRounded / niceStepSize).toInt()

                // Single scale factor for both client and weight data
                val scaleFactor = (size.height * 0.9f) / maxClientRounded
                val pointSpacing = if (weightsData.size > 1) chartWidth / (weightsData.size - 1) else chartWidth

                // Draw grid lines and labels
                for (i in 0..steps) {
                    val yValue = i * niceStepSize.toFloat()

                    // Calculate Y position with common scaling
                    val yPosition = size.height - (yValue * scaleFactor)

                    // Draw grid line at this position
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, yPosition),
                        end = Offset(chartWidth, yPosition),
                        strokeWidth = 1f
                    )

                    // draw Y-axis label as integer
                    drawContext.canvas.nativeCanvas.drawText(
                        yValue.toInt().toString(), // Format as integer
                        chartWidth + 10f,
                        yPosition + 8f,
                        textPaint
                    )
                }

                // calculate client points
                val clientPoints = mutableListOf<Offset>()
                clientsData.forEachIndexed { index, value ->
                    clientPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - (value * scaleFactor)
                    ))
                }

                // calcuate weight points
                val weightPoints = mutableListOf<Offset>()
                weightsData.forEachIndexed { index, value ->
                    weightPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - (value.toFloat() * scaleFactor)
                    ))
                }

                // calculate average weight points
                val averageWeightPoints = mutableListOf<Offset>()
                averageWeightsData.forEachIndexed { index, value ->
                    averageWeightPoints.add(Offset(
                        x = index * pointSpacing,
                        y = size.height - (value.toFloat() * scaleFactor)
                    ))
                }

                if (averageWeightPoints.size > 1) {
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    val averageWeightPath = Path()
                    averageWeightPath.moveTo(averageWeightPoints[0].x, averageWeightPoints[0].y)

                    for (i in 1 until averageWeightPoints.size) {
                        averageWeightPath.lineTo(averageWeightPoints[i].x, averageWeightPoints[i].y)
                    }

                    drawPath(
                        path = averageWeightPath,
                        color = averageLineColor,
                        style = Stroke(
                            width = 2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = dashPathEffect
                        )
                    )
                }

                // draw clients points
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

            ChartKey(
                label = stringResource(id = R.string.reliability_weight),
                color = weightsColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            ChartKey(
                stringResource(id = R.string.clients),
                color = clientsColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            ChartKey(
                label = stringResource(id = R.string.average_reliability)
                    // title case
                    .split(" ")
                    .joinToString(separator = " ") {
                        it.lowercase().replaceFirstChar { char ->
                            char.titlecase()
                        }
                    },
                color = averageLineColor
            )
        }
    }
}
