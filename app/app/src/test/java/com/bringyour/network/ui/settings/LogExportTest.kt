package com.bringyour.network.ui.settings

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings > Export Logs (LogExport.kt): where an export goes, and what it is named. */
class LogExportTest {

    @Test
    fun exportFileNameIsStampedInUtcAndSortsInTime() {
        // pinned exactly: a name stamped in the device's own zone sorts wrongly
        // against one from another zone
        assertEquals("urnetwork-logs-20260101-000000.zip", logExportFileName(1767225600000L))
        val earlier = logExportFileName(1767225500000L)
        assertTrue("$earlier should sort before the later export", earlier < logExportFileName(1767225600000L))
    }

    @Test
    fun exportsGoUnderTheFileProviderSharePath() {
        // res/xml/file_paths.xml shares cacheDir/share and nothing else of the cache
        assertEquals(File("/cache/share/logs"), logExportDir(File("/cache")))
    }

    @Test
    fun preparingTheExportFolderEmptiesOnlyIt() {
        val cacheDir = Files.createTempDirectory("log-export-test").toFile()
        try {
            val dir = logExportDir(cacheDir)
            dir.mkdirs()
            File(dir, logExportFileName(1767225600000L)).writeText("an earlier export")
            // the sdk writes beside the export and renames: what an export cut
            // short leaves
            File(dir, ".logs-1.zip").writeText("a partial zip")
            // another feature's payload in cacheDir/share
            val sharePayload = File(File(cacheDir, "share"), "identicon.png")
            sharePayload.writeText("png")

            prepareLogExportDir(dir)

            assertTrue(dir.isDirectory)
            assertEquals(0, dir.listFiles()?.size)
            assertTrue("a payload outside the export folder was removed", sharePayload.isFile)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun preparingTheExportFolderCreatesIt() {
        val cacheDir = Files.createTempDirectory("log-export-test").toFile()
        try {
            val dir = logExportDir(cacheDir)

            prepareLogExportDir(dir)

            assertTrue(dir.isDirectory)
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
