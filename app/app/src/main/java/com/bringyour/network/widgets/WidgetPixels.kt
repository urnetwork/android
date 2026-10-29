package com.bringyour.network.widgets

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.LocalAppWidgetOptions
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * How the widgets size their bitmaps (apple/DUO.md item 15, before Play
 * requires API 37).
 *
 * The size comes from the widget: Glance's LocalSize, the dp size the
 * launcher gives it (SizeMode.Exact composes once for each size the launcher
 * reports). The density and the screen come from the Glance context's
 * Configuration, never its DisplayMetrics, which on foldables "can report
 * inactive display dimensions" (Android 17 behavior changes, "Memory limit
 * widget", which says to use WindowMetrics instead). A Glance session has no
 * Activity and no window: its context is the application's. On such a
 * context WindowManager.currentWindowMetrics reads this same Configuration
 * (its density is configuration.densityDpi / 160), while the platform logs
 * that call as a visual service used from a non-visual context. For an app
 * targeting API 35 and up, as this one does, Configuration.screenWidthDp "is
 * the same as WindowMetrics, but is expressed rounded to the nearest dp", so
 * reading the Configuration gives WindowMetrics without the WindowManager.
 * It is also the density Glance converts the widget's own dp padding with.
 *
 * Every bitmap carries the density it was drawn at (Bitmap.density), so the
 * launcher shows it at the dp size it was drawn for even when its display's
 * density is another: a foldable's other display, or a changed display size.
 * Before, a bitmap took the device's factory density (Bitmap's default),
 * so the contract stacks, which take their bitmap's size, showed larger or
 * smaller than the cards they were measured for whenever the display's
 * density was not that one.
 *
 * From API 37 the system rejects a widget update whose bitmaps, over every
 * size it carries, pass 6 bytes per pixel of the active display (1.5
 * full-screen ARGB_8888 buffers). The widgets keep to half of that, so a
 * ceiling taken from the smaller display of a foldable, or from a fold that
 * has not reached this process yet, still holds. A widget whose bitmaps
 * would pass it draws them all at the one lower density that fits, so the
 * update is never refused; Glance's error layout, which has no bitmaps, is
 * left for anything else that fails.
 */

/** Android 17's ceiling on one widget update's bitmaps, in bytes per pixel of the active display. */
internal const val WIDGET_BITMAP_CEILING_BYTES_PER_PIXEL = 6L

/** The share of that ceiling the widgets keep to. */
internal const val WIDGET_BITMAP_BUDGET_SHARE = 0.5

/** ARGB_8888, the widgets' bitmap format. */
private const val ARGB_BYTES_PER_PIXEL = 4.0

/** The bytes the widgets allow one update's bitmaps on a [screenWidthPx] x [screenHeightPx] display. */
internal fun widgetBitmapBudgetBytes(screenWidthPx: Int, screenHeightPx: Int): Double =
    WIDGET_BITMAP_CEILING_BYTES_PER_PIXEL * screenWidthPx.toDouble() * screenHeightPx * WIDGET_BITMAP_BUDGET_SHARE

/**
 * The pixels per dp a widget draws its bitmaps at: the display's [density],
 * unless bitmaps covering each of its [sizes] whole would pass
 * [budgetBytes], when every size draws at the one lower density that fits.
 * A widget's bitmaps never overlap and stay inside it, so covering each size
 * whole bounds what it draws.
 */
internal fun widgetBitmapDensity(density: Float, sizes: List<DpSize>, budgetBytes: Double): Float {
    val bytes = sizes.sumOf { size ->
        ARGB_BYTES_PER_PIXEL * (size.width.value * density).toDouble() * (size.height.value * density).toDouble()
    }
    if (bytes <= budgetBytes || bytes <= 0.0 || budgetBytes <= 0.0) {
        return density
    }
    return density * sqrt(budgetBytes / bytes).toFloat()
}

/**
 * The density this widget's bitmaps are drawn at, in the composition for
 * one of its sizes: the display's, from the Glance context's Configuration,
 * lowered when the bitmaps of all the sizes the launcher composes it at
 * would pass the update's budget ([widgetBitmapDensity]).
 */
@Composable
internal fun widgetBitmapDensity(): Float {
    val configuration = LocalContext.current.resources.configuration
    val density = if (configuration.densityDpi != 0) {
        configuration.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
    } else {
        DisplayMetrics.DENSITY_DEVICE_STABLE / DisplayMetrics.DENSITY_DEFAULT.toFloat()
    }
    // the window's size in the same configuration: for apps targeting API
    // 35 and up it includes the system bars, the area WindowMetrics bounds
    // report; below, a little less, which only lowers the budget
    val screenWidthPx = (configuration.screenWidthDp * density).roundToInt()
    val screenHeightPx = (configuration.screenHeightDp * density).roundToInt()
    if (screenWidthPx <= 0 || screenHeightPx <= 0) {
        return density
    }
    return widgetBitmapDensity(
        density = density,
        sizes = composedSizes(LocalAppWidgetOptions.current, LocalSize.current),
        budgetBytes = widgetBitmapBudgetBytes(screenWidthPx, screenHeightPx),
    )
}

/**
 * The sizes Glance composes a SizeMode.Exact widget at, read as Glance reads
 * them: the launcher's list of sizes from API 31, else its portrait and
 * landscape sizes, else the one being composed.
 */
private fun composedSizes(options: Bundle, composing: DpSize): List<DpSize> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        if (!sizes.isNullOrEmpty()) {
            return sizes.map { DpSize(it.width.dp, it.height.dp) }
        }
    }
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    if (0 < minWidth && 0 < maxWidth && 0 < minHeight && 0 < maxHeight) {
        return listOf(DpSize(minWidth.dp, maxHeight.dp), DpSize(maxWidth.dp, minHeight.dp))
    }
    return listOf(composing)
}

/** Marks a bitmap drawn at [density] pixels per dp, so the launcher shows it at the dp size it was drawn for. */
internal fun Bitmap.drawnAt(density: Float): Bitmap {
    this.density = (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    return this
}
