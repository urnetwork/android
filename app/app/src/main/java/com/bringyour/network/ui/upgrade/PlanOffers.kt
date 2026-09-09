package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType

/**
 * One store offer reduced to what the plan picker decides on: the base plan's
 * billing period and the free phase in front of it, if any. `index` is the
 * offer's position in the store's list, so a choice made here can be mapped
 * back to the offer (and its purchase token) it came from.
 */
data class PlanOffer(
    val index: Int,
    val periodDays: Int,
    val freeDays: Int,
    /** the offer's tags as configured in the Play Console (e.g. the welcome offer's `onboarding25`) */
    val tags: List<String> = emptyList(),
)

/**
 * The plan picker's offer rules, kept free of the billing types so every
 * flavor and the unit tests share them.
 *
 * The picker promises a trial only when the store actually returns a yearly
 * offer with a free phase, and then buys THAT offer: a base plan can be listed
 * next to a trial offer for the same plan (or without one at all, for an
 * account that already used it, or while a new offer propagates), and the
 * first yearly entry is not necessarily the one with the trial.
 */
object PlanOffers {

    /**
     * The yearly offer: the one carrying `preferTag` when the caller's welcome
     * offer is active and Play lists that offer (the developer-determined
     * two-phase offer: trial, then the discounted first year), else the one
     * with a free phase, else the first yearly one.
     */
    fun yearly(offers: List<PlanOffer>, preferTag: String? = null): PlanOffer? {
        val yearly = offers.filter { 360 <= it.periodDays }
        if (!preferTag.isNullOrEmpty()) {
            yearly.firstOrNull { preferTag in it.tags }?.let { return it }
        }
        return yearly.firstOrNull { 0 < it.freeDays } ?: yearly.firstOrNull()
    }

    /** The monthly offer: the first one billed every 28..31 days. */
    fun monthly(offers: List<PlanOffer>): PlanOffer? = offers.firstOrNull { it.periodDays in 28..31 }

    /** Days of free trial the yearly offer starts with; 0 when it has no free phase or there is no yearly offer. */
    fun trialDays(offers: List<PlanOffer>): Int = yearly(offers)?.freeDays ?: 0

    /**
     * The offer to buy for a plan; null when the store has no such plan, so the
     * purchase surfaces the store's error rather than selling the other plan
     * behind the one the user picked.
     */
    fun forPlan(offers: List<PlanOffer>, plan: PlanType, preferTag: String? = null): PlanOffer? = when (plan) {
        PlanType.YEARLY -> yearly(offers, preferTag)
        PlanType.MONTHLY -> monthly(offers)
    }
}
