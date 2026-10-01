package com.bringyour.network.acceptance

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.BuildConfig
import com.bringyour.network.DeviceManager
import com.bringyour.network.MainApplication
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

// No activities, credentials, authentication or DeviceLocal construction.
// The external owner must bind its adb invocation to the approved device;
// an app cannot authenticate the host's adb serial selector.
@RunWith(AndroidJUnit4::class)
class AndroidMemoryStartupTest {
    @Test
    fun selectedProfileAppliesMemoryClassBoundAndFullTarget() {
        val optIn = InstrumentationRegistry.getArguments().getString("urnetworkMemoryStartupCheck")
        assumeTrue("explicit startup-memory diagnostic only", optIn != null)
        assertEquals("startup-memory opt-in", "1", optIn)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("debug app required", BuildConfig.DEBUG)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        assertTrue("normal application startup required", context.applicationContext is MainApplication)
        val profile = BuildConfig.URNETWORK_MEMORY_PROFILE
        assertTrue("explicit supported profile required", profile in setOf("android", "ios-memory-audit-v1", "ios-memory-audit-v2"))
        assertEquals(profile, MainApplication.MEMORY_PROFILE_NAME)
        val configuredCapMib = if (profile == "android") 64L else 32L
        assertEquals(configuredCapMib, MainApplication.SDK_PROCESS_MEMORY_LIMIT_MIB)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClassMib = activityManager.memoryClass
        assertTrue("positive ActivityManager memory class required", memoryClassMib > 0)
        // Independent expectation, not a second call to the policy helper.
        val expectedLimitBytes = minOf(3L * memoryClassMib / 4L, configuredCapMib) * 1024L * 1024L
        val actualLimitBytes = Sdk.getMemoryStats().memoryLimitByteCount
        val configuredTargetBytes = DeviceManager.DEVICE_MEMORY_TARGET_BYTE_COUNT
        assertEquals(expectedLimitBytes, actualLimitBytes)
        val expectedTargetBytes = if (profile == "ios-memory-audit-v1") minOf(20L * 1024 * 1024, expectedLimitBytes) else expectedLimitBytes
        assertEquals(expectedTargetBytes, configuredTargetBytes)
        assertEquals(expectedLimitBytes, MainApplication.SDK_EFFECTIVE_PROCESS_MEMORY_LIMIT_MIB * 1024 * 1024)

        instrumentation.sendStatus(0, Bundle().apply {
            putString("memoryProfile", profile)
            putInt("memoryClassMib", memoryClassMib)
            putLong("configuredProcessCapMib", configuredCapMib)
            putLong("expectedGoMemoryLimitBytes", expectedLimitBytes)
            putLong("actualGoMemoryLimitBytes", actualLimitBytes)
            putLong("configuredDeviceTargetBytes", configuredTargetBytes)
        })
    }
}
