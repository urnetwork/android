package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JavaCrashRecorderTest {
    @get:Rule
    val folder = TemporaryFolder()

    // the record the sdk reads (sdk java_crash.go), then the crash goes on to
    // the handler before it, which on Android reports it and ends the process
    @Test
    fun aCrashLeavesItsRecordAndGoesOnToThePreviousHandler() {
        val dir = File(folder.root, "logs/app")
        var handedOn: Throwable? = null
        val previous = Thread.UncaughtExceptionHandler { _, throwable -> handedOn = throwable }
        val crash = IllegalStateException("test crash")
        val thread = Thread("main-test")

        JavaCrashRecorder.handler("2026.11.17-1101053110", { dir }, previous) { 1700000000000L }
            .uncaughtException(thread, crash)

        assertSame(crash, handedOn)
        val record = File(dir, JAVA_CRASH_PENDING_FILE_NAME).readText()
        assertTrue(
            record,
            record.startsWith(
                "urnetwork-java-crash 1\n" +
                    "time_millis: 1700000000000\n" +
                    "app_version: 2026.11.17-1101053110\n" +
                    "thread: main-test\n" +
                    "\n" +
                    "java.lang.IllegalStateException: test crash\n",
            ),
        )
        assertTrue(record, record.contains("\tat com.bringyour.network.JavaCrashRecorderTest."))
        assertFalse(File(dir, "$JAVA_CRASH_PENDING_FILE_NAME.staging").exists())
    }

    // a newer crash replaces the record the sdk has not taken yet
    @Test
    fun aNewerCrashReplacesThePendingRecord() {
        val dir = folder.newFolder("app")
        val handler = JavaCrashRecorder.handler("v", { dir }, { _, _ -> }) { 1L }
        handler.uncaughtException(Thread("a"), RuntimeException("first"))
        handler.uncaughtException(Thread("b"), RuntimeException("second"))
        val record = File(dir, JAVA_CRASH_PENDING_FILE_NAME).readText()
        assertTrue(record, record.contains("RuntimeException: second"))
        assertFalse(record, record.contains("RuntimeException: first"))
    }

    // a directory that can't be written costs the record, never the crash
    @Test
    fun anUnwritableDirectoryStillHandsTheCrashOn() {
        val notADirectory = folder.newFile("not-a-directory")
        var handedOn = false
        JavaCrashRecorder.handler("v", { File(notADirectory, "app") }, { _, _ -> handedOn = true }) { 1L }
            .uncaughtException(Thread("t"), RuntimeException("x"))
        assertTrue(handedOn)
    }

    // a deep recursion's stack trace is cut to the bound
    @Test
    fun aDeepStackTraceIsCut() {
        val crash = RuntimeException("deep")
        crash.stackTrace = Array(20000) { StackTraceElement("com.example.Deep", "recurse", "Deep.kt", it) }
        val record = JavaCrashRecorder.record(1L, "v", "t", crash)
        val stackTrace = record.substringAfter("\n\n")
        assertEquals(JAVA_CRASH_MAX_STACK_TRACE_CHARS, stackTrace.length)
        assertTrue(stackTrace.startsWith("java.lang.RuntimeException: deep\n"))
    }
}
