package com.bringyour.network.ui.stats

import com.bringyour.network.R
import com.bringyour.network.utils.formatByteRate
import com.bringyour.sdk.ProviderStatusViewController
import com.bringyour.sdk.Sdk
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/**
 * One number the provider search admits or ranks this device by, as the
 * server sent it. A mirror of the sdk's `ProviderRankingNumber`, so the
 * presentation is tested without the sdk. The unit follows the name:
 * reliability_* a 0-1 share of steady uptime, url_checks a share of loaded
 * test sites (count of total), speed_test bytes per second, latency
 * milliseconds above the expected delay, weight_* a selection weight,
 * tier_* a tier.
 */
data class ProviderRankingNumberUi(
    val name: String,
    val hasValue: Boolean = false,
    val value: Double = 0.0,
    val hasMinimum: Boolean = false,
    val minimum: Double = 0.0,
    val hasMaximum: Boolean = false,
    val maximum: Double = 0.0,
    /**
     * false when the number holds this device back
     */
    val passes: Boolean = true,
    val count: Long = 0,
    val total: Long = 0,
    /**
     * the server's English text with the values. The localized help keys are
     * the UI; this is a debug and accessibility fallback only
     */
    val explanation: String = "",
)

/**
 * Where clients find this device. A mirror of the sdk's `ProviderStatusCountry`.
 */
data class ProviderStatusCountryUi(
    val countryCode: String,
    val country: String,
) {
    /**
     * the country name, falling back to the upper-cased code
     */
    val displayName: String
        get() = country.ifEmpty { countryCode.uppercase(Locale.ROOT) }
}

/**
 * One snapshot of the sdk's `ProviderStatusViewController` for this device,
 * read on the main thread after each poll.
 */
data class ProviderStatusUi(
    /**
     * a poll has succeeded
     */
    val loaded: Boolean = false,
    /**
     * the last failed poll's error, "" once a poll succeeds
     */
    val lastFetchError: String = "",
    /**
     * this device is one of the network's provider clients in the last poll
     */
    val hasStatus: Boolean = false,
    /**
     * the server's reason code (`Sdk.ProviderStatusReason*`) and its English
     * text, "" without a status
     */
    val reason: String = "",
    val reasonText: String = "",
    /**
     * false when the server could not read the histogram
     */
    val hasAppearances: Boolean = false,
    /**
     * oldest first; the last is the current, partial minute
     */
    val appearancesPerMinute: List<Long> = listOf(),
    /**
     * in the server's display order
     */
    val rankingNumbers: List<ProviderRankingNumberUi> = listOf(),
    val country: ProviderStatusCountryUi? = null,
) {
    companion object {
        val Empty = ProviderStatusUi()

        fun fromSdk(vc: ProviderStatusViewController): ProviderStatusUi {
            val loaded = vc.isLoaded
            val lastFetchError = vc.lastFetchError ?: ""
            // read one status object, so the reason, the numbers and the
            // histogram all come from the same poll
            val status = vc.providerStatus
                ?: return ProviderStatusUi(loaded = loaded, lastFetchError = lastFetchError)

            val rankingNumbers = mutableListOf<ProviderRankingNumberUi>()
            status.ranking?.let { list ->
                val n = list.len()
                for (i in 0 until n) {
                    val number = list.get(i) ?: continue
                    rankingNumbers.add(
                        ProviderRankingNumberUi(
                            name = number.name ?: "",
                            hasValue = number.hasValue,
                            value = number.value,
                            hasMinimum = number.hasMinimum,
                            minimum = number.minimum,
                            hasMaximum = number.hasMaximum,
                            maximum = number.maximum,
                            passes = number.passes,
                            count = number.count,
                            total = number.total,
                            explanation = number.explanation ?: "",
                        )
                    )
                }
            }

            val appearances = status.appearances
            val appearancesPerMinute = mutableListOf<Long>()
            appearances?.appearancesPerMinute?.let { list ->
                val n = list.len()
                for (i in 0 until n) {
                    appearancesPerMinute.add(list.get(i))
                }
            }

            return ProviderStatusUi(
                loaded = loaded,
                lastFetchError = lastFetchError,
                hasStatus = true,
                reason = status.reason ?: "",
                reasonText = status.reasonText ?: "",
                hasAppearances = appearances != null,
                appearancesPerMinute = appearancesPerMinute,
                rankingNumbers = rankingNumbers,
                country = status.country?.let {
                    ProviderStatusCountryUi(
                        countryCode = it.countryCode ?: "",
                        country = it.country ?: "",
                    )
                },
            )
        }
    }
}

/**
 * What the demand chart area shows.
 */
enum class ProviderDemandState {
    // before the first successful poll
    LOADING,
    // no poll has succeeded and the last one failed (for example before the
    // server has the route), this device is not among the network's provider
    // clients, or the server could not read its histogram
    UNAVAILABLE,
    // every minute is zero: the baseline and the axis with the empty text
    EMPTY,
    // the 60 bars with the total
    BARS,
}

data class ProviderStatusDisplay(
    val demand: ProviderDemandState,
    /**
     * the expandable "Why?" with the ranking numbers
     */
    val why: Boolean,
)

/**
 * The states of the provider status under the provider plots. The chart area
 * always shows while providing is enabled, so the layout does not jump: it
 * says loading or unavailable until there is a histogram to draw.
 */
fun providerStatusDisplay(status: ProviderStatusUi): ProviderStatusDisplay {
    return when {
        !status.loaded && status.lastFetchError.isEmpty() ->
            ProviderStatusDisplay(ProviderDemandState.LOADING, why = false)
        !status.loaded || !status.hasStatus ->
            ProviderStatusDisplay(ProviderDemandState.UNAVAILABLE, why = false)
        !status.hasAppearances ->
            ProviderStatusDisplay(ProviderDemandState.UNAVAILABLE, why = true)
        status.appearancesPerMinute.all { it <= 0L } ->
            ProviderStatusDisplay(ProviderDemandState.EMPTY, why = true)
        else ->
            ProviderStatusDisplay(ProviderDemandState.BARS, why = true)
    }
}

/**
 * The line under the provide mode row: a string resource, or the server's
 * English text for a reason code this app does not know yet.
 */
sealed interface ProviderStatusLine {
    data class Resource(val id: Int) : ProviderStatusLine
    data class Text(val text: String) : ProviderStatusLine
}

/**
 * Merges this device's idle reason with the server's reason. Local state
 * wins because it is immediate: the server's ranking is cached for about 5
 * minutes, so right after a mode change it can still say network_only. The
 * server's "none" adds nothing to the line.
 */
fun providerStatusLine(
    idleReason: ProviderIdleReason,
    serverReason: String,
    serverReasonText: String,
): ProviderStatusLine? {
    if (idleReason.local) {
        return idleReason.messageResourceId?.let { ProviderStatusLine.Resource(it) }
    }
    if (serverReason.isNotEmpty() && serverReason != Sdk.ProviderStatusReasonNone) {
        val reasonResourceId = providerStatusReasonResourceId(serverReason)
        if (reasonResourceId != null) {
            return ProviderStatusLine.Resource(reasonResourceId)
        }
        // a newer server's reason: its English text keeps the line forward-compatible
        if (serverReasonText.isNotEmpty()) {
            return ProviderStatusLine.Text(serverReasonText)
        }
    }
    if (idleReason == ProviderIdleReason.NO_TRAFFIC_YET) {
        return ProviderStatusLine.Resource(R.string.provider_idle_no_traffic_yet)
    }
    return null
}

/**
 * provider_status_reason_<code> for each of the server's reason codes, null
 * for a code this app does not know
 */
fun providerStatusReasonResourceId(reason: String): Int? {
    return when (reason) {
        Sdk.ProviderStatusReasonNotProviding -> R.string.provider_status_reason_not_providing
        Sdk.ProviderStatusReasonNotConnected -> R.string.provider_status_reason_not_connected
        Sdk.ProviderStatusReasonLocationInvalid -> R.string.provider_status_reason_location_invalid
        Sdk.ProviderStatusReasonNetworkOnly -> R.string.provider_status_reason_network_only
        Sdk.ProviderStatusReasonReliabilityWarmingUp -> R.string.provider_status_reason_reliability_warming_up
        Sdk.ProviderStatusReasonReliabilityLow -> R.string.provider_status_reason_reliability_low
        Sdk.ProviderStatusReasonNotEligible -> R.string.provider_status_reason_not_eligible
        Sdk.ProviderStatusReasonEgressUnprobed -> R.string.provider_status_reason_egress_unprobed
        Sdk.ProviderStatusReasonEgressFailing -> R.string.provider_status_reason_egress_failing
        Sdk.ProviderStatusReasonSpeedTestMissing -> R.string.provider_status_reason_speed_test_missing
        Sdk.ProviderStatusReasonSlow -> R.string.provider_status_reason_slow
        Sdk.ProviderStatusReasonNone -> R.string.provider_status_reason_none
        else -> null
    }
}

/**
 * How often the provider search offered this device to clients in each of
 * the last 60 minutes, oldest first, the current, partial minute last.
 */
data class ProviderDemandHistogram(
    /**
     * exactly `BAR_COUNT` counts
     */
    val counts: List<Long>,
    /**
     * each bar's height as a fraction of the chart: count / max(maxCount, 1)
     */
    val fractions: List<Float>,
    val total: Long,
    val maxCount: Long,
) {
    /**
     * not offered to clients in the last hour
     */
    val empty: Boolean
        get() = maxCount == 0L

    companion object {
        const val BAR_COUNT = 60

        fun fromCounts(appearancesPerMinute: List<Long>): ProviderDemandHistogram {
            // always 60 bars: the newest 60 minutes, padded with empty minutes
            // on the old side
            val newest = appearancesPerMinute.takeLast(BAR_COUNT).map { max(it, 0L) }
            val counts = List(BAR_COUNT - newest.size) { 0L } + newest
            val maxCount = counts.max()
            val scale = max(maxCount, 1L).toFloat()
            return ProviderDemandHistogram(
                counts = counts,
                fractions = counts.map { it / scale },
                total = counts.sum(),
                maxCount = maxCount,
            )
        }
    }
}

/**
 * How a value in the "Why?" rows reads, before it is localized.
 */
sealed interface ProviderStatusValue {
    // a 0-1 ratio as a whole percent ("82%")
    data class Percent(val ratio: Double) : ProviderStatusValue
    // bytes per second, in the app's byte-rate format
    data class Rate(val bytesPerSecond: Double) : ProviderStatusValue
    // "35 ms"; the unit is not localized
    data class Millis(val millis: Double) : ProviderStatusValue
    data class CountOfTotal(val count: Long, val total: Long) : ProviderStatusValue
    // a selection weight, with 2 decimals
    data class Weight(val weight: Double) : ProviderStatusValue
    // 0 is best
    data class Tier(val tier: Long) : ProviderStatusValue
    data class Text(val text: String) : ProviderStatusValue
    data class WithMinimum(val value: ProviderStatusValue, val minimum: ProviderStatusValue) : ProviderStatusValue
    data class WithMaximum(val value: ProviderStatusValue, val maximum: ProviderStatusValue) : ProviderStatusValue
    data object NotYet : ProviderStatusValue
    data object NoHistory : ProviderStatusValue
    data object NotInPool : ProviderStatusValue
}

/**
 * One row of the "Why?": a label, the value (tinted when it does not pass)
 * and a muted help line under it.
 */
data class ProviderStatusRow(
    val labelResourceId: Int,
    val value: ProviderStatusValue,
    val helpResourceId: Int,
    val passes: Boolean,
)

private const val RELIABILITY_LOOKBACK_PREFIX = "reliability_lookback_"

/**
 * The row for one ranking number, null for a name this app does not know.
 * Only what the server sent is shown; nothing is inferred.
 */
fun providerStatusRow(number: ProviderRankingNumberUi): ProviderStatusRow? {
    fun withMinimum(value: ProviderStatusValue, minimum: ProviderStatusValue): ProviderStatusValue {
        return if (number.hasMinimum) ProviderStatusValue.WithMinimum(value, minimum) else value
    }

    fun withMaximum(value: ProviderStatusValue, maximum: ProviderStatusValue): ProviderStatusValue {
        return if (number.hasMaximum) ProviderStatusValue.WithMaximum(value, maximum) else value
    }

    // a reliability lookback that must reach its minimum
    fun reliabilityWithMinimum(): ProviderStatusValue {
        return if (number.hasValue) {
            withMinimum(ProviderStatusValue.Percent(number.value), ProviderStatusValue.Percent(number.minimum))
        } else {
            ProviderStatusValue.NoHistory
        }
    }

    val name = number.name
    val (labelResourceId, value, helpResourceId) = when {
        name == Sdk.ProviderStatusNumberReliability5m -> Triple(
            R.string.provider_status_number_reliability_5m,
            if (number.hasValue) ProviderStatusValue.Percent(number.value) else ProviderStatusValue.NoHistory,
            R.string.provider_status_help_reliability_5m,
        )
        name == Sdk.ProviderStatusNumberReliability1h -> Triple(
            R.string.provider_status_number_reliability_1h,
            reliabilityWithMinimum(),
            R.string.provider_status_help_reliability_1h,
        )
        name == Sdk.ProviderStatusNumberReliability12h -> Triple(
            R.string.provider_status_number_reliability_12h,
            reliabilityWithMinimum(),
            R.string.provider_status_help_reliability_12h,
        )
        name.startsWith(RELIABILITY_LOOKBACK_PREFIX) &&
            name.removePrefix(RELIABILITY_LOOKBACK_PREFIX).toIntOrNull() != null -> Triple(
            R.string.reliability,
            reliabilityWithMinimum(),
            R.string.provider_status_help_reliability_other,
        )
        name == Sdk.ProviderStatusNumberUrlChecks -> Triple(
            R.string.provider_status_number_url_checks,
            if (number.hasValue) {
                withMinimum(
                    ProviderStatusValue.CountOfTotal(number.count, number.total),
                    ProviderStatusValue.Percent(number.minimum),
                )
            } else {
                ProviderStatusValue.NotYet
            },
            R.string.provider_status_help_url_checks,
        )
        name == Sdk.ProviderStatusNumberSpeedTest -> Triple(
            R.string.provider_status_number_speed_test,
            if (number.hasValue) {
                withMinimum(ProviderStatusValue.Rate(number.value), ProviderStatusValue.Rate(number.minimum))
            } else {
                ProviderStatusValue.NotYet
            },
            R.string.provider_status_help_speed_test,
        )
        name == Sdk.ProviderStatusNumberLatency -> Triple(
            R.string.provider_status_number_latency,
            if (number.hasValue) {
                withMaximum(ProviderStatusValue.Millis(number.value), ProviderStatusValue.Millis(number.maximum))
            } else {
                ProviderStatusValue.NotYet
            },
            R.string.provider_status_help_latency,
        )
        name == Sdk.ProviderStatusNumberWeightQuality || name == Sdk.ProviderStatusNumberWeightSpeed -> Triple(
            if (name == Sdk.ProviderStatusNumberWeightQuality) {
                R.string.provider_status_number_weight_quality
            } else {
                R.string.provider_status_number_weight_speed
            },
            if (number.hasValue && number.passes) {
                ProviderStatusValue.Weight(number.value)
            } else {
                ProviderStatusValue.NotInPool
            },
            R.string.provider_status_help_weight,
        )
        name == Sdk.ProviderStatusNumberTierQuality || name == Sdk.ProviderStatusNumberTierSpeed -> Triple(
            if (name == Sdk.ProviderStatusNumberTierQuality) {
                R.string.provider_status_number_tier_quality
            } else {
                R.string.provider_status_number_tier_speed
            },
            if (number.hasValue) {
                ProviderStatusValue.Tier(Math.round(number.value))
            } else {
                ProviderStatusValue.NotYet
            },
            R.string.provider_status_help_tier,
        )
        else -> return null
    }
    return ProviderStatusRow(
        labelResourceId = labelResourceId,
        value = value,
        helpResourceId = helpResourceId,
        passes = number.passes,
    )
}

/**
 * The "Why?" rows: every known ranking number in the server's order, then
 * the country when the server knows it
 */
fun providerStatusRows(status: ProviderStatusUi): List<ProviderStatusRow> {
    val rows = status.rankingNumbers.mapNotNull { providerStatusRow(it) }.toMutableList()
    status.country?.displayName?.takeIf { it.isNotEmpty() }?.let { countryName ->
        rows.add(
            ProviderStatusRow(
                labelResourceId = R.string.country,
                value = ProviderStatusValue.Text(countryName),
                helpResourceId = R.string.provider_status_help_country,
                passes = true,
            )
        )
    }
    return rows
}

/**
 * The localized pieces a value is built from: string resources in the app,
 * plain templates in tests.
 */
interface ProviderStatusValueStrings {
    fun withMinimum(value: String, minimum: String): String
    fun withMaximum(value: String, maximum: String): String
    fun countOfTotal(count: Long, total: Long): String
    val notYet: String
    val noHistory: String
    val notInPool: String
}

fun ProviderStatusValue.format(strings: ProviderStatusValueStrings, locale: Locale): String {
    return when (this) {
        is ProviderStatusValue.Percent -> NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
        }.format(ratio)
        is ProviderStatusValue.Rate -> formatByteRate(Math.round(bytesPerSecond))
        is ProviderStatusValue.Millis -> "${Math.round(millis)} ms"
        is ProviderStatusValue.CountOfTotal -> strings.countOfTotal(count, total)
        is ProviderStatusValue.Weight -> String.format(locale, "%.2f", weight)
        is ProviderStatusValue.Tier -> NumberFormat.getIntegerInstance(locale).format(tier)
        is ProviderStatusValue.Text -> text
        is ProviderStatusValue.WithMinimum -> strings.withMinimum(
            value.format(strings, locale),
            minimum.format(strings, locale),
        )
        is ProviderStatusValue.WithMaximum -> strings.withMaximum(
            value.format(strings, locale),
            maximum.format(strings, locale),
        )
        ProviderStatusValue.NotYet -> strings.notYet
        ProviderStatusValue.NoHistory -> strings.noHistory
        ProviderStatusValue.NotInPool -> strings.notInPool
    }
}
