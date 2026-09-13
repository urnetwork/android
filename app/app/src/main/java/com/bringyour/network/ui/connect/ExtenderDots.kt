package com.bringyour.network.ui.connect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The extender rings of a provider dot (EXTENDER.md K2).
 *
 * A provider reached through extenders is drawn as a filled dot with one ring
 * per extender in that extender's color: stroke 2 dp, a 2 dp gap between the
 * dot and the first ring and between successive rings, and the outermost
 * ring's OUTER edge at the cell edge, so the footprint never grows into a
 * neighbor — the filled dot shrinks inward by 4 dp per ring instead. At most
 * three rings are drawn; four or more collapse into a dashed third ring.
 *
 * The connect widget and the drawer's IP family histogram both draw through
 * here, so the two views stay identical at their own dot sizes.
 */

// K2's stroke and gap. A ring therefore costs 4 dp of dot radius.
const val EXTENDER_RING_STROKE_DP = 2f
const val EXTENDER_RING_GAP_DP = 2f

// K2's ring ceiling. A provider with more extenders than this draws the last
// ring dashed rather than growing the dot.
const val EXTENDER_RING_MAX_COUNT = 3

// The share of the dot's radius the fill keeps whatever the ring count. The
// connect widget's cell is ~15 dp wide, which is too small for three 4 dp
// rings at full scale; rather than drop rings (which would misreport how many
// extenders carry the provider) or grow the dot (which K2 forbids), the stroke
// and the gap scale down together until the rings fit around a dot of this
// fraction. At the common counts — zero, one, briefly two — nothing scales.
private const val MIN_DOT_RADIUS_FRACTION = 0.25f

/**
 * The drawn geometry of one provider dot: the filled dot's radius, the ring
 * radii from innermost to outermost (each the stroke's center line), the
 * stroke width the rings are drawn with, and whether the outermost ring stands
 * for more extenders than it draws and is therefore dashed. All in pixels.
 */
data class ExtenderRingGeometry(
    val dotRadiusPx: Float,
    val ringRadiiPx: List<Float>,
    val strokePx: Float,
    val dashedOutermost: Boolean,
)

/**
 * The rings for a cell of `cellSizePx` across carrying `extenderCount`
 * extenders. `cellSizePx` is the dot's whole footprint — on the connect widget
 * the animating dot's current diameter, which is how the rings animate in and
 * out with the dot.
 */
fun extenderRingGeometry(
    cellSizePx: Float,
    extenderCount: Int,
    strokePx: Float,
    gapPx: Float,
): ExtenderRingGeometry {
    val radius = (cellSizePx / 2f).coerceAtLeast(0f)
    val ringCount = extenderCount.coerceAtMost(EXTENDER_RING_MAX_COUNT)
    val dashed = EXTENDER_RING_MAX_COUNT < extenderCount
    if (radius <= 0f || ringCount <= 0 || strokePx <= 0f) {
        return ExtenderRingGeometry(
            dotRadiusPx = radius,
            ringRadiiPx = listOf(),
            strokePx = 0f,
            dashedOutermost = false,
        )
    }

    // each ring costs its own stroke plus the gap that precedes it
    val pitch = strokePx + gapPx
    val wanted = ringCount * pitch
    val minDotRadius = radius * MIN_DOT_RADIUS_FRACTION
    val scale = if (radius - wanted < minDotRadius) {
        ((radius - minDotRadius) / wanted).coerceIn(0f, 1f)
    } else {
        1f
    }
    val stroke = strokePx * scale
    val gap = gapPx * scale

    // the outermost ring's outer edge sits on the cell edge, so its center
    // line is half a stroke inside it; each ring inward steps by stroke + gap
    val outermost = radius - stroke / 2f
    val radii = (0 until ringCount).map { index ->
        outermost - (ringCount - 1 - index) * (stroke + gap)
    }
    return ExtenderRingGeometry(
        dotRadiusPx = (radius - ringCount * (stroke + gap)).coerceAtLeast(0f),
        ringRadiiPx = radii,
        strokePx = stroke,
        dashedOutermost = dashed,
    )
}

/**
 * The per-extender color hexes of a grid point, in the sdk's order. The sdk
 * carries them as one comma separated string (`ProviderGridPoint`), empty for
 * a provider reached directly.
 */
fun extenderColorHexList(colorHexes: String?): List<String> {
    val hexes = colorHexes ?: return listOf()
    return hexes.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/**
 * One sdk color hex ("3cdd67", six hex digits with no leading `#`) as an
 * opaque ARGB int, or null when it is not one. The sdk computes the value
 * (K3) and every app draws it as given, so nothing here interprets it further.
 */
fun extenderColorArgb(colorHex: String): Int? {
    val hex = colorHex.trim().removePrefix("#")
    if (hex.length != 6) {
        return null
    }
    val rgb = hex.toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb).toInt()
}

/** The drawable ring colors of a grid point, dropping anything unparsable. */
fun extenderRingArgbList(colorHexes: String?): List<Int> =
    extenderColorHexList(colorHexes).mapNotNull { extenderColorArgb(it) }

/**
 * Draws one provider dot with its extender rings. With no extenders this is
 * the plain filled dot the widget has always drawn.
 */
fun DrawScope.drawExtenderDot(
    center: Offset,
    cellSizePx: Float,
    color: Color,
    ringArgb: List<Int>,
    strokePx: Float,
    gapPx: Float,
) {
    val geometry = extenderRingGeometry(
        cellSizePx = cellSizePx,
        extenderCount = ringArgb.size,
        strokePx = strokePx,
        gapPx = gapPx,
    )
    if (0f < geometry.dotRadiusPx) {
        drawCircle(
            color = color,
            radius = geometry.dotRadiusPx,
            center = center,
        )
    }
    if (geometry.ringRadiiPx.isEmpty()) {
        return
    }
    // the dashed ring stands for every extender past the third, so it takes
    // the color of the third one it draws
    val dashEffect = if (geometry.dashedOutermost) {
        PathEffect.dashPathEffect(
            floatArrayOf(geometry.strokePx * 2f, geometry.strokePx * 2f),
            0f,
        )
    } else {
        null
    }
    geometry.ringRadiiPx.forEachIndexed { index, ringRadius ->
        val ringColor = ringArgb.getOrNull(index) ?: return@forEachIndexed
        val outermost = index == geometry.ringRadiiPx.size - 1
        drawCircle(
            color = Color(ringColor).copy(alpha = color.alpha),
            radius = ringRadius,
            center = center,
            style = Stroke(
                width = geometry.strokePx,
                pathEffect = if (outermost) dashEffect else null,
            ),
        )
    }
}
