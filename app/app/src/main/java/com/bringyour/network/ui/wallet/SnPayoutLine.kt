package com.bringyour.network.ui.wallet

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * The SN payout line at the foot of the points card: how and when a provider
 * is paid since payouts moved to the UR subnet (WHITEPAPER §5.2, §8.3).
 * Earnings settle every epoch and are paid in SN25α to the Bittensor coldkey
 * once the provider claims them; the app never claims by itself. The times
 * are the current epoch's schedule from the SDK (the coordinator's policy),
 * never durations written into the copy.
 */

/** The current epoch's settlement times, from the SDK's SnEpochSchedule. */
data class SnEpochScheduleState(
    val epoch: Long,
    val endMillis: Long,
    val claimOpenMillis: Long,
    val expiryMillis: Long,
)

/** The three times `sn_payout_schedule_times` names, formatted for the reader. */
data class SnPayoutTimes(
    val epochEnd: String,
    val claimOpen: String,
    val expiry: String,
)

sealed class SnPayoutLine {
    /** no coldkey yet: "Set your Bittensor coldkey to get paid", and the action opens the coldkey flow */
    object SetColdkey : SnPayoutLine()

    /**
     * `sn_payout_schedule`, then `sn_payout_schedule_times` when the epoch schedule
     * is known; [claimable] adds the Claim action
     */
    data class Schedule(
        val times: SnPayoutTimes?,
        val claimable: Boolean,
    ) : SnPayoutLine()
}

/**
 * The line for the card, or null while it is not known whether a coldkey is set
 * (the wallet is loading, or its read failed with none cached). A schedule whose
 * epoch has already ended waits for the next refresh rather than show a past end.
 */
fun snPayoutLine(
    walletKnown: Boolean,
    hasColdkey: Boolean,
    totalClaimableRao: Long,
    schedule: SnEpochScheduleState?,
    nowMillis: Long,
    formatTime: (Long) -> String,
): SnPayoutLine? {
    if (!walletKnown) {
        return null
    }
    if (!hasColdkey) {
        return SnPayoutLine.SetColdkey
    }
    val times = schedule
        ?.takeIf { nowMillis < it.endMillis }
        ?.let {
            SnPayoutTimes(
                epochEnd = formatTime(it.endMillis),
                claimOpen = formatTime(it.claimOpenMillis),
                expiry = formatTime(it.expiryMillis),
            )
        }
    return SnPayoutLine.Schedule(times = times, claimable = 0 < totalClaimableRao)
}

/** A schedule time as the reader's local date and time ("Oct 11, 2026, 2:00 PM"). */
fun formatSnPayoutTime(millis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(millis))

/**
 * The final USDC payout still waiting, or null. Since payouts moved to the UR
 * subnet, the "Final USDC payout: N USDC waiting" line shows only while a USDC
 * amount is pending, and not before the payments have loaded.
 */
fun finalUsdcWaitingUsd(legacy: LegacyWalletUi, legacyLoaded: Boolean): Double? =
    if (legacyLoaded && legacy.hasPending) legacy.pendingUsd else null
