package com.bringyour.network.ui.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.Id
import com.bringyour.sdk.ProviderGridPoint
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PromptReviewViewModel
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.isTv

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

    Scaffold { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
//                .padding(horizontal = 16.dp)
//                .padding(bottom = 16.dp)
        ) {

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
//            checkTriggerPromptReview = promptReviewViewModel.checkTriggerPromptReview,
                    launchOverlay = overlayViewModel.launch,
                    locationsViewModel = locationsViewModel,
                    displayReconnectTunnel = connectViewModel.displayReconnectTunnel
                )
            } else {

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
//                checkTriggerPromptReview = checkTriggerPromptReview,
                    launchOverlay = overlayViewModel.launch,
                    locationsViewModel = locationsViewModel,
                    navController = navController,
                    displayReconnectTunnel = connectViewModel.displayReconnectTunnel
                )
            }

        }
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
//    checkTriggerPromptReview: () -> Boolean,
    launchOverlay: (OverlayMode) -> Unit,
    locationsViewModel: LocationsListViewModel,
    displayReconnectTunnel: Boolean
) {

    Column(
        modifier = Modifier
            .fillMaxSize(),
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
//                    checkTriggerPromptReview = checkTriggerPromptReview,
            launchOverlay = launchOverlay,
            locationsViewModel = locationsViewModel,
            navController = navController,
            displayReconnectTunnel = displayReconnectTunnel
        )

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
                    color = locationsViewModel.getLocationColor(key)
                )
            }

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
    launchOverlay: (OverlayMode) -> Unit,
    locationsViewModel: LocationsListViewModel,
    navController: NavController,
    displayReconnectTunnel: Boolean
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
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

    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountSwitcher(
                loginMode,
                networkName,
                launchOverlay = launchOverlay
            )
        }

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {

            Column {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center

                ) {
                    ConnectButton(
                        onClick = {
                            if (connectStatus == ConnectStatus.DISCONNECTED) {
                                connect(selectedLocation)
//                            checkTriggerPromptReview()
                            }
                        },
                        updatedStatus = connectStatus,
                        providerGridPoints = providerGridPoints,
                        grid = grid,
                        animatedSuccessPoints = animatedSuccessPoints,
                        shuffleSuccessPoints = shuffleSuccessPoints,
                        getStateColor = getStateColor,
                        displayReconnectTunnel = displayReconnectTunnel
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                ConnectStatusIndicator(
                    status = connectStatus,
                    windowCurrentSize = windowCurrentSize,
                    networkName = networkName,
                    guestMode = loginMode == LoginMode.Guest,
                    displayReconnectTunnel = displayReconnectTunnel
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        /**
                         * Disconnect Button
                         */
                        AnimatedVisibility(
                            visible = disconnectBtnVisible && !displayReconnectTunnel,
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
                                    stringResource(id = R.string.disconnect),
                                    style = buttonTextStyle,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        /**
                         * Reconnect tunnel button
                         */

                        AnimatedVisibility(
                            visible = displayReconnectTunnel,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            URButton(
                                onClick = {
                                    application?.startVpnService()
                                },
                                style = ButtonStyle.OUTLINE
                            ) { buttonTextStyle ->
                                Text(
                                    stringResource(id = R.string.reconnect),
                                    style = buttonTextStyle,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                }

            }

        }

        OpenProviderListButton(
            selectedLocation = selectedLocation,
            getLocationColor = locationsViewModel.getLocationColor,
            onClick = {
                locationsViewModel.refreshLocations()
                navController.navigate(Route.BrowseLocations)
            }
        )

    }

}

@Composable
fun OpenProviderListButton(
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    onClick: () -> Unit
) {

    val text = if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
        "Best available provider"
    } else {
        selectedLocation.name
    }

    val iconTint = if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
        Red400
    } else {

        val key =
            if (selectedLocation.countryCode.isNullOrEmpty()) selectedLocation.connectLocationId.toString()
            else selectedLocation.countryCode

        getLocationColor(key)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MainTintedBackgroundBase, // primary color
            contentColor = Color.White  // text/icon color
        ),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 4.dp)
        ) {

            Icon(
                painter = painterResource(id = R.drawable.main_nav_globe),
                contentDescription = stringResource(id = R.string.add_wallet),
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column() {

                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (selectedLocation != null && selectedLocation.providerCount > 0) {
                    Text(
                        "${selectedLocation.providerCount} providers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

//@Preview
//@Composable
//private fun ConnectMainContentPreview() {
//    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }
//
//    URNetworkTheme {
//        Scaffold { innerPadding ->
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(innerPadding)
//                    .padding(16.dp),
//            ) {
//                ConnectMainContent(
//                    connectStatus = ConnectStatus.DISCONNECTED,
//                    selectedLocation = null,
//                    networkName = "my_network",
//                    connect = {},
//                    disconnect = {},
//                    grid = null,
//                    providerGridPoints = mapOf(),
//                    windowCurrentSize = 16,
//                    loginMode = LoginMode.Authenticated,
//                    animatedSuccessPoints = listOf(),
//                    shuffleSuccessPoints = {},
//                    getStateColor = mockGetStateColor,
////                    checkTriggerPromptReview = {false},
//                    launchOverlay = {},
//                    locationsViewModel = LocationsListViewModel()
//                    // getLocationColor = { Color.Red }
//                )
//            }
//        }
//    }
//}

