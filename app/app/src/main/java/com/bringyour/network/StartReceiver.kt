package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper


class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_MY_PACKAGE_UNSUSPENDED -> {
                Handler(Looper.getMainLooper()).post {
                    val app = context.applicationContext as MainApplication
                    if (app.vpnRequestStart) {
                        app.startVpnService()
                    }
                }
            }
        }
    }
}
