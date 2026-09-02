package com.bringyour.network

import android.content.Context
import android.os.UserManager
import androidx.core.content.edit

internal const val DIRECT_BOOT_STATE_PREFERENCES = "direct_boot_state"
internal const val DIRECT_BOOT_CREDENTIAL_RESTORE_PENDING = "credential_restore_pending"

/**
 * The only app-owned state available before first unlock.
 *
 * This deliberately contains no account, JWT, location, tunnel configuration,
 * or user preference. Those remain in credential encrypted SDK storage. The
 * bit only records that a process which ran during Direct Boot must complete
 * its ordinary restore once ACTION_USER_UNLOCKED arrives.
 */
internal class DirectBootState(context: Context) {
    private val preferences = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(DIRECT_BOOT_STATE_PREFERENCES, Context.MODE_PRIVATE)

    val credentialRestorePending: Boolean
        get() = preferences.getBoolean(DIRECT_BOOT_CREDENTIAL_RESTORE_PENDING, false)

    fun markCredentialRestorePending() {
        if (credentialRestorePending) return
        // This handoff must survive the process being reclaimed between locked
        // boot and unlock, so commit the one small value synchronously.
        preferences.edit(commit = true) {
            putBoolean(DIRECT_BOOT_CREDENTIAL_RESTORE_PENDING, true)
        }
    }

    fun markCredentialRestoreComplete() {
        if (!credentialRestorePending) return
        preferences.edit(commit = true) {
            remove(DIRECT_BOOT_CREDENTIAL_RESTORE_PENDING)
        }
    }
}

internal fun Context.isCredentialStorageUnlocked(): Boolean = runCatching {
    getSystemService(UserManager::class.java)?.isUserUnlocked == true
}.getOrDefault(false)
