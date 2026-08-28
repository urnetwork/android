package com.bringyour.network.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransientUiActionTest {
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
}
