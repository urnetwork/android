package com.bringyour.network.ui.account

import com.bringyour.network.MainApplication
import com.bringyour.network.ui.Route
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
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
        assertTrue(sessionsRow.contains("navController.navigate(Route.Sessions)"))
        // a guest adds a sign-in method first, as for Profile
        assertTrue(sessionsRow.contains("navController.navigate(Route.GuestConversion)"))

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
            "networkSpace.api?.setClientInfo(Sdk.newClientInfo(CLIENT_INFO_DEVICE_TYPE, BuildConfig.VERSION_NAME))"
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

    @Test
    fun theSessionsStringsAreInEveryCatalogAndTheScreenUsesThem() {
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        val english = strings(File(res, "values"))
        val keys = english.keys.filter { it.startsWith("sessions_") }
        assertEquals(44, keys.size)

        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = locales.flatMap { locale ->
            val translated = strings(locale)
            keys.filter { translated[it].isNullOrEmpty() }.map { "${locale.name}/$it" }
        }
        assertTrue("not translated: $missing", missing.isEmpty())

        // every key is looked up, except the remote sign-out explanation: the
        // controller reports no trustworthy session-revoked cause to show it for
        val sources = listOf(
            "ui/account/SessionsScreen.kt",
            "ui/account/SessionsPresentation.kt",
            "ui/account/AccountScreen.kt",
        ).joinToString("\n") { source(it) }
        val unused = keys.filter { !sources.contains("R.string.$it") }
        assertEquals(listOf("sessions_signed_out_remotely"), unused)
    }
}
