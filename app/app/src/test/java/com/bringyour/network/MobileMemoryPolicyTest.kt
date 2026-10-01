package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileMemoryPolicyTest {
    @Test
    fun ordinaryAndroidUsesItsFullSelectedAllowance() {
        assertEquals(64L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount("android"))
        assertEquals(64L, MainApplication.processMemoryLimitMib("android"))
    }

    @Test
    fun iosAuditMatchesTheExtensionAdmissionAndSoftLimit() {
        val profile = MainApplication.IOS_MEMORY_AUDIT_PROFILE
        assertEquals("ios-memory-audit-v2", profile)
        assertEquals(32L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount(profile))
        assertEquals(32L, MainApplication.processMemoryLimitMib(profile))
    }

    @Test
    fun androidEffectiveLimitRetainsTheMemoryClassClamp() {
        // Cover the exact integer crossover, the unchanged fallback, and
        // larger classes without assuming any physical device's memoryClass.
        val cases = listOf(
            null to 24L,
            0 to 24L,
            -1 to 24L,
            32 to 24L,
            48 to 36L,
            64 to 48L,
            85 to 63L,
            86 to 64L,
            128 to 64L,
            256 to 64L,
            512 to 64L,
            Int.MAX_VALUE to 64L,
        )
        for ((memoryClass, expected) in cases) {
            assertEquals(
                "Android memoryClass=$memoryClass",
                expected,
                MainApplication.effectiveProcessMemoryLimitMib("android", memoryClass),
            )
            assertEquals(
                "DeviceLocal must use the same effective allowance for memoryClass=$memoryClass",
                expected * 1024 * 1024,
                DeviceManager.effectiveDeviceMemoryTargetByteCount("android", expected),
            )
        }
        assertEquals(64L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount("android"))
    }

    @Test
    fun iosAuditEffectiveLimitRemainsIndependentOfAndroidCap() {
        val profile = MainApplication.IOS_MEMORY_AUDIT_PROFILE
        for ((memoryClass, expected) in listOf(null to 24L, 32 to 24L, 42 to 31L, 43 to 32L, 64 to 32L, 256 to 32L)) {
            assertEquals(
                "iOS audit memoryClass=$memoryClass",
                expected,
                MainApplication.effectiveProcessMemoryLimitMib(profile, memoryClass),
            )
            assertEquals(expected * 1024 * 1024, DeviceManager.effectiveDeviceMemoryTargetByteCount(profile, expected))
        }
        assertEquals(32L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount(profile))
    }

    @Test
    fun legacyAuditProfileRetainsItsOwnMeaning() {
        val profile = MainApplication.LEGACY_IOS_MEMORY_AUDIT_PROFILE
        assertEquals("ios-memory-audit-v1", profile)
        assertEquals(20L * 1024 * 1024, DeviceManager.deviceMemoryTargetByteCount(profile))
        assertEquals(32L, MainApplication.processMemoryLimitMib(profile))
        assertEquals(20L * 1024 * 1024, DeviceManager.effectiveDeviceMemoryTargetByteCount(profile, 32))
    }

    @Test
    fun selectedBuildProfileControlsBothMemoryInputs() {
        val profile = MainApplication.MEMORY_PROFILE_NAME
        assertEquals(
            DeviceManager.effectiveDeviceMemoryTargetByteCount(profile, MainApplication.SDK_EFFECTIVE_PROCESS_MEMORY_LIMIT_MIB),
            DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT,
        )
        assertEquals(MainApplication.processMemoryLimitMib(profile), MainApplication.SDK_PROCESS_MEMORY_LIMIT_MIB)
    }

    @Test
    fun deviceTargetNeverExceedsTheAppliedProcessAllowance() {
        assertTrue(
            DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT <=
                MainApplication.SDK_EFFECTIVE_PROCESS_MEMORY_LIMIT_MIB * 1024 * 1024,
        )
    }
}
