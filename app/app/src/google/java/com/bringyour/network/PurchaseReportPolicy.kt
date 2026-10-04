package com.bringyour.network

import kotlinx.coroutines.delay

/**
 * The platform-free half of the Play purchase report (UPGRADE.md N1): which purchases
 * still need a server report, and the report-until-terminal loop. PurchaseReporter
 * binds it to SharedPreferences, the SDK verify call and the SDK backoff; the unit
 * tests bind it to fakes.
 *
 * A purchase token is reported until the server answers a terminal status at least
 * once, and that answer is remembered per token (the reported-terminal flag). The
 * flag, not Play's acknowledged bit, decides whether the server has seen a token:
 * purchases acknowledged by builds that predate the report path (they acknowledged
 * on PURCHASED with no server contact) carry no flag, so the first reconcile after
 * the update reports them once -- the one-time legacy sweep -- and the flag keeps
 * every later reconcile quiet.
 */
internal object PurchaseReportPolicy {

    enum class Action {
        /** Not a completed purchase, or already reported to a terminal answer. */
        None,

        /** Unacknowledged: report until terminal, then acknowledge. */
        ReportAndAcknowledge,

        /**
         * Already acknowledged but never reported to a terminal answer (a legacy
         * purchase, or a crash between acknowledge and clear): report only.
         */
        Report,
    }

    fun actionFor(
        purchased: Boolean,
        acknowledged: Boolean,
        hasPersistedProof: Boolean,
        reportedTerminal: Boolean,
    ): Action {
        if (!purchased) {
            return Action.None
        }
        if (!acknowledged) {
            return Action.ReportAndAcknowledge
        }
        if (hasPersistedProof || !reportedTerminal) {
            return Action.Report
        }
        return Action.None
    }

    /** What a terminal (or missing) report answer means for the user. */
    enum class Outcome {
        /** credited / already_credited: this network has the purchase. */
        Credited,

        /** The purchase is linked to a different network than the session's. */
        WrongNetwork,

        /** The server will never credit this purchase: surfaced as an error. */
        Invalid,

        /**
         * `invalid` for a purchase with no obfuscated account id (bought outside the
         * app's billing flow, e.g. a Play Store promo code redemption). The server
         * credits such a purchase only to the network it is bound to through the
         * issued welcome offer, so for this network it is simply not this network's:
         * acknowledged (it is real), never surfaced as an error.
         */
        NotThisNetwork,

        /** No terminal answer this session; the daily worker carries it. */
        Deferred,
    }

    /**
     * Classifies one report answer. `linkedToAccount` is whether Play's purchase
     * carries an obfuscated account id (set at billing-flow launch).
     */
    fun outcomeFor(
        credited: Boolean,
        wrongNetwork: Boolean,
        invalid: Boolean,
        linkedToAccount: Boolean,
    ): Outcome = when {
        credited -> Outcome.Credited
        wrongNetwork -> Outcome.WrongNetwork
        invalid && !linkedToAccount -> Outcome.NotThisNetwork
        invalid -> Outcome.Invalid
        else -> Outcome.Deferred
    }

    /** Durable per-token report state. */
    interface Store {
        fun persist(productId: String, purchaseToken: String)
        fun bumpAttempts(purchaseToken: String)
        fun markReportedTerminal(purchaseToken: String, status: String)
    }

    /**
     * Persists the proof, then reports it until a terminal answer, bounded to
     * `maxAttempts` reports. Returns the terminal status (and flags the token), or
     * null when the attempts ran out -- the proof stays persisted for the worker.
     * A null `verifyOnce` answer is a transport failure.
     */
    suspend fun reportUntilTerminal(
        store: Store,
        productId: String,
        purchaseToken: String,
        maxAttempts: Int,
        verifyOnce: suspend () -> String?,
        isTerminal: (String) -> Boolean,
        backoffMillis: (Int) -> Long,
    ): String? {
        store.persist(productId, purchaseToken)

        var attempts = 0
        while (true) {
            val status = verifyOnce()
            if (status != null && isTerminal(status)) {
                store.markReportedTerminal(purchaseToken, status)
                return status
            }
            store.bumpAttempts(purchaseToken)
            attempts += 1
            if (maxAttempts <= attempts) {
                return null
            }
            delay(backoffMillis(attempts - 1))
        }
    }
}
