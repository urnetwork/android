package com.bringyour.network.ui.blocked_regions

/**
 * The ways a blocked location row offers to unblock (remove) its location.
 *
 * Swipe-to-reveal alone hides removal from anyone who does not know to swipe,
 * and from TalkBack, Switch Access and keyboard users, who cannot drag the row.
 * The row always shows a remove button; the swipe stays as a shortcut.
 */
enum class BlockedLocationRemoveControl {
    // swipe the row left to reveal Delete
    Swipe,
    // a visible remove button at the end of the row
    Button,
}

val blockedLocationRemoveControls: Set<BlockedLocationRemoveControl> = setOf(
    BlockedLocationRemoveControl.Swipe,
    BlockedLocationRemoveControl.Button,
)
