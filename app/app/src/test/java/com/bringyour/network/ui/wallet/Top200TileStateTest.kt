package com.bringyour.network.ui.wallet

import com.bringyour.network.ui.leaderboard.ProtocolFleet
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import com.bringyour.network.ui.leaderboard.ProtocolMe
import com.bringyour.network.ui.leaderboard.ProtocolNotMeasurableReason
import com.bringyour.network.ui.leaderboard.ProtocolRow
import com.bringyour.network.ui.leaderboard.ProtocolSnapshot
import com.bringyour.network.ui.leaderboard.ProtocolTopMe
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Earnings page's Top 200 tile from GET /sn/top/me (apple/LEADERTOP.md
 * §8): when it shows, with the claim flow or the "Claiming opens soon" note,
 * and the claimed state.
 */
class Top200TileStateTest {

    private fun snapshot(bindingOpen: Boolean) = ProtocolSnapshot(
        id = "snap-1",
        tempoIndex = 7,
        computedAtMillis = null,
        totalRanked = 41_230,
        headSeats = 200,
        claimedFleets = 3,
        openSeats = 197,
        projectedCutoff = 41.2,
        bindingOpen = bindingOpen,
        stale = false,
    )

    private fun fleet(uid: Long) = ProtocolFleet(
        uid = uid,
        generation = 2,
        validToEpoch = 16,
        replicaScore = 41.9,
        validatorScore = null,
        validatorRank = null,
        validatorRankOf = null,
        selected = null,
        incentive = null,
        incentiveStale = false,
    )

    private fun answer(
        position: Long,
        bindingOpen: Boolean = false,
        label: ProtocolLabel? = ProtocolLabel.ESTIMATE,
        measurable: Boolean = true,
        fleets: List<ProtocolFleet> = listOf(),
    ) = ProtocolTopMe(
        snapshot = snapshot(bindingOpen),
        me = ProtocolMe(
            row = ProtocolRow(
                position = position,
                name = "",
                tag = "",
                projectedScore = 52.4,
                margin = (200.0 - position) / 200.0,
                trend = 0,
                label = label,
                measurable = measurable,
                claimPrompt = measurable && position <= 200,
                isMe = true,
                fleets = fleets,
            ),
            soloScore = 61.0,
            distinctPrefixes = 58,
            sharedPrefixes = 4,
            sharedWithUnits = 3,
            notMeasurableReason = ProtocolNotMeasurableReason.NONE,
            history = listOf(),
        ),
    )

    @Test
    fun noStandingShowsNoTile() {
        assertEquals(Top200TileState.Hidden, top200TileState(null))
        assertEquals(Top200TileState.Hidden, top200TileState(ProtocolTopMe(snapshot = snapshot(false), me = null)))
    }

    @Test
    fun projectedInsideWithBindingClosedIsTheOpensSoonTile() {
        assertEquals(
            Top200TileState.Projected(position = 143, margin = 0.285, label = ProtocolLabel.ESTIMATE, bindingOpen = false, borderline = false),
            top200TileState(answer(143)),
        )
    }

    @Test
    fun projectedInsideWithBindingOpenIsTheClaimFlow() {
        assertEquals(
            Top200TileState.Projected(position = 196, margin = 0.02, label = ProtocolLabel.BORDERLINE, bindingOpen = true, borderline = true),
            top200TileState(answer(196, bindingOpen = true, label = ProtocolLabel.BORDERLINE)),
        )
    }

    @Test
    fun whileEveryRowReadsEstimateANetworkNearTheLineIsWarned() {
        // the Protocol card's rule: within 20% of the line is close (Q13, Q17)
        val near = top200TileState(answer(185, bindingOpen = true))
        assertEquals(true, (near as Top200TileState.Projected).borderline)
    }

    @Test
    fun outsideTheHeadSeatsOrNotMeasurableShowsNoTile() {
        assertEquals(Top200TileState.Hidden, top200TileState(answer(201)))
        assertEquals(Top200TileState.Hidden, top200TileState(answer(120, measurable = false, label = ProtocolLabel.NOT_MEASURABLE)))
        // measurable by the flag but labeled not measurable: no claim call
        assertEquals(Top200TileState.Hidden, top200TileState(answer(120, label = ProtocolLabel.NOT_MEASURABLE)))
    }

    @Test
    fun aClaimedNetworkShowsItsFleetsWhereverItRanks() {
        val state = top200TileState(answer(230, label = ProtocolLabel.UNLIKELY, fleets = listOf(fleet(212), fleet(81))))
        assertEquals(
            Top200TileState.Claimed(
                position = 230,
                margin = -0.15,
                label = ProtocolLabel.UNLIKELY,
                fleets = listOf(fleet(81), fleet(212)),
                bindingOpen = false,
                nearLine = false,
            ),
            state,
        )
        val near = top200TileState(answer(190, bindingOpen = true, label = ProtocolLabel.BORDERLINE, fleets = listOf(fleet(81))))
        assertEquals(true, (near as Top200TileState.Claimed).nearLine)
        assertEquals(true, near.bindingOpen)
    }
}
