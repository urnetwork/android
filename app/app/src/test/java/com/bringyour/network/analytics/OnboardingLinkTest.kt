package com.bringyour.network.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The campaign landing links, parsed with a minimal Uri stand-in (android.net.Uri is a stub on the JVM). */
class OnboardingLinkTest {

    @Test
    fun theRoutePrefixRoundTrips() {
        assertEquals("onboarding_widgets", OnboardingLink("widgets").route)
        assertEquals("widgets", OnboardingLink.stepForRoute("onboarding_widgets"))
        assertEquals("feedback", OnboardingLink.stepForRoute("onboarding_feedback"))
        assertNull(OnboardingLink.stepForRoute("connect"))
        assertNull(OnboardingLink.stepForRoute("onboarding_bogus"))
    }
}
