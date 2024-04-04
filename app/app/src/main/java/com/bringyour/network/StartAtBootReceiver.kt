package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class StartAtBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val app = context.applicationContext as MainApplication
            if (app.isVpnRequestStart()) {
                app.startVpnService()
            }
        }
    }
}
