package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.client.ConnectLocation
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectViewModel: ConnectViewModel,
    networkName: String?
) {

    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    ProvidersBottomSheet(
        scaffoldState,
        connect = connectViewModel.connect,
        selectedLocation = connectViewModel.selectedLocation
    ) { _ ->
        ConnectMainContent(
            connectedProviderCount = connectViewModel.connectedProviderCount,
            connectStatus = connectStatus,
            selectedLocation = connectViewModel.selectedLocation,
            networkName = networkName,
            connect = connectViewModel.connect,
            disconnect = connectViewModel.disconnect,
            cancelConnection = connectViewModel.cancelConnection
        )
    }
}

@Composable
fun ConnectMainContent(
    connectedProviderCount: Int,
    connectStatus: ConnectStatus,
    selectedLocation: ConnectLocation?,
    networkName: String?,
    connect: (ConnectLocation?) -> Unit,
    disconnect: () -> Unit?,
    cancelConnection: () -> Unit?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AccountSwitcher(loginMode = LoginMode.Authenticated)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center

            ) {
                ConnectButton(
                    onClick = {
                        if (connectStatus == ConnectStatus.DISCONNECTED) {
                            connect(selectedLocation)
                        }
                    },
                    connectStatus = connectStatus
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            ConnectStatusIndicator(
                text = when(connectStatus) {
                    ConnectStatus.CONNECTED -> "Connected to $connectedProviderCount providers"
                    ConnectStatus.CONNECTING -> "Connecting to providers..."
                    ConnectStatus.DESTINATION_SET -> "Connecting to providers..."
                    ConnectStatus.CANCELING -> "Canceling connection..."
                    ConnectStatus.DISCONNECTED -> if (networkName != null) "$networkName is ready to connect"
                    else "ready to connect"
                },
                status = connectStatus
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (connectStatus == ConnectStatus.CONNECTED) {

                    URButton(
                        onClick = {
                            disconnect()
                        },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Text("Disconnect", style = buttonTextStyle)
                    }

                } else if (connectStatus == ConnectStatus.CONNECTING || connectStatus == ConnectStatus.CANCELING) {

                    // todo - we should only show cancel after connecting is over ~2 seconds
                    URButton(
                        onClick = {
                            cancelConnection()
                        },
                        style = ButtonStyle.OUTLINE,
                        enabled = connectStatus == ConnectStatus.CONNECTING
                    ) { buttonTextStyle ->
                        Text("Cancel", style = buttonTextStyle)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ConnectMainContentPreview() {
    URNetworkTheme {
        ConnectMainContent(
            connectedProviderCount = 100,
            connectStatus = ConnectStatus.DISCONNECTED,
            selectedLocation = null,
            networkName = "my_network",
            connect = {},
            disconnect = {},
            cancelConnection = {}
        )
    }
}
