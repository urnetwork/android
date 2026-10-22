package com.bringyour.network.acceptance

import com.bringyour.network.NetworkSpaceApiAltStartup

internal const val HOSTED_H1_POLICY_ARGUMENT = "hostedTransportPolicy"
internal const val HOSTED_H1_POLICY = "hosted-h1-api-alt-off-v1"
internal const val HOSTED_H1_SCOPED_POLICY = "hosted-h1-api-alt-off-scoped-v2"
internal const val HOSTED_H1_SCOPE_ARGUMENT = "qualificationScope"
internal const val HOSTED_H1_QUALIFICATION_SCOPE = "hosted-h1-dual-memory-v1"
internal const val HOSTED_H1_SESSION_CLASS = "com.bringyour.network.acceptance.PhysicalLowbarSessionTest"

/** An explicit, exact-class launch; missing or broadened policy refuses before app startup. */
internal fun requireHostedH1Startup(argument: (String) -> String?) {
    val policy = argument(HOSTED_H1_POLICY_ARGUMENT).orEmpty()
    require(policy == HOSTED_H1_POLICY || policy == HOSTED_H1_SCOPED_POLICY) { "hosted H1 policy is required" }
    requireHostedH1Scope(policy, argument(HOSTED_H1_SCOPE_ARGUMENT))
    if (policy == HOSTED_H1_SCOPED_POLICY) {
        require(argument("ioLoopDiagnostic") == null) { "scoped H1 cannot enable diagnostic commands" }
    }
    require(argument("class") == HOSTED_H1_SESSION_CLASS) { "hosted H1 session class is required" }
    require(!argument("acceptanceBuildId").isNullOrBlank()) { "hosted H1 build identity is required" }
}

/** The real runner uses this order before the application can construct its manager. */
internal fun startHostedH1Application(startup: NetworkSpaceApiAltStartup, create: () -> Unit) {
    startup.disableForHostedH1()
    create()
}

/** Historical and ordinary launches cannot relabel themselves with the new scoped eligibility. */
internal fun requireHostedH1Scope(policy: String, scope: String?) {
    when (policy) {
        "", HOSTED_H1_POLICY -> require(scope == null) { "qualification scope requires its exact hosted policy" }
        HOSTED_H1_SCOPED_POLICY -> require(scope == HOSTED_H1_QUALIFICATION_SCOPE) { "scoped hosted H1 policy is required" }
        else -> error("unknown hosted physical transport policy")
    }
}

/** Fail before command effects; the scoped workload never enables a provider, other carrier or forced maintenance. */
internal fun requireScopedHostedH1Command(policy: String, scope: String?, verb: String, argument: String) {
    requireHostedH1Scope(policy, scope)
    if (policy != HOSTED_H1_SCOPED_POLICY) return
    require(when (verb) {
        "phase" -> true
        "connect" -> argument == "h1"
        "disconnect", "snapshot", "finish" -> argument.isEmpty()
        else -> false
    }) { "command is outside scoped hosted H1 qualification" }
}
