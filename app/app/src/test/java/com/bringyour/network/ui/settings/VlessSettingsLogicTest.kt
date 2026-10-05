package com.bringyour.network.ui.settings

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The VLESS settings editor's decisions: which fields show for each transport
 * and security, how the form maps to the network space's settings and back,
 * what a pasted link does, and the message of every sdk error id.
 */
class VlessSettingsLogicTest {

    // a reality server as a vless:// link reads, every field it uses set
    private val reality = VlessSettingsValues(
        enabled = true,
        name = "home",
        address = "vless.example",
        port = 443,
        id = "6f0b4d2e-3c9a-4e1b-9a6d-2b8f1c7e5a90",
        flow = VLESS_FLOW_VISION,
        network = VLESS_NETWORK_TCP,
        security = VLESS_SECURITY_REALITY,
        serverName = "www.example.com",
        fingerprint = "chrome",
        publicKey = "jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0",
        shortId = "0123abcd",
        spiderX = "/crawl",
    )

    // the 12 error ids the sdk defines (vless_settings.go, VLESS_APP_SPEC)
    private val sdkErrorIds = listOf(
        "vless_error_link_invalid",
        "vless_error_link_unsupported",
        "vless_error_address_invalid",
        "vless_error_port_invalid",
        "vless_error_id_invalid",
        "vless_error_network_unsupported",
        "vless_error_security_unsupported",
        "vless_error_flow_invalid",
        "vless_error_server_name_required",
        "vless_error_fingerprint_unsupported",
        "vless_error_public_key_invalid",
        "vless_error_short_id_invalid",
    )

    private data class Shown(
        val flow: Boolean,
        val serverName: Boolean,
        val tlsOptions: Boolean,
        val realityKeys: Boolean,
        val httpOptions: Boolean,
    )

    private fun shown(form: VlessForm) = Shown(
        flow = form.showsFlow,
        serverName = form.showsServerName,
        tlsOptions = form.showsTlsOptions,
        realityKeys = form.showsRealityKeys,
        httpOptions = form.showsHttpOptions,
    )

    @Test
    fun aNewFormIsTheSdksNewForm() {
        // Sdk.newVlessSettings(): reality over raw tcp with the vision flow and
        // a chrome hello on 443, not enabled
        val form = VlessForm()

        assertFalse(form.enabled)
        assertEquals("443", form.port)
        assertEquals(VLESS_NETWORK_TCP, form.network)
        assertEquals(VLESS_SECURITY_REALITY, form.security)
        assertEquals(VLESS_FLOW_VISION, form.flow)
        assertEquals("chrome", form.fingerprint)
    }

    @Test
    fun eachTransportAndSecurityShowsOnlyTheFieldsThatApply() {
        val expected = mapOf(
            (VLESS_NETWORK_TCP to VLESS_SECURITY_NONE) to Shown(
                flow = false, serverName = false, tlsOptions = false, realityKeys = false, httpOptions = false,
            ),
            (VLESS_NETWORK_TCP to VLESS_SECURITY_TLS) to Shown(
                flow = true, serverName = true, tlsOptions = true, realityKeys = false, httpOptions = false,
            ),
            (VLESS_NETWORK_TCP to VLESS_SECURITY_REALITY) to Shown(
                flow = true, serverName = true, tlsOptions = false, realityKeys = true, httpOptions = false,
            ),
            (VLESS_NETWORK_WS to VLESS_SECURITY_NONE) to Shown(
                flow = false, serverName = false, tlsOptions = false, realityKeys = false, httpOptions = true,
            ),
            (VLESS_NETWORK_WS to VLESS_SECURITY_TLS) to Shown(
                flow = false, serverName = true, tlsOptions = true, realityKeys = false, httpOptions = true,
            ),
            (VLESS_NETWORK_WS to VLESS_SECURITY_REALITY) to Shown(
                flow = false, serverName = true, tlsOptions = false, realityKeys = true, httpOptions = true,
            ),
            (VLESS_NETWORK_HTTP_UPGRADE to VLESS_SECURITY_NONE) to Shown(
                flow = false, serverName = false, tlsOptions = false, realityKeys = false, httpOptions = true,
            ),
            (VLESS_NETWORK_HTTP_UPGRADE to VLESS_SECURITY_TLS) to Shown(
                flow = false, serverName = true, tlsOptions = true, realityKeys = false, httpOptions = true,
            ),
            (VLESS_NETWORK_HTTP_UPGRADE to VLESS_SECURITY_REALITY) to Shown(
                flow = false, serverName = true, tlsOptions = false, realityKeys = true, httpOptions = true,
            ),
        )

        // every pair the pickers can make is pinned
        for (network in VLESS_NETWORKS) {
            for (security in VLESS_SECURITIES) {
                assertTrue("$network/$security", expected.containsKey(network to security))
            }
        }
        for ((pair, fields) in expected) {
            val (network, security) = pair
            assertEquals(
                "$network/$security",
                fields,
                shown(VlessForm(network = network, security = security)),
            )
        }
    }

    @Test
    fun aSpacesSettingsRoundTripThroughTheForm() {
        assertEquals(reality, vlessFormFrom(reality).toSettingsValues())

        val tlsWebSocket = VlessSettingsValues(
            enabled = true,
            address = "203.0.113.7",
            port = 8443,
            id = "6f0b4d2e3c9a4e1b9a6d2b8f1c7e5a90",
            network = VLESS_NETWORK_WS,
            security = VLESS_SECURITY_TLS,
            serverName = "cdn.example",
            fingerprint = "",
            alpn = "h2,http/1.1",
            allowInsecure = true,
            path = "/ray?ed=2048",
            host = "cdn.example",
        )
        assertEquals(tlsWebSocket, vlessFormFrom(tlsWebSocket).toSettingsValues())

        // off settings are kept as typed, so they round-trip too
        val off = VlessSettingsValues(
            enabled = false,
            address = "192.0.2.1",
            port = 80,
            id = "user",
            network = VLESS_NETWORK_HTTP_UPGRADE,
            security = VLESS_SECURITY_NONE,
            path = "/up",
        )
        assertEquals(off, vlessFormFrom(off).toSettingsValues())
    }

    @Test
    fun theSpiderPathIsKeptThoughTheFormNeverShowsIt() {
        val edited = vlessFormFrom(reality).copy(name = "office", address = "vless2.example")

        val saved = edited.toSettingsValues()

        assertEquals("/crawl", saved.spiderX)
        assertEquals("office", saved.name)
        assertEquals("vless2.example", saved.address)
    }

    @Test
    fun aHiddenFlowIsSavedAsNone() {
        // vision over a websocket, or with no security, is what the sdk refuses
        // as vless_error_flow_invalid -- an error about a picker the user
        // cannot see
        val webSocket = vlessFormFrom(reality).copy(network = VLESS_NETWORK_WS)
        assertFalse(webSocket.showsFlow)
        assertEquals(VLESS_FLOW_NONE, webSocket.toSettingsValues().flow)

        val noSecurity = vlessFormFrom(reality).copy(security = VLESS_SECURITY_NONE)
        assertFalse(noSecurity.showsFlow)
        assertEquals(VLESS_FLOW_NONE, noSecurity.toSettingsValues().flow)

        // the form keeps the pick, so going back to raw tcp shows it again
        assertEquals(VLESS_FLOW_VISION, webSocket.flow)
        assertEquals(
            VLESS_FLOW_VISION,
            webSocket.copy(network = VLESS_NETWORK_TCP).toSettingsValues().flow,
        )
        // and a shown flow saves as picked
        assertEquals(VLESS_FLOW_VISION, vlessFormFrom(reality).toSettingsValues().flow)
        assertEquals(
            VLESS_FLOW_NONE,
            vlessFormFrom(reality).copy(flow = VLESS_FLOW_NONE).toSettingsValues().flow,
        )
    }

    @Test
    fun otherHiddenFieldsAreKeptAsTyped() {
        // the sdk ignores them for the security and transport in force, and
        // switching back finds them again
        val typed = vlessFormFrom(reality).copy(
            alpn = "h2",
            allowInsecure = true,
            path = "/ray",
            host = "cdn.example",
        )

        val saved = typed.copy(security = VLESS_SECURITY_NONE).toSettingsValues()

        assertEquals("www.example.com", saved.serverName)
        assertEquals("chrome", saved.fingerprint)
        assertEquals(reality.publicKey, saved.publicKey)
        assertEquals(reality.shortId, saved.shortId)
        assertEquals("h2", saved.alpn)
        assertTrue(saved.allowInsecure)
        assertEquals("/ray", saved.path)
        assertEquals("cdn.example", saved.host)
    }

    @Test
    fun thePortBoxKeepsDigitsAndAnEmptyBoxIsNoPort() {
        assertEquals("8443", vlessPortText("8a4-4 3"))
        assertEquals("65535", vlessPortText("655350"))
        assertEquals("", vlessPortText("-"))
        // a keyboard's own digits are digits too
        assertEquals("٤٤٣", vlessPortText("٤٤٣"))
        assertEquals(443L, vlessPortValue("٤٤٣"))

        assertEquals(443L, vlessPortValue("443"))
        // the sdk reports both of these as vless_error_port_invalid
        assertEquals(0L, vlessPortValue(""))
        assertEquals(70000L, vlessPortValue("70000"))
        assertEquals(0L, VlessForm(port = "").toSettingsValues().port)

        // no port loads as an empty box, not a 0 to delete first
        assertEquals("", vlessFormFrom(VlessSettingsValues(port = 0)).port)
        assertEquals("8443", vlessFormFrom(VlessSettingsValues(port = 8443)).port)
    }

    @Test
    fun settingsThatLeaveOutTheTransportAndSecurityReadAsRawTcpWithNone() {
        val form = vlessFormFrom(VlessSettingsValues(address = "192.0.2.1"))

        assertEquals(VLESS_NETWORK_TCP, form.network)
        assertEquals(VLESS_SECURITY_NONE, form.security)
    }

    @Test
    fun aPastedLinkReplacesTheWholeFormOrSaysWhyNot() {
        val filled = vlessLinkOutcome("", reality)

        assertEquals(VlessLinkOutcome.Fill(vlessFormFrom(reality)), filled)
        // the sdk reads a link's settings as enabled, and the form takes that
        assertTrue((filled as VlessLinkOutcome.Fill).form.enabled)

        assertEquals(
            VlessLinkOutcome.Error(VLESS_ERROR_LINK_UNSUPPORTED),
            vlessLinkOutcome(VLESS_ERROR_LINK_UNSUPPORTED, null),
        )
        // an error wins over settings
        assertEquals(
            VlessLinkOutcome.Error(VLESS_ERROR_PUBLIC_KEY_INVALID),
            vlessLinkOutcome(VLESS_ERROR_PUBLIC_KEY_INVALID, reality),
        )
        // and an answer with neither reads as an invalid link
        assertEquals(
            VlessLinkOutcome.Error(VLESS_ERROR_LINK_INVALID),
            vlessLinkOutcome("", null),
        )
    }

    @Test
    fun underSaveTheSavesAnswerStandsElseTheValidationOfEnabledSettings() {
        assertEquals(
            VLESS_ERROR_ID_INVALID,
            vlessFormErrorId(enabled = true, validationErrorId = VLESS_ERROR_ID_INVALID, saveErrorId = ""),
        )
        // off settings save as typed, so their problems are not errors yet
        assertEquals(
            "",
            vlessFormErrorId(enabled = false, validationErrorId = VLESS_ERROR_ID_INVALID, saveErrorId = ""),
        )
        assertEquals(
            VLESS_ERROR_PORT_INVALID,
            vlessFormErrorId(enabled = true, validationErrorId = VLESS_ERROR_ID_INVALID, saveErrorId = VLESS_ERROR_PORT_INVALID),
        )
        assertEquals(
            VLESS_ERROR_PORT_INVALID,
            vlessFormErrorId(enabled = false, validationErrorId = "", saveErrorId = VLESS_ERROR_PORT_INVALID),
        )
        assertEquals("", vlessFormErrorId(enabled = true, validationErrorId = "", saveErrorId = ""))
    }

    @Test
    fun everySdkErrorIdShowsTheMessageItNames() {
        // each id is the key of its message, so its resource is found by name
        for (errorId in sdkErrorIds) {
            val named = R.string::class.java.getField(errorId).getInt(null)
            assertEquals(errorId, named, vlessErrorResId(errorId))
        }
        // and no two ids share a message
        assertEquals(sdkErrorIds.size, sdkErrorIds.map { vlessErrorResId(it) }.toSet().size)
    }

    @Test
    fun theErrorIdsAreTheSdksConstants() {
        // Sdk.VlessError* are compile-time constants, which kotlin inlines: this
        // reads no field of the gomobile class (whose initializer loads gojni),
        // and a renamed or removed constant breaks the build
        assertEquals(
            listOf(
                Sdk.VlessErrorLinkInvalid,
                Sdk.VlessErrorLinkUnsupported,
                Sdk.VlessErrorAddressInvalid,
                Sdk.VlessErrorPortInvalid,
                Sdk.VlessErrorIdInvalid,
                Sdk.VlessErrorNetworkUnsupported,
                Sdk.VlessErrorSecurityUnsupported,
                Sdk.VlessErrorFlowInvalid,
                Sdk.VlessErrorServerNameRequired,
                Sdk.VlessErrorFingerprintUnsupported,
                Sdk.VlessErrorPublicKeyInvalid,
                Sdk.VlessErrorShortIdInvalid,
            ),
            sdkErrorIds,
        )
        assertEquals(
            sdkErrorIds,
            listOf(
                VLESS_ERROR_LINK_INVALID,
                VLESS_ERROR_LINK_UNSUPPORTED,
                VLESS_ERROR_ADDRESS_INVALID,
                VLESS_ERROR_PORT_INVALID,
                VLESS_ERROR_ID_INVALID,
                VLESS_ERROR_NETWORK_UNSUPPORTED,
                VLESS_ERROR_SECURITY_UNSUPPORTED,
                VLESS_ERROR_FLOW_INVALID,
                VLESS_ERROR_SERVER_NAME_REQUIRED,
                VLESS_ERROR_FINGERPRINT_UNSUPPORTED,
                VLESS_ERROR_PUBLIC_KEY_INVALID,
                VLESS_ERROR_SHORT_ID_INVALID,
            ),
        )
        // and the sdk has no error id this build does not map. Listing the
        // class's fields does not initialize it.
        val sdkConstants = Sdk::class.java.declaredFields
            .map { it.name }
            .filter { it.startsWith("VlessError") }
        assertEquals(sdkErrorIds.size, sdkConstants.size)
    }

    @Test
    fun anUnknownErrorIdReadsAsAnInvalidLink() {
        assertEquals(R.string.vless_error_link_invalid, vlessErrorResId(""))
        assertEquals(R.string.vless_error_link_invalid, vlessErrorResId("vless_error_later"))
        // connect's bare code is not an sdk id
        assertEquals(R.string.vless_error_link_invalid, vlessErrorResId("port_invalid"))
    }

    @Test
    fun theOptionsAreTheSdks() {
        // Sdk.vlessNetworks(), vlessSecurities(), vlessFlows() and
        // vlessFingerprints() (vless_settings_ui.go)
        assertEquals(listOf("tcp", "ws", "httpupgrade"), VLESS_NETWORKS)
        assertEquals(listOf("none", "tls", "reality"), VLESS_SECURITIES)
        assertEquals(listOf("", "xtls-rprx-vision"), VLESS_FLOWS)
        assertEquals(
            listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized"),
            VLESS_FINGERPRINTS,
        )
    }

    @Test
    fun everyOptionShowsItsLabel() {
        assertEquals(
            listOf(R.string.vless_network_tcp, R.string.vless_network_ws, R.string.vless_network_httpupgrade),
            VLESS_NETWORKS.map { vlessNetworkLabelResId(it) },
        )
        assertEquals(
            listOf(R.string.none, R.string.vless_security_tls, R.string.vless_security_reality),
            VLESS_SECURITIES.map { vlessSecurityLabelResId(it) },
        )
        assertEquals(
            listOf(R.string.none, R.string.vless_flow_vision),
            VLESS_FLOWS.map { vlessFlowLabelResId(it) },
        )
        // a fingerprint shows its own value; only the empty one is named
        assertEquals(R.string.none, vlessFingerprintLabelResId(""))
        for (fingerprint in VLESS_FINGERPRINTS.filter { it.isNotEmpty() }) {
            assertNull(fingerprint, vlessFingerprintLabelResId(fingerprint))
        }
        // an option this build does not know shows its value
        assertNull(vlessNetworkLabelResId("grpc"))
        assertNull(vlessSecurityLabelResId("xtls"))
        assertNull(vlessFlowLabelResId("xtls-rprx-direct"))
    }
}
