package com.bringyour.network

import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.acknowledgePurchase
import com.bringyour.sdk.Api
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.VerifyPlayPurchaseArgs
import com.bringyour.sdk.VerifyPlayPurchaseCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * The ONE persist -> report -> acknowledge sequence for Play purchases, shared by the
 * in-app path (PlanViewModel) and the durable backstop (PendingPurchaseReconcileWorker).
 *
 * The contract (sdk/purchase_report.go, closing UPGRADE.md finding N1):
 *
 *  1. PERSIST the purchase token the moment Play reports PURCHASED, before anything
 *     else, so process death loses nothing.
 *  2. REPORT it to the server (Api.verifyPlayPurchase), retrying on transport failure
 *     or a `pending` answer with Sdk.purchaseReportBackoffMillis between attempts,
 *     until the server answers a TERMINAL status: credited, already_credited,
 *     wrong_network, or invalid.
 *  3. Only THEN acknowledge with Play and drop the persisted token.
 *
 * Acknowledging early destroys the safety net: an acknowledged purchase is never
 * redelivered by Play, so if the server never saw the token (lost webhook AND lost
 * report), the money is gone. wrong_network and invalid still acknowledge -- the
 * purchase is real and Play must not auto-refund it -- but the caller surfaces the
 * situation to the user.
 *
 * In-session reporting is bounded (MAX_REPORT_ATTEMPTS_PER_SESSION); past that the
 * persisted token stays and the daily PendingPurchaseReconcileWorker carries the
 * report until terminal. Every re-report is idempotent server side.
 */
object PurchaseReporter {

    /**
     * Same prefs file the worker's pending marker lives in, so one durable store
     * backs the whole backstop. Keys:
     *  - report_product.<purchaseToken> -> product id (presence == proof persisted)
     *  - report_attempts.<purchaseToken> -> total report attempts so far
     */
    private const val PREFS_NAME = "pending_purchase_reconcile"
    private const val KEY_PRODUCT_PREFIX = "report_product."
    private const val KEY_ATTEMPTS_PREFIX = "report_attempts."

    /**
     * Bounded in-session report attempts (initial + 2 retries at 1 s and 5 s backoff);
     * past that the daily worker owns the retry loop.
     */
    const val MAX_REPORT_ATTEMPTS_PER_SESSION = 3

    /** The only subscription sku this app sells; fallback when Play omits products. */
    private const val DEFAULT_PRODUCT_ID = "supporter"

    data class Entry(
        val purchaseToken: String,
        val productId: String,
        val attempts: Int,
    )

    /**
     * Outcome of one persist -> report -> acknowledge pass.
     * `status` is a terminal PurchaseReportStatus* wire value, or null when this
     * session gave up without a terminal answer (token stays persisted, purchase
     * stays unacknowledged, the worker carries it).
     */
    data class Result(
        val status: String?,
        val acknowledged: Boolean,
    ) {
        /** The server actually credited this network -- the only success signal. */
        val credited: Boolean
            get() = status == Sdk.PurchaseReportStatusCredited ||
                    status == Sdk.PurchaseReportStatusAlreadyCredited

        val wrongNetwork: Boolean get() = status == Sdk.PurchaseReportStatusWrongNetwork

        val invalid: Boolean get() = status == Sdk.PurchaseReportStatusInvalid
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun productIdOf(purchase: Purchase): String =
        purchase.products.firstOrNull() ?: DEFAULT_PRODUCT_ID

    /**
     * Step 1 of the contract: durably persist the proof of purchase. Idempotent;
     * never resets an existing attempt count.
     */
    fun persist(context: Context, purchase: Purchase) =
        persist(context, productIdOf(purchase), purchase.purchaseToken)

    fun persist(context: Context, productId: String, purchaseToken: String) {
        prefs(context)
            .edit()
            .putString(KEY_PRODUCT_PREFIX + purchaseToken, productId)
            .apply()
    }

    fun hasEntries(context: Context): Boolean =
        prefs(context).all.keys.any { it.startsWith(KEY_PRODUCT_PREFIX) }

    fun hasEntry(context: Context, purchaseToken: String): Boolean =
        prefs(context).contains(KEY_PRODUCT_PREFIX + purchaseToken)

    fun entries(context: Context): List<Entry> {
        val all = prefs(context).all
        return all.keys
            .filter { it.startsWith(KEY_PRODUCT_PREFIX) }
            .map { key ->
                val token = key.removePrefix(KEY_PRODUCT_PREFIX)
                Entry(
                    purchaseToken = token,
                    productId = all[key] as? String ?: DEFAULT_PRODUCT_ID,
                    attempts = all[KEY_ATTEMPTS_PREFIX + token] as? Int ?: 0,
                )
            }
    }

    /** Step 3's tail: the proof reached a terminal answer AND Play acknowledged. */
    fun clear(context: Context, purchaseToken: String) {
        prefs(context)
            .edit()
            .remove(KEY_PRODUCT_PREFIX + purchaseToken)
            .remove(KEY_ATTEMPTS_PREFIX + purchaseToken)
            .apply()
    }

    private fun bumpAttempts(context: Context, purchaseToken: String) {
        val p = prefs(context)
        p.edit()
            .putInt(
                KEY_ATTEMPTS_PREFIX + purchaseToken,
                p.getInt(KEY_ATTEMPTS_PREFIX + purchaseToken, 0) + 1
            )
            .apply()
    }

    /**
     * Step 2 of the contract: report the persisted proof until the server answers a
     * terminal status, bounded to `maxAttempts` reports this session. Returns the
     * terminal status, or null when the session gave up (transport failure or
     * `pending` every time) -- the proof stays persisted either way; it is only
     * dropped after a successful acknowledge (clear).
     *
     * A null `api` (network space not up, e.g. a worker run before login state
     * loads) counts as a transport failure: not terminal, retry later.
     */
    suspend fun report(
        context: Context,
        api: Api?,
        productId: String,
        purchaseToken: String,
        maxAttempts: Int = MAX_REPORT_ATTEMPTS_PER_SESSION,
    ): String? {
        persist(context, productId, purchaseToken)

        var attemptsThisSession = 0
        while (true) {
            val status = if (api == null) null else verifyOnce(api, productId, purchaseToken)
            if (status != null && Sdk.isPurchaseReportTerminal(status)) {
                return status
            }
            bumpAttempts(context, purchaseToken)
            attemptsThisSession += 1
            if (maxAttempts <= attemptsThisSession) {
                Log.i(
                    TAG,
                    "PurchaseReporter: no terminal answer after $attemptsThisSession " +
                            "attempts (last status: $status); the daily reconcile carries it"
                )
                return null
            }
            delay(Sdk.purchaseReportBackoffMillis(attemptsThisSession - 1))
        }
    }

    /**
     * The full persist -> report -> acknowledge sequence for one purchase.
     *
     * NEVER acknowledges before a terminal status. On terminal -- including
     * wrong_network and invalid, where the purchase is real and Play must not
     * auto-refund it -- acknowledges and drops the persisted proof. An acknowledge
     * failure keeps the proof persisted and re-arms the daily worker (re-reporting
     * is idempotent; the worker retries the acknowledge before Play's 3-day
     * auto-refund clock wins).
     */
    suspend fun reportAndAcknowledge(
        context: Context,
        api: Api?,
        billingClient: BillingClient,
        purchase: Purchase,
        maxAttempts: Int = MAX_REPORT_ATTEMPTS_PER_SESSION,
    ): Result {
        val status = report(
            context,
            api,
            productIdOf(purchase),
            purchase.purchaseToken,
            maxAttempts
        ) ?: return Result(status = null, acknowledged = false)

        if (purchase.isAcknowledged) {
            // e.g. re-reporting a proof whose acknowledge landed but whose clear was
            // lost to process death
            clear(context, purchase.purchaseToken)
            return Result(status, acknowledged = true)
        }

        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val ackResult = billingClient.acknowledgePurchase(ackParams)
        return if (ackResult.responseCode == BillingResponseCode.OK) {
            clear(context, purchase.purchaseToken)
            Result(status, acknowledged = true)
        } else {
            Log.i(
                TAG,
                "PurchaseReporter: acknowledge error after terminal status $status: " +
                        "${ackResult.responseCode} ${ackResult.debugMessage}"
            )
            PendingPurchaseReconcileWorker.markPendingSeen(context)
            Result(status, acknowledged = false)
        }
    }

    private suspend fun verifyOnce(
        api: Api,
        productId: String,
        purchaseToken: String,
    ): String? = suspendCancellableCoroutine { continuation ->
        val args = VerifyPlayPurchaseArgs()
        args.productId = productId
        args.purchaseToken = purchaseToken

        // defensive: resume exactly once even if the callback misbehaves
        val resumed = AtomicBoolean(false)
        api.verifyPlayPurchase(args, VerifyPlayPurchaseCallback { result, err ->
            if (resumed.compareAndSet(false, true)) {
                if (err != null || result == null) {
                    // transport failure: no status, never terminal
                    continuation.resume(null)
                } else {
                    continuation.resume(result.status)
                }
            }
        })
    }
}
