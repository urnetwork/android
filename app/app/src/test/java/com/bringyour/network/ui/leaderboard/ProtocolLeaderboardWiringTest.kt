package com.bringyour.network.ui.leaderboard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Protocol leaderboard as wired into the app, read from the sources: the
 * tab order and default, the split layout, the sdk calls, the Earnings tile's
 * source, the cross-links, the privacy of the rows and the catalogs. The
 * compose tree and the sdk need a device, which these JVM tests do not have;
 * the behavior is ProtocolLeaderboardModelTest's and
 * ProtocolLeaderboardPresentationTest's.
 */
class ProtocolLeaderboardWiringTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val SOURCE_DIR = "src/main/java/com/bringyour/network"

        private fun source(path: String) = moduleFile("$SOURCE_DIR/$path").readText()

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }

        private fun strings(dir: File): Map<String, Element> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(dir, "strings.xml")).getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }
                .associateBy { it.getAttribute("name") }
        }

        // a java format specifier: argument index, flags, width, precision and its conversion
        private val FORMAT_SPEC = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])")

        private val PROTOCOL_SOURCES = listOf(
            "ui/leaderboard/ProtocolLeaderboardTab.kt",
            "ui/leaderboard/ProtocolLeaderboardViewModel.kt",
            "ui/leaderboard/ProtocolLeaderboardModel.kt",
            "ui/leaderboard/ProtocolLeaderboardPresentation.kt",
            "ui/leaderboard/SdkProtocolTopClient.kt",
            "ui/leaderboard/LeaderboardScreen.kt",
            "ui/wallet/Top200Tile.kt",
            "ui/wallet/Top200TileState.kt",
        )
    }

    @Test
    fun protocolIsTheFirstTabAndTheDefaultThenPointsThenData() {
        val screen = source("ui/leaderboard/LeaderboardScreen.kt")
        assertTrue(screen.contains("var selectedTab by rememberSaveable { mutableIntStateOf(LEADERBOARD_TAB_PROTOCOL) }"))
        val tabs = between(screen, "val tabs = listOf(", ")\n")
        val protocol = tabs.indexOf("LEADERBOARD_TAB_PROTOCOL to stringResource(id = R.string.leaderboard_tab_protocol)")
        val points = tabs.indexOf("LEADERBOARD_TAB_POINTS to stringResource(id = R.string.points)")
        val data = tabs.indexOf("LEADERBOARD_TAB_DATA to stringResource(id = R.string.data)")
        assertTrue(tabs, 0 <= protocol && protocol < points && points < data)
        // a tap on the tab, the selected one included, scrolls its list to the top
        assertTrue(screen.contains("LEADERBOARD_TAB_PROTOCOL -> protocolTabTaps += 1"))
        assertTrue(screen.contains("resetToTop = protocolTabTaps,"))
        // the Data tab's failure never shows over another tab
        assertTrue(screen.contains("if (leaderboardViewModel.displayErrorMsg && selectedTab == LEADERBOARD_TAB_DATA) {"))
    }

    @Test
    fun fromMediumWidthTheCardSitsBesideTheList() {
        val screen = source("ui/leaderboard/LeaderboardScreen.kt")
        val tab = between(screen, "LEADERBOARD_TAB_PROTOCOL -> {", "LEADERBOARD_TAB_POINTS -> {")
        assertTrue(tab.contains("showHeader = !listDetail,"))
        val pane = between(screen, "private fun YourNetworkPane(", "\n}\n")
        assertTrue(pane.contains("if (selectedTab == LEADERBOARD_TAB_PROTOCOL) {"))
        assertTrue(pane.contains("ProtocolHeader("))
    }

    @Test
    fun theTabLoadsWhileStartedAndRefreshesByPullAndRetry() {
        val tab = source("ui/leaderboard/ProtocolLeaderboardTab.kt")
        val visibility = between(tab, "LifecycleStartEffect(viewModel) {", "}\n    }")
        assertTrue(visibility.contains("viewModel.setVisible(true)"))
        assertTrue(visibility.contains("viewModel.setVisible(false)"))
        val pull = between(tab, "PullToRefreshBox(", ") {")
        assertTrue(pull.contains("isRefreshing = ui.refreshing,"))
        assertTrue(pull.contains("onRefresh = viewModel::refresh,"))
        // the standard retry row for a failed first load, a failed page and a failed refresh
        assertTrue(between(tab, "ProtocolListBody.LoadFailed -> item(", "ProtocolListBody.Empty ->").contains("SectionLoadError("))
        assertTrue(between(tab, "item(key = \"protocol-notice\") {", "when (ui.body) {").contains("if (ui.refreshFailed) {"))
        assertTrue(between(tab, "private fun ProtocolFooter(", "\n}\n").contains("ProtocolListFooter.Failed -> SectionLoadError(onRetry = onRetry)"))
        // the list asks the model, which owns the paging rule
        assertTrue(tab.contains("viewModel.onLastVisibleRow(lastVisibleItem - PROTOCOL_LIST_ROWS_OFFSET)"))
    }

    @Test
    fun theListCarriesTheDraggablePositionIndicatorOfThePointsTab() {
        val tab = source("ui/leaderboard/ProtocolLeaderboardTab.kt")
        val indicator = between(tab, "RankedListPositionIndicator(", "modifier = Modifier")
        assertTrue(indicator.contains("rowsOffset = PROTOCOL_LIST_ROWS_OFFSET,"))
        assertTrue(indicator.contains("total = ui.indicatorTotal,"))
        assertTrue(indicator.contains("viewModel.seekTo(rank)"))
        // one shared indicator, not a copy
        val points = source("ui/leaderboard/PointsLeaderboardTab.kt")
        assertTrue(points.contains("RankedListPositionIndicator("))
        assertFalse(points.contains("private fun PointsPositionIndicator("))
    }

    @Test
    fun theCallsGoToTheDevicesApiAndNeverThrowOutOfACallback() {
        val viewModel = source("ui/leaderboard/ProtocolLeaderboardViewModel.kt")
        assertTrue(viewModel.contains("model.setClient(device?.api?.let { SdkProtocolTopClient(it) })"))
        assertTrue(viewModel.contains("post = { block -> viewModelScope.launch { block() } },"))
        val cleared = between(viewModel, "override fun onCleared() {", "super.onCleared()")
        assertTrue(cleared.contains("removeDeviceChangeListener?.invoke()"))
        assertTrue(cleared.contains("model.close()"))

        val client = source("ui/leaderboard/SdkProtocolTopClient.kt")
        for (call in listOf("api.snTop(args) {", "api.snTopMe {", "api.getNetworkLeaderboardRanking {", "api.setPointsLeaderboardPublic(args) {")) {
            assertTrue(call, client.contains(call))
        }
        // an exception out of a gomobile callback would abort the process
        assertEquals(4, Regex("done\\(\\s*runCatching \\{").findAll(client).count())
        // the board covers every network; the first page is the newest snapshot's
        assertTrue(client.contains("args.filter = PROTOCOL_TOP_FILTER_ALL"))
    }

    @Test
    fun theEarningsTileReadsTheStandingNotTheOldHeadRoute() {
        val sdkSource = source("ui/wallet/SdkProtocolSource.kt")
        assertTrue(sdkSource.contains("api.snTopMe {"))
        assertFalse(sdkSource.contains("api.snHead"))
        val models = source("ui/wallet/EarningsModels.kt")
        assertTrue(models.contains("suspend fun topMe(): Result<ProtocolTopMe?>"))
        assertFalse(models.contains("SnHeadState"))
        val earnings = source("ui/wallet/EarningsScreen.kt")
        assertTrue(earnings.contains("top200 = top200TileState(top),"))
        // a 404 or a failure of /sn/top/me only logs: the tile stays as it was
        // (none before a first answer), never a broken one
        val viewModel = source("ui/wallet/EarningsViewModel.kt")
        val refreshTop = between(viewModel, "private suspend fun refreshTop() {", "\n    }")
        assertTrue(refreshTop.contains(".onSuccess { _top.value = it }"))
        assertTrue(refreshTop.contains(".onFailure { Log.i("))
        assertFalse(refreshTop.contains("_top.value = null"))
        val topMe = between(sdkSource, "override suspend fun topMe()", "override fun formatAlpha(")
        assertTrue(topMe.contains("runCatching {"))
        // the leaderboard's "Claim on Earnings" scrolls Earnings to the tile
        assertTrue(earnings.contains("top200BringIntoView.bringIntoView()"))
        assertEquals(2, Regex("modifier = Modifier\\.bringIntoViewRequester\\(top200BringIntoView\\)").findAll(earnings).count())
    }

    @Test
    fun theClaimPromptsCrossLinkTheLeaderboardAndEarnings() {
        val host = source("ui/MainNavHost.kt")
        val earningsLink = between(host, "openEarningsTop200 = {", "},")
        assertTrue(earningsLink.contains("earningsViewModel.requestTop200Focus()"))
        assertTrue(earningsLink.contains("navController.navigate(Route.Earnings)"))
        val boardLink = between(host, "openProtocolLeaderboard = {", "},")
        assertTrue(boardLink.contains("mainNavViewModel.requestProtocolTab()"))
        assertTrue(boardLink.contains("selectTopLevelRoute(TopLevelScaffoldRoutes.LEADERBOARD)"))
        val leaderboard = between(host, "LeaderboardScreen(", ")\n")
        assertTrue(leaderboard.contains("onOpenEarnings = openEarningsTop200,"))
        assertTrue(leaderboard.contains("onProtocolTabShown = consumeProtocolTabRequest,"))
        assertTrue(host.contains("onSeeProtocolBoard = openProtocolLeaderboard,"))

        val tab = source("ui/leaderboard/ProtocolLeaderboardTab.kt")
        val prompt = between(tab, "private fun ProtocolClaimPromptView(", "\n}\n")
        // the one primary button, only while binding is open
        assertEquals(1, Regex("style = ButtonStyle.PRIMARY").findAll(tab).count())
        assertTrue(between(prompt, "if (prompt.bindingOpen) {", "} else {").contains("onClick = onOpenEarnings,"))
        assertTrue(prompt.contains("R.string.leaderboard_protocol_claim_warning"))
    }

    @Test
    fun signFleetBindingStaysHiddenWhileBindingIsClosed() {
        val tile = source("ui/wallet/Top200Tile.kt")
        val projected = between(tile, "fun Top200Tile(", "fun Top200BoundRow(")
        val open = between(projected, "if (state.bindingOpen) {", "} else {")
        assertTrue(open.contains("SignFleetBindingButton(onClick = onSignFleetBinding)"))
        assertTrue(open.contains("R.string.claim_your_spot"))
        assertTrue(between(projected, "} else {", "SeeBoardLink(").contains("ClaimingSoonNote()"))
        assertEquals(1, Regex("SignFleetBindingButton\\(onClick").findAll(projected).count())
        val claimed = between(tile, "fun Top200BoundRow(", "private fun Top200Heading(")
        assertTrue(between(claimed, "if (state.bindingOpen) {", "}").contains("SignFleetBindingButton(onClick = onSignFleetBinding)"))
    }

    @Test
    fun noRowCarriesANetworkId() {
        // LeaderboardScreen also holds the Data tab, whose public rows carry their ids
        for (path in PROTOCOL_SOURCES - "ui/leaderboard/LeaderboardScreen.kt") {
            val code = source(path)
            assertFalse(path, code.contains("networkId"))
            assertFalse(path, code.contains("network_id"))
        }
        // the caller's row is the server's is_me, never a name match
        val presentation = source("ui/leaderboard/ProtocolLeaderboardPresentation.kt")
        assertTrue(presentation.contains("val isMe: Boolean,"))
    }

    @Test
    fun theProtocolStringsAreInTheCatalogAndTheTabUsesEachOfThem() {
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        val english = strings(File(res, "values"))
        val keys = english.keys.filter { it.startsWith("leaderboard_protocol_") || it.startsWith("earnings_top200_") } +
            listOf("leaderboard_tab_protocol")
        assertEquals(52, keys.toSet().size)
        for (shared in listOf("show_on_points_leaderboard", "close", "try_again", "anonymous", "points", "data")) {
            assertTrue(shared, english.containsKey(shared))
        }
        // every key is looked up, by its whole name
        val sources = PROTOCOL_SOURCES.joinToString("\n") { source(it) }
        val unused = keys.filter { !Regex("R\\.string\\.$it\\b").containsMatchIn(sources) }
        assertEquals(listOf<String>(), unused)
    }

    @Test
    fun everyLocaleTakesTheArgumentsTheEnglishTakes() {
        // the tab passes counts, ranks, UIDs and epochs as numbers (%d) and
        // everything else as text it formatted (%s), as the english reads
        // (ProtocolLeaderboardCatalogTest); a translation with another
        // conversion would throw on a device in that locale
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        fun specs(text: String) = FORMAT_SPEC.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()
        val english = strings(File(res, "values")).filter { (name, _) ->
            name.startsWith("leaderboard_protocol_") || name.startsWith("earnings_top200_")
        }.mapValues { (_, element) -> specs(element.textContent) }
        assertEquals(listOf("%1\$d"), english["leaderboard_protocol_uid"])
        assertEquals(listOf("%1\$s"), english["earnings_top200_projected"])
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val mismatched = locales.flatMap { dir ->
            strings(dir).filter { (name, _) -> name in english }
                .filter { (name, element) -> specs(element.textContent) != english[name] }
                .map { (name, _) -> "${dir.name}/$name" }
        }
        assertEquals(listOf<String>(), mismatched)
        // and every locale has every key
        val missing = locales.flatMap { dir ->
            val translated = strings(dir)
            english.keys.filter { translated[it]?.textContent.isNullOrEmpty() }.map { "${dir.name}/$it" }
        }
        assertEquals(listOf<String>(), missing)
    }
}
