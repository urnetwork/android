package com.bringyour.network.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.bringyour.network.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The Glance side of [WidgetRefresh]: which widgets are placed, and re-rendering them. */
class GlanceWidgetRefresh(private val context: Context) : WidgetRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun hasWidgets(kind: String): Boolean {
        val widget = when (kind) {
            WidgetKinds.DASHBOARD -> DashboardWidget::class.java
            WidgetKinds.PROVIDER_GLOBE -> ProviderGlobeWidget::class.java
            WidgetKinds.CONTRACTS -> ContractsWidget::class.java
            else -> return false
        }
        return runCatching {
            runBlocking { GlanceAppWidgetManager(context).getGlanceIds(widget).isNotEmpty() }
        }.getOrDefault(false)
    }

    override fun reloadDashboard() = reload(DashboardWidget())
    override fun reloadProviderGlobe() = reload(ProviderGlobeWidget())
    override fun reloadContracts() = reload(ContractsWidget())

    private fun reload(widget: GlanceAppWidget) {
        scope.launch {
            runCatching { widget.updateAll(context) }
        }
    }
}

/** Widget receivers: one per widget; adding or removing one re-evaluates what the writer tracks. */
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

class ProviderGlobeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProviderGlobeWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

class ContractsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContractsWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

private fun widgetsChanged(context: Context) {
    (context.applicationContext as? MainApplication)?.widgetSnapshotWriter?.widgetsChanged()
}

/** Ask the launcher to pin a widget (Android 8+); returns whether the request was made. */
fun requestPinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching {
        manager.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null)
    }.getOrDefault(false)
}
