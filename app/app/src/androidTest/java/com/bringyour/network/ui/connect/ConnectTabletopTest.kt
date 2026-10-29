package com.bringyour.network.ui.connect

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.device.DeviceInteraction.Companion.setFlatMode
import androidx.test.espresso.device.DeviceInteraction.Companion.setTabletopMode
import androidx.test.espresso.device.EspressoDevice.Companion.onDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bringyour.network.DeviceManager
import com.bringyour.network.JwtManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ProvidePauseState
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.ConnectArrangement
import com.bringyour.network.ui.components.LocalWindowLayout
import com.bringyour.network.ui.components.WindowLayout
import com.bringyour.network.ui.components.connectArrangementFor
import com.bringyour.network.ui.components.rememberWindowLayout
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.stats.BlockActionsViewModel
import com.bringyour.network.ui.stats.BlockerViewModel
import com.bringyour.network.ui.stats.DnsSettingsViewModel
import com.bringyour.network.ui.stats.ExtenderStatusViewModel
import com.bringyour.network.ui.stats.NetworkPeersViewModel
import com.bringyour.network.ui.stats.ThroughputViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * The connect screen on a foldable half opened like a laptop (apple/DUO.md
 * items 7 and 17): Espresso Device puts the emulator in tabletop, and the
 * screen, reading the real fold as MainNavHost does (rememberWindowLayout),
 * stacks its panes at the fold (connectArrangementFor, stackedSplit). The
 * connect graphic and its status sit on the raised half, above the fold; the
 * controls, Connect among them, sit on the flat half below it.
 *
 * It runs on the Gradle Managed Device pixelFoldApi36 (app/build.gradle), with
 * emulator control on, from android/test-main.sh's tabletop job. A device
 * without a hinge skips it; on one with a hinge, a window that never reports
 * the fold fails it. (Espresso Device's RequiresDeviceMode filter would skip
 * it everywhere: it reads the display_features setting, which the Pixel Fold
 * emulator leaves unset.)
 */
@RunWith(AndroidJUnit4::class)
class ConnectTabletopTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var viewModels: ConnectViewModels? = null
    private var folded = false

    @After
    fun unfold() {
        viewModels?.let { models -> compose.runOnUiThread { models.viewModelStore.clear() } }
        if (folded) {
            onDevice().setFlatMode()
        }
    }

    @Test
    fun tabletopPutsTheCanvasAboveTheFoldAndTheControlsBelowIt() {
        assumeTrue("the device has no hinge to fold", deviceHasAHinge())

        // folded before the screen is drawn: on an emulator the fold can
        // rotate the window, which starts the activity again
        onDevice().setTabletopMode()
        folded = true
        compose.waitForIdle()

        // view models observe the process lifecycle, which only the main thread may do
        val context = compose.activity.applicationContext
        val models = compose.runOnUiThread { ConnectViewModels(context) }
        viewModels = models
        // the window as the screen last read it, from the main thread
        val windowLayout = AtomicReference<WindowLayout?>(null)
        compose.setContent {
            val layout = rememberWindowLayout()
            windowLayout.set(layout)
            CompositionLocalProvider(
                LocalWindowLayout provides layout,
                LocalViewModelStoreOwner provides models,
            ) {
                URNetworkTheme {
                    ConnectScreen(
                        connectViewModel = models.connect,
                        overlayViewModel = models.overlay,
                        locationsViewModel = models.locations,
                        navController = rememberNavController(),
                        subscriptionBalanceViewModel = models.subscriptionBalance,
                        planViewModel = models.plan,
                        bundleStore = null,
                        meanReliabilityWeight = 0.0,
                        totalReferrals = 0,
                        isPro = false,
                        connectActionsSheetState = rememberConnectDrawerState(),
                        accountViewModel = models.account,
                        throughputViewModel = models.throughput,
                        blockActionsViewModel = models.blockActions,
                        dnsSettingsViewModel = models.dnsSettings,
                        blockerViewModel = models.blocker,
                        networkPeersViewModel = models.networkPeers,
                    )
                }
            }
        }

        // the window reports the fold once the posture has settled
        compose.waitUntil(timeoutMillis = 15_000) {
            windowLayout.get()?.let { it.tabletop && it.fold?.vertical == false } == true
        }
        compose.waitForIdle()
        val layout = checkNotNull(windowLayout.get())
        val fold = checkNotNull(layout.fold)
        assertEquals(
            "a tabletop window stacks connect at the fold",
            ConnectArrangement.Stacked,
            connectArrangementFor(layout.widthDp, layout.heightDp, fold),
        )
        val density = compose.density.density
        val foldTop = fold.start * density
        val foldBottom = fold.end * density

        val status = compose.onNodeWithTag("acceptance.connect.status").fetchSemanticsNode().boundsInWindow
        val connect = compose.onNodeWithTag("acceptance.connect").fetchSemanticsNode().boundsInWindow
        val graphic = connectGraphicBounds(below = status)

        assertTrue("the connect graphic $graphic ends above the fold at $foldTop", graphic.bottom <= foldTop)
        assertTrue("the connect status $status ends above the fold at $foldTop", status.bottom <= foldTop)
        assertTrue("Connect $connect starts below the fold at $foldBottom", connect.top >= foldBottom)
    }

    /** Whether the device has a hinge, as book-style and flip foldables do. */
    private fun deviceHasAHinge(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val context = compose.activity
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) ||
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
    }

    /**
     * The connect graphic: the screen's largest square tap target, centered
     * over its status. It has no name while disconnected, since its picture
     * and the status beside it say what it does.
     */
    private fun connectGraphicBounds(below: Rect): Rect {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val squares = root.descendants()
            .filter { SemanticsActions.OnClick in it.config }
            .map { it.boundsInWindow }
            .filter { it.width >= 48f && kotlin.math.abs(it.width - it.height) <= 2f }
            .filter { it.left <= below.center.x && below.center.x <= it.right && it.bottom <= below.top + 1f }
        return checkNotNull(squares.maxByOrNull { it.width * it.height }) {
            "no connect graphic over the status at $below"
        }
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> =
        children.flatMap { listOf(it) + it.descendants() }
}

/**
 * The connect screen's view models, built as Hilt builds them, on a device
 * manager of their own that has no device: the screen shows what a signed-in
 * app shows before its first data arrives. It is also their store, so a
 * hiltViewModel() inside the screen finds them here rather than in the
 * activity, which is not a Hilt entry point.
 */
private class ConnectViewModels(context: Context) : ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val jwtManager = JwtManager()
    private val networkSpaceManagerProvider = NetworkSpaceManagerProvider()
    private val deviceManager = DeviceManager(jwtManager, networkSpaceManagerProvider)

    val connect = keep(ConnectViewModel(deviceManager, context))
    val overlay = keep(OverlayViewModel())
    val locations = keep(LocationsListViewModel(deviceManager))
    val subscriptionBalance = keep(SubscriptionBalanceViewModel(deviceManager, jwtManager, context))
    val plan = keep(flavorPlanViewModel(context, networkSpaceManagerProvider))
    val account = keep(AccountViewModel(deviceManager, networkSpaceManagerProvider))
    val throughput = keep(ThroughputViewModel(deviceManager, ProvidePauseState(context)))
    val blockActions = keep(BlockActionsViewModel(deviceManager))
    val dnsSettings = keep(DnsSettingsViewModel(deviceManager))
    val blocker = keep(BlockerViewModel(deviceManager))
    val networkPeers = keep(NetworkPeersViewModel(deviceManager))
    // the drawer's extender panel asks hiltViewModel() for its own (ConnectStatsSections)
    val extenderStatus = keep(ExtenderStatusViewModel(deviceManager))

    private fun <T : ViewModel> keep(viewModel: T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
        }
        return ViewModelProvider(viewModelStore, factory)[viewModel.javaClass]
    }
}

/**
 * The flavor's PlanViewModel from its injected constructor, whose parameters
 * differ by flavor (Play's takes the context; the others add the network
 * space provider). The connect screen takes one but never reads it.
 */
private fun flavorPlanViewModel(context: Context, provider: NetworkSpaceManagerProvider): PlanViewModel {
    val constructor = PlanViewModel::class.java.constructors.single { it.isAnnotationPresent(Inject::class.java) }
    val arguments = constructor.parameterTypes.map { type ->
        when {
            Context::class.java.isAssignableFrom(type) -> context.applicationContext
            type == NetworkSpaceManagerProvider::class.java -> provider
            else -> error("PlanViewModel takes an unexpected ${type.name}")
        }
    }
    return constructor.newInstance(*arguments.toTypedArray()) as PlanViewModel
}
