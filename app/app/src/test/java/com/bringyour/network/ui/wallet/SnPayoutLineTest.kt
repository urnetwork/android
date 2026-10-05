package com.bringyour.network.ui.wallet

import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnPayoutLineTest {

    // the SDK's schedule for an epoch closing 2026-10-13 00:00 UTC on the mainnet
    // policy: claims open 14,400 blocks (two days) later, and the share expires at
    // the end of epoch e+9 (453,600 blocks after the close, less one block)
    private val schedule = SnEpochScheduleState(
        epoch = 9,
        endMillis = 1_791_849_600_000,
        claimOpenMillis = 1_791_849_600_000 + 14_400 * 12_000L,
        expiryMillis = 1_791_849_600_000 + (453_600 - 1) * 12_000L,
    )

    // 2026-10-06 00:00 UTC, a week before the close
    private val now = 1_791_244_800_000

    // names each time by the millis it was formatted from
    private val tag: (Long) -> String = { "t$it" }

    private val taggedTimes = SnPayoutTimes(
        epochEnd = "t${schedule.endMillis}",
        claimOpen = "t${schedule.claimOpenMillis}",
        expiry = "t${schedule.expiryMillis}",
    )

    // recent CLDR data puts a narrow no-break space before the meridiem
    private fun us(millis: Long, zone: ZoneId): String =
        formatSnPayoutTime(millis, zone, Locale.US).replace(' ', ' ')

    @Test
    fun `no coldkey asks for one`() {
        assertEquals(SnPayoutLine.SetColdkey, snPayoutLine(true, false, 0, schedule, now, tag))
        // alpha goes only to a coldkey, whatever the chain says
        assertEquals(SnPayoutLine.SetColdkey, snPayoutLine(true, false, 3_241_000_000, schedule, now, tag))
        assertEquals(SnPayoutLine.SetColdkey, snPayoutLine(true, false, 0, null, now, tag))
    }

    @Test
    fun `nothing shows while it is unknown whether a coldkey is set`() {
        assertNull(snPayoutLine(false, false, 0, schedule, now, tag))
        assertNull(snPayoutLine(false, true, 3_241_000_000, schedule, now, tag))
    }

    @Test
    fun `nothing claimable explains the schedule without Claim`() {
        assertEquals(
            SnPayoutLine.Schedule(taggedTimes, claimable = false),
            snPayoutLine(true, true, 0, schedule, now, tag)
        )
    }

    @Test
    fun `something claimable adds Claim`() {
        assertEquals(
            SnPayoutLine.Schedule(taggedTimes, claimable = true),
            snPayoutLine(true, true, 3_241_000_000, schedule, now, tag)
        )
    }

    @Test
    fun `without the epoch schedule the explanation stands alone`() {
        assertEquals(SnPayoutLine.Schedule(null, claimable = false), snPayoutLine(true, true, 0, null, now, tag))
        assertEquals(SnPayoutLine.Schedule(null, claimable = true), snPayoutLine(true, true, 1, null, now, tag))
    }

    @Test
    fun `an epoch that has ended waits for the next read`() {
        assertEquals(
            SnPayoutLine.Schedule(null, claimable = false),
            snPayoutLine(true, true, 0, schedule, schedule.endMillis, tag)
        )
        assertEquals(
            SnPayoutLine.Schedule(taggedTimes, claimable = false),
            snPayoutLine(true, true, 0, schedule, schedule.endMillis - 1, tag)
        )
    }

    @Test
    fun `the epoch end, claim open and expiry are local dates and times`() {
        val line = snPayoutLine(true, true, 0, schedule, now) { us(it, ZoneOffset.UTC) } as SnPayoutLine.Schedule
        assertEquals(
            SnPayoutTimes(
                epochEnd = "Oct 13, 2026, 12:00 AM",
                claimOpen = "Oct 15, 2026, 12:00 AM",
                expiry = "Dec 14, 2026, 11:59 PM",
            ),
            line.times
        )
    }

    @Test
    fun `times follow the reader's zone and locale`() {
        assertEquals("Oct 12, 2026, 5:00 PM", us(schedule.endMillis, ZoneId.of("America/Los_Angeles")))
        assertEquals(
            "15.10.2026, 02:00",
            formatSnPayoutTime(schedule.claimOpenMillis, ZoneId.of("Europe/Berlin"), Locale.GERMANY)
        )
    }

    @Test
    fun `the final USDC payout shows only while USDC is pending`() {
        fun payment(usd: Double, completed: Boolean = false, canceled: Boolean = false) =
            LegacyPayment("payment", null, usd, 0.0, completed, canceled, null)

        val held = LegacyWalletUi(payments = listOf(payment(3.50), payment(0.37)))
        assertEquals(3.87, finalUsdcWaitingUsd(held, legacyLoaded = true)!!, 1e-9)
        // not before the payments have loaded
        assertNull(finalUsdcWaitingUsd(held, legacyLoaded = false))
        // paid out or canceled: nothing waits
        assertNull(finalUsdcWaitingUsd(LegacyWalletUi(payments = listOf(payment(3.87, completed = true))), true))
        assertNull(finalUsdcWaitingUsd(LegacyWalletUi(payments = listOf(payment(3.87, canceled = true))), true))
        // a remainder that would read 0.00
        assertNull(finalUsdcWaitingUsd(LegacyWalletUi(payments = listOf(payment(0.004))), true))
        assertNull(finalUsdcWaitingUsd(LegacyWalletUi(), true))
    }
}
