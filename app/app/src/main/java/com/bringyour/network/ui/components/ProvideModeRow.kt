package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/**
 * The live provide tier indicator: a dot, with an outer ring for the public
 * tier. One drawing shared by the settings picker header and the provide-mode
 * row, so the stats and earnings screens render it exactly like settings.
 */
@Composable
fun ProvideModeIndicator(dotColor: Color, ringColor: Color?) {
    // fixed slot so the label doesn't shift when the ring appears
    Box(
        modifier = Modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (ringColor != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
    }
}

/**
 * "Provide mode · <current mode>": the settings picker's indicator and label
 * with the current mode beside it. The whole row opens the settings where the
 * mode is changed.
 */
@Composable
fun ProvideModeRow(
    mode: ProvideControlMode,
    dotColor: Color,
    ringColor: Color?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProvideModeIndicator(dotColor = dotColor, ringColor = ringColor)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            stringResource(id = R.string.provide_mode),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            stringResource(id = ProvideControlMode.toStringResourceId(mode)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(16.dp)
        )
    }
}
