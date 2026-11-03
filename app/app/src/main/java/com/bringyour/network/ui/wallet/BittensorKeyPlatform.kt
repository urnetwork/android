package com.bringyour.network.ui.wallet

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bringyour.network.QuickConnectActivity
import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/*
 * What only Android can do for the payout key (apple/BTEASY.md, Screen
 * privacy, and the port brief's Android column): device authentication with
 * BiometricPrompt and the device credential as its fallback, a clipboard copy
 * marked sensitive and cleared after 60 s, and the one local notification,
 * on a channel of its own with one id. The sdk's flow decides when each is
 * used; nothing here keeps or logs a phrase.
 */

// ---- device authentication

/** Biometrics, or the device's PIN, pattern or password. */
interface BittensorDeviceAuthenticating {
    suspend fun authenticate(): Boolean
}

object BittensorDeviceAuthenticator {

    // the device credential is the fallback; a biometric that only unlocks
    // the screen is enough before API 30, where strong with the credential
    // can't be asked for together
    private val authenticators: Int
        get() = if (Build.VERSION_CODES.R <= Build.VERSION.SDK_INT) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    /**
     * Asks the device's owner to authenticate, over [context]'s activity. A
     * device with no screen lock has nothing to ask for, and its owner must
     * still be able to save the phrase, so that counts as authenticated. A
     * failed or cancelled prompt does not.
     */
    suspend fun authenticate(context: Context, title: String): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard != null && !keyguard.isDeviceSecure) {
            return true
        }
        val activity = context.fragmentActivity() ?: return false
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return false
        }
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                },
            )
            // with the device credential allowed there is no negative button:
            // the system's own cancel ends the prompt
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
            continuation.invokeOnCancellation {
                ContextCompat.getMainExecutor(activity).execute { prompt.cancelAuthentication() }
            }
        }
    }

    /** Whether the device asks for a biometric, for the controls that ask: else a lock. */
    fun usesBiometric(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    // the activity under a composition's context, through its wrappers
    private fun Context.fragmentActivity(): FragmentActivity? {
        var current: Context? = this
        while (current != null) {
            if (current is FragmentActivity) {
                return current
            }
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }
}

// ---- the clipboard

fun interface BittensorKeyPasteboarding {
    fun copy(phrase: String)
}

/**
 * An explicit Copy, the only way the phrase reaches the clipboard. The clip
 * is marked sensitive (EXTRA_IS_SENSITIVE: Android 13 and later show no
 * preview of it, and keyboards that offer the clipboard leave it out) and is
 * cleared after [Sdk.BittensorLocalKeyCopySeconds]. Android lets an app read
 * the clipboard only while it has focus, so at the clear the clip is kept only
 * when it can be read and is another one; one that can't be read is cleared,
 * the safe side for a recovery phrase. Best effort: a process that dies
 * within the 60 s leaves the clear to the system's own expiry. The phrase is
 * not kept here; the label and the copy's time say which clip is ours.
 */
class BittensorKeyClipboard(context: Context) : BittensorKeyPasteboarding {

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("InlinedApi")
    override fun copy(phrase: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(CLIP_LABEL, phrase)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        // the system stamps the clip it holds; ours is the one with this time
        val copiedAt = clipboard.primaryClipDescription?.timestamp
        handler.postDelayed({ clear(clipboard, copiedAt) }, Sdk.BittensorLocalKeyCopySeconds * 1000L)
    }

    private fun clear(clipboard: ClipboardManager, copiedAt: Long?) {
        runCatching {
            val description = clipboard.primaryClipDescription
            val ours = description == null || copiedAt == null ||
                (description.label?.toString() == CLIP_LABEL && description.timestamp == copiedAt)
            if (ours) {
                if (Build.VERSION_CODES.P <= Build.VERSION.SDK_INT) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    companion object {
        private const val CLIP_LABEL = "URnetwork"
    }
}

// ---- the one notification

fun interface BittensorKeyReminderPosting {
    /** Does what the flow's reminder says, and tells the flow what happened. */
    fun apply(reminder: BittensorKeyReminderInfo, flow: BittensorKeyFlowing, networkId: String)
}

/**
 * The one local notification of an unsaved key, on a channel of its own and
 * with one id, so posting again replaces rather than stacks. Tapping it opens
 * Earnings with the Key screen, through the app's "open on a screen" entry
 * (QuickConnectActivity), which works whether the app runs or not. The app
 * never asks for the permission here: without it this reports nothing to the
 * flow, so a later permission still gets the reminder, and the Earnings banner
 * does the work meanwhile. A scheduled one is a WorkManager job, which asks
 * the sdk again before it posts, since the key may be saved by then.
 */
class BittensorKeyReminderNotice(context: Context) : BittensorKeyReminderPosting {

    private val context = context.applicationContext

    override fun apply(reminder: BittensorKeyReminderInfo, flow: BittensorKeyFlowing, networkId: String) {
        when (reminder.action) {
            Sdk.BittensorLocalKeyReminderCancel -> withdraw(context)
            Sdk.BittensorLocalKeyReminderSchedule -> {
                if (!allowed(context)) {
                    return
                }
                val delayMillis = maxOf(1_000L, reminder.atMillis - System.currentTimeMillis())
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<BittensorKeyReminderWorker>()
                        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(BittensorKeyReminderWorker.NETWORK_ID to networkId))
                        .build(),
                )
                flow.reminderScheduled(reminder.atMillis)
            }
            Sdk.BittensorLocalKeyReminderDeliverNow -> {
                if (post(context)) {
                    flow.reminderDelivered()
                }
            }
            else -> {}
        }
    }

    companion object {
        const val CHANNEL_ID = "bittensor_key_reminder"
        const val NOTIFICATION_ID = 7_301
        private const val WORK_NAME = "bittensor_key_reminder"
        private const val CONTENT_REQUEST_CODE = 7_301

        /** The key was saved or deleted, or the account is gone: nothing to remind of. */
        fun withdraw(context: Context) {
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }

        /** Notifications are allowed, for the app and for this channel once it exists. */
        fun allowed(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) {
                return false
            }
            val channel = manager.getNotificationChannelCompat(CHANNEL_ID)
            return channel == null || channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
        }

        /** Posts the reminder now. False when the app may not post it. */
        @SuppressLint("MissingPermission")
        fun post(context: Context): Boolean {
            val appContext = context.applicationContext
            if (!allowed(appContext)) {
                return false
            }
            val manager = NotificationManagerCompat.from(appContext)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(appContext.getString(R.string.bittensor_key_back_up))
                    .build()
            )
            val open = PendingIntent.getActivity(
                appContext,
                CONTENT_REQUEST_CODE,
                Intent(appContext, QuickConnectActivity::class.java)
                    .setAction(QuickConnectActivity.ACTION_OPEN)
                    .putExtra(QuickConnectActivity.EXTRA_ROUTE, QuickConnectActivity.ROUTE_EARNINGS_KEY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val body = appContext.getString(R.string.bittensor_key_reminder_body_2)
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status)
                .setContentTitle(appContext.getString(R.string.bittensor_key_reminder_title_2))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            return runCatching {
                manager.notify(NOTIFICATION_ID, notification)
                true
            }.getOrDefault(false)
        }
    }
}

/**
 * The scheduled reminder. It posts only while the sdk still says the
 * network's key needs a backup: one saved or deleted since has nothing to
 * remind of.
 */
class BittensorKeyReminderWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val networkId = inputData.getString(NETWORK_ID) ?: return Result.success()
        if (AndroidBittensorLocalKeyStore.shared(applicationContext).needsBackup(networkId)) {
            BittensorKeyReminderNotice.post(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val NETWORK_ID = "network_id"
    }
}
