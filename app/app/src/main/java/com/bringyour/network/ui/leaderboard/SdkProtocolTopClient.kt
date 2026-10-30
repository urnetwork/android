package com.bringyour.network.ui.leaderboard

import android.util.Log
import com.bringyour.sdk.Api
import com.bringyour.sdk.SetPointsLeaderboardPublicArgs
import com.bringyour.sdk.SnTopArgs
import com.bringyour.sdk.SnTopFleet
import com.bringyour.sdk.SnTopMe
import com.bringyour.sdk.SnTopMeResult
import com.bringyour.sdk.SnTopRow
import com.bringyour.sdk.SnTopSnapshot
import com.bringyour.sdk.Time

private const val TAG = "ProtocolLeaderboard"

/** A call the server answered with its error, whose message the tab never shows. */
private class ProtocolTopException(message: String) : Exception(message)

/**
 * ProtocolTopClient over one device's api: sdk SnTop and SnTopMe, and the
 * network's points_leaderboard_public flag (GetNetworkLeaderboardRanking,
 * SetPointsLeaderboardPublic), which the Points tab's switch sets too.
 *
 * Each answer is copied off its gomobile objects on the sdk thread it arrives
 * on, inside runCatching: an exception thrown out of a gomobile callback
 * cannot cross JNI and aborts the process. Only errors are logged, by their
 * message.
 */
internal class SdkProtocolTopClient(
    private val api: Api,
) : ProtocolTopClient {

    // the device's api always holds the network's client jwt
    override val signedIn: Boolean = true

    override fun top(limit: Int, cursor: String, snapshotId: String, done: (Result<ProtocolTopPage>) -> Unit) {
        val args = SnTopArgs()
        args.limit = limit.toLong()
        args.cursor = cursor
        args.filter = PROTOCOL_TOP_FILTER_ALL
        args.snapshot = snapshotId
        api.snTop(args) { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    checkNotNull(result) { "no answer" }
                    result.error?.let { throw ProtocolTopException(it.message.orEmpty()) }
                    // the server sends a snapshot without an id before the board's
                    // first one; no snapshot at all reads the same: no rankings yet
                    val snapshot = result.snapshot?.let(::protocolSnapshotOf) ?: protocolNoSnapshot()
                    val list = result.rows
                    ProtocolTopPage(
                        snapshot = snapshot,
                        rows = (0L until (list?.len() ?: 0L)).mapNotNull { i ->
                            list?.get(i)?.let(::protocolRowOf)
                        },
                        me = result.me?.let(::protocolRowOf),
                        next = result.next?.ifEmpty { null },
                    )
                }.onFailure { Log.i(TAG, "sn top failed: ${it.message}") }
            )
        }
    }

    override fun me(done: (Result<ProtocolTopMe>) -> Unit) {
        api.snTopMe { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    protocolTopMeOf(checkNotNull(result) { "no answer" })
                }.onFailure { Log.i(TAG, "sn top me failed: ${it.message}") }
            )
        }
    }

    override fun namePublic(done: (Result<Boolean>) -> Unit) {
        api.getNetworkLeaderboardRanking { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    checkNotNull(result) { "no answer" }
                    result.error?.let { throw ProtocolTopException(it.message.orEmpty()) }
                    checkNotNull(result.networkRanking) { "no ranking" }.pointsLeaderboardPublic
                }.onFailure { Log.i(TAG, "leaderboard ranking failed: ${it.message}") }
            )
        }
    }

    override fun setNamePublic(public: Boolean, done: (Result<Unit>) -> Unit) {
        val args = SetPointsLeaderboardPublicArgs()
        args.public = public
        api.setPointsLeaderboardPublic(args) { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    checkNotNull(result) { "no answer" }
                    result.error?.let { throw ProtocolTopException(it.message.orEmpty()) }
                    Unit
                }.onFailure { Log.i(TAG, "set points leaderboard public failed: ${it.message}") }
            )
        }
    }
}

// the sdk's SnTopFilterAll: every network, claimed or not
private const val PROTOCOL_TOP_FILTER_ALL = "all"

/** The caller's standing from an SnTopMeResult; throws on the server's error. */
internal fun protocolTopMeOf(result: SnTopMeResult): ProtocolTopMe {
    result.error?.let { throw ProtocolTopException(it.message.orEmpty()) }
    return ProtocolTopMe(
        snapshot = result.snapshot?.let(::protocolSnapshotOf),
        me = result.me?.let(::protocolMeOf),
    )
}

// a zero time is no time
private fun millisOf(time: Time?): Long? = time?.unixMilli()?.takeIf { 0L < it }

internal fun protocolSnapshotOf(snapshot: SnTopSnapshot): ProtocolSnapshot = ProtocolSnapshot(
    id = snapshot.id.orEmpty(),
    tempoIndex = snapshot.tempoIndex,
    computedAtMillis = millisOf(snapshot.computedAt),
    totalRanked = snapshot.totalRanked,
    headSeats = snapshot.headSeats,
    claimedFleets = snapshot.claimedFleets,
    openSeats = snapshot.openSeats,
    projectedCutoff = snapshot.projectedCutoff,
    bindingOpen = snapshot.bindingOpen,
    stale = snapshot.stale,
)

internal fun protocolFleetOf(fleet: SnTopFleet): ProtocolFleet = ProtocolFleet(
    uid = fleet.uid,
    generation = fleet.generation,
    validToEpoch = fleet.validToEpoch,
    replicaScore = fleet.replicaScore,
    validatorScore = fleet.validatorScore.takeIf { fleet.hasValidatorScore },
    validatorRank = fleet.validatorRank.takeIf { fleet.hasValidatorRank },
    validatorRankOf = fleet.validatorRankOf.takeIf { fleet.hasValidatorRankOf },
    selected = fleet.selected.takeIf { fleet.hasSelected },
    incentive = fleet.incentive.takeIf { fleet.hasIncentive },
    incentiveStale = fleet.incentiveStale,
)

internal fun protocolRowOf(row: SnTopRow): ProtocolRow {
    val fleets = row.fleets
    return ProtocolRow(
        position = row.position,
        name = row.name.orEmpty(),
        tag = row.tag.orEmpty(),
        projectedScore = row.projectedScore,
        margin = row.margin,
        trend = row.trend,
        label = ProtocolLabel.fromWire(row.label.orEmpty()),
        measurable = row.measurable,
        claimPrompt = row.claimPrompt,
        isMe = row.isMe,
        fleets = (0L until (fleets?.len() ?: 0L)).mapNotNull { i ->
            fleets?.get(i)?.let(::protocolFleetOf)
        },
    )
}

internal fun protocolMeOf(me: SnTopMe): ProtocolMe? {
    val row = me.row?.let(::protocolRowOf) ?: return null
    val history = me.history
    return ProtocolMe(
        row = row,
        soloScore = me.soloScore,
        distinctPrefixes = me.distinctPrefixes,
        sharedPrefixes = me.sharedPrefixes,
        sharedWithUnits = me.sharedWithUnits,
        notMeasurableReason = ProtocolNotMeasurableReason.fromWire(me.notMeasurableReason.orEmpty()),
        history = (0L until (history?.len() ?: 0L)).mapNotNull { i ->
            history?.get(i)?.let { point ->
                ProtocolHistoryPoint(day = point.day.orEmpty(), rank = point.rank, score = point.score)
            }
        },
    )
}
