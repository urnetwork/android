package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleDeviceControllerOwnerTest {

    private val events = mutableListOf<String>()

    private fun owner() = VisibleDeviceControllerOwner<String, String>(
        open = {
            events.add("open:$it")
            "controller-$it"
        },
        close = { device, controller -> events.add("close:$device:$controller") },
        start = { events.add("start:$it") },
        stop = { events.add("stop:$it") },
    )

    private fun shownOwner(): VisibleDeviceControllerOwner<String, String> {
        val owner = owner()
        owner.setDevice("device")
        owner.setForeground(true)
        owner.setEnabled(true)
        owner.setVisible(true)
        events.clear()
        return owner
    }

    @Test
    fun neverOpensWhileDisabled() {
        val owner = owner()
        owner.setDevice("device")
        owner.setForeground(true)
        owner.setVisible(true)

        assertEquals(listOf<String>(), events)
        assertNull(owner.controller)
    }

    @Test
    fun opensWhenEnabledAndStartsOnlyWhileVisible() {
        val owner = owner()
        owner.setDevice("device")
        owner.setForeground(true)

        owner.setEnabled(true)
        assertEquals(listOf("open:device"), events)

        owner.setVisible(true)
        owner.setVisible(true)
        assertEquals(listOf("open:device", "start:controller-device"), events)
    }

    @Test
    fun aHiddenScreenStopsWithoutClosing() {
        val owner = shownOwner()

        owner.setVisible(false)
        assertEquals(listOf("stop:controller-device"), events)
        assertEquals("controller-device", owner.controller)

        // the same controller, with its last snapshot, starts again
        owner.setVisible(true)
        assertEquals(listOf("stop:controller-device", "start:controller-device"), events)
    }

    @Test
    fun disablingClosesTheController() {
        val owner = shownOwner()

        owner.setEnabled(false)

        assertEquals(listOf("close:device:controller-device"), events)
        assertNull(owner.controller)
    }

    @Test
    fun theBackgroundClosesAndTheForegroundReopens() {
        val owner = shownOwner()

        owner.setForeground(false)
        assertEquals(listOf("close:device:controller-device"), events)

        owner.setForeground(true)
        assertEquals(
            listOf("close:device:controller-device", "open:device", "start:controller-device"),
            events,
        )
    }

    @Test
    fun aReplacedDeviceClosesTheOldControllerBeforeStartingTheNew() {
        val owner = shownOwner()

        owner.setDevice("new")

        assertEquals(
            listOf("close:device:controller-device", "open:new", "start:controller-new"),
            events,
        )
    }

    @Test
    fun closeTearsDownForGood() {
        val owner = shownOwner()

        owner.close()
        owner.setDevice("new")

        assertEquals(listOf("close:device:controller-device"), events)
        assertNull(owner.controller)
    }
}
