package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which quick connect requests end a connect waiting on the balance
 * (BalanceRecovery). A tap on the connecting tile or widget button ends a
 * start still waiting on the gate and asks for a disconnect with nothing
 * connected, a step that does nothing (review of the connect tap
 * follow-ups, 2026-10-16). It still ends the wait on the balance: a refused
 * start's recovery must not reconnect after the user stopped it, as the
 * connect screen's disconnect always clears it.
 */
class QuickConnectBalanceRecoveryTest {

    private class Case(val connectEnabled: Boolean, val connect: Boolean, val blocked: Boolean, val clears: Boolean)

    @Test
    fun theUsersConnectAndEveryDisconnectEndTheWait() {
        val cases = listOf(
            // a disconnect with nothing connected: the tap on the connecting tile
            Case(connectEnabled = false, connect = false, blocked = false, clears = true),
            Case(connectEnabled = false, connect = false, blocked = true, clears = true),
            // a disconnect
            Case(connectEnabled = true, connect = false, blocked = false, clears = true),
            // the user's connect
            Case(connectEnabled = false, connect = true, blocked = false, clears = true),
            // a refused start waits on the balance itself
            Case(connectEnabled = false, connect = true, blocked = true, clears = false),
            // a connect while connected leaves the connection and its wait as they are
            Case(connectEnabled = true, connect = true, blocked = false, clears = false),
            Case(connectEnabled = true, connect = true, blocked = true, clears = false),
        )
        for (c in cases) {
            val step = quickConnectStep(
                connectEnabled = c.connectEnabled,
                connect = c.connect,
                startConnectBlocked = c.blocked,
            )
            assertEquals(
                "connectEnabled=${c.connectEnabled} connect=${c.connect} blocked=${c.blocked} ($step)",
                c.clears,
                quickConnectClearsBalanceRecovery(c.connect, step),
            )
        }
    }
}
