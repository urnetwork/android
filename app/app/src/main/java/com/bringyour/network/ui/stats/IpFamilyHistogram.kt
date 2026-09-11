package com.bringyour.network.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.connect.CONNECT_GRID_CANVAS_SIZE
import com.bringyour.network.ui.connect.CONNECT_GRID_POINT_PADDING_PX
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextMuted

// the sdk grid's minimum side length (connectGridSettings.MinSideLength): the
// dot size when no grid has been published yet, so an empty histogram lays
// out at the size the first grid will use
private const val DEFAULT_GRID_WIDTH = 16
// the gap between dots, matching the point padding on the connect widget
private val DOT_SPACING = 1.dp
// the fixed width of the row labels so the dot rows share one left edge
private val LABEL_WIDTH = 34.dp

/**
 * One provider as the histogram sees it: the sdk's address-family category
 * ("dualstack", "v4-only", "v6-only"; legacy empty reads as v4-only) and
 * whether the provider is added (routing-eligible). Plain data so the
 * grouping is unit-testable without the gomobile bindings.
 */
data class IpFamilyPoint(
    val clientId: String,
    val ipFamily: String,
    val added: Boolean,
)

/**
 * The three rows of the histogram, in display order. Each maps the sdk's
 * category and label constants to its label resource.
 */
enum class IpFamilyRowKind(val sdkFamily: String, val sdkLabel: String) {
    // the values mirror Sdk.IpFamily* and Sdk.IpFamilyLabel* as literals so
    // the grouping stays a plain JVM unit (the Sdk class loads the native
    // library on first touch)
    BOTH("dualstack", "both"),
    V4("v4-only", "v4"),
    V6("v6-only", "v6");

    companion object {
        /**
         * The row for an sdk category. Anything unknown (including the legacy
         * empty category) is v4: legacy providers carry v4 only, which is what
         * the connect sdk assumes for them too.
         */
        fun fromFamily(ipFamily: String): IpFamilyRowKind = when (ipFamily) {
            BOTH.sdkFamily -> BOTH
            V6.sdkFamily -> V6
            else -> V4
        }

        /** The row for an sdk label ("both", "v4", "v6"); anything unknown is v4. */
        fun fromLabel(label: String): IpFamilyRowKind = when (label) {
            BOTH.sdkLabel -> BOTH
            V6.sdkLabel -> V6
            else -> V4
        }
    }
}

/** One histogram row: the kind and the added providers in it, in stable order. */
data class IpFamilyRow(
    val kind: IpFamilyRowKind,
    val clientIds: List<String>,
)

/**
 * Groups the added providers into the three rows. Always returns all three
 * rows in display order (an empty row still shows its label), and orders the
 * dots within a row by client id so a dot keeps its position across updates
 * instead of jumping as the sdk re-emits the map in hash order.
 */
fun ipFamilyHistogramRows(points: Collection<IpFamilyPoint>): List<IpFamilyRow> {
    val byKind = points
        .filter { it.added }
        .groupBy({ IpFamilyRowKind.fromFamily(it.ipFamily) }, { it.clientId })
    return IpFamilyRowKind.entries.map { kind ->
        IpFamilyRow(kind, (byKind[kind] ?: emptyList()).sorted())
    }
}

/**
 * The drawn diameter of one dot in pixels, exactly the connect widget's:
 * its canvas is `canvasSizePx` wide and holds `gridWidth` points per side,
 * each drawn at the cell size minus the point padding. Falls back to the sdk
 * grid's minimum side length before a grid has been published.
 */
fun ipFamilyDotDiameterPx(canvasSizePx: Float, gridWidth: Int?, paddingPx: Float = CONNECT_GRID_POINT_PADDING_PX): Float {
    val width = if (gridWidth != null && 0 < gridWidth) gridWidth else DEFAULT_GRID_WIDTH
    return (canvasSizePx / width - paddingPx).coerceAtLeast(1f)
}

/**
 * The connected providers grouped by the IP version they carry, under the
 * transport bar in the client statistics card: three wrapping rows labeled
 * both / v4 / v6, one dot per added provider, the dots the same size as on
 * the connect widget. The size follows the live grid width so the two views
 * stay in lockstep as the window grows; the color is the widget's added color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IpFamilyHistogram(
    points: Collection<IpFamilyPoint>,
    gridWidth: Int?,
    modifier: Modifier = Modifier,
) {
    val rows = ipFamilyHistogramRows(points)
    val density = LocalDensity.current
    val dotDiameter: Dp = with(density) {
        ipFamilyDotDiameterPx(CONNECT_GRID_CANVAS_SIZE.toPx(), gridWidth).toDp()
    }
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // title row, styled like the transport bar title
        Text(
            stringResource(id = R.string.ip_families),
            style = labelStyle,
            color = TextMuted
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    stringResource(id = row.kind.labelResId()),
                    style = labelStyle,
                    color = TextMuted,
                    modifier = Modifier
                        .width(LABEL_WIDTH)
                        .padding(top = 1.dp),
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
                    verticalArrangement = Arrangement.spacedBy(DOT_SPACING),
                ) {
                    for (clientId in row.clientIds) {
                        key(clientId) {
                            Box(
                                modifier = Modifier
                                    .size(dotDiameter)
                                    .background(Green, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The localized row label resource for a row kind. */
fun IpFamilyRowKind.labelResId(): Int = when (this) {
    IpFamilyRowKind.BOTH -> R.string.ip_family_both
    IpFamilyRowKind.V4 -> R.string.ip_family_v4
    IpFamilyRowKind.V6 -> R.string.ip_family_v6
}
