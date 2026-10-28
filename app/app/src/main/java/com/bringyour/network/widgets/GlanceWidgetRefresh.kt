package com.bringyour.network.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.bringyour.network.MainApplication
import com.bringyour.network.analytics.ClientEvents
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
            // both its receivers: the home screen's and the cover screen's
            WidgetKinds.OVERVIEW -> OverviewWidget::class.java
            else -> return false
        }
        return runCatching {
            runBlocking { GlanceAppWidgetManager(context).getGlanceIds(widget).isNotEmpty() }
        }.getOrDefault(false)
    }

    // the overview shows what each of the three does, so it re-renders with any of them
    override fun reloadDashboard() {
        reload(WidgetKinds.DASHBOARD, DashboardWidget())
        reloadOverview()
    }

    override fun reloadProviderGlobe() {
        reload(WidgetKinds.PROVIDER_GLOBE, ProviderGlobeWidget())
        reloadOverview()
    }

    override fun reloadContracts() {
        reload(WidgetKinds.CONTRACTS, ContractsWidget())
        reloadOverview()
    }

    override fun reloadAll() {
        reload(WidgetKinds.DASHBOARD, DashboardWidget())
        reload(WidgetKinds.PROVIDER_GLOBE, ProviderGlobeWidget())
        reload(WidgetKinds.CONTRACTS, ContractsWidget())
        reloadOverview()
    }

    private fun reloadOverview() = reload(WidgetKinds.OVERVIEW, OverviewWidget())

    /**
     * A running composition reads its entry again on the kind's tick
     * (WidgetReloads); updateAll starts one for a widget that has none,
     * which reads the entry as it starts.
     */
    private fun reload(kind: String, widget: GlanceAppWidget) {
        WidgetReloads.bump(kind)
        scope.launch {
            runCatching { widget.updateAll(context) }
        }
    }
}

/** Widget receivers: one per widget; adding or removing one re-evaluates what the writer tracks. */
class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); ClientEvents.widgetAdded(ClientEvents.WIDGET_DASHBOARD); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

class ProviderGlobeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProviderGlobeWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); ClientEvents.widgetAdded(ClientEvents.WIDGET_GLOBE); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

class ContractsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContractsWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); ClientEvents.widgetAdded(ClientEvents.WIDGET_CONTRACTS); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

/** The overview on the home screen (widget_overview_info). */
class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); ClientEvents.widgetAdded(ClientEvents.WIDGET_OVERVIEW); widgetsChanged(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); widgetsChanged(context) }
}

/**
 * The same overview on the lock screen and Samsung's cover screen
 * (widget_cover_info, widget_cover_samsung): Samsung documents the cover's
 * widget as keyguard only, so it is a receiver of its own.
 */
class CoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); ClientEvents.widgetAdded(ClientEvents.WIDGET_COVER); widgetsChanged(context) }
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
