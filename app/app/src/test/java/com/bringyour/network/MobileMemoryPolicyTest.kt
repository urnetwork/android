package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileMemoryPolicyTest {
    @Test
    fun deviceUsesTwentyFourMiBSteadyTarget() {
        assertEquals(24L * 1024 * 1024, DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT)
    }
}
