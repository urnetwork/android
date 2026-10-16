package com.bringyour.network.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reload tick each widget's composition reads its entry on
 * (rememberWidgetEntry): a reload of one kind moves that kind's tick by one
 * and no other, so a running composition reads its entry again exactly
 * when its own widget is asked to re-render.
 */
class WidgetReloadsTest {

    private val kinds = listOf(WidgetKinds.DASHBOARD, WidgetKinds.PROVIDER_GLOBE, WidgetKinds.CONTRACTS)

    private fun ticks(): Map<String, Long> = kinds.associateWith { WidgetReloads.tick(it).value }

    @Test
    fun aReloadMovesOnlyItsKindsTick() {
        for (kind in kinds) {
            val before = ticks()
            WidgetReloads.bump(kind)
            val after = ticks()
            for (other in kinds) {
                val moved = if (other == kind) 1L else 0L
                assertEquals("$other after a $kind reload", before.getValue(other) + moved, after.getValue(other))
            }
        }
    }

    @Test
    fun eachReloadMovesTheTickAgain() {
        // two reloads in one session are two re-reads, not one
        val before = WidgetReloads.tick(WidgetKinds.DASHBOARD).value
        WidgetReloads.bump(WidgetKinds.DASHBOARD)
        WidgetReloads.bump(WidgetKinds.DASHBOARD)
        assertEquals(before + 2, WidgetReloads.tick(WidgetKinds.DASHBOARD).value)
    }
}
