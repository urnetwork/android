package com.bringyour.network

internal enum class DirectBootServiceDecision {
    USE_CREDENTIAL_STATE,
    HOLD_ALWAYS_ON_GUARD,
    STOP,
}

/**
 * Decide what a VPN-service delivery may do before the user's credential
 * encrypted storage is available.
 *
 * Only Android's system-owned Always-on delivery can create the pre-unlock
 * fail-closed interface. App commands must wait for unlock because their
 * durable desired state and authentication material intentionally remain in
 * credential encrypted storage.
 */
internal fun decideDirectBootServiceStart(
    credentialStorageUnlocked: Boolean,
    stopRequested: Boolean,
    startRequested: Boolean,
    systemStart: Boolean,
    commandCompatible: Boolean,
): DirectBootServiceDecision {
    if (credentialStorageUnlocked) {
        return DirectBootServiceDecision.USE_CREDENTIAL_STATE
    }
    if (!commandCompatible || stopRequested || !startRequested || !systemStart) {
        return DirectBootServiceDecision.STOP
    }
    return DirectBootServiceDecision.HOLD_ALWAYS_ON_GUARD
}
