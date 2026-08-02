package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundWorkOwnerTest {
    @Test
    fun foregroundStartsWorkExactlyOnce() {
        var starts = 0
        val owner = ForegroundWorkOwner(start = { starts += 1 }, stop = {})

        owner.setForeground(true)
        owner.setForeground(true)

        assertEquals(1, starts)
    }

    @Test
    fun backgroundStopsWorkExactlyOnce() {
        var stops = 0
        val owner = ForegroundWorkOwner(start = {}, stop = { stops += 1 })
        owner.setForeground(true)

        owner.setForeground(false)
        owner.setForeground(false)

        assertEquals(1, stops)
    }

    @Test
    fun foregroundResumeRestartsWork() {
        var starts = 0
        var stops = 0
        val owner = ForegroundWorkOwner(
            start = { starts += 1 },
            stop = { stops += 1 },
        )

        owner.setForeground(true)
        owner.setForeground(false)
        owner.setForeground(true)

        assertEquals(2, starts)
        assertEquals(1, stops)
    }

    @Test
    fun closeStopsOnlyActiveWork() {
        var stops = 0
        val owner = ForegroundWorkOwner(start = {}, stop = { stops += 1 })
        owner.setForeground(true)

        owner.close()
        owner.close()

        assertEquals(1, stops)
    }
}
