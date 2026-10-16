package com.bringyour.network.ui.connect

/**
 * What the Quick Settings tile, the dashboard widget and the VPN notification
 * say about the connection, in the connect screen's words
 * (ConnectStatusIndicator): connected only when the connect window holds its
 * providers and the tunnel is up.
 *
 * They read the device, which has no controller's status, and they said
 * Connected for every requested connection: one forming, one the sdk gave up
 * on (CONNECT_FAILED, which can last indefinitely) and one whose tunnel is
 * down. The Apple app's menu bar said "✓ Connected" for the same states
 * (ba2a36d3, e46555b7, 64cd2c1b). Each state is derived here as the sdk's
 * controller derives it (gridConnectionStatus).
 */
enum class QuickConnectStatus {
    /** No signed-in device: the surfaces offer no connect or disconnect, only the app. */
    NOT_SIGNED_IN,
    DISCONNECTED,
    /** A start waits on its balance, a connect is queued, or the window is forming. */
    CONNECTING,
    /** The window holds its providers and the tunnel is up. */
    CONNECTED,
    /** The window passed both of its outcome deadlines with no provider added: the sdk's CONNECT_FAILED. */
    CONNECT_FAILED,
    /** The window holds its providers, but the VPN tunnel is not up. */
    TUNNEL_DISCONNECTED,
}

/**
 * The window holds its providers: the sdk controller's CONNECTED. The window
 * is satisfied, or the location's own providers are all added (a location
 * with fewer providers than the window's minimum).
 */
internal fun windowConnected(minSatisfied: Boolean, addedProviderCount: Int, locationProviderCount: Int): Boolean =
    minSatisfied ||
        (0 < locationProviderCount && 0 < addedProviderCount && locationProviderCount <= addedProviderCount)

/**
 * The status, from the device and the app's own state: whether it is signed
 * in (`configured`), the connection the user asked for (`requested`, the
 * newest queued command else the device's connectEnabled), a start waiting
 * on its balance, a connect still queued, the device's window status, the
 * location's provider count and whether the tunnel is up.
 */
internal fun quickConnectStatus(
    configured: Boolean,
    requested: Boolean,
    startWaiting: Boolean,
    connectQueued: Boolean,
    minSatisfied: Boolean,
    failed: Boolean,
    addedProviderCount: Int,
    locationProviderCount: Int,
    tunnelStarted: Boolean,
): QuickConnectStatus = when {
    !configured -> QuickConnectStatus.NOT_SIGNED_IN
    // the tap's state: what the window says belongs to the connection before it
    startWaiting || connectQueued -> QuickConnectStatus.CONNECTING
    !requested -> QuickConnectStatus.DISCONNECTED
    windowConnected(minSatisfied, addedProviderCount, locationProviderCount) ->
        if (tunnelStarted) QuickConnectStatus.CONNECTED else QuickConnectStatus.TUNNEL_DISCONNECTED
    // checked after the connected cases: a recovered window's providers win
    failed -> QuickConnectStatus.CONNECT_FAILED
    else -> QuickConnectStatus.CONNECTING
}

/** The status line for `status`, as the connect screen says it. */
internal fun quickConnectStatusText(status: QuickConnectStatus): Int = when (status) {
    QuickConnectStatus.NOT_SIGNED_IN -> com.bringyour.network.R.string.widget_not_signed_in
    QuickConnectStatus.DISCONNECTED -> com.bringyour.network.R.string.tile_status_disconnected
    QuickConnectStatus.CONNECTING -> com.bringyour.network.R.string.connecting_status_indicator
    QuickConnectStatus.CONNECTED -> com.bringyour.network.R.string.tile_status_connected
    QuickConnectStatus.CONNECT_FAILED -> com.bringyour.network.R.string.conn_failed
    QuickConnectStatus.TUNNEL_DISCONNECTED -> com.bringyour.network.R.string.reconnect_tunnel_status_indicator
}
