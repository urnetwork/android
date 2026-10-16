package com.bringyour.network.ui.connect

import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.bringyour.network.DeviceManager
import com.bringyour.network.JwtManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ContractStatus
import com.bringyour.sdk.Sub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The connect screen's view model on a fake sdk, with the start connect gate
 * held until the test decides, as a balance fetch is held until the server
 * answers (owner report 2026-10-16). It pins how the view model wires its
 * start connect: the user's disconnect and a device change end a start
 * waiting on its balance, and an admitted start shows the controller's own
 * status at once.
 *
 * The fake controller reports a status change only when the test fires its
 * listener, as the sdk's listener reports on the sdk's own thread, later.
 * The main dispatcher runs each launch at once, as the main thread does for
 * work posted from it. Nothing waits on time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** The start connect gate, held until [decide]. */
    private class HeldGate {
        var checks = 0
        private var pending: ((Boolean) -> Unit)? = null

        fun check(onResult: (Boolean) -> Unit) {
            checks += 1
            pending = onResult
        }

        fun decide(blocked: Boolean) {
            val onResult = pending
            pending = null
            onResult?.invoke(blocked)
        }
    }

    private class FakeSub(private val onClose: () -> Unit = {}) : Sub {
        override fun close() = onClose()
    }

    /** The sdk's connect view controller: what it was asked, and its status. */
    private class FakeController : ConnectController {
        val commands = mutableListOf<String>()
        var started = false
        var closed = false
        private var statusListener: (() -> Unit)? = null

        override var connectionStatus: String? = "DISCONNECTED"
        override val grid: ConnectGrid? = null
        override val selectedLocation: ConnectLocation? = null

        override fun addGridListener(listener: () -> Unit): Sub = FakeSub()
        override fun addSelectedLocationListener(listener: () -> Unit): Sub = FakeSub()
        override fun addConnectionStatusListener(listener: () -> Unit): Sub {
            statusListener = listener
            return FakeSub { statusListener = null }
        }

        override fun start() {
            started = true
        }

        override fun stop() {
            started = false
        }

        override fun connect(location: ConnectLocation) {
            commands.add("connect")
            connectionStatus = "DESTINATION_SET"
        }

        override fun connectBestAvailable() {
            commands.add("connectBestAvailable")
            // the sdk's own first status for a connect; its listener fires later
            connectionStatus = "DESTINATION_SET"
        }

        override fun disconnect() {
            commands.add("disconnect")
            connectionStatus = "DISCONNECTED"
        }

        /** The sdk's listener reports `status`, on its own thread in the app. */
        fun report(status: String) {
            connectionStatus = status
            statusListener?.invoke()
        }
    }

    /** The sdk's device: every controller it opened, in order. */
    private class FakeDevice : ConnectDevice {
        val controllers = mutableListOf<FakeController>()

        override val connectEnabled = false
        override val tunnelStarted = false
        override val contractStatus: ContractStatus? = null

        override fun addTunnelChangeListener(listener: (tunnelStarted: Boolean) -> Unit): Sub = FakeSub()
        override fun addContractStatusChangeListener(listener: () -> Unit): Sub = FakeSub()

        override fun openConnectViewController(): ConnectController =
            FakeController().also { controllers.add(it) }

        override fun closeViewController(controller: ConnectController) {
            (controller as FakeController).closed = true
        }
    }

    /** The view model with the app around it replaced: the gate, the devices, the process lifecycle. */
    private class Harness {
        val gate = HeldGate()
        private val lifecycleOwner: LifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }
        private val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(lifecycleOwner)
        private var deviceListener: ((ConnectDevice?) -> Unit)? = null

        val viewModel = ConnectViewModel(
            deviceManager = DeviceManager(JwtManager(), NetworkSpaceManagerProvider()),
            appContext = ContextWrapper(null),
            processLifecycle = registry,
            deviceChanges = { listener ->
                deviceListener = listener
                // the device manager delivers its device on subscribe: none yet
                listener(null)
                ({ deviceListener = null })
            },
            checkStartConnect = gate::check,
        )

        val displayed: ConnectStatus get() = viewModel.displayConnectStatus.value

        /** The device manager publishes `device`. */
        fun publish(device: FakeDevice?) {
            deviceListener?.invoke(device)
        }

        fun foreground() {
            registry.currentState = Lifecycle.State.STARTED
        }

        fun background() {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    /** A view model presenting a controller of a published device, in the foreground. */
    private fun presenting(): Triple<Harness, FakeDevice, FakeController> {
        val harness = Harness()
        val device = FakeDevice()
        harness.foreground()
        harness.publish(device)
        assertEquals(1, device.controllers.size)
        val controller = device.controllers.single()
        assertTrue(controller.started)
        return Triple(harness, device, controller)
    }

    @Test
    fun tapShowsConnectingWhileTheGateFetches() {
        val (harness, _, controller) = presenting()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.viewModel.connect(null)
        assertEquals(1, harness.gate.checks)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)
        // nothing reaches the sdk until the gate admits the start
        assertEquals(emptyList<String>(), controller.commands)
    }

    @Test
    fun disconnectWhileTheStartWaitsEndsIt() {
        // the connecting state offers Disconnect: the user's disconnect ends
        // the start, so the gate's later admit never connects
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)

        harness.viewModel.disconnect()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.gate.decide(blocked = false)
        assertEquals(listOf("disconnect"), controller.commands)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun deviceChangeWhileTheStartWaitsEndsIt() {
        // the waiting start belonged to the previous device: the new one's
        // controller is never connected by it
        val (harness, previous, previousController) = presenting()
        harness.viewModel.connect(null)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)

        val next = FakeDevice()
        harness.publish(next)
        assertTrue(previousController.closed)
        val nextController = next.controllers.single()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.gate.decide(blocked = false)
        assertEquals(emptyList<String>(), previousController.commands)
        assertEquals(emptyList<String>(), nextController.commands)
        assertEquals(1, previous.controllers.size)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun signOutWhileTheStartWaitsEndsIt() {
        val (harness, device, controller) = presenting()
        harness.viewModel.connect(null)

        harness.publish(null)
        assertTrue(controller.closed)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.gate.decide(blocked = false)
        assertEquals(emptyList<String>(), controller.commands)
        assertEquals(1, device.controllers.size)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun admittedStartShowsTheControllersStatusAtOnce() {
        // the controller has the connect's status right after the connect,
        // but its listener reports it later, on the sdk's thread: the screen
        // reads it at once instead of stepping back to the status from before
        // the tap
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)

        harness.gate.decide(blocked = false)
        assertEquals(listOf("connectBestAvailable"), controller.commands)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.viewModel.connectStatus.value)

        // the sdk's statuses follow from here
        controller.report("CONNECTING")
        assertEquals(ConnectStatus.CONNECTING, harness.displayed)
    }

    @Test
    fun startAdmittedInTheBackgroundStillConnects() {
        // the app went to the background while the gate fetched the balance,
        // which closes the presented controller: the admitted connect runs on
        // a controller opened for it, so the user's connect is not lost
        val (harness, device, presented) = presenting()
        harness.viewModel.connect(null)
        harness.background()
        assertTrue(presented.closed)

        harness.gate.decide(blocked = false)
        assertEquals(2, device.controllers.size)
        val temporary = device.controllers[1]
        assertEquals(listOf("connectBestAvailable"), temporary.commands)
        assertTrue(temporary.closed)
        assertFalse(temporary.started)
        assertEquals(emptyList<String>(), presented.commands)
    }

    @Test
    fun disconnectWithoutAPresentedControllerStillReachesTheSdk() {
        val harness = Harness()
        val device = FakeDevice()
        // in the background no controller is presented
        harness.publish(device)
        assertEquals(0, device.controllers.size)

        harness.viewModel.disconnect()
        val temporary = device.controllers.single()
        assertEquals(listOf("disconnect"), temporary.commands)
        assertTrue(temporary.closed)
    }
}
