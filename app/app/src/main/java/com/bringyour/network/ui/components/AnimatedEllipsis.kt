package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AnimatedEllipsis() {
    var dotCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(400)
                dotCount = (dotCount + 1) % 4
            }
        }
    }

    Box(modifier = Modifier.width(20.dp)) {
        // Invisible full ellipsis for layout stability
        Text("...", modifier = Modifier.alpha(0f))
        // Animated dots
        Text(".".repeat(dotCount))
    }
}
