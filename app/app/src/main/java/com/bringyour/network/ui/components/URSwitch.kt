package com.bringyour.network.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Blue400
import com.bringyour.network.ui.theme.URNetworkTheme

// credit to https://stackoverflow.com/a/70567213/3703043
@Composable
fun URSwitch(
    checked: Boolean,
    toggle: () -> Unit
) {
    val width = 36.dp
    val height = 20.dp
    val gapBetweenThumbAndTrackEdge = 4.dp
    val strokeWidth: Dp = 2.dp

    val thumbRadius = (height / 2) - gapBetweenThumbAndTrackEdge

    val animatePosition = animateFloatAsState(
        targetValue = if (checked)
            with(LocalDensity.current) { (width - thumbRadius - gapBetweenThumbAndTrackEdge).toPx() }
        else
            with(LocalDensity.current) { (thumbRadius + gapBetweenThumbAndTrackEdge).toPx() },
        label = ""
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (checked) Blue400 else Color.Transparent, label = ""
    )

    Canvas(
        modifier = Modifier
            .size(width = width, height = height)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        toggle()
                    }
                )
            }
    ) {

        drawRoundRect(
            color = backgroundColor,
            cornerRadius = CornerRadius(x = 10.dp.toPx(), y = 10.dp.toPx())
        )

        // Track
        drawRoundRect(
            color = Blue400,
            cornerRadius = CornerRadius(x = 10.dp.toPx(), y = 10.dp.toPx()),
            style = Stroke(width = strokeWidth.toPx()),
        )

        // Thumb
        drawCircle(
            color = if (checked) Black else Blue400,
            radius = thumbRadius.toPx(),
            center = Offset(
                x = animatePosition.value,
                y = size.height / 2
            )
        )
    }
}

@Preview
@Composable
fun URSwitchCheckedPreview() {
    URNetworkTheme {
        URSwitch(
            checked = true,
            toggle = {}
        )
    }
}

@Preview
@Composable
fun URSwitchUncheckedPreview() {
    URNetworkTheme {
        URSwitch(
            checked = false,
            toggle = {}
        )
    }
}