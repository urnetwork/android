package com.bringyour.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 4074: in December 2025 the balance read failed under database
 * load, and the app showed paying networks Free, with its upgrade prompts. The
 * plan cards showed `isPro`, which before a balance loads is the stored jwt's
 * claim, Free after a purchase until the network token renews, and stays so
 * while the read fails. They now show the sdk's plan (ShownPlan, through
 * JwtManager.plan): Pro or Free as the server answered, else a spinner, or the
 * read's error with a retry once it failed; Change, Get Pro, the upgrade tip
 * and the offer lines only on the server's Free.
 *
 * The sdk's tracker needs the native library, which these jvm tests do not
 * have: JwtManager gets SdkRuleProTracker (the rule is tested in the sdk,
 * subscription_shown_plan_test.go), and the screens are read from source.
 */
class ShownPlanTest {

    /** The sdk's plan and read state, as each card state. */
    @Test
    fun theSdksPlanMapsToWhatACardShows() {
        assertEquals(ShownPlan.Pro, ShownPlan.of(sdkPlan = "pro", lastReadFailed = false))
        assertEquals(ShownPlan.Pro, ShownPlan.of(sdkPlan = "pro", lastReadFailed = true))
        assertEquals(ShownPlan.Free, ShownPlan.of(sdkPlan = "free", lastReadFailed = false))
        assertEquals(ShownPlan.Free, ShownPlan.of(sdkPlan = "free", lastReadFailed = true))
        assertEquals(ShownPlan.Loading, ShownPlan.of(sdkPlan = "unknown", lastReadFailed = false))
        assertEquals(ShownPlan.Unreachable, ShownPlan.of(sdkPlan = "unknown", lastReadFailed = true))
        // a value this app does not know is not Free either
        assertEquals(ShownPlan.Loading, ShownPlan.of(sdkPlan = "", lastReadFailed = false))
        assertEquals(listOf(ShownPlan.Free), ShownPlan.values().filter { it.sellsPro })
    }

    /** A paying network with a stale Free claim whose read fails: unknown, then Pro. */
    @Test
    fun aFailedReadIsNeverFree() {
        val jwtManager = JwtManager { SdkRuleProTracker() }

        // a network that just paid: its stored jwt still claims Free
        jwtManager.trackStoredJwt("free-1")
        assertEquals("the read on its way showed Free", ShownPlan.Loading, jwtManager.plan.value)
        assertFalse(jwtManager.isPro.value)

        // the read fails, as on 2025-12-04
        jwtManager.balanceFailed()
        assertEquals("a failed read showed Free", ShownPlan.Unreachable, jwtManager.plan.value)
        assertFalse(jwtManager.plan.value.sellsPro)

        // the retry reads the server's answer, with the one jwt refresh the
        // failure did not use
        assertTrue(jwtManager.balanceLoaded(true))
        assertEquals(ShownPlan.Pro, jwtManager.plan.value)
        assertTrue(jwtManager.isPro.value)

        // a read that fails after the server answered keeps the answer
        jwtManager.balanceFailed()
        assertEquals(ShownPlan.Pro, jwtManager.plan.value)
        assertTrue(jwtManager.isPro.value)
    }

    /** Free only from the server; a Pro claim stays Pro through a failed read. */
    @Test
    fun onlyTheServerSaysFree() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        jwtManager.trackStoredJwt("free-1")
        assertEquals(ShownPlan.Loading, jwtManager.plan.value)
        jwtManager.balanceLoaded(false)
        assertEquals(ShownPlan.Free, jwtManager.plan.value)
        assertTrue(jwtManager.plan.value.sellsPro)
        // the server's Free stands through a failed read
        jwtManager.balanceFailed()
        assertEquals(ShownPlan.Free, jwtManager.plan.value)

        // a Pro claim is Pro until the server answers, also through a failure
        val proClaim = JwtManager { SdkRuleProTracker() }
        proClaim.trackStoredJwt("pro-1")
        proClaim.balanceFailed()
        assertEquals(ShownPlan.Pro, proClaim.plan.value)
        assertTrue(proClaim.isPro.value)
    }

    /** A sign out forgets the plan and a failed read. */
    @Test
    fun aSignOutForgetsThePlanAndAFailedRead() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        jwtManager.trackStoredJwt("free-1")
        jwtManager.balanceFailed()
        jwtManager.clearJwt()
        assertEquals(ShownPlan.Loading, jwtManager.plan.value)
        assertFalse(jwtManager.isPro.value)
        jwtManager.trackStoredJwt("free-2")
        assertEquals(ShownPlan.Loading, jwtManager.plan.value)
    }

    /** A source file of the app, from the directory the tests run in. */
    private fun source(path: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/$path") }
            .firstOrNull { it.isFile }
        assertNotNull(path, file)
        return file!!.readText()
    }

    /** The text of [source] from [start] up to the next [end]. */
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing $start", 0 <= from)
        val to = source.indexOf(end, from)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    /** Both failure paths of the balance read reach the plan. */
    @Test
    fun everyFailedReadReachesThePlan() {
        val viewModel = source("main/java/com/bringyour/network/ui/shared/viewmodels/SubscriptionBalanceViewModel.kt")
        val fetch = between(viewModel, "    val fetchSubscriptionBalance: () -> Unit = {", "\n    private val isSupporterWithBalance")
        // an error, and an answer with no balance
        assertEquals(fetch, 2, Regex("jwtManager\\.balanceFailed\\(\\)").findAll(fetch).count())
        assertTrue(viewModel.contains("val shownPlan: StateFlow<com.bringyour.network.ShownPlan> = jwtManager.plan"))
    }

    /** Every plan card shows the sdk's plan, and sells Pro only on Free. */
    @Test
    fun everyPlanCardShowsTheSdksPlan() {
        // the Account plan, in every flavor
        for (flavor in listOf("google", "ethos_dapp", "solana_dapp", "ungoogle")) {
            val row = source("$flavor/java/com/bringyour/network/ui/account/AccountRootSubscription.kt")
            assertTrue(flavor, row.contains("ShownPlanName("))
            assertTrue(flavor, row.contains("if (shownPlan.sellsPro && "))
            assertFalse(flavor, row.contains("stringResource(id = R.string.free)"))
            assertFalse(flavor, row.contains("if (currentPlan == Plan.Basic"))
        }
        val account = source("main/java/com/bringyour/network/ui/account/AccountScreen.kt")
        assertTrue(account.contains("val shownPlan by subscriptionBalanceViewModel.shownPlan.collectAsState()"))
        assertTrue(account.contains("if (shownPlan.sellsPro && accountOffer != null)"))
        assertTrue(account.contains("} else if (shownPlan.sellsPro && accountWinback != null)"))

        // Connect's plan card, in the drawer and in the pane
        val actions = source("main/java/com/bringyour/network/ui/connect/ConnectActions.kt")
        val card = between(actions, "internal fun ConnectPlanCard(", "\n}\n")
        assertTrue(card, card.contains("ShownPlanName(shownPlan = shownPlan, retryPlan = retryPlan)"))
        assertTrue(card, card.contains("if (shownPlan.sellsPro) {"))
        assertFalse(card, card.contains("R.string.free"))
        val connect = source("main/java/com/bringyour/network/ui/connect/ConnectScreen.kt")
        assertEquals(2, Regex("shownPlan = shownPlan,").findAll(connect).count())

        // Settings' plan row
        val settings = source("main/java/com/bringyour/network/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("ShownPlanName("))
        assertEquals(2, Regex("if \\(shownPlan\\.sellsPro\\) \\{").findAll(settings).count())
        assertFalse(settings.contains("if (currentPlan == Plan.Basic) {"))
        assertTrue(source("main/java/com/bringyour/network/ui/MainNavHost.kt")
            .contains("shownPlan = subscriptionBalanceViewModel.shownPlan.collectAsState().value,"))
    }
}
