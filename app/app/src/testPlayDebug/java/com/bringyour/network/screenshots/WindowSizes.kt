package com.bringyour.network.screenshots

/**
 * The windows the screenshot tests draw the screens in (apple/DUO.md item 7),
 * one for each case the window rules tell apart (ui/components/AdaptiveLayout.kt),
 * as Robolectric qualifiers: the window's size in dp, at xhdpi.
 */
object WindowSizes {

    /**
     * Compact width, a phone held upright (Android Studio's Medium Phone):
     * the bottom bar, connect's drawer, and Account and Leaderboard as one
     * column.
     */
    const val COMPACT = "w411dp-h914dp-port-xhdpi"

    /**
     * Medium width, a book-style foldable open and upright (the Galaxy Z
     * Fold8's inner screen, DUO.md): the rail, connect stacked (the graphic
     * above, the statistics and connect below), and Account and Leaderboard
     * list-detail.
     */
    const val MEDIUM = "w704dp-h933dp-port-xhdpi"

    /**
     * Expanded width, a tablet held sideways (the Pixel Tablet, as the
     * urnetwork-tablet AVD): the rail, and connect's statistics pane beside
     * the connect pane, split 40/60 within the side pane's 300-400 dp.
     */
    const val EXPANDED = "w1280dp-h800dp-land-xhdpi"

    /**
     * Compact height, a phone held sideways (a 360 dp phone in landscape):
     * the bottom bar, since a rail needs medium height, and connect side by
     * side, since the window is too short to stack even at medium width.
     */
    const val COMPACT_HEIGHT = "w780dp-h360dp-land-xhdpi"
}
