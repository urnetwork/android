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
    /** The slow internet content in place of the connected content, null for the normal content. */
    val slowInternet: SlowInternetPresentation? = null,
)

/**
 * What the connected notification says about a slow connection (apple/DELAY.md
 * §7.2): the same notification, channel and Disconnect action, its content
 * swapped, never a new notification.
 */
internal sealed interface SlowInternetPresentation {
    /**
     * Slow: the small icon is ic_tunnel_<level> (1 to 4), the title "Slow
     * internet" or, for [fallback], "Using a slower fallback connection", and
     * the expanded text the upload and download lines of [delay].
     */
    data class Slow(
        val level: Int,
        val fallback: Boolean,
        val delay: TransportDelayUi,
    ) : SlowInternetPresentation

    /** "Internet back to normal", for the few seconds before the normal content returns. */
    data object Recovered : SlowInternetPresentation
}

/**
 * Pure projection of SDK state into the foreground notification. Connected
 * only when the connect window holds its providers and the tunnel is up, as
 * the connect screen and the quick connect surfaces say it
 * (quickConnectStatus): a connection forming, one the sdk gave up on
 * (CONNECT_FAILED) and one whose tunnel is down each say so.
 *
 * A connected notification says when the connection is slow, as the slow
 * internet dwell decides ([slowInternetPhase], with the reading it shows,
 * SlowInternetNotice). Every other status keeps its own content: it is more
 * than a slow connection, and "still connected" would not be true of it.
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
    slowInternetPhase: SlowInternetNoticePhase = SlowInternetNoticePhase.NONE,
    transportDelay: TransportDelayUi = TransportDelayUi.None,
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
    val slowInternet = if (status == VpnNotificationStatus.CONNECTED) {
        when (slowInternetPhase) {
            SlowInternetNoticePhase.NONE -> null
            SlowInternetNoticePhase.SLOW -> SlowInternetPresentation.Slow(
                // the dwell shows a slow reading; a zero one still gets the first icon
                level = com.bringyour.network.ui.components.tunnelLevel(transportDelay.quality).coerceAtLeast(1),
                fallback = transportDelay.fallback,
                delay = transportDelay,
            )
            SlowInternetNoticePhase.RECOVERED -> SlowInternetPresentation.Recovered
        }
    } else {
        null
    }
    return VpnNotificationPresentation(
        status = status,
        locationLabel = locationLabel,
        providerCount = safeProviderCount,
        // Android Always-on policy would immediately reconnect, so presenting
        // a button that cannot stick would be misleading.
        showDisconnect = connectRequested && !systemAlwaysOn,
        slowInternet = slowInternet,
    )
}
