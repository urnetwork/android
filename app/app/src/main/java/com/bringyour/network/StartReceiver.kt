package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_MY_PACKAGE_UNSUSPENDED -> {
                val app = context.applicationContext as MainApplication
                if (app.vpnRequestStart) {
                    // note starting in Android 15, boot completed receivers cannot start foreground services
                    // see https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
                    // TODO do we need to use a foreground service generally
                    app.startVpnService(false)
                }
            }
        }
    }
}
