package com.bringyour.network.ui.connection_region

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The connection region is the country the user says they connect from (the
 * ingress country). It only picks how the app resolves URnetwork's own names
 * (ingress DNS); the tunnel's traffic DNS settings (egress) never read it.
 * Stored as "" for "Other / Undisclosed".
 */
const val UNDISCLOSED_REGION = ""

/**
 * Where the connection region is kept: the network space values, local only.
 * The login control and the settings picker use the same store.
 */
interface ConnectionRegionStore {
    fun get(): String

    /**
     * Stores the region and returns the stored value; an unknown code is
     * stored as [UNDISCLOSED_REGION]
     */
    fun set(countryCode: String): String
}

/**
 * The regions with a recommendation and the resolvers each selects
 * (sdk `IngressCountryCodes`, `IngressDohUrls`, `IngressPlainDnsServers`)
 */
interface ConnectionRegionCatalog {
    fun countryCodes(): List<String>
    fun dohUrls(countryCode: String): List<String>
    fun plainDnsServers(countryCode: String): List<String>
}

/**
 * One picker row. [displayName] is null for "Other / Undisclosed", which the
 * ui shows from its string resource.
 */
data class ConnectionRegionOption(
    val countryCode: String,
    val displayName: String?,
) {
    val isUndisclosed: Boolean
        get() = countryCode == UNDISCLOSED_REGION
}

/**
 * The resolvers a region selects: encrypted DoH ahead of the defaults, and an
 * unencrypted last resort used only for URnetwork names. Both empty means the
 * default resolvers.
 */
data class ConnectionRegionResolvers(
    val dohUrls: List<String>,
    val plainDnsServers: List<String>,
) {
    val usesDefaults: Boolean
        get() = dohUrls.isEmpty() && plainDnsServers.isEmpty()
}

/**
 * Picker model shared by the login control and the dns settings screen.
 * Selecting writes the store, which applies in place from the next lookup.
 */
class ConnectionRegionModel(
    private val store: ConnectionRegionStore,
    private val catalog: ConnectionRegionCatalog,
    private val displayName: (String) -> String,
) {
    var selected by mutableStateOf(store.get())
        private set

    fun refresh() {
        selected = store.get()
    }

    /**
     * "Other / Undisclosed" first, then exactly the regions with a
     * recommendation, by localized name
     */
    fun options(): List<ConnectionRegionOption> {
        val regions = catalog.countryCodes()
            .filter { it != UNDISCLOSED_REGION }
            .distinct()
            .map { ConnectionRegionOption(it, displayName(it)) }
            .sortedBy { it.displayName }
        return listOf(ConnectionRegionOption(UNDISCLOSED_REGION, null)) + regions
    }

    fun resolvers(countryCode: String = selected): ConnectionRegionResolvers {
        if (countryCode == UNDISCLOSED_REGION) {
            return ConnectionRegionResolvers(listOf(), listOf())
        }
        return ConnectionRegionResolvers(
            dohUrls = catalog.dohUrls(countryCode),
            plainDnsServers = catalog.plainDnsServers(countryCode),
        )
    }

    fun select(countryCode: String): String {
        selected = store.set(countryCode)
        return selected
    }
}

private val httpStatusPrefix = Regex("^\\d{3} ")

private val unreachableMarkers = listOf(
    "no such host",
    "dial tcp",
    "dial udp",
    "i/o timeout",
    "connection refused",
    "connection reset",
    "network is unreachable",
    "no route to host",
    "context deadline exceeded",
    "tls handshake",
    "http request failed",
    "unable to resolve host",
    "failed to connect",
)

/**
 * Whether an api failure means the app could not reach URnetwork (a network
 * level failure), as opposed to the platform answering with an auth or
 * validation error. Validation errors come back in the result, not as the
 * error, and an http status error means the platform was reached.
 */
fun isUrnetworkUnreachable(err: Throwable?): Boolean {
    if (err == null) {
        return false
    }
    when (err) {
        is UnknownHostException,
        is SocketTimeoutException,
        is ConnectException,
        is NoRouteToHostException -> return true
    }
    val message = err.message?.trim()?.lowercase() ?: return false
    if (httpStatusPrefix.containsMatchIn(message)) {
        return false
    }
    return unreachableMarkers.any { message.contains(it) }
}

/**
 * "Can't reach URnetwork — choose where you're connecting from". Api call sites
 * report failures; the prompt opens the connection region picker. It never
 * changes the region itself.
 */
class CantReachPrompt {
    private val visibleState = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = visibleState.asStateFlow()

    /**
     * Shows the prompt when `err` is a network-level failure. True when shown.
     */
    fun report(err: Throwable?): Boolean {
        if (!isUrnetworkUnreachable(err)) {
            return false
        }
        visibleState.value = true
        return true
    }

    fun dismiss() {
        visibleState.value = false
    }

    companion object {
        /**
         * The app-wide prompt, shown by the login and main nav hosts
         */
        val shared = CantReachPrompt()
    }
}
