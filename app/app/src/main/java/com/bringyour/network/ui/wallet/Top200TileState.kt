package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.network.ui.leaderboard.ProtocolFleet
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import com.bringyour.network.ui.leaderboard.ProtocolText
import com.bringyour.network.ui.leaderboard.ProtocolTopMe
import com.bringyour.network.ui.leaderboard.protocolClaimPrompt

/**
 * The Earnings page's Top 200 tile (apple/LEADERTOP.md §8), read from
 * GET /sn/top/me so it shows the same projected position, margin and label as
 * the leaderboard's Protocol tab. Plain data, for the JVM tests.
 */
sealed interface Top200TileState {
    // no tile: no standing yet, or the network is neither claimed nor
    // projected inside the head seats
    data object Hidden : Top200TileState

    /**
     * Asked to claim (the server's claim_prompt: measurable, inside the head
     * seats, not claimed). With
     * binding open the existing claim flow (Claim your spot, Sign fleet
     * binding); while it is closed the "Claiming opens soon" note instead,
     * and Sign fleet binding stays hidden (Q16).
     */
    data class Projected(
        val position: Long,
        val margin: Double,
        val label: ProtocolLabel?,
        val bindingOpen: Boolean,
        // close to the line (protocolClaimPrompt): the "close to the line" warning
        val borderline: Boolean,
    ) : Top200TileState

    /**
     * The network holds head spots: its fleets' UIDs, the validator's rank
     * and selection, as in the leaderboard's detail sheet.
     */
    data class Claimed(
        val position: Long,
        val margin: Double,
        val label: ProtocolLabel?,
        // by UID
        val fleets: List<ProtocolFleet>,
        // Sign fleet binding renews and extends the fleet, once binding is open
        val bindingOpen: Boolean,
        // labeled borderline: close to the line, at risk of the spot
        val nearLine: Boolean,
    ) : Top200TileState
}

/** The tile for an answer of /sn/top/me; null (not read yet, or failed) shows none. */
fun top200TileState(answer: ProtocolTopMe?): Top200TileState {
    val me = answer?.me ?: return Top200TileState.Hidden
    val row = me.row
    // /sn/top/me answers position 0 for a network without a rank: no tile, a
    // claimed fleet or not, as the Protocol card shows no rank for it
    if (row.position <= 0L) {
        return Top200TileState.Hidden
    }
    val bindingOpen = answer.snapshot?.bindingOpen == true
    if (row.fleets.isNotEmpty()) {
        return Top200TileState.Claimed(
            position = row.position,
            margin = row.margin,
            label = row.label,
            fleets = row.fleets.sortedBy { it.uid },
            bindingOpen = bindingOpen,
            nearLine = row.label == ProtocolLabel.BORDERLINE,
        )
    }
    // the server's claim_prompt, as on the Protocol card: no tile where the card prompts nothing
    val prompt = protocolClaimPrompt(row, answer.snapshot) ?: return Top200TileState.Hidden
    return Top200TileState.Projected(
        position = row.position,
        margin = row.margin,
        label = row.label,
        bindingOpen = bindingOpen,
        borderline = prompt.borderline,
    )
}

/** "projected · +12%": the tile's line beside the position, the margin to the Top 200 line with its sign. */
fun top200ProjectedText(margin: Double, text: ProtocolText): String =
    text.string(R.string.earnings_top200_projected, text.signedPercent(margin))
