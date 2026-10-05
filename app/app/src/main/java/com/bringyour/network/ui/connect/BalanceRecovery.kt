package com.bringyour.network.ui.connect

import com.bringyour.network.widgets.WidgetBalanceSnapshot

/**
 * Self-recovery for a connect that insufficient balance blocked, kept pure so
 * it is unit testable without an Android runtime.
 *
 * A connect the user asked for is blocked in one of two ways (see
 * InsufficientBalancePolicy): a start connect the gate refused, which leaves
 * the tunnel down, or a requested connection held out of balance, which keeps
 * the tunnel up with no exit. Either way the balance usually comes back by
 * itself: open connections return the reserved data they do not use as they
 * close, and the free data refreshes at 00:00 UTC. So while the user waits,
 * every balance reading is fed in, and once the available balance is back at
 * [BALANCE_RECOVERY_THRESHOLD_BYTES] the connect is retried: the refused start
 * is started, or the held connection is rebuilt. A rebuild asks for new
 * contracts and drops the latched contract status, which nothing else clears
 * while the held connection sends nothing.
 *
 * The retry is bounded:
 * - Once per recovery. A block arms it, and so does a reading below the
 *   threshold; a retry disarms it. Only a reading fetched at or after the
 *   arming counts, so a balance read before the block never retries.
 * - At most [BALANCE_RECOVERY_MAX_RETRIES] in a row. A new ask from the user
 *   refills them, and so does a connection that stays out of the block for
 *   [BALANCE_RECOVERY_BUDGET_RESET_MILLIS] after a retry.
 *
 * It never connects a user who did not ask to connect: only a refused start
 * (the user's own connect) or a connection already requested is retried, and
 * a disconnect, a sign out or Cancel clears the refused start ([clear]).
 *
 * Not thread safe; feed it from one thread.
 */

/**
 * The available balance at which data counts as back for a blocked connect,
 * and the reserved balance whose return could bring it back. The server
 * grants a contract down to 1 MiB, but a connection opens several at once and
 * ramps each to 128 MiB, so a few MiB back would only block again.
 */
internal const val BALANCE_RECOVERY_THRESHOLD_BYTES = 64L * 1024 * 1024

/** Retries in a row before the user has to act again. */
internal const val BALANCE_RECOVERY_MAX_RETRIES = 3

/** How long a retried connection stays out of the block to refill the retries. */
internal const val BALANCE_RECOVERY_BUDGET_RESET_MILLIS = 10L * 60 * 1000

/** The retry one balance observation asks for. */
internal sealed interface BalanceRecoveryStep<out T> {
    /** Nothing to retry now. */
    data object None : BalanceRecoveryStep<Nothing>

    /** The connect the gate refused, to the target the user asked for. */
    data class Start<T>(val target: T) : BalanceRecoveryStep<T>

    /** The held connection, connected again to its location. */
    data object Rebuild : BalanceRecoveryStep<Nothing>
}

/** What the out-of-balance notice says about the recovery. */
internal data class BalanceRecoveryState(
    /** A refused start is waiting for the balance (Cancel clears it). */
    val startWaiting: Boolean = false,
    /** A retry is still allowed: "You'll be reconnected when data is available again." */
    val retriesLeft: Boolean = true,
)

/** The self-recovery of a connect the balance blocked, as the file header describes. */
internal class BalanceRecovery<T>(
    private val thresholdBytes: Long = BALANCE_RECOVERY_THRESHOLD_BYTES,
    private val maxRetries: Int = BALANCE_RECOVERY_MAX_RETRIES,
    private val budgetResetMillis: Long = BALANCE_RECOVERY_BUDGET_RESET_MILLIS,
) {
    // boxed, so a null target (the best available provider) is still a start
    private class RefusedStart<T>(val target: T)

    private var refusedStart: RefusedStart<T>? = null
    private var held = false
    private var armedAtMillis: Long? = null
    private var retries = 0
    private var lastRetryAtMillis = 0L

    val state: BalanceRecoveryState
        get() = BalanceRecoveryState(
            startWaiting = refusedStart != null,
            retriesLeft = retries < maxRetries,
        )

    /**
     * The gate refused a start connect the user asked for: wait for the
     * balance to retry it. A new ask refills the retries.
     */
    fun startRefused(target: T, nowMillis: Long) {
        refusedStart = RefusedStart(target)
        retries = 0
        arm(nowMillis)
    }

    /**
     * The user took the connect into their own hands (connected, disconnected,
     * signed out or cancelled the wait): nothing is waiting any more.
     */
    fun clear() {
        refusedStart = null
        armedAtMillis = null
        retries = 0
    }

    /**
     * Feeds one observation: whether the out-of-balance gate holds, whether a
     * connection is requested, and the last balance reading (null when none is
     * known). Returns the retry to make now, at most one per recovery.
     */
    fun observe(
        gate: Boolean,
        connectRequested: Boolean,
        balance: WidgetBalanceSnapshot?,
        nowMillis: Long,
    ): BalanceRecoveryStep<T> {
        val heldNow = gate && connectRequested
        if (heldNow && !held) {
            // a connection the user asked for is newly held
            arm(nowMillis)
        }
        held = heldNow
        if (connectRequested && !heldNow && 0 < retries &&
            budgetResetMillis <= nowMillis - lastRetryAtMillis
        ) {
            retries = 0
        }
        if (refusedStart == null && !heldNow) {
            armedAtMillis = null
            return BalanceRecoveryStep.None
        }
        if (balance == null) {
            return BalanceRecoveryStep.None
        }
        if (balance.balanceByteCount < thresholdBytes) {
            arm(balance.updatedAtMillis)
            return BalanceRecoveryStep.None
        }
        val armedAt = armedAtMillis ?: return BalanceRecoveryStep.None
        if (balance.updatedAtMillis < armedAt || maxRetries <= retries) {
            return BalanceRecoveryStep.None
        }
        armedAtMillis = null
        retries += 1
        lastRetryAtMillis = nowMillis
        val start = refusedStart ?: return BalanceRecoveryStep.Rebuild
        refusedStart = null
        return BalanceRecoveryStep.Start(start.target)
    }

    /**
     * Arms the recovery at [atMillis] unless it is armed already: only a reading
     * fetched at or after then can retry.
     */
    private fun arm(atMillis: Long) {
        if (armedAtMillis == null) {
            armedAtMillis = atMillis
        }
    }
}
