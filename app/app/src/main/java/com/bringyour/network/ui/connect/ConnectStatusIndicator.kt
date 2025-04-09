package com.bringyour.network.ui.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.sdk.ContractStatus

@Composable
fun ConnectStatusIndicator(
    networkName: String?,
    guestMode: Boolean,
    status: ConnectStatus,
    windowCurrentSize: Int,
    displayReconnectTunnel: Boolean,
    contractStatus: ContractStatus?,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean
) {

    val text = when {
        isPollingSubscriptionBalance -> "Processing subscription balance..."
        contractStatus?.insufficientBalance == true && currentPlan != Plan.Supporter -> "Insufficient balance"
        displayReconnectTunnel -> stringResource(id = R.string.reconnect_tunnel_status_indicator)
        status == ConnectStatus.CONNECTED -> stringResource(id = R.string.connected_provider_count, windowCurrentSize)
        status == ConnectStatus.CONNECTING || status == ConnectStatus.DESTINATION_SET ->
            stringResource(id = R.string.connecting_status_indicator)
        status == ConnectStatus.DISCONNECTED -> when {
            guestMode -> stringResource(id = R.string.ready_to_connect)
            networkName != null -> stringResource(id = R.string.network_name_ready_to_connect, networkName)
            else -> ""
        }
        else -> ""
    }

    val indicatorId = when {
        displayReconnectTunnel || isPollingSubscriptionBalance || (contractStatus?.insufficientBalance == true && currentPlan != Plan.Supporter) -> R.drawable.circle_indicator_yellow
        status == ConnectStatus.CONNECTED -> R.drawable.circle_indicator_green
        status == ConnectStatus.CONNECTING || status == ConnectStatus.DESTINATION_SET -> R.drawable.circle_indicator_yellow
        status == ConnectStatus.DISCONNECTED -> R.drawable.circle_indicator_blue
        else -> R.drawable.circle_indicator_blue
    }

    val indicatorDescription = if (displayReconnectTunnel) "Reconnect" else when(status) {
        ConnectStatus.CONNECTED -> "Connected"
        ConnectStatus.CONNECTING -> "Connecting"
        ConnectStatus.DESTINATION_SET -> "Connecting"
        ConnectStatus.DISCONNECTED -> "Disconnected"
    }

    AnimatedVisibility(
        visible = text.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {

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
}

@Preview
@Composable
fun ConnectStatusIndicatorDisconnected() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.DISCONNECTED,
            windowCurrentSize = 0,
            networkName = "my_network",
            guestMode = false,
            displayReconnectTunnel = false,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorConnecting() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.CONNECTING,
            windowCurrentSize = 12,
            networkName = "my_network",
            guestMode = false,
            displayReconnectTunnel = false,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorConnected() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.CONNECTED,
            windowCurrentSize = 32,
            networkName = "my_network",
            guestMode = false,
            displayReconnectTunnel = false,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorGuestMode() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.DISCONNECTED,
            windowCurrentSize = 32,
            networkName = "guest1244567",
            guestMode = true,
            displayReconnectTunnel = false,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorReconnectTunnel() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.CONNECTED,
            windowCurrentSize = 32,
            networkName = "guest1244567",
            guestMode = false,
            displayReconnectTunnel = true,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false
        )
    }
}

@Preview
@Composable
fun ConnectStatusIndicatorPollingSubscriptionBalance() {
    URNetworkTheme {
        ConnectStatusIndicator(
            status = ConnectStatus.CONNECTED,
            windowCurrentSize = 32,
            networkName = "guest1244567",
            guestMode = false,
            displayReconnectTunnel = true,
            contractStatus = null,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = true
        )
    }
}