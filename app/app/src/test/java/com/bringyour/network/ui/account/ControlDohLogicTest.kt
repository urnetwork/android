package com.bringyour.network.ui.account

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bootstrap DNS-over-HTTPS servers field's decisions (P216): how the
 * multiline field reads as the list the sdk is given and shows the list it
 * stores, what shows under it, and the message of every sdk error id.
 */
class ControlDohLogicTest {

    // the 4 error ids the sdk defines (control_doh.go)
    private val sdkErrorIds = listOf(
        "control_doh_error_url_invalid",
        "control_doh_error_https_required",
        "control_doh_error_ip_required",
        "control_doh_error_too_many",
    )

    // Sdk.regionalControlDohUrls("cn"), v4 first (connect
    // net_http_doh_regional.go): AliDNS, then DNSPod
    private val chinaPreset = listOf(
        "https://223.5.5.5/dns-query",
        "https://223.6.6.6/dns-query",
        "https://1.12.12.12/dns-query",
        "https://120.53.53.53/dns-query",
    )

    @Test
    fun aLineEndsAtANewlineOrACarriageReturn() {
        // windows (\r\n), old mac (\r) and unix (\n) line endings read the same
        assertEquals(
            listOf("https://223.5.5.5/dns-query", "https://223.6.6.6/dns-query", "https://1.12.12.12/dns-query"),
            controlDohLinesFromText(
                "https://223.5.5.5/dns-query\r\nhttps://223.6.6.6/dns-query\rhttps://1.12.12.12/dns-query\n"
            ),
        )
    }

    @Test
    fun blankLinesAndPaddingAreNotServers() {
        assertEquals(
            listOf("https://223.5.5.5/dns-query", "https://[2400:3200::1]/dns-query"),
            controlDohLinesFromText("  https://223.5.5.5/dns-query \n\n\t\r\n   \nhttps://[2400:3200::1]/dns-query\t"),
        )
        assertEquals(listOf<String>(), controlDohLinesFromText(""))
        assertEquals(listOf<String>(), controlDohLinesFromText(" \r\n\t\n"))
    }

    @Test
    fun aCommaDoesNotSeparateServers() {
        assertEquals(
            listOf("https://223.5.5.5/dns-query,https://223.6.6.6/dns-query"),
            controlDohLinesFromText("https://223.5.5.5/dns-query,https://223.6.6.6/dns-query"),
        )
        assertEquals(
            listOf("https://223.5.5.5/dns-query, https://223.6.6.6/dns-query"),
            controlDohLinesFromText("https://223.5.5.5/dns-query, https://223.6.6.6/dns-query"),
        )
    }

    @Test
    fun theLinesKeepTheirOrderAndRepeatsForTheSdk() {
        // the order typed is the order tried, and the sdk drops the repeat
        assertEquals(
            listOf("https://1.12.12.12/dns-query", "https://223.5.5.5/dns-query", "https://1.12.12.12/dns-query"),
            controlDohLinesFromText(
                "https://1.12.12.12/dns-query\nhttps://223.5.5.5/dns-query\nhttps://1.12.12.12/dns-query"
            ),
        )
    }

    @Test
    fun thePresetFillsTheFieldOneServerPerLine() {
        val text = controlDohText(chinaPreset)

        assertEquals(
            "https://223.5.5.5/dns-query\nhttps://223.6.6.6/dns-query\nhttps://1.12.12.12/dns-query\nhttps://120.53.53.53/dns-query",
            text,
        )
        // and reads back as the same list
        assertEquals(chinaPreset, controlDohLinesFromText(text))
        assertEquals("cn", CONTROL_DOH_PRESET_CHINA)
    }

    @Test
    fun noServersIsAnEmptyField() {
        // the default servers alone, which the field shows as nothing
        assertEquals("", controlDohText(listOf()))
    }

    @Test
    fun theFirstLineThatFailsIsShown() {
        val answers = mapOf(
            "https://223.5.5.5/dns-query" to "",
            "http://223.6.6.6/dns-query" to CONTROL_DOH_ERROR_HTTPS_REQUIRED,
            "https://dns.alidns.com/dns-query" to CONTROL_DOH_ERROR_IP_REQUIRED,
        )
        val checked = mutableListOf<String>()
        val validate: (String) -> String = { line ->
            checked.add(line)
            answers.getValue(line)
        }

        assertEquals(
            CONTROL_DOH_ERROR_HTTPS_REQUIRED,
            controlDohValidationErrorId(
                listOf("https://223.5.5.5/dns-query", "http://223.6.6.6/dns-query", "https://dns.alidns.com/dns-query"),
                validate,
            ),
        )
        // the check stops at the first line that fails
        assertEquals(listOf("https://223.5.5.5/dns-query", "http://223.6.6.6/dns-query"), checked)

        assertEquals("", controlDohValidationErrorId(listOf("https://223.5.5.5/dns-query"), validate))
        assertEquals("", controlDohValidationErrorId(listOf(), validate))
    }

    @Test
    fun aSaveAnswerStandsOverTheLiveCheck() {
        // too many servers is only known when the sdk is asked to save them
        assertEquals(
            CONTROL_DOH_ERROR_TOO_MANY,
            controlDohFormErrorId(validationErrorId = "", saveErrorId = CONTROL_DOH_ERROR_TOO_MANY),
        )
        assertEquals(
            CONTROL_DOH_ERROR_IP_REQUIRED,
            controlDohFormErrorId(
                validationErrorId = CONTROL_DOH_ERROR_HTTPS_REQUIRED,
                saveErrorId = CONTROL_DOH_ERROR_IP_REQUIRED,
            ),
        )
        assertEquals(
            CONTROL_DOH_ERROR_HTTPS_REQUIRED,
            controlDohFormErrorId(validationErrorId = CONTROL_DOH_ERROR_HTTPS_REQUIRED, saveErrorId = ""),
        )
        assertEquals("", controlDohFormErrorId(validationErrorId = "", saveErrorId = ""))
    }

    @Test
    fun everySdkErrorIdHasItsOwnMessage() {
        // each id is the name of its string
        for (errorId in sdkErrorIds) {
            val named = R.string::class.java.getField(errorId).getInt(null)
            assertEquals(errorId, named, controlDohErrorResId(errorId))
        }
        // and no two ids share a message
        assertEquals(sdkErrorIds.size, sdkErrorIds.map { controlDohErrorResId(it) }.toSet().size)
    }

    @Test
    fun anUnknownErrorIdReadsAsAnInvalidUrl() {
        assertEquals(R.string.control_doh_error_url_invalid, controlDohErrorResId(""))
        assertEquals(R.string.control_doh_error_url_invalid, controlDohErrorResId("control_doh_error_later"))
        // connect's bare code is not an sdk id
        assertEquals(R.string.control_doh_error_url_invalid, controlDohErrorResId("ip_required"))
        assertEquals(R.string.control_doh_error_url_invalid, controlDohErrorResId("vless_error_link_invalid"))
    }

    @Test
    fun theErrorIdsAreTheSdksConstants() {
        // Sdk.ControlDohError* are compile-time constants, which kotlin inlines:
        // this reads no field of the gomobile class (whose initializer loads
        // gojni), and a renamed or removed constant breaks the build
        assertEquals(
            listOf(
                Sdk.ControlDohErrorUrlInvalid,
                Sdk.ControlDohErrorHttpsRequired,
                Sdk.ControlDohErrorIpRequired,
                Sdk.ControlDohErrorTooMany,
            ),
            sdkErrorIds,
        )
        assertEquals(
            sdkErrorIds,
            listOf(
                CONTROL_DOH_ERROR_URL_INVALID,
                CONTROL_DOH_ERROR_HTTPS_REQUIRED,
                CONTROL_DOH_ERROR_IP_REQUIRED,
                CONTROL_DOH_ERROR_TOO_MANY,
            ),
        )
        // and the sdk has no error id this build does not map. Listing the
        // class's fields does not initialize it.
        val sdkConstants = Sdk::class.java.declaredFields
            .map { it.name }
            .filter { it.startsWith("ControlDohError") }
        assertEquals(sdkErrorIds.size, sdkConstants.size)
    }
}
