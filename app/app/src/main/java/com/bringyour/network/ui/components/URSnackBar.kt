package com.bringyour.network.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.BlueDark
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextDanger
import kotlinx.coroutines.delay

enum class SnackBarType {
    SUCCESS, ERROR
}

@Composable
fun URSnackBar(
    type: SnackBarType,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 100f

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(10 * 1000)
            onDismiss()
        }
    }

    Box(modifier = Modifier.padding(16.dp)) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlueDark, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                cumulativeDrag += dragAmount
                                if (cumulativeDrag < -swipeThreshold) {
                                    onDismiss()
                                }
                            },
                            onDragEnd = {
                                cumulativeDrag = 0f
                            }
                        )
                    }
                    .padding(16.dp, 16.dp, 24.dp, 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (type == SnackBarType.SUCCESS)
                            Icons.Filled.CheckCircle
                        else Icons.Filled.Warning,
                        contentDescription = if (type == SnackBarType.SUCCESS) "Success" else "Error",
                        modifier = Modifier.size(24.dp),
                        tint = if (type == SnackBarType.SUCCESS) Green else TextDanger
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    content()

                }
            }
        }
    }
}