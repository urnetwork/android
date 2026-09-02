package com.bringyour.network.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EgressProbeRequestTest {
    @Test
    fun resultArrivesDirectlyFromTheSeparateTestUid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        val response = EgressProbeRequest.execute(
            instrumentation = instrumentation,
            timeoutMillis = 10_000,
            fixedResult = "ACCEPTANCE_IP=203.0.113.7",
        )

        assertEquals("ACCEPTANCE_IP=203.0.113.7", response.message)
        assertEquals(instrumentation.context.applicationInfo.uid, response.sourceUid)
        assertNotEquals(instrumentation.targetContext.applicationInfo.uid, response.sourceUid)
    }
}
