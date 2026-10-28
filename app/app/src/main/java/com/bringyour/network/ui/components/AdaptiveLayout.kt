package com.bringyour.network.ui.components

import androidx.window.core.layout.WindowSizeClass

/*
 * The window rules of the foldable design (apple/DUO.md, "Design direction
 * agreed after this research"). Every layout decides from the window's size
 * class and the fold that divides it, never from the device or its
 * orientation, so one rule serves phones, flip and book-style foldables,
 * tablets, split screen and resizable windows. Pure, in dp, for the JVM tests.
 */

/**
 * A fold that divides the window in two (FoldingFeature.isSeparating: half
 * opened, or a hinge between two screens), in window dp. A vertical fold puts
 * the halves side by side (book posture); a horizontal one stacks them
 * (tabletop). [start] and [end] are the fold's edges along the axis it
 * divides, left and right or top and bottom; they are equal for a fold that
 * hides nothing.
 */
data class SeparatingFold(
    val vertical: Boolean,
    val start: Float,
    val end: Float,
)

/** The top-level navigation: the bottom bar or the side rail. */
enum class NavigationLayout { BottomBar, Rail }

/**
 * Material's navigation suite rule, with the bar kept through medium width:
 * the rail once the window is expanded wide and not short, the bottom bar
 * otherwise, and always in tabletop, where a full-height rail would cross the
 * fold. Material's own default (NavigationSuiteScaffoldDefaults) already
 * keeps the bar for compact width or height and tabletop; at medium width the
 * agreed design keeps it too, under the stacked connect split.
 */
fun navigationLayoutFor(widthDp: Float, heightDp: Float, tabletop: Boolean): NavigationLayout =
    if (
        !tabletop &&
        widthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND &&
        heightDp >= WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    ) {
        NavigationLayout.Rail
    } else {
        NavigationLayout.BottomBar
    }

/** True when the window is at least medium wide: the list-detail and pane layouts. */
fun isMediumWidthOrWider(widthDp: Float): Boolean =
    widthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

/** How the connect screen arranges the connect graphic and the drawer's statistics. */
enum class ConnectArrangement {
    /** Compact width: the phone layout, the connect graphic above the drawer. */
    Drawer,

    /** The statistics pane (the drawer, fully expanded) beside the connect pane. */
    SideBySide,

    /** The connect pane above; the location, compact statistics and Disconnect below. */
    Stacked,
}

/**
 * The connect screen's arrangement for the window. Compact width keeps the
 * phone layout. A separating fold decides next: the split lands on it, side
 * by side across a vertical fold (book posture) and stacked across a
 * horizontal one (tabletop). Otherwise expanded width puts the panes side by
 * side and medium width stacks them, unless the window is too short to
 * stack (a landscape phone, a short split-screen window).
 */
fun connectArrangementFor(widthDp: Float, heightDp: Float, fold: SeparatingFold?): ConnectArrangement =
    when {
        widthDp < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND -> ConnectArrangement.Drawer
        fold != null && fold.vertical -> ConnectArrangement.SideBySide
        fold != null -> ConnectArrangement.Stacked
        widthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND -> ConnectArrangement.SideBySide
        heightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND -> ConnectArrangement.SideBySide
        else -> ConnectArrangement.Stacked
    }

/**
 * Where two panes meet, in dp from the start of their container along the
 * axis they share (left to right, or top to bottom): the first pane ends at
 * [firstEnd] and the second starts at [secondStart]. A fold that hides
 * content lies between them.
 */
data class PaneSplit(val firstEnd: Float, val secondStart: Float)

/** The side pane's share of the width with no fold to split at (40/60). */
const val SIDE_PANE_FRACTION = 0.4f

/** The side pane never gets narrower than this, so its cards stay readable. */
const val SIDE_PANE_MIN_WIDTH_DP = 300f

/** The side pane never gets wider than this, so a large window widens the main pane. */
const val SIDE_PANE_MAX_WIDTH_DP = 400f

/** The main pane keeps at least this much width beside the side pane. */
const val MAIN_PANE_MIN_WIDTH_DP = 240f

/** A fold leaving either side less than this is not used as the split. */
const val FOLD_PANE_MIN_DP = 200f

/**
 * Splits a side-by-side container [width] dp wide whose left edge is at
 * [containerStart] in window dp. A separating vertical [fold] inside it, with
 * room on both sides, takes the split: no row, control or dialog straddles
 * it. Otherwise the side pane (the statistics or a list) gets 40% of the
 * width, between its minimum and maximum, on the left when [sidePaneOnLeft]
 * (left to right layouts) and on the right otherwise.
 */
fun sideBySideSplit(
    containerStart: Float,
    width: Float,
    fold: SeparatingFold?,
    sidePaneOnLeft: Boolean,
): PaneSplit {
    foldSplit(containerStart, width, fold?.takeIf { it.vertical })?.let { return it }
    val side = (width * SIDE_PANE_FRACTION)
        .coerceIn(SIDE_PANE_MIN_WIDTH_DP, SIDE_PANE_MAX_WIDTH_DP)
        .coerceAtMost(width - MAIN_PANE_MIN_WIDTH_DP)
        .coerceAtLeast(0f)
    val edge = if (sidePaneOnLeft) side else width - side
    return PaneSplit(edge, edge)
}

/** The side pane's width in a side-by-side [split] of a container [width] dp wide. */
fun sidePaneWidth(split: PaneSplit, width: Float, sidePaneOnLeft: Boolean): Float =
    if (sidePaneOnLeft) split.firstEnd else width - split.secondStart

/** The main pane's width beside the side pane in the same [split]. */
fun mainPaneWidth(split: PaneSplit, width: Float, sidePaneOnLeft: Boolean): Float =
    if (sidePaneOnLeft) width - split.secondStart else split.firstEnd

/**
 * Splits a stacked container [height] dp tall whose top edge is at
 * [containerTop] in window dp: at a separating horizontal [fold] inside it
 * with room on both sides (tabletop), else in half.
 */
fun stackedSplit(containerTop: Float, height: Float, fold: SeparatingFold?): PaneSplit {
    foldSplit(containerTop, height, fold?.takeIf { !it.vertical })?.let { return it }
    val half = height / 2f
    return PaneSplit(half, half)
}

private fun foldSplit(containerStart: Float, length: Float, fold: SeparatingFold?): PaneSplit? {
    fold ?: return null
    val firstEnd = fold.start - containerStart
    val secondStart = fold.end - containerStart
    if (firstEnd < FOLD_PANE_MIN_DP || length - secondStart < FOLD_PANE_MIN_DP || secondStart < firstEnd) {
        return null
    }
    return PaneSplit(firstEnd, secondStart)
}

/** A rectangle of the window in dp: the region a dialog may open in. */
data class WindowRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A dialog keeps to one side of a fold only when that side has at least this much room. */
const val FOLD_DIALOG_MIN_DP = 240f

/**
 * Where a dialog opens in a [width] × [height] dp window: all of it, or with
 * a separating [fold] one side of it, so no dialog sits on the fold. In
 * tabletop that is the half below the fold, under the hands; beside a
 * vertical fold it is the start side, on the left when [startOnLeft]. A side
 * too small to hold a dialog falls back to the other one.
 */
fun foldSafeDialogRegion(width: Float, height: Float, fold: SeparatingFold?, startOnLeft: Boolean): WindowRegion {
    val window = WindowRegion(0f, 0f, width, height)
    fold ?: return window
    if (fold.vertical) {
        val left = WindowRegion(0f, 0f, fold.start, height)
        val right = WindowRegion(fold.end, 0f, width, height)
        val (preferred, other) = if (startOnLeft) left to right else right to left
        return listOf(preferred, other).firstOrNull { FOLD_DIALOG_MIN_DP <= it.width } ?: window
    }
    val below = WindowRegion(0f, fold.end, width, height)
    val above = WindowRegion(0f, 0f, width, fold.start)
    return listOf(below, above).firstOrNull { FOLD_DIALOG_MIN_DP <= it.height } ?: window
}

/** The connect graphic at full size: the phone layout's 256dp square. */
const val CONNECT_GRAPHIC_FULL_DP = 256f

/**
 * The smallest the connect graphic shrinks to in a short window (a flip
 * cover, a bubble, half a split screen): still a touch target, so the status
 * stays in view above the drawer.
 */
const val CONNECT_GRAPHIC_MIN_DP = 48f

/** The largest the connect graphic grows to in a pane. */
const val CONNECT_GRAPHIC_PANE_MAX_DP = 280f

/**
 * The connect graphic's side for the room it has: the smaller of the
 * available width and height, between [min] and [max]. The phone layout uses
 * the full 256dp as its maximum, so only a short window shrinks it.
 */
fun connectGraphicSize(availableWidth: Float, availableHeight: Float, min: Float, max: Float): Float =
    minOf(availableWidth, availableHeight).coerceIn(min, max)

/**
 * The expanded connect drawer's height in a layout [layoutHeight] tall: the
 * [fraction] of it the phone design opens to, never past the top of the
 * layout, and in tabletop never above the fold, whose bottom edge is
 * [foldBottom] from the layout's top. It is never shorter than the collapsed
 * [peekHeight] that the layout has room for.
 */
fun connectDrawerExpandedHeight(
    layoutHeight: Float,
    peekHeight: Float,
    fraction: Float,
    foldBottom: Float?,
): Float {
    val ceiling = if (foldBottom != null && foldBottom > 0f && foldBottom < layoutHeight) {
        layoutHeight - foldBottom
    } else {
        layoutHeight
    }
    return (layoutHeight * fraction)
        .coerceAtMost(ceiling)
        .coerceAtLeast(minOf(peekHeight, layoutHeight))
        .coerceAtLeast(0f)
}
