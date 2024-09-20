package com.bringyour.network.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
    providerCount: Int? = null,
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
        Row(
            modifier = Modifier
                .weight(1f),
        ) {
            CircleImage(
                size = 40.dp,
                imageResourceId = imageResourceId,
                backgroundColor = color,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column (
                modifier = Modifier.height(40.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row {
                    Text(
                        location,
                        style = MaterialTheme.typography.bodyLarge,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .weight(1f)
                    )
                }
                if (providerCount != null && providerCount > 0) {
                    Row {
                        Text(
                            "${formatter.format(providerCount)} Providers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
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
private fun ProviderRowPreview() {
    URNetworkTheme {
        ProviderRow(
            location = "Switzerland",
            providerCount = 1520,
            onClick = {},
            color = Red400,
            isSelected = false
        )
    }
}

@Preview
@Composable
private fun ProviderRowSelectedPreview() {
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

@Preview
@Composable
private fun ProviderRowLongTextSelectedPreview() {
    URNetworkTheme {
        ProviderRow(
            location = "Strong Privacy Laws and Internet Freedom lorem ipsum",
            providerCount = 1520,
            onClick = {},
            isSelected = true,
            color = Red400
        )
    }
}

@Preview
@Composable
private fun ProviderRowLongTextNoProvidersPreview() {
    URNetworkTheme {
        ProviderRow(
            location = "Best available provider",
            providerCount = 0,
            onClick = {},
            isSelected = true,
            color = Red400
        )
    }
}