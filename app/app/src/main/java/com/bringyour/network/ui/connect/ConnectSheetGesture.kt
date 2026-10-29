package com.bringyour.network.ui.connect

/**
 * Keeps the connect drawer's open/close gesture and its content's scroll
 * gesture apart. One touch is one gesture, and a gesture is either a drawer
 * gesture or a content gesture: neither hands its leftover movement or its
 * release fling to the other (mmm/DESIGNSTYLE.md).
 *
 * The kind is decided once per touch. A touch that lands while the drawer is
 * anywhere but settled open is a drawer gesture. A touch on the settled-open
 * drawer is undecided until its first movement: pulling down with the content
 * at its top closes the drawer, anything else scrolls the content.
 *
 * A mouse wheel turn or a trackpad's two-finger scroll is a third kind: it
 * opens the closed drawer, closes the open one from the top of its content,
 * and otherwise scrolls the content ([wheelMove]). A mouse press decides
 * nothing for the drawer, since a mouse drag never scrolls the content.
 *
 * Deltas and velocities follow Compose's nested-scroll convention: negative
 * when the finger moves up.
 */
class ConnectSheetGesture {

    enum class Kind { Undecided, Sheet, Content, Wheel }

    /** What a wheel scroll does to the drawer. */
    enum class WheelMove { Open, Close, ScrollContent }

    var kind: Kind = Kind.Content

    /**
     * A new touch: decided at once unless the drawer is settled open. A
     * mouse press ([touch] false) leaves what scrolls next to the content.
     */
    fun onDown(sheetSettledOpen: Boolean, touch: Boolean = true) {
        kind = when {
            !touch -> Kind.Content
            sheetSettledOpen -> Kind.Undecided
            else -> Kind.Sheet
        }
    }

    /** A mouse wheel or trackpad scroll over the drawer, until the next touch. */
    fun onWheel() {
        kind = Kind.Wheel
    }

    /** The first movement of an undecided touch. */
    fun onFirstMovement(deltaY: Float, contentAtTop: Boolean) {
        if (kind == Kind.Undecided) {
            kind = if (deltaY > 0f && contentAtTop) Kind.Sheet else Kind.Content
        }
    }

    companion object {

        /**
         * A wheel scroll of [deltaY] over the drawer: scrolling on toward more
         * content opens the closed drawer whole, and scrolling back at the top
         * of the open drawer's content closes it; anything else scrolls the
         * content. The drawer never stops part way, as it may under a finger.
         */
        fun wheelMove(deltaY: Float, drawerOpen: Boolean, contentAtTop: Boolean): WheelMove = when {
            !drawerOpen && deltaY < 0f -> WheelMove.Open
            drawerOpen && contentAtTop && deltaY > 0f -> WheelMove.Close
            else -> WheelMove.ScrollContent
        }

        /**
         * What the connection consumes before the content scrolls, out of
         * what the sheet left over. In a drawer gesture an upward remainder
         * means the drawer is already at the top, so it is swallowed rather
         * than scrolling the content; a downward one passes through so the
         * sheet can take it after the content (which is at its top).
         */
        fun preScrollConsumedY(kind: Kind, availableY: Float): Float =
            if (kind == Kind.Sheet && availableY < 0f) availableY else 0f

        /**
         * What the connection consumes after the content scrolled, before
         * the sheet sees the remainder. A content gesture that reaches the
         * top stops there: the remainder is swallowed so the sheet never
         * moves.
         */
        fun postScrollConsumedY(kind: Kind, availableY: Float): Float =
            if (kind == Kind.Content) availableY else 0f

        /**
         * The release velocity consumed before the content flings. In a
         * drawer gesture an upward fling that the sheet did not take (it is
         * at the top) is swallowed; a downward one passes through to reach
         * the sheet after the content, so a short flick still closes it.
         */
        fun preFlingConsumedY(kind: Kind, velocityY: Float): Float =
            if (kind == Kind.Sheet && velocityY < 0f) velocityY else 0f

        /**
         * The leftover velocity consumed after the content flung, before the
         * sheet sees it. A content fling never closes the drawer.
         */
        fun postFlingConsumedY(kind: Kind, velocityY: Float): Float =
            if (kind == Kind.Content) velocityY else 0f
    }
}
