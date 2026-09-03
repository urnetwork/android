package com.bringyour.network.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/** The color of the disc next to the widget's location name (the Apple widget's rules). */
class LocationMarkTest {

    @Test
    fun countryColorWhenTheTunnelShowsALocation() {
        assertEquals(0xFFF94144.toInt(), locationMarkArgb(hasLocation = true, colorHex = "F94144"))
        assertEquals(0xFF0099FF.toInt(), locationMarkArgb(hasLocation = true, colorHex = "#0099ff"))
    }

    @Test
    fun faintWhenThereIsNoLocation() {
        assertEquals(LOCATION_MARK_OFF, locationMarkArgb(hasLocation = false, colorHex = "F94144"))
        assertEquals(LOCATION_MARK_OFF, locationMarkArgb(hasLocation = false, colorHex = null))
    }

    @Test
    fun unknownCountryBlueWhenTheLocationHasNoColor() {
        assertEquals(LOCATION_MARK_UNKNOWN, locationMarkArgb(hasLocation = true, colorHex = ""))
        assertEquals(LOCATION_MARK_UNKNOWN, locationMarkArgb(hasLocation = true, colorHex = null))
        assertEquals(LOCATION_MARK_UNKNOWN, locationMarkArgb(hasLocation = true, colorHex = "zz"))
        assertEquals(LOCATION_MARK_UNKNOWN, locationMarkArgb(hasLocation = true, colorHex = "F9414"))
    }
}
