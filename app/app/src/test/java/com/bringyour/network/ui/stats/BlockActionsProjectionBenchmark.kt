package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional host-only benchmark: URNETWORK_PROJECTION_BENCHMARK=1. This compares
 * the old full-projection tick with the new exit-only tick. SDK getters and
 * hostname collapse are counted callbacks, not fake device/JNI timing results.
 */
class BlockActionsProjectionBenchmark {
    @Volatile private var sink: List<BlockActionUi> = emptyList()

    @Test
    fun unchangedRowTicks() {
        assumeTrue(System.getenv("URNETWORK_PROJECTION_BENCHMARK") == "1")
        val threadBean = Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean").invoke(null)
        val allocatedBytes = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
        val threadId = Thread.currentThread().id
        fun allocated() = allocatedBytes.invoke(threadBean, threadId) as Long
        for (rowCount in listOf(1, 256)) {
            val input = List(rowCount) { index ->
                BlockActionUi(
                    id = "row-$index", timeMillis = 1,
                    hosts = listOf("a.same-site.test", "same-site.test"),
                    ips = listOf("192.0.2.1"), matchedHosts = emptyList(),
                    matchedIps = listOf("192.0.2.2"), hostBaseNames = emptyList(),
                    block = false, local = false, hasBlockOverride = false,
                    hasRouteOverride = false, overrideId = null, byteCount = 10,
                )
            }
            val exits = mapOf("192.0.2.1" to setOf("one"), "192.0.2.2" to setOf("two"))
            val displayNames = listOf("*.same-site.test")
            var rowReads = 0
            var collapseCalls = 0
            val readRows = { rowReads++; input }
            val collapse: (List<String>) -> List<String> = { collapseCalls++; displayNames }
            val projection = BlockActionsProjection(readRows, { exits }, collapse)
            projection.refreshRows()
            val legacy = {
                readRows().map { row ->
                    row.copy(
                        hostBaseNames = collapse(row.hosts),
                        exitShortIds = (row.matchedIps + row.ips)
                            .flatMap { exits[it] ?: emptySet() }.distinct().sorted(),
                    )
                }
            }
            val candidate = { projection.refreshExits() }
            assertEquals(legacy(), candidate())
            repeat(2_000) { sink = legacy(); sink = candidate() }
            repeat(6) { repetition ->
                val arms = if (repetition % 2 == 0) listOf("legacy", "candidate") else listOf("candidate", "legacy")
                for (arm in arms) {
                    val tick = if (arm == "legacy") legacy else candidate
                    val readsBefore = rowReads
                    val callsBefore = collapseCalls
                    val bytesBefore = allocated()
                    val start = System.nanoTime()
                    val iterations = 2_000
                    repeat(iterations) { sink = tick() }
                    val nanos = System.nanoTime() - start
                    val bytes = allocated() - bytesBefore
                    val reads = rowReads - readsBefore
                    val calls = collapseCalls - callsBefore
                    assertEquals(if (arm == "legacy") iterations else 0, reads)
                    assertEquals(if (arm == "legacy") iterations * rowCount else 0, calls)
                    println("{\"rows\":$rowCount,\"arm\":\"$arm\",\"rep\":${repetition + 1},\"nsPerTick\":${nanos.toDouble() / iterations},\"bytesPerTick\":${bytes.toDouble() / iterations},\"rowReads\":$reads,\"collapseCalls\":$calls}")
                }
            }
        }
    }
}
