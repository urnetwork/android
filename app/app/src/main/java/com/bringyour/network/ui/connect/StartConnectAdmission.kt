package com.bringyour.network.ui.connect

/**
 * A start connect waiting on the start connect gate ([check]), from the tap
 * to the sdk's connect: the app's one wait, shared by every surface
 * (StartConnect).
 *
 * The gate may first fetch a fresh account balance, for up to
 * [START_CONNECT_BALANCE_FETCH_TIMEOUT_MILLIS], and the surfaces showed
 * nothing until it answered (owner report 2026-10-16: the view should change
 * to the connecting state as soon as connect is tapped). While the gate
 * decides, [waiting] is true and the surfaces show connecting (the connect
 * screen's ConnectStatusDisplay, the quick connect surfaces'
 * quickConnectShown). A refused start ends the wait before `refused` runs, so
 * the surfaces show what they did before the upgrade screen opens. An
 * admitted one runs `start` (its connect command, which the surfaces show as
 * pending) before the wait ends, so they never step back to disconnected in
 * between. A decision made at once never shows as waiting.
 *
 * Only the display changes: the gate decides as before, and the readers of
 * the reported status (the out-of-balance notice, the balance recovery) do
 * not see a start waiting on its balance as a requested connection. A tap
 * while a start waits does not stack, and [cancel] (a disconnect anywhere, a
 * device change, a sign out) ends it without connecting. Main thread only;
 * [waiting] may be read from any thread.
 */
internal class StartConnectAdmission(
    /** The gate: calls back once, at once or after a fetch, with whether the start is refused. */
    private val check: (onResult: (blocked: Boolean) -> Unit) -> Unit,
    /** Runs on each change of [waiting] the surfaces were told of, a refused start's before `refused`. */
    private val changed: () -> Unit = {},
) {
    // bumped by each start and cancel, so a superseded decision is dropped
    private var generation = 0L
    // the surfaces were told the start waits: a decision at once never is
    private var shown = false

    /** A start waits on the gate. */
    @Volatile
    var waiting = false
        private set

    /**
     * Starts a connect through the gate: `start` runs once it is admitted
     * (the sdk's connect), `refused` once it is refused, after the wait
     * ended. Nothing while a start waits.
     */
    fun connect(start: () -> Unit, refused: () -> Unit) {
        if (waiting) {
            return
        }
        generation += 1
        val startGeneration = generation
        waiting = true
        check { blocked ->
            if (startGeneration == generation && waiting) {
                waiting = false
                if (blocked) {
                    ended()
                    refused()
                } else {
                    start()
                    ended()
                }
            }
        }
        // still waiting: the gate is fetching the balance
        if (waiting && startGeneration == generation) {
            shown = true
            changed()
        }
    }

    /** Ends a start waiting on its balance: it never connects. */
    fun cancel() {
        if (waiting) {
            generation += 1
            waiting = false
            ended()
        }
    }

    // the wait ended: tell the surfaces, if they were told it began
    private fun ended() {
        if (shown) {
            shown = false
            changed()
        }
    }
}
