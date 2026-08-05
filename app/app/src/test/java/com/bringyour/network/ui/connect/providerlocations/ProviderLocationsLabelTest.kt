package com.bringyour.network.ui.connect.providerlocations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLocationsLabelTest {

    private fun row(
        city: String = "",
        region: String = "",
        country: String = "",
        lat: Double? = null,
        lon: Double? = null,
    ) = ProviderLocationRow(
        clientId = "01234567-89ab-cdef-0123-456789abcdef",
        country = country,
        countryCode = "",
        region = region,
        city = city,
        hasLocation = city.isNotEmpty() || region.isNotEmpty() || country.isNotEmpty(),
        lat = lat,
        lon = lon,
        connectedSinceMillis = 0,
    )

    @Test
    fun placeLabelIsCityRegionCountry() {
        assertEquals(
            "San Francisco, California, United States",
            placeLabel(row(city = "San Francisco", region = "California", country = "United States")),
        )
    }

    @Test
    fun placeLabelOmitsTheUnknownParts() {
        assertEquals("California, United States", placeLabel(row(region = "California", country = "United States")))
        assertEquals("Reykjavik, Iceland", placeLabel(row(city = "Reykjavik", country = "Iceland")))
        assertEquals("Iceland", placeLabel(row(country = "Iceland")))
        assertEquals("", placeLabel(row()))
    }

    @Test
    fun coordinatesLabelFormatsFourDecimals() {
        assertEquals("37.7749, -122.4194", coordinatesLabel(row(lat = 37.7749, lon = -122.4194)))
    }

    @Test
    fun coordinatesLabelIsADashWhenUnknown() {
        assertEquals("—", coordinatesLabel(row()))
        assertEquals("—", coordinatesLabel(row(lat = 1.0)))
    }

    @Test
    fun plottableRequiresBothCoordinates() {
        assertTrue(row(lat = 1.0, lon = 2.0).plottable)
        assertFalse(row(lat = 1.0).plottable)
        assertFalse(row().plottable)
    }
}
