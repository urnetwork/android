package com.bringyour.network.ui.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun ConnectStatusIndicator(
    text: String,
    status: ConnectStatus
) {

    val indicatorId = when(status) {
        ConnectStatus.CONNECTED -> R.drawable.circle_indicator_green
        ConnectStatus.CONNECTING -> R.drawable.circle_indicator_yellow
        ConnectStatus.DESTINATION_SET -> R.drawable.circle_indicator_yellow
        ConnectStatus.CANCELING -> R.drawable.circle_indicator_yellow
        ConnectStatus.DISCONNECTED -> R.drawable.circle_indicator_blue
    }

    val indicatorDescription = when(status) {
        ConnectStatus.CONNECTED -> "Connected"
        ConnectStatus.CONNECTING -> "Connecting"
        ConnectStatus.DESTINATION_SET -> "Connecting"
        ConnectStatus.CANCELING -> "Canceling"
        ConnectStatus.DISCONNECTED -> "Disconnected"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(id = indicatorId),
            contentDescription = indicatorDescription,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(text)

    }
}

@Preview
@Composable
fun ConnectStatusIndicatorDisconnected() {
    URNetworkTheme {
        ConnectStatusIndicator(
            text = "username is ready to connect",
            status = ConnectStatus.DISCONNECTED
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorConnecting() {
    URNetworkTheme {
        ConnectStatusIndicator(
            text = "Connecting to providers...",
            status = ConnectStatus.CONNECTING
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorConnected() {
    URNetworkTheme {
        ConnectStatusIndicator(
            text = "Connecting to 32 providers",
            status = ConnectStatus.CONNECTED
        )
    }
}