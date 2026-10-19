package com.bringyour.network.acceptance

import android.os.Bundle
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EgressProbeRequestTest {
    @Test
    fun workerStackModeIsRequestScopedDefaultOffAndKeepsCallbackIdentity() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) = Unit
        }
        val ordinary = EgressProbeRequest.requestIntent(testContext, receiver, "synthetic-nonce")
        val diagnostic = EgressProbeRequest.requestIntent(
            testContext, receiver, "synthetic-nonce", diagnosticWorkerStacks = true,
        )
        assertFalse(ordinary.hasExtra(EgressProbeActivity.EXTRA_DIAGNOSTIC_WORKER_STACKS))
        assertTrue(diagnostic.getBooleanExtra(EgressProbeActivity.EXTRA_DIAGNOSTIC_WORKER_STACKS, false))
        for (intent in listOf(ordinary, diagnostic)) {
            assertEquals(testContext.packageName, intent.component?.packageName)
            assertEquals(EgressProbeActivity::class.java.name, intent.component?.className)
            assertEquals("synthetic-nonce", intent.getStringExtra(EgressProbeActivity.EXTRA_REQUEST_NONCE))
            assertTrue(intent.getBooleanExtra(EgressProbeActivity.EXTRA_FINISH_AFTER_RESULT, false))
            @Suppress("DEPRECATION")
            val retained = intent.getParcelableExtra<ResultReceiver>(EgressProbeActivity.EXTRA_RESULT_RECEIVER)
            assertEquals(receiver, retained)
        }
    }

    @Test
    fun workerStackModeDoesNotChangeFixedResultOrTrafficRequests() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val receiver = object : ResultReceiver(null) {}
        val fixed = EgressProbeRequest.requestIntent(
            testContext, receiver, "synthetic-nonce", fixedResult = "ACCEPTANCE_IP=203.0.113.7",
            diagnosticWorkerStacks = true,
        )
        val traffic = EgressProbeRequest.requestIntent(
            testContext, receiver, "synthetic-nonce", trafficBytes = 256, diagnosticWorkerStacks = true,
        )
        assertFalse(fixed.hasExtra(EgressProbeActivity.EXTRA_DIAGNOSTIC_WORKER_STACKS))
        assertFalse(traffic.hasExtra(EgressProbeActivity.EXTRA_DIAGNOSTIC_WORKER_STACKS))
        assertEquals("ACCEPTANCE_IP=203.0.113.7", fixed.getStringExtra(EgressProbeActivity.EXTRA_FIXED_RESULT))
        assertEquals(256L, traffic.getLongExtra(EgressProbeActivity.EXTRA_TRAFFIC_BYTES, 0))
    }

    @Test
    fun resultArrivesDirectlyFromTheSeparateTestUid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        val response = EgressProbeRequest.execute(
            instrumentation = instrumentation,
            timeoutMillis = 10_000,
            fixedResult = "ACCEPTANCE_IP=203.0.113.7",
        )

        assertEquals("ACCEPTANCE_IP=203.0.113.7", response.message)
        assertEquals(instrumentation.context.installedPackageUid(), response.sourceUid)
        assertNotEquals(instrumentation.targetContext.installedPackageUid(), response.sourceUid)
    }
}
