package com.bringyour.network.ui.components.referral

import com.bringyour.network.ui.shared.models.SectionLoad

/**
 * The usage bar's referral row: "Total referrals: N" and "+N GiB/Day".
 * Both figures come from the referral code read
 * (`GET /account/referral-code`), whose load is [SectionLoad].
 */
sealed interface ReferralBonusLine {
    /** The read has not answered yet. */
    data object Loading : ReferralBonusLine

    /** The read failed with no count to show: no figures. */
    data object Unavailable : ReferralBonusLine

    /** The count the server returned and the GiB/day it earns. */
    data class Earned(val totalReferrals: Long, val gibPerDay: Int) : ReferralBonusLine
}

fun referralBonusLine(
    load: SectionLoad,
    totalReferrals: Long,
    terms: ReferralTerms,
): ReferralBonusLine =
    ReferralBonusLine.Earned(totalReferrals, terms.earnedGibPerDay(totalReferrals))
