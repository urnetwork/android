package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The connect screen's start connect, from the tap to the sdk's connect, and
 * the status the screen shows meanwhile.
 *
 * The start connect gate ([checkStartConnect]) may first fetch a fresh
 * account balance, for up to [START_CONNECT_BALANCE_FETCH_TIMEOUT_MILLIS],
 * and the screen showed nothing until it answered (owner report 2026-10-16:
 * the view should change to the connecting state as soon as connect is
 * tapped). While the gate decides, [displayStatus] is
 * [ConnectStatus.DESTINATION_SET], the sdk's own first status for a connect,
 * and the sdk's reports are kept but not shown. A refused start goes back to
 * the reported status before the upgrade screen opens. An admitted one
 * starts the connect, and the sdk's statuses for it follow, so the screen
 * never steps back to disconnected in between. A decision made at once shows
 * no pending state.
 *
 * Only the display changes: the gate decides as before, and the readers of
 * the reported status (the out-of-balance notice, the balance recovery) do
 * not see a start waiting on its balance as a requested connection. A tap
 * while a start waits does not stack, and [cancel] (the user's disconnect, a
 * device change) ends it without connecting. Main thread only.
 */
internal class StartConnectAdmission(
    /** The gate: calls back once, at once or after a fetch, with whether the start is refused. */
    private val check: (onResult: (blocked: Boolean) -> Unit) -> Unit,
) {
    private val status = MutableStateFlow(ConnectStatus.DISCONNECTED)

    /** What the connect screen shows. */
    val displayStatus: StateFlow<ConnectStatus> = status

    private var reportedStatus = ConnectStatus.DISCONNECTED
    // bumped by each start and cancel, so a superseded decision is dropped
    private var generation = 0L
    private var waiting = false

    /** The sdk's status. */
    fun reported(status: ConnectStatus) {
        reportedStatus = status
        publish()
    }

    /**
     * Starts a connect through the gate: `start` runs once it is admitted
     * (the sdk's connect, which reports its own status), `refused` once it
     * is refused, after the display went back to the reported status.
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
                    publish()
                    refused()
                } else {
                    start()
                    publish()
                }
            }
        }
        // still waiting: the gate is fetching the balance
        publish()
    }

    /** Ends a start waiting on its balance: it never connects. */
    fun cancel() {
        if (waiting) {
            generation += 1
            waiting = false
            publish()
        }
    }

    private fun publish() {
        status.value = if (waiting) ConnectStatus.DESTINATION_SET else reportedStatus
    }
}
