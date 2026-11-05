package com.bringyour.network.acceptance

/** Private additive physical epoch only; scoped MEMSTEADY remains unchanged. */
internal fun requirePrivateRelayOnly(argument: String?, hostedPolicy: String): Boolean {
    require(argument == null || argument == "true") { "invalid privateRelayOnly argument" }
    require(argument == null || hostedPolicy.isEmpty()) { "private relay cannot relabel hosted qualification" }
    return argument == "true"
}

/** Retire NAT provider before settings; the platform client persists and H1 migration
 * is asynchronous. Positive H1/zero-other-carrier traffic evidence remains required. */
internal fun configurePrivateRelayOnly(
    enabled: Boolean,
    retireProvider: () -> Unit,
    disableDirect: () -> Unit,
    setClientH1: () -> Unit,
    setProviderH1: () -> Unit,
    verifyH1: () -> Unit,
) {
    if (!enabled) return
    retireProvider()
    disableDirect()
    setClientH1()
    setProviderH1()
    verifyH1()
}

/** Read once; peer-list churn can never replace this operation's private identity. */
internal fun capturePrivateRelayPin(readPin: () -> String?): String {
    val pin = readPin()?.trim()
    require(pin != null && pin.matches(Regex("[A-Za-z0-9._-]+"))) { "private relay requires exact peer pin" }
    return pin
}

internal fun privateRelayPeerMatches(expected: String?, candidate: String?): Boolean =
    expected == null || expected == candidate

internal fun requirePrivateRelayPeerMode(networkPeer: Boolean, mode: String) {
    require(networkPeer && mode == "h1") { "private relay requires exact network peer H1" }
}
