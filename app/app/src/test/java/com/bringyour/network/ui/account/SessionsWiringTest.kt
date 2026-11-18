package com.bringyour.network.ui.account

import com.bringyour.network.BuildConfig
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.Route
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Account -> Sessions as wired into the app, read from the sources: the
 * Account row right after Profile, the route, the screen's lifecycle, swipe,
 * refresh and copy, the controller's teardown, the client info every api
 * reports, and the catalogs. The compose tree and the sdk need a device, which
 * these JVM tests do not have; the behavior is SessionsScreenModelTest's and
 * SessionsPresentationTest's.
 */
class SessionsWiringTest {
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

        private fun strings(dir: File): Map<String, String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(dir, "strings.xml")).getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }
                .associate { it.getAttribute("name") to it.textContent }
        }
    }

    @Test
    fun theSessionsRowComesRightAfterProfile() {
        val screen = source("ui/account/AccountScreen.kt")
        val rows = between(screen, "iconResourceId = R.drawable.nav_list_item_user,", "iconResourceId = R.drawable.nav_list_item_settings,")
        val sessionsRow = between(rows, "iconResourceId = R.drawable.nav_list_item_sessions,", "HorizontalDivider()")
        assertTrue(sessionsRow.contains("text = stringResource(id = R.string.sessions_title),"))
        // the account list's rows go through navigate, which beside a detail
        // pane replaces the chosen screen instead of stacking on it
        assertTrue(sessionsRow.contains("navigate(Route.Sessions)"))
        // a guest adds a sign-in method first, as for Profile
        assertTrue(sessionsRow.contains("navigate(Route.GuestConversion)"))
        assertTrue(sessionsRow.contains("selected = chosenRoute == Route.Sessions"))

        val icon = moduleFile("src/main/res/drawable/nav_list_item_sessions.xml").readText()
        assertTrue(icon.contains("android:width=\"24dp\""))
        assertTrue(icon.contains("android:fillColor=\"#989898\""))
    }

    @Test
    fun theRouteOpensTheScreen() {
        // Route.fromString matches by qualified-name containment
        assertEquals(Route.Sessions, Route.fromString("com.bringyour.network.ui.Route.Sessions"))
        assertEquals(Route.Settings, Route.fromString("com.bringyour.network.ui.Route.Settings"))
        val destination = between(source("ui/MainNavHost.kt"), "composable<Route.Sessions>(", "}\n")
        assertTrue(destination.contains("SessionsScreen(navController = navController)"))
    }

    @Test
    fun theScreenIsVisibleWhileStartedAndRefreshesByPull() {
        val screen = source("ui/account/SessionsScreen.kt")
        val visibility = between(screen, "LifecycleStartEffect(viewModel) {", "SessionsContent(")
        assertTrue(visibility.contains("viewModel.setVisible(true)"))
        assertTrue(between(visibility, "onStopOrDispose {", "}").contains("viewModel.setVisible(false)"))

        val pull = between(screen, "PullToRefreshBox(", ") {")
        assertTrue(pull.contains("isRefreshing = ui.refreshing,"))
        assertTrue(pull.contains("onRefresh = onRefresh,"))
        assertTrue(screen.contains("onRefresh = viewModel::refresh,"))
    }

    @Test
    fun aRowSwipesToSignOutWithItsScreenReaderAction() {
        val screen = source("ui/account/SessionsScreen.kt")
        val swipe = between(screen, "SwipeToRevealRow(", ") {")
        assertTrue(swipe.contains("onAction = onSignOut,"))
        assertTrue(swipe.contains("actionLabel = stringResource(id = R.string.sign_out),"))
        // "Sign out {device}"
        assertTrue(swipe.contains("accessibilityLabel = rowText.signOutAction,"))
        // a running sign out disables the control
        assertTrue(swipe.contains("enabled = !row.signingOut,"))
        // the row is the node that carries the action
        assertTrue(swipe.contains("mergeDescendants = true,"))
        assertTrue(screen.contains("onSignOut = viewModel::requestSignOut,"))

        // the long press copies the full id, and is the row's long-click action
        val copy = between(screen, "val copySessionId = {", "\n    }\n")
        assertTrue(copy.contains("ClipData.newPlainText(row.sessionId, row.sessionId)"))
        assertTrue(screen.contains("onLongClick(label = copyLabel) {"))
        assertTrue(screen.contains("detectTapGestures(onLongPress = { copySessionId() })"))
    }

    @Test
    fun theControllerIsBuiltOnTheApiAndTornDownListenerFirst() {
        val viewModel = source("ui/account/SessionsViewModel.kt")
        assertTrue(viewModel.contains("SdkSessionsController(device.api, onChange)"))
        assertTrue(viewModel.contains("private val controller = api.openClientSessionViewController()"))
        val close = between(viewModel, "override fun close() {", "}")
        val listener = close.indexOf("listenerSub.close()")
        val controller = close.indexOf("controller.close()")
        assertTrue(close, 0 <= listener && listener < controller)
        // changes are posted to the main thread
        assertTrue(viewModel.contains("post = { block -> viewModelScope.launch { block() } },"))
        // the view model's end closes it
        assertTrue(between(viewModel, "override fun onCleared() {", "super.onCleared()").contains("model.close()"))
    }

    @Test
    fun everyApiReportsTheDeviceTypeAndVersionBeforeItsRequests() {
        val application = source("MainApplication.kt")
        assertEquals("android", MainApplication.CLIENT_INFO_DEVICE_TYPE)
        val update = between(application, "private fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {", "\n    }\n")
        val clientInfo = update.indexOf(
            "networkSpace.api?.setClientInfo(Sdk.newClientInfoWithDistribution(CLIENT_INFO_DEVICE_TYPE, BuildConfig.VERSION_NAME, BuildConfig.BRINGYOUR_BUNDLE_DISTRIBUTION))"
        )
        assertTrue("updateActiveNetworkSpace sets no client info", 0 <= clientInfo)
        // after the space (and so its api) is active, before anything sends
        assertTrue(update.indexOf("networkSpaceManagerProvider.setNetworkSpace(networkSpace)") < clientInfo)
        for (request in listOf("Sdk.newClientEventQueue(", "Sdk.newLoginViewController(api)", "initDevice(byClientJwt)")) {
            val at = update.indexOf(request)
            assertTrue("missing: $request", 0 <= at)
            assertTrue("$request comes before the client info", clientInfo < at)
        }
    }

    // Each flavor reports its own distribution (sdk ClientDistribution*), which
    // the server reads for who bills the build: Google Play for play, Stripe for
    // the flavors outside it. A flavor without one, or with a value the sdk
    // does not pair with android, would report an unknown distribution.
    @Test
    fun everyFlavorReportsItsDistribution() {
        // the module's build.gradle, not the root project's beside it
        val module = moduleFile("src/main/AndroidManifest.xml").absoluteFile.parentFile!!.parentFile!!.parentFile!!
        val gradle = File(module, "build.gradle").readText()
        val flavors = between(gradle, "productFlavors {", "\n    sourceSets {")
        val want = mapOf(
            "github" to "android_direct",
            "play" to "play",
            "solana_dapp" to "solana_dapp",
            "ethos_dapp" to "ethos_dapp",
        )
        val field = Regex("""buildConfigField "String", "BRINGYOUR_BUNDLE_DISTRIBUTION", '"([a-z_]+)"'""")
        for ((flavor, distribution) in want) {
            val start = flavors.indexOf("\n        $flavor {")
            assertTrue("no $flavor flavor", 0 <= start)
            val next = want.keys.filter { it != flavor }
                .map { flavors.indexOf("\n        $it {", start + 1) }
                .filter { start < it }
                .minOrNull() ?: flavors.length
            val values = field.findAll(flavors.substring(start, next)).map { it.groupValues[1] }.toList()
            assertEquals(flavor, listOf(distribution), values)
        }
        assertTrue(BuildConfig.BRINGYOUR_BUNDLE_DISTRIBUTION in want.values)
    }

    @Test
    fun theSessionsStringsAreInEveryCatalogAndTheScreenUsesThem() {
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        val english = strings(File(res, "values"))
        val keys = english.keys.filter { it.startsWith("sessions_") }
        // with sessions_sign_out_others_failed and sessions_sign_in_required,
        // and session1118's 12 (C.md §4: the groups, their row labels, Show
        // more, the hosted proxy bodies and the refusal at the cap, signed
        // out and signed in)
        assertEquals(58, keys.size)
        assertTrue(english.containsKey("sessions_limit_reached"))
        // the site's own key is not in the app's catalogs
        assertFalse(english.containsKey("sessions_open"))

        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = locales.flatMap { locale ->
            val translated = strings(locale)
            (keys + "manage_devices_proxy").filter { translated[it].isNullOrEmpty() }.map { "${locale.name}/$it" }
        }
        assertTrue("not translated: $missing", missing.isEmpty())

        // every key is looked up: the screen's, and the sign-in screens'
        // refusal at the network's cap of active sessions, all through one
        // helper; the remote sign-out explanation also by the sign-in
        // screen's notice
        val sources = listOf(
            "ui/account/SessionsScreen.kt",
            "ui/account/SessionsPresentation.kt",
            "ui/account/AccountScreen.kt",
            "ui/login/SessionLimitSignIn.kt",
        ).joinToString("\n") { source(it) }
        val unused = keys.filter { !sources.contains("R.string.$it") }
        assertEquals(listOf<String>(), unused)
        assertTrue(source("ui/login/SignInNotice.kt").contains("R.string.sessions_signed_out_remotely"))
        // a hosted proxy's row reads as the Manage devices list names it
        assertTrue(source("ui/account/SessionsPresentation.kt").contains("R.string.manage_devices_proxy"))
    }

    @Test
    fun theForegroundGoesToTheControllerAndOnlyTheScreensEndClosesIt() {
        val viewModel = source("ui/account/SessionsViewModel.kt")
        assertTrue(viewModel.contains("override fun setForeground(foreground: Boolean) = controller.setForeground(foreground)"))
        // the process lifecycle is forwarded, not used to close
        assertTrue(between(viewModel, "override fun onStop(owner: LifecycleOwner) {", "}").contains("model.setForeground(false)"))

        val model = source("ui/account/SessionsScreenModel.kt")
        val foreground = between(model, "fun setForeground(nextForeground: Boolean) {", "\n    }\n")
        assertTrue(foreground.contains("controller?.setForeground(nextForeground)"))
        assertFalse(foreground.contains("close("))
        // the model no longer takes the provider screen's open-in-the-foreground owner
        assertFalse(model.contains("VisibleDeviceControllerOwner"))
    }

    @Test
    fun theSnapshotsGroupsAndCountsReachTheScreen() {
        val viewModel = source("ui/account/SessionsViewModel.kt")
        val copy = between(viewModel, "return SessionsSnapshot(", "\n    )\n")
        for (field in listOf(
            "sessions = sessions(snapshot.sessions),",
            "sessionCount = snapshot.sessionCount,",
            "sessionsHasMore = snapshot.sessionsHasMore,",
            "groups = groups,",
            "otherSessionCount = snapshot.otherSessionCount,",
            "truncated = snapshot.truncated,",
        )) {
            assertTrue(field, copy.contains(field))
        }
        val group = between(viewModel, "SessionGroupEntry(", "\n        )\n")
        for (field in listOf("kind = value.kind", "count = value.count", "expanded = value.expanded", "sessions = sessions(value.sessions)", "hasMore = value.hasMore")) {
            assertTrue(field, group.contains(field))
        }
        assertTrue(viewModel.contains("override fun setGroupExpanded(kind: String, expanded: Boolean) = controller.setGroupExpanded(kind, expanded)"))
        assertTrue(viewModel.contains("override fun showMoreSessions(kind: String) = controller.showMoreSessions(kind)"))
    }

    @Test
    fun aGroupIsAnExpandableHeaderAndEachSectionOffersShowMore() {
        val screen = source("ui/account/SessionsScreen.kt")
        val rows = between(screen, "SessionsBody.Rows -> {", "ui.signOutOthers?.let { others ->")
        // the device sign-ins' Show more is the sdk's sign-ins section
        assertTrue(rows.contains("if (ui.rowsHaveMore) {"))
        assertTrue(rows.contains("onShowMore(Sdk.ClientSessionGroupSignIns)"))
        // each group: its header toggles it, and an open group's Show more is its own
        assertTrue(rows.contains("for (group in ui.groups) {"))
        assertTrue(rows.contains("onToggle = { onSetGroupExpanded(group.kind, !group.expanded) },"))
        assertTrue(rows.contains("sessionRows(group.rows, ui.nowMillis, text, onSignOut)"))
        assertTrue(rows.contains("if (group.hasMore) {"))
        assertTrue(rows.contains("onShowMore(group.kind)"))
        assertTrue(screen.contains("onSetGroupExpanded = viewModel::setGroupExpanded,"))
        assertTrue(screen.contains("onShowMore = viewModel::showMoreSessions,"))

        // the header reads its state and offers expand or collapse
        val header = between(screen, "private fun SessionGroupHeader(", "\n}\n")
        assertTrue(header.contains(".clickable(onClick = onToggle)"))
        assertTrue(header.contains(".expandableRow(expanded = group.expanded, onToggle = onToggle)"))
        assertTrue(header.contains("sessionGroupHeader(group, text)"))
        // rows of every section are lazy items keyed by session
        assertTrue(between(screen, "private fun LazyListScope.sessionRows(", "\n}\n").contains("items(rows, key = { it.key }) { row ->"))

        // the sign out of the others says when hosted proxies stay
        assertTrue(screen.contains("body = stringResource(id = ui.signOutOthers?.confirmBodyRes ?: R.string.sessions_confirm_others_body),"))
    }

    @Test
    fun aFailedSignOutOfAllOthersShowsItsOwnLineAndKeepsTheButton() {
        val screen = source("ui/account/SessionsScreen.kt")
        val others = between(screen, "item(key = \"sign-out-others\") {", "if (ui.body == SessionsBody.Rows) {")
        // the button asks again; only a running sign out disables it
        assertTrue(others.contains("onClick = onSignOutOthers,"))
        assertTrue(others.contains("isProcessing = others.signingOut,"))
        assertFalse(others.contains("enabled = "))
        // the failure's own line (SignOutOthersUi.errorRes), not the bare Try again
        assertTrue(others.contains("others.errorRes?.let { errorRes ->"))
        assertTrue(others.contains("URInlineErrorText(message = stringResource(id = errorRes))"))
        assertFalse(others.contains("R.string.try_again"))
    }

    @Test
    fun theStatesInPlaceOfTheRowsShowThePresentationsMessage() {
        val screen = source("ui/account/SessionsScreen.kt")
        val signIn = between(screen, "SessionsBody.SignInRequired,", "SessionsBody.Unsupported ->")
        assertTrue(signIn.contains("SessionsBody.SignedOutRemotely -> item(key = \"sign-in-required\") {"))
        assertTrue(signIn.contains("SessionsBodyMessage(ui.body)"))
        // a retry cannot help before a new sign-in
        assertFalse(signIn.contains("onRefresh"))
        for (body in listOf("LoadFailed", "Unsupported", "Empty")) {
            val item = between(screen, "SessionsBody.$body -> item(", "\n\n")
            assertTrue(body, item.contains("SessionsBodyMessage(ui.body)"))
        }
        assertTrue(between(screen, "SessionsBody.LoadFailed -> item(", "\n\n").contains("TextButton(onClick = onRefresh)"))
        // the controller's flags reach the screen, the trusted cause included
        val flags = between(source("ui/account/SessionsViewModel.kt"), "SessionErrorFlags(", ")")
        assertTrue(flags.contains("signInRequired = it.signInRequired,"))
        assertTrue(flags.contains("sessionRevoked = it.sessionRevoked,"))
    }
}
