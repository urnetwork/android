package com.bringyour.network.ui.upgrade

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Where the past-due notice shows and what it opens (PastDueRenewal), read
 * from the module's sources, build file and string resources; no device.
 *
 * The notice sits under the plan in the account plan area (where the crypto
 * renewal notice is) and in the Settings plan row (where Manage subscription
 * is), on every flavor. Play's "Update payment method" hook gives no action,
 * as its Manage subscription opens nothing, so nothing Play compiles links to
 * billing outside Play Billing. No app code reads the balance's
 * update_payment_url, so a sign-in code only ever goes to ur.io.
 */
class PaymentPastDueNoticeWiringTest {

    private val upgrade = "java/com/bringyour/network/ui/upgrade"

    private fun src(path: String) = File("src/$path")

    // a source without its comments (which may name what they explain)
    private fun code(file: File): String =
        file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line -> line.replace(Regex("""(^|\s)//.*$"""), "") }

    private fun sourceFiles(sourceSet: String): List<File> =
        src(sourceSet).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

    // the text from [start] to the bracket that closes the one [start] ends with
    private fun block(text: String, start: String): String {
        val from = text.indexOf(start)
        assertTrue("no $start", 0 <= from)
        val open = start.last()
        val close = if (open == '{') '}' else ')'
        var depth = 0
        for (i in from until text.length) {
            if (text[i] == open) {
                depth += 1
            } else if (text[i] == close) {
                depth -= 1
                if (depth == 0) {
                    return text.substring(from, i + 1)
                }
            }
        }
        throw AssertionError("$start does not close")
    }

    @Test
    fun theAccountPlanAreaShowsTheNoticeOnEveryFlavor() {
        // shared code, beside the crypto lines the flavors add (AccountPaymentNotices)
        val account = code(src("main/java/com/bringyour/network/ui/account/AccountScreen.kt"))
        val planNotices = block(account, "planNotices = {")
        assertTrue(planNotices, planNotices.contains("PaymentPastDueNotice(subscriptionBalanceViewModel = subscriptionBalanceViewModel)"))
        assertTrue(planNotices, planNotices.contains("AccountPaymentNotices("))
    }

    @Test
    fun theSettingsPlanRowShowsTheNoticeOnEveryFlavor() {
        val navHost = code(src("main/java/com/bringyour/network/ui/MainNavHost.kt"))
        val settingsRoute = navHost.indexOf("composable<Route.Settings>(")
        assertTrue(0 <= settingsRoute)
        // the route's content: the first SettingsScreen call after it
        val call = block(navHost.substring(settingsRoute), "SettingsScreen(")
        val planNotices = block(call, "planNotices = {")
        assertTrue(planNotices, planNotices.contains("PaymentPastDueNotice(subscriptionBalanceViewModel = subscriptionBalanceViewModel)"))

        // the screen hands the lines on and renders them under the plan row's manage line
        val settings = code(src("main/java/com/bringyour/network/ui/settings/SettingsScreen.kt"))
        assertTrue(settings.contains("planNotices = planNotices,"))
        val manageLine = settings.indexOf("SubscriptionManageLine(currentStore = currentStore, stripePortalUrl = stripePortalUrl)")
        val rendered = settings.indexOf("planNotices()")
        assertTrue(0 <= manageLine && manageLine < rendered)
        assertTrue(rendered < settings.indexOf("R.string.developer"))
    }

    @Test
    fun theNoticeFollowsTheLatestBalance() {
        val viewModel = code(src("main/java/com/bringyour/network/ui/shared/viewmodels/SubscriptionBalanceViewModel.kt"))
        assertTrue(viewModel.contains("PastDueRenewal.noticeShows(answer?.result?.paymentPastDue)"))
        val notice = code(src("main/$upgrade/PaymentPastDueNotice.kt"))
        assertTrue(notice.contains("subscriptionBalanceViewModel.isPaymentPastDue.collectAsState()"))
        assertTrue(notice.contains("R.string.payment_past_due_notice"))
        assertTrue(notice.contains("R.string.payment_past_due_update_payment"))
    }

    @Test
    fun playShowsTheNoticeWithoutTheUpdatePaymentAction() {
        // Play's Manage subscription opens nothing, so its notice is the text alone
        val playHooks = code(src("google/$upgrade/PaymentMethodHooks.kt"))
        assertTrue(
            Regex("""fun rememberUpdatePaymentAction\([^)]*\): \(\(\) -> Unit\)\? = null""").containsMatchIn(playHooks)
        )
        val playManage = code(src("google/java/com/bringyour/network/ui/settings/ManageSubscriptionButton.kt"))
        assertTrue(!playManage.contains("startActivity") && !playManage.contains("openUri"))

        // the flavors that sell outside a store start the code and the link from their hook
        val opens = Regex("""\.openUpdatePayment\b""")
        assertTrue(opens.containsMatchIn(code(src("cryptoPay/$upgrade/PaymentMethodHooks.kt"))))
        // and nothing Play compiles does
        val playSourceSets = listOf("main", "play", "google", "debug")
        val playCallers = playSourceSets.flatMap { sourceFiles(it) }.filter { opens.containsMatchIn(code(it)) }
        assertEquals(emptyList<File>(), playCallers)
    }

    @Test
    fun noAppCodeOpensTheBalancesUpdatePaymentUrl() {
        // the sdk's PaymentPastDue.updatePaymentUrl is never read: the link is ur.io's constant
        val appSourceSets = src("").listFiles()!!
            .filter { it.isDirectory }
            .map { it.name }
            .filter { !it.startsWith("test") && it != "androidTest" && it != "acceptanceShared" }
        assertTrue(appSourceSets.containsAll(listOf("main", "google", "cryptoPay", "ungoogle", "solana_dapp", "ethos_dapp")))
        val readers = appSourceSets.flatMap { sourceFiles(it) }
            .filter { Regex("""\b(updatePaymentUrl|getUpdatePaymentUrl)\b""").containsMatchIn(code(it)) }
            .map { it.path }
        assertEquals(emptyList<String>(), readers)
    }

    private fun strings(dir: File): Map<String, Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("name") }
    }

    @Test
    fun theNoticeIsInEveryLocale() {
        val locales = src("main/res").listFiles { file -> file.name == "values" || file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.size > 1)

        val english = strings(src("main/res/values"))
        assertEquals(
            "Your UR Pro payment didn't go through. Update your payment method to keep Pro.",
            english["payment_past_due_notice"]?.textContent?.replace("\\'", "'"),
        )
        assertEquals("Update payment method", english["payment_past_due_update_payment"]?.textContent)

        val missing = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            for (key in listOf("payment_past_due_notice", "payment_past_due_update_payment")) {
                val value = values[key]
                if (value == null || value.textContent.isBlank() || value.getAttribute("translatable") == "false") {
                    missing.add("${locale.name}/$key")
                }
            }
            // the product name stays as the other strings keep it
            if (values["payment_past_due_notice"]?.textContent?.contains("UR Pro") != true) {
                missing.add("${locale.name}/payment_past_due_notice (UR Pro)")
            }
        }
        assertEquals(emptyList<String>(), missing)
    }
}
