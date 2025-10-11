package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R

@Composable
fun ProvideCellPicker(
    allowProvideCell: Boolean,
    toggleProvideCell: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.allow_providing_cell),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        URSwitch(
            checked = allowProvideCell,
            toggle = {
                toggleProvideCell()
            },
        )
    }
}