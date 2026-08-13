package com.bringyour.network.ui.connect.providerlocations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
// The selected provider's dot is its own country color darkened toward black.
// Same factor on every platform (see PROVIDERLOCATIONS.md), so the selection
// reads the same everywhere.
private const val SELECTED_DOT_DARKEN = 0.55f
// The sphere is sized to fit its box with room for a selected dot's ring at
// the limb, so the globe never paints outside the component. (The web zooms
// past its frame and crops; here the globe sits fully inside instead.)
private const val GLOBE_SCALE =
    GlobeGeometry.CENTER - DOT_RADIUS - SELECTED_RING_GAP - SELECTED_RING_STROKE
// Recentering is a primary interaction (every selection change recenters), not
// the web's occasional pointer-leave animation, so it is snappier than the
// web's 1000ms — a slow curve makes stepping feel like it lags the finger.
//
// It is a spring rather than a timing curve because those recenters overlap: a
// second one lands while the first is still running, and `Animatable.animateTo`
// carries the current velocity into a spring, where a tween restarts from a
// standstill and reads as a stutter on every step. StiffnessLow (200) is a
// natural frequency of ~14 rad/s — the same ~450ms settle the tween had, and
// the same spring as apple's `response: 0.45` — and NoBouncy is critically
// damped, so the globe settles onto the provider instead of swinging past it.
private val RECENTER_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)
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
 * The rotation the globe is animating toward, identified by the provider it
 * belongs to. Comparing the coordinates as well as the id is what makes a
 * provider whose position changes under the selection recenter the globe.
 */
private data class GlobeCenterTarget(
    val clientId: String,
    val lat: Double,
    val lon: Double,
)

/**
 * The provider globe: a dark sphere with white land, a graticule, and one dot
 * per plottable provider colored by its country. The selected provider gets a
 * ring, and the globe is always centered on it — however the selection moved,
 * whether the user tapped a dot or a row, stepped the wheel, or the provider it
 * was resting on left the window.
 *
 * [onStep] reports wheel steps (positive east) once a horizontal drag crosses
 * the hysteresis threshold; the wheel itself — the centroid-relative order and
 * the clamping at its ends — lives in the sdk's ProviderLocationsViewController.
 */
@Composable
fun ProviderGlobe(
    rows: List<ProviderLocationRow>,
    selectedClientId: String?,
    onSelect: (String) -> Unit,
    onStep: (Int) -> Unit,
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

    // read inside the gesture handler without restarting it — re-keying
    // pointerInput on each step would cancel the in-flight drag
    val currentOnStep by rememberUpdatedState(onStep)

    // Where the globe belongs: the selected provider's position on the sphere,
    // or null when there is nothing to center on (no providers yet, or a
    // selected provider with no coordinates and so no dot to center under).
    // Until the globe has been placed once the first plottable provider stands
    // in for an unplottable selection, so opening the sheet lands on a provider
    // rather than on the empty Atlantic at (0, 0); after that the globe follows
    // the selection only, because chasing the first row as the window turns
    // over would yank the globe around for no reason the user can see.
    var centeredOnce by remember { mutableStateOf(false) }
    val centerTarget = run {
        val row = plottable.firstOrNull { it.clientId == selectedClientId }
            ?: plottable.firstOrNull().takeIf { !centeredOnce }
        val lat = row?.lat
        val lon = row?.lon
        if (row != null && lat != null && lon != null) {
            GlobeCenterTarget(row.clientId, lat, lon)
        } else {
            null
        }
    }
    // Keyed on the whole target, not just the selected id: a provider whose
    // coordinates arrive after its row did must still pull the globe over.
    // Restarting this effect cancels the in-flight animateTo, which leaves the
    // Animatable at its current value AND velocity — which is exactly what the
    // spring below then continues from.
    LaunchedEffect(centerTarget) {
        if (centerTarget != null) {
            centeredOnce = true
            // fold any accumulated drag into the animated value so the
            // interpolation starts where the user left the globe
            rotationLambda.snapTo(rotationLambda.value + dragLambda)
            rotationPhi.snapTo((rotationPhi.value + dragPhi).coerceIn(-90f, 90f))
            dragLambda = 0f
            dragPhi = 0f
            val to = GlobeGeometry.rotationCentering(
                centerTarget.lon.toFloat(),
                centerTarget.lat.toFloat(),
            )
            val from = rotationLambda.value to rotationPhi.value
            // resolve the target to its nearest equivalent angle so the globe
            // spins the short way around
            val shortest = GlobeGeometry.lerpRotation(from, to, 1f)
            launch { rotationLambda.animateTo(shortest.first, RECENTER_SPRING) }
            launch { rotationPhi.animateTo(shortest.second, RECENTER_SPRING) }
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
            .pointerInput(plottable.isEmpty()) {
                if (plottable.isEmpty()) {
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
                            currentOnStep(step.steps)
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

        // Provider dots. The selected one is held back and drawn last so it is
        // never covered by a dot that happens to sit on top of it — providers
        // in one city land on the same pixel.
        var selectedCenter: Offset? = null
        var selectedColor: Color? = null
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
            if (row.clientId == selectedClientId) {
                selectedCenter = center
                selectedColor = color
            } else {
                drawCircle(color = color, radius = DOT_RADIUS * unit, center = center)
            }
        }
        val center = selectedCenter
        val color = selectedColor
        if (center != null && color != null) {
            // a darker core inside its own full-strength ring: the selection
            // reads at a glance without changing which country color it is
            drawCircle(color = color.darkenForSelection(), radius = DOT_RADIUS * unit, center = center)
            drawCircle(
                color = color,
                radius = SELECTED_RING_RADIUS * unit,
                center = center,
                style = Stroke(width = SELECTED_RING_STROKE * unit),
            )
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
/** The same color, darkened toward black for the selected provider's dot. */
private fun Color.darkenForSelection(): Color = Color(
    red = red * SELECTED_DOT_DARKEN,
    green = green * SELECTED_DOT_DARKEN,
    blue = blue * SELECTED_DOT_DARKEN,
    alpha = alpha,
)

fun providerColor(row: ProviderLocationRow, getLocationColor: (String) -> Color): Color {
    if (row.countryCode.isEmpty()) {
        return Color(UNKNOWN_COUNTRY_COLOR)
    }
    return getLocationColor(row.countryCode.lowercase())
}
