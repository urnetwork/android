package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ProviderGridPoint
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.network.ui.theme.ppNeueBitBold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectButton(
    onClick: () -> Unit,
    grid: ConnectGrid?,
    providerGridPoints: List<ProviderGridPoint>,
    connectStatus: ConnectStatus,
) {

    Box(
        modifier = Modifier
            .size(256.dp)
            .clipToBounds()
            .background(color = Color(0x0AFFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .zIndex(0f)
        ) {

            if (connectStatus == ConnectStatus.DISCONNECTED) {
                DisconnectedButtonContent()
            }

            if (connectStatus == ConnectStatus.CONNECTED
                || connectStatus == ConnectStatus.CONNECTING
                || connectStatus == ConnectStatus.DESTINATION_SET) {
                ConnectingButtonContent(
                    providerGridPoints = providerGridPoints,
                    grid = grid,
                )
            }

        }

        Image(
            painter = painterResource(id = R.drawable.connect_mask),
            contentDescription = "Connect Mask",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(512.dp)
                .align(Alignment.Center)
                .zIndex(1f)
        )
    }
}

@Composable
private fun ConnectingButtonContent(
    grid: ConnectGrid?,
    providerGridPoints: List<ProviderGridPoint>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.connector_globe),
            contentDescription = "Connecting"
        )

        GridCanvas(
            size = 248.dp,
            providerGridPoints = providerGridPoints,
            grid = grid
        )
    }
}

data class AnimatedProviderGridPoint(
    val x: Int,
    val y: Int,
    var state: ProviderPointState,
    val radius: Animatable<Float, AnimationVector1D> = Animatable(0f),
    val color: Animatable<Color, AnimationVector4D> = Animatable(Color.Transparent)
)

@Composable
fun GridCanvas(
    grid: ConnectGrid?,
    providerGridPoints: List<ProviderGridPoint>,
    size: Dp
) {
    val density = LocalDensity.current.density
    val pointSize = (size.value / (grid?.width ?: 0)) * density
    val padding = 1f
    // this is hacky, but we need it for LaunchedEffect to process
    // changes in the providerGridPoints list
    val derivedState = remember(providerGridPoints) {
        derivedStateOf {
            providerGridPoints.map { it.x to it.y to it.state }
        }
    }

    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)
    val animatedPoints = remember { mutableStateListOf<AnimatedProviderGridPoint>() }
    var isInFocus by remember { mutableStateOf(true) }

    val updateAnimatedPoints: suspend () -> Unit = {

        providerGridPoints.forEach { point ->

            val existingPoint = animatedPoints.find { it.x == point.x && it.y == point.y }
            val newState = ProviderPointState.fromString(point.state)

            val targetColor = when (newState) {
                ProviderPointState.IN_EVALUATION -> Yellow
                ProviderPointState.EVALUATION_FAILED -> Red
                ProviderPointState.NOT_ADDED -> TextFaint
                ProviderPointState.ADDED -> Green
                ProviderPointState.REMOVED -> TextFaint
                else -> Color.Transparent
            }

            if (existingPoint == null) {
                // Adding a new point
                val newPoint = AnimatedProviderGridPoint(point.x, point.y, newState!!)
                animatedPoints.add(newPoint)
                newPoint.color.snapTo(targetColor)
                newPoint.radius.animateTo(pointSize / 2 - padding / 2)
            } else if (existingPoint.state != newState) {

                // Update point state and animate accordingly
                if (newState == ProviderPointState.REMOVED) {
                    // Remove point
                    existingPoint.radius.animateTo(0f)
                    existingPoint.color.animateTo(Color.Transparent, animationSpec = tween(durationMillis = 500))
                    animatedPoints.remove(existingPoint)
                } else {
                    // Otherwise update to the new state
                    existingPoint.color.animateTo(targetColor, animationSpec = tween(durationMillis = 500))
                    existingPoint.state = newState!!
                }
            }
        }
    }

    // Update points with animations
    // we have to check isInFocus because the app will not draw new points
    // if this is trigged when the app is in the background
    LaunchedEffect(derivedState.value, isInFocus) {

        if (isInFocus) {
            updateAnimatedPoints()
        }

    }

    // used for detecting when the app goes into the background or foreground
    DisposableEffect(Unit) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isInFocus = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    isInFocus = false
                }

                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }

    }

    Canvas(modifier = Modifier.size(size)) {
        animatedPoints.forEach { point ->
            drawCircle(
                color = point.color.value,
                radius = point.radius.value,
                center = Offset(point.x * pointSize + pointSize / 2, point.y * pointSize + pointSize / 2)
            )
        }
    }
}

@Composable
private fun DisconnectedButtonContent() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = -(12).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "Tap to connect",
            style = TextStyle(
                fontSize = 20.sp,
                lineHeight = 20.sp,
                fontFamily = ppNeueBitBold,
                fontWeight = FontWeight(700),
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        TapToConnectAnimation()
    }
}

@Composable
fun TapToConnectAnimation() {

    val size = remember { Animatable(56f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            launch {
                size.animateTo(
                    targetValue = 82f,
                    animationSpec = tween(durationMillis = 1000)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 1000)
                )
            }
            delay(4000)
            size.snapTo(56f)
            alpha.snapTo(1f)
            delay(100)
        }
    }

    Box(
        modifier = Modifier.size(82.dp),
        contentAlignment = Alignment.Center,
    ) {

        // pulsing animation
        Box(
            modifier = Modifier
                .size(size.value.dp)
                .background(color = BlueMedium.copy(alpha = alpha.value), shape = CircleShape)
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = BlueMedium, shape = CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(color = Black, shape = CircleShape)
                    .align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = BlueMedium, shape = CircleShape)
                        .align(Alignment.Center)
                )
            }
        }
    }
}


@Preview
@Composable
private fun ConnectButtonDisconnectedPreview() {
    URNetworkTheme {
        ConnectButton(
            onClick = {},
            connectStatus = ConnectStatus.DISCONNECTED,
            providerGridPoints = listOf(),
            grid = null
        )
    }
}

@Preview
@Composable
private fun ConnectButtonConnectedPreview() {
    URNetworkTheme {
        ConnectButton(
            onClick = {},
            connectStatus = ConnectStatus.CONNECTED,
            providerGridPoints = listOf(),
            grid = null
        )
    }
}
