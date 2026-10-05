package com.bringyour.network.ui.components

import com.bringyour.network.ui.components.referral.referralLinkTarget
import com.bringyour.network.ui.components.referral.referralShareText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Support inbox 1698: the share text had been the code alone since 2026-07,
 * so a friend on Android had no link to open the app (or Play, with the
 * install referrer) with the code applied. The invitation now carries the
 * code's ur.io/c link after the message, which still names the code.
 */
class ReferralShareTextTest {

    private val message = "Join me on URnetwork! Get the app and enter referral code AB12CD when you sign up."

    @Test
    fun theLinkFollowsTheMessageOnItsOwnLine() {
        assertEquals(
            "$message\nhttps://ur.io/c?bonus=AB12CD",
            referralShareText(message, "https://ur.io/c?bonus=AB12CD"),
        )
    }

    @Test
    fun withoutALinkTheMessageStandsAlone() {
        assertEquals(message, referralShareText(message, null))
        assertEquals(message, referralShareText(message, ""))
    }

    @Test
    fun theLinkTargetIsTheBonusCode() {
        // sdk ConnectLinkUrl(target) = https://<link host>/c?<target>
        assertEquals("bonus=AB12CD", referralLinkTarget("AB12CD"))
        assertEquals("bonus=9f1c-22ab", referralLinkTarget("9f1c-22ab"))
        // a code can never add a parameter to the link
        assertEquals("bonus=A%26auth_code%3Dx", referralLinkTarget("A&auth_code=x"))
    }
}
