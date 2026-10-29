package com.bringyour.network.screenshots

import com.bringyour.network.ui.leaderboard.ProtocolFleet
import com.bringyour.network.ui.leaderboard.ProtocolHistoryPoint
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import com.bringyour.network.ui.leaderboard.ProtocolMe
import com.bringyour.network.ui.leaderboard.ProtocolNotMeasurableReason
import com.bringyour.network.ui.leaderboard.ProtocolRow
import com.bringyour.network.ui.leaderboard.ProtocolSnapshot
import com.bringyour.network.ui.leaderboard.ProtocolTopClient
import com.bringyour.network.ui.leaderboard.ProtocolTopMe
import com.bringyour.network.ui.leaderboard.ProtocolTopPage
import java.time.LocalDate

/**
 * A synthetic Protocol board for the screenshot tests, the same on every run:
 * 1,000 ranked networks with the caller at #173, an anonymous claimed network
 * at #1 and #202, a few the validator can't measure, and binding closed. It
 * answers each call at once, as a fast sdk would; with `answers` false it
 * never answers, so the tab stays on its placeholders. Every name is made up.
 */
internal class SampleProtocolTopClient(
    private val bindingOpen: Boolean = false,
    private val mePosition: Long = 173,
    private val meLabel: ProtocolLabel = ProtocolLabel.ESTIMATE,
    private val answers: Boolean = true,
) : ProtocolTopClient {

    override val signedIn: Boolean = true

    private val snapshot = ProtocolSnapshot(
        id = "sample-snapshot",
        tempoIndex = 7,
        computedAtMillis = 1_799_000_000_000L,
        totalRanked = 1_000,
        headSeats = 200,
        claimedFleets = 2,
        openSeats = 198,
        projectedCutoff = 41.2,
        bindingOpen = bindingOpen,
        stale = false,
    )

    private val names = listOf("stellar-mesh", "quiet-harbor", "northwind-7", "lumen-relay", "cedar-node", "ridgeline-net")

    private fun fleet(uid: Long) = ProtocolFleet(
        uid = uid,
        generation = 2,
        validToEpoch = 16,
        replicaScore = 312.4,
        validatorScore = 312.4,
        validatorRank = 1,
        validatorRankOf = 142,
        selected = true,
        incentive = 0.018,
        incentiveStale = true,
    )

    // the projected score falls from 312.4 at the top to the line's 41.2 at 200
    private fun score(position: Long): Double = 41.2 * Math.pow(312.4 / 41.2, (200.0 - position) / 199.0)

    private fun row(position: Long): ProtocolRow {
        val isMe = position == mePosition
        val claimed = position == 1L || position == 202L
        val measurable = position % 7L != 4L || isMe
        val named = position % 3L == 2L && !claimed
        return ProtocolRow(
            position = position,
            name = if (named) names[(position % names.size).toInt()] else "",
            // no emoji tags: the JVM's fonts draw them differently from a device's
            tag = "",
            projectedScore = score(position),
            margin = (score(position) - 41.2) / 41.2,
            trend = when (position % 4L) {
                0L -> 1L
                1L -> -2L
                else -> 0L
            },
            label = when {
                !measurable -> ProtocolLabel.NOT_MEASURABLE
                isMe -> meLabel
                else -> ProtocolLabel.ESTIMATE
            },
            measurable = measurable,
            claimPrompt = measurable && position <= 200,
            isMe = isMe,
            fleets = if (claimed) listOf(fleet(if (position == 1L) 81 else 77)) else listOf(),
        )
    }

    override fun top(limit: Int, cursor: String, snapshotId: String, done: (Result<ProtocolTopPage>) -> Unit) {
        if (!answers) {
            return
        }
        val first = (cursor.toLongOrNull() ?: 0L) + 1L
        val last = minOf(first + limit - 1, 400L)
        done(
            Result.success(
                ProtocolTopPage(
                    snapshot = snapshot,
                    rows = (first..last).map(::row),
                    me = row(mePosition),
                    next = if (last < 400L) "$last" else null,
                )
            )
        )
    }

    override fun me(done: (Result<ProtocolTopMe>) -> Unit) {
        if (!answers) {
            return
        }
        // a fixed 90 days, from #412 to today's position
        val end = LocalDate.of(2026, 10, 28)
        val history = (0 until 90).map { day ->
            val rank = 412L - (412L - mePosition) * day / 89L + if (day % 9 == 4) 6L else 0L
            ProtocolHistoryPoint(day = end.minusDays((89 - day).toLong()).toString(), rank = rank, score = score(rank))
        }
        done(
            Result.success(
                ProtocolTopMe(
                    snapshot = snapshot,
                    me = ProtocolMe(
                        row = row(mePosition).copy(trend = 9),
                        soloScore = 61.0,
                        distinctPrefixes = 18,
                        sharedPrefixes = 3,
                        sharedWithUnits = 2,
                        notMeasurableReason = ProtocolNotMeasurableReason.NONE,
                        history = history,
                    ),
                )
            )
        )
    }

    override fun namePublic(done: (Result<Boolean>) -> Unit) {
        if (answers) {
            done(Result.success(false))
        }
    }

    override fun setNamePublic(public: Boolean, done: (Result<Unit>) -> Unit) {
        if (answers) {
            done(Result.success(Unit))
        }
    }
}
