package com.bringyour.network

import com.bringyour.network.analytics.WHITELIST_PROBE_LOG_TAG
import com.bringyour.network.analytics.WhitelistProbe
import com.bringyour.network.analytics.WhitelistProbeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostics support needs from "send feedback with logs" (P021, P052): each
 * goes to logcat unchanged and to the sdk log that feedback uploads, one line
 * per call under its tag, and a failing sdk write never fails the caller.
 */
class AppDiagnosticLogTest {
    private val logcat = mutableListOf<String>()
    private val sdkLog = mutableListOf<Pair<String, String>>()
    private val diagnosticLog = AppDiagnosticLog(
        logcat = { logcat.add(it) },
        sdkLog = { tag, line -> sdkLog.add(tag to line) },
    )

    @Test
    fun privateDnsLineGoesToLogcatUnchangedAndToTheSdkLogAsOneLine() {
        diagnosticLog.info(SERVICE_LOG_TAG, privateDnsModeLogLine(PrivateDnsMode.Strict("dns.example")))
        diagnosticLog.info(SERVICE_LOG_TAG, privateDnsModeLogLine(PrivateDnsMode.Opportunistic))
        diagnosticLog.info(SERVICE_LOG_TAG, privateDnsModeLogLine(PrivateDnsMode.Off))

        assertEquals(
            listOf(
                "[service]private dns mode=strict(dns.example)",
                "[service]private dns mode=opportunistic",
                "[service]private dns mode=off",
            ),
            logcat,
        )
        // the sdk writes each as "[app][service] private dns mode=..."
        assertEquals(
            listOf(
                "service" to "private dns mode=strict(dns.example)",
                "service" to "private dns mode=opportunistic",
                "service" to "private dns mode=off",
            ),
            sdkLog,
        )
    }

    @Test
    fun whitelistProbeBlockGoesToTheSdkLogLineByLine() {
        val probe = WhitelistProbe(
            nowMillis = { 1_000_000L },
            checkApiReachable = {
                WhitelistProbeStep("api-reachable", false, "SocketTimeoutException after 5001ms")
            },
            log = { diagnosticLog.info(WHITELIST_PROBE_LOG_TAG, it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))

        // logcat keeps the one block as before
        assertEquals(1, logcat.size)
        val blockLines = logcat[0].lines()
        assertEquals("[whitelist-probe] cellular connect failure in RU", blockLines[0])

        // the sdk log gets every line of it, each under the probe's tag, with the
        // step indentation kept
        assertEquals(blockLines.size, sdkLog.size)
        assertTrue(sdkLog.all { (tag, _) -> tag == "whitelist-probe" })
        assertEquals("cellular connect failure in RU", sdkLog[0].second)
        assertEquals("  [fail] api-reachable: SocketTimeoutException after 5001ms", sdkLog[1].second)
        assertEquals(blockLines.drop(1), sdkLog.drop(1).map { it.second })
    }

    @Test
    fun linesSplitDropBlanksAndLoseOnlyTheirOwnLeadingTag() {
        assertEquals(
            listOf("header", "  [ok] step: detail", "x [whitelist-probe]y", "[other]z"),
            appDiagnosticLogLines(
                "whitelist-probe",
                "[whitelist-probe] header\r\n  [ok] step: detail\n\n   \nx [whitelist-probe]y\n[other]z\n",
            ),
        )
        assertEquals(
            listOf("private dns mode=off"),
            appDiagnosticLogLines("service", "[service]private dns mode=off"),
        )
        assertEquals(emptyList<String>(), appDiagnosticLogLines("service", ""))
    }

    @Test
    fun aFailingSdkWriteDoesNotFailTheCaller() {
        val failing = AppDiagnosticLog(
            logcat = { logcat.add(it) },
            sdkLog = { _, _ -> throw UnsatisfiedLinkError("gojni") },
        )

        failing.info(SERVICE_LOG_TAG, privateDnsModeLogLine(PrivateDnsMode.Off))

        assertEquals(listOf("[service]private dns mode=off"), logcat)
    }
}
