package com.bringyour.network

internal enum class VpnNotificationStatus {
    ACTIVE,
    CONNECTING,
    CONNECTED,
    /** The sdk's CONNECT_FAILED: no provider could be added, the connection keeps trying. */
    CONNECT_FAILED,
    /** The window holds its providers, but the VPN tunnel is not up. */
    TUNNEL_DISCONNECTED,
}

internal data class VpnNotificationPresentation(
    val status: VpnNotificationStatus,
    val locationLabel: String?,
    val providerCount: Int,
    val showDisconnect: Boolean,
)

/**
 * Pure projection of SDK state into the foreground notification. Connected
 * only when the connect window holds its providers and the tunnel is up, as
 * the connect screen and the quick connect surfaces say it
 * (quickConnectStatus): a connection forming, one the sdk gave up on
 * (CONNECT_FAILED) and one whose tunnel is down each say so.
 */
internal fun vpnNotificationPresentation(
    connectRequested: Boolean,
    systemAlwaysOn: Boolean,
    providerCount: Long,
    minSatisfied: Boolean,
    failed: Boolean,
    locationProviderCount: Int,
    tunnelStarted: Boolean,
    locationName: String?,
    city: String?,
    region: String?,
    country: String?,
    bestAvailableLabel: String,
): VpnNotificationPresentation {
    val safeProviderCount = providerCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val locationLabel = if (connectRequested) {
        sequenceOf(locationName, city, region, country)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?: bestAvailableLabel
    } else {
        null
    }
    val status = when (
        com.bringyour.network.ui.connect.quickConnectStatus(
            configured = true,
            requested = connectRequested,
            startWaiting = false,
            connectQueued = false,
            minSatisfied = minSatisfied,
            failed = failed,
            addedProviderCount = safeProviderCount,
            locationProviderCount = locationProviderCount,
            tunnelStarted = tunnelStarted,
        )
    ) {
        com.bringyour.network.ui.connect.QuickConnectStatus.NOT_SIGNED_IN,
        com.bringyour.network.ui.connect.QuickConnectStatus.DISCONNECTED -> VpnNotificationStatus.ACTIVE
        com.bringyour.network.ui.connect.QuickConnectStatus.CONNECTING -> VpnNotificationStatus.CONNECTING
        com.bringyour.network.ui.connect.QuickConnectStatus.CONNECTED -> VpnNotificationStatus.CONNECTED
        com.bringyour.network.ui.connect.QuickConnectStatus.CONNECT_FAILED -> VpnNotificationStatus.CONNECT_FAILED
        com.bringyour.network.ui.connect.QuickConnectStatus.TUNNEL_DISCONNECTED -> VpnNotificationStatus.TUNNEL_DISCONNECTED
    }
    return VpnNotificationPresentation(
        status = status,
        locationLabel = locationLabel,
        providerCount = safeProviderCount,
        // Android Always-on policy would immediately reconnect, so presenting
        // a button that cannot stick would be misleading.
        showDisconnect = connectRequested && !systemAlwaysOn,
    )
}
