package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Support inbox 1698: the instant (seedphrase) account screen never received
 * the code from a referral link or the Play install referrer; only the
 * create-network routes did. The instant screen now starts with it filled in
 * (CreateNetworkInstantViewModel.seedReferralCode applies this decision).
 */
class InstantReferralSeedTest {

    @Test
    fun anIncomingCodeFillsAnEmptyField() {
        assertEquals("AB12CD", referralCodeSeed("AB12CD", current = ""))
        assertEquals("AB12CD", referralCodeSeed(" AB12CD ", current = ""))
    }

    @Test
    fun aCodeTheUserEnteredIsKept() {
        assertNull(referralCodeSeed("AB12CD", current = "ZZ99ZZ"))
    }

    @Test
    fun noCodeSeedsNothing() {
        assertNull(referralCodeSeed(null, current = ""))
        assertNull(referralCodeSeed("  ", current = ""))
    }
}
