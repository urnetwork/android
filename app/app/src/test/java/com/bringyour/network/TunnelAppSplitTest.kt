package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelAppSplitTest {
    @Test
    fun selfOnlyAllowlistFallsBackToEmptyIncludedSet() {
        val split = sanitizeTunnelAppSplit(
            "com.bringyour.network",
            setOf("com.bringyour.network"),
            emptySet(),
        )

        assertEquals(emptySet<String>(), split.first)
        assertEquals(emptySet<String>(), split.second)
    }

    @Test
    fun selfIsRemovedWithoutDroppingOtherIncludedApps() {
        val split = sanitizeTunnelAppSplit(
            "com.bringyour.network",
            setOf("com.bringyour.network", "com.example.browser"),
            emptySet(),
        )

        assertEquals(setOf("com.example.browser"), split.first)
    }

    @Test
    fun selfAndBlankIdsAreRemovedFromBothDirections() {
        val split = sanitizeTunnelAppSplit(
            "com.bringyour.network",
            setOf("", "com.example.remote"),
            setOf(" ", "com.bringyour.network", "com.example.local"),
        )

        assertEquals(setOf("com.example.remote"), split.first)
        assertEquals(setOf("com.example.local"), split.second)
    }

    @Test
    fun uninstalledOnlyAllowlistFallsBackToEmptyIncludedSet() {
        val split = sanitizeTunnelAppSplit(
            "com.bringyour.network",
            setOf("com.example.uninstalled"),
            emptySet(),
            isPackageInstalled = { false },
        )

        assertEquals(emptySet<String>(), split.first)
    }

    @Test
    fun installedAllowlistEntriesSurviveUninstalledStaleEntries() {
        val split = sanitizeTunnelAppSplit(
            "com.bringyour.network",
            setOf("com.example.browser", "com.example.uninstalled"),
            emptySet(),
            isPackageInstalled = { it == "com.example.browser" },
        )

        assertEquals(setOf("com.example.browser"), split.first)
    }
}
