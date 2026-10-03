package com.bringyour.network.ui.components

import com.bringyour.network.ui.components.referral.ReferralBonusLine
import com.bringyour.network.ui.components.referral.ReferralTerms
import com.bringyour.network.ui.components.referral.referralBonusLine
import com.bringyour.network.ui.shared.models.SectionLoad
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reported defect: the usage bar's referral row printed the raw referral
 * count, which is 0 until the referral read lands and stays 0 when it fails,
 * so a new user saw "Total referrals: 0" and "+0 GiB/Day".
 */
class ReferralBonusLineTest {

    private val terms = ReferralTerms(maxReferrals = 20, bonusGibPerDay = 3, referredBonusGibPerDay = 3)

    @Test
    fun theBonusWaitsForTheReferralRead() {
        assertEquals(
            ReferralBonusLine.Loading,
            referralBonusLine(load = SectionLoad.Loading, totalReferrals = 0, terms = terms),
        )
    }

    @Test
    fun aFailedReferralReadIsNotAZeroBonus() {
        assertEquals(
            ReferralBonusLine.Unavailable,
            referralBonusLine(load = SectionLoad.Failed, totalReferrals = 0, terms = terms),
        )
    }

    @Test
    fun aLandedReadShowsTheEarnedBonus() {
        assertEquals(
            ReferralBonusLine.Earned(totalReferrals = 2, gibPerDay = 6),
            referralBonusLine(load = SectionLoad.Loaded, totalReferrals = 2, terms = terms),
        )
        // a network with no referrals really earns nothing once the read says so
        assertEquals(
            ReferralBonusLine.Earned(totalReferrals = 0, gibPerDay = 0),
            referralBonusLine(load = SectionLoad.Loaded, totalReferrals = 0, terms = terms),
        )
    }

    @Test
    fun theBonusIsCappedByTheTerms() {
        assertEquals(
            ReferralBonusLine.Earned(totalReferrals = 25, gibPerDay = 60),
            referralBonusLine(load = SectionLoad.Loaded, totalReferrals = 25, terms = terms),
        )
    }
}
