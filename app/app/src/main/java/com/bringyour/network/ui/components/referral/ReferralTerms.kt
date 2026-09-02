package com.bringyour.network.ui.components.referral

import androidx.compose.runtime.compositionLocalOf
import com.bringyour.sdk.GetNetworkReferralCodeResult

/**
 * The referral program's numbers. The server's pro.yml is the single source of
 * truth and `GET /account/referral-code` carries them (max_referrals,
 * bonus_per_referral_bytes, referred_bonus_bytes, bonus_period_seconds), so
 * every app and the site print the same cap and bonus. The compile-time
 * defaults only cover the moment before the first fetch and a server that
 * reports no terms (no pro.yml).
 */
data class ReferralTerms(
    val maxReferrals: Int,
    val bonusGibPerDay: Int,
    val referredBonusGibPerDay: Int,
) {

    /** How many of the network's referrals it is paid for. */
    fun paidReferrals(totalReferrals: Long): Int {
        if (totalReferrals <= 0L) {
            return 0
        }
        if (0 < maxReferrals && maxReferrals.toLong() < totalReferrals) {
            return maxReferrals
        }
        return totalReferrals.toInt()
    }

    /** The GiB/day the network earns from its referrals. */
    fun earnedGibPerDay(totalReferrals: Long): Int = paidReferrals(totalReferrals) * bonusGibPerDay

    companion object {
        val Default = ReferralTerms(
            maxReferrals = REFERRAL_MAX_REFERRALS,
            bonusGibPerDay = REFERRAL_BONUS_GIB_PER_DAY,
            referredBonusGibPerDay = REFERRAL_BONUS_GIB_PER_DAY,
        )

        /** The server's terms, keeping a default for any value the server left at zero. */
        fun from(result: GetNetworkReferralCodeResult): ReferralTerms = ReferralTerms(
            maxReferrals = positiveOr(result.maxReferrals, Default.maxReferrals),
            bonusGibPerDay = positiveOr(result.bonusGibPerDay(), Default.bonusGibPerDay),
            referredBonusGibPerDay = positiveOr(result.referredBonusGibPerDay(), Default.referredBonusGibPerDay),
        )

        private fun positiveOr(value: Long, fallback: Int): Int =
            if (0L < value) value.toInt() else fallback
    }
}

/** The current referral terms, provided at the root of the signed-in UI. */
val LocalReferralTerms = compositionLocalOf { ReferralTerms.Default }
