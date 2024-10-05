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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Id
import com.bringyour.client.ProviderGridPoint
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PromptReviewViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.URNetworkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectViewModel: ConnectViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    overlayViewModel: OverlayViewModel,
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val networkUser by accountViewModel.networkUser.collectAsState()

    ProvidersBottomSheet(
        scaffoldState,
        connect = connectViewModel.connect,
        selectedLocation = connectViewModel.selectedLocation
    ) { _ ->
        ConnectMainContent(
            connectStatus = connectStatus,
            selectedLocation = connectViewModel.selectedLocation,
            networkName = networkUser?.networkName,
            connect = connectViewModel.connect,
            disconnect = connectViewModel.disconnect,
            providerGridPoints = connectViewModel.providerGridPoints,
            windowCurrentSize = connectViewModel.windowCurrentSize,
            grid = connectViewModel.grid,
            loginMode = accountViewModel.loginMode,
            animatedSuccessPoints = connectViewModel.shuffledSuccessPoints,
            shuffleSuccessPoints = connectViewModel.shuffleSuccessPoints,
            getStateColor = connectViewModel.getStateColor,
            checkTriggerPromptReview = promptReviewViewModel.checkTriggerPromptReview,
            launchOverlay = overlayViewModel.launch
        )
    }
}

@Composable
fun ConnectMainContent(
    connectStatus: ConnectStatus,
    selectedLocation: ConnectLocation?,
    networkName: String?,
    grid: ConnectGrid?,
    providerGridPoints: Map<Id, ProviderGridPoint>,
    windowCurrentSize: Int,
    connect: (ConnectLocation?) -> Unit,
    disconnect: () -> Unit?,
    loginMode: LoginMode,
    animatedSuccessPoints: List<AnimatedSuccessPoint>,
    shuffleSuccessPoints: () -> Unit,
    getStateColor: (ProviderPointState?) -> Color,
    checkTriggerPromptReview: () -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
) {

    var currentStatus by remember { mutableStateOf<ConnectStatus?>(null) }
    var disconnectBtnVisible by remember { mutableStateOf(false) }

    LaunchedEffect(connectStatus) {

        currentStatus = connectStatus

        when(connectStatus) {
            ConnectStatus.CONNECTED -> disconnectBtnVisible = true
            ConnectStatus.CONNECTING -> disconnectBtnVisible = true
            ConnectStatus.DESTINATION_SET -> disconnectBtnVisible = true
            ConnectStatus.DISCONNECTED -> disconnectBtnVisible = false

        }

        if (connectStatus == ConnectStatus.CONNECTED) {
                disconnectBtnVisible = true
        }

        else if (connectStatus == ConnectStatus.CONNECTING) {
            disconnectBtnVisible = true
        }

        else if (connectStatus == ConnectStatus.DESTINATION_SET) {
            disconnectBtnVisible = true
        }

        else if (connectStatus == ConnectStatus.DISCONNECTED) {
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
                AccountSwitcher(
                    loginMode,
                    networkName,
                    launchOverlay = launchOverlay
                )
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
                            checkTriggerPromptReview()
                        }
                    },
                    updatedStatus = connectStatus,
                    providerGridPoints = providerGridPoints,
                    grid = grid,
                    animatedSuccessPoints = animatedSuccessPoints,
                    shuffleSuccessPoints = shuffleSuccessPoints,
                    getStateColor = getStateColor
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
                            checkTriggerPromptReview()
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
            }
        }
    }
}

@Preview
@Composable
private fun ConnectMainContentPreview() {
    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }

    URNetworkTheme {
        ConnectMainContent(
            connectStatus = ConnectStatus.DISCONNECTED,
            selectedLocation = null,
            networkName = "my_network",
            connect = {},
            disconnect = {},
            grid = null,
            providerGridPoints = mapOf(),
            windowCurrentSize = 16,
            loginMode = LoginMode.Authenticated,
            animatedSuccessPoints = listOf(),
            shuffleSuccessPoints = {},
            getStateColor = mockGetStateColor,
            checkTriggerPromptReview = {},
            launchOverlay = {}
        )
    }
}
