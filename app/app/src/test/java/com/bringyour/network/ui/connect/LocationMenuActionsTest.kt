package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A location row's context menu (LocationsList.kt): only the connect screen's
 * own actions, Connect as a click on the row does, and Disconnect on the
 * location the connection is on.
 */
class LocationMenuActionsTest {

    @Test
    fun aRowOffersConnect() {
        assertEquals(
            listOf(LocationMenuAction.Connect),
            locationMenuActions(selected = false, connectRequested = false),
        )
        // connected elsewhere: Connect moves the connection here
        assertEquals(
            listOf(LocationMenuAction.Connect),
            locationMenuActions(selected = false, connectRequested = true),
        )
        // the chosen location while disconnected
        assertEquals(
            listOf(LocationMenuAction.Connect),
            locationMenuActions(selected = true, connectRequested = false),
        )
    }

    @Test
    fun theLocationTheConnectionIsOnOffersDisconnect() {
        assertEquals(
            listOf(LocationMenuAction.Disconnect),
            locationMenuActions(selected = true, connectRequested = true),
        )
    }
}
