package com.bringyour.network

import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * The one connect/disconnect path behind every quick connect surface: the
 * Quick Settings tile, the launcher shortcuts, the widgets' button and the
 * notification action. It mirrors what the connect screen does — a
 * short-lived ConnectViewController connecting to the saved location (else
 * the best available provider) or disconnecting — and flipping
 * `connectEnabled` is what starts or stops the VPN service through the app's
 * own connect-change listener.
 *
 * Everything runs in the app process next to the SDK, so there is no IPC and
 * no shared intent record to keep: the SDK local state the connect screen
 * reads is the same one these surfaces write.
 */
object QuickConnect {

    enum class Result {
        /** The request was applied. */
        APPLIED,
        /**
         * The request was applied, but the first-ever connect needs the system
         * VPN consent dialog, which only an activity can show: open the app so
         * it finishes starting the tunnel (`vpnRequestStart`).
         */
        NEEDS_CONSENT,
        /** Logged out, or the device is not initialized yet: only the app can help. */
        NEEDS_APP,
    }

    /** The app has a signed-in device to drive. */
    fun isConfigured(app: MainApplication): Boolean = app.device != null

    /** The tile's notion of "on": the user asked for a connection. */
    fun isConnected(app: MainApplication): Boolean = app.device?.connectEnabled == true

    fun toggle(app: MainApplication, source: String): Result =
        setConnected(app, connect = !isConnected(app), source = source)

    fun setConnected(app: MainApplication, connect: Boolean, source: String): Result {
        val device = app.device ?: return Result.NEEDS_APP
        if (device.connectEnabled == connect) {
            return if (connect && VpnService.prepare(app) != null) Result.NEEDS_CONSENT else Result.APPLIED
        }
        val vc = device.openConnectViewController() ?: return Result.NEEDS_APP
        try {
            if (connect) {
                val location = device.connectLocation
                if (location != null) {
                    vc.connect(location)
                } else {
                    vc.connectBestAvailable()
                }
            } else {
                vc.disconnect()
            }
        } finally {
            device.closeViewController(vc)
        }
        android.util.Log.i("QuickConnect", "${if (connect) "connect" else "disconnect"} from $source")
        if (connect && VpnService.prepare(app) != null) {
            return Result.NEEDS_CONSENT
        }
        return Result.APPLIED
    }

    /** The app's launcher intent, for the surfaces that must hand off to it. */
    fun launchAppIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
