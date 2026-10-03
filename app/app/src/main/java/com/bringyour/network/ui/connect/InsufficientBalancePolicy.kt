package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan

/**
 * Decisions for the insufficient balance state, kept pure so they are unit
 * testable without an Android runtime.
 *
 * Insufficient balance is a billing state reported by the server contract, and
 * backend incidents can raise it on funded accounts too. While it holds, connect
 * stays requested and the tunnel keeps capturing, so no traffic leaves outside
 * the tunnel without the user knowing. The app never disconnects on its own:
 * it tells the user (in-app alert plus one notification per entry into the
 * state) and always offers an explicit disconnect next to upgrade.
 */

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

/**
 * The session controls the monitor reports to. disconnect is the user's
 * control; the monitor holds it only to make explicit that out of balance
 * never calls it.
 */
internal interface InsufficientBalanceSession {
    fun disconnect()
    fun postNotice()
    fun cancelNotice()
}

/**
 * Reacts to contract status, plan, balance poll and connection observations.
 * An episode starts when insufficient balance is first seen and ends when it
 * clears. The notice says traffic is held in the tunnel, so it is posted once
 * per episode at the first observation where the gate holds and a connection
 * is requested (a supporter plan or a balance poll at entry defers it). It is
 * removed when it stops being true: the episode ends or the user disconnects.
 * A disconnect does not re-arm it within the episode. Not thread safe; feed it
 * from one thread.
 */
internal class InsufficientBalanceMonitor(
    private val session: InsufficientBalanceSession,
) {
    private var noticePosted = false
    private var noticeShown = false

    fun update(
        insufficientBalance: Boolean,
        currentPlan: Plan,
        isPollingSubscriptionBalance: Boolean,
        connectRequested: Boolean,
    ) {
        if (!insufficientBalance) {
            noticePosted = false
            hideNotice()
            return
        }
        if (!connectRequested) {
            hideNotice()
            return
        }
        if (!noticePosted &&
            insufficientBalanceGate(insufficientBalance, currentPlan, isPollingSubscriptionBalance)
        ) {
            noticePosted = true
            noticeShown = true
            session.postNotice()
        }
    }

    private fun hideNotice() {
        if (noticeShown) {
            noticeShown = false
            session.cancelNotice()
        }
    }
}
