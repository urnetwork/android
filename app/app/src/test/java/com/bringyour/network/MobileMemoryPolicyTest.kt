package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileMemoryPolicyTest {
    @Test
    fun deviceUsesTwentyEightMiBTarget() {
        assertEquals(28L * 1024 * 1024, DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT)
    }

    @Test
    fun processSoftLimitIsFortyMiB() {
        assertEquals(40L, MainApplication.SDK_PROCESS_MEMORY_LIMIT_MIB)
    }

    @Test
    fun processSoftLimitStaysAboveTheDeviceTarget() {
        // the soft limit is an emergency boundary above the target, never the
        // target itself: the sdk's live set plus runtime amplification must fit
        assertTrue(
            DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT <
                MainApplication.SDK_PROCESS_MEMORY_LIMIT_MIB * 1024 * 1024,
        )
    }
}
