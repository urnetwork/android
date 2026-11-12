package com.bringyour.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 8259: a Play trial was credited at once, and the next day the
 * Account screen still read "Member Free" above the trial's Pro balance. Every
 * MainActivity flavor showed the stored network jwt's `pro` claim, which is
 * minted at sign in and stays Free after a purchase until the network token
 * renews on its own schedule (a jwt refresh renews only the client token).
 * The screens now show JwtManager.isPro, the sdk decision's Pro
 * (SubscriptionProTracker): the claim until a balance loads, the server's
 * answer after.
 *
 * The sdk's tracker needs the native library, which these JVM tests do not
 * have, so JwtManager gets a tracker with the sdk's rule (the rule itself is
 * tested in the sdk, subscription_pro_tracker_test.go), and the flavors are
 * read from their source.
 */
class ShownProTest {

    /** The sdk tracker's rule: the claim until a balance loads, the server's answer after. */
    private class SdkRuleProTracker : ProTracker {
        private var claimPro = false
        private var loaded = false
        private var serverIsPro = false
        private var asked = false

        // test jwts are "pro-<n>" or "free-<n>"
        override fun setJwt(byJwt: String): Boolean {
            val before = isPro
            claimPro = byJwt.startsWith("pro-")
            asked = false
            return before != isPro
        }

        override fun balanceLoaded(serverIsPro: Boolean): Boolean {
            loaded = true
            this.serverIsPro = serverIsPro
            if (serverIsPro == claimPro || asked) {
                return false
            }
            asked = true
            return true
        }

        override val isPro: Boolean
            get() = if (loaded) serverIsPro else claimPro

        override fun reset() {
            claimPro = false
            loaded = false
            serverIsPro = false
            asked = false
        }
    }

    @Test
    fun theShownProIsTheServersOnceABalanceLoads() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        assertFalse(jwtManager.isPro.value)

        // the stored jwt was minted before the purchase: its claim says Free
        jwtManager.trackStoredJwt("free-1")
        assertFalse(jwtManager.isPro.value)

        // the balance carries the trial's current subscription
        assertTrue("a claim that disagrees asks for a refresh", jwtManager.balanceLoaded(true))
        assertTrue("the screens still show the stale claim", jwtManager.isPro.value)

        // the refresh renewed the client token: the stored jwt read again is
        // the same, and the screens keep the server's answer
        jwtManager.trackStoredJwt("free-1")
        assertTrue(jwtManager.isPro.value)
        assertFalse(jwtManager.balanceLoaded(true))
        assertTrue(jwtManager.isPro.value)
    }

    @Test
    fun aLapseShowsFreeOnceTheServerSaysSo() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        jwtManager.trackStoredJwt("pro-1")
        assertTrue(jwtManager.isPro.value)
        jwtManager.balanceLoaded(false)
        assertFalse(jwtManager.isPro.value)
    }

    @Test
    fun aSignOutShowsFreeAndForgetsTheServersAnswer() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        jwtManager.trackStoredJwt("free-1")
        jwtManager.balanceLoaded(true)
        assertTrue(jwtManager.isPro.value)

        jwtManager.clearJwt()
        assertFalse(jwtManager.isPro.value)

        // the next session shows its own claim until its balance loads
        jwtManager.trackStoredJwt("free-2")
        assertFalse(jwtManager.isPro.value)
    }

    @Test
    fun noStoredJwtChangesNothing() {
        val jwtManager = JwtManager { SdkRuleProTracker() }
        jwtManager.trackStoredJwt("free-1")
        jwtManager.balanceLoaded(true)
        jwtManager.trackStoredJwt(null)
        jwtManager.trackStoredJwt("")
        assertTrue(jwtManager.isPro.value)
    }

    private fun source(path: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/$path") }
            .firstOrNull { it.isFile }
        assertNotNull(path, file)
        return file!!.readText()
    }

    @Test
    fun everyFlavorShowsTheDecisionsProNotTheStoredClaim() {
        for (flavor in listOf("google", "ethos_dapp", "solana_dapp", "ungoogle")) {
            val activity = source("$flavor/java/com/bringyour/network/MainActivity.kt")
            assertTrue(flavor, activity.contains("val isPro by jwtManager.isPro.collectAsState()"))
            assertFalse(flavor, activity.contains("jwt?.pro"))
            assertEquals(flavor, 1, Regex("isPro = isPro").findAll(activity).count())
        }
    }

    @Test
    fun everyLoadedBalanceReachesTheShownPro() {
        val viewModel = source("main/java/com/bringyour/network/ui/shared/viewmodels/SubscriptionBalanceViewModel.kt")
        assertTrue(viewModel.contains("jwtManager.balanceLoaded(serverIsPro)"))
        // a load handed to the tracker directly would leave the shown Pro behind
        assertFalse(viewModel.contains("proTracker.balanceLoaded("))
        assertFalse(source("main/java/com/bringyour/network/DeviceManager.kt").contains("proTracker.balanceLoaded("))
        assertFalse(source("main/java/com/bringyour/network/MainApplication.kt").contains("proTracker.balanceLoaded("))
    }
}
