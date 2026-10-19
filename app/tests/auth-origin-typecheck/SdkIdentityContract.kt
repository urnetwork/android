package com.bringyour.network

import com.bringyour.sdk.NetworkSpace

/** Compiled only: real SDK binding signatures, never invoked or substituted. */
internal fun sdkIdentityContract(space: NetworkSpace, other: NetworkSpace?): LoginRequest? {
    if (!space.same(other)) return null
    val api = space.api ?: return null
    val state = space.asyncLocalState?.localState ?: return null
    val keys = state.readDeviceLocalKeyMaterial()
    val keyMaterial = keys.keyMaterial
    return LoginRequest(1, space, api, state)
}
