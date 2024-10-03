package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow

@Composable
fun FetchLocationsError(
    onRefresh: () -> Unit,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onRefresh()
            }
            .background(MainTintedBackgroundBase, shape = RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = stringResource(id = R.string.check_internet),
            tint = Yellow
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Row {
                Text(
                    stringResource(id = R.string.check_internet),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Row {
                Text(
                    stringResource(id = R.string.tap_to_refetch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

@Preview
@Composable
private fun FetchLocationsErrorPreview() {
    URNetworkTheme {
        FetchLocationsError(
            onRefresh = {}
        )
    }
}