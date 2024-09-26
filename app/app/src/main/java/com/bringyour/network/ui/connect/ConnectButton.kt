package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Id
import com.bringyour.client.Time
import com.bringyour.client.ProviderGridPoint
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.network.ui.theme.ppNeueBitBold
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ConnectButton(
    onClick: () -> Unit,
    grid: ConnectGrid?,
    providerGridPoints: Map<Id, ProviderGridPoint>,
    updatedStatus: ConnectStatus,
    animatedSuccessPoints: List<AnimatedSuccessPoint>,
    shuffleSuccessPoints: () -> Unit,
    getStateColor: (ProviderPointState?) -> Color
) {

    var currentStatus by remember { mutableStateOf<ConnectStatus?>(null) }
    var disconnectedVisible by remember { mutableStateOf(true) }


    LaunchedEffect(updatedStatus) {

        if (updatedStatus == ConnectStatus.DISCONNECTED) {
            disconnectedVisible = true

        }

        if (updatedStatus != ConnectStatus.DISCONNECTED) {
            disconnectedVisible = false
        }

        currentStatus = updatedStatus

    }

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


            AnimatedVisibility(
                visible = disconnectedVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                DisconnectedButtonContent()
            }


            ConnectingButtonContent(
                providerGridPoints = providerGridPoints,
                grid = grid,
                status = updatedStatus,
                animatedSuccessPoints = animatedSuccessPoints,
                shuffleSuccessPoints = shuffleSuccessPoints,
                getStateColor = getStateColor
            )

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
    providerGridPoints: Map<Id, ProviderGridPoint>,
    status: ConnectStatus,
    animatedSuccessPoints: List<AnimatedSuccessPoint>,
    shuffleSuccessPoints: () -> Unit,
    getStateColor: (ProviderPointState?) -> Color
) {

    var globeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(status) {

        if (status == ConnectStatus.DESTINATION_SET || status == ConnectStatus.CONNECTING) {
            delay(500)
            globeVisible = true
        } else {
            delay(500)
            globeVisible = false
        }

    }


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        AnimatedVisibility(
            visible = globeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Image(
                painter = painterResource(id = R.drawable.connector_globe),
                contentDescription = "Connecting"
            )
        }

        GridCanvas(
            size = 248.dp, // slightly smaller than the parent so points don't rub against the mask edges
            providerGridPoints = providerGridPoints,
            grid = grid,
            updatedStatus = status,
            animatedSuccessPoints = animatedSuccessPoints,
            shuffleSuccessPoints = shuffleSuccessPoints,
            getStateColor = getStateColor
        )
    }
}

data class AnimatedProviderGridPoint(
    val clientId: Id,
    val x: Int,
    val y: Int,
    var state: ProviderPointState,
    var endTime: Time?,
    val radius: Animatable<Float, AnimationVector1D> = Animatable(0f),
    val color: Animatable<Color, AnimationVector4D> = Animatable(Color.Transparent)
)

@Composable
fun GridCanvas(
    grid: ConnectGrid?,
    providerGridPoints: Map<Id, ProviderGridPoint>,
    size: Dp,
    updatedStatus: ConnectStatus,
    animatedSuccessPoints: List<AnimatedSuccessPoint>,
    shuffleSuccessPoints: () -> Unit,
    getStateColor: (ProviderPointState?) -> Color
) {
    val localDensityCurrent = LocalDensity.current
    val pointSize = (size.value / (grid?.width ?: 0)) * localDensityCurrent.density
    val padding = 1f
    var currentStatus by remember { mutableStateOf<ConnectStatus?>(null) }

    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)
    val animatedPoints = remember { mutableStateMapOf<Id, AnimatedProviderGridPoint>() }

    var isInFocus by remember { mutableStateOf(true) }

    val animateProviderPoint: suspend (AnimatedProviderGridPoint) -> Unit = { point ->

        val targetColor = getStateColor(point.state)

        point.endTime?.let { endTime ->
            point.color.animateTo(
                getStateColor(point.state)
            )

            // Remove point
            point.radius.animateTo(0f)
            //                            existingPoint.color.animateTo(
            //                                Color.Transparent,
            //                                animationSpec = tween(durationMillis = endTime.millisUntil())
            //                            )
        } ?: run {
            // Otherwise update to the new state


            point.color.animateTo(
                targetColor,
                animationSpec = tween(durationMillis = 500)
            )
            // existingPoint.radius.animateTo(pointSize / 2 - padding / 2)

        }

    }

    val updateAnimatedPoints: suspend () -> Unit = {

        coroutineScope {

            val removeAnimatedClientIds = mutableListOf<Id>()
            for (clientId in animatedPoints.keys) {
                if (!providerGridPoints.containsKey(clientId)) {
                    removeAnimatedClientIds.add(clientId)
                }
            }
            for (clientId in removeAnimatedClientIds) {
                animatedPoints.remove(clientId)
            }

            providerGridPoints.values.forEach { point ->

                launch {
                    val existingPoint = animatedPoints[point.clientId]
                    val newState = ProviderPointState.fromString(point.state)

                    val targetColor = getStateColor(newState)

                    if (existingPoint == null) {
                        // Adding a new point
                        val newPoint = AnimatedProviderGridPoint(
                            point.clientId,
                            point.x,
                            point.y,
                            newState!!,
                            point.endTime
                        )
                        animatedPoints[point.clientId] = newPoint

                        if (updatedStatus == ConnectStatus.CONNECTING || updatedStatus == ConnectStatus.DESTINATION_SET) {
                            newPoint.color.snapTo(targetColor)
                            newPoint.radius.animateTo(pointSize / 2 - padding / 2)
                        }

                    } else if (existingPoint.state != newState || existingPoint.endTime != point.endTime) {

                        existingPoint.state = newState!!
                        existingPoint.endTime = point.endTime


                        // if the state is updated to CONNECTED
                        // points should be faded, so we don't need to animate them
                        // only animate while connecting
                        if (updatedStatus == ConnectStatus.CONNECTING || updatedStatus == ConnectStatus.DESTINATION_SET) {

                            animateProviderPoint(existingPoint)

                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(updatedStatus) {

        // when navigating between screens,
        // if connected, snap to state
        if (currentStatus == null && updatedStatus == ConnectStatus.CONNECTED) {

            animatedSuccessPoints.forEach { point ->
                launch {
                    point.center.snapTo(point.targetOffset)
                }
            }
        }

       // update to connected state
       // animate big dots in
        else if (updatedStatus == ConnectStatus.CONNECTED) {

//            animatedSuccessPoints.clear()
//           shuffledPoints.addAll(connectedBigPoints.shuffled())
            shuffleSuccessPoints()

           val firstHalf = animatedSuccessPoints.take(animatedSuccessPoints.size / 2)
           val secondHalf = animatedSuccessPoints.drop(animatedSuccessPoints.size / 2)

           delay(250)

            // turn points that have changed state from IN_EVALUATION -> ADDED green
            // before fading out the points and rolling in the
            // big success points
            animatedPoints.values.forEach { point ->
                launch {
                    animateProviderPoint(point)
                }
            }

            delay(500)

            // fade out the provider points
           animatedPoints.values.forEach { point ->
               launch {
                   point.color.animateTo(Color.Transparent, animationSpec = tween(durationMillis = 1000))
               }
           }

           delay(1000)

           // animate in the first half
           firstHalf.forEach { point ->
               launch {
                   point.center.snapTo(point.initialOffset)
                   point.center.animateTo(point.targetOffset)
               }
           }

           delay(1000)

           // animate in the second half
           secondHalf.forEach { point ->
               launch {
                   point.center.snapTo(point.initialOffset)
                   point.center.animateTo(point.targetOffset)
               }
           }

        }

       // we went from connected to reconnecting
       // animate the big dots out
       else if (updatedStatus == ConnectStatus.CONNECTING) {

           animatedSuccessPoints.forEach { point ->
               launch {
                   point.center.animateTo(point.initialOffset)
               }
           }

           delay(100)
//
           animatedPoints.values.forEach { point ->
               launch {
                   point.color.animateTo(getStateColor(point.state), animationSpec = tween(durationMillis = 1000))
               }
           }

        }


//        else if (updatedStatus == ConnectStatus.DESTINATION_SET) {
//            Log.i("ConnectButton", "setting to destination set")
//
//            // remove all provider grid points
//            animatedPoints.values.forEach { point ->
//                launch {
//                    point.radius.snapTo(0f)
//                }
//            }
//
//            delay(500)
//        }


        //
        // disconnect
        //
        if (updatedStatus == ConnectStatus.DISCONNECTED) {
            Log.i("ConnectButton", "setting to disconnected")

            // remove all provider grid points
//            animatedPoints.values.forEach { point ->
//                launch {
//                    point.radius.snapTo(0f)
//                }
//            }

            // pull out the large dots
            animatedSuccessPoints.forEach{ point ->
                launch {
                    point.center.animateTo(point.initialOffset)
                }
            }

            delay(500)
        }

        currentStatus = updatedStatus
    }

    // Update points with animations
    // we have to check isInFocus because the app will not draw new points
    // if this is trigged when the app is in the background
    LaunchedEffect(providerGridPoints, isInFocus) {

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
        // our provider grid
        animatedPoints.values.forEach { point ->
            drawCircle(
                color = point.color.value,
                radius = point.radius.value,
                center = Offset(
                    point.x * pointSize + pointSize / 2,
                    point.y * pointSize + pointSize / 2
                )
            )
        }

        // the overlaying big dots on connection success
        animatedSuccessPoints.forEach { point ->
            drawCircle(
                color = point.color,
                radius = point.radius,
                center = point.center.value
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
    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }

    URNetworkTheme {
        ConnectButton(
            onClick = {},
            updatedStatus = ConnectStatus.DISCONNECTED,
            providerGridPoints = mapOf(),
            grid = null,
            animatedSuccessPoints = listOf(),
            shuffleSuccessPoints = {},
            getStateColor = mockGetStateColor
        )
    }
}

@Preview
@Composable
private fun ConnectButtonConnectedPreview() {
    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }

    URNetworkTheme {
        ConnectButton(
            onClick = {},
            updatedStatus = ConnectStatus.CONNECTED,
            providerGridPoints = mapOf(),
            grid = null,
            animatedSuccessPoints = listOf(),
            shuffleSuccessPoints = {},
            getStateColor = mockGetStateColor
        )
    }
}
