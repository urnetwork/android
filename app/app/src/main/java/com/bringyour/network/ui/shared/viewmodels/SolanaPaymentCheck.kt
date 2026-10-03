package com.bringyour.network.ui.shared.viewmodels

/**
 * The Solana Pay return path (UPGRADE.md N6). The app hands the payment to a wallet
 * app and, when it comes back to the foreground, polls the balance for the payment to
 * land. Two things used to break that:
 *
 *  - the payment reference lived only in memory, so the system killing the app while
 *    the wallet was in front lost it, and the app came back without checking;
 *  - the check gave up silently after 20 s, shorter than a typical finality plus
 *    webhook latency, leaving a paying user with no word at all.
 *
 * The pending payment (reference, plan, quoted amount) is now persisted until the
 * check ends, the check runs for up to two minutes, and the user hears about it:
 * a "still checking" notice once the check passes the old 20 s, and the
 * confirmation-delayed notice if two minutes pass without the payment landing.
 */
internal data class PendingSolanaPayment(
    val reference: String,
    val plan: String,
    val amountUsd: Double,
    val createdAtMillis: Long,
)

/**
 * Durable storage for the one pending Solana payment. A record older than
 * [MAX_AGE_MILLIS] is dropped on load: a wallet hand-off that old is not coming back,
 * and the balance poll picks up a late payment anyway.
 */
internal class PendingSolanaPaymentStore(
    private val prefs: Prefs,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    /** The few SharedPreferences operations the store needs. */
    interface Prefs {
        fun getString(key: String): String?
        fun putStrings(values: Map<String, String?>)
    }

    fun save(payment: PendingSolanaPayment) {
        prefs.putStrings(
            mapOf(
                KEY_REFERENCE to payment.reference,
                KEY_PLAN to payment.plan,
                KEY_AMOUNT_USD to payment.amountUsd.toString(),
                KEY_CREATED_AT to payment.createdAtMillis.toString(),
            )
        )
    }

    fun load(): PendingSolanaPayment? {
        val reference = prefs.getString(KEY_REFERENCE)?.takeIf { it.isNotEmpty() } ?: return null
        val createdAtMillis = prefs.getString(KEY_CREATED_AT)?.toLongOrNull() ?: 0L
        if (MAX_AGE_MILLIS < nowMillis() - createdAtMillis) {
            clear()
            return null
        }
        return PendingSolanaPayment(
            reference = reference,
            plan = prefs.getString(KEY_PLAN) ?: "",
            amountUsd = prefs.getString(KEY_AMOUNT_USD)?.toDoubleOrNull() ?: 0.0,
            createdAtMillis = createdAtMillis,
        )
    }

    fun clear() {
        prefs.putStrings(
            mapOf(
                KEY_REFERENCE to null,
                KEY_PLAN to null,
                KEY_AMOUNT_USD to null,
                KEY_CREATED_AT to null,
            )
        )
    }

    companion object {
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

        private const val KEY_REFERENCE = "reference"
        private const val KEY_PLAN = "plan"
        private const val KEY_AMOUNT_USD = "amount_usd"
        private const val KEY_CREATED_AT = "created_at"
    }
}

internal object SolanaPaymentCheck {
    /** How long the return-path check polls the balance. */
    const val MAX_DURATION_MILLIS = 120_000L

    /** When the check tells the user it is still going (the old, silent cap). */
    const val STILL_CHECKING_AFTER_MILLIS = 20_000L

    enum class Notice {
        None,
        StillChecking,
        TimedOut,
    }

    /**
     * What to tell the user at this point of a check. `stillCheckingShown` keeps the
     * still-checking notice to once per check.
     */
    fun noticeFor(
        elapsedMillis: Long,
        expired: Boolean,
        confirmed: Boolean,
        stillCheckingShown: Boolean,
    ): Notice {
        if (confirmed) {
            return Notice.None
        }
        if (expired) {
            return Notice.TimedOut
        }
        if (!stillCheckingShown && STILL_CHECKING_AFTER_MILLIS <= elapsedMillis) {
            return Notice.StillChecking
        }
        return Notice.None
    }
}
