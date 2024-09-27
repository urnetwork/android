package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun NoPayoutsFound() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(id = R.string.no_payouts_found),
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview
@Composable
private fun NoPayoutsFoundPreview() {
    URNetworkTheme {
        NoPayoutsFound()
    }
}