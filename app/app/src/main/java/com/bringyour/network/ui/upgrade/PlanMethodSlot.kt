package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable

/**
 * A payment method row on a plan surface (mmm/ZCASH-CONTRACT.md §6.1), on the
 * flavors that sell outside a store. The flavor's `rememberPlanMethodSlot`
 * builds one; Play's returns null, so its surfaces render as they always have.
 * The shared surfaces (PlanPicker, IntroductionOffer) know only this shape: the
 * row, drawn after the plan cards and directly above the buy button, and while
 * a method other than the card is chosen, the purchase that replaces the
 * button.
 */
class PlanMethodSlot(
    /** the row, with anything it says under itself */
    val row: @Composable () -> Unit,
    /** the chosen method's own purchase; null while the card is chosen */
    val alternative: PlanAlternative?,
)

/**
 * A purchase through a method other than the card: the button that replaces
 * the buy button, the fine print that replaces the terms line, and the note on
 * the monthly card, which such a method does not sell. It has no free trial,
 * so the plan cards print none while it is chosen.
 */
class PlanAlternative(
    val label: String,
    val finePrint: String,
    val monthlyNote: String,
    val inProgress: Boolean,
    val start: () -> Unit,
)
