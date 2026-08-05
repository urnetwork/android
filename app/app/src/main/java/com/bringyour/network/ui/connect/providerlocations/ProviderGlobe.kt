package com.bringyour.network.ui.connect.providerlocations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// visual constants, matching the /ip globe on ur.io (see PROVIDERLOCATIONS.md
// "ur.io /ip globe"). All lengths are in the 600-unit virtual space.
private val GRATICULE_COLOR = Color(0xCC, 0xCC, 0xCC, 0x60)
private const val LAND_STROKE_WIDTH = 0.3f
private const val GRATICULE_STROKE_WIDTH = 0.5f
private const val DOT_RADIUS = 7f
// the selected provider keeps its solid dot; the ring is an outline sitting
// SELECTED_RING_GAP outside the dot's edge (radii are stroke centerlines)
private const val SELECTED_RING_GAP = 4f
private const val SELECTED_RING_STROKE = 1.5f
private const val SELECTED_RING_RADIUS =
    DOT_RADIUS + SELECTED_RING_GAP + SELECTED_RING_STROKE / 2f
private const val UNKNOWN_COUNTRY_COLOR = 0xFF0099FF.toInt()
// The sphere is sized to fit its box with room for a selected dot's ring at
// the limb, so the globe never paints outside the component. (The web zooms
// past its frame and crops; here the globe sits fully inside instead.)
private const val GLOBE_SCALE =
    GlobeGeometry.CENTER - DOT_RADIUS - SELECTED_RING_GAP - SELECTED_RING_STROKE
// Recentering is now a primary interaction (every wheel step recenters), not
// the web's occasional pointer-leave animation, so it is snappier than the
// web's 1000ms — a slow ease makes rapid stepping feel like it lags the finger.
private const val RECENTER_MILLIS = 450
private const val TAP_SLOP = 28f
// how far the finger travels to advance one provider, as a fraction of the
// globe's width
private const val WHEEL_STEP_WIDTH_FRACTION = 0.18f
// the component is wider than it is tall; the globe fits to the smaller
// dimension (the height here) and centers, so it is never cropped
private const val GLOBE_HEIGHT_FRACTION = 0.75f
private const val GLOBE_ASPECT = 1f / GLOBE_HEIGHT_FRACTION
private const val WORLD_TOPOLOGY_ASSET = "world-110m.json"

/**
 * The provider globe: a dark sphere with white land, a graticule, and one dot
 * per plottable provider colored by its country. The selected provider gets a
 * ring, and selecting spins the globe to center that provider.
 */
@Composable
fun ProviderGlobe(
    rows: List<ProviderLocationRow>,
    selectedClientId: String?,
    onSelect: (String) -> Unit,
    getLocationColor: (String) -> Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // ~100 KB of TopoJSON to parse and stitch: decode off the main thread so
    // opening the sheet does not drop frames. The globe renders (sphere,
    // graticule, dots) while the land is still loading.
    val topology by produceState<WorldTopology?>(initialValue = null, context) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.assets.open(WORLD_TOPOLOGY_ASSET).bufferedReader().use { it.readText() }
            }.mapCatching { WorldTopology.decode(it) }.getOrNull()
        }
    }
    val graticule = remember { GlobeGeometry.graticule() }

    val rotationLambda = remember { Animatable(0f) }
    val rotationPhi = remember { Animatable(0f) }
    // drag offsets applied on top of the animated rotation
    var dragLambda by remember { mutableFloatStateOf(0f) }
    var dragPhi by remember { mutableFloatStateOf(0f) }

    val lambda = rotationLambda.value + dragLambda
    val phi = (rotationPhi.value + dragPhi).coerceIn(-90f, 90f)

    val plottable = remember(rows) { rows.filter { it.plottable } }

    // The wheel order is by longitude (west to east), independent of the
    // list's duration order: swiping traverses the globe left to right.
    val wheel = remember(plottable) { plottable.sortedBy { it.lon } }
    // read inside the gesture handler without restarting it — re-keying
    // pointerInput on the selection would cancel the drag on every step
    val currentWheel by rememberUpdatedState(wheel)
    val currentSelectedClientId by rememberUpdatedState(selectedClientId)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Spin to the selected provider. Keyed on the selection alone: the provider
    // list churns as the window turns over, and recentering on every such
    // update would yank the globe out from under a user who is dragging it.
    // The globe is centered once on the first provider that appears.
    var centeredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(selectedClientId, plottable.isNotEmpty()) {
        val target = plottable.firstOrNull { it.clientId == selectedClientId }
            ?: plottable.firstOrNull().takeIf { !centeredOnce }
        val lat = target?.lat
        val lon = target?.lon
        if (lat != null && lon != null) {
            centeredOnce = true
            // fold any accumulated drag into the animated value so the
            // interpolation starts where the user left the globe
            rotationLambda.snapTo(rotationLambda.value + dragLambda)
            rotationPhi.snapTo((rotationPhi.value + dragPhi).coerceIn(-90f, 90f))
            dragLambda = 0f
            dragPhi = 0f
            val to = GlobeGeometry.rotationCentering(lon.toFloat(), lat.toFloat())
            val from = rotationLambda.value to rotationPhi.value
            // resolve the target to its nearest equivalent angle so the globe
            // spins the short way around
            val shortest = GlobeGeometry.lerpRotation(from, to, 1f)
            val spec = tween<Float>(durationMillis = RECENTER_MILLIS, easing = EaseInOutCubic)
            launch { rotationLambda.animateTo(shortest.first, spec) }
            launch { rotationPhi.animateTo(shortest.second, spec) }
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GLOBE_ASPECT)
            // GLOBE_SCALE already keeps every draw inside the box; this is the
            // backstop so a Canvas (which does not clip) can never paint over
            // the rows above and below
            .clipToBounds()
            // Two interaction modes. With providers on the globe the drag is a
            // scroll wheel locked to the provider order — free rotation would
            // fight the centering animation. With none, there is nothing to
            // traverse, so the globe rotates freely.
            .pointerInput(wheel.isEmpty()) {
                if (currentWheel.isEmpty()) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val unit = GlobeGeometry.unitFor(
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                        val k = GlobeGeometry.dragDegreesPerVirtualPx(GLOBE_SCALE)
                        dragLambda += dragAmount.x / unit * k
                        dragPhi -= dragAmount.y / unit * k
                    }
                } else {
                    // travel carries across pointer events within one gesture
                    // and resets between gestures, so each swipe starts fresh
                    var travel = 0f
                    detectDragGestures(
                        onDragEnd = { travel = 0f },
                        onDragCancel = { travel = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        travel += dragAmount.x
                        val step = GlobeGeometry.wheelStep(
                            travel,
                            size.width * WHEEL_STEP_WIDTH_FRACTION,
                        )
                        if (step.steps != 0) {
                            travel = step.remainingTravel
                            val order = currentWheel
                            val current = order.indexOfFirst {
                                it.clientId == currentSelectedClientId
                            }
                            val next = if (current < 0) {
                                // nothing selected yet: the first step lands on
                                // the westernmost provider
                                0
                            } else {
                                GlobeGeometry.wrapIndex(current, step.steps, order.size)
                            }
                            order.getOrNull(next)?.let { currentOnSelect(it.clientId) }
                        }
                    }
                }
            }
            .pointerInput(plottable, lambda, phi) {
                detectTapGestures { tap ->
                    val tapVirtual = GlobeGeometry.toVirtual(
                        tap.x,
                        tap.y,
                        size.width.toFloat(),
                        size.height.toFloat(),
                    )
                    val points = plottable.map { row ->
                        GlobeGeometry.project(
                            row.lon!!.toFloat(),
                            row.lat!!.toFloat(),
                            lambda,
                            phi,
                            GLOBE_SCALE,
                        )
                    }
                    val visible = points.mapIndexed { index, point -> index to point }
                        .filter { it.second != null }
                    val hit = GlobeGeometry.nearestWithin(
                        tapVirtual.x,
                        tapVirtual.y,
                        visible.map { it.second!! },
                        TAP_SLOP,
                    )
                    if (0 <= hit) {
                        onSelect(plottable[visible[hit].first].clientId)
                    }
                }
            },
    ) {
        // fit center: the globe is scaled to the smaller dimension and
        // centered in both, so a non-square box neither crops nor offsets it
        val unit = GlobeGeometry.unitFor(size.width, size.height)
        val currentScale = GLOBE_SCALE

        fun canvas(point: GlobePoint): Offset {
            val mapped = GlobeGeometry.toCanvas(point, size.width, size.height)
            return Offset(mapped.x, mapped.y)
        }

        // the sphere
        drawCircle(
            color = Black,
            radius = currentScale * unit,
            center = Offset(size.width / 2f, size.height / 2f),
        )

        // land: filled countries with a hairline border, clamped at the horizon
        topology?.countries?.forEach { country ->
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
                    val point = GlobeGeometry.projectClamped(lon, lat, lambda, phi, currentScale)
                    val offset = canvas(point)
                    if (started) {
                        path.lineTo(offset.x, offset.y)
                    } else {
                        path.moveTo(offset.x, offset.y)
                        started = true
                    }
                    i += 2
                }
                if (anyVisible) {
                    path.close()
                    drawPath(path, color = OffWhite)
                    drawPath(
                        path,
                        color = Black,
                        style = Stroke(width = LAND_STROKE_WIDTH * unit),
                    )
                }
            }
        }

        // graticule over the land, as on the web
        graticule.forEach { line ->
            drawPolyline(line, lambda, phi, currentScale, unit)
        }

        // provider dots, largest last so nothing is fully hidden
        plottable.forEach { row ->
            val point = GlobeGeometry.project(
                row.lon!!.toFloat(),
                row.lat!!.toFloat(),
                lambda,
                phi,
                currentScale,
            ) ?: return@forEach
            val color = providerColor(row, getLocationColor)
            val center = canvas(point)
            drawCircle(color = color, radius = DOT_RADIUS * unit, center = center)
            if (row.clientId == selectedClientId) {
                drawCircle(
                    color = color,
                    radius = SELECTED_RING_RADIUS * unit,
                    center = center,
                    style = Stroke(width = SELECTED_RING_STROKE * unit),
                )
            }
        }
    }
}

/**
 * Draws one lon/lat polyline, breaking it wherever it crosses the horizon so
 * the back half is not drawn as a chord across the sphere.
 */
private fun DrawScope.drawPolyline(
    line: FloatArray,
    lambda: Float,
    phi: Float,
    scale: Float,
    unit: Float,
) {
    var path: Path? = null
    var i = 0
    while (i + 1 < line.size) {
        val point = GlobeGeometry.project(line[i], line[i + 1], lambda, phi, scale)
        if (point == null) {
            path?.let {
                drawPath(it, color = GRATICULE_COLOR, style = Stroke(width = GRATICULE_STROKE_WIDTH * unit))
            }
            path = null
        } else {
            val mapped = GlobeGeometry.toCanvas(point, size.width, size.height)
            if (path == null) {
                path = Path().apply { moveTo(mapped.x, mapped.y) }
            } else {
                path.lineTo(mapped.x, mapped.y)
            }
        }
        i += 2
    }
    path?.let {
        drawPath(it, color = GRATICULE_COLOR, style = Stroke(width = GRATICULE_STROKE_WIDTH * unit))
    }
}

/**
 * The dot color is the provider's country color (the same palette the location
 * list uses). Providers whose country is unknown fall back to the web globe's
 * neutral blue.
 */
fun providerColor(row: ProviderLocationRow, getLocationColor: (String) -> Color): Color {
    if (row.countryCode.isEmpty()) {
        return Color(UNKNOWN_COUNTRY_COLOR)
    }
    return getLocationColor(row.countryCode.lowercase())
}
