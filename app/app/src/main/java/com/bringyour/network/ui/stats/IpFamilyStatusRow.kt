package com.bringyour.network.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTextBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.ppNeueBitBold

/**
 * The address families of the window's providers, as one row of three
 * columns under the transport bar: Dualstack, IPv4 and IPv6 (connect/IPV6.md
 * D2). Each column is a provider category, never a capability, so a dualstack
 * provider counts once, under Dualstack.
 *
 * A column is its label in the pixel face over its status: the providers
 * connected (Added) and connecting (InEvaluation) in that category, one line
 * each and only when the count is not zero, or "disconnected" when both are.
 * Providers that failed evaluation, were not added, or are on their way out
 * count as nothing.
 *
 * The columns rank the families: dualstack carries both, IPv4 and IPv6 tie
 * below it. The best of the columns with a connected provider is bright (the
 * main text color, shared on a tie), the other columns with anything
 * connected or connecting are dimmed (muted), and a column with nothing is
 * dimmed out (faint). The whole column takes its tier, label and status lines
 * alike, so the row reads as three units.
 *
 * The row is always the height of a label and two status lines, top aligned,
 * so it never reflows as lines come and go. The points come from the connect
 * view model's grid, the same window the connect widget draws, and the
 * category is the sdk's for the provider (`ProviderGridPoint.ipFamily`).
 */

// the app's general tween, matching the transport bar
private const val IP_FAMILY_TWEEN_MILLIS = 1000
// the gap between the three columns
private val COLUMN_SPACING = 8.dp
// the gap between a column's label and its lines, and between lines
private val LINE_SPACING = 2.dp

/**
 * One provider as the row sees it: the sdk's grid state ("Added",
 * "InEvaluation", ...) and its address-family category ("dualstack",
 * "v4-only", "v6-only"; legacy empty reads as v4-only). Plain data so the
 * counting is unit-testable without the gomobile bindings.
 */
data class IpFamilyPoint(
    val state: String,
    val ipFamily: String,
)

/**
 * The three columns, in display order, which is also rank order. Each maps
 * the sdk's category and label constants (mirrored as literals so the logic
 * stays a plain JVM unit; the Sdk class loads the native library on first
 * touch) to its column label and its provider-row tag.
 */
enum class IpFamilyColumn(val sdkFamily: String, val sdkLabel: String, val rank: Int) {
    // rank, lower is better: dualstack carries both families, and IPv4 and
    // IPv6 tie below it
    DUALSTACK("dualstack", "both", 0),
    V4("v4-only", "v4", 1),
    V6("v6-only", "v6", 1);

    companion object {
        /**
         * The column a provider category lands in. Anything unknown
         * (including the legacy empty category) is v4: legacy providers carry
         * v4 only, which is what the connect sdk assumes for them too.
         */
        fun fromFamily(ipFamily: String): IpFamilyColumn = when (ipFamily) {
            DUALSTACK.sdkFamily -> DUALSTACK
            V6.sdkFamily -> V6
            else -> V4
        }

        /** The column for an sdk label ("both", "v4", "v6"); anything unknown is v4. */
        fun fromLabel(label: String): IpFamilyColumn = when (label) {
            DUALSTACK.sdkLabel -> DUALSTACK
            V6.sdkLabel -> V6
            else -> V4
        }
    }
}

/** The emphasis of a column. */
enum class IpFamilyTier {
    /** the best-ranked column with a connected provider, shared on a tie */
    BEST,
    /** something connected or connecting, but a better column is connected */
    ACTIVE,
    /** nothing connected or connecting */
    UNAVAILABLE,
}

/**
 * One status line of a column, in display order: connected, then connecting,
 * or disconnected alone. The kind is the line's identity, so a count change
 * updates a line in place while a line appearing or vanishing is an insertion
 * or removal.
 */
sealed class IpFamilyStatusLine(val kind: String) {
    data class Connected(val count: Int) : IpFamilyStatusLine("connected")
    data class Connecting(val count: Int) : IpFamilyStatusLine("connecting")
    data object Disconnected : IpFamilyStatusLine("disconnected")
}

data class IpFamilyColumnStatus(
    val column: IpFamilyColumn,
    /** the Added providers in this category */
    val connectedCount: Int,
    /** the InEvaluation providers in this category */
    val connectingCount: Int,
) {
    /** nothing connected or connecting: the column reads "disconnected" */
    val isUnavailable: Boolean
        get() = connectedCount == 0 && connectingCount == 0
}

// the grid state of a routing-eligible provider, as the sdk spells it
private const val IP_FAMILY_CONNECTED_STATE = "Added"
// the grid state of a provider still being evaluated, as the sdk spells it
private const val IP_FAMILY_CONNECTING_STATE = "InEvaluation"

/**
 * The three columns, always present, counting only the connected and the
 * connecting providers of each category.
 */
fun ipFamilyColumnStatuses(points: Collection<IpFamilyPoint>): List<IpFamilyColumnStatus> {
    val connectedCounts = mutableMapOf<IpFamilyColumn, Int>()
    val connectingCounts = mutableMapOf<IpFamilyColumn, Int>()
    for (point in points) {
        val column = IpFamilyColumn.fromFamily(point.ipFamily)
        when (point.state) {
            IP_FAMILY_CONNECTED_STATE -> connectedCounts[column] = (connectedCounts[column] ?: 0) + 1
            IP_FAMILY_CONNECTING_STATE -> connectingCounts[column] = (connectingCounts[column] ?: 0) + 1
        }
    }
    return IpFamilyColumn.entries.map { column ->
        IpFamilyColumnStatus(
            column = column,
            connectedCount = connectedCounts[column] ?: 0,
            connectingCount = connectingCounts[column] ?: 0,
        )
    }
}

/**
 * The tier of every column: the best rank among the columns with a connected
 * provider is bright (every column at that rank, on a tie), any other column
 * with a live provider is active, and the rest are unavailable. A column that
 * is only connecting carries no traffic yet, so it is never best.
 */
fun ipFamilyColumnTiers(statuses: List<IpFamilyColumnStatus>): Map<IpFamilyColumn, IpFamilyTier> {
    val bestRank = statuses
        .filter { 0 < it.connectedCount }
        .minOfOrNull { it.column.rank }
    return statuses.associate { status ->
        status.column to when {
            status.isUnavailable -> IpFamilyTier.UNAVAILABLE
            0 < status.connectedCount && status.column.rank == bestRank -> IpFamilyTier.BEST
            else -> IpFamilyTier.ACTIVE
        }
    }
}

/**
 * The status lines of a column: the non-zero counts, connected first, or
 * "disconnected" alone when both are zero.
 */
fun ipFamilyStatusLines(status: IpFamilyColumnStatus): List<IpFamilyStatusLine> {
    val lines = mutableListOf<IpFamilyStatusLine>()
    if (0 < status.connectedCount) {
        lines.add(IpFamilyStatusLine.Connected(status.connectedCount))
    }
    if (0 < status.connectingCount) {
        lines.add(IpFamilyStatusLine.Connecting(status.connectingCount))
    }
    if (lines.isEmpty()) {
        lines.add(IpFamilyStatusLine.Disconnected)
    }
    return lines
}

/** The localized column label resource. */
fun IpFamilyColumn.labelResId(): Int = when (this) {
    IpFamilyColumn.DUALSTACK -> R.string.ip_family_dualstack
    IpFamilyColumn.V4 -> R.string.ipv4
    IpFamilyColumn.V6 -> R.string.ipv6
}

/** The localized short tag resource the provider rows show ("Both", "v4", "v6"). */
fun IpFamilyColumn.tagResId(): Int = when (this) {
    IpFamilyColumn.DUALSTACK -> R.string.ip_family_both
    IpFamilyColumn.V4 -> R.string.ip_family_v4
    IpFamilyColumn.V6 -> R.string.ip_family_v6
}

private fun IpFamilyTier.color(): Color = when (this) {
    IpFamilyTier.BEST -> MainTextBase
    IpFamilyTier.ACTIVE -> TextMuted
    IpFamilyTier.UNAVAILABLE -> TextFaint
}

@Composable
fun IpFamilyStatusRow(
    points: Collection<IpFamilyPoint>,
    modifier: Modifier = Modifier,
) {
    val statuses = ipFamilyColumnStatuses(points)
    val tiers = ipFamilyColumnTiers(statuses)
    // the pixel face of the labels, at the smallest size the design sets it
    val labelStyle = TextStyle(fontFamily = ppNeueBitBold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    // the status lines, in the face of the neighboring panels' rows, with
    // tabular digits so a rolling count does not jitter
    val statusStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val description = ipFamilyRowDescription(statuses)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // one element for assistive tech, read as the three columns
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // the tallest column a status can produce, invisible, so the row keeps
        // the height of a label and two status lines whatever the live
        // columns show (mmm/DESIGNSTYLE.md "Placeholders, not pop-in")
        Column(
            modifier = Modifier.alpha(0f),
            verticalArrangement = Arrangement.spacedBy(LINE_SPACING),
        ) {
            Text(stringResource(id = R.string.ip_family_dualstack), style = labelStyle)
            Text(pluralStringResource(id = R.plurals.ip_family_connected_count, count = 0, 0), style = statusStyle)
            Text(pluralStringResource(id = R.plurals.ip_family_connecting_count, count = 0, 0), style = statusStyle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING),
            verticalAlignment = Alignment.Top,
        ) {
            for (status in statuses) {
                IpFamilyColumnView(
                    status = status,
                    tier = tiers[status.column] ?: IpFamilyTier.UNAVAILABLE,
                    labelStyle = labelStyle,
                    statusStyle = statusStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One column: the label over its status lines, all in the tier's color. The
 * lines are three slots in display order (connected, connecting,
 * disconnected); a slot fades in and out in place with the general tween and
 * a count rolls to its new value, holding its last value through the exit
 * fade so a leaving line does not roll to zero.
 */
@Composable
private fun IpFamilyColumnView(
    status: IpFamilyColumnStatus,
    tier: IpFamilyTier,
    labelStyle: TextStyle,
    statusStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = tier.color(),
        animationSpec = tween(IP_FAMILY_TWEEN_MILLIS, easing = FastOutSlowInEasing),
        label = "ipFamilyTier",
    )
    val lines = ipFamilyStatusLines(status)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LINE_SPACING),
    ) {
        Text(
            stringResource(id = status.column.labelResId()),
            style = labelStyle,
            color = color,
        )
        CountLine(
            visible = lines.any { it is IpFamilyStatusLine.Connected },
            count = status.connectedCount,
            pluralResId = R.plurals.ip_family_connected_count,
            style = statusStyle,
            color = color,
        )
        CountLine(
            visible = lines.any { it is IpFamilyStatusLine.Connecting },
            count = status.connectingCount,
            pluralResId = R.plurals.ip_family_connecting_count,
            style = statusStyle,
            color = color,
        )
        AnimatedVisibility(
            visible = lines.any { it is IpFamilyStatusLine.Disconnected },
            enter = fadeIn(tween(IP_FAMILY_TWEEN_MILLIS)),
            exit = fadeOut(tween(IP_FAMILY_TWEEN_MILLIS)),
        ) {
            Text(
                stringResource(id = R.string.ip_family_disconnected),
                style = statusStyle,
                color = color,
            )
        }
    }
}

/** A "n connected" / "n connecting" line whose count rolls to its new value. */
@Composable
private fun CountLine(
    visible: Boolean,
    count: Int,
    pluralResId: Int,
    style: TextStyle,
    color: Color,
) {
    // the last non-zero count, held through the exit fade
    val heldCount = remember { IntArray(1) { count } }
    if (visible) {
        heldCount[0] = count
    }
    val animatedCount by animateIntAsState(
        targetValue = heldCount[0],
        animationSpec = tween(IP_FAMILY_TWEEN_MILLIS, easing = FastOutSlowInEasing),
        label = "ipFamilyCount",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(IP_FAMILY_TWEEN_MILLIS)),
        exit = fadeOut(tween(IP_FAMILY_TWEEN_MILLIS)),
    ) {
        Text(
            pluralStringResource(id = pluralResId, count = animatedCount, animatedCount),
            style = style,
            color = color,
        )
    }
}

/**
 * The row for assistive tech: "Dualstack: 3 connected, 1 connecting. IPv4:
 * disconnected. IPv6: 2 connected."
 */
@Composable
private fun ipFamilyRowDescription(statuses: List<IpFamilyColumnStatus>): String {
    val description = StringBuilder()
    for (status in statuses) {
        if (description.isNotEmpty()) {
            description.append(". ")
        }
        description.append(stringResource(id = status.column.labelResId())).append(": ")
        val lines = ipFamilyStatusLines(status)
        for ((index, line) in lines.withIndex()) {
            if (0 < index) {
                description.append(", ")
            }
            description.append(
                when (line) {
                    is IpFamilyStatusLine.Connected ->
                        pluralStringResource(id = R.plurals.ip_family_connected_count, count = line.count, line.count)
                    is IpFamilyStatusLine.Connecting ->
                        pluralStringResource(id = R.plurals.ip_family_connecting_count, count = line.count, line.count)
                    IpFamilyStatusLine.Disconnected ->
                        stringResource(id = R.string.ip_family_disconnected)
                }
            )
        }
    }
    return description.toString()
}
