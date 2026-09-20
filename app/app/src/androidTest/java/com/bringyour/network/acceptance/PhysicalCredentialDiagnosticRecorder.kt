package com.bringyour.network.acceptance

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** Metadata-only sink shared by the early runner and the existing test hooks. */
internal object PhysicalCredentialDiagnosticRecorder {
    @Synchronized
    fun checkpoint(context: Context, stage: PhysicalCredentialStage) {
        val record = physicalCredentialCheckpointJson(stage, SystemClock.elapsedRealtime(), System.currentTimeMillis()) {
            // Unlike getFilesDir(), this path lookup does not create filesDir
            // if the actor under investigation has already removed it.
            val credentials = File(context.dataDir, "files/acceptance/credentials")
            val attributes = try {
                Os.lstat(credentials.absolutePath)
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) {
                    return@physicalCredentialCheckpointJson PhysicalCredentialMetadata(PhysicalCredentialFileType.MISSING)
                }
                throw error
            }
            val type = when {
                OsConstants.S_ISREG(attributes.st_mode) -> PhysicalCredentialFileType.REGULAR
                OsConstants.S_ISDIR(attributes.st_mode) -> PhysicalCredentialFileType.DIRECTORY
                OsConstants.S_ISLNK(attributes.st_mode) -> PhysicalCredentialFileType.SYMLINK
                else -> PhysicalCredentialFileType.OTHER
            }
            PhysicalCredentialMetadata(type, attributes.st_uid == Process.myUid(), attributes.st_mode and 0xfff, attributes.st_size)
        }
        // Only fixed schema and primitive metadata; never raw errors or content.
        Log.i("PhysicalCredential", record)
        runCatching {
            // Cache is outside filesDir and records survive its removal.
            val destination = File(context.cacheDir, "acceptance/physical-credential-checkpoints.ndjson")
            val directory = checkNotNull(destination.parentFile)
            check(directory.mkdirs() || directory.isDirectory)
            val parent = Os.lstat(directory.absolutePath)
            check(OsConstants.S_ISDIR(parent.st_mode) && parent.st_uid == Process.myUid())
            Os.chmod(directory.absolutePath, 0x1c0) // 0700
            val descriptor = Os.open(
                destination.absolutePath,
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_APPEND or
                    OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK,
                0x180, // 0600
            )
            FileOutputStream(descriptor).use { output ->
                val bytes = "$record\n".toByteArray(Charsets.UTF_8)
                val prior = Os.fstat(descriptor)
                check(OsConstants.S_ISREG(prior.st_mode) && prior.st_uid == Process.myUid())
                check(physicalCredentialCheckpointAppendAllowed(prior.st_size, bytes.size))
                Os.fchmod(descriptor, 0x180)
                output.write(bytes)
            }
        }.onFailure {
            Log.w("PhysicalCredential", "credential-checkpoint-persistence-unavailable")
        }
    }
}
