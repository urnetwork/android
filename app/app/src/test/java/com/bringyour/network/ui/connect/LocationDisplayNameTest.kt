package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The location list names a country in the app's language, with the server's
 * English name as the fallback, and leaves every other location's name as the
 * server sends it (support inbox 8621).
 */
class LocationDisplayNameTest {

    private val turkish = mapOf("DE" to "Almanya", "IR" to "İran")
    private val countryName = { code: String -> turkish[code.uppercase()] ?: "" }

    @Test
    fun countryReadsInTheAppLanguage() {
        assertEquals("Almanya", locationDisplayName(true, "de", "Germany", countryName))
        assertEquals("İran", locationDisplayName(true, "ir", "Iran", countryName))
    }

    @Test
    fun countryThePlatformDoesNotNameKeepsTheServerName() {
        assertEquals("Kosovo", locationDisplayName(true, "xk", "Kosovo", countryName))
    }

    @Test
    fun countryWithoutATwoLetterCodeKeepsTheServerName() {
        assertEquals("Germany", locationDisplayName(true, "", "Germany", countryName))
        assertEquals("Germany", locationDisplayName(true, null, "Germany", countryName))
        assertEquals("Germany", locationDisplayName(true, "deu", "Germany", countryName))
    }

    @Test
    fun regionCityGroupAndDeviceKeepTheServerName() {
        // a city or region carries its country's code, which must not rename it
        assertEquals("Berlin", locationDisplayName(false, "de", "Berlin", countryName))
        assertEquals("Strong Privacy", locationDisplayName(false, null, "Strong Privacy", countryName))
    }
}
