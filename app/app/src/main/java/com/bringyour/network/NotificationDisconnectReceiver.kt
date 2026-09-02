package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Private PendingIntent target for the foreground notification's Disconnect action. */
class NotificationDisconnectReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISCONNECT = "com.bringyour.network.action.NOTIFICATION_DISCONNECT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCONNECT) return
        (context.applicationContext as? MainApplication)
            ?.disconnectVpnConnection("notification")
    }
}
