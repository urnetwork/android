package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The network country the app reports to the sdk (P052): the mobile network's
 * country on cellular, so a whitelist-only mobile network still gets the
 * country's extender spoof list when the extender hint cannot be fetched, and
 * nothing on Wi-Fi.
 */
class NetworkCountryReporterTest {
    private class Telephony(var networkCountryIso: String?) {
        var readCount = 0

        fun read(): String? {
            readCount += 1
            return networkCountryIso
        }
    }

    private fun newReporter(telephony: Telephony, reports: MutableList<String>) =
        NetworkCountryReporter(
            readNetworkCountryIso = telephony::read,
            report = { reports.add(it) },
        )

    @Test
    fun cellularSelectsTheNetworkCountry() {
        assertEquals("ru", networkCountryCodeFor(isCellular = true, networkCountryIso = "ru"))
        assertEquals("ru", networkCountryCodeFor(isCellular = true, networkCountryIso = " RU "))
        // roaming: the network the device is registered on, not the sim's home
        assertEquals("kz", networkCountryCodeFor(isCellular = true, networkCountryIso = "kz"))
    }

    @Test
    fun notCellularSelectsNoCountry() {
        assertEquals("", networkCountryCodeFor(isCellular = false, networkCountryIso = "ru"))
        assertEquals("", networkCountryCodeFor(isCellular = false, networkCountryIso = null))
    }

    @Test
    fun anUnregisteredOrMalformedCountryIsNoCountry() {
        assertEquals("", networkCountryCodeFor(isCellular = true, networkCountryIso = ""))
        assertEquals("", networkCountryCodeFor(isCellular = true, networkCountryIso = null))
        assertEquals("", networkCountryCodeFor(isCellular = true, networkCountryIso = "rus"))
        assertEquals("", networkCountryCodeFor(isCellular = true, networkCountryIso = "r1"))
    }

    @Test
    fun cellularReportsItAndWifiClearsIt() {
        val telephony = Telephony("ru")
        val reports = mutableListOf<String>()
        val reporter = newReporter(telephony, reports)

        reporter.defaultNetworkChanged(isCellular = true)
        reporter.defaultNetworkChanged(isCellular = false)
        reporter.defaultNetworkChanged(isCellular = true)

        assertEquals(listOf("ru", "", "ru"), reports)
    }

    @Test
    fun anUnchangedCountryIsReportedOnce() {
        val telephony = Telephony("ru")
        val reports = mutableListOf<String>()
        val reporter = newReporter(telephony, reports)

        // capability callbacks repeat for the same network (signal, bandwidth)
        reporter.defaultNetworkChanged(isCellular = true)
        reporter.defaultNetworkChanged(isCellular = true)
        reporter.defaultNetworkChanged(isCellular = true)

        assertEquals(listOf("ru"), reports)
    }

    @Test
    fun theFirstReportIsMadeEvenWithNoCountry() {
        val telephony = Telephony("ru")
        val reports = mutableListOf<String>()
        val reporter = newReporter(telephony, reports)

        // the startup report on Wi-Fi states "none" rather than staying silent
        reporter.defaultNetworkChanged(isCellular = false)
        reporter.defaultNetworkChanged(isCellular = false)

        assertEquals(listOf(""), reports)
    }

    @Test
    fun aNewNetworkCountryOnCellularIsReported() {
        val telephony = Telephony("ru")
        val reports = mutableListOf<String>()
        val reporter = newReporter(telephony, reports)

        reporter.defaultNetworkChanged(isCellular = true)
        telephony.networkCountryIso = "kz"
        reporter.defaultNetworkChanged(isCellular = true)
        // the radio drops its registration
        telephony.networkCountryIso = ""
        reporter.defaultNetworkChanged(isCellular = true)

        assertEquals(listOf("ru", "kz", ""), reports)
    }

    @Test
    fun telephonyIsReadOnlyOnCellular() {
        val telephony = Telephony("ru")
        val reporter = newReporter(telephony, mutableListOf())

        reporter.defaultNetworkChanged(isCellular = false)
        assertEquals(0, telephony.readCount)
        reporter.defaultNetworkChanged(isCellular = true)
        assertEquals(1, telephony.readCount)
    }
}
