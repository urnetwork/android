package com.bringyour.network.ui.connect

import android.util.Log
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Id
import com.bringyour.client.ProviderGridPoint
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PromptReviewViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    connectViewModel: ConnectViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    overlayViewModel: OverlayViewModel,
    locationsViewModel: LocationsListViewModel,
    navController: NavController,
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val connectStatus by connectViewModel.connectStatus.collectAsState()

    val networkUser by accountViewModel.networkUser.collectAsState()

    if (isTv()) {
        ConnectTV(
            navController = navController,
            connect = connectViewModel.connect,
            selectedLocation = connectViewModel.selectedLocation,
            connectStatus = connectStatus,
            networkName = networkUser?.networkName,
            disconnect = connectViewModel.disconnect,
            providerGridPoints = connectViewModel.providerGridPoints,
            windowCurrentSize = connectViewModel.windowCurrentSize,
            grid = connectViewModel.grid,
            loginMode = accountViewModel.loginMode,
            animatedSuccessPoints = connectViewModel.shuffledSuccessPoints,
            shuffleSuccessPoints = connectViewModel.shuffleSuccessPoints,
            getStateColor = connectViewModel.getStateColor,
            checkTriggerPromptReview = promptReviewViewModel.checkTriggerPromptReview,
            launchOverlay = overlayViewModel.launch,
            getLocationColor = locationsViewModel.getLocationColor
        )
    } else {
        ConnectMobileAndTablet(
            connectStatus = connectStatus,
            networkName = networkUser?.networkName,
            loginMode = accountViewModel.loginMode,
            checkTriggerPromptReview = promptReviewViewModel.checkTriggerPromptReview,
            launchOverlay = overlayViewModel.launch,
            locationsViewModel = locationsViewModel,
            connectViewModel = connectViewModel
        )
    }
}

@Composable
private fun ConnectTV(
    navController: NavController,
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
    getLocationColor: (String) -> Color
) {

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                ConnectMainContent(
                    connectStatus = connectStatus,
                    selectedLocation = selectedLocation,
                    networkName = networkName,
                    connect = connect,
                    disconnect = disconnect,
                    providerGridPoints = providerGridPoints,
                    windowCurrentSize = windowCurrentSize,
                    grid = grid,
                    loginMode = loginMode,
                    animatedSuccessPoints = animatedSuccessPoints,
                    shuffleSuccessPoints = shuffleSuccessPoints,
                    getStateColor = getStateColor,
                    checkTriggerPromptReview = checkTriggerPromptReview,
                    launchOverlay = launchOverlay
                )

            }

            Column {
                HorizontalDivider(
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth(),
                    color = MainBorderBase
                )

                if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
                    ProviderRow(
                        location = "Best available provider",
                        onClick = {
                            navController.navigate(Route.BrowseLocations)
                        },
                        color = Red400
                    )
                } else {

                    val key =
                        if (selectedLocation.countryCode.isNullOrEmpty()) selectedLocation.connectLocationId.toString()
                        else selectedLocation.countryCode

                    ProviderRow(
                        location = selectedLocation.name,
                        providerCount = selectedLocation.providerCount,
                        onClick = {
                            navController.navigate(Route.BrowseLocations)
                        },
                        color = getLocationColor(key)
                    )
                }

            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectMobileAndTablet(
    connectStatus: ConnectStatus,
    networkName: String?,
    loginMode: LoginMode,
    checkTriggerPromptReview: () -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
    locationsViewModel: LocationsListViewModel,
    connectViewModel: ConnectViewModel
) {
    val scaffoldState = rememberBottomSheetScaffoldState()

    ProvidersBottomSheet(
        scaffoldState,
        connect = connectViewModel.connect,
        selectedLocation = connectViewModel.selectedLocation,
        locationsViewModel = locationsViewModel
    ) { _ ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(16.dp),
        ) {
            ConnectMainContent(
                connectStatus = connectStatus,
                selectedLocation = connectViewModel.selectedLocation,
                networkName = networkName,
                connect = connectViewModel.connect,
                disconnect = connectViewModel.disconnect,
                providerGridPoints = connectViewModel.providerGridPoints,
                windowCurrentSize = connectViewModel.windowCurrentSize,
                grid = connectViewModel.grid,
                loginMode = loginMode,
                animatedSuccessPoints = connectViewModel.shuffledSuccessPoints,
                shuffleSuccessPoints = connectViewModel.shuffleSuccessPoints,
                getStateColor = connectViewModel.getStateColor,
                checkTriggerPromptReview = checkTriggerPromptReview,
                launchOverlay = launchOverlay
            )
        }
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

        disconnectBtnVisible = when(connectStatus) {
            ConnectStatus.CONNECTED -> true
            ConnectStatus.CONNECTING -> true
            ConnectStatus.DESTINATION_SET -> true
            ConnectStatus.DISCONNECTED -> false
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

//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Black)
//            .padding(16.dp),
//    ) {
        Column(
            // modifier = Modifier.padding(bottom = 112.dp)
        ) {
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
                networkName = networkName,
                guestMode = loginMode == LoginMode.Guest
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

            // Spacer(modifier = Modifier.height(112.dp))

        }
    // }
}

@Preview
@Composable
private fun ConnectMainContentPreview() {
    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }

    URNetworkTheme {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
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
    }
}

