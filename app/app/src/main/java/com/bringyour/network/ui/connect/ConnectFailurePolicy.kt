package com.bringyour.network.ui.connect

/**
 * When a connect attempt counts as failed, kept pure so it is unit testable
 * without an Android runtime (ConnectFailurePolicyTest).
 *
 * The signal is the connect view controller's own: the sdk reports
 * CONNECT_FAILED once the connect window has hit both of its outcome deadlines
 * (about 45 s by default) with zero providers Added. As a backstop the app also
 * counts an attempt that has had no provider in its window for
 * [CONNECT_FAILURE_TIMEOUT_MILLIS], in case that verdict never arrives. Either
 * way it only counts while the user wants to be connected.
 *
 * ConnectViewModel feeds this from the view controller's status and window and
 * reports a failed attempt to MainApplication.maybeRunWhitelistProbe.
 */

/**
 * The app's backstop for a requested connect with no provider in its window.
 * Set past the sdk's own window outcome deadlines so CONNECT_FAILED normally
 * decides first.
 */
internal const val CONNECT_FAILURE_TIMEOUT_MILLIS = 90_000L

// The sdk ConnectViewController statuses this policy reads (Sdk.Connecting,
// Sdk.DestinationSet, Sdk.Connected and Sdk.ConnectFailed). Literal strings,
// as ConnectStatus.fromString uses, so the policy never loads the native Sdk
// class.
internal const val CONNECTION_STATUS_CONNECTING = "CONNECTING"
internal const val CONNECTION_STATUS_DESTINATION_SET = "DESTINATION_SET"
internal const val CONNECTION_STATUS_CONNECTED = "CONNECTED"
internal const val CONNECTION_STATUS_CONNECT_FAILED = "CONNECT_FAILED"

/**
 * Whether [connectionStatus] is a connect still in progress or failed: the
 * statuses an attempt runs through. DISCONNECTED, CONNECTED, null or an
 * unknown status is no attempt.
 */
internal fun isConnectAttemptStatus(connectionStatus: String?): Boolean =
    connectionStatus == CONNECTION_STATUS_CONNECTING ||
        connectionStatus == CONNECTION_STATUS_DESTINATION_SET ||
        connectionStatus == CONNECTION_STATUS_CONNECT_FAILED

/**
 * Whether the current connect attempt has failed.
 *
 * - Only while the user wants to be connected ([connectRequested], the device's
 *   connectEnabled): a disconnect is never a failure.
 * - Failed at once when the sdk reports CONNECT_FAILED.
 * - Otherwise only while the status stays connecting (CONNECTING or
 *   DESTINATION_SET; never once connected), and only when the window has had no
 *   provider ([windowProviderCount], the grid's active providers) for
 *   [timeoutMillis] since the attempt began at [attemptStartedAtMillis]. A
 *   provider in the window is progress, not failure.
 */
internal fun connectAttemptFailed(
    connectRequested: Boolean,
    connectionStatus: String?,
    windowProviderCount: Int,
    attemptStartedAtMillis: Long?,
    nowMillis: Long,
    timeoutMillis: Long = CONNECT_FAILURE_TIMEOUT_MILLIS,
): Boolean {
    if (!connectRequested) {
        return false
    }
    if (connectionStatus == CONNECTION_STATUS_CONNECT_FAILED) {
        return true
    }
    if (!isConnectAttemptStatus(connectionStatus)) {
        return false
    }
    if (0 < windowProviderCount) {
        return false
    }
    if (attemptStartedAtMillis == null) {
        return false
    }
    return timeoutMillis <= nowMillis - attemptStartedAtMillis
}

/**
 * Tracks the current connect attempt and reports its failure once. An attempt
 * begins at the first observation where the user wants to be connected and the
 * status is an attempt status (isConnectAttemptStatus), and ends (re-arming the
 * report) when the user no longer wants to be connected or the status leaves
 * the attempt (connected or disconnected). Main thread only.
 */
internal class ConnectFailureMonitor(
    private val timeoutMillis: Long = CONNECT_FAILURE_TIMEOUT_MILLIS,
) {
    private var attemptStartedAtMillis: Long? = null
    private var reported = false

    /**
     * Folds in the latest connect state. Returns true exactly once per failed
     * attempt.
     */
    fun observe(
        connectRequested: Boolean,
        connectionStatus: String?,
        windowProviderCount: Int,
        nowMillis: Long,
    ): Boolean {
        if (!connectRequested || !isConnectAttemptStatus(connectionStatus)) {
            attemptStartedAtMillis = null
            reported = false
            return false
        }
        val startedAtMillis = attemptStartedAtMillis ?: nowMillis.also {
            attemptStartedAtMillis = it
        }
        if (reported) {
            return false
        }
        if (
            connectAttemptFailed(
                connectRequested = connectRequested,
                connectionStatus = connectionStatus,
                windowProviderCount = windowProviderCount,
                attemptStartedAtMillis = startedAtMillis,
                nowMillis = nowMillis,
                timeoutMillis = timeoutMillis,
            )
        ) {
            reported = true
            return true
        }
        return false
    }

    /**
     * When the current attempt's time bound elapses, so the caller can re-check
     * then even if no status or window event arrives; null when no attempt is
     * pending or its failure was already reported.
     */
    fun timeoutAtMillis(): Long? {
        if (reported) {
            return null
        }
        return attemptStartedAtMillis?.plus(timeoutMillis)
    }

    /** Forgets the current attempt, e.g. when the device or view controller changes. */
    fun reset() {
        attemptStartedAtMillis = null
        reported = false
    }
}
