package com.bringyour.network.ui.upgrade

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The past-due notice's rules (PastDueRenewal): it shows exactly while the
 * latest balance carries payment_past_due, with no time or store rule, and
 * "Update payment method" opens ur.io's Manage Subscription with the one-time
 * auth code as one query value (encoded as the Apple apps encode it,
 * manageSubscriptionOnWebURL), or without a code when none could be created,
 * never at a url the server sent. Plain values; no device, network or clock.
 */
class PastDueRenewalTest {

    // a balance's payment_past_due as the server sends it (the sdk's PaymentPastDue)
    private data class WirePaymentPastDue(val store: String, val failTime: String, val updatePaymentUrl: String)

    // the query's items, decoded ("+" never appears encoded, see aCodeCannotChangeTheLink)
    private fun queryItems(uri: URI): List<Pair<String, String>> =
        uri.rawQuery.split("&").map { item ->
            val name = item.substringBefore("=")
            val value = item.substringAfter("=", "")
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun aBalanceThatCarriesPaymentPastDueShowsTheNotice() {
        val pastDue = WirePaymentPastDue(
            store = "stripe",
            failTime = "2026-10-26T15:56:00Z",
            updatePaymentUrl = "https://ur.io/app/subscription",
        )
        assertTrue(PastDueRenewal.noticeShows(pastDue))
    }

    @Test
    fun theNoticeShowsWhateverTheFailureTimeOrStore() {
        // no time rule and no store rule: shown for as long as the server sends the field
        val cases = listOf(
            WirePaymentPastDue(store = "stripe", failTime = "2020-01-01T00:00:00Z", updatePaymentUrl = "https://ur.io/app/subscription"),
            WirePaymentPastDue(store = "stripe", failTime = "2099-12-31T23:59:59Z", updatePaymentUrl = "https://ur.io/app/subscription"),
            WirePaymentPastDue(store = "stripe", failTime = "not a time", updatePaymentUrl = ""),
            WirePaymentPastDue(store = "", failTime = "", updatePaymentUrl = ""),
            WirePaymentPastDue(store = "play", failTime = "2026-10-26T15:56:00Z", updatePaymentUrl = ""),
        )
        for (pastDue in cases) {
            assertTrue("$pastDue", PastDueRenewal.noticeShows(pastDue))
        }
    }

    @Test
    fun aBalanceWithoutPaymentPastDueHidesTheNotice() {
        // no failing renewal, an older server's balance that has no such field,
        // and no balance yet all read as null (the sdk drops an absent field)
        assertFalse(PastDueRenewal.noticeShows(null))
    }

    @Test
    fun theLinkOpensManageSubscriptionWithTheCode() {
        // the server's codes are base64url with "=" padding
        val url = PastDueRenewal.manageSubscriptionUrl("AbC-dEf_123=")
        assertEquals("https://ur.io/app/subscription?auth_code=AbC-dEf_123%3D", url)
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("ur.io", uri.host)
        assertEquals("/app/subscription", uri.path)
        assertEquals(listOf("auth_code" to "AbC-dEf_123="), queryItems(uri))
        assertNull(uri.fragment)
    }

    @Test
    fun aCodeCannotChangeTheLink() {
        // "&", "=", "#", "?" and "+" stay inside the one parameter ("+" would read as a space)
        val code = "a&next=x#y+z?w"
        val url = PastDueRenewal.manageSubscriptionUrl(code)
        assertEquals("https://ur.io/app/subscription?auth_code=a%26next%3Dx%23y%2Bz%3Fw", url)
        val uri = URI(url)
        assertEquals("/app/subscription", uri.path)
        assertEquals(listOf("auth_code" to code), queryItems(uri))
        assertNull(uri.fragment)
        assertFalse(uri.rawQuery.contains("+"))
    }

    @Test
    fun spacesPercentSignsAndNonAsciiAreEncoded() {
        val cases = listOf(
            "a b" to "a%20b",
            "a\tb" to "a%09b",
            "100%" to "100%25",
            "%41" to "%2541",
            "\"<>\\^`{|}[]" to "%22%3C%3E%5C%5E%60%7B%7C%7D%5B%5D",
            "é€" to "%C3%A9%E2%82%AC",
        )
        for ((code, encoded) in cases) {
            val url = PastDueRenewal.manageSubscriptionUrl(code)
            assertEquals(code, "https://ur.io/app/subscription?auth_code=$encoded", url)
            assertEquals(code, listOf("auth_code" to code), queryItems(URI(url)))
        }
    }

    @Test
    fun theQueryCharactersTheAppleAppsKeepStayAsTheyAre() {
        // CharacterSet.urlQueryAllowed without "&=+#?" (manageSubscriptionOnWebURL)
        val kept = "AZaz09-._~!$'()*,;:@/"
        assertEquals("https://ur.io/app/subscription?auth_code=$kept", PastDueRenewal.manageSubscriptionUrl(kept))
    }

    @Test
    fun theLinkIsAlwaysUrIoManageSubscription() {
        // the base is the constant, not the wire's update_payment_url, and no code moves it
        assertEquals("https://ur.io/app/subscription", PastDueRenewal.MANAGE_SUBSCRIPTION_URL)
        val codes = listOf(
            "https://elsewhere.example/app/subscription",
            "//elsewhere.example/app/subscription",
            "@elsewhere.example",
            "../../account",
            "\\\\elsewhere.example\\x",
        )
        for (code in codes) {
            val uri = URI(PastDueRenewal.manageSubscriptionUrl(code))
            assertEquals(code, "https", uri.scheme)
            assertEquals(code, "ur.io", uri.host)
            assertEquals(code, "/app/subscription", uri.path)
            assertEquals(code, listOf("auth_code" to code), queryItems(uri))
            assertNull(code, uri.fragment)
        }
    }

    @Test
    fun withoutACodeThePageOpensWithoutOne() {
        // no code could be created (an error or a blank code): ur.io asks the user to sign in
        for (code in listOf(null, "", " ", " \n\t")) {
            assertEquals("$code", "https://ur.io/app/subscription", PastDueRenewal.manageSubscriptionUrl(code))
        }
    }

    @Test
    fun theCodeIsTrimmed() {
        assertEquals(
            "https://ur.io/app/subscription?auth_code=AbC%3D",
            PastDueRenewal.manageSubscriptionUrl(" AbC=\n"),
        )
    }
}
