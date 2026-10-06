package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAutoSaveTest {
    @Test
    fun `auto save is switched on exactly once with true`() {
        val calls = mutableListOf<Boolean>()
        val failures = mutableListOf<Throwable>()

        val enabled = enableDeviceAutoSave(
            setAutoSave = { calls += it },
            onFailure = { failures += it },
        )

        assertTrue(enabled)
        assertEquals(listOf(true), calls)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a failing sdk call is reported with its cause and not thrown`() {
        val cause = IllegalStateException("sdk rejected auto save")
        val failures = mutableListOf<Throwable>()

        val enabled = enableDeviceAutoSave(
            setAutoSave = { throw cause },
            onFailure = { failures += it },
        )

        assertFalse(enabled)
        assertEquals(1, failures.size)
        assertSame(cause, failures.single())
    }

    @Test
    fun `an auto save failure does not fail device configuration`() {
        var closes = 0
        var failureReports = 0

        val result = configureCreatedDevice(
            configure = {
                enableDeviceAutoSave(
                    setAutoSave = { throw IllegalStateException("sdk rejected auto save") },
                    onFailure = { failureReports += 1 },
                )
                "configured"
            },
            closePartialState = { closes += 1 },
        )

        assertEquals(DeviceConfigurationResult.Configured("configured"), result)
        assertEquals(1, failureReports)
        assertEquals(0, closes)
    }
}
