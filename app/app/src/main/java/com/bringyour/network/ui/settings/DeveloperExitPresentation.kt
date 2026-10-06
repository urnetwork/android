package com.bringyour.network.ui.settings

/**
 * Decisions of the developer screen's exit readout, kept off the gomobile
 * `com.bringyour.sdk.Exit` class so they run in a JVM test. The exit row reads
 * the fields from the sdk's exit and passes them in.
 */

/**
 * The exit row's policy line: the built-in security rules generation the
 * exit's provider enforces (connect SecurityPolicyRulesGeneration). Connect
 * raises the number with every reviewed rules change, so an exit with a lower
 * number than the others runs a provider with older rules, which can drop
 * flows this device's rules admit.
 */
sealed interface ExitPolicyGenerationLine {
    /** the provider reported this generation */
    data class Known(val generation: Long) : ExitPolicyGenerationLine

    /**
     * the provider reported its policy without a generation: it predates the
     * generation, or it runs a custom policy
     */
    data object Unknown : ExitPolicyGenerationLine
}

/**
 * The policy line of one exit, or null before the provider's first diagnostics
 * arrive, when nothing is known about its policy yet. The sdk sends 0 for an
 * unknown generation and never a negative one.
 */
fun exitPolicyGenerationLine(
    providerDiagnosticsAvailable: Boolean,
    providerSecurityPolicyGeneration: Long,
): ExitPolicyGenerationLine? {
    if (!providerDiagnosticsAvailable) {
        return null
    }
    if (providerSecurityPolicyGeneration <= 0) {
        return ExitPolicyGenerationLine.Unknown
    }
    return ExitPolicyGenerationLine.Known(providerSecurityPolicyGeneration)
}
