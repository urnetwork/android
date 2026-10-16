package com.bringyour.network.ui.connect

import com.bringyour.network.widgets.WidgetBalanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The start connect gate's fetch (checkStartConnect). A plan known to be
 * Supporter and a running balance poll are never refused, so they decide at
 * once instead of fetching the balance first (owner report 2026-10-16: the
 * fetch kept a connect tap waiting for up to five seconds, Pro included).
 * Everything else decides exactly as before.
 */
class StartConnectGateTest {

    private val nowMillis = 1_000_000_000L
    private val gib = 1024L * 1024 * 1024

    private fun balance(
        balanceByteCount: Long,
        isPro: Boolean = false,
        ageMillis: Long = 0,
    ) = WidgetBalanceSnapshot(
        updatedAtMillis = nowMillis - ageMillis,
        startBalanceByteCount = gib,
        balanceByteCount = balanceByteCount,
        openTransferByteCount = 0,
        isPro = isPro,
    )

    /** One gate check with a held fetch: its result, if decided, and the fetch. */
    private class Check {
        var fetches = 0
        var result: Boolean? = null
        private var pending: ((WidgetBalanceSnapshot?) -> Unit)? = null

        fun fetch(onBalance: (WidgetBalanceSnapshot?) -> Unit) {
            fetches += 1
            pending = onBalance
        }

        fun answer(balance: WidgetBalanceSnapshot?) {
            val onBalance = pending
            pending = null
            onBalance?.invoke(balance)
        }
    }

    private fun check(
        isProKnown: Boolean = false,
        isPolling: Boolean = false,
        contractInsufficientBalance: Boolean = false,
        cached: WidgetBalanceSnapshot?,
    ): Check {
        val check = Check()
        checkStartConnect(
            isProKnown = { isProKnown },
            isPollingSubscriptionBalance = { isPolling },
            contractInsufficientBalance = { contractInsufficientBalance },
            cached = cached,
            nowMillis = nowMillis,
            fetch = check::fetch,
            onResult = { check.result = it },
        )
        return check
    }

    @Test
    fun knownProStartsWithoutFetchingTheBalance() {
        // a stale empty balance: Pro was still made to wait for the fetch
        val stale = balance(balanceByteCount = 0, ageMillis = 30 * 60 * 1000L)
        val check = check(isProKnown = true, cached = stale)
        assertEquals(0, check.fetches)
        assertEquals(false, check.result)

        // out of balance by contract too: Pro is never refused
        val contract = check(isProKnown = true, contractInsufficientBalance = true, cached = null)
        assertEquals(0, contract.fetches)
        assertEquals(false, contract.result)
    }

    @Test
    fun pollingStartsWithoutFetchingTheBalance() {
        val check = check(isPolling = true, cached = null)
        assertEquals(0, check.fetches)
        assertEquals(false, check.result)
    }

    @Test
    fun staleBalanceIsFetchedWhenThePlanIsNotKnown() {
        val stale = balance(balanceByteCount = gib, ageMillis = 30 * 60 * 1000L)
        val empty = check(cached = stale)
        assertEquals(1, empty.fetches)
        assertNull(empty.result)
        empty.answer(balance(balanceByteCount = 0))
        assertEquals(true, empty.result)

        // the fetched balance can still show Pro
        val pro = check(cached = stale)
        pro.answer(balance(balanceByteCount = 0, isPro = true))
        assertEquals(false, pro.result)

        // a failed fetch never blocks
        val failed = check(cached = stale)
        failed.answer(null)
        assertEquals(false, failed.result)
    }

    @Test
    fun contractOutOfBalanceStillWaitsForTheBalance() {
        // only a known plan skips the fetch: by contract alone a start is
        // refused unless the fetched balance shows Pro
        val refused = check(contractInsufficientBalance = true, cached = null)
        assertEquals(1, refused.fetches)
        refused.answer(balance(balanceByteCount = gib))
        assertEquals(true, refused.result)

        val pro = check(contractInsufficientBalance = true, cached = null)
        pro.answer(balance(balanceByteCount = 0, isPro = true))
        assertEquals(false, pro.result)
    }

    @Test
    fun freshBalanceDecidesAtOnce() {
        val empty = check(cached = balance(balanceByteCount = 0, ageMillis = 5_000))
        assertEquals(0, empty.fetches)
        assertEquals(true, empty.result)

        val funded = check(cached = balance(balanceByteCount = gib))
        assertEquals(0, funded.fetches)
        assertEquals(false, funded.result)
    }
}
