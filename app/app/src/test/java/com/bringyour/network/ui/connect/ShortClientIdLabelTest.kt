package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The provider button label shortens a client id to "abcd…1234"
 * so it does not crowd the Change button.
 */
class ShortClientIdLabelTest {

    private val clientId = "018f2b6e-3c4d-7a8b-9c0d-1e2f3a4b5c6d"

    @Test
    fun clientIdLabelIsShortened() {
        assertEquals("018f…5c6d", shortClientIdLabel(clientId, clientId))
    }

    @Test
    fun clientIdLabelMatchIgnoresCase() {
        assertEquals("018F…5C6D", shortClientIdLabel(clientId.uppercase(), clientId))
    }

    @Test
    fun deviceNameIsUnchanged() {
        assertEquals("Pixel 8", shortClientIdLabel("Pixel 8", clientId))
    }

    @Test
    fun locationNameWithoutClientIdIsUnchanged() {
        assertEquals("Germany", shortClientIdLabel("Germany", null))
        assertEquals(clientId, shortClientIdLabel(clientId, null))
    }
}
