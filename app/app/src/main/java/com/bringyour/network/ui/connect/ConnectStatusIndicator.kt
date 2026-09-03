package com.bringyour.network.ui.connect

import com.bringyour.network.ui.theme.TextMuted
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.AnimatedEllipsis
import com.bringyour.network.ui.shared.models.ConnectStatus
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
    isPollingSubscriptionBalance: Boolean,
    // when set, the provider status label opens the provider locations detail:
    // "Connected to N providers" and "Connecting to providers" alike (the detail
    // shows whatever providers are known so far while connecting)
    onShowProviderLocations: (() -> Unit)? = null
) {

    val text = when {
        isPollingSubscriptionBalance -> "Processing subscription balance..."
        contractStatus?.insufficientBalance == true && currentPlan != Plan.Supporter -> "Insufficient balance"
        displayReconnectTunnel -> stringResource(id = R.string.reconnect_tunnel_status_indicator)
        status == ConnectStatus.CONNECTED -> pluralStringResource(
            id = R.plurals.connected_provider_count,
            count = windowCurrentSize,
            windowCurrentSize,
        )
        status == ConnectStatus.CONNECTING || status == ConnectStatus.DESTINATION_SET ->
            stringResource(id = R.string.connecting_status_indicator)
        status == ConnectStatus.DISCONNECTED -> when {
            guestMode -> stringResource(id = R.string.ready_to_connect)
            networkName != null -> stringResource(id = R.string.network_name_ready_to_connect, networkName)
            else -> stringResource(id = R.string.ready_to_connect)
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

    // the provider status is the affordance: tapping it opens the per-provider
    // locations detail, while connecting as well as once connected. Any other
    // status text is not interactive.
    val providerStatus = status == ConnectStatus.CONNECTED ||
            status == ConnectStatus.CONNECTING ||
            status == ConnectStatus.DESTINATION_SET
    val showProviderLocations = onShowProviderLocations?.takeIf {
        providerStatus &&
                !displayReconnectTunnel &&
                !isPollingSubscriptionBalance &&
                !(contractStatus?.insufficientBalance == true && currentPlan != Plan.Supporter)
    }

    AnimatedVisibility(
        visible = text.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("acceptance.connect.status")
                .then(
                    if (showProviderLocations != null) {
                        Modifier.clickable { showProviderLocations() }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(id = indicatorId),
                contentDescription = indicatorDescription,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text)

            if (status == ConnectStatus.CONNECTING) {
                AnimatedEllipsis()
            }

            // the caret says the provider count opens the provider details
            if (showProviderLocations != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
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
