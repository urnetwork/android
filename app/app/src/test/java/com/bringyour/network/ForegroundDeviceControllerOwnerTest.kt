package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundDeviceControllerOwnerTest {
    @Test
    fun backgroundDeviceDoesNotOpenController() {
        var openCount = 0
        val owner = ForegroundDeviceControllerOwner<String, String>(
            open = {
                openCount += 1
                "controller"
            },
            close = { _, _ -> },
        )

        owner.setDevice("device")

        assertEquals(0, openCount)
        assertNull(owner.controller)
    }

    @Test
    fun foregroundTransitionOpensExactlyOnce() {
        var openCount = 0
        val owner = ForegroundDeviceControllerOwner<String, String>(
            open = {
                openCount += 1
                "controller"
            },
            close = { _, _ -> },
        )
        owner.setDevice("device")

        owner.setForeground(true)
        owner.setForeground(true)

        assertEquals(1, openCount)
        assertEquals("controller", owner.controller)
    }

    @Test
    fun backgroundTransitionClosesThroughOpeningDevice() {
        val closes = mutableListOf<Pair<String, String>>()
        val owner = ForegroundDeviceControllerOwner<String, String>(
            open = { "controller-$it" },
            close = { device, controller -> closes.add(Pair(device, controller)) },
        )
        owner.setDevice("old-device")
        owner.setForeground(true)

        owner.setForeground(false)

        assertEquals(listOf(Pair("old-device", "controller-old-device")), closes)
        assertNull(owner.controller)
    }

    @Test
    fun foregroundDeviceReplacementClosesOldBeforeOpeningNew() {
        val events = mutableListOf<String>()
        val owner = ForegroundDeviceControllerOwner<String, String>(
            open = {
                events.add("open:$it")
                "controller-$it"
            },
            close = { device, controller -> events.add("close:$device:$controller") },
        )
        owner.setDevice("old")
        owner.setForeground(true)

        owner.setDevice("new")

        assertEquals(
            listOf(
                "open:old",
                "close:old:controller-old",
                "open:new",
            ),
            events,
        )
    }

    @Test
    fun backgroundReplacementWaitsForNextForeground() {
        val events = mutableListOf<String>()
        val owner = ForegroundDeviceControllerOwner<String, String>(
            open = {
                events.add("open:$it")
                "controller-$it"
            },
            close = { device, _ -> events.add("close:$device") },
        )
        owner.setDevice("old")
        owner.setForeground(true)
        owner.setForeground(false)

        owner.setDevice("new")

        assertEquals(listOf("open:old", "close:old"), events)
        assertNull(owner.controller)

        owner.setForeground(true)
        assertEquals(listOf("open:old", "close:old", "open:new"), events)
    }
}
