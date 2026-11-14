package com.bringyour.network.ui.connection_region

import java.net.ConnectException
import java.net.NoRouteToHostException
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

    // The texts of a failure are the sdk's rule (sdk cant_reach.go
    // IsCantReachError), tested there with every app's cases, so the four apps
    // can't drift apart again (support inbox 7956, 7973). Here a fake stands
    // in for it: the app's own part is its transport exceptions, and asking
    // the sdk about anything else.

    // Android's own transport exceptions are the sdk's call too, by their
    // text, which names their class: one of them alone no longer raises the
    // prompt (the sdk's hold, sdk cant_reach.go)
    @Test
    fun androidTransportFailuresAreTheSdksCall() {
        val asked = mutableListOf<String>()
        val sdkRule = { message: String -> asked.add(message); false }
        assertFalse(isUrnetworkUnreachable(UnknownHostException("api.example"), sdkRule))
        assertFalse(isUrnetworkUnreachable(SocketTimeoutException(), sdkRule))
        assertFalse(isUrnetworkUnreachable(ConnectException(), sdkRule))
        assertFalse(isUrnetworkUnreachable(NoRouteToHostException(), sdkRule))
        assertEquals(
            listOf(
                "java.net.UnknownHostException: api.example",
                "java.net.SocketTimeoutException",
                "java.net.ConnectException",
                "java.net.NoRouteToHostException",
            ),
            asked,
        )
        assertTrue(isUrnetworkUnreachable(SocketTimeoutException("timeout")) { true })
    }

    @Test
    fun aFailuresTextIsTheSdksCall() {
        val asked = mutableListOf<String>()
        assertTrue(isUrnetworkUnreachable(Exception("http request attempts exhausted")) { asked.add(it); true })
        assertFalse(isUrnetworkUnreachable(Exception("401 Unauthorized: invalid jwt")) { asked.add(it); false })
        assertEquals(listOf("http request attempts exhausted", "401 Unauthorized: invalid jwt"), asked)
    }

    @Test
    fun noFailureAndNoTextAreNotUnreachable() {
        assertFalse(isUrnetworkUnreachable(null) { true })
        assertFalse(isUrnetworkUnreachable(Exception()) { true })
    }

    // Signed in, Windows alone raised the prompt as the window's diagnosis
    // turned to the platform being unreachable; android now does too, by the
    // sdk's rule (CantReachOnStall), reported by MainService's window status
    // listener.
    @Test
    fun aSignedInStallRaisesThePromptByTheSdksRule() {
        val asked = mutableListOf<Pair<String, String>>()
        val prompt = CantReachPrompt(
            cantReachText = { false },
            cantReachOnStall = { previous, current ->
                asked.add(previous to current)
                current == "platform-unreachable" && previous != current
            },
        )

        assertFalse(prompt.reportStall("", "evaluating"))
        assertFalse(prompt.visible.value)

        assertTrue(prompt.reportStall("evaluating", "platform-unreachable"))
        assertTrue(prompt.visible.value)

        prompt.dismiss()
        assertFalse(prompt.reportStall("platform-unreachable", "platform-unreachable"))
        assertFalse(prompt.visible.value)

        assertEquals(
            listOf("" to "evaluating", "evaluating" to "platform-unreachable", "platform-unreachable" to "platform-unreachable"),
            asked,
        )
    }

    @Test
    fun theWindowStatusListenerReportsEveryDiagnosisToThePrompt() {
        val file = listOf("", "app/", "app/app/")
            .map { java.io.File(it + "src/main/java/com/bringyour/network/MainService.kt") }
            .firstOrNull { it.isFile }
        assertTrue("MainService.kt not found", file != null)
        val source = file!!.readText()
        val update = source.substring(
            source.indexOf("fun updateWindowStatus(windowStatus: WindowStatus) {"),
            source.indexOf("windowStatusChangeSub = currentDevice.addWindowStatusChangeListener"),
        )
        assertTrue(update, update.contains("CantReachPrompt.shared.reportStall(windowStallReason, stallReason)"))
        assertTrue(update, update.contains("windowStallReason = stallReason"))
        // a new device starts from no diagnosis
        assertTrue(source.contains("windowStatusChangeSub = null\n        windowStallReason = \"\""))
    }

    @Test
    fun promptShowsOnlyForUnreachableAndNeverChangesTheRegion() {
        val store = FakeStore(setOf("cn", "ru", "tr"), value = "ru")
        // the sdk's rule, faked: only a resolver failure is unreachable here
        val prompt = CantReachPrompt(cantReachText = { it.contains("no such host") })

        assertFalse(prompt.report(Exception("401 Unauthorized: invalid jwt")))
        assertFalse(prompt.visible.value)
        assertFalse(prompt.report(null))
        assertFalse(prompt.visible.value)

        assertTrue(prompt.report(Exception("dial tcp: lookup api.example: no such host")))
        assertTrue(prompt.visible.value)

        prompt.dismiss()
        assertFalse(prompt.visible.value)

        assertEquals("ru", store.value)
        assertEquals(0, store.setCount)
    }
}
