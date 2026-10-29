package com.bringyour.network.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.bringyour.network.DeviceManager
import com.bringyour.network.JwtManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ProvidePauseState
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.TopLevelScaffoldRoutes
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.LocalWindowLayout
import com.bringyour.network.ui.components.NavigationLayout
import com.bringyour.network.ui.components.navigationLayoutFor
import com.bringyour.network.ui.components.rememberWindowLayout
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.connect.LocationsListViewModel
import com.bringyour.network.ui.leaderboard.LeaderboardViewModel
import com.bringyour.network.ui.leaderboard.PointsLeaderboardViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.PostQuantumIdentityViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.stats.BlockActionsViewModel
import com.bringyour.network.ui.stats.BlockerViewModel
import com.bringyour.network.ui.stats.DnsSettingsViewModel
import com.bringyour.network.ui.stats.ExtenderStatusViewModel
import com.bringyour.network.ui.stats.NetworkPeersViewModel
import com.bringyour.network.ui.stats.ThroughputViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.URNetworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import kotlin.math.roundToInt

/**
 * The screens' view models as the app builds them, on a device manager that
 * has no device: a signed-in app before its first data arrives, the same on
 * every run. The sdk's classes are inert here (ScreenshotTestConfig), so no
 * native call happens and every sdk read comes back empty.
 *
 * It is also the screens' view model store: a screen that asks hiltViewModel()
 * for one of these gets it from here, since the store is not a Hilt owner.
 */
internal class ScreenshotViewModels(context: Context) : ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val jwtManager = JwtManager()
    private val networkSpaceManagerProvider = NetworkSpaceManagerProvider()
    private val deviceManager = DeviceManager(jwtManager, networkSpaceManagerProvider)

    val connect = keep(ConnectViewModel(deviceManager, context))
    val overlay = keep(OverlayViewModel())
    val locations = keep(LocationsListViewModel(deviceManager))
    val subscriptionBalance = keep(SubscriptionBalanceViewModel(deviceManager, jwtManager, context))
    val plan = keep(PlanViewModel(context))
    val account = keep(AccountViewModel(deviceManager, networkSpaceManagerProvider))
    val throughput = keep(ThroughputViewModel(deviceManager, ProvidePauseState(context)))
    val blockActions = keep(BlockActionsViewModel(deviceManager))
    val dnsSettings = keep(DnsSettingsViewModel(deviceManager))
    val blocker = keep(BlockerViewModel(deviceManager))
    val networkPeers = keep(NetworkPeersViewModel(deviceManager))
    // the drawer's extender panel asks hiltViewModel() for its own (ConnectStatsSections)
    val extenderStatus = keep(ExtenderStatusViewModel(deviceManager))
    val postQuantumIdentity = keep(PostQuantumIdentityViewModel(deviceManager))
    val leaderboard = keep(LeaderboardViewModel(deviceManager, networkSpaceManagerProvider))
    val pointsLeaderboard = keep(PointsLeaderboardViewModel(deviceManager))

    private fun <T : ViewModel> keep(viewModel: T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
        }
        return ViewModelProvider(viewModelStore, factory)[viewModel.javaClass]
    }
}

/**
 * A screen in the app's window as MainNavHost frames it: the window's layout
 * read as MainNavHost reads it (rememberWindowLayout), the theme, and the
 * navigation suite, the bar or the rail by navigationLayoutFor. The frame
 * mirrors MainNavHostContent's NavigationSuiteScaffold: keep them in step.
 */
@Composable
internal fun AppWindow(
    viewModels: ScreenshotViewModels,
    selected: TopLevelScaffoldRoutes,
    screen: @Composable () -> Unit,
) {
    val windowLayout = rememberWindowLayout()
    CompositionLocalProvider(
        LocalWindowLayout provides windowLayout,
        LocalViewModelStoreOwner provides viewModels,
    ) {
        URNetworkTheme {
            val navigationLayout = navigationLayoutFor(windowLayout.widthDp, windowLayout.heightDp, windowLayout.tabletop)
            val navItemColors = NavigationSuiteDefaults.itemColors(
                navigationBarItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = Pink
                ),
                navigationRailItemColors = NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = Pink
                ),
                navigationDrawerItemColors = NavigationDrawerItemDefaults.colors()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
            ) {
                NavigationSuiteScaffold(
                    containerColor = Black,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    navigationSuiteColors = NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = Black,
                        navigationBarContentColor = Black,
                        navigationBarContainerColor = Black,
                    ),
                    layoutType = when (navigationLayout) {
                        NavigationLayout.Rail -> NavigationSuiteType.NavigationRail
                        NavigationLayout.BottomBar -> NavigationSuiteType.NavigationBar
                    },
                    navigationSuiteItems = {
                        TopLevelScaffoldRoutes.entries.forEach { route ->
                            item(
                                icon = {
                                    if (route.route == Route.Leaderboard) {
                                        Icon(
                                            imageVector = Icons.Filled.StackedLineChart,
                                            contentDescription = stringResource(id = R.string.leaderboard),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                id = if (route == selected) route.selectedIcon else route.unselectedIcon
                                            ),
                                            contentDescription = stringResource(id = route.descriptionId),
                                        )
                                    }
                                },
                                selected = route == selected,
                                onClick = {},
                                colors = navItemColors,
                            )
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(
                            bottom = if (navigationLayout == NavigationLayout.BottomBar) 1.dp else 0.dp
                        )
                    ) {
                        if (navigationLayout == NavigationLayout.Rail) {
                            VerticalDivider(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight(),
                                color = MainBorderBase
                            )
                        }
                        screen()
                    }
                }
            }
        }
    }
}

/** Where the baselines live, relative to the module (Gradle's test working directory). */
private const val BASELINE_DIRECTORY = "src/testPlayDebug/screenshots"

/**
 * Captures the window once the screen has settled, as the baseline `name`.png:
 * recorded, compared or verified by the run's mode (app/build.gradle,
 * -Pscreenshots). First it checks that the test's qualifiers took, so a
 * recording never keeps a screen drawn in the wrong window.
 */
internal fun ComposeContentTestRule.captureWindow(name: String) {
    waitForIdle()
    val root = onRoot()
    val configuration = ApplicationProvider.getApplicationContext<Context>().resources.configuration
    val size = root.fetchSemanticsNode().size
    assertEquals("window width in dp", configuration.screenWidthDp, (size.width / density.density).roundToInt())
    assertEquals("window height in dp", configuration.screenHeightDp, (size.height / density.density).roundToInt())
    root.captureRoboImage(filePath = "$BASELINE_DIRECTORY/$name.png")
}
