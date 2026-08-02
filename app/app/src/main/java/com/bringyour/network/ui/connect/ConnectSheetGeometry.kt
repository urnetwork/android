package com.bringyour.network.ui.connect

/**
 * Returns the collapsed connect-sheet height in pixels. Measurements are local
 * integer sizes/offsets rather than root positions, so moving the sheet cannot
 * feed a new position back into its own layout.
 *
 * [foldGapPx] is the standard visible padding between the above-the-fold
 * content (the bottom of the connect button) and the sheet's bottom edge —
 * the top of the tab bar on phones. [unconsumedBottomInsetPx] is the part of
 * the system bottom inset nothing below the sheet absorbs: zero above the
 * bottom tab bar (it clears the system bar itself), the full inset when the
 * sheet reaches the screen edge next to a navigation rail.
 */
internal fun connectSheetPeekHeightPx(
    baseHeightPx: Int,
    dragHandleHeightPx: Int,
    foldMarkerOffsetPx: Int,
    foldGapPx: Int,
    unconsumedBottomInsetPx: Int,
): Int {
    val contentHeightPx =
        if (dragHandleHeightPx > 0 && foldMarkerOffsetPx > 0) {
            dragHandleHeightPx + foldMarkerOffsetPx + foldGapPx
        } else {
            baseHeightPx
        }
    return (contentHeightPx + unconsumedBottomInsetPx).coerceAtLeast(0)
}
