package com.bringyour.network.ui.components.referral

import androidx.compose.runtime.compositionLocalOf
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

/**
 * The count is 0 until the read lands and stays 0 when it fails, so the raw
 * count alone read as "+0 GiB/Day" for every new user; the figures wait for
 * the read instead. A failed background poll keeps a count already shown
 * ([SectionLoad] stays Loaded).
 */
fun referralBonusLine(
    load: SectionLoad,
    totalReferrals: Long,
    terms: ReferralTerms,
): ReferralBonusLine = when (load) {
    SectionLoad.Loading -> ReferralBonusLine.Loading
    SectionLoad.Failed -> ReferralBonusLine.Unavailable
    SectionLoad.Loaded -> ReferralBonusLine.Earned(totalReferrals, terms.earnedGibPerDay(totalReferrals))
}

/**
 * The referral code read's load, provided with [LocalReferralTerms] at the
 * root of the signed-in UI (the count and the terms come from the same read).
 */
val LocalReferralCountLoad = compositionLocalOf { SectionLoad.Loaded }
