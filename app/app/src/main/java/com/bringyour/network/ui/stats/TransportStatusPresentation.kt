package com.bringyour.network.ui.stats

/**
 * The status decorations of the transport settings editor, computed as one
 * pure step so every display rule is deterministic and testable:
 * - decorations render only for Auto, only with a known status, and only
 *   while the draft equals the applied policy the status was computed for
 *   (a status must never be interpreted against an unrelated draft)
 * - `autoDegraded` is authoritative: no degradation, no decorations
 * - a transport is constrained when it is enabled under Auto and absent from
 *   the status's eligible modes; the banner can render with no constrained
 *   rows when the ineligible modes are vocabulary this app does not know
 * - the memory constraint has its own copy; any other constraint uses the
 *   generic system-constraint copy (an unknown future constraint must not be
 *   presented as a memory limit)
 *
 * Generic over the transport key type: the sdk-backed types are native and
 * not loadable in JVM unit tests, so the matrix runs over plain strings.
 */
data class TransportStatusPresentation<T>(
    val showBanner: Boolean,
    val memoryConstraint: Boolean,
    val constrainedTransports: Set<T>,
) {
    companion object {
        /** mirror of Sdk.TransportConstraintMemory (native-backed class) */
        const val CONSTRAINT_MEMORY = "memory"

        fun <T> hidden(): TransportStatusPresentation<T> {
            return TransportStatusPresentation(
                showBanner = false,
                memoryConstraint = false,
                constrainedTransports = emptySet(),
            )
        }

        fun <T> compute(
            isAuto: Boolean,
            draftMatchesStatusPolicy: Boolean,
            autoTransports: List<T>,
            statusKnown: Boolean,
            autoDegraded: Boolean,
            autoEligibleTransports: Set<T>,
            autoConstraint: String,
        ): TransportStatusPresentation<T> {
            if (!statusKnown || !autoDegraded || !isAuto || !draftMatchesStatusPolicy) {
                return hidden()
            }
            return TransportStatusPresentation(
                showBanner = true,
                memoryConstraint = autoConstraint == CONSTRAINT_MEMORY,
                constrainedTransports = autoTransports
                    .filterNot { autoEligibleTransports.contains(it) }
                    .toSet(),
            )
        }

        /** the adapter over the view model's snapshot and status types */
        fun compute(
            draft: TransportSettingsUi,
            statusPolicy: TransportSettingsUi?,
            status: TransportRuntimeStatusUi?,
        ): TransportStatusPresentation<TransportTypeUi> {
            return compute(
                isAuto = draft.isAuto,
                draftMatchesStatusPolicy = statusPolicy != null && draft == statusPolicy,
                autoTransports = draft.autoTransports,
                statusKnown = status != null,
                autoDegraded = status?.autoDegraded == true,
                autoEligibleTransports = status?.autoEligibleTransports ?: emptySet(),
                autoConstraint = status?.autoConstraint ?: "",
            )
        }
    }
}
