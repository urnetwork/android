package com.bringyour.network.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Debug-only test hooks: pin a widget through the launcher from adb, and
 * switch the widgets to the sample entry (the picker preview content) so
 * their connected layouts can be checked without a signed-in tunnel.
 */
class DebugWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.bringyour.network.debug.PIN_WIDGET" -> {
                val receiver = when (intent.getStringExtra("kind")) {
                    WidgetKinds.PROVIDER_GLOBE -> ProviderGlobeWidgetReceiver::class.java
                    WidgetKinds.CONTRACTS -> ContractsWidgetReceiver::class.java
                    else -> DashboardWidgetReceiver::class.java
                }
                Log.i("DebugWidgetReceiver", "pin ${receiver.simpleName}: ${requestPinWidget(context, receiver)}")
            }
            "com.bringyour.network.debug.SAMPLE" -> {
                val flag = File(context.filesDir, WidgetEntry.SAMPLE_FLAG)
                if (intent.getBooleanExtra("enabled", true)) {
                    flag.parentFile?.mkdirs()
                    flag.writeText("1")
                } else {
                    flag.delete()
                }
                GlanceWidgetRefresh(context).reloadAll()
            }
        }
    }
}
