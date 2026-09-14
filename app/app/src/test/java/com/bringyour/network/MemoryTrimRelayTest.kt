package com.bringyour.network

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class MemoryTrimRelayTest {
    private val platformLevels = listOf(
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
    )

    @Test
    fun platformLevelsMatchTheSdkMapping() {
        // The SDK's mapping (memory_trim_level.go) is keyed on these values.
        assertEquals(listOf(5, 10, 15, 20, 40, 60, 80), platformLevels)
    }

    @Test
    fun everyTrimLevelReachesTheBridgeUnchanged() {
        val reported = mutableListOf<Long>()
        val relay = MemoryTrimRelay(sdkReady = { true }, report = { reported += it })
        platformLevels.forEach(relay::onTrimMemory)
        assertEquals(platformLevels.map { it.toLong() }, reported)
    }

    @Test
    fun lowMemoryReportsTrimMemoryComplete() {
        val reported = mutableListOf<Long>()
        val relay = MemoryTrimRelay(sdkReady = { true }, report = { reported += it })
        relay.onLowMemory()
        assertEquals(listOf(ComponentCallbacks2.TRIM_MEMORY_COMPLETE.toLong()), reported)
    }

    @Test
    fun nothingReachesTheBridgeBeforeTheSdkIsInitialized() {
        var ready = false
        val reported = mutableListOf<Long>()
        val relay = MemoryTrimRelay(sdkReady = { ready }, report = { reported += it })
        relay.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        relay.onLowMemory()
        assertEquals(emptyList<Long>(), reported)
        ready = true
        relay.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertEquals(listOf(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND.toLong()), reported)
    }
}
