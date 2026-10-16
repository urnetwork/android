package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A connect tap shows the connecting state at once, also while the start
 * connect gate fetches a fresh balance (owner report 2026-10-16). The gate
 * here is held until the test decides, as a fetch is held until the server
 * answers, and the sdk's connect reports its first status on the same
 * thread, as the controller does. Nothing waits on time.
 */
class StartConnectAdmissionTest {
    /** The admission and its separate presentation, as wired by the app. */
    private class AdmissionDisplay(check: (onResult: (Boolean) -> Unit) -> Unit) {
        private val display = ConnectStatusDisplay()
        private val admission = StartConnectAdmission(check = check, changed = {
            display.startWaiting(waiting())
        })
        private fun waiting(): Boolean = admission.waiting
        val displayStatus get() = display.displayStatus
        fun reported(status: ConnectStatus) = display.reported(status)
        fun connect(start: () -> Unit, refused: () -> Unit) = admission.connect(start, refused)
        fun cancel() = admission.cancel()
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

    /** The sdk side of a tap: what ran, and the screen's status at the time. */
    private class Sdk(private val admission: () -> AdmissionDisplay) {
        var starts = 0
        var refusals = 0
        val statusesAtRefusal = mutableListOf<ConnectStatus>()

        fun start() {
            starts += 1
            // the controller's first status for the connect
            admission().reported(ConnectStatus.DESTINATION_SET)
        }

        fun refused() {
            refusals += 1
            statusesAtRefusal.add(admission().displayStatus.value)
        }
    }

    private fun admission(gate: HeldGate): Pair<AdmissionDisplay, Sdk> {
        lateinit var admission: AdmissionDisplay
        admission = AdmissionDisplay(gate::check)
        val sdk = Sdk { admission }
        return Pair(admission, sdk)
    }

    @Test
    fun startWaitingOnItsBalanceShowsConnectingAtOnce() {
        // the reported wait: up to five seconds of the disconnected view
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)
        assertEquals(ConnectStatus.DISCONNECTED, admission.displayStatus.value)

        admission.connect(sdk::start, sdk::refused)
        assertEquals(ConnectStatus.DESTINATION_SET, admission.displayStatus.value)
        assertEquals(0, sdk.starts)
    }

    @Test
    fun admittedStartHandsOverWithoutSteppingBack() {
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)
        val shown = mutableListOf(admission.displayStatus.value)

        admission.connect(sdk::start, sdk::refused)
        shown.add(admission.displayStatus.value)
        gate.decide(blocked = false)
        shown.add(admission.displayStatus.value)

        assertEquals(1, sdk.starts)
        assertEquals(0, sdk.refusals)
        assertEquals(
            listOf(ConnectStatus.DISCONNECTED, ConnectStatus.DESTINATION_SET, ConnectStatus.DESTINATION_SET),
            shown,
        )
        // the sdk's statuses follow from here
        admission.reported(ConnectStatus.CONNECTED)
        assertEquals(ConnectStatus.CONNECTED, admission.displayStatus.value)
    }

    @Test
    fun refusedStartGoesBackBeforeTheUpgradeScreen() {
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)

        admission.connect(sdk::start, sdk::refused)
        assertEquals(ConnectStatus.DESTINATION_SET, admission.displayStatus.value)
        gate.decide(blocked = true)

        // a refused start still refuses: nothing reaches the sdk
        assertEquals(0, sdk.starts)
        assertEquals(1, sdk.refusals)
        assertEquals(listOf(ConnectStatus.DISCONNECTED), sdk.statusesAtRefusal)
        assertEquals(ConnectStatus.DISCONNECTED, admission.displayStatus.value)
    }

    @Test
    fun reportWhileTheStartWaitsDoesNotFlipTheView() {
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)

        admission.connect(sdk::start, sdk::refused)
        // the sdk reports the state from before the tap
        admission.reported(ConnectStatus.DISCONNECTED)
        assertEquals(ConnectStatus.DESTINATION_SET, admission.displayStatus.value)

        gate.decide(blocked = true)
        assertEquals(ConnectStatus.DISCONNECTED, admission.displayStatus.value)
    }

    @Test
    fun disconnectWhileTheStartWaitsNeverConnects() {
        // the connecting state offers Disconnect: it ends the start
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)

        admission.connect(sdk::start, sdk::refused)
        admission.cancel()
        assertEquals(ConnectStatus.DISCONNECTED, admission.displayStatus.value)

        gate.decide(blocked = false)
        assertEquals(0, sdk.starts)
        assertEquals(0, sdk.refusals)
        assertEquals(ConnectStatus.DISCONNECTED, admission.displayStatus.value)
    }

    @Test
    fun repeatedTapWhileTheStartWaitsDoesNotStack() {
        val gate = HeldGate()
        val (admission, sdk) = admission(gate)

        admission.connect(sdk::start, sdk::refused)
        admission.connect(sdk::start, sdk::refused)
        assertEquals(1, gate.checks)

        gate.decide(blocked = false)
        assertEquals(1, sdk.starts)
        // the next tap goes through the gate again
        admission.reported(ConnectStatus.DISCONNECTED)
        admission.connect(sdk::start, sdk::refused)
        assertEquals(2, gate.checks)
    }

    @Test
    fun decisionWithoutAFetchShowsNoPendingState() {
        // a fresh balance or a known plan decides at once: the sdk's own
        // statuses are all the screen shows
        val shownAtStart = mutableListOf<ConnectStatus>()
        lateinit var admission: AdmissionDisplay
        admission = AdmissionDisplay({ onResult -> onResult(false) })
        admission.connect(
            start = {
                shownAtStart.add(admission.displayStatus.value)
                admission.reported(ConnectStatus.DESTINATION_SET)
            },
            refused = {},
        )
        assertEquals(listOf(ConnectStatus.DISCONNECTED), shownAtStart)
        assertEquals(ConnectStatus.DESTINATION_SET, admission.displayStatus.value)

        var refused = false
        val blocked = AdmissionDisplay({ onResult -> onResult(true) })
        blocked.connect(start = {}, refused = { refused = true })
        assertTrue(refused)
        assertEquals(ConnectStatus.DISCONNECTED, blocked.displayStatus.value)
    }
}
