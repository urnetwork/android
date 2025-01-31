package com.bringyour.network.ui.components

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Blue300
import com.bringyour.network.ui.theme.Blue400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme

// credit to https://stackoverflow.com/a/70567213/3703043
@Composable
fun URSwitch(
    checked: Boolean,
    enabled: Boolean = true,
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

    val targetBackgroundColor = when {
        checked && enabled -> Blue400
        checked && !enabled -> TextFaint
        else -> Color.Transparent
    }

    val backgroundColor by animateColorAsState(targetValue = targetBackgroundColor, label = "")
    var isFocused by remember { mutableStateOf(false) }

    val thumbColor = when {
        checked -> Black
        !checked && !enabled -> TextFaint
        !checked && isFocused -> Blue300
        else -> Blue400
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .onFocusChanged { focusState ->
                Log.i("switch", "onFocusChanged: $focusState")
                isFocused = focusState.isFocused
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        toggle()
                    }
                )
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                    toggle()
                    true
                } else {
                    false
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
        ) {

            val focusBackgroundColor = if (isFocused) backgroundColor.copy(alpha = 0.5f) else backgroundColor

            drawRoundRect(
                color = focusBackgroundColor,
                cornerRadius = CornerRadius(x = 10.dp.toPx(), y = 10.dp.toPx())
            )

            // Track
            drawRoundRect(
                color = if (enabled) Blue400 else TextFaint,
                cornerRadius = CornerRadius(x = 10.dp.toPx(), y = 10.dp.toPx()),
                style = Stroke(width = strokeWidth.toPx()),
            )

            // Thumb
            drawCircle(
                color = thumbColor,
                radius = thumbRadius.toPx(),
                center = Offset(
                    x = animatePosition.value,
                    y = size.height / 2
                )
            )
        }
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

@Preview
@Composable
fun URSwitchUncheckedDisabledPreview() {
    URNetworkTheme {
        URSwitch(
            checked = false,
            toggle = {},
            enabled = false
        )
    }
}

@Preview
@Composable
fun URSwitchCheckedDisabledPreview() {
    URNetworkTheme {
        URSwitch(
            checked = true,
            toggle = {},
            enabled = false
        )
    }
}