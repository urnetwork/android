package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PhysicalSnapshotMemoryStatsTest {
    private data class MemorySample(
        val runtimeBytes: Long,
        val carrierTotalBytes: Long,
        val carrierUsedBytes: Long,
        val carrierUsedCount: Int,
        val carrierPendingH1Count: Int,
    )

    private class Device(val sample: MemorySample)

    @Test
    fun `snapshot reads its captured device once and preserves carrier counters`() {
        val owned = MemorySample(20_000_000, 8_000_000, 2_000_000, 5, 1)
        val process = MemorySample(21_000_000, 0, 0, 0, 0)
        val device = Device(owned)
        var currentDevice: Device? = device
        val capturedDevice = currentDevice
        var deviceReads = 0
        var processReads = 0
        val actual = physicalSnapshotMemoryStats(
            capturedDevice,
            readDevice = { captured ->
                deviceReads++
                assertSame(device, captured)
                // A replacement of the app's owner during a read must not
                // redirect this snapshot or trigger a second runtime read.
                currentDevice = Device(process)
                captured.sample
            },
            readProcess = { processReads++; process },
        )
        assertEquals(1, deviceReads)
        assertEquals(0, processReads)
        assertSame(owned, actual)
        assertEquals(20_000_000L, actual.runtimeBytes)
        assertEquals(8_000_000L, actual.carrierTotalBytes)
        assertEquals(2_000_000L, actual.carrierUsedBytes)
        assertEquals(5, actual.carrierUsedCount)
        assertEquals(1, actual.carrierPendingH1Count)
        assertSame(process, currentDevice?.sample)
    }

    @Test
    fun `snapshot without a device uses exactly one process read`() {
        val process = MemorySample(12_000_000, 0, 0, 0, 0)
        var deviceReads = 0
        var processReads = 0
        val actual = physicalSnapshotMemoryStats<Device, MemorySample>(
            null,
            readDevice = { deviceReads++; it.sample },
            readProcess = { processReads++; process },
        )
        assertEquals(0, deviceReads)
        assertEquals(1, processReads)
        assertSame(process, actual)
        assertEquals(12_000_000L, actual.runtimeBytes)
        assertEquals(0L, actual.carrierTotalBytes)
        assertEquals(0L, actual.carrierUsedBytes)
        assertEquals(0, actual.carrierUsedCount)
        assertEquals(0, actual.carrierPendingH1Count)
    }

    @Test
    fun `device read failure propagates without a masking process fallback`() {
        val process = MemorySample(12_000_000, 0, 0, 0, 0)
        val device = Device(process)
        val failure = IllegalStateException("synthetic owner read failed")
        var deviceReads = 0
        var processReads = 0
        val actual = assertThrows(IllegalStateException::class.java) {
            physicalSnapshotMemoryStats(
                device,
                readDevice = { deviceReads++; throw failure },
                readProcess = { processReads++; process },
            )
        }
        assertSame(failure, actual)
        assertEquals(1, deviceReads)
        assertEquals(0, processReads)
    }
}
