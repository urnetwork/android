package com.bringyour.network.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine behind SessionsViewModel (REVOKE-UI-FINAL.md §4-§6, §10)
 * over a fake controller: the lifecycle forwarding, sign out through the
 * confirmation, pending and failed sign outs, the races between deliveries
 * and account switches, and that nothing updates once a controller closed.
 *
 * Changes are posted, as the view model posts the sdk thread's changes to the
 * main thread; each test runs the posted work explicitly, in the order it
 * needs, with no time or threads involved.
 */
class SessionsScreenModelTest {

    private val events = mutableListOf<String>()
    private val posted = ArrayDeque<() -> Unit>()
    private val controllers = mutableMapOf<String, FakeController>()
    private var clockMillis = 1_800_000_000_000L

    /** One open controller, named by the device it opened on, and the n-th open of it. */
    private inner class FakeController(val name: String) : SessionsController {
        var current = SessionsSnapshot.Initial
        lateinit var onChange: () -> Unit
        var closed = false

        override fun snapshot(): SessionsSnapshot {
            // a closed controller is never read
            check(!closed) { "$name read after close" }
            return current
        }

        override fun start() {
            events.add("$name:start")
        }

        override fun setVisible(visible: Boolean) {
            events.add("$name:visible:$visible")
        }

        override fun refresh() {
            events.add("$name:refresh")
        }

        override fun revokeSession(sessionId: String) {
            events.add("$name:revoke:$sessionId")
            // the sdk marks the action loading before it returns
            current = current.copy(
                actions = current.actions + SessionActionState(sessionId, loading = true, pending = false, error = null)
            )
        }

        override fun revokeOtherSessions() {
            events.add("$name:revoke-others")
            current = current.copy(
                bulkAction = SessionActionState(null, loading = true, pending = false, error = null)
            )
        }

        override fun close() {
            closed = true
            events.add("$name:close")
        }

        // the sdk's listener: a change on an sdk thread
        fun publish(snapshot: SessionsSnapshot) {
            current = snapshot
            onChange()
        }
    }

    private var opens = 0

    private fun model() = SessionsScreenModel<String>(
        openController = { device, onChange ->
            opens += 1
            val name = "$device#$opens"
            events.add("$name:open")
            FakeController(name).also {
                it.onChange = onChange
                controllers[device] = it
            }
        },
        post = { posted.addLast(it) },
        clock = { clockMillis },
    )

    private fun runPosted() {
        while (posted.isNotEmpty()) {
            posted.removeFirst()()
        }
    }

    /** A model on device a, in the foreground, with the screen visible. */
    private fun shownModel(): SessionsScreenModel<String> {
        val model = model()
        model.setDevice("a")
        model.setForeground(true)
        model.setVisible(true)
        return model
    }

    private fun session(index: Int, current: Boolean = false) = SessionEntry(
        sessionId = "00000000-0000-4000-8000-00000000000$index",
        current = current,
        kind = "password",
        createTimeMillis = 1_799_000_000_000L,
        lastUse = SessionLastUse(1_799_999_000L, "", "", "", "", "linux", ""),
    )

    private fun loaded(vararg sessions: SessionEntry) = SessionsSnapshot.Initial.copy(
        sessions = sessions.toList(),
        currentSessionId = sessions.firstOrNull { it.current }?.sessionId,
        legacyCoverage = "complete",
        loaded = true,
    )

    private val threeSessions = loaded(session(0, current = true), session(1), session(2))

    @Test
    fun opensOnTheDeviceInTheForegroundAndStartsOnlyWhenTheScreenAppears() {
        val model = model()
        model.setVisible(true)
        model.setForeground(true)
        // no device yet: nothing opens
        assertEquals(listOf<String>(), events)
        assertEquals(SessionsBody.Progress, model.ui.body)

        model.setDevice("a")
        // on appear: Start, then SetVisible(true)
        assertEquals(listOf("a#1:open", "a#1:start", "a#1:visible:true"), events)
    }

    @Test
    fun aHiddenScreenStopsPollingAndKeepsTheList() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()

        model.setVisible(false)
        assertEquals(listOf("a#1:visible:false"), events)
        assertEquals(3, model.ui.rows.size)

        // shown again: the same controller polls again
        model.setVisible(true)
        assertEquals(listOf("a#1:visible:false", "a#1:start", "a#1:visible:true"), events)
    }

    @Test
    fun theBackgroundClosesTheControllerAndTheForegroundLoadsANewOne() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()

        model.setForeground(false)
        assertEquals(listOf("a#1:close"), events)
        assertEquals(SessionsBody.Progress, model.ui.body)

        model.setForeground(true)
        assertEquals(listOf("a#1:close", "a#2:open", "a#2:start", "a#2:visible:true"), events)
    }

    @Test
    fun pullToRefreshAndTryAgainRefreshTheController() {
        val model = model()
        // nothing open: nothing to refresh
        model.refresh()
        assertEquals(listOf<String>(), events)

        model.setDevice("a")
        model.setForeground(true)
        model.setVisible(true)
        events.clear()
        model.refresh()
        assertEquals(listOf("a#1:refresh"), events)
    }

    @Test
    fun theControllersChangesShowAfterThePost() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(controller.current.copy(loading = true))
        assertEquals(SessionsBody.Progress, model.ui.body)
        controller.publish(threeSessions)
        // not on the sdk thread
        assertEquals(SessionsBody.Progress, model.ui.body)
        runPosted()
        assertEquals(SessionsBody.Rows, model.ui.body)
        assertEquals(3, model.ui.rows.size)
    }

    @Test
    fun deliveriesOutOfOrderNeverShowAnOlderSnapshot() {
        // two sdk threads deliver their changes in the reverse order of the
        // changes; each post reads the controller's latest snapshot
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(loaded(session(0, current = true), session(1)))
        val older = posted.removeLast()
        controller.publish(threeSessions)
        val newer = posted.removeLast()

        newer()
        assertEquals(3, model.ui.rows.size)
        older()
        assertEquals(3, model.ui.rows.size)
    }

    @Test
    fun nothingUpdatesAfterTheScreenCloses() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        events.clear()

        // the screen is gone before the posted change runs
        model.close()
        assertEquals(listOf("a#1:close"), events)
        runPosted()
        assertEquals(SessionsBody.Progress, model.ui.body)
        assertEquals(listOf<String>(), model.ui.rows)

        // and a late change of the closed controller does nothing
        controller.onChange()
        runPosted()
        assertEquals(SessionsBody.Progress, model.ui.body)

        // nor does the screen's dispose after the view model cleared
        model.setVisible(false)
        model.setForeground(true)
        model.setDevice("b")
        assertEquals(listOf("a#1:close"), events)
    }

    @Test
    fun swipeThenConfirmSignsTheSessionOut() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()

        val target = threeSessions.sessions[1]
        model.requestSignOut(target.sessionId)
        // asks first
        assertEquals(listOf<String>(), events)
        val confirmation = model.confirmation as SessionsConfirmation.SignOut
        assertEquals(target.sessionId, confirmation.row.sessionId)

        model.confirm()
        assertNull(model.confirmation)
        assertEquals(listOf("a#1:revoke:${target.sessionId}"), events)
        // at once: progress and no control on the row
        assertTrue(model.ui.rows[1].signingOut)
    }

    @Test
    fun cancelSignsNothingOut() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()

        model.requestSignOut(threeSessions.sessions[2].sessionId)
        model.dismissConfirmation()
        assertNull(model.confirmation)
        model.confirm()
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun aRepeatedSignOutIsSuppressed() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()
        val target = threeSessions.sessions[1].sessionId

        model.requestSignOut(target)
        model.confirm()
        // a second tap of the dialog's button finds no confirmation
        model.confirm()
        // the row's sign out is running: no new confirmation
        model.requestSignOut(target)
        assertNull(model.confirmation)
        assertEquals(listOf("a#1:revoke:$target"), events)
    }

    @Test
    fun aSignOutThatStartedWhileAskingIsNotSentAgain() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        val target = threeSessions.sessions[1].sessionId
        model.requestSignOut(target)

        // another path started the same sign out before the user confirmed
        controller.publish(
            threeSessions.copy(actions = listOf(SessionActionState(target, loading = false, pending = true, error = null)))
        )
        runPosted()
        events.clear()
        model.confirm()
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun aPendingSignOutShowsProgressUntilTheControllerConfirmsIt() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        val target = threeSessions.sessions[2].sessionId
        model.requestSignOut(target)
        model.confirm()

        // accepted (202): pending
        controller.publish(
            threeSessions.copy(actions = listOf(SessionActionState(target, loading = false, pending = true, error = null)))
        )
        runPosted()
        assertTrue(model.ui.rows[2].signingOut)
        // a later poll that still lists it keeps the progress
        clockMillis += 30_000
        runPosted()
        assertTrue(model.ui.rows.single { it.sessionId == target }.signingOut)

        // the controller confirms enforcement by dropping the row
        controller.publish(
            loaded(session(0, current = true), session(1)).copy(
                actions = listOf(SessionActionState(target, loading = false, pending = false, error = null))
            )
        )
        runPosted()
        assertEquals(listOf(threeSessions.sessions[0].sessionId, threeSessions.sessions[1].sessionId), model.ui.rows.map { it.sessionId })
    }

    @Test
    fun aFailedSignOutShowsOnItsRowAndCanBeTriedAgain() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        val target = threeSessions.sessions[1].sessionId
        val error = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        controller.publish(
            threeSessions.copy(actions = listOf(SessionActionState(target, loading = false, pending = false, error = error)))
        )
        runPosted()
        assertTrue(model.ui.rows[1].actionFailed)
        assertTrue(!model.ui.rows[1].signingOut)

        events.clear()
        model.requestSignOut(target)
        model.confirm()
        assertEquals(listOf("a#1:revoke:$target"), events)
    }

    @Test
    fun theCurrentSessionCanBeSignedOutWithTheSelfWarning() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()
        val current = threeSessions.sessions[0].sessionId

        model.requestSignOut(current)
        val confirmation = model.confirmation as SessionsConfirmation.SignOut
        assertTrue(confirmation.row.current)
        model.confirm()
        // the sdk signs this app out on success; the app's logout flow follows
        assertEquals(listOf("a#1:revoke:$current"), events)
    }

    @Test
    fun signOutOfAllOthersAsksThenSignsThemOut() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        // only this session: not offered
        controller.publish(loaded(session(0, current = true)))
        runPosted()
        model.requestSignOutOthers()
        assertNull(model.confirmation)

        controller.publish(threeSessions)
        runPosted()
        events.clear()
        model.requestSignOutOthers()
        assertEquals(SessionsConfirmation.SignOutOthers, model.confirmation)
        assertEquals(listOf<String>(), events)

        model.confirm()
        assertEquals(listOf("a#1:revoke-others"), events)
        assertTrue(model.ui.signOutOthers!!.signingOut)
        // running: no second confirmation
        model.requestSignOutOthers()
        assertNull(model.confirmation)
    }

    @Test
    fun switchingAccountsDropsTheOlderAccountsListConfirmationAndLateChanges() {
        val model = shownModel()
        val older = controllers.getValue("a")
        older.publish(threeSessions)
        runPosted()
        model.requestSignOut(threeSessions.sessions[1].sessionId)
        events.clear()

        // the account switches while the older list's change is in flight
        older.current = loaded(session(5, current = true), session(6))
        older.onChange()
        model.setDevice("b")
        assertEquals(listOf("a#1:close", "b#2:open", "b#2:start", "b#2:visible:true"), events)
        assertNull(model.confirmation)

        val newer = controllers.getValue("b")
        newer.publish(loaded(session(7, current = true)))
        runPosted()
        assertEquals(listOf(session(7).sessionId), model.ui.rows.map { it.sessionId })

        // the older controller's change, delivered after, never shows
        older.onChange()
        runPosted()
        assertEquals(listOf(session(7).sessionId), model.ui.rows.map { it.sessionId })

        // and nothing signs out on the newer account from the older dialog
        model.confirm()
        assertEquals(listOf("a#1:close", "b#2:open", "b#2:start", "b#2:visible:true"), events)
    }

    @Test
    fun errorsUseTheFlags() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        val unsupported = SessionErrorFlags(retryable = false, signInRequired = false, unsupported = true)
        controller.publish(SessionsSnapshot.Initial.copy(supported = false, error = unsupported))
        runPosted()
        assertEquals(SessionsBody.Unsupported, model.ui.body)

        val retryable = SessionErrorFlags(retryable = true, signInRequired = false, unsupported = false)
        controller.publish(SessionsSnapshot.Initial.copy(error = retryable))
        runPosted()
        assertEquals(SessionsBody.LoadFailed, model.ui.body)

        // a retryable refresh error keeps the last list
        controller.publish(threeSessions.copy(error = retryable))
        runPosted()
        assertEquals(SessionsBody.Rows, model.ui.body)
        assertEquals(3, model.ui.rows.size)
        assertTrue(model.ui.refreshFailed)
    }

    @Test
    fun relativeTimesCountFromTheLatestChange() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        assertEquals(clockMillis, model.ui.nowMillis)

        // the controller's poll 30 seconds later moves the times on
        clockMillis += 30_000
        controller.publish(threeSessions)
        runPosted()
        assertEquals(clockMillis, model.ui.nowMillis)
    }
}
