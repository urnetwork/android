package com.bringyour.network.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import java.text.NumberFormat
import java.util.Locale

@Composable()
fun ProviderRow(
    location: String,
    providerCount: Int,
    imageResourceId: Int? = null,
    onClick: (Int) -> Unit,
    isSelected: Boolean = false,
    color: Color
) {

    val formatter = NumberFormat.getNumberInstance(Locale.US)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(1)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row() {
            CircleImage(
                size = 40.dp,
                imageResourceId = imageResourceId,
                backgroundColor = color,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row {
                    Text(location)
                }
                Row {
                    Text("${formatter.format(providerCount)} Providers", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Keyboard Arrow Right",
                tint = BlueMedium
            )
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
}

@Preview
@Composable
fun ProviderRowPreview() {
    URNetworkTheme {
        ProviderRow(
            location = "Switzerland",
            providerCount = 1520,
            onClick = {},
            color = Red400
        )
    }
}

@Preview
@Composable
fun ProviderRowSelectedPreview() {
    URNetworkTheme {
        ProviderRow(
            location = "Switzerland",
            providerCount = 1520,
            onClick = {},
            isSelected = true,
            color = Red400
        )
    }
}