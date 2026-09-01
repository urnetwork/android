package com.bringyour.network

import java.io.File

/**
 * Where each process writes its logs.
 *
 * The sdk prunes per directory -- `clearOldLogs` keeps the 4 newest files in
 * whichever directory glog is pointed at -- so processes that share one
 * directory delete each other's history. Android is single-process (no
 * `android:process` in the manifest), so "app" is the only subdirectory that
 * ever appears here; the layout still matches iOS, which really does have two
 * writers (the app and the network extension), so both platforms make the
 * same sdk call and lay their logs out the same way.
 */
const val APP_LOG_PROCESS_NAME = "app"

/** The shared log root: one subdirectory per writing process. */
fun logRootDir(filesDir: File): File = File(filesDir, "logs")

/** glog's severity tags, as they appear in a file name after ".log.". */
private val LOG_FILE_SEVERITIES = listOf("INFO", "WARNING", "ERROR", "FATAL")

/**
 * True for a name glog wrote, i.e.
 * `<program>.<host>.<user>.log.<SEVERITY>.<time>.<pid>`.
 */
fun isGlogFileName(name: String): Boolean =
    LOG_FILE_SEVERITIES.any { name.contains(".log.$it") }

/**
 * Moves pre-upgrade log files out of `filesDir` into the per-process log
 * directory, returning how many were relocated.
 *
 * Builds before the per-process root wrote glog files directly into
 * `filesDir`. Retention only ever prunes the directory glog is currently
 * pointed at (`clearOldLogs`, sdk/sdk.go), so once the root moves those files
 * are never touched again: up to four files of up to 16MB each stranded in
 * `filesDir` forever on every upgrading install.
 * They are also unreachable evidence. Every reader of the logs resolves them
 * through `Sdk.getLogDir()` -- the feedback screen's share and export buttons
 * do today -- and that now names `<filesDir>/logs/<process>`, so a user who
 * upgrades and then reports an incident that predates the upgrade attaches
 * none of the logs that recorded it.
 *
 * Moving rather than deleting keeps those logs reachable, and doing it BEFORE
 * `Sdk.setLogDirForProcess` hands the merged set to that same retention pass
 * -- which keeps the four newest and drops the rest -- so this bounds the
 * storage rather than doubling it.
 */
fun migrateLegacyLogFiles(filesDir: File, processLogDir: File): Int {
    val legacy = filesDir.listFiles()?.filter { it.isFile && isGlogFileName(it.name) } ?: return 0
    if (legacy.isEmpty()) {
        return 0
    }
    if (!processLogDir.isDirectory && !processLogDir.mkdirs()) {
        return 0
    }

    var moved = 0
    for (file in legacy) {
        val dest = File(processLogDir, file.name)
        if (dest.exists()) {
            // glog names embed host, pid and start time, so a collision means
            // an earlier launch already migrated this file: the copy still
            // sitting in filesDir is the redundant one.
            if (file.delete()) {
                moved += 1
            }
            continue
        }
        // A rename that fails leaves the file exactly where it was, which is
        // no worse than never having run. Never delete a log we could not
        // move -- the whole point is that it stays reachable.
        if (file.renameTo(dest)) {
            moved += 1
        }
    }
    return moved
}
