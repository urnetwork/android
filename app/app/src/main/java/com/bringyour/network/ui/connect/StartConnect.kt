package com.bringyour.network.ui.connect

/**
 * One app-level start admission shared by the connect screen and the quick
 * connect surfaces (Quick Settings tile, launcher shortcuts, widget button).
 *
 * The gate may first fetch a fresh balance, for up to
 * [START_CONNECT_BALANCE_FETCH_TIMEOUT_MILLIS], and the surfaces showed the
 * disconnected state until it answered (owner report 2026-10-16). A start now
 * waits in a [StartConnectAdmission], as on the connect screen: the surfaces
 * show connecting from the tap ([waiting]) and are re-rendered (`render`) on
 * each change, so a refused start shows off again before its surface opens
 * the upgrade screen, and an admitted one shows the connection it asked for.
 * A tap while the start waits does not stack, and a disconnect or a sign out
 * ends it without connecting ([cancel]). Every request is answered once,
 * also a start that was ended: a launcher shortcut's translucent trampoline
 * finishes only on its answer. A connection already requested never waits on
 * the gate. Main thread only; [waiting] may be read from any thread (the
 * widgets render off the main thread).
 */
internal class StartConnect(
    /** The gate: calls back once, at once or after a fetch, with whether the start is refused. */
    check: (onResult: (blocked: Boolean) -> Unit) -> Unit,
    /** Re-renders the surfaces: they read [waiting] and the device. */
    render: () -> Unit,
) {
    private val listeners = mutableListOf<() -> Unit>()
    private val admission = StartConnectAdmission(check = check, changed = {
        render()
        listeners.toList().forEach { it() }
    })

    /** Observes the one app-level wait; each surface owns its subscription. */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    private var waitingOwner: Any? = null

    /** A start waits on its balance: the surfaces show connecting. */
    val waiting: Boolean get() = admission.waiting

    // answers the request whose start waits, if it is ended first
    private var answerEnded: (() -> Unit)? = null

    /**
     * A surface's connect or disconnect, with the device's `connectEnabled`.
     * `apply` makes the request with whether the gate refused the start (the
     * quick connect step, which opens the upgrade screen for a refused one)
     * and its result goes to `onResult`, once. A tap while a start waits gets
     * `waiting()` instead, at once, and a start ended before the gate decided
     * gets `ended()`.
     */
    fun <R> request(
        connectEnabled: Boolean,
        connect: Boolean,
        apply: (startConnectBlocked: Boolean) -> R,
        waiting: () -> R,
        ended: () -> R,
        onResult: (R) -> Unit,
        owner: Any? = null,
    ) {
        if (!connect) {
            // the user's disconnect: a start waiting on its balance does not connect
            cancel()
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
        waitingOwner = owner
        answerEnded = { onResult(ended()) }
        admission.connect(
            start = {
                answerEnded = null
                waitingOwner = null
                onResult(apply(false))
            },
            refused = {
                answerEnded = null
                waitingOwner = null
                onResult(apply(true))
            },
        )
    }

    /**
     * Ends a start waiting on its balance: it never connects, the surfaces
     * show off, and its request gets `ended()`. Nothing when none waits.
     */
    fun cancel() {
        waitingOwner = null
        admission.cancel()
        val answer = answerEnded
        answerEnded = null
        answer?.invoke()
    }

    /** Ends only this surface's wait when the surface itself goes away. */
    fun cancel(owner: Any) {
        if (waitingOwner === owner) cancel()
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
