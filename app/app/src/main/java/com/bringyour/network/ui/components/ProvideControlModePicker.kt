package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.bringyour.network.ui.theme.TextMuted
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ProvideControlMode

@Composable
fun ProvideControlModePicker(
    provideControlMode: ProvideControlMode,
    setProvideControlMode: (ProvideControlMode) -> Unit,
    provideIndicatorColor: Color,
    // outer ring = public provide tier (apple parity); null = no ring
    provideIndicatorRingColor: Color? = null,
    // one short line under each option saying what it does (onboarding)
    showDescriptions: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // fixed slot so the label doesn't shift when the ring appears
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (provideIndicatorRingColor != null) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(width = 1.5.dp, color = provideIndicatorRingColor, shape = CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = provideIndicatorColor, shape = CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                stringResource(id = R.string.provide_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        ProvideControlMode.entries.forEach { mode ->
            val descriptionId = if (showDescriptions) {
                ProvideControlMode.toDescriptionResourceId(mode)
            } else {
                null
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (mode == provideControlMode),
                    onClick = { setProvideControlMode(mode) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (descriptionId != null) 6.dp else 0.dp)
                ) {
                    Text(
                        stringResource(id = ProvideControlMode.toStringResourceId(mode)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (descriptionId != null) {
                        Text(
                            stringResource(id = descriptionId),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}