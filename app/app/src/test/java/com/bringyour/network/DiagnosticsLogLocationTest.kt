package com.bringyour.network

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsLogLocationTest {

    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = Files.createTempDirectory("filesDir").toFile()
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    private fun processLogDir(): File = File(logRootDir(filesDir), APP_LOG_PROCESS_NAME)

    @Test
    fun theLogRootIsASubdirectoryOfFilesDirPerProcess() {
        assertEquals(File(filesDir, "logs"), logRootDir(filesDir))
        // the directory name is the label for which process wrote a file, and
        // it is the same one iOS uses for its app process
        assertEquals("app", APP_LOG_PROCESS_NAME)
    }

    @Test
    fun glogFileNamesAreRecognisedByTheirSeverity() {
        assertTrue(isGlogFileName("urnetwork.host.user.log.INFO.20260101-000000.1234"))
        assertTrue(isGlogFileName("urnetwork.host.user.log.WARNING.20260101-000000.1234"))
        assertTrue(isGlogFileName("urnetwork.host.user.log.ERROR.20260101-000000.1234"))
        assertTrue(isGlogFileName("urnetwork.host.user.log.FATAL.20260101-000000.1234"))
        assertFalse(isGlogFileName("shared_prefs.xml"))
        assertFalse(isGlogFileName("urnetwork.log"))
        assertFalse(isGlogFileName("localstate.db"))
    }

    @Test
    fun preUpgradeLogsAreMovedUnderTheProcessRootRatherThanStranded() {
        // the old build wrote glog files straight into filesDir; nothing prunes
        // or exports that directory once the root moves, so those files were
        // both dead storage and unreachable evidence
        val legacy = File(filesDir, "urnetwork.host.user.log.INFO.20260101-000000.1234")
        legacy.writeText("pre-upgrade line")
        val other = File(filesDir, "localstate.db")
        other.writeText("not a log")

        assertEquals(1, migrateLegacyLogFiles(filesDir, processLogDir()))

        assertFalse("the legacy copy must not be left behind", legacy.exists())
        val moved = File(processLogDir(), legacy.name)
        assertTrue("the log must still be exportable", moved.exists())
        assertEquals("pre-upgrade line", moved.readText())
        assertTrue("only glog files are touched", other.exists())
    }

    @Test
    fun migratingIsIdempotentAndNeverOverwritesALiveLog() {
        val name = "urnetwork.host.user.log.INFO.20260101-000000.1234"
        val live = File(processLogDir(), name)
        assertTrue(processLogDir().mkdirs())
        live.writeText("the file already under the new root")
        val legacy = File(filesDir, name)
        legacy.writeText("the stale duplicate")

        assertEquals(1, migrateLegacyLogFiles(filesDir, processLogDir()))

        assertFalse(legacy.exists())
        assertEquals("the file already under the new root", live.readText())

        // a second launch has nothing left to do
        assertEquals(0, migrateLegacyLogFiles(filesDir, processLogDir()))
    }

    @Test
    fun anInstallWithNoLegacyLogsIsUntouched() {
        assertEquals(0, migrateLegacyLogFiles(filesDir, processLogDir()))
        // no destination directory is created for nothing
        assertFalse(processLogDir().exists())
    }
}
