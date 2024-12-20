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
                    app.startVpnService()
                }
            }
        }
    }
}
