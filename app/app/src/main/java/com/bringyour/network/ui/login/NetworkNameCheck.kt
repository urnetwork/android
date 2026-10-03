package com.bringyour.network.ui.login

/**
 * The state of the online network name availability check on the create
 * network form.
 *
 * A check that errored or never answered (offline, timeout, server error) is
 * [FAILED], not [UNAVAILABLE]: the name was never judged. A failed name may
 * still be submitted, because network create re-checks availability on the
 * server and rejects a taken name with its own error.
 */
enum class NetworkNameCheckState {
    EMPTY,
    TOO_SHORT,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    FAILED;

    /** the name may be submitted to network create */
    val allowsCreate: Boolean
        get() = this == AVAILABLE || this == FAILED
}

/**
 * Runs the availability check for the latest name the user entered.
 *
 * `check` starts one online check and answers `true`/`false` for
 * available/unavailable, or `null` when the check errored. `schedule` runs an
 * action after a delay and returns a function that cancels it. Only the
 * answer for the latest attempt is applied. A failed check is retried
 * automatically a bounded number of times, and a check that never answers
 * fails after [CHECK_TIMEOUT_MILLIS].
 *
 * Not safe for concurrent use: call it, and deliver `check` answers and
 * scheduled actions, on one thread.
 */
class NetworkNameCheck(
    private val check: (networkName: String, onResult: (available: Boolean?) -> Unit) -> Unit,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> () -> Unit,
    private val onStateChange: (NetworkNameCheckState) -> Unit,
) {
    companion object {
        const val MIN_LENGTH = 6

        /** automatic re-checks of the same name after a failed check */
        const val MAX_RETRY_COUNT = 3
        const val RETRY_DELAY_MILLIS = 3_000L

        /** a check with no answer by then is treated as failed */
        const val CHECK_TIMEOUT_MILLIS = 15_000L

        /** `available` is null when the check errored or returned no result */
        fun resultState(available: Boolean?): NetworkNameCheckState {
            return when (available) {
                true -> NetworkNameCheckState.AVAILABLE
                false -> NetworkNameCheckState.UNAVAILABLE
                null -> NetworkNameCheckState.FAILED
            }
        }
    }

    var state = NetworkNameCheckState.EMPTY
        private set

    private var networkName = ""
    private var attempt = 0
    private var retryCount = 0
    private var cancelScheduled: (() -> Unit)? = null

    fun validate(networkName: String) {
        this.networkName = networkName
        retryCount = 0
        start()
    }

    private fun start() {
        attempt += 1
        cancelScheduled?.invoke()
        cancelScheduled = null

        val localState = when {
            networkName.isEmpty() -> NetworkNameCheckState.EMPTY
            networkName.length < MIN_LENGTH -> NetworkNameCheckState.TOO_SHORT
            else -> null
        }
        if (localState != null) {
            setState(localState)
            return
        }

        setState(NetworkNameCheckState.CHECKING)
        val checkAttempt = attempt
        cancelScheduled = schedule(CHECK_TIMEOUT_MILLIS) {
            if (checkAttempt == attempt) {
                finish(null)
            }
        }
        check(networkName) { available ->
            if (checkAttempt == attempt) {
                finish(available)
            }
        }
    }

    private fun finish(available: Boolean?) {
        // a late answer for this attempt is ignored
        attempt += 1
        cancelScheduled?.invoke()
        cancelScheduled = null

        val nextState = resultState(available)
        setState(nextState)

        if (nextState == NetworkNameCheckState.FAILED && retryCount < MAX_RETRY_COUNT) {
            retryCount += 1
            cancelScheduled = schedule(RETRY_DELAY_MILLIS) {
                start()
            }
        }
    }

    private fun setState(nextState: NetworkNameCheckState) {
        if (state != nextState) {
            state = nextState
            onStateChange(nextState)
        }
    }
}
