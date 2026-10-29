package com.bringyour.network

import com.bringyour.network.ui.connect.QuickConnectStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When the app asks the system to show the active Quick Settings tile again
 * (QuickConnectTileRefresh). The posts are held until the test runs them, as
 * the main looper runs them later.
 */
class QuickConnectTileRefreshTest {

    private var status = QuickConnectStatus.CONNECTING
    private var rendered: QuickConnectStatus? = QuickConnectStatus.CONNECTING
    private var placed = false
    private var requests = 0
    private val posted = ArrayDeque<Runnable>()

    private val refresh = QuickConnectTileRefresh(
        post = { posted.addLast(it) },
        status = { status },
        rendered = { rendered },
        placed = { placed },
        requestUpdate = { requests += 1 },
    )

    private fun runPosted() {
        while (posted.isNotEmpty()) {
            posted.removeFirst().run()
        }
    }

    @Test
    fun aWindowThatFillsAsksOnceForTheBurst() {
        status = QuickConnectStatus.CONNECTED
        repeat(5) { refresh.changed() }
        assertEquals(1, posted.size)

        runPosted()
        assertEquals(1, requests)
    }

    @Test
    fun reportsThatLeaveTheStatusAsTheTileSaysItAskNothing() {
        repeat(3) {
            refresh.changed()
            runPosted()
        }
        assertEquals(0, requests)
    }

    @Test
    fun theTunnelGoingDownAsks() {
        status = QuickConnectStatus.TUNNEL_DISCONNECTED
        rendered = QuickConnectStatus.CONNECTED
        refresh.changed()
        runPosted()
        assertEquals(1, requests)
    }

    @Test
    fun aTileThatMissedTheRequestIsAskedAgain() {
        // the system can drop a request; the next report asks again
        status = QuickConnectStatus.CONNECTED
        refresh.changed()
        runPosted()
        refresh.changed()
        runPosted()
        assertEquals(2, requests)
    }

    @Test
    fun aTileNobodyPlacedIsNotAskedForEachReport() {
        // it never spoke in this process and the app never saw it added
        rendered = null
        status = QuickConnectStatus.CONNECTED
        refresh.changed()
        runPosted()
        assertEquals(0, requests)

        placed = true
        refresh.changed()
        runPosted()
        assertEquals(1, requests)
    }

    @Test
    fun aSignOutAsks() {
        // the device changes: asked even before the tile spoke, and even
        // when the status reads the same
        rendered = null
        status = QuickConnectStatus.NOT_SIGNED_IN
        refresh.deviceChanged()
        runPosted()
        assertEquals(1, requests)

        rendered = QuickConnectStatus.NOT_SIGNED_IN
        refresh.deviceChanged()
        runPosted()
        assertEquals(2, requests)
    }

    @Test
    fun aDeviceChangeFoldsIntoAPendingCheck() {
        refresh.changed()
        refresh.deviceChanged()
        assertEquals(1, posted.size)

        runPosted()
        assertEquals(1, requests)
        // the next report is judged on its own again
        refresh.changed()
        runPosted()
        assertEquals(1, requests)
    }
}
