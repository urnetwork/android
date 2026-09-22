package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status row's counting, ranking and line selection: which providers
 * count in which column, which column is bright, and which lines a column
 * shows. Mirrors the apple IpFamilyStatusRowTests.
 */
class IpFamilyStatusRowTest {

    private fun point(ipFamily: String, state: String = "Added") =
        IpFamilyPoint(state = state, ipFamily = ipFamily)

    private fun status(column: IpFamilyColumn, connected: Int = 0, connecting: Int = 0) =
        IpFamilyColumnStatus(column = column, connectedCount = connected, connectingCount = connecting)

    // counting

    @Test
    fun columnsAreAlwaysPresentInDisplayOrder() {
        val statuses = ipFamilyColumnStatuses(listOf())
        assertEquals(
            listOf(IpFamilyColumn.DUALSTACK, IpFamilyColumn.V4, IpFamilyColumn.V6),
            statuses.map { it.column },
        )
        assertTrue(statuses.all { it.isUnavailable })
    }

    // The columns are categories, not capabilities: a dualstack provider
    // counts once, under Dualstack, never under IPv4 or IPv6 as well.
    @Test
    fun countsConnectedAndConnectingByCategory() {
        val statuses = ipFamilyColumnStatuses(
            listOf(
                point("dualstack"),
                point("dualstack"),
                point("dualstack", state = "InEvaluation"),
                point("v4-only"),
                point("v6-only", state = "InEvaluation"),
                point("v6-only", state = "InEvaluation"),
            )
        )
        assertEquals(status(IpFamilyColumn.DUALSTACK, connected = 2, connecting = 1), statuses[0])
        assertEquals(status(IpFamilyColumn.V4, connected = 1), statuses[1])
        assertEquals(status(IpFamilyColumn.V6, connecting = 2), statuses[2])
    }

    // A provider that failed evaluation, was not added, or is on its way out
    // (it lingers on the grid for the removal tween) counts as nothing.
    @Test
    fun ignoresProvidersThatAreNotLive() {
        val statuses = ipFamilyColumnStatuses(
            listOf(
                point("dualstack", state = "EvaluationFailed"),
                point("v4-only", state = "NotAdded"),
                point("v6-only", state = "Removed"),
                point("v6-only", state = "something-newer"),
            )
        )
        assertTrue(statuses.all { it.isUnavailable })
    }

    // A legacy or unknown category carries v4, so it is an IPv4 provider
    // rather than one that vanishes from the row.
    @Test
    fun legacyAndUnknownCategoriesReadAsV4() {
        val statuses = ipFamilyColumnStatuses(
            listOf(
                point(""),
                point("something-newer", state = "InEvaluation"),
            )
        )
        assertEquals(status(IpFamilyColumn.V4, connected = 1, connecting = 1), statuses[1])
        assertEquals(IpFamilyColumn.V4, IpFamilyColumn.fromFamily(""))
        assertEquals(IpFamilyColumn.V4, IpFamilyColumn.fromFamily("anything"))
    }

    // The provider rows tag a provider by the sdk's short label ("both", "v4",
    // "v6"); legacy and unknown labels read as v4, like the categories.
    @Test
    fun tagLabelsMapToColumns() {
        assertEquals(IpFamilyColumn.DUALSTACK, IpFamilyColumn.fromLabel("both"))
        assertEquals(IpFamilyColumn.V4, IpFamilyColumn.fromLabel("v4"))
        assertEquals(IpFamilyColumn.V6, IpFamilyColumn.fromLabel("v6"))
        assertEquals(IpFamilyColumn.V4, IpFamilyColumn.fromLabel(""))
    }

    // ranking

    @Test
    fun dualstackConnectedIsBestAndTheOthersAreDimmed() {
        val tiers = ipFamilyColumnTiers(
            listOf(
                status(IpFamilyColumn.DUALSTACK, connected = 1),
                status(IpFamilyColumn.V4, connected = 3),
                status(IpFamilyColumn.V6, connecting = 1),
            )
        )
        assertEquals(IpFamilyTier.BEST, tiers[IpFamilyColumn.DUALSTACK])
        assertEquals(IpFamilyTier.ACTIVE, tiers[IpFamilyColumn.V4])
        assertEquals(IpFamilyTier.ACTIVE, tiers[IpFamilyColumn.V6])
    }

    // IPv4 and IPv6 tie, so with nothing dualstack connected they share the
    // top.
    @Test
    fun v4AndV6ShareBestWhenNothingDualstackIsConnected() {
        val tiers = ipFamilyColumnTiers(
            listOf(
                status(IpFamilyColumn.DUALSTACK),
                status(IpFamilyColumn.V4, connected = 2),
                status(IpFamilyColumn.V6, connected = 1),
            )
        )
        assertEquals(IpFamilyTier.UNAVAILABLE, tiers[IpFamilyColumn.DUALSTACK])
        assertEquals(IpFamilyTier.BEST, tiers[IpFamilyColumn.V4])
        assertEquals(IpFamilyTier.BEST, tiers[IpFamilyColumn.V6])
    }

    @Test
    fun aLoneConnectedColumnIsBest() {
        val tiers = ipFamilyColumnTiers(
            listOf(
                status(IpFamilyColumn.DUALSTACK),
                status(IpFamilyColumn.V4),
                status(IpFamilyColumn.V6, connected = 1),
            )
        )
        assertEquals(IpFamilyTier.UNAVAILABLE, tiers[IpFamilyColumn.DUALSTACK])
        assertEquals(IpFamilyTier.UNAVAILABLE, tiers[IpFamilyColumn.V4])
        assertEquals(IpFamilyTier.BEST, tiers[IpFamilyColumn.V6])
    }

    // A column that is only connecting carries no traffic yet: it is active,
    // never best, even when it outranks the connected column.
    @Test
    fun aConnectingOnlyColumnIsActiveNotBest() {
        val tiers = ipFamilyColumnTiers(
            listOf(
                status(IpFamilyColumn.DUALSTACK, connecting = 2),
                status(IpFamilyColumn.V4, connected = 1),
                status(IpFamilyColumn.V6),
            )
        )
        assertEquals(IpFamilyTier.ACTIVE, tiers[IpFamilyColumn.DUALSTACK])
        assertEquals(IpFamilyTier.BEST, tiers[IpFamilyColumn.V4])
        assertEquals(IpFamilyTier.UNAVAILABLE, tiers[IpFamilyColumn.V6])
    }

    @Test
    fun onlyConnectingColumnsMakeNothingBest() {
        val tiers = ipFamilyColumnTiers(
            listOf(
                status(IpFamilyColumn.DUALSTACK, connecting = 1),
                status(IpFamilyColumn.V4, connecting = 1),
                status(IpFamilyColumn.V6),
            )
        )
        assertEquals(IpFamilyTier.ACTIVE, tiers[IpFamilyColumn.DUALSTACK])
        assertEquals(IpFamilyTier.ACTIVE, tiers[IpFamilyColumn.V4])
        assertEquals(IpFamilyTier.UNAVAILABLE, tiers[IpFamilyColumn.V6])
    }

    @Test
    fun nothingLiveMakesEveryColumnUnavailable() {
        val tiers = ipFamilyColumnTiers(ipFamilyColumnStatuses(listOf()))
        assertEquals(3, tiers.size)
        assertTrue(tiers.values.all { it == IpFamilyTier.UNAVAILABLE })
    }

    // lines

    @Test
    fun linesShowTheNonZeroCountsConnectedFirst() {
        assertEquals(
            listOf(IpFamilyStatusLine.Connected(3), IpFamilyStatusLine.Connecting(1)),
            ipFamilyStatusLines(status(IpFamilyColumn.V4, connected = 3, connecting = 1)),
        )
        assertEquals(
            listOf(IpFamilyStatusLine.Connected(3)),
            ipFamilyStatusLines(status(IpFamilyColumn.V4, connected = 3)),
        )
        assertEquals(
            listOf(IpFamilyStatusLine.Connecting(1)),
            ipFamilyStatusLines(status(IpFamilyColumn.V4, connecting = 1)),
        )
    }

    @Test
    fun aColumnWithNothingReadsDisconnected() {
        assertEquals(
            listOf(IpFamilyStatusLine.Disconnected),
            ipFamilyStatusLines(status(IpFamilyColumn.V6)),
        )
    }

    // The line kind is its identity, so a count change is an in-place update
    // (the number rolls) while a kind change is an insertion or removal (the
    // line fades).
    @Test
    fun lineIdentityIsTheKind() {
        assertEquals(IpFamilyStatusLine.Connected(1).kind, IpFamilyStatusLine.Connected(2).kind)
        assertNotEquals(IpFamilyStatusLine.Connected(1).kind, IpFamilyStatusLine.Connecting(1).kind)
        assertNotEquals(IpFamilyStatusLine.Connected(1), IpFamilyStatusLine.Connected(2))
    }
}
