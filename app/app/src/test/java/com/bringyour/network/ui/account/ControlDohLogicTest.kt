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

    // a preset as Sdk.regionalControlDohUrls returns one: four servers, v4 first
    private val presetUrls = listOf(
        "https://192.0.2.1/dns-query",
        "https://198.51.100.1/dns-query",
        "https://203.0.113.1/dns-query",
        "https://192.0.2.2/dns-query",
    )

    @Test
    fun aLineEndsAtANewlineOrACarriageReturn() {
        // windows (\r\n), old mac (\r) and unix (\n) line endings read the same
        assertEquals(
            listOf("https://192.0.2.1/dns-query", "https://198.51.100.1/dns-query", "https://203.0.113.1/dns-query"),
            controlDohLinesFromText(
                "https://192.0.2.1/dns-query\r\nhttps://198.51.100.1/dns-query\rhttps://203.0.113.1/dns-query\n"
            ),
        )
    }

    @Test
    fun blankLinesAndPaddingAreNotServers() {
        assertEquals(
            listOf("https://192.0.2.1/dns-query", "https://[2001:db8::1]/dns-query"),
            controlDohLinesFromText("  https://192.0.2.1/dns-query \n\n\t\r\n   \nhttps://[2001:db8::1]/dns-query\t"),
        )
        assertEquals(listOf<String>(), controlDohLinesFromText(""))
        assertEquals(listOf<String>(), controlDohLinesFromText(" \r\n\t\n"))
    }

    @Test
    fun aCommaDoesNotSeparateServers() {
        assertEquals(
            listOf("https://192.0.2.1/dns-query,https://198.51.100.1/dns-query"),
            controlDohLinesFromText("https://192.0.2.1/dns-query,https://198.51.100.1/dns-query"),
        )
        assertEquals(
            listOf("https://192.0.2.1/dns-query, https://198.51.100.1/dns-query"),
            controlDohLinesFromText("https://192.0.2.1/dns-query, https://198.51.100.1/dns-query"),
        )
    }

    @Test
    fun theLinesKeepTheirOrderAndRepeatsForTheSdk() {
        // the order typed is the order tried, and the sdk drops the repeat
        assertEquals(
            listOf("https://203.0.113.1/dns-query", "https://192.0.2.1/dns-query", "https://203.0.113.1/dns-query"),
            controlDohLinesFromText(
                "https://203.0.113.1/dns-query\nhttps://192.0.2.1/dns-query\nhttps://203.0.113.1/dns-query"
            ),
        )
    }

    @Test
    fun thePresetFillsTheFieldOneServerPerLine() {
        val text = controlDohText(presetUrls)

        assertEquals(
            "https://192.0.2.1/dns-query\nhttps://198.51.100.1/dns-query\nhttps://203.0.113.1/dns-query\nhttps://192.0.2.2/dns-query",
            text,
        )
        // and reads back as the same list
        assertEquals(presetUrls, controlDohLinesFromText(text))
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
            "https://192.0.2.1/dns-query" to "",
            "http://198.51.100.1/dns-query" to CONTROL_DOH_ERROR_HTTPS_REQUIRED,
            "https://dns.example/dns-query" to CONTROL_DOH_ERROR_IP_REQUIRED,
        )
        val checked = mutableListOf<String>()
        val validate: (String) -> String = { line ->
            checked.add(line)
            answers.getValue(line)
        }

        assertEquals(
            CONTROL_DOH_ERROR_HTTPS_REQUIRED,
            controlDohValidationErrorId(
                listOf("https://192.0.2.1/dns-query", "http://198.51.100.1/dns-query", "https://dns.example/dns-query"),
                validate,
            ),
        )
        // the check stops at the first line that fails
        assertEquals(listOf("https://192.0.2.1/dns-query", "http://198.51.100.1/dns-query"), checked)

        assertEquals("", controlDohValidationErrorId(listOf("https://192.0.2.1/dns-query"), validate))
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
