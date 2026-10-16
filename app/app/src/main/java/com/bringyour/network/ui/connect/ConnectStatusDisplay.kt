package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the connect screen shows: connecting while a start waits on its
 * balance, any surface's (the app's one StartConnect); else the newest
 * queued command's own first status, until the queue has run it
 * (ConnectCommands); else the status the sdk reports. So a tap shows its
 * state at once, a report from before it cannot flip the view back, and the
 * screen shows the tile's or a shortcut's wait as its own. Main thread only.
 */
internal class ConnectStatusDisplay {
    private val status = MutableStateFlow(ConnectStatus.DISCONNECTED)

    /** What the connect screen shows. */
    val displayStatus: StateFlow<ConnectStatus> = status

    private var reportedStatus = ConnectStatus.DISCONNECTED
    // the newest queued command's own first status, until the queue has run it
    private var pendingStatus: ConnectStatus? = null
    private var startWaiting = false

    /** The sdk's status. */
    fun reported(status: ConnectStatus) {
        reportedStatus = status
        publish()
    }

    /**
     * The status of the newest connect command still queued: the sdk
     * controller's own first status for it (DESTINATION_SET for a connect,
     * DISCONNECTED for a disconnect), or null once the queue has run them all.
     */
    fun commandPending(status: ConnectStatus?) {
        pendingStatus = status
        publish()
    }

    /** Whether a start waits on its balance, on any surface. */
    fun startWaiting(waiting: Boolean) {
        startWaiting = waiting
        publish()
    }

    private fun publish() {
        val next = if (startWaiting) ConnectStatus.DESTINATION_SET else pendingStatus ?: reportedStatus
        if (status.value != next) {
            status.value = next
        }
    }
}
