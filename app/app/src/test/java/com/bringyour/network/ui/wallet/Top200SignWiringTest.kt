package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign fleet binding as wired into the app, read from the sources: the
 * screen, the activities and the sdk need a device. The behavior is
 * Top200BindingTest's.
 */
class Top200SignWiringTest {

    private fun source(path: String): String = File("src/$path").readText()

    private fun wallet(name: String): String = source("main/java/com/bringyour/network/ui/wallet/$name")

    // the text between two markers, failing when either is missing
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        val to = source.indexOf(end, from + start.length)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun `the screen looks up every sign string`() {
        val strings = source("main/res/values/strings.xml")
        val keys = Regex("<string name=\"(top200_sign_[a-z_]+)\"").findAll(strings).map { it.groupValues[1] }.toList()
        assertEquals(20, keys.size)
        val ui = wallet("Top200SignScreen.kt") + wallet("Top200Tile.kt")
        for (key in keys) {
            assertTrue("unused: $key", Regex("R\\.string\\.$key\\b").containsMatchIn(ui))
        }
        assertTrue(ui.contains("R.string.client_id"))
        assertTrue(ui.contains("R.string.copied"))
    }

    @Test
    fun `both top 200 states offer sign fleet binding once binding is open`() {
        // while binding is closed it stays hidden (apple/LEADERTOP.md §8, Q16)
        val tile = wallet("Top200Tile.kt")
        val projected = between(tile, "fun Top200Tile(", "fun Top200BoundRow(")
        assertTrue(between(projected, "if (state.bindingOpen) {", "} else {").contains("SignFleetBindingButton(onClick = onSignFleetBinding)"))
        val claimed = between(tile, "fun Top200BoundRow(", "private fun Top200Heading(")
        assertTrue(between(claimed, "if (state.bindingOpen) {", "}").contains("SignFleetBindingButton(onClick = onSignFleetBinding)"))
        val earnings = wallet("EarningsScreen.kt")
        assertTrue(earnings.contains("val onSignFleetBinding = { navController.navigate(Route.Top200Sign) }"))
        assertTrue(between(earnings, "Top200BoundRow(", ")").contains("onSignFleetBinding = onSignFleetBinding,"))
        assertTrue(between(earnings, "Top200Tile(", ")").contains("onSignFleetBinding = onSignFleetBinding,"))
    }

    @Test
    fun `the nav host routes the screen and the app link`() {
        val nav = source("main/java/com/bringyour/network/ui/MainNavHost.kt")
        assertTrue(nav.contains("composable<Route.Top200Sign>("))
        assertTrue(nav.contains("Top200SignScreen(\n                        navController = navController,"))
        assertTrue(nav.contains("Top200SignLinks.navigation.collectAsState()"))
        assertTrue(source("main/java/com/bringyour/network/ui/MainNavViewModel.kt").contains("@Serializable object Top200Sign : Route()"))
    }

    @Test
    fun `every login activity forwards the app link only when signed in, before the referral catch-all`() {
        for (flavor in listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")) {
            val login = source("$flavor/java/com/bringyour/network/LoginActivity.kt")
            val branch = between(login, "} else if (Top200Bindings.isSignLink(u.scheme, u.host, u.path)) {", "} else if ((u.scheme == \"https\"")
            assertTrue(flavor, branch.contains("if (app.device != null) {"))
            assertTrue(flavor, branch.contains("forwardTop200SignLink(this, Top200Bindings.linkBindingParameter(u.encodedQuery), MainActivity::class.java)"))
            // the link (and its binding) is never logged
            assertFalse(flavor, branch.contains("\$u"))
        }
    }

    @Test
    fun `the screen signs only through the flow and sends nothing`() {
        val screen = wallet("Top200SignScreen.kt")
        val flow = wallet("Top200SignFlow.kt")
        // the one signing entry point
        assertEquals(1, Regex("signSnFleetBinding\\(").findAll(screen + flow).count())
        assertTrue(flow.contains("val check = Top200Bindings.check(current.text, d.clientId, d.clientKey)"))
        assertTrue(flow.contains("if (check !is Top200BindingCheck.Valid) {"))
        // no api, no log of the binding or payload
        for (s in listOf(screen, flow)) {
            assertFalse(s.contains(".api"))
            assertFalse(s.contains("Log."))
        }
        // the link path never signs
        assertFalse(between(flow, "fun applyLink(", "suspend fun sign()").contains("sign"))
    }
}
