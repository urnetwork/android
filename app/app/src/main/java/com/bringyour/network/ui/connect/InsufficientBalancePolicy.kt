package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.widgets.WidgetBalanceSnapshot

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
 *
 * Two cases are kept apart (product decision, 2026-10-03):
 * - Start connect: a new connect request from any surface (the connect
 *   screen, a location pick, a link, the Quick Settings tile, the launcher
 *   shortcuts, the widget button). Blocked while out of balance; the surface
 *   opens the upgrade screen instead. See [startConnectBlocked].
 * - Already connected: a connection the user asked for earlier, including the
 *   system restarting or restoring it (boot, package update, service
 *   redelivery) and system Always-on, which owns its own connection. Never
 *   dropped or refused because of balance; traffic is held as above. See
 *   [quickConnectStep] and [InsufficientBalanceMonitor].
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

/**
 * The last account balance fetched from the server has nothing left: none
 * available and none held in open contracts (which return what they do not
 * use). Unknown (never fetched, or signed out) and Supporter are never
 * exhausted. The contract status only reports insufficient balance while a
 * connection is up and the SDK clears it with the destination, so this is
 * what a start connect from the disconnected state can see; a start connect
 * reads it only fresh (see [startConnectBalance]).
 */
internal fun accountBalanceExhausted(balance: WidgetBalanceSnapshot?): Boolean =
    balance != null &&
        !balance.isPro &&
        balance.balanceByteCount <= 0 &&
        balance.openTransferByteCount <= 0

/**
 * The insufficient balance the connect controls show. The contract status
 * always counts; the account balance counts only before a connection is
 * requested, so the held-traffic state stays tied to what the connection
 * itself reports.
 */
internal fun displayInsufficientBalance(
    contractInsufficientBalance: Boolean,
    accountBalanceExhausted: Boolean,
    connectRequested: Boolean,
): Boolean = contractInsufficientBalance || (!connectRequested && accountBalanceExhausted)

/**
 * Whether a new connect request must not start the tunnel: the same gate as
 * the connect screen's connect/upgrade swap, over the contract status or the
 * account balance. Never applies to a connection already requested; see
 * [quickConnectStep].
 */
internal fun startConnectBlocked(
    contractInsufficientBalance: Boolean,
    accountBalanceExhausted: Boolean,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
): Boolean = insufficientBalanceGate(
    contractInsufficientBalance || accountBalanceExhausted,
    currentPlan,
    isPollingSubscriptionBalance,
)

/**
 * How old the account balance may be for a start connect to be blocked on it.
 * The snapshot is otherwise refreshed only every 30 minutes outside the app,
 * so a zero from before a purchase or a daily refill would send a funded
 * account to upgrade. Matches the other platforms.
 */
internal const val START_CONNECT_BALANCE_MAX_AGE_MILLIS = 60_000L

/** How long a start connect waits for a fresh account balance. */
internal const val START_CONNECT_BALANCE_FETCH_TIMEOUT_MILLIS = 5_000L

/** The account balance was fetched recently enough to block a start connect. */
internal fun startConnectBalanceFresh(balance: WidgetBalanceSnapshot?, nowMillis: Long): Boolean =
    balance != null &&
        balance.updatedAtMillis <= nowMillis &&
        nowMillis - balance.updatedAtMillis <= START_CONNECT_BALANCE_MAX_AGE_MILLIS

/**
 * The account balance a start connect decides on. A fresh cached balance is
 * used as is. Otherwise the balance is fetched: `fetch` calls back once with
 * the fetched balance, or null when the fetch failed or timed out. A failed
 * fetch never blocks (null is not exhausted): the server refuses the contract
 * anyway, and the connection's held state and alert cover it.
 */
internal fun startConnectBalance(
    cached: WidgetBalanceSnapshot?,
    nowMillis: Long,
    fetch: (onBalance: (WidgetBalanceSnapshot?) -> Unit) -> Unit,
    onBalance: (WidgetBalanceSnapshot?) -> Unit,
) {
    if (startConnectBalanceFresh(cached, nowMillis)) {
        onBalance(cached)
    } else {
        fetch(onBalance)
    }
}

/**
 * Passes on only the first of a balance fetch's result and its timeout, so a
 * start connect decides exactly once. Main thread only.
 */
internal class FirstBalance(private val onBalance: (WidgetBalanceSnapshot?) -> Unit) {
    private var delivered = false

    fun offer(balance: WidgetBalanceSnapshot?) {
        if (!delivered) {
            delivered = true
            onBalance(balance)
        }
    }
}

internal enum class QuickConnectStep {
    /** The request matches the current state: nothing to do. */
    NONE,
    CONNECT,
    DISCONNECT,
    /** Out of balance: do not start the tunnel, open the upgrade screen. */
    UPGRADE,
}

/**
 * What a quick connect surface (tile, shortcut, widget button) does. A
 * connection already requested is kept as is whatever the balance, so a
 * connect while connected never turns into a disconnect or an upgrade.
 */
internal fun quickConnectStep(
    connectEnabled: Boolean,
    connect: Boolean,
    startConnectBlocked: Boolean,
): QuickConnectStep = when {
    connectEnabled == connect -> QuickConnectStep.NONE
    !connect -> QuickConnectStep.DISCONNECT
    startConnectBlocked -> QuickConnectStep.UPGRADE
    else -> QuickConnectStep.CONNECT
}

internal data class ConnectActionButtons(
    val upgrade: Boolean,
    val connect: Boolean,
    val disconnect: Boolean,
    val reconnect: Boolean,
    val retry: Boolean = false,
)

/**
 * The drawer's action buttons. In the gate only connect is replaced by upgrade:
 * disconnect remains whenever a connection is requested, so the gate can never
 * strand the user in a tunnel with no exit. A failed connect (the sdk's
 * CONNECT_FAILED) offers retry next to disconnect: the session is still
 * standing, and a retry connects to the selected location again, which rebuilds
 * the connection (the windows app's Retry; linux keeps the disconnect).
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
        retry = connectStatus == ConnectStatus.CONNECT_FAILED && !displayReconnectTunnel,
    )
}

/**
 * Why the balance is out, from the last account balance: what is missing is
 * either held by open connections, which return what they do not use as they
 * close, or used up until the free refresh or an upgrade.
 */
internal enum class OutOfBalanceKind {
    /** No balance known, Pro, or the balance reads available again: say neither. */
    UNKNOWN,
    /** Enough is reserved (Pending) that its return could bring data back. */
    RESERVED,
    /** Nothing meaningful is reserved: out until the free refresh or an upgrade. */
    EXHAUSTED,
}

/**
 * Reserved or exhausted, by the same threshold the self-recovery waits for
 * ([BALANCE_RECOVERY_THRESHOLD_BYTES]): reserved when at least that much is
 * held by open connections. No time is promised for its return; connections
 * close when they are used up or end.
 */
internal fun outOfBalanceKind(balance: WidgetBalanceSnapshot?): OutOfBalanceKind = when {
    balance == null || balance.isPro -> OutOfBalanceKind.UNKNOWN
    BALANCE_RECOVERY_THRESHOLD_BYTES <= balance.balanceByteCount -> OutOfBalanceKind.UNKNOWN
    BALANCE_RECOVERY_THRESHOLD_BYTES <= balance.openTransferByteCount -> OutOfBalanceKind.RESERVED
    else -> OutOfBalanceKind.EXHAUSTED
}

/** What the out-of-balance notice shows. */
internal data class OutOfBalanceNotice(
    /** "Free data refreshes in {time}." with a Why? link to the data sheet. */
    val refresh: Boolean,
    /** Traffic is held in the tunnel until the user upgrades or disconnects. */
    val held: Boolean,
    /** "{amount} is reserved ..." or "You're out of data ...", or neither. */
    val kind: OutOfBalanceKind = OutOfBalanceKind.UNKNOWN,
    /** "You'll be reconnected when data is available again." */
    val willReconnect: Boolean = false,
    /** A Cancel action next to it: a refused start has no Disconnect to stop it. */
    val cancel: Boolean = false,
)

/**
 * The notice under the drawer's buttons while out of balance. It leads with
 * when the free data refreshes, whether or not a connection is requested, so
 * the upgrade button does not read as the only way back; Why? opens the
 * "About your data" sheet. Then whether the data is reserved or used up, and
 * the held-traffic line while a connection is requested.
 *
 * While a connect the user asked for waits on the balance (BalanceRecovery,
 * null when there is none), it says the app reconnects by itself: for a held
 * connection, and for a refused start even once the gate has lifted, since
 * that start can still fire. A refused start gets Cancel, the only way to
 * stop it.
 */
internal fun outOfBalanceNotice(
    buttons: ConnectActionButtons,
    kind: OutOfBalanceKind = OutOfBalanceKind.UNKNOWN,
    recovery: BalanceRecoveryState? = null,
): OutOfBalanceNotice {
    val held = buttons.upgrade && buttons.disconnect
    val startWaiting = recovery?.startWaiting == true
    val willReconnect = recovery?.retriesLeft == true && (startWaiting || held)
    return OutOfBalanceNotice(
        refresh = buttons.upgrade,
        held = held,
        kind = if (buttons.upgrade) kind else OutOfBalanceKind.UNKNOWN,
        willReconnect = willReconnect,
        cancel = willReconnect && startWaiting,
    )
}

/**
 * Whether the upgrade screen leads with when the free data refreshes and
 * offers Wait for refresh: only when a start connect blocked by the balance
 * opened it (the drawer's out-of-balance button, or a quick connect surface
 * sent to upgrade). Supporters are never blocked, and get no free grant.
 */
internal fun upgradeShowsFreeRefresh(
    openedByStartConnectBlock: Boolean,
    currentPlan: Plan,
): Boolean = openedByStartConnectBlock && currentPlan != Plan.Supporter

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
