package com.bringyour.network.ui.account

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine behind SessionsViewModel (REVOKE-UI-FINAL.md §4-§6, §10,
 * session1118 C.md §5 C-D) over a fake controller: the lifecycle forwarding
 * (the background keeps the controller and a pending sign out), the group
 * headers and Show more, sign out through the confirmation, pending and
 * failed sign outs, the races between deliveries and account switches, and
 * that nothing updates once a controller closed.
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

        // the rows the sdk lays out for the next header tap or Show more,
        // before the call returns (presentation only: no request)
        var nextLayout: SessionsSnapshot? = null

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

        override fun setForeground(foreground: Boolean) {
            events.add("$name:foreground:$foreground")
        }

        override fun refresh() {
            events.add("$name:refresh")
        }

        override fun setGroupExpanded(kind: String, expanded: Boolean) {
            events.add("$name:group:$kind:$expanded")
            nextLayout?.let { current = it }
            nextLayout = null
        }

        override fun showMoreSessions(kind: String) {
            events.add("$name:more:$kind")
            nextLayout?.let { current = it }
            nextLayout = null
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

    // a loaded list of device sign-ins as the sdk lays it out: Sign out all
    // other sessions signs out each one but the current
    private fun loaded(vararg sessions: SessionEntry) = SessionsSnapshot.Initial.copy(
        sessions = sessions.toList(),
        sessionCount = sessions.size.toLong(),
        otherSessionCount = sessions.count { !it.current }.toLong(),
        currentSessionId = sessions.firstOrNull { it.current }?.sessionId,
        legacyCoverage = "complete",
        loaded = true,
    )

    private val threeSessions = loaded(session(0, current = true), session(1), session(2))

    // two hosted proxies of a network that has 120, below this session
    private val proxies = listOf(session(3), session(4))

    private fun withProxies(expanded: Boolean) = loaded(session(0, current = true)).copy(
        groups = listOf(
            SessionGroupEntry(
                kind = Sdk.ClientSessionGroupHostedProxies,
                count = 120,
                expanded = expanded,
                sessions = if (expanded) proxies else listOf(),
                hasMore = expanded,
            )
        ),
        // the sign out of the others keeps the hosted proxies
        otherSessionCount = 0,
    )

    @Test
    fun opensOnTheDeviceAndStartsOnlyWhenTheScreenAppears() {
        val model = model()
        model.setVisible(true)
        model.setForeground(true)
        // no device yet: nothing opens
        assertEquals(listOf<String>(), events)
        assertEquals(SessionsBody.Progress, model.ui.body)

        model.setDevice("a")
        // on appear: Start, then SetVisible(true)
        assertEquals(listOf("a#1:open", "a#1:start", "a#1:visible:true"), events)

        // a device while the screen is hidden opens without starting
        val hidden = model()
        events.clear()
        hidden.setDevice("b")
        assertEquals(listOf("b#2:open"), events)
        hidden.setVisible(true)
        assertEquals(listOf("b#2:open", "b#2:start", "b#2:visible:true"), events)
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
    fun theForegroundGoesToTheControllerWhichTheBackgroundKeeps() {
        val model = shownModel()
        controllers.getValue("a").publish(threeSessions)
        runPosted()
        events.clear()

        // §6: foreground and background go to SetForeground; nothing closes
        model.setForeground(false)
        assertEquals(listOf("a#1:foreground:false"), events)
        assertEquals(SessionsBody.Rows, model.ui.body)
        assertEquals(3, model.ui.rows.size)

        // the same controller loads again in the foreground (the sdk's
        // SetForeground(true) is that reload)
        model.setForeground(true)
        assertEquals(listOf("a#1:foreground:false", "a#1:foreground:true"), events)
        assertFalse(controllers.getValue("a").closed)
    }

    // S1 W15: the background closed the controller, and with it the operation
    // id of a sign out still pending, so its outcome was never recovered and
    // the row came back as if nothing had been asked
    @Test
    fun aPendingSignOutSurvivesTheBackground() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        val target = threeSessions.sessions[2].sessionId
        model.requestSignOut(target)
        model.confirm()
        // accepted (202): pending until the controller confirms enforcement
        controller.publish(
            threeSessions.copy(actions = listOf(SessionActionState(target, loading = false, pending = true, error = null)))
        )
        runPosted()
        events.clear()

        // the app goes to the background: the activity stops, which hides the
        // screen, then the process
        model.setVisible(false)
        model.setForeground(false)
        assertEquals(listOf("a#1:visible:false", "a#1:foreground:false"), events)
        assertFalse(controller.closed)
        assertTrue(model.ui.row(target)!!.signingOut)

        // back: the same controller, still recovering the same sign out
        model.setForeground(true)
        model.setVisible(true)
        assertEquals(
            listOf("a#1:visible:false", "a#1:foreground:false", "a#1:foreground:true", "a#1:start", "a#1:visible:true"),
            events,
        )
        assertEquals(1, opens)
        assertTrue(model.ui.row(target)!!.signingOut)

        // the controller confirms the outcome and drops the row
        controller.publish(loaded(session(0, current = true), session(1)))
        runPosted()
        assertNull(model.ui.row(target))
    }

    @Test
    fun aGroupHeaderAndShowMoreGoToTheControllerAndShowItsLayoutAtOnce() {
        val model = model()
        // no controller yet: nothing to open
        model.setGroupExpanded(Sdk.ClientSessionGroupHostedProxies, true)
        model.showMoreSessions(Sdk.ClientSessionGroupSignIns)
        assertEquals(listOf<String>(), events)

        model.setDevice("a")
        model.setForeground(true)
        model.setVisible(true)
        val controller = controllers.getValue("a")
        controller.publish(withProxies(expanded = false))
        runPosted()
        events.clear()
        val collapsed = model.ui.groups.single()
        assertFalse(collapsed.expanded)
        assertEquals(listOf<SessionRowUi>(), collapsed.rows)

        // the header's tap: the sdk opens the group and lays its rows out
        controller.nextLayout = withProxies(expanded = true)
        model.setGroupExpanded(collapsed.kind, !collapsed.expanded)
        assertEquals(listOf("a#1:group:hosted_proxies:true"), events)
        val open = model.ui.groups.single()
        assertTrue(open.expanded)
        assertEquals(proxies.map { it.sessionId }, open.rows.map { it.sessionId })
        assertTrue(open.hasMore)

        // Show more under the group, and under the device sign-ins
        model.showMoreSessions(open.kind)
        model.showMoreSessions(Sdk.ClientSessionGroupSignIns)
        assertEquals(
            listOf("a#1:group:hosted_proxies:true", "a#1:more:hosted_proxies", "a#1:more:sign_ins"),
            events,
        )

        // and the tap again closes it
        controller.nextLayout = withProxies(expanded = false)
        model.setGroupExpanded(open.kind, false)
        assertEquals(listOf<SessionRowUi>(), model.ui.groups.single().rows)
    }

    @Test
    fun aGroupsRowSignsOutThroughTheConfirmation() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(withProxies(expanded = true))
        runPosted()
        events.clear()
        // only hosted proxies besides this session: nothing for Sign out all others
        assertNull(model.ui.signOutOthers)
        model.requestSignOutOthers()
        assertNull(model.confirmation)

        val target = proxies[1].sessionId
        model.requestSignOut(target)
        val confirmation = model.confirmation as SessionsConfirmation.SignOut
        assertEquals(SessionClientGroup.HostedProxies, confirmation.row.group)
        model.confirm()
        assertEquals(listOf("a#1:revoke:$target"), events)
        // its row shows the sign out running, as a device sign-in's does
        assertTrue(model.ui.row(target)!!.signingOut)
        model.requestSignOut(target)
        assertNull(model.confirmation)
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
    fun aFailedSignOutOfAllOthersSaysSoAndIsTriedAgainThroughTheController() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        model.requestSignOutOthers()
        model.confirm()

        // the controller gave up on it
        val error = SessionErrorFlags(retryable = false, signInRequired = false, unsupported = false)
        controller.publish(
            threeSessions.copy(bulkAction = SessionActionState(null, loading = false, pending = false, error = error))
        )
        runPosted()
        val failed = model.ui.signOutOthers!!
        assertEquals(SignOutOthersUi(signingOut = false, failed = true), failed)
        assertEquals(R.string.sessions_sign_out_others_failed, failed.errorRes)

        // the button asks again, and the retry is the controller's
        events.clear()
        model.requestSignOutOthers()
        assertEquals(SessionsConfirmation.SignOutOthers, model.confirmation)
        model.confirm()
        assertEquals(listOf("a#1:revoke-others"), events)
        assertTrue(model.ui.signOutOthers!!.signingOut)
        assertNull(model.ui.signOutOthers!!.errorRes)
    }

    @Test
    fun signInRequiredReplacesTheListAndSignsNothingOut() {
        val model = shownModel()
        val controller = controllers.getValue("a")
        controller.publish(threeSessions)
        runPosted()
        events.clear()

        // the account's sign-in was rejected: the sdk reports sign-in required
        // until a new sign-in
        val signIn = SessionErrorFlags(retryable = false, signInRequired = true, unsupported = false)
        controller.publish(threeSessions.copy(error = signIn))
        runPosted()
        assertEquals(SessionsBody.SignInRequired, model.ui.body)
        assertEquals(R.string.sessions_sign_in_required, model.ui.body.messageRes)
        assertEquals(listOf<SessionRowUi>(), model.ui.rows)

        // nothing left to sign out from here
        model.requestSignOut(threeSessions.sessions[1].sessionId)
        model.requestSignOutOthers()
        assertNull(model.confirmation)
        assertEquals(listOf<String>(), events)

        // the trusted cause: another device signed this session out
        controller.publish(SessionsSnapshot.Initial.copy(error = signIn.copy(sessionRevoked = true)))
        runPosted()
        assertEquals(SessionsBody.SignedOutRemotely, model.ui.body)
        assertEquals(R.string.sessions_signed_out_remotely, model.ui.body.messageRes)
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
