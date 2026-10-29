package com.bringyour.network.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.TopLevelScaffoldRoutes
import com.bringyour.network.ui.account.AccountDetailPlaceholder
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.components.LocalWindowLayout
import com.bringyour.network.ui.components.sideBySideSplit
import com.bringyour.network.ui.components.sidePaneWidth
import com.bringyour.network.ui.theme.MainBorderBase
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The Account tab at each window size: one column at compact width, and from
 * medium width the list-detail layout, the account list in the side pane
 * (sideBySideSplit) beside a detail pane with no screen chosen yet.
 */
class AccountScreenshotTest : ScreenshotTest() {

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun compactWidth() = capture("account-compact")

    @Test
    @Config(qualifiers = WindowSizes.MEDIUM)
    fun mediumWidth() = capture("account-medium")

    @Test
    @Config(qualifiers = WindowSizes.EXPANDED)
    fun expandedWidth() = capture("account-expanded")

    @Test
    @Config(qualifiers = WindowSizes.COMPACT_HEIGHT)
    fun compactHeight() = capture("account-compact-height")

    private fun capture(name: String) {
        compose.setContent {
            AppWindow(viewModels, TopLevelScaffoldRoutes.ACCOUNT_CONTAINER) {
                AccountListDetail(viewModels)
            }
        }
        compose.captureWindow(name)
    }
}

/**
 * The Account destination as MainNavContent lays it out around the NavHost:
 * from medium width the account list at the start, as wide as the side pane,
 * a hairline, and the destination beside it, which is AccountDetailPlaceholder
 * until a row is chosen; at compact width the account screen alone. This
 * mirrors MainNavContent's pane row: keep the two in step.
 */
@Composable
private fun AccountListDetail(viewModels: ScreenshotViewModels) {
    val navController = rememberNavController()
    val windowLayout = LocalWindowLayout.current
    val listDetail = windowLayout.isMediumWidthOrWider
    val accountScreen: @Composable () -> Unit = {
        AccountScreen(
            navController,
            viewModels.account,
            totalAccountPoints = 0.0,
            accountPointsLoaded = true,
            planViewModel = viewModels.plan,
            subscriptionBalanceViewModel = viewModels.subscriptionBalance,
            overlayViewModel = viewModels.overlay,
            totalReferrals = 0,
            meanReliabilityWeight = 0.0,
            isPro = false,
            navigate = {},
            chosenRoute = null,
            isPresentingRedeemTransferBalanceSheet = false,
            setPresentingRedeemTransferBalanceSheet = {},
            postQuantumIdentityViewModel = viewModels.postQuantumIdentity,
        )
    }

    var paneRowInWindow by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val listOnLeft = LocalLayoutDirection.current == LayoutDirection.Ltr
    val paneRowWidth = paneRowInWindow.width / density.density
    val listWidth = sidePaneWidth(
        split = sideBySideSplit(
            containerStart = paneRowInWindow.left / density.density,
            width = paneRowWidth,
            fold = windowLayout.hinge,
            sidePaneOnLeft = listOnLeft,
        ),
        width = paneRowWidth,
        sidePaneOnLeft = listOnLeft,
    ).dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { paneRowInWindow = it.boundsInWindow() }
    ) {
        if (listDetail) {
            Box(modifier = Modifier.width(listWidth.coerceAtLeast(0.dp))) {
                accountScreen()
            }
            VerticalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MainBorderBase
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (listDetail) {
                AccountDetailPlaceholder()
            } else {
                accountScreen()
            }
        }
    }
}
