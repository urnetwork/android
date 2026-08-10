package com.bringyour.network

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Durable backstop for PENDING Play purchases and for purchases whose server report
 * has not reached a terminal answer.
 *
 * A PENDING purchase (parental approval, out-of-band payment) becomes PURCHASED
 * whenever Play decides -- possibly days later, possibly while the app is closed. If
 * nothing acknowledges it within Play's 3-day window, Play auto-refunds an APPROVED
 * purchase. Before this worker existed the only acknowledgement path was
 * `reconcileExistingSubscriptions` on Activity recreation, i.e. the user had to
 * happen to open the app in time.
 *
 * This worker is also what makes report-before-acknowledge (UPGRADE.md N1) survive
 * process death: it wakes, finds the persisted purchase token or the
 * still-unacknowledged purchase, REPORTS it to the server, and only acknowledges
 * once the server answers a terminal status (PurchaseReporter runs the identical
 * sequence for the in-app path). Every re-report is idempotent server side.
 *
 * Lifecycle:
 *  - `markPendingSeen` is called the moment a PENDING purchase, a persisted-but-
 *    unreported purchase, or a failed acknowledgement is observed. It persists a
 *    marker and schedules this worker daily (network-constrained). WorkManager
 *    persists the schedule across process death and reboot.
 *  - Each run performs the same reconcile the app does on start:
 *    queryPurchasesAsync(SUBS) -> persist -> report -> acknowledge every
 *    PURCHASED && !isAcknowledged, and re-reports any leftover persisted proof.
 *  - Once a run (or the in-app reconcile, via `markSettled`) observes no PENDING,
 *    no unacknowledged purchase, and no persisted proof, the marker is cleared and
 *    the job cancels itself.
 */
class PendingPurchaseReconcileWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val client = BillingClient.newBuilder(applicationContext)
            // required by the builder; purchase updates are handled by the query below
            .setListener { _, _ -> }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        try {
            if (!connect(client)) {
                Log.i(TAG, "PendingPurchaseReconcileWorker: billing connection failed, will retry")
                return Result.retry()
            }

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val queryResult = client.queryPurchasesAsync(params)
            if (queryResult.billingResult.responseCode != BillingResponseCode.OK) {
                Log.i(
                    TAG,
                    "PendingPurchaseReconcileWorker: queryPurchasesAsync error " +
                            "${queryResult.billingResult.responseCode} ${queryResult.billingResult.debugMessage}"
                )
                return Result.retry()
            }

            val purchases = queryResult.purchasesList
            val api = (applicationContext as? MainApplication)?.api

            var unresolved = false

            /**
             * The full persist -> report -> acknowledge sequence for every purchase
             * that still needs it: unacknowledged PURCHASED purchases, plus
             * acknowledged ones that still carry a persisted proof (a crash between
             * acknowledge and clear -- the re-report is idempotent and clears it).
             */
            purchases
                .filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            (!it.isAcknowledged ||
                                    PurchaseReporter.hasEntry(applicationContext, it.purchaseToken))
                }
                .forEach { purchase ->
                    val result = PurchaseReporter.reportAndAcknowledge(
                        applicationContext,
                        api,
                        client,
                        purchase
                    )
                    if (result.status == null || !result.acknowledged) {
                        unresolved = true
                        Log.i(
                            TAG,
                            "PendingPurchaseReconcileWorker: purchase not settled " +
                                    "(status ${result.status}, acknowledged ${result.acknowledged})"
                        )
                    }
                }

            /**
             * Persisted proofs whose purchase Play no longer returns (account
             * switched on the device, subscription expired, refunded): there is
             * nothing to acknowledge, but the server must still get its terminal
             * answer -- a lost webhook plus a dropped proof is money gone. Terminal
             * drops the proof; anything else keeps the daily job alive.
             */
            val visibleTokens = purchases.map { it.purchaseToken }.toSet()
            PurchaseReporter.entries(applicationContext)
                .filter { it.purchaseToken !in visibleTokens }
                .forEach { entry ->
                    val status = PurchaseReporter.report(
                        applicationContext,
                        api,
                        entry.productId,
                        entry.purchaseToken
                    )
                    if (status != null) {
                        PurchaseReporter.clear(applicationContext, entry.purchaseToken)
                    } else {
                        unresolved = true
                    }
                }

            val stillPending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }

            if (!stillPending && !unresolved && !PurchaseReporter.hasEntries(applicationContext)) {
                // nothing pending, nothing unacknowledged, nothing unreported:
                // the backstop is done
                markSettled(applicationContext)
            }
            // still pending (or a report/acknowledge did not settle): leave the
            // daily job in place -- the next run reconciles again
            return Result.success()
        } finally {
            client.endConnection()
        }
    }

    private suspend fun connect(client: BillingClient): Boolean =
        suspendCancellableCoroutine { continuation ->
            // onBillingServiceDisconnected can fire after onBillingSetupFinished --
            // resume exactly once
            val resumed = AtomicBoolean(false)
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(billingResult.responseCode == BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(false)
                    }
                }
            })
        }

    companion object {
        private const val UNIQUE_WORK_NAME = "pending_purchase_reconcile"
        private const val PREFS_NAME = "pending_purchase_reconcile"
        private const val KEY_PENDING_SEEN = "pending_seen"

        /**
         * A PENDING purchase (or a failed acknowledgement) was observed: persist the
         * marker and make sure the daily reconcile job is scheduled.
         */
        fun markPendingSeen(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING_SEEN, true)
                .apply()
            schedule(context)
        }

        /**
         * Reconcile observed no PENDING and no unacknowledged purchase: clear the
         * marker and cancel the job.
         */
        fun markSettled(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_SEEN)
                .apply()
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        /**
         * Belt-and-braces on app start: WorkManager already persists the schedule,
         * but if the marker survived (e.g. an app update dropped the job) put the
         * job back.
         */
        fun ensureScheduledIfPendingSeen(context: Context) {
            val pendingSeen = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PENDING_SEEN, false)
            if (pendingSeen) {
                schedule(context)
            }
        }

        private fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PendingPurchaseReconcileWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
