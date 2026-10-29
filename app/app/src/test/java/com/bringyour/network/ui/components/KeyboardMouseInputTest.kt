package com.bringyour.network.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key rules behind the keyboard support of the adaptive app quality
 * guidelines' Tier 2 (KeyboardMouseInput.kt): the context menu keys, the keys
 * that scroll a list, and Esc in a search field.
 */
class KeyboardMouseInputTest {

    @Test
    fun theContextMenuKeyAndShiftF10OpenAContextMenu() {
        assertTrue(isContextMenuKey(Key.Menu, isShiftPressed = false))
        assertTrue("with Shift as well", isContextMenuKey(Key.Menu, isShiftPressed = true))
        assertTrue(isContextMenuKey(Key.F10, isShiftPressed = true))
    }

    @Test
    fun otherKeysDoNotOpenAContextMenu() {
        assertFalse("F10 alone", isContextMenuKey(Key.F10, isShiftPressed = false))
        // Enter and Space activate the element; they never open its menu
        assertFalse(isContextMenuKey(Key.Enter, isShiftPressed = false))
        assertFalse(isContextMenuKey(Key.Spacebar, isShiftPressed = true))
        assertFalse(isContextMenuKey(Key.Escape, isShiftPressed = false))
    }

    @Test
    fun enterOpensTheMenuOfAnElementWithNoClick() {
        // a session row takes focus but has no click: Enter opens its menu
        assertTrue(isContextMenuKey(Key.Enter, isShiftPressed = false, enterOpens = true))
        assertTrue(isContextMenuKey(Key.NumPadEnter, isShiftPressed = false, enterOpens = true))
        assertTrue(isContextMenuKey(Key.Menu, isShiftPressed = false, enterOpens = true))
        // Space still never does
        assertFalse(isContextMenuKey(Key.Spacebar, isShiftPressed = false, enterOpens = true))
    }

    @Test
    fun theArrowsScrollALine() {
        assertEquals(KeyboardScroll.LineUp, keyboardScrollFor(Key.DirectionUp, isShiftPressed = false))
        assertEquals(KeyboardScroll.LineDown, keyboardScrollFor(Key.DirectionDown, isShiftPressed = false))
    }

    @Test
    fun pageKeysAndSpaceScrollAPage() {
        assertEquals(KeyboardScroll.PageUp, keyboardScrollFor(Key.PageUp, isShiftPressed = false))
        assertEquals(KeyboardScroll.PageDown, keyboardScrollFor(Key.PageDown, isShiftPressed = false))
        // Space as in a browser, Shift+Space back up
        assertEquals(KeyboardScroll.PageDown, keyboardScrollFor(Key.Spacebar, isShiftPressed = false))
        assertEquals(KeyboardScroll.PageUp, keyboardScrollFor(Key.Spacebar, isShiftPressed = true))
    }

    @Test
    fun homeAndEndJumpToTheEnds() {
        assertEquals(KeyboardScroll.Top, keyboardScrollFor(Key.MoveHome, isShiftPressed = false))
        assertEquals(KeyboardScroll.Bottom, keyboardScrollFor(Key.MoveEnd, isShiftPressed = false))
        // the device's Home button is not the keyboard's Home key
        assertNull(keyboardScrollFor(Key.SystemHome, isShiftPressed = false))
    }

    @Test
    fun otherKeysGoOnToFocusNavigation() {
        assertNull(keyboardScrollFor(Key.Tab, isShiftPressed = false))
        assertNull(keyboardScrollFor(Key.Tab, isShiftPressed = true))
        assertNull(keyboardScrollFor(Key.DirectionLeft, isShiftPressed = false))
        assertNull(keyboardScrollFor(Key.DirectionRight, isShiftPressed = false))
        assertNull(keyboardScrollFor(Key.Enter, isShiftPressed = false))
        assertNull(keyboardScrollFor(Key.Escape, isShiftPressed = false))
    }

    @Test
    fun aLineIsTheLineHeightAndAPageMostOfTheViewport() {
        assertEquals(-56f, keyboardScrollDistance(KeyboardScroll.LineUp, viewportPx = 800f, linePx = 56f)!!, 0f)
        assertEquals(56f, keyboardScrollDistance(KeyboardScroll.LineDown, viewportPx = 800f, linePx = 56f)!!, 0f)
        // a page keeps a line of context in view
        assertEquals(700f, keyboardScrollDistance(KeyboardScroll.PageDown, viewportPx = 800f, linePx = 56f)!!, 0f)
        assertEquals(-700f, keyboardScrollDistance(KeyboardScroll.PageUp, viewportPx = 800f, linePx = 56f)!!, 0f)
        assertTrue(keyboardScrollDistance(KeyboardScroll.PageDown, viewportPx = 800f, linePx = 56f)!! < 800f)
    }

    @Test
    fun theEndsAreJumpsNotDistances() {
        assertNull(keyboardScrollDistance(KeyboardScroll.Top, viewportPx = 800f, linePx = 56f))
        assertNull(keyboardScrollDistance(KeyboardScroll.Bottom, viewportPx = 800f, linePx = 56f))
    }

    @Test
    fun escClearsTheSearchThenLeavesTheField() {
        assertEquals(SearchEscape.ClearSearch, searchEscape("ger"))
        assertEquals(SearchEscape.ClearSearch, searchEscape(" "))
        assertEquals(SearchEscape.LeaveField, searchEscape(""))
    }
}
