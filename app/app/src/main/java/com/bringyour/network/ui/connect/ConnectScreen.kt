package com.bringyour.network.ui.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ProviderGridPoint
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectViewModel: ConnectViewModel,
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    ProvidersBottomSheet(
        scaffoldState,
        connect = connectViewModel.connect,
        selectedLocation = connectViewModel.selectedLocation
    ) { _ ->
        ConnectMainContent(
            connectStatus = connectStatus,
            selectedLocation = connectViewModel.selectedLocation,
            networkName = accountViewModel.networkName,
            // networkName = networkName,
            connect = connectViewModel.connect,
            disconnect = connectViewModel.disconnect,
            cancelConnection = connectViewModel.cancelConnection,
            providerGridPoints = connectViewModel.providerGridPoints,
            windowCurrentSize = connectViewModel.windowCurrentSize,
            grid = connectViewModel.grid,
            loginMode = accountViewModel.loginMode
        )
    }
}

@Composable
fun ConnectMainContent(
    connectStatus: ConnectStatus,
    selectedLocation: ConnectLocation?,
    networkName: String?,
    grid: ConnectGrid?,
    providerGridPoints: List<ProviderGridPoint>,
    windowCurrentSize: Int,
    connect: (ConnectLocation?) -> Unit,
    disconnect: () -> Unit?,
    cancelConnection: () -> Unit?,
    loginMode: LoginMode,
) {

    var currentStatus by remember { mutableStateOf<ConnectStatus?>(null) }
    var cancelBtnVisible by remember { mutableStateOf(false) }
    var disconnectBtnVisible by remember { mutableStateOf(false) }

    LaunchedEffect(connectStatus) {

        currentStatus = connectStatus

        if (connectStatus == ConnectStatus.CONNECTED) {
            launch {
                cancelBtnVisible = false

                delay(2000)
                disconnectBtnVisible = true
            }
        }

        if (connectStatus == ConnectStatus.CONNECTING || connectStatus == ConnectStatus.DESTINATION_SET) {

            launch {
                delay(5000)
                // ensure still connecting
                if (currentStatus == ConnectStatus.CONNECTING || currentStatus == ConnectStatus.DESTINATION_SET) {
                    cancelBtnVisible = true
                }
            }
        }

        if (connectStatus == ConnectStatus.DISCONNECTED) {
            cancelBtnVisible = false
            disconnectBtnVisible = false
        }

    }

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
                AccountSwitcher(loginMode)
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
                    updatedStatus = connectStatus,
                    providerGridPoints = providerGridPoints,
                    grid = grid,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            ConnectStatusIndicator(
                status = connectStatus,
                windowCurrentSize = windowCurrentSize,
                networkName = networkName
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {

                AnimatedVisibility(
                    visible = disconnectBtnVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {

                    URButton(
                        onClick = {
                            disconnect()
                        },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Text(
                            "Disconnect",
                            style = buttonTextStyle,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                }


                AnimatedVisibility(
                    visible = cancelBtnVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    URButton(
                        onClick = {
                            // todo - handle canceling
                            cancelConnection()
                        },
                        style = ButtonStyle.OUTLINE,
                    ) { buttonTextStyle ->
                        Text(
                            "Cancel",
                            style = buttonTextStyle,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
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
            connectStatus = ConnectStatus.DISCONNECTED,
            selectedLocation = null,
            networkName = "my_network",
            connect = {},
            disconnect = {},
            cancelConnection = {},
            grid = null,
            providerGridPoints = listOf(),
            windowCurrentSize = 16,
            loginMode = LoginMode.Authenticated
        )
    }
}
