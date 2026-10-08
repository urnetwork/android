package com.bringyour.network.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransientUiActionTest {
    @Test
    fun mainNavigationWaitsForAnArmedIntroDecisionAndItsDismissal() {
        assertFalse(postLoginMainNavigationReady(false, false, false, true, false))
        assertFalse(postLoginMainNavigationReady(false, false, true, true, false))
        assertFalse(postLoginMainNavigationReady(false, false, true, false, true))
        assertTrue(postLoginMainNavigationReady(false, false, true, false, false))
    }

    @Test
    fun promptClearedBeforeTheCollectedDisplayFlagArrivesIsNotReady() {
        // The show effect publishes display=true before allow=false. A
        // collected display=false can lag; the current display value cannot.
        val collectedDisplay = false
        val currentDisplay = true
        assertTrue(postLoginMainNavigationReady(false, false, true, false, collectedDisplay))
        assertFalse(postLoginMainNavigationReady(false, false, true, false, currentDisplay))
    }

    @Test
    fun guestProAndPreviouslyPromptedNavigationDoNotWaitForAnIntro() {
        assertTrue(postLoginMainNavigationReady(false, true, true, true, false))
        assertTrue(postLoginMainNavigationReady(true, false, false, true, false))
        assertTrue(postLoginMainNavigationReady(false, false, false, false, false))
        assertFalse(postLoginMainNavigationReady(false, true, false, true, false))
    }

    @Test
    fun aDisplayedIntroMustDisappearForEveryAccountState() {
        for (isPro in listOf(false, true)) {
            for (isGuest in listOf(false, true)) {
                for (known in listOf(false, true)) {
                    for (allowPrompt in listOf(false, true)) {
                        assertFalse(postLoginMainNavigationReady(isPro, isGuest, known, allowPrompt, true))
                    }
                }
            }
        }
    }

    @Test
    fun welcomeEnterIsSelectedAsAPostLoginAction() {
        assertEquals(
            PostLoginUiAction.WelcomeEnter,
            nextPostLoginUiAction(
                welcomeEnterPresent = true,
                introClosePresent = false,
                closePresent = false,
                closeOverlayPresent = false,
            ),
        )
    }

    @Test
    fun introCloseIsSelectedAfterTheWelcomeSurface() {
        assertEquals(
            PostLoginUiAction.IntroClose,
            nextPostLoginUiAction(
                welcomeEnterPresent = false,
                introClosePresent = true,
                closePresent = true,
                closeOverlayPresent = false,
            ),
        )
    }

    @Test
    fun closeActionsRemainAvailableWithoutTheWelcomeSurface() {
        assertEquals(
            PostLoginUiAction.CloseOverlay,
            nextPostLoginUiAction(
                welcomeEnterPresent = false,
                introClosePresent = false,
                closePresent = false,
                closeOverlayPresent = true,
            ),
        )
    }

    @Test
    fun disappearanceBetweenPresenceCheckAndActionIsBenign() {
        var present = true
        var actionCount = 0

        val performed = performTransientUiActionIfPresent(
            isPresent = { present },
            action = {
                actionCount += 1
                present = false
                throw AssertionError("node disappeared before the click")
            },
        )

        assertFalse(performed)
        assertFalse(present)
        assertEquals(1, actionCount)
    }

    @Test
    fun assertionWhileSurfaceRemainsPresentIsNotSuppressed() {
        try {
            performTransientUiActionIfPresent(
                isPresent = { true },
                action = { throw AssertionError("compose never became idle") },
            )
            fail("a persistent driver failure was reported as a disappearing surface")
        } catch (error: AssertionError) {
            assertEquals("compose never became idle", error.message)
        }
    }
}
