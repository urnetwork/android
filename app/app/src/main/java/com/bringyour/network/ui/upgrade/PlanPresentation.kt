package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.OnboardingOffer
import com.bringyour.sdk.PriceTier
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.WinbackOffer
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * What every plan surface prints (mmm/onboarding/PLAN.md "PRICE PRESENTATION"),
 * computed once from the server's tier, the store's localized prices when it
 * has answered, the network's welcome offer, and a returning subscriber's
 * win-back offer (mmm/reengagement/PLAN.md "THE STORES"). The per-month equivalent and
 * the saving come from the SDK (`ComputePriceEquivalent`: ceiling, floor,
 * suppressed under one major unit) so every platform prints the same numbers.
 *
 * Pure: no Compose, no store types, so the unit tests and every flavor share it.
 */
data class PlanPresentation(
    /** "$39.99" -- the yearly plan's headline amount (the store's figure once loaded) */
    val yearlyPrice: String,
    /** "$4.99" */
    val monthlyPrice: String,
    /** "$3.34" -- the per-month equivalent of the yearly plan; null when suppressed (the regional tier) */
    val monthlyEquivalent: String?,
    /** the saving of yearly against twelve monthly payments, rounded down; 0 when none */
    val savingPercent: Int,
    /** the tier the prices belong to (PriceTier name); "" when unknown */
    val tier: String,
    /** the ISO currency the amounts are in */
    val currency: String,
    /** the yearly amount as a number (the store's, else the tier's), for the events */
    val yearlyAmount: Double,
    /** the monthly amount as a number */
    val monthlyAmount: Double,
    /** the welcome offer while it can be redeemed, else null */
    val offer: OfferPresentation?,
    /** the win-back offer on the monthly plan while this app can sell it, else null */
    val winback: WinbackPresentation? = null,
) {
    val isRegional: Boolean get() = tier == Sdk.PriceTierRegional

    /** What the plan's first charge is, for the purchase events: an offer's discounted first period, else the plan's price. */
    fun firstChargeAmount(plan: PlanType): Double = when (plan) {
        PlanType.YEARLY -> offer?.firstYearAmount ?: yearlyAmount
        PlanType.MONTHLY -> winback?.firstMonthAmount ?: monthlyAmount
    }
}

/** The welcome offer as printed: "$29.99 for your first year", "then $39.99/year", the deadline. */
data class OfferPresentation(
    val firstYearPrice: String,
    val regularYearPrice: String,
    val firstYearAmount: Double,
    val percentOff: Int,
    val monthsFree: Int,
    val expiresAtMillis: Long,
    val playOfferTag: String,
    val appleOfferCode: String,
    val stripeCouponId: String,
)

/**
 * The win-back offer as printed on the monthly card: "$2.49 for your first
 * month", "then $4.99/month", the percent off, the deadline. On Play the
 * first month is Play's own price for the tagged offer; on Stripe it is the
 * monthly price at the offer's discount (the server applies it at checkout).
 */
data class WinbackPresentation(
    val firstMonthPrice: String,
    val regularMonthPrice: String,
    val firstMonthAmount: Double,
    val percentOff: Int,
    val expiresAtMillis: Long,
    /** the Play offer the purchase buys (play only), else "" */
    val playOfferTag: String,
)

/** The tier as plain values (the SDK's PriceTier, or a test's). */
data class TierInput(val name: String, val yearlyUsd: Double, val monthlyUsd: Double, val currency: String)

/** The welcome offer as plain values (the SDK's OnboardingOffer while active, or a test's). */
data class OfferInput(
    val firstYearUsd: Double,
    val percentOff: Int,
    val monthsFree: Int,
    val expiresAtMillis: Long,
    val playOfferTag: String = "",
    val appleOfferCode: String = "",
    val stripeCouponId: String = "",
)

/** The win-back offer as plain values (the SDK's WinbackOffer while active, or a test's). */
data class WinbackInput(
    val percentOff: Int,
    val expiresAtMillis: Long,
    /** the store it was issued for (WinbackOfferStore*) */
    val store: String,
    /** the plan it discounts: monthly, the only one there is */
    val plan: String = Sdk.PlanMonthly,
    val playOfferTag: String = "",
)

/** The per-month equivalent rule's result (the SDK's PriceEquivalent, or the pure twin in tests). */
data class EquivalentResult(val monthlyEquivalent: Double, val showEquivalent: Boolean, val savingPercent: Int)

object PlanPresentations {

    /** The SDK's rule: ceiling to the minor unit, saving rounded down, suppressed under one major unit. */
    val sdkEquivalent: (yearly: Double, monthly: Double, digits: Int) -> EquivalentResult = { yearly, monthly, digits ->
        val e = Sdk.computePriceEquivalent(yearly, monthly, digits.toLong())
        EquivalentResult(e.monthlyEquivalent, e.showEquivalent, e.savingPercent.toInt())
    }

    /**
     * @param tier the server's `price_tier` (null before the balance loads: USD standard)
     * @param offer the server's `onboarding_offer` (used only while active)
     * @param storeYearly the store's localized yearly amount and currency when the store answered, else null
     * @param storeMonthly the store's localized monthly amount, else null
     * @param winback the server's `winback_offer` (used only while active)
     * @param winbackStore the store this app sells the monthly plan through (WinbackOfferStore*): the only win-back it shows
     * @param storeWinbackFirstMonth the store's own price for the win-back offer's first month (Play), else null
     */
    fun build(
        tier: PriceTier?,
        offer: OnboardingOffer?,
        storeYearly: StorePrice? = null,
        storeMonthly: StorePrice? = null,
        locale: Locale = Locale.getDefault(),
        winback: WinbackOffer? = null,
        winbackStore: String = "",
        storeWinbackFirstMonth: StorePrice? = null,
    ): PlanPresentation = build(
        tier = tier?.let { TierInput(it.name ?: "", it.yearlyUsd, it.monthlyUsd, it.currency ?: "") },
        offer = offer?.takeIf { it.isActive }?.let {
            OfferInput(
                firstYearUsd = it.firstYearUsd,
                percentOff = it.percentOff.toInt(),
                monthsFree = it.monthsFree.toInt(),
                expiresAtMillis = it.expiresAtUnixMillis(),
                playOfferTag = it.playOfferTag ?: "",
                appleOfferCode = it.appleOfferCode ?: "",
                stripeCouponId = it.stripeCouponId ?: "",
            )
        },
        storeYearly = storeYearly,
        storeMonthly = storeMonthly,
        locale = locale,
        equivalent = sdkEquivalent,
        winback = winback?.takeIf { it.isActive }?.let {
            WinbackInput(
                percentOff = it.percentOff.toInt(),
                expiresAtMillis = it.expiresAtUnixMillis(),
                store = it.store ?: "",
                plan = it.plan ?: "",
                playOfferTag = it.playOfferTag ?: "",
            )
        },
        winbackStore = winbackStore,
        storeWinbackFirstMonth = storeWinbackFirstMonth,
    )

    /** The pure builder: plain inputs, an injectable equivalent rule (the SDK's in the app). */
    fun build(
        tier: TierInput?,
        offer: OfferInput?,
        storeYearly: StorePrice? = null,
        storeMonthly: StorePrice? = null,
        locale: Locale = Locale.getDefault(),
        equivalent: (yearly: Double, monthly: Double, digits: Int) -> EquivalentResult = sdkEquivalent,
        winback: WinbackInput? = null,
        winbackStore: String = "",
        storeWinbackFirstMonth: StorePrice? = null,
    ): PlanPresentation {
        val currency = storeYearly?.currency ?: tier?.currency?.takeIf { it.isNotEmpty() } ?: "USD"
        val yearlyAmount = storeYearly?.amount ?: tier?.yearlyUsd?.takeIf { 0.0 < it } ?: FALLBACK_YEARLY_USD
        val monthlyAmount = storeMonthly?.amount ?: tier?.monthlyUsd?.takeIf { 0.0 < it } ?: FALLBACK_MONTHLY_USD
        val digits = minorUnitDigits(currency)
        val eq = equivalent(yearlyAmount, monthlyAmount, digits)
        val tierName = tier?.name ?: ""
        val regional = tierName == Sdk.PriceTierRegional
        val format = currencyFormat(currency, locale)

        val offerPresentation = offer?.let { o ->
            // the offer's amount is USD for the caller's tier; scale it to the
            // store's currency by the tier's yearly ratio so the card matches the
            // headline price the store prints
            val tierYearly = tier?.yearlyUsd ?: 0.0
            val ratio = if (0.0 < tierYearly) yearlyAmount / tierYearly else 1.0
            val firstYear = roundTo(o.firstYearUsd * ratio, digits)
            OfferPresentation(
                firstYearPrice = format.format(firstYear),
                regularYearPrice = format.format(yearlyAmount),
                firstYearAmount = firstYear,
                percentOff = o.percentOff,
                monthsFree = o.monthsFree,
                expiresAtMillis = o.expiresAtMillis,
                playOfferTag = o.playOfferTag,
                appleOfferCode = o.appleOfferCode,
                stripeCouponId = o.stripeCouponId,
            )
        }

        val winbackPresentation = winback
            // only a monthly offer for the store this app sells through: a Play
            // offer means nothing to a Stripe checkout, and the other way round
            ?.takeIf { it.plan == Sdk.PlanMonthly && it.store == winbackStore && 0 < it.percentOff }
            ?.let { w ->
                val firstMonth = if (w.store == Sdk.WinbackOfferStorePlay) {
                    // Play's own price for the tagged offer; while Play does not
                    // list it the purchase buys the base plan, so nothing is shown
                    storeWinbackFirstMonth?.amount
                } else {
                    discountedAmount(monthlyAmount, w.percentOff, digits)
                }
                firstMonth?.let {
                    WinbackPresentation(
                        firstMonthPrice = format.format(it),
                        regularMonthPrice = format.format(monthlyAmount),
                        firstMonthAmount = it,
                        percentOff = w.percentOff,
                        expiresAtMillis = w.expiresAtMillis,
                        playOfferTag = if (w.store == Sdk.WinbackOfferStorePlay) w.playOfferTag else "",
                    )
                }
            }

        return PlanPresentation(
            yearlyPrice = format.format(yearlyAmount),
            monthlyPrice = format.format(monthlyAmount),
            // the regional tier never prints a per-month line; elsewhere the SDK decides
            monthlyEquivalent = if (!regional && eq.showEquivalent) format.format(eq.monthlyEquivalent) else null,
            savingPercent = eq.savingPercent,
            tier = tierName,
            currency = currency,
            yearlyAmount = yearlyAmount,
            monthlyAmount = monthlyAmount,
            offer = offerPresentation,
            winback = winbackPresentation,
        )
    }

    /**
     * An amount less a whole percent, rounded half up to the currency's minor
     * unit as the server rounds the welcome offer's first year: never below
     * what Stripe charges for the discounted period.
     */
    fun discountedAmount(amount: Double, percentOff: Int, digits: Int): Double {
        val scale = Math.pow(10.0, digits.toDouble())
        return Math.round(amount * (100 - percentOff) * scale / 100.0) / scale
    }

    /** The picker before anything loads: the standard USD tier, no offer. */
    fun fallback(locale: Locale = Locale.getDefault()): PlanPresentation =
        build(tier = null as TierInput?, offer = null, locale = locale)

    fun currencyFormat(currency: String, locale: Locale): NumberFormat {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currency) }
        val digits = minorUnitDigits(currency)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format
    }

    fun minorUnitDigits(currency: String): Int =
        runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    private fun roundTo(amount: Double, digits: Int): Double {
        val scale = Math.pow(10.0, digits.toDouble())
        return Math.round(amount * scale) / scale
    }

    // the standard tier's figures until the server or the store answers
    const val FALLBACK_YEARLY_USD = 40.0
    const val FALLBACK_MONTHLY_USD = 5.0
}

/** A store's localized price: the amount in major units and its ISO currency. */
data class StorePrice(val amount: Double, val currency: String)
