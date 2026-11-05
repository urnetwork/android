package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The process exit diagnostics (support 8190, "the app closes by itself"): each
 * new exit record becomes one line in the uploaded log that says why the process
 * ended, records an earlier start already logged are not repeated, and a long
 * system description stays one bounded line.
 */
class ProcessExitDiagnosticsTest {
    private fun record(
        timestampMillis: Long,
        reason: Int,
        importance: Int = 100,
        description: String? = null,
    ) = ProcessExitRecord(
        timestampMillis = timestampMillis,
        processName = "com.bringyour.network",
        reason = reason,
        status = 0,
        importance = importance,
        pssKb = 1234,
        rssKb = 5678,
        description = description,
    )

    @Test
    fun newRecordsAreLoggedOldestFirstWithTheirReasonAndTheWatermarkMovesToTheNewest() {
        val (lines, watermark) = processExitLogLines(
            listOf(record(3_000, 3, importance = 400), record(1_000, 4), record(2_000, 6)),
            loggedThroughMillis = 0,
        )

        assertEquals(3, lines.size)
        assertTrue(lines[0].contains(" reason=crash(4) "))
        assertTrue(lines[1].contains(" reason=anr(6) "))
        assertTrue(lines[2].contains(" reason=low_memory(3) "))
        assertTrue(lines[2].contains(" importance=cached "))
        assertEquals(3_000L, watermark)
    }

    @Test
    fun recordsAnEarlierStartLoggedAreNotRepeated() {
        val records = listOf(record(1_000, 4), record(2_000, 10))

        val (lines, watermark) = processExitLogLines(records, loggedThroughMillis = 1_000)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains(" reason=user_requested(10) "))
        assertEquals(2_000L, watermark)

        val (again, sameWatermark) = processExitLogLines(records, loggedThroughMillis = watermark)
        assertEquals(emptyList<String>(), again)
        assertEquals(2_000L, sameWatermark)
    }

    @Test
    fun aLineCarriesTheTimeProcessAndMemoryAndABoundedOneLineDescription() {
        val line = processExitLogLine(
            record(0, 5, description = "signal 11\n  backtrace:\n" + "x".repeat(500)),
        )

        assertTrue(
            line.startsWith("[$PROCESS_EXIT_LOG_TAG]exit at 1970-01-01T00:00:00Z process=com.bringyour.network "),
        )
        assertTrue(line.contains(" reason=crash_native(5) "))
        assertTrue(line.contains(" pss_kb=1234 rss_kb=5678 "))
        assertFalse(line.contains("\n"))
        val description = line.substringAfter("description=\"").removeSuffix("\"")
        assertEquals(PROCESS_EXIT_DESCRIPTION_MAX_LENGTH, description.length)
        assertTrue(description.startsWith("signal 11 backtrace: "))
    }

    @Test
    fun aRecordWithoutADescriptionEndsAtItsMemory() {
        val line = processExitLogLine(record(0, 2, description = "  \n "))

        assertTrue(line.endsWith(" rss_kb=5678"))
    }

    @Test
    fun unknownCodesStillReadInTheLog() {
        assertEquals("reason_99", processExitReasonName(99))
        assertEquals("importance_42", processImportanceName(42))
    }

    @Test
    fun theSdkLogGetsEachLineUnderItsTagWithoutTheTagPrefix() {
        val logcat = mutableListOf<String>()
        val sdkLog = mutableListOf<Pair<String, String>>()
        val diagnosticLog = AppDiagnosticLog(
            logcat = { logcat.add(it) },
            sdkLog = { tag, line -> sdkLog.add(tag to line) },
        )
        val line = processExitLogLine(record(0, 4))

        diagnosticLog.info(PROCESS_EXIT_LOG_TAG, line)

        assertEquals(listOf(line), logcat)
        assertEquals(listOf(PROCESS_EXIT_LOG_TAG to line.removePrefix("[$PROCESS_EXIT_LOG_TAG]")), sdkLog)
    }
}
