package com.bringyour.network

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/*
 * The app's Java and Kotlin crashes, left where "send feedback with logs"
 * finds them.
 *
 * Android sends an uncaught exception's stack only to logcat, and before
 * Android 11 it keeps no exit reason (ProcessExitDiagnostics), so "the app
 * closes by itself" (support 8308, an Android 8.0 phone) left nothing in an
 * uploaded log. The default uncaught exception handler installed here writes
 * the crash as it happens to java-crash-pending.txt in the app's log
 * directory: the time, the app version, the thread's name and the stack
 * trace, and nothing of the account, the network or the settings. It writes a
 * staging file and renames it into place, and it cuts the stack trace to
 * JAVA_CRASH_MAX_STACK_TRACE_CHARS. Then it hands the exception to the handler
 * that was installed before it, so the system, logcat and Google Play's
 * Android vitals still see the crash.
 *
 * It runs no sdk code: a crash can come before gomobile is loaded (the VPN
 * service's cold start defers loading it, MainApplication.onCreate), and in
 * direct boot the log directory can't be written at all, which costs only this
 * record. The sdk takes the pending record into java-crash.txt the next time
 * the app points its logs at the directory (Sdk.setLogDirForProcess): it masks
 * addresses, ids and email addresses, keeps the newest records within a bound,
 * and the feedback upload carries that file first (sdk java_crash.go, whose
 * reader this record's format must match).
 */

/** The record's file in the app's log directory, which the sdk takes from there. */
const val JAVA_CRASH_PENDING_FILE_NAME = "java-crash-pending.txt"

/** The first line of a record, naming its format (sdk javaCrashPendingHeader). */
const val JAVA_CRASH_PENDING_HEADER = "urnetwork-java-crash 1"

/** The most of a stack trace a record keeps: a deep recursion's runs to megabytes. */
const val JAVA_CRASH_MAX_STACK_TRACE_CHARS = 64 * 1024

object JavaCrashRecorder {
    private val installed = AtomicBoolean(false)
    private val writeLock = Any()

    /**
     * Installs the recorder as this process's default uncaught exception
     * handler, in front of the one installed now. [appVersion] is what the
     * record names. [logDir] is read only at a crash, so the directory need
     * not exist or be writable until then. Installs once per process.
     */
    fun install(appVersion: String, logDir: () -> File) {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(handler(appVersion, logDir, previous))
    }

    /**
     * The handler [install] installs, which a test can call without replacing
     * the process's own.
     */
    internal fun handler(
        appVersion: String,
        logDir: () -> File,
        previous: Thread.UncaughtExceptionHandler?,
        nowMillis: () -> Long = System::currentTimeMillis,
    ): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            writePending(logDir(), record(nowMillis(), appVersion, thread.name, throwable))
        } catch (_: Throwable) {
            // the record is best effort; the crash itself must still go on
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            // Android always installs one; without it, end the process as
            // the crash would have
            exitProcess(10)
        }
    }

    /** One record: the header, the fields the sdk reads, a blank line, then the stack trace. */
    internal fun record(timeMillis: Long, appVersion: String, threadName: String, throwable: Throwable): String {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            append(JAVA_CRASH_PENDING_HEADER).append('\n')
            append("time_millis: ").append(timeMillis).append('\n')
            append("app_version: ").append(oneLine(appVersion)).append('\n')
            append("thread: ").append(oneLine(threadName)).append('\n')
            append('\n')
            append(stackTrace.take(JAVA_CRASH_MAX_STACK_TRACE_CHARS))
        }
    }

    /** Writes [record] whole to a staging file, then renames it over the pending record. */
    internal fun writePending(dir: File, record: String) {
        synchronized(writeLock) {
            if (!dir.isDirectory && !dir.mkdirs()) {
                return
            }
            val staging = File(dir, "$JAVA_CRASH_PENDING_FILE_NAME.staging")
            FileOutputStream(staging).use { output ->
                output.write(record.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!staging.renameTo(File(dir, JAVA_CRASH_PENDING_FILE_NAME))) {
                staging.delete()
            }
        }
    }

    private fun oneLine(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
}
