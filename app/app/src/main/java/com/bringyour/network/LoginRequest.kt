package com.bringyour.network

import com.bringyour.sdk.Api
import com.bringyour.sdk.LocalState
import com.bringyour.sdk.NetworkSpace
import java.util.concurrent.atomic.AtomicBoolean

/** One initiating request owns its exact API/store through every result and UI queue. */
class LoginRequest internal constructor(
    internal val attemptId: Long,
    internal val networkSpace: NetworkSpace,
    val api: Api,
    internal val localState: LocalState,
) {
    internal val resultConsumed = AtomicBoolean(false)
    internal val sessionStarted = AtomicBoolean(false)
}
