package com.bringyour.network.widgets

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.bringyour.network.R
import com.bringyour.network.utils.formatBitRate
import com.bringyour.network.widgets.render.ContractStackRenderer

/**
 * The top client contracts as a flowing grid: one compact card per peer,
 * each with its send stack and its receive stack (contracts are never
 * paired: a peer's send and receive contracts are many-to-many, so each is
 * its own circle). Cards are laid out left to right and wrap until the
 * widget is full; whatever does not fit is left out, most relevant peers
 * first — the SDK's contract row order.
 *
 * Glance has no measuring flow layout, so rows are packed here from each
 * card's estimated width (the id and rate text measured with the widget's
 * density, the stacks from their circle counts) against the widget's width.
 */
class ContractsWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(220.dp, 110.dp)
        val LARGE = DpSize(220.dp, 220.dp)
    }

    /**
     * Exact, not responsive: the flow packs cards against the widget's real
     * width and height, and in responsive mode a composition only sees its
     * size bucket (on the phone that left half of a 4x2 empty). The cost is
     * a recomposition per resize, which is fine for a static grid.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetEntry.load(context)
        provideContent { ContractsContent(entry) }
    }
}

private const val ID_LENGTH = 8
private const val CARD_PADDING_DP = 7f
private const val CARD_SPACING_DP = 6f
private const val STACK_GAP_DP = 8f
private const val HEADER_HEIGHT_DP = 14f
private const val CARD_INNER_SPACING_DP = 4f

private class CardLayout(
    val peer: WidgetContractPeerSnapshot,
    val widthDp: Float,
    val heightDp: Float,
    val rate: String,
)

@Composable
internal fun ContractsContent(entry: WidgetEntry) {
    val size = LocalSize.current
    val context = LocalContext.current
    val compact = size.width < ContractsWidget.MEDIUM.width
    val peers = if (entry.showsTunnelData) entry.tunnel.contracts else emptyList()
    val style = ContractStackRenderer.Style(context.resources.displayMetrics.density, compact)
    WidgetSurface(entry, com.bringyour.network.QuickConnectActivity.ROUTE_CONTRACT_STATS) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(R.string.site_app_contracts), style = WidgetTheme.title)
                Spacer(GlanceModifier.defaultWeight())
                if (peers.isNotEmpty()) {
                    val rate = peers.sumOf { it.bitRate }
                    Text(
                        if (0 < rate) formatBitRate(rate)
                        else context.resources.getQuantityString(R.plurals.widget_peer_count, peers.size, peers.size),
                        style = WidgetTheme.label,
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            if (peers.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        context.getString(
                            when {
                                !entry.isConfigured -> R.string.widget_open_to_set_up
                                !entry.isOn -> R.string.widget_connect_to_see_contracts
                                else -> R.string.widget_no_contracts
                            }
                        ),
                        style = WidgetTheme.faint,
                    )
                }
            } else {
                val cards = peers.map { layout(it, style, compact) }
                val availableWidth = size.width.value - 28f
                val availableHeight = size.height.value - 28f - 14f - 6f
                val rows = packRows(cards, availableWidth, availableHeight)
                rows.forEachIndexed { index, row ->
                    if (0 < index) Spacer(GlanceModifier.height(CARD_SPACING_DP.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        row.forEachIndexed { cardIndex, card ->
                            if (0 < cardIndex) Spacer(GlanceModifier.width(CARD_SPACING_DP.dp))
                            PeerCard(card, style)
                        }
                    }
                }
            }
        }
    }
}

private val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
private val ratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }

private fun layout(peer: WidgetContractPeerSnapshot, style: ContractStackRenderer.Style, compact: Boolean): CardLayout {
    val density = style.density
    idPaint.textSize = 11f * density
    ratePaint.textSize = 10f * density
    val rate = if (0 < peer.bitRate) formatBitRate(peer.bitRate) else ""
    val headerWidth = idPaint.measureText(peer.id.take(ID_LENGTH)) / density +
        (if (rate.isEmpty()) 0f else 6f + ratePaint.measureText(rate) / density)
    val stacksWidth = (ContractStackRenderer.width(peer.send.size, style) + ContractStackRenderer.width(peer.receive.size, style)) / density + STACK_GAP_DP
    val stackHeight = ContractStackRenderer.height(style) / density
    return CardLayout(
        peer = peer,
        widthDp = maxOf(headerWidth, stacksWidth) + 2 * CARD_PADDING_DP + 2f,
        heightDp = HEADER_HEIGHT_DP + CARD_INNER_SPACING_DP + stackHeight + 2 * CARD_PADDING_DP + (if (compact) 0f else 2f),
        rate = rate,
    )
}

/** Left to right, wrapping, stopping at the bottom edge: cards that would start below it are left out. */
private fun packRows(cards: List<CardLayout>, availableWidth: Float, availableHeight: Float): List<List<CardLayout>> {
    val rows = ArrayList<List<CardLayout>>()
    var row = ArrayList<CardLayout>()
    var x = 0f
    var y = 0f
    var rowHeight = 0f
    for (card in cards) {
        if (row.isNotEmpty() && availableWidth < x + card.widthDp) {
            rows.add(row)
            row = ArrayList()
            y += rowHeight + CARD_SPACING_DP
            x = 0f
            rowHeight = 0f
        }
        if (availableHeight < y + card.heightDp) break
        if (row.isEmpty() && availableWidth < card.widthDp) {
            // a card wider than the widget still gets a row; it clips at the edge
        }
        row.add(card)
        x += card.widthDp + CARD_SPACING_DP
        rowHeight = maxOf(rowHeight, card.heightDp)
    }
    if (row.isNotEmpty()) rows.add(row)
    return rows
}

@Composable
private fun PeerCard(card: CardLayout, style: ContractStackRenderer.Style) {
    val context = LocalContext.current
    val peer = card.peer
    Column(
        modifier = GlanceModifier
            .cornerRadius(8.dp)
            .background(WidgetTheme.card)
            .padding(horizontal = CARD_PADDING_DP.dp, vertical = (CARD_PADDING_DP - 2f).dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(peer.id.take(ID_LENGTH), style = WidgetTheme.mono, maxLines = 1)
            if (card.rate.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(card.rate, style = WidgetTheme.label, maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(CARD_INNER_SPACING_DP.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(ContractStackRenderer.render(peer.send, WidgetTheme.sendStackArgb, pointsRight = true, style = style)),
                contentDescription = context.resources.getQuantityString(
                    R.plurals.widget_contract_stack_accessibility, peer.send.size, peer.send.size,
                    com.bringyour.network.utils.formatByteCountCompact(peer.sendByteCount),
                ),
            )
            Spacer(GlanceModifier.width(STACK_GAP_DP.dp))
            Image(
                provider = ImageProvider(ContractStackRenderer.render(peer.receive, WidgetTheme.receiveStackArgb, pointsRight = false, style = style)),
                contentDescription = context.resources.getQuantityString(
                    R.plurals.widget_contract_stack_accessibility, peer.receive.size, peer.receive.size,
                    com.bringyour.network.utils.formatByteCountCompact(peer.receiveByteCount),
                ),
            )
        }
    }
}
