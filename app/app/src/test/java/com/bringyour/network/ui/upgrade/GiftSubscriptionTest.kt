package com.bringyour.network.ui.upgrade

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * A redeemed gift code's year of Pro, a subscription on the store "gift"
 * (GIFTOFPRIVACY.md 10.5, "After redemption, everywhere"): every flavor's plan
 * row says "A gift. It doesn't renew." where it offers to manage, and offers
 * nothing to manage, cancel or update. The rules run over a stand-in for the
 * sdk's classifier; the wiring is read from the module's sources, as
 * PaymentPastDueNoticeWiringTest reads it; no device.
 */
class GiftSubscriptionTest {

    // the sdk's ClassifySubscriptionStore for these stores: exactly "gift", case
    // aside, before the other families
    private fun classify(store: String): String {
        val s = store.lowercase()
        return when {
            s == "gift" -> "gift"
            "stripe" in s -> "stripe"
            Regex("apple|itunes|ios|app.?store").containsMatchIn(s) -> "apple"
            Regex("google|play|android").containsMatchIn(s) -> "google"
            Regex("bitcoin|bittensor|ethereum|monero|solana|zcash").containsMatchIn(s) -> "crypto"
            else -> "other"
        }
    }

    @Test
    fun onlyTheGiftStoreIsAGift() {
        assertTrue(isGiftStore("gift", ::classify))
        assertTrue(isGiftStore("Gift", ::classify))
        for (store in listOf("stripe", "google", "apple", "solana", "manual", "", null)) {
            assertFalse("$store", isGiftStore(store, ::classify))
        }
    }

    @Test
    fun aGiftAloneIsWhatThePlanRowsDescribe() {
        assertEquals("gift", planStore("gift", listOf("gift"), ::classify))
        assertEquals("gift", planStore("gift", emptyList(), ::classify))
        // the server's entry for renewals of no known store names nothing to manage
        assertEquals("gift", planStore("gift", listOf("", "gift"), ::classify))
    }

    @Test
    fun aSubscriptionChargingBesideAGiftKeepsItsManageControl() {
        // the server names the first active store by name: "gift" before these
        assertEquals("google", planStore("gift", listOf("gift", "google"), ::classify))
        assertEquals("stripe", planStore("gift", listOf("gift", "stripe"), ::classify))
        assertEquals("solana", planStore("gift", listOf("gift", "solana"), ::classify))
    }

    @Test
    fun anyOtherStoreIsDescribedAsTheServerNamesIt() {
        assertEquals("apple", planStore("apple", listOf("apple", "gift"), ::classify))
        assertEquals("stripe", planStore("stripe", listOf("stripe"), ::classify))
        assertNull(planStore(null, emptyList(), ::classify))
    }

    private fun src(path: String) = File("src/$path")

    // a source without its comments (which may name what they explain)
    private fun code(file: File): String =
        file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line -> line.replace(Regex("""(^|\s)//.*$"""), "") }

    // the text from [start] to the brace that closes the one [start] ends with
    private fun block(text: String, start: String): String {
        val from = text.indexOf(start)
        assertTrue("no $start", 0 <= from)
        var depth = 0
        for (i in from until text.length) {
            if (text[i] == '{') {
                depth += 1
            } else if (text[i] == '}') {
                depth -= 1
                if (depth == 0) {
                    return text.substring(from, i + 1)
                }
            }
        }
        throw AssertionError("$start does not close")
    }

    @Test
    fun everyFlavorsSettingsPlanRowSaysAGiftDoesNotRenewInPlaceOfManage() {
        // Play's hooks (src/google) and every other flavor's (src/cryptoPay)
        for (sourceSet in listOf("google", "cryptoPay")) {
            val hooks = code(src("$sourceSet/java/com/bringyour/network/ui/upgrade/PaymentMethodHooks.kt"))
            val line = block(hooks, "fun SubscriptionManageLine(currentStore: String?, stripePortalUrl: String?) {")
            val gift = block(line, "if (isGiftStore(currentStore)) {")
            assertTrue(sourceSet, gift.contains("GiftDoesNotRenewLine()"))
            assertFalse(sourceSet, gift.contains("ManageSubscriptionButton"))
            // checked before anything else the line can show
            assertEquals(sourceSet, line.indexOf("if ("), line.indexOf("if (isGiftStore(currentStore))"))
        }
    }

    @Test
    fun theAccountPlanAreaSaysAGiftDoesNotRenewOnEveryFlavor() {
        // shared code, under the plan with the other plan notices
        val account = code(src("main/java/com/bringyour/network/ui/account/AccountScreen.kt"))
        val planNotices = block(account, "planNotices = {")
        assertTrue(planNotices, planNotices.contains("GiftPlanNotice(subscriptionBalanceViewModel = subscriptionBalanceViewModel)"))
    }

    @Test
    fun theGiftLinesSayItAndOfferNothing() {
        val gift = code(src("main/java/com/bringyour/network/ui/upgrade/GiftSubscription.kt"))
        for (start in listOf("fun GiftDoesNotRenewLine() {", "fun GiftPlanNotice(subscriptionBalanceViewModel: SubscriptionBalanceViewModel) {")) {
            val body = block(gift, start)
            assertTrue(start, body.contains("R.string.gift_does_not_renew"))
            // a line of text: nothing to manage, cancel or update
            assertFalse(start, Regex("""clickable|Button|onClick|openUri|startActivity|openUpdatePayment""").containsMatchIn(body))
        }
    }

    @Test
    fun thePlanRowsFollowThePlanStore() {
        val viewModel = code(src("main/java/com/bringyour/network/ui/shared/viewmodels/SubscriptionBalanceViewModel.kt"))
        assertTrue(viewModel.contains("_currentStore.value = planStore(result.currentSubscription?.store, activeStores)"))
    }

    private fun strings(dir: File): Map<String, Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("name") }
    }

    @Test
    fun theGiftStringsAreInEveryLocale() {
        val locales = src("main/res").listFiles { file -> file.name == "values" || file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.size > 1)

        val english = strings(src("main/res/values"))
        assertEquals("A gift. It doesn't renew.", english["gift_does_not_renew"]?.textContent?.replace("\\'", "'"))
        assertEquals("A year of Pro", english["balance_code_pro_year"]?.textContent)

        val missing = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            for (key in listOf("gift_does_not_renew", "balance_code_pro_year", "gift_pro_redeemed", "gift_pro_redeemed_queued")) {
                if (values[key]?.textContent.isNullOrBlank()) {
                    missing.add("${locale.name}/$key")
                }
            }
            // the start date goes in as the one string argument
            val queued = values["gift_pro_redeemed_queued"]?.textContent ?: ""
            if (Regex("""%\d*\$?[a-zA-Z]""").findAll(queued).map { it.value }.toList() != listOf("%1\$s")) {
                missing.add("${locale.name}/gift_pro_redeemed_queued (%1\$s)")
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
