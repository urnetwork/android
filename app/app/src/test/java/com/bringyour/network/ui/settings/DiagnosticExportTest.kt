package com.bringyour.network.ui.settings

import java.io.File
import java.io.StringReader
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {

    @Test
    fun bundleFileNameIsSortableAndCarriesTheMode() {
        val raw = diagnosticBundleFileName(millis = 1767225600000L, redacted = false)
        val redacted = diagnosticBundleFileName(millis = 1767225600000L, redacted = true)

        // pinned exactly, so the UTC formatting this implementation deliberately
        // does is load-bearing: a bundle stamped in the exporting device's local
        // zone sorts wrongly against one from another zone, and two bundles from
        // the same second are indistinguishable in a support thread
        assertEquals("urnetwork-diagnostics-20260101-000000.zip", raw)
        assertEquals("urnetwork-diagnostics-20260101-000000-redacted.zip", redacted)
        assertTrue("raw name should not claim redaction, was $raw", !raw.contains("redacted"))
        // lexical sort must match chronological sort
        val earlier = diagnosticBundleFileName(millis = 1767225500000L, redacted = false)
        assertTrue("$earlier should sort before $raw", earlier < raw)
    }

    @Test
    fun exportSelectionIsBlockedWithNothingChecked() {
        // An empty SelectedNames means "no filter" to the sdk, i.e. every log
        // file, raw -- the opposite of what "Export selected" promises. This is
        // the predicate behind both the disabled row and the handler guard.
        assertFalse(canExportSelection(emptySet()))
        assertFalse(canExportSelection(emptyList()))
        assertTrue(canExportSelection(setOf("urnetwork.log.INFO.20260101-000000.1")))
    }

    @Test
    fun logcatCommandIsBoundedAndReadsOnlyThisAppsOwnBuffer() {
        val command = logcatDumpCommand()

        assertEquals("logcat", command[0])
        // -d dumps and exits; without it logcat streams forever
        assertTrue("must dump and exit, was $command", command.contains("-d"))
        // never widen the scope: -b all would pull other apps' buffers into a
        // bundle the user is about to email to support
        assertFalse("must not widen the buffer scope, was $command", command.contains("-b"))
        // bounded: the dump is materialised as a String, copied into a Go
        // string and read again by deflate
        val cap = command.indexOf("-t")
        assertTrue("must bound the dump with -t, was $command", 0 < cap)
        assertEquals(LOGCAT_MAX_LINES.toString(), command[cap + 1])
    }

    @Test
    fun readingTheDumpStopsAtTheCap() {
        assertEquals("abcde", readAtMost(StringReader("abcdefghij"), 5))
        assertEquals("abc", readAtMost(StringReader("abc"), 512))
        assertEquals("", readAtMost(StringReader("abc"), 0))
    }

    @Test
    fun inventoryRowLabelNamesTheSourceSeverityAndSizeAndModifiedTime() {
        val label = logFileRowLabel(
            source = "extension",
            severity = "ERROR",
            byteCount = 2048L,
            modifiedMillis = 1767225600000L,
        )
        assertTrue("should name the source, was $label", label.contains("extension"))
        assertTrue("should name the severity, was $label", label.contains("ERROR"))
        assertTrue("should show the size, was $label", label.contains("2.00 KiB"))
        // the picker's rows carry modified time: which file covers the
        // incident is a question about time
        assertTrue("should show when it was written, was $label", label.contains("2026-01-01 00:00Z"))
    }

    @Test
    fun rowLabelOmitsAnUnknownModifiedTime() {
        val label = logFileRowLabel(source = "app", severity = "INFO", byteCount = 2048L, modifiedMillis = 0L)
        assertEquals("app · INFO · 2.00 KiB", label)
    }

    @Test
    fun sizesNeverRenderAFileAsEmptyWhenItIsNot() {
        // `byteCount / 1024` renders a freshly rotated log as "0 KiB", which in
        // a picker reads as "nothing in this file" rather than as a rounding.
        // The app's own formatter is what the rest of the ui already uses.
        assertEquals("app · INFO · 400 B", logFileRowLabel("app", "INFO", 400L))
        assertEquals("app · INFO · 1 B", logFileRowLabel("app", "INFO", 1L))
        assertEquals("app · INFO · 16.0 MiB", logFileRowLabel("app", "INFO", 16L * 1024 * 1024))
    }

    @Test
    fun theTotalSizeIsAvailableBeforeExporting() {
        assertEquals("No log files on disk", inventoryLabel(fileCount = 0, byteCount = 0L))
        assertEquals("1 log file on disk · 2.00 KiB", inventoryLabel(fileCount = 1, byteCount = 2048L))
        assertEquals("3 log files on disk · 48.0 MiB", inventoryLabel(fileCount = 3, byteCount = 48L * 1024 * 1024))
        assertEquals("Nothing selected", selectionLabel(fileCount = 0, byteCount = 0L))
        assertEquals("Selected 2 files · 4.00 KiB", selectionLabel(fileCount = 2, byteCount = 4096L))
    }

    @Test
    fun onlyThisAppsOwnBundlesArePruned() {
        assertTrue(isDiagnosticBundleName("urnetwork-diagnostics-20260101-000000.zip"))
        assertTrue(isDiagnosticBundleName("urnetwork-diagnostics-20260101-000000-redacted.zip"))
        assertTrue(isDiagnosticBundleName(diagnosticBundleFileName(1767225600000L, redacted = true)))
        // anything else in cacheDir/share belongs to another feature
        assertFalse(isDiagnosticBundleName("urnetwork-diagnostics-20260101-000000.zip.part"))
        assertFalse(isDiagnosticBundleName("support-attachment.zip"))
        assertFalse(isDiagnosticBundleName("urnetwork.log.INFO.20260101-000000.1"))
    }

    @Test
    fun anUnreadableLogSourceIsReportedRatherThanSilentlyOmitted() {
        // the sdk swallows directory-read failures entirely, so this predicate
        // is the only place android can notice one -- such a source has to be
        // recorded as missing
        assertNotNull(logSourceUnavailableReason("", "app"))

        val root = Files.createTempDirectory("logroot").toFile()
        try {
            assertNotNull("an absent source directory is missing", logSourceUnavailableReason(root.absolutePath, "app"))

            val notADirectory = File(root, "app")
            assertTrue(notADirectory.createNewFile())
            assertNotNull(
                "a source that is not a directory is missing",
                logSourceUnavailableReason(root.absolutePath, "app"),
            )
            assertTrue(notADirectory.delete())

            assertTrue(File(root, "app").mkdirs())
            assertNull("a readable source directory is not missing", logSourceUnavailableReason(root.absolutePath, "app"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theSummaryCarriesTheCountAsANumberSoItCanBePluralised() {
        // "Exported 1 log files" -- a count formatted into a fixed English
        // phrase in the viewmodel, where no <plurals> can be resolved, and a
        // count already inside a string cannot be pluralised afterwards. So
        // the summary stays numeric all the way to the screen, which is what
        // R.plurals.dev_export_summary selects on. A file count of exactly 1
        // is the common case: it is what a selective export of one log
        // produces.
        val one = DiagnosticExportSummary(fileCount = 1, byteCount = 2048L, missingSources = listOf())
        assertEquals(1, one.fileCount)
        assertEquals(2048L, one.byteCount)
        assertNull("a successful export has no failure reason", one.failure)
        assertTrue(one.missingSources.isEmpty())
    }

    @Test
    fun anUnreadableSourceSurvivesIntoTheSummaryRatherThanBeingFlattenedIntoIt() {
        // each missing source is rendered as its own line by the screen, so it
        // stays a list here -- the reason has to reach the user, and a
        // pre-joined string cannot be re-styled or re-localised
        val summary = DiagnosticExportSummary(
            fileCount = 3,
            byteCount = 4096L,
            missingSources = listOf("app: no log directory on disk", "logcat.txt: logcat unavailable"),
        )
        assertEquals(2, summary.missingSources.size)
        assertEquals("app: no log directory on disk", summary.missingSources[0])
    }

    @Test
    fun anExportThatWroteNothingReportsWhyAndClaimsNoFiles() {
        val failed = DiagnosticExportSummary.failed("permission denied")
        assertEquals("permission denied", failed.failure)
        // never "Exported 0 log files" alongside a failure: the count is not a
        // result when there is no bundle
        assertEquals(0, failed.fileCount)
        assertEquals(0L, failed.byteCount)
        assertTrue(failed.missingSources.isEmpty())
        // an sdk exception with no message must still say something
        assertNotNull(DiagnosticExportSummary.failed(null).failure)
    }

    @Test
    fun theMissingSourceReasonCarriesNoFilesystemPath() {
        // the reason is copied verbatim into README.txt, the one bundle entry
        // the sdk writes WITHOUT the redaction transform
        val root = Files.createTempDirectory("logroot").toFile()
        try {
            val reason = logSourceUnavailableReason(root.absolutePath, "app")
            assertNotNull(reason)
            assertFalse("reason must not leak a path, was $reason", reason!!.contains(root.absolutePath))
            assertFalse("reason must not leak a path, was $reason", reason.contains("/"))
        } finally {
            root.deleteRecursively()
        }
    }
}
