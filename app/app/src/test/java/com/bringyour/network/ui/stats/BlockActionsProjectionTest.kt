package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class BlockActionsProjectionTest {
    private fun row(id: String = "row", host: String = "a.same-site.test") = BlockActionUi(
        id = id, timeMillis = 1, hosts = listOf(host), ips = listOf("192.0.2.1"),
        matchedHosts = listOf("exact.test"), matchedIps = listOf("192.0.2.2"),
        hostBaseNames = emptyList(), block = false, local = false,
        hasBlockOverride = false, hasRouteOverride = false, overrideId = null, byteCount = 10,
    )

    private class Fixture(initialRows: List<BlockActionUi>) {
        var input = initialRows
        var exits: Map<String, Set<String>> = emptyMap()
        var reads = 0
        var exitReads = 0
        var collapses = 0
        val projection = BlockActionsProjection(
            readRows = { reads++; input },
            readExits = { exitReads++; exits },
            collapseHosts = { collapses++; listOf("collapsed:${it.joinToString()}") },
        )
    }

    @Test
    fun exitTicksNeverReadRowsOrCollapseHostsButRefreshTheLiveJoin() {
        val fixture = Fixture(listOf(row()))
        val original = fixture.projection.refreshRows().single()
        repeat(12) { tick ->
            fixture.exits = mapOf(
                "192.0.2.1" to setOf("exit-$tick", "shared"),
                "192.0.2.2" to setOf("matched", "shared"),
            )
            val refreshed = fixture.projection.refreshExits().single()
            assertEquals(listOf("exit-$tick", "matched", "shared"), refreshed.exitShortIds)
            assertSame(original.hostBaseNames, refreshed.hostBaseNames)
            assertSame(original.hosts, refreshed.hosts)
            assertSame(original.matchedHosts, refreshed.matchedHosts)
            assertEquals(original.byteCount, refreshed.byteCount)
        }
        fixture.exits = emptyMap()
        assertEquals(emptyList<String>(), fixture.projection.refreshExits().single().exitShortIds)
        assertEquals(1, fixture.reads)
        assertEquals(1, fixture.collapses)
        assertEquals(14, fixture.exitReads)
    }

    @Test
    fun unchangedExitsReuseRowsAndList() {
        val fixture = Fixture(listOf(row()))
        fixture.exits = mapOf("192.0.2.1" to setOf("exit"))
        val first = fixture.projection.refreshRows()
        repeat(12) { assertSame(first, fixture.projection.refreshExits()) }
        assertEquals(1, fixture.collapses)
    }

    @Test
    fun rowUpdatesReuseOnlyUnchangedHostnameInputs() {
        val fixture = Fixture(listOf(row(), row("other", "b.other.test")))
        val initial = fixture.projection.refreshRows()
        fixture.input = listOf(
            row().copy(byteCount = 123, timeMillis = 999, block = true, local = true),
            row("other", "new.other.test"),
        )
        val next = fixture.projection.refreshRows()
        assertSame(initial[0].hostBaseNames, next[0].hostBaseNames)
        assertEquals(123L, next[0].byteCount)
        assertEquals(999L, next[0].timeMillis)
        assertEquals(true, next[0].block)
        assertEquals(true, next[0].local)
        assertNotSame(initial[1].hostBaseNames, next[1].hostBaseNames)
        assertEquals(listOf("collapsed:new.other.test"), next[1].hostBaseNames)
        assertEquals(3, fixture.collapses)
    }

    @Test
    fun removedRowsAndReplacedDeviceDoNotRetainMemoEntries() {
        val fixture = Fixture(listOf(row()))
        fixture.projection.refreshRows()
        fixture.input = emptyList()
        fixture.projection.refreshRows()
        val exitReads = fixture.exitReads
        repeat(12) { assertEquals(emptyList<BlockActionUi>(), fixture.projection.refreshExits()) }
        assertEquals(exitReads, fixture.exitReads)
        fixture.input = listOf(row())
        fixture.projection.refreshRows()
        assertEquals(2, fixture.collapses)
        fixture.projection.clear()
        assertEquals(emptyList<BlockActionUi>(), fixture.projection.refreshExits())
        fixture.projection.refreshRows()
        assertEquals(3, fixture.collapses)
    }

    @Test
    fun matchedAndUnmatchedIpsFollowUpdatesWithoutChangingHostMemo() {
        val fixture = Fixture(listOf(row()))
        fixture.exits = mapOf("192.0.2.1" to setOf("old"), "192.0.2.3" to setOf("new"))
        fixture.projection.refreshRows()
        fixture.input = listOf(row().copy(ips = listOf("192.0.2.3"), matchedIps = emptyList()))
        assertEquals(listOf("new"), fixture.projection.refreshRows().single().exitShortIds)
        assertEquals(1, fixture.collapses)
    }
}
