package com.bringyour.network.ui.connection_region

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bringyour.sdk.Sdk
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

/**
 * The sdk's one rule for a failure's text (sdk cant_reach.go IsCantReachError):
 * whether it means URnetwork could not be reached at all, as against an http
 * status or an auth or validation answer. Every app asks the sdk, so the same
 * failure raises the prompt on every platform (support inbox 7956, 7973).
 */
internal fun sdkIsCantReachError(message: String): Boolean = Sdk.isCantReachError(message)

/**
 * The sdk's one rule for a signed-in connect (sdk cant_reach.go
 * CantReachOnStall): whether the window's diagnosis has just turned to the
 * platform being unreachable (WindowStatus.stallReason).
 */
internal fun sdkCantReachOnStall(previousStallReason: String, stallReason: String): Boolean =
    Sdk.cantReachOnStall(previousStallReason, stallReason)

/**
 * Whether an api failure raises the prompt: the app could not reach URnetwork
 * (a network level failure), as opposed to the platform answering with an auth
 * or validation error, and not just once. The sdk decides (`cantReachText`,
 * [sdkIsCantReachError]), Android's own transport exceptions included, by
 * their text, which names their class: one failed call is not an outage, and
 * failures that last the sdk's hold are.
 */
fun isUrnetworkUnreachable(
    err: Throwable?,
    cantReachText: (String) -> Boolean = ::sdkIsCantReachError,
): Boolean {
    if (err == null) {
        return false
    }
    when (err) {
        is UnknownHostException,
        is SocketTimeoutException,
        is ConnectException,
        is NoRouteToHostException -> return cantReachText(err.toString())
    }
    val message = err.message ?: return false
    return cantReachText(message)
}

/**
 * "Can't reach URnetwork — choose where you're connecting from". Api call sites
 * report failures, and a signed-in connect reports its window's diagnosis; the
 * prompt opens the connection region picker. It never changes the region
 * itself. The rules are the sdk's (`cantReachText`, `cantReachOnStall`).
 */
class CantReachPrompt(
    private val cantReachText: (String) -> Boolean = ::sdkIsCantReachError,
    private val cantReachOnStall: (String, String) -> Boolean = ::sdkCantReachOnStall,
) {
    private val visibleState = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = visibleState.asStateFlow()

    /**
     * Shows the prompt when `err` is a network-level failure. True when shown.
     */
    fun report(err: Throwable?): Boolean {
        if (!isUrnetworkUnreachable(err, cantReachText)) {
            return false
        }
        visibleState.value = true
        return true
    }

    /**
     * A signed-in connect's window report: shows the prompt when its
     * diagnosis has just turned to the platform being unreachable, once per
     * turn, as Windows does. `previousStallReason` is the diagnosis of the
     * report before. True when shown.
     */
    fun reportStall(previousStallReason: String, stallReason: String): Boolean {
        if (!cantReachOnStall(previousStallReason, stallReason)) {
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
