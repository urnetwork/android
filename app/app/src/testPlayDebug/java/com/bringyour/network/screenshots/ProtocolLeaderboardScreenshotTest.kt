package com.bringyour.network.screenshots

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import com.bringyour.network.ui.TopLevelScaffoldRoutes
import com.bringyour.network.ui.leaderboard.LeaderboardScreen
import com.bringyour.network.ui.leaderboard.PROTOCOL_LIST_TEST_TAG
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The Protocol tab's states (apple/LEADERTOP.md §9) at compact width, over
 * SampleProtocolTopClient: the placeholders before the first answer, the
 * list at the Top 200 line, and the claim prompt with binding open for a
 * borderline network; and the line in the list-detail layout.
 */
class ProtocolLeaderboardScreenshotTest : ScreenshotTest() {

    private companion object {
        // a position under the Top 200 line
        const val SEEK_PAST_THE_LINE = 210L
    }

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun loadingPlaceholders() = capture("protocol-loading-compact", SampleProtocolTopClient(answers = false))

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun listAtTheLine() = capture("protocol-line-compact", SampleProtocolTopClient(), scrollToKey = "protocol:195")

    @Test
    @Config(qualifiers = WindowSizes.MEDIUM)
    fun listAtTheLineBesideTheCard() = capture("protocol-line-medium", SampleProtocolTopClient(), scrollToKey = "protocol:195")

    @Test
    @Config(qualifiers = WindowSizes.COMPACT)
    fun claimingOpenBorderline() = capture(
        "protocol-claim-open-compact",
        SampleProtocolTopClient(bindingOpen = true, mePosition = 196, meLabel = ProtocolLabel.BORDERLINE),
    )

    private fun capture(name: String, client: SampleProtocolTopClient, scrollToKey: String? = null) {
        viewModels.protocolLeaderboard.useClient(client)
        compose.setContent {
            AppWindow(viewModels, TopLevelScaffoldRoutes.LEADERBOARD) {
                LeaderboardScreen(leaderboardViewModel = viewModels.leaderboard)
            }
        }
        if (scrollToKey != null) {
            // the first page holds rows 1 to 50: a seek, as the position
            // indicator's release makes, pages on past the line first
            compose.waitForIdle()
            viewModels.protocolLeaderboard.seekTo(SEEK_PAST_THE_LINE)
            compose.waitForIdle()
            compose.onNodeWithTag(PROTOCOL_LIST_TEST_TAG).performScrollToKey(scrollToKey)
        }
        compose.captureWindow(name)
    }
}
