package com.bringyour.network.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Blue300
import com.bringyour.network.ui.theme.Blue400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme

// credit to https://stackoverflow.com/a/70567213/3703043
/**
 * The app's switch. It is a toggleable with the switch role: a tap or a
 * click toggles it, and so do Enter and Space while it has keyboard focus,
 * and assistive tech reads it as a switch and its state. The keyboard's focus
 * shows as a ring around it, and a pointer over it lightens the thumb and
 * the track (adaptive app quality, Drawable_Focus and Hover_States).
 *
 * It takes a 48 dp square in the layout, the track drawn in its middle, as
 * Material's own switch does (Touch_Targets). Compose would grow its touch
 * area to 48 dp anyway, but a neighbor's grown area could cut into it: two
 * switch rows 16 dp apart left the upper switch 36 dp tall to the
 * Accessibility Scanner. The reserved square keeps every neighbor out.
 */
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    // focus and a pointer over it light the switch alike
    val isHighlighted = enabled && (isFocused || isHovered)

    val thumbColor = when {
        checked -> Black
        !checked && !enabled -> TextFaint
        !checked && isHighlighted -> Blue300
        else -> Blue400
    }

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .focusRing(shape = RoundedCornerShape(50), outset = 4.dp)
            .size(width = width, height = height)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                // the switch draws its own focus and hover, below
                indication = null,
                onValueChange = { toggle() },
            )
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
        ) {

            val focusBackgroundColor = if (isHighlighted) backgroundColor.copy(alpha = 0.5f) else backgroundColor

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
