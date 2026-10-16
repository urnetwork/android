package com.bringyour.network.ui.connect

import com.bringyour.network.ui.shared.models.ConnectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The connect screen's start connect, from the tap to the sdk's connect, and
 * the status the screen shows meanwhile: the status the sdk reports. Each
 * tap asks the start connect gate ([checkStartConnect]), which may first
 * fetch a fresh account balance, and starts the connect once it is admitted.
 * Main thread only.
 */
internal class StartConnectAdmission(
    /** The gate: calls back once, at once or after a fetch, with whether the start is refused. */
    private val check: (onResult: (blocked: Boolean) -> Unit) -> Unit,
) {
    private val status = MutableStateFlow(ConnectStatus.DISCONNECTED)

    /** What the connect screen shows. */
    val displayStatus: StateFlow<ConnectStatus> = status

    /** The sdk's status. */
    fun reported(status: ConnectStatus) {
        this.status.value = status
    }

    /**
     * Starts a connect through the gate: `start` runs once it is admitted
     * (the sdk's connect, which reports its own status), `refused` once it
     * is refused.
     */
    fun connect(start: () -> Unit, refused: () -> Unit) {
        check { blocked ->
            if (blocked) {
                refused()
            } else {
                start()
            }
        }
    }

    /** Nothing waits on the gate here. */
    fun cancel() {
    }
}
