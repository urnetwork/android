package com.bringyour.network.widgets.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.bringyour.network.ui.connect.providerlocations.GlobeGeometry
import com.bringyour.network.ui.connect.providerlocations.GlobePoint
import com.bringyour.network.ui.connect.providerlocations.WorldTopology
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffWhite
import com.bringyour.network.widgets.WidgetProviderSnapshot
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A static rendering of the app's provider globe (ui/connect/providerlocations/
 * ProviderGlobe.kt) for a widget: the same 600x600 virtual space, orthographic
 * projection (GlobeGeometry), Natural Earth 110m land (WorldTopology) and d3
 * graticule, drawn once per widget update with the globe turned to face the
 * providers' centroid. A widget cannot animate a recenter and has nothing to
 * select, so there is no motion and no selection ring.
 */
object GlobeBitmapRenderer {

    private const val GLOBE_SCALE = 300f - 7f - 4f - 1.5f
    private const val LAND_STROKE_WIDTH = 0.3f
    private const val GRATICULE_STROKE_WIDTH = 0.5f
    private const val DOT_RADIUS = 7f
    private const val GRATICULE_COLOR = 0x60CCCCCC
    private const val UNKNOWN_COUNTRY_COLOR = 0xFF0099FF.toInt()

    /** Where the globe faces with nothing to face: the Atlantic. */
    private const val DEFAULT_LON = -30f
    private const val DEFAULT_LAT = 25f

    @Volatile
    private var topology: WorldTopology? = null

    private fun topology(context: Context): WorldTopology? {
        topology?.let { return it }
        return runCatching {
            val json = context.assets.open("world-110m.json").bufferedReader().use { it.readText() }
            WorldTopology.decode(json)
        }.getOrNull()?.also { topology = it }
    }

    /** Rotation that centers the providers' spherical centroid. */
    fun rotation(providers: List<WidgetProviderSnapshot>): Pair<Float, Float> {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var count = 0
        for (provider in providers) {
            val lat = provider.lat ?: continue
            val lon = provider.lon ?: continue
            val latRadians = Math.toRadians(lat)
            val lonRadians = Math.toRadians(lon)
            x += cos(latRadians) * cos(lonRadians)
            y += cos(latRadians) * sin(lonRadians)
            z += sin(latRadians)
            count += 1
        }
        val norm = sqrt(x * x + y * y + z * z)
        if (count == 0 || norm < 1e-6) {
            return GlobeGeometry.rotationCentering(DEFAULT_LON, DEFAULT_LAT)
        }
        val lon = Math.toDegrees(atan2(y, x)).toFloat()
        val lat = Math.toDegrees(asin((z / norm).coerceIn(-1.0, 1.0))).toFloat()
        return GlobeGeometry.rotationCentering(lon, lat)
    }

    fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        providers: List<WidgetProviderSnapshot>,
        density: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val unit = GlobeGeometry.unitFor(width, height)
        val (lambda, phi) = rotation(providers)

        fun canvasPoint(point: GlobePoint): Pair<Float, Float> {
            val mapped = GlobeGeometry.toCanvas(point, width, height)
            return mapped.x to mapped.y
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // the sphere
        fill.color = Black.toArgb()
        canvas.drawCircle(width / 2f, height / 2f, GLOBE_SCALE * unit, fill)

        // land: filled countries with a hairline border, clamped at the horizon
        topology(context)?.countries?.forEach { country ->
            country.rings.forEach { ring ->
                val path = Path()
                var started = false
                var anyVisible = false
                var i = 0
                while (i + 1 < ring.size) {
                    val lon = ring[i]
                    val lat = ring[i + 1]
                    if (0f <= GlobeGeometry.cosAngleToCenter(lon, lat, lambda, phi)) {
                        anyVisible = true
                    }
                    val (px, py) = canvasPoint(GlobeGeometry.projectClamped(lon, lat, lambda, phi, GLOBE_SCALE))
                    if (started) path.lineTo(px, py) else { path.moveTo(px, py); started = true }
                    i += 2
                }
                if (anyVisible) {
                    path.close()
                    fill.color = OffWhite.toArgb()
                    canvas.drawPath(path, fill)
                    stroke.color = Black.toArgb()
                    stroke.strokeWidth = LAND_STROKE_WIDTH * unit
                    canvas.drawPath(path, stroke)
                }
            }
        }

        // graticule, broken where it crosses the horizon
        stroke.color = GRATICULE_COLOR
        stroke.strokeWidth = GRATICULE_STROKE_WIDTH * unit
        GlobeGeometry.graticule().forEach { line ->
            var path: Path? = null
            var i = 0
            while (i + 1 < line.size) {
                val point = GlobeGeometry.project(line[i], line[i + 1], lambda, phi, GLOBE_SCALE)
                if (point == null) {
                    path?.let { canvas.drawPath(it, stroke) }
                    path = null
                } else {
                    val (px, py) = canvasPoint(point)
                    val current = path
                    if (current == null) {
                        path = Path().apply { moveTo(px, py) }
                    } else {
                        current.lineTo(px, py)
                    }
                }
                i += 2
            }
            path?.let { canvas.drawPath(it, stroke) }
        }

        // provider dots, legible at widget sizes, with a hairline of the sphere
        // color so overlapping dots stay distinct
        val radius = max(2.5f * density, DOT_RADIUS * unit)
        stroke.color = Black.toArgb()
        stroke.strokeWidth = 0.5f * density
        providers.forEach { provider ->
            val lat = provider.lat ?: return@forEach
            val lon = provider.lon ?: return@forEach
            val point = GlobeGeometry.project(lon.toFloat(), lat.toFloat(), lambda, phi, GLOBE_SCALE)
                ?: return@forEach
            val (px, py) = canvasPoint(point)
            fill.color = WidgetColors.parseHex(provider.colorHex, UNKNOWN_COUNTRY_COLOR)
            canvas.drawCircle(px, py, radius, fill)
            canvas.drawCircle(px, py, radius, stroke)
        }
        return bitmap
    }
}

/** Shared color helpers for the widget renderers. */
object WidgetColors {

    /** Six hex digits with or without a leading `#`, as the SDK palette returns them. */
    fun parseHex(hex: String, fallback: Int): Int {
        val digits = hex.removePrefix("#")
        if (digits.length != 6) return fallback
        val value = digits.toLongOrNull(16) ?: return fallback
        return (0xFF000000L or value).toInt()
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
