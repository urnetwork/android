package com.bringyour.network.screenshots

import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.TopLevelScaffoldRoutes
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.connect.rememberConnectDrawerState
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The connect screen, disconnected, at each window size. The screen picks its
 * arrangement from the window (connectArrangementFor): the phone drawer at
 * compact width, the statistics pane below the connect pane at medium width,
 * and the two side by side at expanded width and in a window too short to
 * stack. The panes split by sideBySideSplit and stackedSplit (ConnectPanes).
 */
class ConnectScreenshotTest : ScreenshotTest() {

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun compactWidth() = capture("connect-compact")

    @Test
    @Config(qualifiers = WindowSizes.MEDIUM)
    fun mediumWidth() = capture("connect-medium")

    @Test
    @Config(qualifiers = WindowSizes.EXPANDED)
    fun expandedWidth() = capture("connect-expanded")

    @Test
    @Config(qualifiers = WindowSizes.COMPACT_HEIGHT)
    fun compactHeight() = capture("connect-compact-height")

    private fun capture(name: String) {
        compose.setContent {
            AppWindow(viewModels, TopLevelScaffoldRoutes.CONNECT_CONTAINER) {
                ConnectScreen(
                    connectViewModel = viewModels.connect,
                    overlayViewModel = viewModels.overlay,
                    locationsViewModel = viewModels.locations,
                    navController = rememberNavController(),
                    subscriptionBalanceViewModel = viewModels.subscriptionBalance,
                    planViewModel = viewModels.plan,
                    bundleStore = null,
                    meanReliabilityWeight = 0.0,
                    totalReferrals = 0,
                    isPro = false,
                    connectActionsSheetState = rememberConnectDrawerState(),
                    accountViewModel = viewModels.account,
                    throughputViewModel = viewModels.throughput,
                    blockActionsViewModel = viewModels.blockActions,
                    dnsSettingsViewModel = viewModels.dnsSettings,
                    blockerViewModel = viewModels.blocker,
                    networkPeersViewModel = viewModels.networkPeers,
                )
            }
        }
        compose.captureWindow(name)
    }
}
