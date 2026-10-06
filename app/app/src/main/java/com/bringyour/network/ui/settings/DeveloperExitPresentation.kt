package com.bringyour.network.ui.settings

import androidx.annotation.StringRes
import com.bringyour.network.R
import com.bringyour.sdk.Sdk

/**
 * Decisions of the developer screen's exit readout, kept off the gomobile
 * `com.bringyour.sdk.Exit` class so they run in a JVM test. The exit row reads
 * the fields from the sdk's exit and passes them in.
 *
 * Every word the readout shows is a string resource, as on the Windows and
 * Linux developer pages (the dev_state_* keys), except the tokens the sdk
 * itself names (a window type, a warning cause), which show as the sdk spells
 * them so a new one renders without an app update.
 */

/** The exit fields the state line reads. */
data class ExitStateFields(
    /** the sdk's window type token: "quality", "speed", or "" for none */
    val windowType: String,
    val tier: Int,
    val effectiveTier: Int,
    val quarantined: Boolean,
    val warning: Boolean,
    /** the sdk's warning cause token, or "" */
    val warningCause: String,
    val done: Boolean,
    val p2pOnly: Boolean,
    val proven: Boolean,
)

/** One part of the exit row's state line. */
sealed interface ExitStateText {
    /** a string resource and its format arguments */
    data class Resource(@StringRes val id: Int, val formatArgs: List<Any> = emptyList()) : ExitStateText

    /** a token the sdk names, shown verbatim */
    data class SdkToken(val token: String) : ExitStateText
}

/**
 * The state line's parts in display order, joined with " · " by the row.
 *
 * - The window type, the sdk's own token; an exit with none reads as the sdk's
 *   auto window type.
 * - The tier: the platform's rank for this provider. Only the best rank present
 *   is raced until it is at the flow cap, so a tier above the minimum with 0
 *   flows is a spare, not a failure. effectiveTier is the rank selection
 *   actually uses (tier plus live demerits); when it is greater the exit is
 *   demoted and "N→M" makes that visible.
 * - The warning state, by name. benched is a quarantine (a soft verdict held
 *   against a loaded exit: it stops taking new placements while its flows keep
 *   running, and receive progress acquits it); otherwise the resize pass's
 *   cause, verbatim: draining (healthy, retiring), starved (upstream failing
 *   dials), or unhealthy (a verdict demoted or deferred). Before the cause
 *   existed every one of these displayed as "draining", which made benches read
 *   as retirements.
 * - done and p2p, then proven: a probe pass (or the exit's own traffic) proved
 *   this provider dials real destinations within the qualification window.
 *   Absence is "not yet proven", never "bad"; the probe design records no
 *   negative state to show.
 */
fun exitStateTexts(fields: ExitStateFields): List<ExitStateText> {
    val texts = mutableListOf<ExitStateText>()
    texts.add(ExitStateText.SdkToken(fields.windowType.ifEmpty { Sdk.WindowTypeAuto }))
    val tier = if (fields.tier < fields.effectiveTier) {
        "${fields.tier}→${fields.effectiveTier}"
    } else {
        "${fields.tier}"
    }
    texts.add(ExitStateText.Resource(R.string.dev_state_tier, listOf(tier)))
    if (fields.quarantined) {
        texts.add(ExitStateText.Resource(R.string.dev_state_benched))
    } else if (fields.warning) {
        texts.add(
            if (fields.warningCause.isEmpty()) {
                ExitStateText.Resource(R.string.dev_state_warned)
            } else {
                ExitStateText.SdkToken(fields.warningCause)
            }
        )
    }
    if (fields.done) {
        texts.add(ExitStateText.Resource(R.string.dev_state_done))
    }
    if (fields.p2pOnly) {
        texts.add(ExitStateText.Resource(R.string.dev_state_p2p))
    }
    if (fields.proven) {
        texts.add(ExitStateText.Resource(R.string.dev_state_proven))
    }
    return texts
}

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
