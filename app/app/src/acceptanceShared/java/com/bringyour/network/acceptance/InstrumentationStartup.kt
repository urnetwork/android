package com.bringyour.network.acceptance

/**
 * The instrumentation arguments that opt a run into the app's live startup:
 * acceptance (the harness passes acceptanceBuildId to every case it drives) and
 * the startup-memory diagnostic (AndroidMemoryStartupTest).
 */
internal val LIVE_STARTUP_ARGUMENTS = listOf("acceptanceBuildId", "urnetworkMemoryStartupCheck")

/**
 * Whether an instrumentation run keeps the app's live startup, read from its
 * arguments. Every other run starts the app with the SDK's extender network
 * client off (ExtenderNetworkClientOffRunner): the bundled bringyour.com space
 * the app installs would otherwise resolve and dial the public DoH servers and
 * production as soon as it is built.
 */
internal fun keepsLiveStartup(argument: (String) -> String?): Boolean =
    LIVE_STARTUP_ARGUMENTS.any { argument(it) != null }
