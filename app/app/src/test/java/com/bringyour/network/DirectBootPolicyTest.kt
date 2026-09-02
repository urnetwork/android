package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectBootPolicyTest {
    @Test
    fun unlockedProcessUsesOrdinaryCredentialStateForAdmission() {
        assertEquals(
            DirectBootServiceDecision.USE_CREDENTIAL_STATE,
            decideDirectBootServiceStart(
                credentialStorageUnlocked = true,
                stopRequested = true,
                startRequested = false,
                systemStart = false,
                commandCompatible = false,
            ),
        )
    }

    @Test
    fun lockedSystemOwnedStartHoldsFailClosedGuard() {
        assertEquals(
            DirectBootServiceDecision.HOLD_ALWAYS_ON_GUARD,
            decideDirectBootServiceStart(
                credentialStorageUnlocked = false,
                stopRequested = false,
                startRequested = true,
                systemStart = true,
                commandCompatible = true,
            ),
        )
    }

    @Test
    fun lockedAppOwnedStartCannotReadOrInferCredentialState() {
        assertEquals(
            DirectBootServiceDecision.STOP,
            decideDirectBootServiceStart(
                credentialStorageUnlocked = false,
                stopRequested = false,
                startRequested = true,
                systemStart = false,
                commandCompatible = true,
            ),
        )
    }

    @Test
    fun lockedStopAndIncompatibleCommandsAreRejected() {
        assertEquals(
            DirectBootServiceDecision.STOP,
            decideDirectBootServiceStart(false, true, false, true, true),
        )
        assertEquals(
            DirectBootServiceDecision.STOP,
            decideDirectBootServiceStart(false, false, true, true, false),
        )
    }
}
