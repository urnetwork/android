package com.bringyour.network.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * One tick per widget kind, moved before each re-render request
 * (GlanceWidgetRefresh).
 *
 * Glance keeps a widget's composition running for about 45 seconds after it
 * renders, longer while events arrive, and an update in that time recomposes
 * it without running provideGlance again. An entry loaded in provideGlance
 * was the one the session started with: the dashboard kept showing a quick
 * connect start's connecting state after its wait ended, and a disconnect
 * soon after an update kept it on. Each widget reads its entry inside its
 * composition instead, again on each of its kind's ticks
 * ([rememberWidgetEntry]). Safe for concurrent use.
 */
internal object WidgetReloads {
    private val ticks = mapOf(
        WidgetKinds.DASHBOARD to MutableStateFlow(0L),
        WidgetKinds.PROVIDER_GLOBE to MutableStateFlow(0L),
        WidgetKinds.CONTRACTS to MutableStateFlow(0L),
    )

    fun tick(kind: String): StateFlow<Long> = ticks.getValue(kind)

    /** A re-render of the kind's widgets is about to be requested. */
    fun bump(kind: String) {
        ticks.getValue(kind).update { it + 1 }
    }
}

/**
 * The widget's entry: read when the composition starts, and again on each
 * of its kind's ticks while it runs.
 */
@Composable
internal fun rememberWidgetEntry(context: Context, kind: String): WidgetEntry {
    val tick by WidgetReloads.tick(kind).collectAsState()
    return remember(tick) { WidgetEntry.load(context) }
}
