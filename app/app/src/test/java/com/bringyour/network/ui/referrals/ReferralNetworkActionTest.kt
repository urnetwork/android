package com.bringyour.network.ui.referrals

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Support inbox 1698: after sign-up, the only way to add a referral code was
 * a generic "Update" next to "Referral network: None". With no referral
 * network the row is now the "Add referral code" action itself.
 */
class ReferralNetworkActionTest {

    @Test
    fun noReferralNetworkOffersTheCodeEntry() {
        assertEquals(ReferralNetworkAction.AddCode, referralNetworkAction(null))
        assertEquals(ReferralNetworkAction.AddCode, referralNetworkAction(""))
    }

    @Test
    fun aLinkedNetworkShowsItsNameWithUpdate() {
        assertEquals(ReferralNetworkAction.Update("parent_network"), referralNetworkAction("parent_network"))
    }
}
