package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // Do not infer app VPN intent or touch credential encrypted SDK
                // state here. Android itself starts a configured Always-on
                // VpnService; the Direct-Boot-aware service then installs the
                // minimal fail-closed guard.
                app.handleLockedBootCompleted(intent.action)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_MY_PACKAGE_UNSUSPENDED -> {
                // vpnRequestStart is transient UI state and is empty after a
                // process/reboot. The restored SDK desired state is the durable
                // source of truth.
                app.restoreVpnServiceFromSystemEvent(intent.action)
            }
        }
    }
}
