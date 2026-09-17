package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileMemoryPolicyTest {
    @Test
    fun ordinaryAndroidRetainsItsLargerBudget() {
        assertEquals(28L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount("android"))
        assertEquals(40L, MainApplication.processMemoryLimitMib("android"))
    }

    @Test
    fun iosAuditMatchesTheExtensionAdmissionAndSoftLimit() {
        val profile = MainApplication.IOS_MEMORY_AUDIT_PROFILE
        assertEquals(20L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount(profile))
        assertEquals(32L, MainApplication.processMemoryLimitMib(profile))
    }

    @Test
    fun selectedBuildProfileControlsBothMemoryInputs() {
        val profile = MainApplication.MEMORY_PROFILE_NAME
        assertEquals(DeviceManager.deviceMemoryTargetByteCount(profile), DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT)
        assertEquals(MainApplication.processMemoryLimitMib(profile), MainApplication.SDK_PROCESS_MEMORY_LIMIT_MIB)
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
