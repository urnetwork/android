package com.bringyour.network.ui.connect

/**
 * The start connect of the quick connect surfaces (the Quick Settings tile,
 * the launcher shortcuts, the widget button), through the start connect gate
 * the connect screen uses.
 *
 * The gate may first fetch a fresh balance, for up to
 * [START_CONNECT_BALANCE_FETCH_TIMEOUT_MILLIS], and the surfaces showed the
 * disconnected state until it answered (owner report 2026-10-16). A start now
 * waits in a [StartConnectAdmission], as on the connect screen: the surfaces
 * show connecting from the tap ([waiting]) and are re-rendered (`render`) on
 * each change, so a refused start shows off again before its surface opens
 * the upgrade screen, and an admitted one shows the connection it asked for.
 * A tap while the start waits does not stack, and a disconnect ends it
 * without connecting. A connection already requested never waits on the
 * gate. Main thread only; [waiting] may be read from any thread (the widgets
 * render off the main thread).
 */
internal class QuickStartConnect(
    /** The gate: calls back once, at once or after a fetch, with whether the start is refused. */
    check: (onResult: (blocked: Boolean) -> Unit) -> Unit,
    /** Re-renders the surfaces: they read [waiting] and the device. */
    render: () -> Unit,
) {
    private val admission = StartConnectAdmission(displayChanged = render, check = check)

    /** A start waits on its balance: the surfaces show connecting. */
    val waiting: Boolean get() = admission.waiting

    /**
     * A surface's connect or disconnect, with the device's `connectEnabled`.
     * `apply` makes the request with whether the gate refused the start (the
     * quick connect step, which opens the upgrade screen for a refused one)
     * and its result goes to `onResult`, once. A tap while a start waits gets
     * `waiting()` instead, at once.
     */
    fun <R> request(
        connectEnabled: Boolean,
        connect: Boolean,
        apply: (startConnectBlocked: Boolean) -> R,
        waiting: () -> R,
        onResult: (R) -> Unit,
    ) {
        if (!connect) {
            // the user's disconnect: a start waiting on its balance does not connect
            admission.cancel()
            onResult(apply(false))
            return
        }
        if (connectEnabled) {
            onResult(apply(false))
            return
        }
        if (admission.waiting) {
            onResult(waiting())
            return
        }
        admission.connect(
            start = { onResult(apply(false)) },
            refused = { onResult(apply(true)) },
        )
    }
}

/** What the quick connect surfaces show: the tile's state and subtitle, the widget's button. */
internal enum class QuickConnectShown {
    OFF,
    /** A start waits on its balance, as the connect screen's connecting state. */
    CONNECTING,
    /** A connection is requested (the device's `connectEnabled`). */
    ON,
}

internal fun quickConnectShown(connectEnabled: Boolean, startWaiting: Boolean): QuickConnectShown = when {
    connectEnabled -> QuickConnectShown.ON
    startWaiting -> QuickConnectShown.CONNECTING
    else -> QuickConnectShown.OFF
}
