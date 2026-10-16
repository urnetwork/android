package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A quick connect surface (the Quick Settings tile, a launcher shortcut, the
 * widget button) shows connecting from the tap while the start connect gate
 * fetches a fresh balance, as the connect screen does (owner report
 * 2026-10-16). The gate here is held until the test decides, as a fetch is
 * held until the server answers, and each re-render of the surfaces records
 * what they show. Nothing waits on time.
 */
class QuickStartConnectTest {

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

    /**
     * The surfaces and the device behind them: what each re-render showed,
     * each request the quick connect step made (connect, refused), and each
     * result a surface got.
     */
    private class Surfaces(gate: HeldGate) {
        var connectEnabled = false
        val rendered = mutableListOf<QuickConnectShown>()
        val applied = mutableListOf<Pair<Boolean, Boolean>>()
        val shownWhenApplied = mutableListOf<QuickConnectShown>()
        val results = mutableListOf<String>()

        val quick = QuickStartConnect(gate::check) { rendered.add(shown()) }

        fun shown(): QuickConnectShown = quickConnectShown(connectEnabled, quick.waiting)

        /** A tap: `connect` is what the surface asks for. */
        fun tap(connect: Boolean) {
            quick.request(
                connectEnabled = connectEnabled,
                connect = connect,
                apply = { blocked ->
                    applied.add(Pair(connect, blocked))
                    shownWhenApplied.add(shown())
                    when {
                        connect && blocked -> "upgrade"
                        else -> {
                            connectEnabled = connect
                            "applied"
                        }
                    }
                },
                waiting = { "waiting" },
                onResult = { results.add(it) },
            )
        }

        /** The tile and the widget button toggle what they show. */
        fun toggle() = tap(connect = shown() == QuickConnectShown.OFF)
    }

    @Test
    fun startWaitingOnItsBalanceShowsConnectingAtOnce() {
        // the reported wait: up to five seconds of the disconnected tile
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        assertEquals(QuickConnectShown.OFF, surfaces.shown())

        surfaces.toggle()
        assertEquals(1, gate.checks)
        assertEquals(listOf(QuickConnectShown.CONNECTING), surfaces.rendered)
        assertEquals(QuickConnectShown.CONNECTING, surfaces.shown())
        // nothing is asked of the sdk, and the surface has no result, until
        // the gate decides
        assertEquals(emptyList<Pair<Boolean, Boolean>>(), surfaces.applied)
        assertEquals(emptyList<String>(), surfaces.results)
    }

    @Test
    fun refusedStartShowsOffBeforeTheUpgradeScreen() {
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        surfaces.toggle()

        gate.decide(blocked = true)
        // a refused start still refuses: the step opens the upgrade screen,
        // after the surfaces went back to off
        assertEquals(listOf(Pair(true, true)), surfaces.applied)
        assertEquals(listOf(QuickConnectShown.OFF), surfaces.shownWhenApplied)
        assertEquals(QuickConnectShown.OFF, surfaces.rendered.last())
        assertEquals(listOf("upgrade"), surfaces.results)
        assertFalse(surfaces.connectEnabled)
    }

    @Test
    fun admittedStartConnects() {
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        surfaces.toggle()

        gate.decide(blocked = false)
        assertEquals(listOf(Pair(true, false)), surfaces.applied)
        assertEquals(listOf("applied"), surfaces.results)
        assertTrue(surfaces.connectEnabled)
        assertEquals(QuickConnectShown.ON, surfaces.rendered.last())
    }

    @Test
    fun toggleWhileTheStartWaitsEndsIt() {
        // the tile shows connecting, so a second tap is the user's disconnect:
        // it ends the start, and the gate's later admit never connects
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        surfaces.toggle()
        assertEquals(QuickConnectShown.CONNECTING, surfaces.shown())

        surfaces.toggle()
        assertEquals(QuickConnectShown.OFF, surfaces.shown())
        assertEquals(QuickConnectShown.OFF, surfaces.rendered.last())

        gate.decide(blocked = false)
        assertEquals(listOf(Pair(false, false)), surfaces.applied)
        assertEquals(listOf("applied"), surfaces.results)
        assertFalse(surfaces.connectEnabled)
    }

    @Test
    fun connectWhileTheStartWaitsAnswersAtOnceAndDoesNotStack() {
        // a second Connect shortcut: its trampoline finishes at once, and the
        // gate is asked once
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        surfaces.tap(connect = true)
        surfaces.tap(connect = true)
        assertEquals(1, gate.checks)
        assertEquals(listOf("waiting"), surfaces.results)

        gate.decide(blocked = false)
        assertEquals(listOf(Pair(true, false)), surfaces.applied)
        assertEquals(listOf("waiting", "applied"), surfaces.results)
    }

    @Test
    fun connectionAlreadyRequestedNeverWaitsOnTheGate() {
        val gate = HeldGate()
        val surfaces = Surfaces(gate)
        surfaces.connectEnabled = true

        surfaces.tap(connect = true)
        assertEquals(0, gate.checks)
        assertEquals(listOf(Pair(true, false)), surfaces.applied)
        assertEquals(listOf("applied"), surfaces.results)
        assertEquals(emptyList<QuickConnectShown>(), surfaces.rendered)
    }

    @Test
    fun decisionWithoutAFetchShowsNoPendingState() {
        // a fresh balance or a known plan decides at once: no connecting state
        val rendered = mutableListOf<QuickConnectShown>()
        var connectEnabled = false
        lateinit var quick: QuickStartConnect
        quick = QuickStartConnect({ onResult -> onResult(false) }) {
            rendered.add(quickConnectShown(connectEnabled, quick.waiting))
        }
        val results = mutableListOf<String>()
        quick.request(
            connectEnabled = false,
            connect = true,
            apply = { blocked ->
                assertFalse(blocked)
                connectEnabled = true
                "applied"
            },
            waiting = { "waiting" },
            onResult = { results.add(it) },
        )
        assertEquals(listOf("applied"), results)
        assertFalse(rendered.contains(QuickConnectShown.CONNECTING))
        assertFalse(quick.waiting)
    }

    @Test
    fun surfacesShowOnConnectingOrOff() {
        assertEquals(QuickConnectShown.OFF, quickConnectShown(connectEnabled = false, startWaiting = false))
        assertEquals(QuickConnectShown.CONNECTING, quickConnectShown(connectEnabled = false, startWaiting = true))
        assertEquals(QuickConnectShown.ON, quickConnectShown(connectEnabled = true, startWaiting = false))
        assertEquals(QuickConnectShown.ON, quickConnectShown(connectEnabled = true, startWaiting = true))
    }
}
