package com.bringyour.network.ui.upgrade

import com.bringyour.sdk.OnboardingOffer
import com.bringyour.sdk.PriceTier
import com.bringyour.sdk.Sdk
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * What every plan surface prints (mmm/onboarding/PLAN.md "PRICE PRESENTATION"),
 * computed once from the server's tier, the store's localized prices when it
 * has answered, and the network's welcome offer. The per-month equivalent and
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
) {
    val isRegional: Boolean get() = tier == Sdk.PriceTierRegional
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

object PlanPresentations {

    /**
     * @param tier the server's `price_tier` (null before the balance loads: USD standard)
     * @param offer the server's `onboarding_offer` (used only while active)
     * @param storeYearly the store's localized yearly amount and currency when the store answered, else null
     * @param storeMonthly the store's localized monthly amount, else null
     */
    fun build(
        tier: PriceTier?,
        offer: OnboardingOffer?,
        storeYearly: StorePrice? = null,
        storeMonthly: StorePrice? = null,
        locale: Locale = Locale.getDefault(),
    ): PlanPresentation {
        val currency = storeYearly?.currency ?: tier?.currency?.takeIf { it.isNotEmpty() } ?: "USD"
        val yearlyAmount = storeYearly?.amount ?: tier?.yearlyUsd?.takeIf { 0.0 < it } ?: FALLBACK_YEARLY_USD
        val monthlyAmount = storeMonthly?.amount ?: tier?.monthlyUsd?.takeIf { 0.0 < it } ?: FALLBACK_MONTHLY_USD
        val digits = minorUnitDigits(currency)
        val equivalent = Sdk.computePriceEquivalent(yearlyAmount, monthlyAmount, digits.toLong())
        val tierName = tier?.name ?: ""
        val regional = tierName == Sdk.PriceTierRegional
        val format = currencyFormat(currency, locale)

        val offerPresentation = offer?.takeIf { it.isActive }?.let { o ->
            // the offer's amounts are USD for the caller's tier; scale them to the
            // store's currency by the tier's yearly ratio so the card matches the
            // headline price the store prints
            val ratio = if (0.0 < (tier?.yearlyUsd ?: 0.0)) yearlyAmount / tier!!.yearlyUsd else 1.0
            val firstYear = roundTo(o.firstYearUsd * ratio, digits)
            OfferPresentation(
                firstYearPrice = format.format(firstYear),
                regularYearPrice = format.format(yearlyAmount),
                firstYearAmount = firstYear,
                percentOff = o.percentOff.toInt(),
                monthsFree = o.monthsFree.toInt(),
                expiresAtMillis = o.expiresAtUnixMillis(),
                playOfferTag = o.playOfferTag ?: "",
                appleOfferCode = o.appleOfferCode ?: "",
                stripeCouponId = o.stripeCouponId ?: "",
            )
        }

        return PlanPresentation(
            yearlyPrice = format.format(yearlyAmount),
            monthlyPrice = format.format(monthlyAmount),
            // the regional tier never prints a per-month line; elsewhere the SDK decides
            monthlyEquivalent = if (!regional && equivalent.showEquivalent) format.format(equivalent.monthlyEquivalent) else null,
            savingPercent = equivalent.savingPercent.toInt(),
            tier = tierName,
            currency = currency,
            yearlyAmount = yearlyAmount,
            monthlyAmount = monthlyAmount,
            offer = offerPresentation,
        )
    }

    /** The picker before anything loads: the standard USD tier, no offer. */
    fun fallback(locale: Locale = Locale.getDefault()): PlanPresentation = build(null, null, locale = locale)

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
