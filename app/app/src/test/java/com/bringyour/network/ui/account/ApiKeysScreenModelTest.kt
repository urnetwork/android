package com.bringyour.network.ui.account

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine behind ApiKeysViewModel over a fake client: loading on
 * each appearance, create with the new key shown once and dropped by Done,
 * delete through the confirmation, failures, and the races between answers,
 * account switches and the close.
 *
 * Each call waits until the test answers it, as the sdk answers on its own
 * thread, and each answer is posted, as the view model posts it to the main
 * thread; the tests run the posted work explicitly, in the order they need,
 * with no time or threads involved.
 */
class ApiKeysScreenModelTest {

    private val events = mutableListOf<String>()
    private val posted = ArrayDeque<() -> Unit>()
    private val clients = mutableMapOf<String, FakeClient>()
    private var opens = 0

    // synthetic, in the server's format: urn_ and 52 base32hex characters
    private val newKey = "urn_" + "0123456789ABCDEFGHIJKLMNOPQRSTUV".repeat(2).take(52)

    /** The client of one device, named by the device and the n-th open. */
    private inner class FakeClient(val name: String) : ApiKeysClient {
        val lists = ArrayDeque<(Result<List<ApiKeyEntry>>) -> Unit>()
        val creates = ArrayDeque<(Result<CreatedApiKey>) -> Unit>()
        val deletes = ArrayDeque<(Result<Unit>) -> Unit>()

        override fun list(done: (Result<List<ApiKeyEntry>>) -> Unit) {
            events.add("$name:list")
            lists.addLast(done)
        }

        override fun create(name: String, done: (Result<CreatedApiKey>) -> Unit) {
            events.add("${this.name}:create:$name")
            creates.addLast(done)
        }

        override fun delete(apiKeyId: String, done: (Result<Unit>) -> Unit) {
            events.add("$name:delete:$apiKeyId")
            deletes.addLast(done)
        }

        // the sdk's answers, on an sdk thread, oldest call first
        fun answerList(vararg keys: ApiKeyEntry) = lists.removeFirst()(Result.success(keys.toList()))

        fun failList() = lists.removeFirst()(Result.failure(IOException("offline")))

        fun answerCreate(name: String, apiKey: String = newKey) =
            creates.removeFirst()(Result.success(CreatedApiKey(apiKeyId = key(9).apiKeyId, name = name, apiKey = apiKey)))

        fun failCreate() = creates.removeFirst()(Result.failure(IOException("offline")))

        fun answerDelete() = deletes.removeFirst()(Result.success(Unit))

        fun failDelete() = deletes.removeFirst()(Result.failure(IOException("offline")))
    }

    private fun model() = ApiKeysScreenModel<String>(
        openClient = { device ->
            opens += 1
            val name = "$device#$opens"
            events.add("$name:open")
            FakeClient(name).also { clients[device] = it }
        },
        post = { posted.addLast(it) },
    )

    private fun runPosted() {
        while (posted.isNotEmpty()) {
            posted.removeFirst()()
        }
    }

    private fun key(index: Int, name: String = "key $index") = ApiKeyEntry(
        apiKeyId = "01a1f3c2-0000-4000-8000-00000000000$index",
        name = name,
        createTimeMillis = 1_799_000_000_000L + index * 1000L,
    )

    /** A model on device a with the screen shown and its list of [keys] loaded. */
    private fun shownModel(vararg keys: ApiKeyEntry): ApiKeysScreenModel<String> {
        val model = model()
        model.setDevice("a")
        model.setVisible(true)
        clients.getValue("a").answerList(*keys)
        runPosted()
        events.clear()
        return model
    }

    @Test
    fun loadsOnceTheScreenShowsOnADevice() {
        val model = model()
        model.setVisible(true)
        // no device yet: nothing to call
        assertEquals(listOf<String>(), events)
        assertEquals(ApiKeysBody.Progress, model.ui.body)

        model.setDevice("a")
        assertEquals(listOf("a#1:open", "a#1:list"), events)
        assertEquals(ApiKeysBody.Progress, model.ui.body)

        clients.getValue("a").answerList(key(0), key(1))
        // not on the sdk thread
        assertEquals(ApiKeysBody.Progress, model.ui.body)
        runPosted()
        assertEquals(ApiKeysBody.Rows, model.ui.body)
        assertEquals(2, model.ui.rows.size)
        assertFalse(model.ui.refreshing)
    }

    @Test
    fun aDeviceAloneLoadsNothingUntilTheScreenShows() {
        val model = model()
        model.setDevice("a")
        assertEquals(listOf("a#1:open"), events)
        model.setVisible(true)
        assertEquals(listOf("a#1:open", "a#1:list"), events)
    }

    @Test
    fun eachAppearanceLoadsAgainAndAHiddenScreenKeepsTheList() {
        val model = shownModel(key(0))
        model.setVisible(false)
        assertEquals(listOf<String>(), events)
        assertEquals(1, model.ui.rows.size)

        // back from another screen or another app: keys change on ur.io and
        // the other apps too
        model.setVisible(true)
        assertEquals(listOf("a#1:list"), events)
        // over the list, with the pull indicator
        assertEquals(1, model.ui.rows.size)
        assertTrue(model.ui.refreshing)

        clients.getValue("a").answerList(key(0), key(1))
        runPosted()
        assertEquals(2, model.ui.rows.size)
        assertFalse(model.ui.refreshing)
    }

    @Test
    fun anEmptyListSaysSo() {
        val model = shownModel()
        assertEquals(ApiKeysBody.Empty, model.ui.body)
        assertEquals(listOf<ApiKeyRowUi>(), model.ui.rows)
    }

    @Test
    fun aFailedFirstLoadOffersRetryWhichLoadsAgain() {
        val model = model()
        model.setDevice("a")
        model.setVisible(true)
        val client = clients.getValue("a")
        client.failList()
        runPosted()
        assertEquals(ApiKeysBody.LoadFailed, model.ui.body)

        events.clear()
        model.refresh()
        assertEquals(listOf("a#1:list"), events)
        // the retry shows progress again
        assertEquals(ApiKeysBody.Progress, model.ui.body)

        client.answerList(key(0))
        runPosted()
        assertEquals(ApiKeysBody.Rows, model.ui.body)
    }

    @Test
    fun aFailedReloadKeepsTheRowsAndSaysSo() {
        val model = shownModel(key(0), key(1))
        model.refresh()
        assertEquals(listOf("a#1:list"), events)
        clients.getValue("a").failList()
        runPosted()
        assertEquals(ApiKeysBody.Rows, model.ui.body)
        assertEquals(2, model.ui.rows.size)
        assertTrue(model.ui.refreshFailed)

        // a reload that works clears it
        model.refresh()
        assertFalse(model.ui.refreshFailed)
        clients.getValue("a").answerList(key(0))
        runPosted()
        assertFalse(model.ui.refreshFailed)
        assertEquals(1, model.ui.rows.size)
    }

    @Test
    fun onlyTheLatestLoadApplies() {
        val model = shownModel(key(0))
        val client = clients.getValue("a")
        model.refresh()
        model.refresh()
        assertEquals(listOf("a#1:list", "a#1:list"), events)

        // the earlier load answers last, with the older list: it never shows
        val earlier = client.lists.removeFirst()
        client.answerList(key(0), key(1), key(2))
        runPosted()
        assertEquals(3, model.ui.rows.size)
        earlier(Result.success(listOf(key(0))))
        runPosted()
        assertEquals(3, model.ui.rows.size)
        assertFalse(model.ui.refreshing)
    }

    @Test
    fun anEarlierLoadsAnswerLeavesTheLatestLoadRunning() {
        val model = shownModel(key(0))
        val client = clients.getValue("a")
        model.refresh()
        model.refresh()
        client.answerList(key(0), key(1))
        runPosted()
        // the latest load still runs, so the indicator stays and the list waits
        assertTrue(model.ui.refreshing)
        assertEquals(1, model.ui.rows.size)
        client.answerList(key(0), key(1), key(2))
        runPosted()
        assertFalse(model.ui.refreshing)
        assertEquals(3, model.ui.rows.size)
    }

    @Test
    fun createAsksForANameThenShowsTheKeyOnceAndDoneDropsIt() {
        val model = shownModel(key(0))
        val client = clients.getValue("a")
        model.requestCreate()
        assertEquals(ApiKeysDialog.Create(creating = false, failed = false), model.dialog)
        assertEquals(listOf<String>(), events)

        // the name the field holds, without its outer spaces
        model.create("  home server  ")
        assertEquals(listOf("a#1:create:home server"), events)
        assertEquals(ApiKeysDialog.Create(creating = true, failed = false), model.dialog)

        client.answerCreate("home server")
        runPosted()
        val created = model.dialog as ApiKeysDialog.Created
        assertEquals(newKey, created.apiKey)
        assertEquals("home server", created.name)
        // and the list loads again for the new key's row
        assertEquals(listOf("a#1:create:home server", "a#1:list"), events)

        model.dismissDialog()
        assertNull(model.dialog)
    }

    @Test
    fun theNewKeyLivesOnlyInItsDialog() {
        val model = shownModel(key(0))
        val client = clients.getValue("a")
        model.requestCreate()
        model.create("home server")
        client.answerCreate("home server")
        runPosted()
        client.answerList(key(0), key(9, name = "home server"))
        runPosted()

        // the list shows the key's name and id, never the key
        assertFalse(model.ui.toString().contains(newKey))
        assertEquals(listOf(key(9).apiKeyId, key(0).apiKeyId), model.ui.rows.map { it.apiKeyId })
        // and no log or crash report that prints the dialog or the answer prints it
        val created = model.dialog as ApiKeysDialog.Created
        assertFalse(created.toString().contains(newKey))
        assertFalse(CreatedApiKey(apiKeyId = "id", name = "home server", apiKey = newKey).toString().contains(newKey))

        model.dismissDialog()
        assertNull(model.dialog)
        assertFalse(model.ui.toString().contains(newKey))
    }

    @Test
    fun nothingOpensOverTheNewKey() {
        val model = shownModel(key(0))
        model.requestCreate()
        model.create("ci")
        clients.getValue("a").answerCreate("ci")
        runPosted()
        val created = model.dialog as ApiKeysDialog.Created
        // a stray Create API key or a row's Delete would replace it, and the key
        // with it
        model.requestCreate()
        model.requestDelete(key(0).apiKeyId)
        assertTrue(model.dialog === created)
        model.dismissDialog()
        assertNull(model.dialog)
    }

    @Test
    fun aBlankNameSendsNothing() {
        val model = shownModel()
        model.requestCreate()
        model.create("")
        model.create("   ")
        model.create("\n\t")
        assertEquals(listOf<String>(), events)
        assertEquals(ApiKeysDialog.Create(creating = false, failed = false), model.dialog)
    }

    @Test
    fun aNameOverTheServersLimitIsCutToIt() {
        val model = shownModel()
        model.requestCreate()
        model.create("n".repeat(200))
        assertEquals(listOf("a#1:create:" + "n".repeat(API_KEY_NAME_MAX_LENGTH)), events)
    }

    @Test
    fun aCreateIsNotSentTwice() {
        val model = shownModel()
        model.requestCreate()
        model.create("ci")
        // a second tap of Create, or Done on the keyboard, while it runs
        model.create("ci")
        assertEquals(listOf("a#1:create:ci"), events)
    }

    @Test
    fun theFormStaysWhileItsCreateRuns() {
        val model = shownModel()
        model.requestCreate()
        model.create("ci")
        // back, a tap outside or Cancel: the answer holds the key, which a
        // dismissed form would lose
        model.dismissDialog()
        assertEquals(ApiKeysDialog.Create(creating = true, failed = false), model.dialog)

        clients.getValue("a").answerCreate("ci")
        runPosted()
        assertEquals(newKey, (model.dialog as ApiKeysDialog.Created).apiKey)
    }

    @Test
    fun cancelOnTheFormCreatesNothing() {
        val model = shownModel()
        model.requestCreate()
        model.dismissDialog()
        assertNull(model.dialog)
        // the form is gone, so a late Create does nothing
        model.create("ci")
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun aFailedCreateSaysSoAndCreateTriesAgain() {
        val model = shownModel()
        val client = clients.getValue("a")
        model.requestCreate()
        model.create("ci")
        client.failCreate()
        runPosted()
        assertEquals(ApiKeysDialog.Create(creating = false, failed = true), model.dialog)
        // the list loads again, in case the server made the key before failing
        assertEquals(listOf("a#1:create:ci", "a#1:list"), events)

        model.create("ci")
        assertEquals(ApiKeysDialog.Create(creating = true, failed = false), model.dialog)
        client.answerCreate("ci")
        runPosted()
        assertEquals(newKey, (model.dialog as ApiKeysDialog.Created).apiKey)

        // and the failed form can be canceled
        model.dismissDialog()
        model.requestCreate()
        model.create("ci2")
        client.failCreate()
        runPosted()
        model.dismissDialog()
        assertNull(model.dialog)
    }

    @Test
    fun anAnswerWithoutAKeyIsAFailedCreate() {
        val model = shownModel()
        model.requestCreate()
        model.create("ci")
        clients.getValue("a").answerCreate("ci", apiKey = "")
        runPosted()
        assertEquals(ApiKeysDialog.Create(creating = false, failed = true), model.dialog)
    }

    @Test
    fun deleteAsksFirstNamingTheKey() {
        val model = shownModel(key(0), key(1))
        model.requestDelete(key(1).apiKeyId)
        assertEquals(listOf<String>(), events)
        val confirmation = model.dialog as ApiKeysDialog.ConfirmDelete
        assertEquals(key(1).apiKeyId, confirmation.row.apiKeyId)
        assertEquals("key 1", confirmation.row.name)

        // an id with no row asks nothing
        model.dismissDialog()
        model.requestDelete("not-a-listed-id")
        assertNull(model.dialog)
    }

    @Test
    fun cancelDeletesNothing() {
        val model = shownModel(key(0))
        model.requestDelete(key(0).apiKeyId)
        model.dismissDialog()
        assertNull(model.dialog)
        model.confirmDelete()
        assertEquals(listOf<String>(), events)
        assertFalse(model.ui.rows.single().deleting)
    }

    @Test
    fun confirmDeletesAndTheRowShowsProgressUntilTheAnswer() {
        val model = shownModel(key(0), key(1))
        val client = clients.getValue("a")
        val target = key(0).apiKeyId
        model.requestDelete(target)
        model.confirmDelete()
        assertNull(model.dialog)
        assertEquals(listOf("a#1:delete:$target"), events)
        // at once: progress and no control on the row
        assertTrue(model.ui.rows.single { it.apiKeyId == target }.deleting)

        client.answerDelete()
        runPosted()
        assertEquals(listOf(key(1).apiKeyId), model.ui.rows.map { it.apiKeyId })
        // and the list loads again
        assertEquals(listOf("a#1:delete:$target", "a#1:list"), events)
    }

    @Test
    fun aRepeatedDeleteIsSuppressed() {
        val model = shownModel(key(0))
        val target = key(0).apiKeyId
        model.requestDelete(target)
        model.confirmDelete()
        // a second tap of the dialog's button finds no confirmation
        model.confirmDelete()
        // the row's delete runs: no new confirmation
        model.requestDelete(target)
        assertNull(model.dialog)
        assertEquals(listOf("a#1:delete:$target"), events)
    }

    @Test
    fun aFailedDeleteShowsOnItsRowAndCanBeTriedAgain() {
        val model = shownModel(key(0), key(1))
        val client = clients.getValue("a")
        val target = key(1).apiKeyId
        model.requestDelete(target)
        model.confirmDelete()
        client.failDelete()
        runPosted()
        val row = model.ui.rows.single { it.apiKeyId == target }
        assertTrue(row.deleteFailed)
        assertFalse(row.deleting)
        // only on its own row
        assertEquals(
            listOf(key(1).apiKeyId to true, key(0).apiKeyId to false),
            model.ui.rows.map { it.apiKeyId to it.deleteFailed },
        )

        // a reload that still lists it keeps the failure on the row
        model.refresh()
        client.answerList(key(0), key(1))
        runPosted()
        assertTrue(model.ui.rows.single { it.apiKeyId == target }.deleteFailed)

        events.clear()
        model.requestDelete(target)
        model.confirmDelete()
        assertEquals(listOf("a#1:delete:$target"), events)
        // the retry runs: progress and no error
        val retrying = model.ui.rows.single { it.apiKeyId == target }
        assertTrue(retrying.deleting)
        assertFalse(retrying.deleteFailed)
    }

    @Test
    fun aDeletedKeyStaysGoneWhenAnEarlierLoadAnswersAfter() {
        val model = shownModel(key(0), key(1))
        val client = clients.getValue("a")
        // a load runs while the delete does
        model.refresh()
        val target = key(0).apiKeyId
        model.requestDelete(target)
        model.confirmDelete()
        client.answerDelete()
        runPosted()

        // the earlier load answers with the key it read before the delete
        val earlier = client.lists.removeFirst()
        earlier(Result.success(listOf(key(0), key(1))))
        runPosted()
        assertEquals(listOf(key(1).apiKeyId), model.ui.rows.map { it.apiKeyId })

        client.answerList(key(1))
        runPosted()
        assertEquals(listOf(key(1).apiKeyId), model.ui.rows.map { it.apiKeyId })
    }

    @Test
    fun switchingAccountsDropsTheOlderAccountsListDialogAndNewKey() {
        val model = shownModel(key(0), key(1))
        val older = clients.getValue("a")
        model.requestCreate()
        model.create("ci")
        older.answerCreate("ci")
        runPosted()
        assertTrue(model.dialog is ApiKeysDialog.Created)
        events.clear()

        // the account switches with the new key still showing
        model.setDevice("b")
        assertNull(model.dialog)
        assertEquals(listOf("b#2:open", "b#2:list"), events)
        assertEquals(ApiKeysBody.Progress, model.ui.body)

        val newer = clients.getValue("b")
        newer.answerList(key(5))
        runPosted()
        assertEquals(listOf(key(5).apiKeyId), model.ui.rows.map { it.apiKeyId })

        // the older account's answers, delivered after, never show
        older.answerList(key(0), key(1))
        runPosted()
        assertEquals(listOf(key(5).apiKeyId), model.ui.rows.map { it.apiKeyId })
    }

    @Test
    fun aCreateAnsweredAfterAnAccountSwitchNeverShowsItsKey() {
        val model = shownModel(key(0))
        val older = clients.getValue("a")
        model.requestCreate()
        model.create("ci")
        model.setDevice("b")
        clients.getValue("b").answerList()
        runPosted()

        older.answerCreate("ci")
        runPosted()
        assertNull(model.dialog)
        assertEquals(ApiKeysBody.Empty, model.ui.body)

        // and nothing deletes on the newer account from the older dialog
        events.clear()
        model.confirmDelete()
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun aDeleteAnsweredAfterAnAccountSwitchChangesNothing() {
        val model = shownModel(key(0))
        val older = clients.getValue("a")
        model.requestDelete(key(0).apiKeyId)
        model.confirmDelete()
        model.setDevice("b")
        val newer = clients.getValue("b")
        newer.answerList(key(0))
        runPosted()

        older.failDelete()
        runPosted()
        assertFalse(model.ui.rows.single().deleteFailed)
        assertFalse(model.ui.rows.single().deleting)
    }

    @Test
    fun signingOutClearsEverything() {
        val model = shownModel(key(0))
        model.requestDelete(key(0).apiKeyId)
        model.setDevice(null)
        assertNull(model.dialog)
        assertEquals(ApiKeysBody.Progress, model.ui.body)
        // no device: nothing to call
        events.clear()
        model.refresh()
        model.requestCreate()
        assertNull(model.dialog)
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun nothingUpdatesAfterTheScreenCloses() {
        val model = shownModel(key(0))
        val client = clients.getValue("a")
        model.requestCreate()
        model.create("ci")
        model.refresh()

        // the screen is gone before the answers arrive
        model.close()
        assertNull(model.dialog)
        assertEquals(ApiKeysBody.Progress, model.ui.body)
        client.answerCreate("ci")
        client.answerList(key(0), key(1))
        runPosted()
        assertNull(model.dialog)
        assertEquals(ApiKeysBody.Progress, model.ui.body)

        // nor does the screen's dispose after the view model cleared
        events.clear()
        model.setVisible(false)
        model.setVisible(true)
        model.setDevice("b")
        model.refresh()
        model.requestCreate()
        assertEquals(listOf<String>(), events)
        assertNull(model.dialog)
    }
}
