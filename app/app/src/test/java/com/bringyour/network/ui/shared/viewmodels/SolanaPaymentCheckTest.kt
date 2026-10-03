package com.bringyour.network.ui.shared.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * UPGRADE.md N6: the Solana Pay return path lost its reference on process death and
 * gave up silently after 20 s.
 */
class SolanaPaymentCheckTest {

    /** A SharedPreferences file that outlives any one process. */
    private class DiskPrefs : PendingSolanaPaymentStore.Prefs {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putStrings(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
        }
    }

    @Test
    fun pendingPaymentSurvivesProcessDeath() {
        val disk = DiskPrefs()
        val now = 1_000_000L
        PendingSolanaPaymentStore(disk, nowMillis = { now }).save(
            PendingSolanaPayment("ref-1", "yearly", 40.0, createdAtMillis = now)
        )

        // the process dies while the wallet is in front; a new process reads the store
        val restarted = PendingSolanaPaymentStore(disk, nowMillis = { now + 60_000L })

        assertEquals(
            PendingSolanaPayment("ref-1", "yearly", 40.0, createdAtMillis = now),
            restarted.load()
        )
    }

    @Test
    fun clearedPaymentIsGone() {
        val disk = DiskPrefs()
        val store = PendingSolanaPaymentStore(disk, nowMillis = { 0L })
        store.save(PendingSolanaPayment("ref-1", "yearly", 40.0, createdAtMillis = 0L))

        store.clear()

        assertNull(PendingSolanaPaymentStore(disk, nowMillis = { 0L }).load())
        assertEquals(emptyMap<String, String>(), disk.values)
    }

    @Test
    fun staleRecordIsDropped() {
        val disk = DiskPrefs()
        PendingSolanaPaymentStore(disk, nowMillis = { 0L }).save(
            PendingSolanaPayment("ref-1", "yearly", 40.0, createdAtMillis = 0L)
        )

        val later = PendingSolanaPaymentStore(
            disk,
            nowMillis = { PendingSolanaPaymentStore.MAX_AGE_MILLIS + 1L }
        )

        assertNull(later.load())
        assertEquals(emptyMap<String, String>(), disk.values)
    }

    @Test
    fun checkOutlastsTheOldTwentySecondCap() {
        // typical finality plus webhook latency needs more than 20 s
        assertEquals(120_000L, SolanaPaymentCheck.MAX_DURATION_MILLIS)
    }

    @Test
    fun stillCheckingIsShownOnceAfterTwentySeconds() {
        assertEquals(
            SolanaPaymentCheck.Notice.None,
            SolanaPaymentCheck.noticeFor(15_000L, expired = false, confirmed = false, stillCheckingShown = false)
        )
        assertEquals(
            SolanaPaymentCheck.Notice.StillChecking,
            SolanaPaymentCheck.noticeFor(20_000L, expired = false, confirmed = false, stillCheckingShown = false)
        )
        assertEquals(
            SolanaPaymentCheck.Notice.None,
            SolanaPaymentCheck.noticeFor(40_000L, expired = false, confirmed = false, stillCheckingShown = true)
        )
    }

    @Test
    fun unconfirmedCheckEndsWithTheTimeoutNoticeNotSilence() {
        assertEquals(
            SolanaPaymentCheck.Notice.TimedOut,
            SolanaPaymentCheck.noticeFor(120_000L, expired = true, confirmed = false, stillCheckingShown = true)
        )
    }

    @Test
    fun confirmedCheckShowsNothing() {
        assertEquals(
            SolanaPaymentCheck.Notice.None,
            SolanaPaymentCheck.noticeFor(120_000L, expired = true, confirmed = true, stillCheckingShown = true)
        )
        assertEquals(
            SolanaPaymentCheck.Notice.None,
            SolanaPaymentCheck.noticeFor(30_000L, expired = false, confirmed = true, stillCheckingShown = false)
        )
    }
}
