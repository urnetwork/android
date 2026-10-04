package com.bringyour.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UPGRADE.md N1: a purchase acknowledged by a pre-report build (acknowledged on
 * PURCHASED, no server contact) must still be reported to the server once, and a
 * token the server answered terminally must not be reported again.
 */
class PurchaseReportPolicyTest {

    private val terminalStatuses = setOf("credited", "already_credited", "wrong_network", "invalid")

    private class MemoryStore : PurchaseReportPolicy.Store {
        val proofs = mutableMapOf<String, String>()
        val attempts = mutableMapOf<String, Int>()
        val terminal = mutableMapOf<String, String>()

        override fun persist(productId: String, purchaseToken: String) {
            proofs[purchaseToken] = productId
        }

        override fun bumpAttempts(purchaseToken: String) {
            attempts[purchaseToken] = (attempts[purchaseToken] ?: 0) + 1
        }

        override fun markReportedTerminal(purchaseToken: String, status: String) {
            terminal[purchaseToken] = status
        }

        fun action(purchased: Boolean, acknowledged: Boolean, purchaseToken: String) =
            PurchaseReportPolicy.actionFor(
                purchased = purchased,
                acknowledged = acknowledged,
                hasPersistedProof = purchaseToken in proofs,
                reportedTerminal = purchaseToken in terminal,
            )
    }

    @Test
    fun legacyAcknowledgedPurchaseWithoutProofIsReported() {
        // the exact pre-fix blind spot: acknowledged, never persisted, never reported
        val action = PurchaseReportPolicy.actionFor(
            purchased = true,
            acknowledged = true,
            hasPersistedProof = false,
            reportedTerminal = false,
        )
        assertEquals(PurchaseReportPolicy.Action.Report, action)
    }

    @Test
    fun unacknowledgedPurchaseIsReportedThenAcknowledged() {
        val action = PurchaseReportPolicy.actionFor(
            purchased = true,
            acknowledged = false,
            hasPersistedProof = false,
            reportedTerminal = false,
        )
        assertEquals(PurchaseReportPolicy.Action.ReportAndAcknowledge, action)
    }

    @Test
    fun pendingPurchaseIsNotReported() {
        val action = PurchaseReportPolicy.actionFor(
            purchased = false,
            acknowledged = false,
            hasPersistedProof = false,
            reportedTerminal = false,
        )
        assertEquals(PurchaseReportPolicy.Action.None, action)
    }

    @Test
    fun acknowledgedProofLeftByACrashIsReported() {
        val action = PurchaseReportPolicy.actionFor(
            purchased = true,
            acknowledged = true,
            hasPersistedProof = true,
            reportedTerminal = true,
        )
        assertEquals(PurchaseReportPolicy.Action.Report, action)
    }

    @Test
    fun legacyPurchaseIsReportedExactlyOnceAcrossReconciles() = runBlocking<Unit> {
        val store = MemoryStore()
        val token = "legacy-token"
        var verifyCalls = 0

        // two reconciles over the same acknowledged purchase
        repeat(2) {
            if (store.action(purchased = true, acknowledged = true, purchaseToken = token) !=
                PurchaseReportPolicy.Action.None
            ) {
                val status = PurchaseReportPolicy.reportUntilTerminal(
                    store,
                    "supporter",
                    token,
                    maxAttempts = 3,
                    verifyOnce = { verifyCalls += 1; "credited" },
                    isTerminal = { it in terminalStatuses },
                    backoffMillis = { 0L },
                )
                assertEquals("credited", status)
                // the acknowledged purchase's proof is cleared after the terminal answer
                store.proofs.remove(token)
            }
        }

        assertEquals(1, verifyCalls)
        assertEquals("credited", store.terminal[token])
        assertEquals(
            PurchaseReportPolicy.Action.None,
            store.action(purchased = true, acknowledged = true, purchaseToken = token)
        )
    }

    @Test
    fun transportFailureKeepsTheProofAndLeavesTheTokenUnflagged() = runBlocking<Unit> {
        val store = MemoryStore()
        val token = "legacy-token"

        val status = PurchaseReportPolicy.reportUntilTerminal(
            store,
            "supporter",
            token,
            maxAttempts = 3,
            verifyOnce = { null },
            isTerminal = { it in terminalStatuses },
            backoffMillis = { 0L },
        )

        assertNull(status)
        assertEquals("supporter", store.proofs[token])
        assertEquals(3, store.attempts[token])
        assertTrue(token !in store.terminal)
        // the next reconcile (or the worker) reports it again
        assertEquals(
            PurchaseReportPolicy.Action.Report,
            store.action(purchased = true, acknowledged = true, purchaseToken = token)
        )
    }

    @Test
    fun pendingAnswerIsRetriedUntilTerminal() = runBlocking<Unit> {
        val store = MemoryStore()
        val answers = ArrayDeque(listOf("pending", null, "already_credited"))

        val status = PurchaseReportPolicy.reportUntilTerminal(
            store,
            "supporter",
            "token",
            maxAttempts = 3,
            verifyOnce = { answers.removeFirst() },
            isTerminal = { it in terminalStatuses },
            backoffMillis = { 0L },
        )

        assertEquals("already_credited", status)
        assertEquals(2, store.attempts["token"])
        assertEquals("already_credited", store.terminal["token"])
    }

    /**
     * UPGRADE.md A1 (Play): a purchase made outside the app's billing flow (a Play
     * Store promo code redemption) carries no obfuscated account id. The server
     * credits it only to the network it is bound to through the issued welcome
     * offer; for any other network it answers invalid. That answer means "not this
     * network's" -- the purchase is real and stays acknowledged -- so it must not
     * be surfaced as a verification error on every reconcile that finds it.
     */
    @Test
    fun invalidAnswerForUnlinkedPurchaseIsNotAnError() {
        assertNotEquals(
            PurchaseReportPolicy.Outcome.Invalid,
            PurchaseReportPolicy.outcomeFor(
                credited = false,
                wrongNetwork = false,
                invalid = true,
                linkedToAccount = false,
            )
        )
    }

    @Test
    fun invalidAnswerForUnlinkedPurchaseIsNotThisNetworks() {
        assertEquals(
            PurchaseReportPolicy.Outcome.NotThisNetwork,
            PurchaseReportPolicy.outcomeFor(
                credited = false,
                wrongNetwork = false,
                invalid = true,
                linkedToAccount = false,
            )
        )
    }

    @Test
    fun invalidAnswerForLinkedPurchaseIsAnError() {
        // a purchase this app launched for this network that the server will never
        // credit: the user must hear about it
        assertEquals(
            PurchaseReportPolicy.Outcome.Invalid,
            PurchaseReportPolicy.outcomeFor(
                credited = false,
                wrongNetwork = false,
                invalid = true,
                linkedToAccount = true,
            )
        )
    }

    @Test
    fun creditedUnlinkedPurchaseIsThisNetworks() {
        // a promo redemption the server bound to this network: success, and the
        // confirmation poll starts like any credited purchase
        assertEquals(
            PurchaseReportPolicy.Outcome.Credited,
            PurchaseReportPolicy.outcomeFor(
                credited = true,
                wrongNetwork = false,
                invalid = false,
                linkedToAccount = false,
            )
        )
        assertEquals(
            PurchaseReportPolicy.Outcome.Deferred,
            PurchaseReportPolicy.outcomeFor(
                credited = false,
                wrongNetwork = false,
                invalid = false,
                linkedToAccount = false,
            )
        )
        assertEquals(
            PurchaseReportPolicy.Outcome.WrongNetwork,
            PurchaseReportPolicy.outcomeFor(
                credited = false,
                wrongNetwork = true,
                invalid = false,
                linkedToAccount = true,
            )
        )
    }
}
