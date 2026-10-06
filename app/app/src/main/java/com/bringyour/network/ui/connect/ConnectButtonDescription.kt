package com.bringyour.network.ui.connect

import androidx.annotation.StringRes
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ConnectStatus

/**
 * The connect button's name for screen readers in each state, as a string
 * resource, or null while its visible "Tap to connect" names it. Otherwise
 * the button draws only icons, a globe or the provider grid, so without a
 * name it read as an unlabeled button, once connected for one.
 *
 * The order follows what the button shows: the balance states replace
 * everything, a failed connect replaces the grid, and the reconnect warning
 * sits over the grid while connecting or connected.
 */
@StringRes
fun connectButtonDescriptionId(
    status: ConnectStatus,
    insufficientBalance: Boolean,
    isPollingSubscriptionBalance: Boolean,
    displayReconnectTunnel: Boolean,
): Int? = when {
    insufficientBalance && isPollingSubscriptionBalance -> R.string.processing_subscription_balance
    insufficientBalance -> R.string.insufficient_balance
    status == ConnectStatus.CONNECT_FAILED -> R.string.conn_failed
    displayReconnectTunnel -> R.string.reconnect_tunnel_status_indicator
    status == ConnectStatus.CONNECTING || status == ConnectStatus.DESTINATION_SET -> R.string.connecting_status_indicator
    status == ConnectStatus.CONNECTED -> R.string.connected
    else -> null
}
