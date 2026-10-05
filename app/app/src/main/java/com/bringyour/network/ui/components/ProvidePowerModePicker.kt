package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ProvidePowerMode

/**
 * When this device provides while it runs on battery: keep providing, pause
 * in Battery Saver (the default), or pause until it is charging.
 */
@Composable
fun ProvidePowerModePicker(
    providePowerMode: ProvidePowerMode,
    setProvidePowerMode: (ProvidePowerMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {

        Text(
            stringResource(id = R.string.provide_power_mode),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        ProvidePowerMode.entries.forEach { mode ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (mode == providePowerMode),
                    onClick = { setProvidePowerMode(mode) }
                )
                Text(
                    stringResource(id = ProvidePowerMode.toStringResourceId(mode)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
