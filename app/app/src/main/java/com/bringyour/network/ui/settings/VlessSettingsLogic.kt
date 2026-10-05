package com.bringyour.network.ui.settings

import androidx.annotation.StringRes
import com.bringyour.network.R

/**
 * The plain shapes and decisions of the VLESS settings editor (Account >
 * Settings > VLESS, and the login screen's network settings): the form, which
 * fields it shows, how it maps to the network space's VLESS settings, and the
 * messages of the sdk's error ids.
 *
 * The sdk owns the rules: validation, the share link and the write all live in
 * it (vless_settings.go), so nothing here validates a server or parses a link.
 * What lives here is what the screen does with the sdk's answers, kept off the
 * gomobile classes so it runs in a JVM test. The view model converts
 * [VlessSettingsValues] to and from `com.bringyour.sdk.VlessSettings`.
 */

// The option values of connect's VLESS configuration (connect vless.go), as
// literals so this file stays off the native class's initializer.
const val VLESS_NETWORK_TCP = "tcp"
const val VLESS_NETWORK_WS = "ws"
const val VLESS_NETWORK_HTTP_UPGRADE = "httpupgrade"

const val VLESS_SECURITY_NONE = "none"
const val VLESS_SECURITY_TLS = "tls"
const val VLESS_SECURITY_REALITY = "reality"

const val VLESS_FLOW_NONE = ""
const val VLESS_FLOW_VISION = "xtls-rprx-vision"

// The option lists the sdk offers (Sdk.vlessNetworks(), vlessSecurities(),
// vlessFlows(), vlessFingerprints()). The screen takes the sdk's lists; these
// are what it falls back to.
val VLESS_NETWORKS = listOf(VLESS_NETWORK_TCP, VLESS_NETWORK_WS, VLESS_NETWORK_HTTP_UPGRADE)
val VLESS_SECURITIES = listOf(VLESS_SECURITY_NONE, VLESS_SECURITY_TLS, VLESS_SECURITY_REALITY)
val VLESS_FLOWS = listOf(VLESS_FLOW_NONE, VLESS_FLOW_VISION)
// the empty fingerprint is the Go tls client for tls, and chrome for reality
val VLESS_FINGERPRINTS = listOf(
    "", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized",
)

// The sdk's error ids (Sdk.VlessError*), as literals for the same reason. Each
// id is the key of its message.
const val VLESS_ERROR_LINK_INVALID = "vless_error_link_invalid"
const val VLESS_ERROR_LINK_UNSUPPORTED = "vless_error_link_unsupported"
const val VLESS_ERROR_ADDRESS_INVALID = "vless_error_address_invalid"
const val VLESS_ERROR_PORT_INVALID = "vless_error_port_invalid"
const val VLESS_ERROR_ID_INVALID = "vless_error_id_invalid"
const val VLESS_ERROR_NETWORK_UNSUPPORTED = "vless_error_network_unsupported"
const val VLESS_ERROR_SECURITY_UNSUPPORTED = "vless_error_security_unsupported"
const val VLESS_ERROR_FLOW_INVALID = "vless_error_flow_invalid"
const val VLESS_ERROR_SERVER_NAME_REQUIRED = "vless_error_server_name_required"
const val VLESS_ERROR_FINGERPRINT_UNSUPPORTED = "vless_error_fingerprint_unsupported"
const val VLESS_ERROR_PUBLIC_KEY_INVALID = "vless_error_public_key_invalid"
const val VLESS_ERROR_SHORT_ID_INVALID = "vless_error_short_id_invalid"

/**
 * The sdk's `VlessSettings` as plain values: one VLESS server as the network
 * space stores it.
 */
data class VlessSettingsValues(
    val enabled: Boolean = false,
    val name: String = "",
    val address: String = "",
    val port: Long = 0,
    val id: String = "",
    val flow: String = "",
    val network: String = "",
    val security: String = "",
    val serverName: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val path: String = "",
    val host: String = "",
)

/**
 * The editor's form: the settings as the boxes hold them, the port as typed.
 * The defaults are the sdk's new form (`Sdk.newVlessSettings()`), which the
 * screen replaces with the space's settings as soon as they load.
 */
data class VlessForm(
    val enabled: Boolean = false,
    val name: String = "",
    val address: String = "",
    val port: String = "443",
    val id: String = "",
    val flow: String = VLESS_FLOW_VISION,
    val network: String = VLESS_NETWORK_TCP,
    val security: String = VLESS_SECURITY_REALITY,
    val serverName: String = "",
    val fingerprint: String = "chrome",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
    // not edited: kept so the share link round-trips (reality)
    val spiderX: String = "",
    val path: String = "",
    val host: String = "",
) {
    /** The flow picker: vision needs a raw tcp stream under tls or reality. */
    val showsFlow: Boolean
        get() = network == VLESS_NETWORK_TCP && security != VLESS_SECURITY_NONE

    /** The server name and the tls fingerprint: tls and reality. */
    val showsServerName: Boolean
        get() = security != VLESS_SECURITY_NONE

    /** The alpn and the insecure certificate switch: tls only. */
    val showsTlsOptions: Boolean
        get() = security == VLESS_SECURITY_TLS

    /** The public key and the short id: reality only. */
    val showsRealityKeys: Boolean
        get() = security == VLESS_SECURITY_REALITY

    /** The path and the host header: the http transports. */
    val showsHttpOptions: Boolean
        get() = network == VLESS_NETWORK_WS || network == VLESS_NETWORK_HTTP_UPGRADE

    /**
     * The settings the form saves. A field the form hides is kept as it is
     * (the spider path, which is never edited, and whatever was typed for
     * another security or transport, which the sdk ignores), except the flow:
     * a hidden vision flow would fail validation with an error about a picker
     * the user cannot see, so it is saved as none.
     */
    fun toSettingsValues(): VlessSettingsValues = VlessSettingsValues(
        enabled = enabled,
        name = name,
        address = address,
        port = vlessPortValue(port),
        id = id,
        flow = if (showsFlow) flow else VLESS_FLOW_NONE,
        network = network,
        security = security,
        serverName = serverName,
        fingerprint = fingerprint,
        alpn = alpn,
        allowInsecure = allowInsecure,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        path = path,
        host = host,
    )
}

/** The form a space's settings fill in. */
fun vlessFormFrom(values: VlessSettingsValues): VlessForm = VlessForm(
    enabled = values.enabled,
    name = values.name,
    address = values.address,
    // no port shows an empty box rather than a 0 to delete first
    port = if (0 < values.port) values.port.toString() else "",
    id = values.id,
    flow = values.flow,
    // settings that leave these out mean raw tcp with no security, as connect
    // reads them, and the pickers need a value to select
    network = values.network.ifEmpty { VLESS_NETWORK_TCP },
    security = values.security.ifEmpty { VLESS_SECURITY_NONE },
    serverName = values.serverName,
    fingerprint = values.fingerprint,
    alpn = values.alpn,
    allowInsecure = values.allowInsecure,
    publicKey = values.publicKey,
    shortId = values.shortId,
    spiderX = values.spiderX,
    path = values.path,
    host = values.host,
)

/**
 * What the port box keeps of what was typed: digits, at most five. Any script's
 * digits, since a keyboard may type its own.
 */
fun vlessPortText(text: String): String = text.filter { it.isDigit() }.take(5)

/**
 * The port the box says. An empty box is 0, which the sdk reports as
 * `vless_error_port_invalid`, as it does a port above 65535.
 */
fun vlessPortValue(text: String): Long = text.trim().toLongOrNull() ?: 0L

/** What a pasted link does to the form. */
sealed class VlessLinkOutcome {
    /** The link's settings, which replace the whole form. */
    data class Fill(val form: VlessForm) : VlessLinkOutcome()

    /** The link did not read; the error id says why. */
    data class Error(val errorId: String) : VlessLinkOutcome()
}

/**
 * The outcome of `Sdk.parseVlessLink`: its settings (which come back enabled)
 * replace the whole form, or its error id is shown. An answer with neither
 * reads as an invalid link.
 */
fun vlessLinkOutcome(errorId: String, settings: VlessSettingsValues?): VlessLinkOutcome = when {
    errorId.isNotEmpty() -> VlessLinkOutcome.Error(errorId)
    settings == null -> VlessLinkOutcome.Error(VLESS_ERROR_LINK_INVALID)
    else -> VlessLinkOutcome.Fill(vlessFormFrom(settings))
}

/**
 * The error shown under Save. What the last save answered stands until the
 * form changes; otherwise the live validation of enabled settings. Settings
 * that are off save as typed, so their problems are not errors yet.
 */
fun vlessFormErrorId(enabled: Boolean, validationErrorId: String, saveErrorId: String): String =
    saveErrorId.ifEmpty { if (enabled) validationErrorId else "" }

/**
 * The localized message for one of the sdk's error ids. Anything this build
 * does not know reads as an invalid link.
 */
@StringRes
fun vlessErrorResId(errorId: String): Int = when (errorId) {
    VLESS_ERROR_LINK_INVALID -> R.string.vless_error_link_invalid
    VLESS_ERROR_LINK_UNSUPPORTED -> R.string.vless_error_link_unsupported
    VLESS_ERROR_ADDRESS_INVALID -> R.string.vless_error_address_invalid
    VLESS_ERROR_PORT_INVALID -> R.string.vless_error_port_invalid
    VLESS_ERROR_ID_INVALID -> R.string.vless_error_id_invalid
    VLESS_ERROR_NETWORK_UNSUPPORTED -> R.string.vless_error_network_unsupported
    VLESS_ERROR_SECURITY_UNSUPPORTED -> R.string.vless_error_security_unsupported
    VLESS_ERROR_FLOW_INVALID -> R.string.vless_error_flow_invalid
    VLESS_ERROR_SERVER_NAME_REQUIRED -> R.string.vless_error_server_name_required
    VLESS_ERROR_FINGERPRINT_UNSUPPORTED -> R.string.vless_error_fingerprint_unsupported
    VLESS_ERROR_PUBLIC_KEY_INVALID -> R.string.vless_error_public_key_invalid
    VLESS_ERROR_SHORT_ID_INVALID -> R.string.vless_error_short_id_invalid
    else -> R.string.vless_error_link_invalid
}

/** The label of a transport option, or null to show the value itself. */
@StringRes
fun vlessNetworkLabelResId(network: String): Int? = when (network) {
    VLESS_NETWORK_TCP -> R.string.vless_network_tcp
    VLESS_NETWORK_WS -> R.string.vless_network_ws
    VLESS_NETWORK_HTTP_UPGRADE -> R.string.vless_network_httpupgrade
    else -> null
}

/** The label of a security option, or null to show the value itself. */
@StringRes
fun vlessSecurityLabelResId(security: String): Int? = when (security) {
    VLESS_SECURITY_NONE -> R.string.none
    VLESS_SECURITY_TLS -> R.string.vless_security_tls
    VLESS_SECURITY_REALITY -> R.string.vless_security_reality
    else -> null
}

/** The label of a flow option, or null to show the value itself. */
@StringRes
fun vlessFlowLabelResId(flow: String): Int? = when (flow) {
    VLESS_FLOW_NONE -> R.string.none
    VLESS_FLOW_VISION -> R.string.vless_flow_vision
    else -> null
}

/**
 * The label of a fingerprint option: the empty one is none, and every other
 * shows its value (chrome, firefox, ...), which is how VLESS clients name them.
 */
@StringRes
fun vlessFingerprintLabelResId(fingerprint: String): Int? = when (fingerprint) {
    "" -> R.string.none
    else -> null
}
