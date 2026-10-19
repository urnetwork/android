package com.bringyour.network.ui.connection_region

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connection region (ingress country) picker, the login control and the
 * settings picker sharing one stored value, and the can't-reach prompt.
 */
class ConnectionRegionTest {

    /**
     * Stands in for the network space: keeps only codes the catalog knows,
     * like `NetworkSpace.SetIngressCountryCode`
     */
    private class FakeStore(
        private val knownCodes: Set<String>,
        var value: String = "",
    ) : ConnectionRegionStore {
        var setCount = 0
        override fun get(): String = value
        override fun set(countryCode: String): String {
            setCount += 1
            value = if (countryCode in knownCodes) countryCode else ""
            return value
        }
    }

    private class FakeCatalog : ConnectionRegionCatalog {
        override fun countryCodes() = listOf("tr", "cn", "ru")
        override fun dohUrls(countryCode: String) = when (countryCode) {
            "cn" -> listOf("https://dns.alidns.com/dns-query")
            else -> listOf()
        }
        override fun plainDnsServers(countryCode: String) = when (countryCode) {
            "cn" -> listOf("223.5.5.5")
            else -> listOf()
        }
    }

    private val names = mapOf("cn" to "China", "ru" to "Russia", "tr" to "Türkiye")

    private fun model(store: ConnectionRegionStore) = ConnectionRegionModel(
        store = store,
        catalog = FakeCatalog(),
        displayName = { names.getValue(it) },
    )

    @Test
    fun optionsAreExactlyTheSdkRegionsPlusUndisclosed() {
        val m = model(FakeStore(setOf("cn", "ru", "tr")))
        val options = m.options()
        assertEquals(
            listOf(UNDISCLOSED_REGION, "cn", "ru", "tr"),
            options.map { it.countryCode },
        )
        assertEquals(
            listOf(null, "China", "Russia", "Türkiye"),
            options.map { it.displayName },
        )
        assertTrue(options.first().isUndisclosed)
    }

    @Test
    fun selectionStoresTheCodeAndUndisclosedStoresEmpty() {
        val store = FakeStore(setOf("cn", "ru", "tr"))
        val m = model(store)

        assertEquals("cn", m.select("cn"))
        assertEquals("cn", store.value)
        assertEquals("cn", m.selected)

        assertEquals("", m.select(UNDISCLOSED_REGION))
        assertEquals("", store.value)
        assertEquals("", m.selected)
    }

    @Test
    fun selectionShowsWhatTheStoreKept() {
        // an unknown code is stored as undisclosed
        val store = FakeStore(setOf("cn"))
        val m = model(store)
        assertEquals("", m.select("zz"))
        assertEquals("", m.selected)
    }

    @Test
    fun resolversShowEncryptedFallbackOrDefaults() {
        val m = model(FakeStore(setOf("cn", "ru", "tr")))
        val cn = m.resolvers("cn")
        assertEquals(listOf("https://dns.alidns.com/dns-query"), cn.dohUrls)
        assertEquals(listOf("223.5.5.5"), cn.plainDnsServers)
        assertFalse(cn.usesDefaults)
        assertTrue(m.resolvers("ru").usesDefaults)
        assertTrue(m.resolvers(UNDISCLOSED_REGION).usesDefaults)
    }

    @Test
    fun loginControlAndSettingsPickerShareTheStoredValue() {
        val store = FakeStore(setOf("cn", "ru", "tr"), value = "ru")
        val login = model(store)
        val settings = model(store)
        assertEquals("ru", login.selected)
        assertEquals("ru", settings.selected)

        login.select("cn")
        settings.refresh()
        assertEquals("cn", settings.selected)

        settings.select(UNDISCLOSED_REGION)
        login.refresh()
        assertEquals("", login.selected)

        // a later screen (after a restart) reads the persisted value
        assertEquals("", model(store).selected)
    }

    @Test
    fun networkFailuresAreUnreachable() {
        assertTrue(isUrnetworkUnreachable(UnknownHostException("api.bringyour.com")))
        assertTrue(isUrnetworkUnreachable(SocketTimeoutException()))
        assertTrue(isUrnetworkUnreachable(ConnectException()))
        assertTrue(isUrnetworkUnreachable(Exception(
            "http request failed: Post \"https://api.bringyour.com/auth/login\": dial tcp: lookup api.bringyour.com: no such host"
        )))
        assertTrue(isUrnetworkUnreachable(Exception("context deadline exceeded")))
        assertTrue(isUrnetworkUnreachable(Exception("read tcp 10.0.0.2:5000->1.2.3.4:443: i/o timeout")))
        assertTrue(isUrnetworkUnreachable(Exception("dial tcp 1.2.3.4:443: connect: network is unreachable")))
    }

    @Test
    fun authAndValidationErrorsAreNotUnreachable() {
        assertFalse(isUrnetworkUnreachable(null))
        // HttpStatusError: the platform answered
        assertFalse(isUrnetworkUnreachable(Exception("401 Unauthorized: invalid jwt")))
        assertFalse(isUrnetworkUnreachable(Exception("403 Forbidden: ")))
        assertFalse(isUrnetworkUnreachable(Exception("400 Bad Request: user auth is required")))
        // a status error whose body mentions a timeout is still a reached platform
        assertFalse(isUrnetworkUnreachable(Exception("503 Service Unavailable: upstream i/o timeout")))
        assertFalse(isUrnetworkUnreachable(Exception("Invalid user auth")))
    }

    @Test
    fun promptShowsOnlyForUnreachableAndNeverChangesTheRegion() {
        val store = FakeStore(setOf("cn", "ru", "tr"), value = "ru")
        val prompt = CantReachPrompt()

        assertFalse(prompt.report(Exception("401 Unauthorized: invalid jwt")))
        assertFalse(prompt.visible.value)
        assertFalse(prompt.report(null))
        assertFalse(prompt.visible.value)

        assertTrue(prompt.report(UnknownHostException("api.bringyour.com")))
        assertTrue(prompt.visible.value)

        prompt.dismiss()
        assertFalse(prompt.visible.value)

        assertEquals("ru", store.value)
        assertEquals(0, store.setCount)
    }
}
