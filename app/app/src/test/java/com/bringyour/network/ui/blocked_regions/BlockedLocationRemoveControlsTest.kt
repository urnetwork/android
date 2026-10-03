package com.bringyour.network.ui.blocked_regions

import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedLocationRemoveControlsTest {

    @Test
    fun rowShowsARemoveButton() {
        // unblocking used to be reachable only by swiping the row left, which
        // TalkBack, Switch Access and keyboard users cannot do
        assertTrue(
            "a blocked location row must show a remove button, not only swipe-to-reveal",
            BlockedLocationRemoveControl.Button in blockedLocationRemoveControls,
        )
    }

    @Test
    fun swipeStaysAsAShortcut() {
        assertTrue(BlockedLocationRemoveControl.Swipe in blockedLocationRemoveControls)
    }
}
