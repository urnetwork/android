package com.bringyour.network.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The keyboard, mouse and trackpad support of the adaptive app quality
 * guidelines' Tier 2, as wired into the app, read from the sources: one focus
 * stop per control, Enter and Space on the custom switch, hover and focus
 * state layers that show on the connect screen, the context menus, keyboard
 * scrolling of the leaderboard, and no relaunch when a keyboard comes or goes.
 * The compose tree needs a device, which these JVM tests do not have; the key
 * rules are KeyboardMouseInputTest's.
 */
class KeyboardMouseWiringTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val SOURCE_DIR = "src/main/java/com/bringyour/network"

        private fun source(path: String) = moduleFile("$SOURCE_DIR/$path").readText()

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }
    }

    @Test
    fun aKeyboardComingOrGoingNeverRelaunchesAnActivity() {
        val nodes = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(moduleFile("src/main/AndroidManifest.xml"))
            .getElementsByTagName("activity")
        val configChanges = (0 until nodes.length).map { nodes.item(it) as Element }.associate {
            it.getAttribute("android:name") to it.getAttribute("android:configChanges").split("|").toSet()
        }
        for (activity in listOf(".MainActivity", ".LoginActivity")) {
            val handled = configChanges[activity]
            assertNotNull("$activity not declared", handled)
            // Keyboard_Input: a physical keyboard connected or disconnected
            assertTrue("$activity: $handled", handled!!.containsAll(listOf("keyboard", "keyboardHidden", "navigation")))
            // and the fold handling it already had (apple/DUO.md item 1)
            assertTrue(
                "$activity: $handled",
                handled.containsAll(listOf("screenSize", "smallestScreenSize", "screenLayout", "orientation", "density")),
            )
        }
    }

    @Test
    fun aLocationRowIsOneFocusStopWithAContextMenu() {
        val row = source("ui/connect/ProviderRow.kt")
        // a second focusable before the click took Tab and arrows first, and Enter did nothing there
        assertFalse(row.contains(".focusable()"))
        val modifiers = between(row, "ContextMenuBox(items = contextMenuItems)", "horizontalArrangement")
        assertTrue(modifiers.indexOf(".onFocusChanged") < modifiers.indexOf(".clickable"))
        assertTrue(modifiers.contains(".focusRing("))
        assertTrue(modifiers.contains("pointerHoverIcon(PointerIcon.Hand)"))

        // every row of the list, and the network peers above them, has the menu
        val list = source("ui/connect/LocationsList.kt")
        assertEquals(
            Regex("""ProviderRow\(""").findAll(list).count(),
            Regex("""contextMenuItems = locationMenu\(""").findAll(list).count(),
        )
        assertTrue(source("ui/stats/NetworkPeersSection.kt").contains("ContextMenuBox(items = contextMenuItems)"))
    }

    @Test
    fun theSearchFieldIsOneFocusStopAndEscLeavesIt() {
        val search = source("ui/components/URSearchInput.kt")
        assertFalse(search.contains(".focusable()"))
        assertTrue(search.contains(".onEscape {"))
        assertTrue(search.contains("searchEscape(value.text)"))
        // Enter searches once, through the field's Search action
        assertFalse(search.contains("Key.Enter"))
        assertTrue(search.contains("FocusDirection.Down"))
    }

    @Test
    fun theCustomSwitchIsAToggleable() {
        val switch = source("ui/components/URSwitch.kt")
        // Enter, Space, hover, focus and its state for assistive tech come with it
        assertTrue(switch.contains(".toggleable("))
        assertTrue(switch.contains("role = Role.Switch"))
        assertFalse(switch.contains("detectTapGestures"))
        assertFalse(switch.contains(".focusable()"))
        assertTrue(switch.contains(".focusRing("))
    }

    @Test
    fun menuRowsAndTheServerLinkAreOneFocusStopEach() {
        val popupRow = between(source("ui/components/AccountSwitcher.kt"), "fun PopupActionRow(", "@Preview")
        assertFalse(popupRow.contains(".focusable()"))
        assertTrue(popupRow.indexOf(".onFocusChanged") < popupRow.indexOf(".clickable"))
        // the popup's pointer-only catch areas never take focus
        val popup = between(source("ui/components/AccountSwitcher.kt"), "fun AccountSwitcherPopup(", "fun GuestPopup(")
        assertEquals(2, Regex("""focusProperties \{ canFocus = false \}""").findAll(popup).count())

        val selector = source("ui/login/NetworkServerSelector.kt")
        assertFalse(selector.contains(".focusable(enabled = enabled)"))
    }

    @Test
    fun stateLayersShowOnEverySurface() {
        val theme = source("ui/theme/Theme.kt")
        assertTrue(theme.contains("LocalRippleConfiguration provides"))
        // a black content color drew the connect screen's ripples black on black
        val scaffold = between(source("ui/MainNavHost.kt"), "NavigationSuiteScaffold(", "navigationSuiteItems")
        assertFalse(scaffold.contains("contentColor = Black"))
    }

    @Test
    fun swipeActionsAreAlsoAContextMenuAndTheHiddenButtonTakesNoFocus() {
        val swipe = source("ui/components/SwipeToRevealRow.kt")
        assertTrue(swipe.contains("ContextMenuBox("))
        val button = between(swipe, "// action button behind the content", "// foreground content")
        assertTrue(button.contains("focusProperties"))
        // a session's touch and hold copies its id; a right click offers it too
        val sessions = source("ui/account/SessionsScreen.kt")
        assertTrue(sessions.contains("contextMenuItems = listOf("))
    }

    @Test
    fun theLeaderboardsScrollFromTheKeyboard() {
        assertTrue(source("ui/leaderboard/LeaderboardScreen.kt").contains(".keyboardScrollable("))
        assertTrue(source("ui/leaderboard/PointsLeaderboardTab.kt").contains(".keyboardScrollable("))
    }

    @Test
    fun theCollapsedDrawerOpensForFocusAndClosesOnEsc() {
        val scaffold = between(source("ui/connect/ConnectScreen.kt"), "fun ConnectActionsSheetScaffold(", "fun ConnectMainContent(")
        assertTrue(scaffold.contains(".onFocusedBoundsChanged"))
        assertTrue(scaffold.contains("ConnectDrawerState.opensForFocus("))
        assertTrue(scaffold.contains(".onEscape {"))
        // a mouse press arms no drawer gesture; the wheel has its own rules
        assertTrue(scaffold.contains("touch = down.type != PointerType.Mouse"))
        assertTrue(scaffold.contains("sheetGesture.onWheel()"))
    }
}
