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
    /**
     * the offer's own tags as configured in the Play Console (e.g. the welcome
     * offer's `onboarding25`), its base plan's tags left out ([PlanOffers.ownTags])
     */
    val tags: List<String> = emptyList(),
    /** the base plan itself (Play lists it without an offer id), as opposed to an offer on it */
    val basePlan: Boolean = false,
)

/**
 * The plan picker's offer rules, kept free of the billing types so every
 * flavor and the unit tests share them.
 *
 * The picker promises a trial only when the store actually returns a yearly
 * offer with a free phase, and then buys that offer: a base plan can be listed
 * next to a trial offer for the same plan (or without one at all, for an
 * account that already used it, or while a new offer propagates), and the
 * first yearly entry is not necessarily the one with the trial.
 *
 * A tagged offer is a developer-determined one (the welcome offer's
 * `onboarding25`, the win-back offer's `winback-monthly-50`): Play lists it to
 * every account and enforces nothing, so it is bought only when the server
 * names its tag for this network (`preferTag`), never because Play happened to
 * list it first. Without a tag the choice is among the base plan and the
 * untagged offers (the trial offer, which Play itself lists only to the
 * accounts eligible for it).
 */
object PlanOffers {

    /**
     * The yearly offer: the one carrying `preferTag` when the caller's welcome
     * offer is active and Play lists that offer (the developer-determined
     * two-phase offer: trial, then the discounted first year), else the
     * untagged one with a free phase, else the base plan.
     */
    fun yearly(offers: List<PlanOffer>, preferTag: String? = null): PlanOffer? {
        val yearly = offers.filter { 360 <= it.periodDays }
        if (!preferTag.isNullOrEmpty()) {
            yearly.firstOrNull { preferTag in it.tags }?.let { return it }
        }
        val regular = yearly.filter { it.isRegular() }
        return regular.firstOrNull { 0 < it.freeDays } ?: regular.firstOrNull { it.basePlan } ?: regular.firstOrNull()
    }

    /**
     * The monthly offer (billed every 28..31 days): the one carrying
     * `preferTag` when the caller's win-back offer is active and Play lists
     * that offer (the discounted first month), else the base plan.
     */
    fun monthly(offers: List<PlanOffer>, preferTag: String? = null): PlanOffer? {
        val monthly = offers.filter { it.periodDays in 28..31 }
        if (!preferTag.isNullOrEmpty()) {
            monthly.firstOrNull { preferTag in it.tags }?.let { return it }
        }
        val regular = monthly.filter { it.isRegular() }
        return regular.firstOrNull { it.basePlan } ?: regular.firstOrNull()
    }

    /** Days of free trial the yearly offer starts with; 0 when it has no free phase or there is no yearly offer. */
    fun trialDays(offers: List<PlanOffer>): Int = yearly(offers)?.freeDays ?: 0

    /**
     * The offer to buy for a plan; null when the store has no such plan, so the
     * purchase surfaces the store's error rather than selling the other plan
     * behind the one the user picked. `preferTag` is the tag the server named
     * for that plan's offer (the welcome offer's for yearly, the win-back
     * offer's for monthly).
     */
    fun forPlan(offers: List<PlanOffer>, plan: PlanType, preferTag: String? = null): PlanOffer? = when (plan) {
        PlanType.YEARLY -> yearly(offers, preferTag)
        PlanType.MONTHLY -> monthly(offers, preferTag)
    }

    /**
     * An offer's own tags: Play reports the tags of an offer with its base
     * plan's tags included, so a tag on a base plan would otherwise make every
     * offer on it look developer-determined. `baseTags` are the tags of the
     * offer's base plan (none for the base plan itself, or when Play does not
     * list it).
     */
    fun ownTags(tags: List<String>, baseTags: Collection<String>): List<String> = tags.filterNot { it in baseTags }

    /** The base plan, or an offer without tags of its own: one Play may sell to anyone it lists it to. */
    private fun PlanOffer.isRegular(): Boolean = basePlan || tags.isEmpty()
}
