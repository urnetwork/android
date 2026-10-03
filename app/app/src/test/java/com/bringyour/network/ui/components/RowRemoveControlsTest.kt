package com.bringyour.network.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowRemoveControlsTest {

    @Test
    fun blockedLocationRowShowsARemoveButton() {
        // unblocking used to be reachable only by swiping the row left, which
        // TalkBack, Switch Access and keyboard users cannot do
        assertTrue(
            "a blocked location row must show a remove button, not only swipe-to-reveal",
            RowRemoveControl.Button in blockedLocationRemoveControls,
        )
        assertTrue(RowRemoveControl.Swipe in blockedLocationRemoveControls)
    }

    @Test
    fun providerLocationRowShowsARemoveButton() {
        // removing a provider had the same swipe-only path
        assertTrue(
            "a provider location row must show a remove button, not only swipe-to-reveal",
            RowRemoveControl.Button in providerLocationRemoveControls,
        )
        assertTrue(RowRemoveControl.Swipe in providerLocationRemoveControls)
    }

    @Test
    fun swipeRowOffersDeleteAsAnAccessibilityAction() {
        var deletes = 0
        val actions = swipeToRevealAccessibilityActions("Remove") { deletes += 1 }
        assertEquals(
            "a swipe-to-reveal row must offer its delete to assistive tech",
            listOf("Remove"),
            actions.map { it.label },
        )
        assertTrue(actions.single().action())
        assertEquals(1, deletes)
    }
}
