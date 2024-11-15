package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector4D
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.Id
import com.bringyour.sdk.Time
import com.bringyour.sdk.ProviderGridPoint
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.network.utils.isTv
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
            .size(if (isTv()) 128.dp else 256.dp)
//            .widthIn(max = 256.dp)
//            .heightIn(max = 256.dp)
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
                .size(if (isTv()) 256.dp else 512.dp)
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
            size = if (isTv()) 124.dp else 248.dp, // slightly smaller than the parent so points don't rub against the mask edges
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
//    var endTime: Time?,
    var done: Boolean = false,
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
    val pointSize = grid?.width?.let { (size.value / it.toFloat()) * localDensityCurrent.density }
        ?: 2.toFloat()
    val padding = 1f
    var currentStatus by remember { mutableStateOf<ConnectStatus?>(null) }
    val animatedPoints = remember { mutableStateMapOf<Id, AnimatedProviderGridPoint>() }

    LaunchedEffect(updatedStatus) {

        // when navigating between screens,
        // if connected, snap to state
        if (currentStatus == null && updatedStatus == ConnectStatus.CONNECTED) {
            shuffleSuccessPoints()

            animatedSuccessPoints.forEach { point ->
                point.center.snapTo(point.targetOffset)
            }
        }

        // update to connected state
        // animate big dots in
        else if (updatedStatus == ConnectStatus.CONNECTED) {
            shuffleSuccessPoints()

            val firstHalf = animatedSuccessPoints.take(animatedSuccessPoints.size / 2)
            val secondHalf = animatedSuccessPoints.drop(animatedSuccessPoints.size / 2)

            // fade out the provider points
            animatedPoints.values.forEach { point ->
                launch {
                    try {
                        point.color.snapTo(getStateColor(point.state))
                        point.color.animateTo(
                            Color.Transparent,
                            animationSpec = tween(durationMillis = 500, delayMillis = 1000)
                        )
                    } catch (e: Exception) {
                        if (currentStatus == ConnectStatus.CONNECTED) {
                            point.color.snapTo(Color.Transparent)
                        }
                    }
                }
            }

            // animate in the first half
            firstHalf.forEach { point ->
                launch {
                    try {
                        point.center.snapTo(point.initialOffset)
                        point.center.animateTo(
                            point.targetOffset,
                            animationSpec = tween(durationMillis = 500, delayMillis = 1000)
                        )
                    } catch (e: Exception) {
                        if (currentStatus == ConnectStatus.CONNECTED) {
                            point.center.snapTo(point.targetOffset)
                        }
                    }
                }
            }

            // animate in the second half
            secondHalf.forEach { point ->
                launch {
                    try {
                        point.center.snapTo(point.initialOffset)
                        point.center.animateTo(
                            point.targetOffset,
                            animationSpec = tween(durationMillis = 500, delayMillis = 2000)
                        )
                    } catch (e: Exception) {
                        if (currentStatus == ConnectStatus.CONNECTED) {
                            point.center.snapTo(point.targetOffset)
                        }
                    }
                }
            }

        }

        // we went from connected to reconnecting
        // animate the big dots out
        else if (updatedStatus == ConnectStatus.CONNECTING) {

            animatedSuccessPoints.forEach { point ->
                launch {
                    try {
                        point.center.animateTo(
                            point.initialOffset,
                            animationSpec = tween(durationMillis = 500)
                        )
                    }  catch (e: Exception) {
                        if (currentStatus == ConnectStatus.CONNECTING) {
                            point.center.snapTo(point.initialOffset)
                        }
                    }
                }
            }

            animatedPoints.values.forEach { point ->
                launch {
                    try {
                        point.color.animateTo(
                            getStateColor(point.state),
                            animationSpec = tween(durationMillis = 500)
                        )
                    } catch (e: Exception) {
                        if (currentStatus == ConnectStatus.CONNECTING) {
                            point.color.snapTo(getStateColor(point.state))
                        }
                    }
                }
            }
        }


        //
        // disconnect
        //
        if (updatedStatus == ConnectStatus.DISCONNECTED) {
            Log.i("ConnectButton", "setting to disconnected")
            // pull out the large dots
            animatedSuccessPoints.forEach { point ->
                launch {
                    try {
                        point.center.animateTo(
                            point.initialOffset,
                            animationSpec = tween(durationMillis = 1000)
                        )
                    } catch (e: Exception) {
                        if (currentStatus == ConnectStatus.DISCONNECTED) {
                            point.center.snapTo(point.initialOffset)
                        }
                    }
                }
            }
        }

        currentStatus = updatedStatus
    }

    // Update points with animations
    // we have to check isInFocus because the app will not draw new points
    // if this is trigged when the app is in the background
    LaunchedEffect(providerGridPoints) {
        val removeAnimatedPoints = mutableListOf<AnimatedProviderGridPoint>()
        for ((clientId, animatedPoint) in animatedPoints) {
            if (!providerGridPoints.containsKey(clientId)) {
                removeAnimatedPoints.add(animatedPoint)
            }
        }
        for (animatedPoint in removeAnimatedPoints) {
            if (!animatedPoint.done) {

                launch {
                    try {
                        animatedPoint.color.animateTo(
                            getStateColor(ProviderPointState.REMOVED),
                            animationSpec = tween(durationMillis = 500)
                        )
                    } catch (e: Exception) {
                        if (animatedPoint.done) {
                            animatedPoint.color.snapTo(getStateColor(ProviderPointState.REMOVED))
                        }
                    }
                }

                launch {
                    // Remove point
                    try {
                        animatedPoint.radius.animateTo(
                            0f,
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 500
                            )
                        )
                    } catch (e: Exception) {
                        // Failure(androidx.compose.runtime.LeftCompositionCancellationException: The coroutine scope left the composition)
                        if (animatedPoint.done) {
                            animatedPoint.radius.snapTo(0f)
                        }
                    } finally {
                        if (animatedPoint.done) {
                            animatedPoints.remove(animatedPoint.clientId)
                        }
                    }

                }
                animatedPoint.done = true
            }
        }

        providerGridPoints.values.forEach { point ->


            var animatedPoint = animatedPoints[point.clientId]
            val newState = ProviderPointState.fromString(point.state)


            if (animatedPoint == null) {
                val done =
                    newState == ProviderPointState.REMOVED || newState == ProviderPointState.EVALUATION_FAILED || newState == ProviderPointState.NOT_ADDED

                if (!done) {
                    // Adding a new point
                    animatedPoint = AnimatedProviderGridPoint(
                        point.clientId,
                        point.x,
                        point.y,
                        newState!!
                    )
                    if (currentStatus != ConnectStatus.CONNECTED) {
                        launch {
                            try {
                                animatedPoint.color.animateTo(
                                    getStateColor(newState),
                                    animationSpec = tween(durationMillis = 500)
                                )
                            } catch (e: Exception) {
                                if (!animatedPoint.done) {
                                    animatedPoint.color.snapTo(getStateColor(animatedPoint.state))
                                }
                            }
                        }
                    }

                    launch {
                        try {
                            animatedPoint.radius.animateTo(
                                pointSize / 2 - padding / 2,
                                animationSpec = tween(durationMillis = 500)
                            )
                        } catch (e: Exception) {
                            if (!animatedPoint.done) {
                                animatedPoint.radius.snapTo(
                                    pointSize / 2 - padding / 2
                                )
                            }
                        }
                    }

                    animatedPoints[point.clientId] = animatedPoint
                }
            } else if (animatedPoint.state != newState) {

                if (currentStatus != ConnectStatus.CONNECTED) {
                    launch {
                        try {
                            animatedPoint.color.animateTo(
                                getStateColor(newState),
                                animationSpec = tween(durationMillis = 500)
                            )
                        } catch (e: Exception) {
                            if (!animatedPoint.done) {
                                animatedPoint.color.snapTo(getStateColor(animatedPoint.state))
                            }
                        }
                    }
                }

                animatedPoint.state = newState!!

                val done =
                    newState == ProviderPointState.REMOVED || newState == ProviderPointState.EVALUATION_FAILED || newState == ProviderPointState.NOT_ADDED
                if (animatedPoint.done != done) {
                    if (done) {
                        launch {
                            // Remove point
                            try {
                                animatedPoint.radius.animateTo(
                                    0f,
                                    animationSpec = tween(
                                        durationMillis = 500,
                                        delayMillis = 500
                                    )
                                )
                            } catch (e: Exception) {
                                if (animatedPoint.done) {
                                    // Failure(androidx.compose.runtime.LeftCompositionCancellationException: The coroutine scope left the composition)
                                    animatedPoint.radius.snapTo(0f)
                                }
                            } finally {
                                if (animatedPoint.done) {
                                    animatedPoints.remove(animatedPoint.clientId)
                                }
                            }
                        }
                    } else {
                        launch {
                            try {
                                animatedPoint.radius.animateTo(
                                    pointSize / 2 - padding / 2,
                                    animationSpec = tween(durationMillis = 500)
                                )
                            } catch (e: Exception) {
                                if (!animatedPoint.done) {
                                    animatedPoint.radius.snapTo(pointSize / 2 - padding / 2)
                                }
                            }
                        }
                    }
                }

                animatedPoint.done = done
            }
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
            stringResource(id = if (isTv()) R.string.connect else R.string.tap_to_connect),
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

    val initialSize = if (isTv()) 28f else 56f
    val targetSize = if (isTv()) 41f else 82f
    val size = remember { Animatable(initialSize) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            launch {
                size.animateTo(
                    targetValue = targetSize,
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
            size.snapTo(initialSize)
            alpha.snapTo(1f)
            delay(100)
        }
    }

    Box(
        modifier = Modifier.size(if (isTv()) 41.dp else 82.dp),
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
                .size(if (isTv()) 28.dp else 56.dp)
                .background(color = BlueMedium, shape = CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv()) 26.dp else 52.dp)
                    .background(color = Black, shape = CircleShape)
                    .align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isTv()) 24.dp else 48.dp)
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
