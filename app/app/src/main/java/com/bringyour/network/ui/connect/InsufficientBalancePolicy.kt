package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan

/**
 * Decisions for the insufficient balance state, kept pure so they are unit
 * testable without an Android runtime.
 *
 * Insufficient balance is a billing state reported by the server contract, and
 * backend incidents can raise it on funded accounts too. While it holds, the
 * tunnel keeps capturing because connect is still requested, so the user must
 * always have a way out: disconnect stays offered, and once the state outlasts
 * a short grace the connect request is cleared the same way a user disconnect
 * clears it. An enabled kill switch keeps capture (fail closed); the user can
 * still disconnect by hand.
 */

/** Rides out transient contract latches before the app disconnects on its own. */
internal const val INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS = 15_000L

/**
 * Whether the connect controls are replaced by the upgrade flow. Supporters and
 * an in-flight subscription balance poll are never gated.
 */
internal fun insufficientBalanceGate(
    insufficientBalance: Boolean,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
): Boolean = insufficientBalance && currentPlan != Plan.Supporter && !isPollingSubscriptionBalance

internal data class ConnectActionButtons(
    val upgrade: Boolean,
    val connect: Boolean,
    val disconnect: Boolean,
    val reconnect: Boolean,
)

/**
 * The drawer's action buttons. In the gate only connect is replaced by upgrade:
 * disconnect remains whenever a connection is requested, so the gate can never
 * strand the user in a tunnel with no exit.
 */
internal fun connectActionButtons(
    insufficientBalance: Boolean,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
    connectStatus: ConnectStatus,
    displayReconnectTunnel: Boolean,
): ConnectActionButtons {
    val connectionRequested = connectStatus != ConnectStatus.DISCONNECTED
    if (insufficientBalanceGate(insufficientBalance, currentPlan, isPollingSubscriptionBalance)) {
        return ConnectActionButtons(
            upgrade = true,
            connect = false,
            disconnect = connectionRequested,
            reconnect = false,
        )
    }
    return ConnectActionButtons(
        upgrade = false,
        connect = !connectionRequested,
        disconnect = connectionRequested && !displayReconnectTunnel,
        reconnect = displayReconnectTunnel,
    )
}

internal enum class ConnectButtonTapAction {
    CONNECT,
    DISCONNECT,
    COUNT_CONNECTED_TAP,
    NONE,
}

/**
 * The round connect button. It connects when disconnected as before; in the
 * gate it shows the insufficient balance warning instead of the grid, so a tap
 * while a connection is requested disconnects. Outside the gate a tap while
 * connected only feeds the hidden tap sequence.
 */
internal fun connectButtonTapAction(
    insufficientBalance: Boolean,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
    connectStatus: ConnectStatus,
): ConnectButtonTapAction = when {
    connectStatus == ConnectStatus.DISCONNECTED -> ConnectButtonTapAction.CONNECT
    insufficientBalanceGate(insufficientBalance, currentPlan, isPollingSubscriptionBalance) ->
        ConnectButtonTapAction.DISCONNECT
    connectStatus == ConnectStatus.CONNECTED -> ConnectButtonTapAction.COUNT_CONNECTED_TAP
    else -> ConnectButtonTapAction.NONE
}

/**
 * Whether the app clears the connect request on its own. The gate must have
 * held continuously since gateSinceMillis for the full grace; null means it
 * does not hold now. An enabled kill switch keeps the request (fail closed).
 */
internal fun insufficientBalanceAutoDisconnect(
    insufficientBalance: Boolean,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
    connectRequested: Boolean,
    killSwitch: Boolean,
    gateSinceMillis: Long?,
    nowMillis: Long,
): Boolean {
    if (!insufficientBalanceGate(insufficientBalance, currentPlan, isPollingSubscriptionBalance)) {
        return false
    }
    if (!connectRequested || killSwitch || gateSinceMillis == null) {
        return false
    }
    return INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS <= nowMillis - gateSinceMillis
}
