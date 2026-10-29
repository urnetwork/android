package com.bringyour.network.screenshots

import android.os.Looper
import com.bringyour.network.ui.TopLevelScaffoldRoutes
import com.bringyour.network.ui.leaderboard.LeaderboardScreen
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Leaderboard tab at each window size, on its Data tab: one column with
 * "Your network" above the ranking at compact width, and from medium width
 * the list-detail layout, the ranking with its tabs in the side pane and
 * "Your network" beside it.
 */
class LeaderboardScreenshotTest : ScreenshotTest() {

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun compactWidth() = capture("leaderboard-compact")

    @Test
    @Config(qualifiers = WindowSizes.MEDIUM)
    fun mediumWidth() = capture("leaderboard-medium")

    @Test
    @Config(qualifiers = WindowSizes.EXPANDED)
    fun expandedWidth() = capture("leaderboard-expanded")

    @Test
    @Config(qualifiers = WindowSizes.COMPACT_HEIGHT)
    fun compactHeight() = capture("leaderboard-compact-height")

    private fun capture(name: String) {
        // Without a device the first fetch fails at once. Its snackbar is a
        // notice for one failed refresh, timed, not part of the layout: the
        // screen is drawn as it stands once the notice is gone.
        shadowOf(Looper.getMainLooper()).idle()
        viewModels.leaderboard.setDisplayErrorMsg(false)
        compose.setContent {
            AppWindow(viewModels, TopLevelScaffoldRoutes.LEADERBOARD) {
                LeaderboardScreen(leaderboardViewModel = viewModels.leaderboard)
            }
        }
        compose.captureWindow(name)
    }
}
