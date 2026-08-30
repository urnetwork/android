package com.bringyour.network.acceptance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EgressProbeResultTest {
    @Test
    fun pendingSentinelIsNotACompletedProbe() {
        val result = EgressProbeResult.terminalText

        assertFalse(result.matcher("ACCEPTANCE_IP_PENDING").matches())
        assertTrue(result.matcher("ACCEPTANCE_IP=203.0.113.7").matches())
        assertTrue(result.matcher("ACCEPTANCE_IP=2001:db8::7").matches())
        assertTrue(result.matcher("ACCEPTANCE_ERROR=SocketTimeoutException:timeout").matches())
    }
}
