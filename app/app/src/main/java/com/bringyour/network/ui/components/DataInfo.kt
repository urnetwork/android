package com.bringyour.network.ui.components

import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.utils.formatBalanceBytes

/**
 * The "About your data" sheet, kept pure so it is unit testable without an
 * Android runtime.
 *
 * The sheet explains the usage bar: Used, Pending (balance held by open
 * connections, returned when they close) and Available, the daily balance the
 * server reports (`start_balance_byte_count`, never a hard-coded amount), and
 * when the free data refreshes. The server grants the free daily balance at
 * 00:00 UTC to every network without Pro (RefreshFreeTransferBalances), so the
 * refresh is computed on the device as the next UTC midnight.
 *
 * It opens from the info button next to the daily balance in the usage bar
 * and from the Why? link under the out-of-balance notice.
 */

internal const val DAY_MILLIS = 24L * 60 * 60 * 1000

private const val MINUTE_MILLIS = 60_000L

/**
 * The next free data refresh: the first 00:00 UTC strictly after [nowMillis].
 * Epoch time has no leap seconds, so UTC days are whole multiples of a day.
 */
internal fun nextFreeRefreshMillis(nowMillis: Long): Long =
    Math.floorDiv(nowMillis, DAY_MILLIS) * DAY_MILLIS + DAY_MILLIS

internal data class RefreshCountdown(
    val hours: Long,
    val minutes: Long,
)

/**
 * The time left until the next free data refresh, rounded up to whole minutes
 * so it never reads zero while the refresh is still ahead.
 */
internal fun freeRefreshCountdown(nowMillis: Long): RefreshCountdown {
    val remainingMillis = nextFreeRefreshMillis(nowMillis) - nowMillis
    val totalMinutes = (remainingMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS
    return RefreshCountdown(
        hours = totalMinutes / 60,
        minutes = totalMinutes % 60,
    )
}

/**
 * How long until [freeRefreshCountdown] shows a different value, so a ticking
 * countdown wakes once per displayed minute.
 */
internal fun millisUntilCountdownChanges(nowMillis: Long): Long {
    val remainingMillis = nextFreeRefreshMillis(nowMillis) - nowMillis
    return (remainingMillis - 1) % MINUTE_MILLIS + 1
}

/**
 * The countdown as a compact duration (e.g. "5h 12m", or "12m" in the last
 * hour), with the provider_connected_duration_* strings.
 */
internal fun formatRefreshCountdown(
    countdown: RefreshCountdown,
    hoursAndMinutes: (hours: Long, minutes: Long) -> String,
    minutesOnly: (minutes: Long) -> String,
): String = if (0 < countdown.hours) {
    hoursAndMinutes(countdown.hours, countdown.minutes)
} else {
    minutesOnly(countdown.minutes)
}

internal data class DataInfo(
    val used: String,
    val pending: String,
    val available: String,
    val daily: String,
)

/**
 * The sheet's amounts from the balance, split the way the usage bar splits
 * it: used is start - available - pending, clamped at 0 (the server samples
 * the values independently, so the raw difference can go negative).
 */
internal fun dataInfo(
    startBalanceByteCount: Long,
    availableByteCount: Long,
    pendingByteCount: Long,
    formatBytes: (Long) -> String = formatBalanceBytes,
): DataInfo {
    val available = availableByteCount.coerceAtLeast(0)
    val pending = pendingByteCount.coerceAtLeast(0)
    val start = startBalanceByteCount.coerceAtLeast(0)
    val used = (start - available - pending).coerceAtLeast(0)
    return DataInfo(
        used = formatBytes(used),
        pending = formatBytes(pending),
        available = formatBytes(available),
        daily = formatBytes(start),
    )
}

/**
 * Whether the sheet shows when the free data refreshes. Pro networks get the
 * Pro grant instead of the free daily one, so the line would not apply.
 */
internal fun dataInfoShowsFreeRefresh(currentPlan: Plan): Boolean =
    currentPlan != Plan.Supporter
