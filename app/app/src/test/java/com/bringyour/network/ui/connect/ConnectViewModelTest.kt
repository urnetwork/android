package com.bringyour.network.ui.connect

import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
 * answers, and the connect command queue held until the test runs it, as the
 * sdk holds a connect while it commits the local state (owner report
 * 2026-10-16). It pins how the view model wires its start connect and its
 * commands: the user's disconnect and a device change end a start waiting on
 * its balance, a tap shows its state at once and holds it until the queue has
 * run it, and the presented controller closes only after the commands queued
 * against it.
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

    /** The connect command queue's thread, held: nothing runs until [runAll]. */
    private class HeldQueue {
        private val queued = ArrayDeque<Runnable>()

        val size: Int get() = queued.size

        fun execute(runnable: Runnable) {
            queued.addLast(runnable)
        }

        /** Runs what is queued, in order, also what the work queues meanwhile. */
        fun runAll() {
            while (queued.isNotEmpty()) {
                queued.removeFirst().run()
            }
        }
    }

    /**
     * The sdk's connect view controller: what it was asked, and its status.
     * Closed, it refuses a connect or disconnect, as the sdk's does
     * (ConnectViewController.beginLocalPreferenceGesture).
     */
    private class FakeController : ConnectController {
        val commands = mutableListOf<String>()
        var started = false
        var closed = false
        private var statusListener: (() -> Unit)? = null

        private fun command(name: String, status: String) {
            if (closed) {
                commands.add("refused $name")
                return
            }
            commands.add(name)
            connectionStatus = status
        }

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

        override fun connect(location: ConnectLocation) = command("connect", "DESTINATION_SET")

        // the sdk's own first status for a connect; its listener fires later
        override fun connectBestAvailable() = command("connectBestAvailable", "DESTINATION_SET")

        override fun disconnect() = command("disconnect", "DISCONNECTED")

        /** The sdk's listener reports `status`, on its own thread in the app. */
        fun report(status: String) {
            connectionStatus = status
            statusListener?.invoke()
        }
    }

    /** The sdk's device: every controller it opened, in order. */
    private class FakeDevice : ConnectDevice {
        val controllers = mutableListOf<FakeController>()

        override val identity: Any get() = this

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
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
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
            connectCommands = commands,
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

        /** The view model's owner goes away (its activity finished): the store clears it. */
        fun clear() {
            val store = ViewModelStore()
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
            ViewModelProvider(store, factory)[ConnectViewModel::class.java]
            store.clear()
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
        harness.queue.runAll()
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
        harness.queue.runAll()
        assertTrue(previousController.closed)
        val nextController = next.controllers.single()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.gate.decide(blocked = false)
        harness.queue.runAll()
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
        harness.queue.runAll()
        assertTrue(controller.closed)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.gate.decide(blocked = false)
        harness.queue.runAll()
        assertEquals(emptyList<String>(), controller.commands)
        assertEquals(1, device.controllers.size)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun admittedStartShowsTheControllersStatusAtOnce() {
        // the controller has the connect's status right after the connect,
        // but its listener reports it later, on the sdk's thread: the screen
        // reads it once the queue has run the connect instead of stepping back
        // to the status from before the tap
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)

        harness.gate.decide(blocked = false)
        harness.queue.runAll()
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
        harness.queue.runAll()
        assertTrue(presented.closed)

        harness.gate.decide(blocked = false)
        harness.queue.runAll()
        assertEquals(2, device.controllers.size)
        val temporary = device.controllers[1]
        assertEquals(listOf("connectBestAvailable"), temporary.commands)
        assertTrue(temporary.closed)
        assertFalse(temporary.started)
        assertEquals(emptyList<String>(), presented.commands)
    }

    @Test
    fun clearedViewModelNeverConnectsAWaitingStart() {
        // the screen's view model is cleared while a start waits: it no longer
        // follows the device changes, so the start ends with it instead of
        // connecting whichever device is current when the gate decides
        val (harness, device, presented) = presenting()
        harness.viewModel.connect(null)
        harness.clear()
        harness.queue.runAll()
        assertTrue(presented.closed)

        harness.gate.decide(blocked = false)
        harness.queue.runAll()
        assertEquals(1, device.controllers.size)
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
        harness.queue.runAll()
        val temporary = device.controllers.single()
        assertEquals(listOf("disconnect"), temporary.commands)
        assertTrue(temporary.closed)
    }

    /** A view model presenting a controller that reports `status`, the queue empty. */
    private fun presentingWith(status: String): Triple<Harness, FakeDevice, FakeController> {
        val presenting = presenting()
        presenting.third.report(status)
        assertEquals(ConnectStatus.fromString(status), presenting.first.displayed)
        return presenting
    }

    @Test
    fun admittedConnectWaitsOnTheQueueAndShowsAtOnce() {
        // the connect runs on the queue, not where it was tapped, and the
        // screen shows it from the tap
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)
        harness.gate.decide(blocked = false)

        assertEquals(emptyList<String>(), controller.commands)
        assertEquals(1, harness.queue.size)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)

        harness.queue.runAll()
        assertEquals(listOf("connectBestAvailable"), controller.commands)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)
    }

    @Test
    fun disconnectShowsAtOnceAndWaitsOnTheQueue() {
        val (harness, _, controller) = presentingWith("CONNECTED")

        harness.viewModel.disconnect()
        assertEquals(emptyList<String>(), controller.commands)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.queue.runAll()
        assertEquals(listOf("disconnect"), controller.commands)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun aReportFromBeforeTheDisconnectCannotFlipItBack() {
        // the outgoing session's report lands after the tap but before the
        // queue ran the disconnect: the tap's state stands
        val (harness, _, controller) = presentingWith("CONNECTED")
        harness.viewModel.disconnect()

        controller.report("CONNECTED")
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        // once the queue has run it, the controller is read again
        harness.queue.runAll()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
        assertEquals(ConnectStatus.DISCONNECTED, harness.viewModel.connectStatus.value)
    }

    @Test
    fun aReportFromBeforeTheConnectCannotFlipItBack() {
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)
        harness.gate.decide(blocked = false)

        controller.report("DISCONNECTED")
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)

        harness.queue.runAll()
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)
        controller.report("CONNECTED")
        assertEquals(ConnectStatus.CONNECTED, harness.displayed)
    }

    @Test
    fun connectThenDisconnectShowsTheNewestAndRunsInOrder() {
        val (harness, _, controller) = presenting()
        harness.viewModel.connect(null)
        harness.gate.decide(blocked = false)
        harness.viewModel.disconnect()
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.queue.runAll()
        assertEquals(listOf("connectBestAvailable", "disconnect"), controller.commands)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }

    @Test
    fun disconnectTappedBeforeTheBackgroundStillReachesTheSdk() {
        // the app goes to the background while the disconnect is still
        // queued: the presented controller closes only after it, since a
        // closed controller refuses it and the next launch would reconnect
        val (harness, _, controller) = presentingWith("CONNECTED")
        harness.viewModel.disconnect()
        harness.background()
        assertFalse(controller.closed)

        harness.queue.runAll()
        assertEquals(listOf("disconnect"), controller.commands)
        assertTrue(controller.closed)
    }

    @Test
    fun deviceChangeDropsThePreviousDevicesCommandsFromTheStatus() {
        // a sign out with the previous account's connect still queued: the
        // next account's screen does not show it, while it still runs
        val (harness, _, previousController) = presenting()
        harness.viewModel.connect(null)
        harness.gate.decide(blocked = false)
        assertEquals(ConnectStatus.DESTINATION_SET, harness.displayed)

        val next = FakeDevice()
        harness.publish(next)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)

        harness.queue.runAll()
        assertEquals(listOf("connectBestAvailable"), previousController.commands)
        assertEquals(emptyList<String>(), next.controllers.single().commands)
        assertEquals(ConnectStatus.DISCONNECTED, harness.displayed)
    }
}
