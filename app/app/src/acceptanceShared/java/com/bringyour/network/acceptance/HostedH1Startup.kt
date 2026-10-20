package com.bringyour.network.acceptance

import com.bringyour.network.NetworkSpaceApiAltStartup

internal const val HOSTED_H1_POLICY_ARGUMENT = "hostedTransportPolicy"
internal const val HOSTED_H1_POLICY = "hosted-h1-api-alt-off-v1"
internal const val HOSTED_H1_SESSION_CLASS = "com.bringyour.network.acceptance.PhysicalLowbarSessionTest"

/** An explicit, exact-class launch; missing or broadened policy refuses before app startup. */
internal fun requireHostedH1Startup(argument: (String) -> String?) {
    require(argument(HOSTED_H1_POLICY_ARGUMENT) == HOSTED_H1_POLICY) { "hosted H1 policy is required" }
    require(argument("class") == HOSTED_H1_SESSION_CLASS) { "hosted H1 session class is required" }
    require(!argument("acceptanceBuildId").isNullOrBlank()) { "hosted H1 build identity is required" }
}

/** The real runner uses this order before the application can construct its manager. */
internal fun startHostedH1Application(startup: NetworkSpaceApiAltStartup, create: () -> Unit) {
    startup.disableForHostedH1()
    create()
}
