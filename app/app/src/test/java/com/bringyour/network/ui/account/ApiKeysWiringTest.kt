package com.bringyour.network.ui.account

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
 * Account -> API keys as wired into the app, read from the sources: the
 * Account row right after Sessions, the route, the screen's lifecycle and
 * refresh, the sdk calls, how the new key is shown, copied and never saved,
 * and the catalogs. The compose tree and the sdk need a device, which these
 * JVM tests do not have; the behavior is ApiKeysScreenModelTest's and
 * ApiKeysPresentationTest's.
 */
class ApiKeysWiringTest {
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

        private val API_KEYS_SOURCES = listOf(
            "ui/account/ApiKeysScreen.kt",
            "ui/account/ApiKeysViewModel.kt",
            "ui/account/ApiKeysScreenModel.kt",
            "ui/account/ApiKeysPresentation.kt",
        )
    }

    @Test
    fun theApiKeysRowComesRightAfterSessions() {
        val screen = source("ui/account/AccountScreen.kt")
        val rows = between(screen, "iconResourceId = R.drawable.nav_list_item_sessions,", "iconResourceId = R.drawable.nav_list_item_settings,")
        val apiKeysRow = between(rows, "iconResourceId = R.drawable.nav_list_item_api_keys,", "HorizontalDivider()")
        assertTrue(apiKeysRow.contains("text = stringResource(id = R.string.api_keys_title),"))
        // the account list's rows go through navigate, which beside a detail
        // pane replaces the chosen screen instead of stacking on it
        assertTrue(apiKeysRow.contains("navigate(Route.ApiKeys)"))
        // a key gives full access: a guest adds a sign-in method first, as for Sessions
        assertTrue(apiKeysRow.contains("if (loginMode == LoginMode.Authenticated) {"))
        assertTrue(apiKeysRow.contains("navigate(Route.GuestConversion)"))
        assertTrue(apiKeysRow.contains("selected = chosenRoute == Route.ApiKeys"))

        val icon = moduleFile("src/main/res/drawable/nav_list_item_api_keys.xml").readText()
        assertTrue(icon.contains("android:width=\"24dp\""))
        assertTrue(icon.contains("android:fillColor=\"#989898\""))
    }

    @Test
    fun theRouteOpensTheScreen() {
        // Route.fromString matches by qualified-name containment
        assertEquals(Route.ApiKeys, Route.fromString("com.bringyour.network.ui.Route.ApiKeys"))
        assertEquals(Route.Sessions, Route.fromString("com.bringyour.network.ui.Route.Sessions"))
        val host = source("ui/MainNavHost.kt")
        val destination = between(host, "composable<Route.ApiKeys>(", "}\n")
        assertTrue(destination.contains("ApiKeysScreen(navController = navController)"))
        // in the account graph, beside the list from medium width, as Sessions
        val accountGraph = between(host, "navigation<Route.AccountContainer>(", "composable<Route.Settings>(")
        assertTrue(accountGraph.contains("composable<Route.ApiKeys>("))
    }

    @Test
    fun theScreenLoadsWhileStartedAndRefreshesByPullAndRetry() {
        val screen = source("ui/account/ApiKeysScreen.kt")
        val visibility = between(screen, "LifecycleStartEffect(viewModel) {", "ApiKeysContent(")
        assertTrue(visibility.contains("viewModel.setVisible(true)"))
        assertTrue(between(visibility, "onStopOrDispose {", "}").contains("viewModel.setVisible(false)"))

        val pull = between(screen, "PullToRefreshBox(", ") {")
        assertTrue(pull.contains("isRefreshing = ui.refreshing,"))
        assertTrue(pull.contains("onRefresh = onRefresh,"))
        assertTrue(screen.contains("onRefresh = viewModel::refresh,"))

        // a failed load offers Retry; its message already says to try again
        val failed = between(screen, "ApiKeysBody.LoadFailed -> item(", "\n\n")
        assertTrue(failed.contains("ApiKeysBodyMessage(ui.body)"))
        assertTrue(failed.contains("TextButton(onClick = onRefresh)"))
        assertTrue(failed.contains("R.string.retry"))
        // and so does a failed reload over the rows, for those who cannot pull
        val refreshFailed = between(screen, "item(key = \"refresh-failed\") {", "when (ui.body) {")
        assertTrue(refreshFailed.contains("R.string.api_keys_load_failed"))
        assertTrue(refreshFailed.contains("TextButton(onClick = onRefresh)"))
    }

    @Test
    fun theIntroLinksToTheApiReferenceUnderIt() {
        val screen = source("ui/account/ApiKeysScreen.kt")
        val intro = between(screen, "item(key = \"intro\") {", "item(key = \"create\") {")
        val explanation = intro.indexOf("R.string.api_keys_intro")
        val link = intro.indexOf("onClick = { uriHandler.openUri(API_KEYS_DOCS_URL) },")
        assertTrue(intro, 0 <= explanation && explanation < link)
        assertTrue(intro.contains("R.string.api_keys_docs_link"))
        // a full-size target, not a few words inside the explanation
        assertTrue(intro.contains("TextButton("))
        // then Create API key
        assertTrue(between(screen, "item(key = \"create\") {", "\n\n").contains("onClick = onCreate,"))
        assertTrue(screen.contains("onCreate = viewModel::requestCreate,"))
        assertTrue(screen.contains("onCreateNamed = viewModel::create,"))
    }

    @Test
    fun theCallsGoToTheDevicesAccountApi() {
        val viewModel = source("ui/account/ApiKeysViewModel.kt")
        assertTrue(viewModel.contains("openClient = { device -> SdkApiKeysClient(device) },"))
        for (call in listOf("api.listApiKeys {", "api.createApiKey(args) {", "api.deleteApiKey(args) {")) {
            assertTrue(call, viewModel.contains(call))
        }
        // answers are posted to the main thread
        assertTrue(viewModel.contains("post = { block -> viewModelScope.launch { block() } },"))
        // the view model's end closes the model and stops following the device
        val cleared = between(viewModel, "override fun onCleared() {", "super.onCleared()")
        assertTrue(cleared.contains("removeDeviceChangeListener?.invoke()"))
        assertTrue(cleared.contains("model.close()"))
        // an exception out of a gomobile callback would abort the process
        assertEquals(3, Regex("done\\(\\s*runCatching \\{").findAll(viewModel).count())
    }

    @Test
    fun aRowDeletesByItsButtonOrASwipeAndAsksFirst() {
        val screen = source("ui/account/ApiKeysScreen.kt")
        val row = between(screen, "private fun ApiKeyRow(", "\n}\n")
        // deleting has no other path, so the row shows a button besides the swipe
        assertTrue(row.contains("RowRemoveControl.Button in apiKeyRemoveControls"))
        assertTrue(row.contains("IconButton(onClick = onDelete)"))
        assertTrue(row.contains("contentDescription = deleteLabel,"))
        val swipe = between(row, "SwipeToRevealRow(", "content = content,")
        assertTrue(swipe.contains("onAction = onDelete,"))
        assertTrue(swipe.contains("actionLabel = deleteLabel,"))
        // a running delete disables the control
        assertTrue(swipe.contains("enabled = !row.deleting,"))
        // both ask first: the row's Delete is the model's request
        assertTrue(screen.contains("onDelete = viewModel::requestDelete,"))
        assertTrue(screen.contains("onConfirmDelete = viewModel::confirmDelete,"))
        val confirm = between(screen, "private fun ApiKeysConfirmDeleteDialog(", "\n}\n")
        assertTrue(confirm.contains("R.string.api_keys_delete_confirm_title"))
        assertTrue(confirm.contains("style = ButtonStyle.WARNING,"))
        // Cancel is the default
        assertTrue(confirm.contains("cancelFocus.requestFocus()"))
        assertTrue(screen.contains("body = apiKeyDeleteConfirmBody(dialog.row, text),"))
    }

    @Test
    fun theNewKeyIsShownInASecureDialogThatOnlyDoneCloses() {
        val screen = source("ui/account/ApiKeysScreen.kt")
        val created = between(screen, "private fun ApiKeysCreatedDialog(", "\n}\n")
        // screenshots, screen recording and the saved recents thumbnail leave it out
        assertTrue(created.contains("securePolicy = SecureFlagPolicy.SecureOn,"))
        // the key is never shown again: back and a tap outside do nothing
        assertTrue(created.contains("dismissOnBackPress = false,"))
        assertTrue(created.contains("dismissOnClickOutside = false,"))
        assertTrue(created.contains("onClick = onDone,"))
        assertTrue(screen.contains("onDone = onDismissDialog,"))
        assertTrue(created.contains("R.string.api_keys_created_warning"))
        assertTrue(created.contains("R.string.done"))
        // not selectable: the only copy is the sensitive one
        assertFalse(screen.contains("SelectionContainer"))
    }

    @Test
    fun theCopyIsMarkedSensitive() {
        val screen = source("ui/account/ApiKeysScreen.kt")
        val clip = between(screen, "private fun sensitiveClip(", "\n\n")
        assertTrue(clip.contains("ClipData.newPlainText(label, apiKey)"))
        assertTrue(clip.contains("putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)"))
        val created = between(screen, "private fun ApiKeysCreatedDialog(", "\n}\n")
        assertTrue(created.contains("val clip = sensitiveClip(clipLabel, created.apiKey)"))
        assertTrue(created.contains("clipboard.setClipEntry(clip.toClipEntry())"))
        // the label is the screen's title, never the key
        assertTrue(created.contains("val clipLabel = stringResource(id = R.string.api_keys_title)"))
        // and the only clip the screen makes
        assertEquals(1, Regex("ClipData\\.newPlainText").findAll(screen).count())
    }

    @Test
    fun nothingSavesOrLogsTheNewKey() {
        for (path in API_KEYS_SOURCES) {
            val code = source(path)
            for (store in listOf(
                "SharedPreferences",
                "DataStore",
                "SavedStateHandle",
                "FileOutputStream",
                "writeText(",
                "openFileOutput",
                "Analytics",
                "logEvent",
                "onSaveInstanceState",
            )) {
                assertFalse("$path: $store", code.contains(store))
            }
            // a log line names neither the key nor a whole answer, whose
            // gomobile toString prints the key
            for (line in code.lines().filter { it.contains("Log.") }) {
                assertFalse("$path: $line", line.contains("apiKey") || line.contains("\$result") || line.contains("{result"))
            }
        }
        // the only saved state is the name field's, which is no secret
        val screen = source("ui/account/ApiKeysScreen.kt")
        val saved = Regex("rememberSaveable\\(([^)]*)\\)").findAll(screen).map { it.value }.toList()
        assertEquals(listOf("rememberSaveable(stateSaver = TextFieldValue.Saver)"), saved)
        assertTrue(between(screen, "private fun ApiKeysCreateDialog(", "\n}\n").contains("rememberSaveable("))

        // the model keeps the key only in the dialog, whose toString leaves it out
        val model = source("ui/account/ApiKeysScreenModel.kt")
        assertTrue(model.contains("class Created(val name: String, val apiKey: String) : ApiKeysDialog {"))
        assertFalse(model.contains("data class Created("))
        assertFalse(model.contains("data class CreatedApiKey("))
        assertFalse(source("ui/account/ApiKeysPresentation.kt").contains("apiKey: String"))
    }

    @Test
    fun theApiKeysStringsAreInEveryCatalogAndTheScreenUsesThem() {
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        val english = strings(File(res, "values"))
        val keys = english.keys.filter { it.startsWith("api_keys_") }
        assertEquals(20, keys.size)
        // the screen also reuses these
        for (shared in listOf("cancel", "done", "retry", "create", "back", "loading")) {
            assertTrue(shared, english.containsKey(shared))
        }

        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = locales.flatMap { locale ->
            val translated = strings(locale)
            keys.filter { translated[it]?.textContent.isNullOrEmpty() }.map { "${locale.name}/$it" }
        }
        assertTrue("not translated: $missing", missing.isEmpty())

        // every key is looked up, by its whole name (api_keys_create is also
        // the start of api_keys_create_failed)
        val sources = (API_KEYS_SOURCES + "ui/account/AccountScreen.kt").joinToString("\n") { source(it) }
        val unused = keys.filter { !Regex("R\\.string\\.$it\\b").containsMatchIn(sources) }
        assertEquals(listOf<String>(), unused)
        for (shared in listOf("cancel", "done", "retry", "create")) {
            assertTrue(shared, source("ui/account/ApiKeysScreen.kt").contains("R.string.$shared)"))
        }
    }
}
