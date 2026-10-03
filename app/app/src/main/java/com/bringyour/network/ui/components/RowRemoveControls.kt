package com.bringyour.network.ui.components

import androidx.compose.ui.semantics.CustomAccessibilityAction

/**
 * The ways a list row offers to remove its item.
 *
 * Swipe-to-reveal alone hides removal from anyone who does not know to swipe,
 * and from TalkBack, Switch Access and keyboard users, who cannot drag the row.
 * Rows whose removal has no other path (an editor with its own Remove) show a
 * remove button; the swipe stays as a shortcut.
 */
enum class RowRemoveControl {
    // swipe the row left to reveal Delete
    Swipe,
    // a visible remove button at the end of the row
    Button,
}

// blocked locations: unblocking has no other path
val blockedLocationRemoveControls: Set<RowRemoveControl> = setOf(
    RowRemoveControl.Swipe,
    RowRemoveControl.Button,
)

// provider locations: removing a provider has no other path
val providerLocationRemoveControls: Set<RowRemoveControl> = setOf(
    RowRemoveControl.Swipe,
    RowRemoveControl.Button,
)

/**
 * The accessibility actions of a swipe-to-reveal row. Assistive tech cannot
 * swipe, so the revealed delete is also offered as a custom action
 * (TalkBack's actions menu, Switch Access) on every such row.
 */
fun swipeToRevealAccessibilityActions(
    removeLabel: String,
    onDelete: () -> Unit,
): List<CustomAccessibilityAction> = listOf(
    CustomAccessibilityAction(removeLabel) {
        onDelete()
        true
    },
)
