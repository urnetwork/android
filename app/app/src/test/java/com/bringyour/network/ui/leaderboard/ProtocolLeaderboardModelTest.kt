package com.bringyour.network.ui.leaderboard

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine behind ProtocolLeaderboardViewModel over a fake client:
 * the first load on a client, paging pinned to one snapshot, the top 400,
 * seeks, failures and retries, the name switch, and the races between
 * answers, client changes and the close.
 *
 * Each call waits until the test answers it, as the sdk answers on its own
 * thread, and each answer is posted, as the view model posts it to the main
 * thread; the tests run the posted work explicitly, in the order they need,
 * with no time or threads involved.
 */
class ProtocolLeaderboardModelTest {

    private val events = mutableListOf<String>()
    private val posted = ArrayDeque<() -> Unit>()

    private val settings = ProtocolLeaderboardSettings(
        pageSize = 50,
        seekPageSize = 200,
        listRange = 400,
        loadMoreThreshold = 10,
        closeMargin = 0.20,
    )

    /** One client: an account's device api, named for the events. */
    private inner class FakeClient(
        val name: String,
        override val signedIn: Boolean = true,
    ) : ProtocolTopClient {
        val tops = ArrayDeque<(Result<ProtocolTopPage>) -> Unit>()
        val mes = ArrayDeque<(Result<ProtocolTopMe>) -> Unit>()
        val namePublics = ArrayDeque<(Result<Boolean>) -> Unit>()
        val setNamePublics = ArrayDeque<(Result<Unit>) -> Unit>()

        override fun top(limit: Int, cursor: String, snapshotId: String, done: (Result<ProtocolTopPage>) -> Unit) {
            events.add("$name:top:$limit:$cursor:$snapshotId")
            tops.addLast(done)
        }

        override fun me(done: (Result<ProtocolTopMe>) -> Unit) {
            events.add("$name:me")
            mes.addLast(done)
        }

        override fun namePublic(done: (Result<Boolean>) -> Unit) {
            events.add("$name:namePublic")
            namePublics.addLast(done)
        }

        override fun setNamePublic(public: Boolean, done: (Result<Unit>) -> Unit) {
            events.add("$name:setNamePublic:$public")
            setNamePublics.addLast(done)
        }

        // the sdk's answers, on an sdk thread, oldest call first
        fun answerTop(page: ProtocolTopPage) = tops.removeFirst()(Result.success(page))

        fun failTop() = tops.removeFirst()(Result.failure(IOException("offline")))

        fun answerMe(me: ProtocolMe?, answerSnapshot: ProtocolSnapshot? = snapshot()) =
            mes.removeFirst()(Result.success(ProtocolTopMe(snapshot = answerSnapshot, me = me)))

        fun failMe() = mes.removeFirst()(Result.failure(IOException("offline")))

        fun answerNamePublic(public: Boolean) = namePublics.removeFirst()(Result.success(public))

        fun answerSetNamePublic() = setNamePublics.removeFirst()(Result.success(Unit))

        fun failSetNamePublic() = setNamePublics.removeFirst()(Result.failure(IOException("offline")))
    }

    private fun model() = ProtocolLeaderboardModel(
        settings = settings,
        post = { posted.addLast(it) },
    )

    private fun runPosted() {
        while (posted.isNotEmpty()) {
            posted.removeFirst()()
        }
    }

    private fun snapshot(
        id: String = "snap-1",
        totalRanked: Long = 1_000,
        bindingOpen: Boolean = false,
        stale: Boolean = false,
        computedAtMillis: Long? = 1_799_000_000_000L,
    ) = ProtocolSnapshot(
        id = id,
        tempoIndex = 7,
        computedAtMillis = computedAtMillis,
        totalRanked = totalRanked,
        headSeats = 200,
        claimedFleets = 3,
        openSeats = 197,
        projectedCutoff = 41.2,
        bindingOpen = bindingOpen,
        stale = stale,
    )

    private fun row(
        position: Long,
        name: String = "",
        label: ProtocolLabel? = ProtocolLabel.ESTIMATE,
        measurable: Boolean = true,
        isMe: Boolean = false,
        fleets: List<ProtocolFleet> = listOf(),
        margin: Double = (200.0 - position) / 200.0,
    ) = ProtocolRow(
        position = position,
        name = name,
        tag = "",
        projectedScore = 1000.0 / position,
        margin = margin,
        trend = 0,
        label = label,
        measurable = measurable,
        claimPrompt = measurable && position <= 200,
        isMe = isMe,
        fleets = fleets,
    )

    private fun rows(from: Long, to: Long) = (from..to).map { row(it) }

    private fun page(from: Long, to: Long, next: String?, snapshot: ProtocolSnapshot = snapshot()) =
        ProtocolTopPage(snapshot = snapshot, rows = rows(from, to), me = null, next = next)

    private fun me(row: ProtocolRow, reason: ProtocolNotMeasurableReason = ProtocolNotMeasurableReason.NONE) = ProtocolMe(
        row = row,
        soloScore = 21.4,
        distinctPrefixes = 18,
        sharedPrefixes = 3,
        sharedWithUnits = 2,
        notMeasurableReason = reason,
        history = listOf(),
    )

    /** A model on client a with the tab shown and its first page of 1..50 loaded. */
    private fun shownModel(client: FakeClient = FakeClient("a")): ProtocolLeaderboardModel {
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        client.answerTop(page(1, 50, next = "c1"))
        client.answerMe(me(row(173, isMe = true)))
        client.answerNamePublic(false)
        runPosted()
        events.clear()
        return model
    }

    @Test
    fun theFirstAppearanceOnAClientLoadsThePageTheStandingAndTheSwitch() {
        val model = model()
        model.setVisible(true)
        // no client yet: nothing to call, and nothing that never resolves
        assertEquals(listOf<String>(), events)
        assertEquals(ProtocolListBody.None, model.ui.body)

        val client = FakeClient("a")
        model.setClient(client)
        assertEquals(listOf("a:top:50::", "a:me", "a:namePublic"), events)
        assertEquals(ProtocolListBody.Progress, model.ui.body)
        assertEquals(ProtocolCard.Loading, model.ui.card)

        client.answerTop(page(1, 50, next = "c1"))
        client.answerMe(me(row(173, isMe = true)))
        // not on the sdk thread
        assertEquals(ProtocolListBody.Progress, model.ui.body)
        runPosted()
        assertEquals(ProtocolListBody.Rows, model.ui.body)
        assertEquals(50, model.ui.rows.size)
        assertTrue(model.ui.card is ProtocolCard.Standing)
        assertFalse(model.ui.refreshing)
    }

    @Test
    fun aClientAloneLoadsNothingUntilTheTabShowsAndAReturnKeepsThePlace() {
        val model = model()
        val client = FakeClient("a")
        model.setClient(client)
        assertEquals(listOf<String>(), events)
        model.setVisible(true)
        assertEquals(3, events.size)
        client.answerTop(page(1, 50, next = "c1"))
        runPosted()
        events.clear()

        // another tab and back: the rows stay and nothing reloads
        model.setVisible(false)
        model.setVisible(true)
        assertEquals(listOf<String>(), events)
        assertEquals(50, model.ui.rows.size)
    }

    @Test
    fun nextPagesAreFiftyRowsAfterTheCursorPinnedToTheFirstPagesSnapshot() {
        val client = FakeClient("a")
        val model = shownModel(client)

        // far from the end: nothing
        model.onLastVisibleRow(20)
        assertEquals(listOf<String>(), events)
        // within ten rows of the end
        model.onLastVisibleRow(39)
        assertEquals(listOf("a:top:50:c1:snap-1"), events)
        assertEquals(ProtocolListFooter.Loading, model.ui.footer)
        // asked once while it is on its way
        model.onLastVisibleRow(45)
        assertEquals(1, events.size)

        client.answerTop(page(51, 100, next = "c2"))
        runPosted()
        assertEquals(100, model.ui.rows.size)
        assertEquals((1L..100L).toList(), model.ui.rows.map { it.row.position })
        assertEquals(ProtocolListFooter.None, model.ui.footer)
    }

    @Test
    fun aFailedPageIsNotAskedForAgainByScrollingOnlyByTheRetry() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.onLastVisibleRow(49)
        client.failTop()
        runPosted()
        assertEquals(ProtocolListFooter.Failed, model.ui.footer)
        events.clear()

        // the scroll that would ask again on every layout pass is refused
        model.onLastVisibleRow(48)
        model.onLastVisibleRow(49)
        assertEquals(listOf<String>(), events)

        model.retry()
        assertEquals(listOf("a:top:50:c1:snap-1"), events)
        client.answerTop(page(51, 100, next = null))
        runPosted()
        assertEquals(100, model.ui.rows.size)
        assertFalse(model.ui.hasMoreAfter)
    }

    @Test
    fun theListEndsAtTheLastPageAndAtPosition400() {
        val client = FakeClient("a")
        val model = shownModel(client)
        // a server page that runs past the top 400 keeps only the top 400
        model.onLastVisibleRow(49)
        client.answerTop(page(51, 420, next = "c-more"))
        runPosted()
        assertEquals(400, model.ui.rows.size)
        assertEquals(400L, model.ui.rows.last().row.position)
        assertFalse(model.ui.hasMoreAfter)
        events.clear()
        model.onLastVisibleRow(399)
        assertEquals(listOf<String>(), events)
        assertEquals(400L, model.ui.indicatorTotal)
    }

    @Test
    fun repeatedOrOutOfOrderRowsAreKeptOnceInPositionOrder() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.onLastVisibleRow(49)
        val repeated = ProtocolTopPage(
            snapshot = snapshot(),
            rows = listOf(row(52), row(49), row(51), row(50), row(53)),
            me = null,
            next = "c2",
        )
        client.answerTop(repeated)
        runPosted()
        assertEquals((1L..53L).toList(), model.ui.rows.map { it.row.position })
    }

    @Test
    fun aPageFromAnotherSnapshotReloadsTheListFromTheTop() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.onLastVisibleRow(49)
        client.answerTop(page(51, 100, next = "c2", snapshot = snapshot(id = "snap-2")))
        runPosted()
        // never two tempos' rankings in one list
        assertEquals(listOf("a:top:50:c1:snap-1", "a:top:50::"), events)
        assertEquals(50, model.ui.rows.size)
        assertTrue(model.ui.refreshing)

        client.answerTop(page(1, 50, next = "d1", snapshot = snapshot(id = "snap-2")))
        runPosted()
        events.clear()
        model.onLastVisibleRow(49)
        assertEquals(listOf("a:top:50:d1:snap-2"), events)
    }

    @Test
    fun anExpiredSnapshotReloadsTheBoardFromTheTopOfTheNewest() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.onLastVisibleRow(49)
        // the cursor's snapshot is gone (kept 7 days): the server answers 410,
        // and the sdk hands over its status line and body
        client.tops.removeFirst()(Result.failure(IOException("410 Gone: The snapshot expired. Reload from the top.")))
        runPosted()
        // not a failed page that its retry would send again forever: the first
        // page of the newest snapshot, over the rows shown
        assertEquals(listOf("a:top:50:c1:snap-1", "a:top:50::"), events)
        assertEquals(ProtocolListFooter.None, model.ui.footer)
        assertEquals(50, model.ui.rows.size)
        client.answerTop(page(1, 50, next = "d1", snapshot = snapshot(id = "snap-2")))
        runPosted()
        events.clear()
        model.onLastVisibleRow(49)
        assertEquals(listOf("a:top:50:d1:snap-2"), events)
    }

    @Test
    fun anExpiredSnapshotDuringASeekReloadsAndDropsTheSeek() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.seekTo(380)
        client.tops.removeFirst()(Result.failure(IOException("410 Gone: The snapshot expired. Reload from the top.")))
        runPosted()
        assertEquals(listOf("a:top:200:c1:snap-1", "a:top:50::"), events)
        client.answerTop(page(1, 50, next = "d1", snapshot = snapshot(id = "snap-2")))
        runPosted()
        // the seek was into the old ranking: it does not page on by itself
        assertEquals(2, events.size)
    }

    @Test
    fun anyOtherFailedPageWaitsForItsRetry() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.onLastVisibleRow(49)
        client.tops.removeFirst()(Result.failure(IOException("404 Not Found: Unknown snapshot.")))
        runPosted()
        assertEquals(ProtocolListFooter.Failed, model.ui.footer)
        assertEquals(listOf("a:top:50:c1:snap-1"), events)
        // and a message that only mentions 410 is not one
        model.retry()
        client.tops.removeFirst()(Result.failure(IOException("dial tcp 192.0.2.1:410: connection refused")))
        runPosted()
        assertEquals(ProtocolListFooter.Failed, model.ui.footer)
        assertEquals(listOf("a:top:50:c1:snap-1", "a:top:50:c1:snap-1"), events)
    }

    @Test
    fun aSeekPagesWithTheLargestPageUntilItsPositionIsLoaded() {
        val client = FakeClient("a")
        val model = shownModel(client)

        // a loaded position needs nothing: the tab scrolls to it
        model.seekTo(30)
        assertEquals(listOf<String>(), events)

        model.seekTo(380)
        assertEquals(listOf("a:top:200:c1:snap-1"), events)
        client.answerTop(page(51, 250, next = "c2"))
        runPosted()
        // not there yet: the next large page follows by itself
        assertEquals(listOf("a:top:200:c1:snap-1", "a:top:200:c2:snap-1"), events)
        client.answerTop(page(251, 400, next = null))
        runPosted()
        assertEquals(400L, model.ui.rows.last().row.position)
        assertEquals(2, events.size)
    }

    @Test
    fun aSeekPastTheRankedIsClampedAndAFailedSeekStops() {
        val client = FakeClient("a")
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        client.answerTop(page(1, 50, next = "c1", snapshot = snapshot(totalRanked = 120)))
        runPosted()
        assertEquals(120L, model.ui.indicatorTotal)
        events.clear()

        model.seekTo(5_000)
        assertEquals(listOf("a:top:200:c1:snap-1"), events)
        client.failTop()
        runPosted()
        assertEquals(ProtocolListFooter.Failed, model.ui.footer)
        // the failed seek does not continue by itself
        assertEquals(1, events.size)

        // a seek is the user asking again
        model.seekTo(100)
        assertEquals(listOf("a:top:200:c1:snap-1", "a:top:200:c1:snap-1"), events)
    }

    @Test
    fun aRefreshThatFailsKeepsTheRowsUnderANotice() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.refresh()
        assertEquals(listOf("a:top:50::", "a:me", "a:namePublic"), events)
        assertTrue(model.ui.refreshing)
        client.failTop()
        client.failMe()
        runPosted()
        assertEquals(ProtocolListBody.Rows, model.ui.body)
        assertEquals(50, model.ui.rows.size)
        assertTrue(model.ui.refreshFailed)
        assertFalse(model.ui.refreshing)
        // the standing shown stays too
        assertTrue(model.ui.card is ProtocolCard.Standing)
    }

    @Test
    fun aFailedFirstLoadShowsTheRetryRowAndTheRetryShowsPlaceholdersAgain() {
        val client = FakeClient("a")
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        client.failTop()
        client.failMe()
        runPosted()
        assertEquals(ProtocolListBody.LoadFailed, model.ui.body)
        assertEquals(ProtocolCard.LoadFailed, model.ui.card)
        events.clear()

        model.retry()
        assertEquals(listOf("a:top:50::", "a:me"), events)
        assertEquals(ProtocolListBody.Progress, model.ui.body)
        assertEquals(ProtocolCard.Loading, model.ui.card)
    }

    @Test
    fun anEmptySnapshotSaysRankingsWillAppear() {
        val client = FakeClient("a")
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        client.answerTop(ProtocolTopPage(snapshot = snapshot(totalRanked = 0), rows = listOf(), me = null, next = "c1"))
        runPosted()
        assertEquals(ProtocolListBody.Empty, model.ui.body)
        assertFalse(model.ui.hasMoreAfter)
        assertEquals(0L, model.ui.indicatorTotal)
        events.clear()
        // an empty page never asks for the same nothing again
        model.onLastVisibleRow(-1)
        assertEquals(listOf<String>(), events)
    }

    @Test
    fun anotherClientDropsTheFormerAccountsBoardAndItsAnswers() {
        val a = FakeClient("a")
        val model = shownModel(a)
        model.onLastVisibleRow(49)
        model.refresh()
        val b = FakeClient("b")
        model.setClient(b)
        assertEquals(ProtocolListBody.Progress, model.ui.body)
        assertEquals(ProtocolCard.Loading, model.ui.card)
        assertEquals(listOf("a:top:50:c1:snap-1", "a:top:50::", "a:me", "a:namePublic", "b:top:50::", "b:me", "b:namePublic"), events)

        // a's late answers apply nowhere
        a.answerTop(page(51, 100, next = null))
        a.answerTop(page(1, 50, next = null, snapshot = snapshot(id = "a-snap")))
        a.answerMe(me(row(9, isMe = true)))
        a.answerNamePublic(true)
        runPosted()
        assertEquals(ProtocolListBody.Progress, model.ui.body)
        assertEquals(ProtocolCard.Loading, model.ui.card)

        b.answerTop(page(1, 50, next = null, snapshot = snapshot(id = "b-snap")))
        runPosted()
        assertEquals(50, model.ui.rows.size)
    }

    @Test
    fun noClientLeavesNothingToLoadAndTheCloseStopsEverything() {
        val a = FakeClient("a")
        val model = shownModel(a)
        model.setClient(null)
        assertEquals(ProtocolListBody.None, model.ui.body)
        assertEquals(ProtocolCard.SignedOut, model.ui.card)
        assertEquals(listOf<String>(), events)

        val b = FakeClient("b")
        model.setClient(b)
        model.close()
        b.answerTop(page(1, 50, next = null))
        runPosted()
        assertEquals(ProtocolListBody.None, model.ui.body)
        // closed: a new client and a refresh call nothing
        model.setClient(FakeClient("c"))
        model.refresh()
        assertEquals(listOf("b:top:50::", "b:me", "b:namePublic"), events)
    }

    @Test
    fun aSignedOutClientShowsThePublicListWithNoCardAndNoOwnCalls() {
        val client = FakeClient("public", signedIn = false)
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        assertEquals(listOf("public:top:50::"), events)
        assertEquals(ProtocolCard.SignedOut, model.ui.card)
        client.answerTop(page(1, 50, next = null))
        runPosted()
        assertEquals(ProtocolListBody.Rows, model.ui.body)
        assertEquals(ProtocolCard.SignedOut, model.ui.card)
        // the switch needs the network's auth
        model.setNamePublic(true)
        assertEquals(1, events.size)
    }

    @Test
    fun aNetworkWithNoRowGetsTheNotRankedCard() {
        val client = FakeClient("a")
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        client.answerMe(null)
        runPosted()
        assertEquals(
            ProtocolCard.NotRanked(ProtocolUnranked(tag = "", name = "", namePublic = null, reason = null)),
            model.ui.card,
        )
    }

    @Test
    fun anUnrankedStandingIsTheNotRankedCardWithItsReason() {
        val client = FakeClient("a")
        val model = model()
        model.setClient(client)
        model.setVisible(true)
        // what GET /sn/top/me answers for a network with no board row
        client.answerMe(
            me(
                row(0, label = ProtocolLabel.NOT_MEASURABLE, measurable = false).copy(claimPrompt = false, tag = "\uD83E\uDD8A"),
                reason = ProtocolNotMeasurableReason.NOT_PROVIDING,
            )
        )
        client.answerNamePublic(false)
        runPosted()
        assertEquals(
            ProtocolCard.NotRanked(
                ProtocolUnranked(
                    tag = "\uD83E\uDD8A",
                    name = "",
                    namePublic = false,
                    reason = ProtocolNotMeasurableReason.NOT_PROVIDING,
                )
            ),
            model.ui.card,
        )
    }

    @Test
    fun aSecondExpiredSnapshotBeforeAnyPageLandsIsAFailure() {
        val client = FakeClient("a")
        val model = shownModel(client)
        val expired = IOException("410 Gone: The snapshot expired. Reload from the top.")
        model.onLastVisibleRow(49)
        client.tops.removeFirst()(Result.failure(expired))
        runPosted()
        client.answerTop(page(1, 50, next = "d1", snapshot = snapshot(id = "snap-2")))
        runPosted()
        model.onLastVisibleRow(49)
        // the newest snapshot's cursor is refused too: no reload loop
        client.tops.removeFirst()(Result.failure(expired))
        runPosted()
        assertEquals(ProtocolListFooter.Failed, model.ui.footer)
        assertEquals(listOf("a:top:50:c1:snap-1", "a:top:50::", "a:top:50:d1:snap-2"), events)

        // a page that lands, or a refresh, makes the next expiry a reload again
        model.retry()
        client.answerTop(page(51, 100, next = "d2", snapshot = snapshot(id = "snap-2")))
        runPosted()
        model.onLastVisibleRow(99)
        client.tops.removeFirst()(Result.failure(expired))
        runPosted()
        assertEquals("a:top:50::", events.last())
        assertEquals(ProtocolListFooter.None, model.ui.footer)
    }

    @Test
    fun theStatusOfAFailureIsItsLeadingStatusLine() {
        assertEquals(410, protocolHttpStatus("410 Gone: The snapshot expired. Reload from the top."))
        assertEquals(404, protocolHttpStatus("404 Not Found: Unknown snapshot."))
        assertEquals(null, protocolHttpStatus("dial tcp 192.0.2.1:410: connection refused"))
        assertEquals(null, protocolHttpStatus("4100 too many digits"))
        assertEquals(null, protocolHttpStatus(""))
        assertEquals(null, protocolHttpStatus(null))
        assertTrue(protocolSnapshotExpired(IOException("410 Gone: The snapshot expired. Reload from the top.")))
        assertFalse(protocolSnapshotExpired(IOException("404 Not Found: Unknown snapshot.")))
        assertFalse(protocolSnapshotExpired(null))
    }

    @Test
    fun theNameSwitchChangesTheFlagAndReadsTheBoardAgain() {
        val client = FakeClient("a")
        val model = shownModel(client)
        assertEquals(false, (model.ui.card as ProtocolCard.Standing).standing.namePublic)

        model.setNamePublic(true)
        assertEquals(listOf("a:setNamePublic:true"), events)
        assertTrue(model.ui.settingNamePublic)
        // one change at a time
        model.setNamePublic(true)
        assertEquals(1, events.size)

        client.answerSetNamePublic()
        runPosted()
        assertFalse(model.ui.settingNamePublic)
        assertEquals(true, (model.ui.card as ProtocolCard.Standing).standing.namePublic)
        // the board shows the name now: read again
        assertEquals(listOf("a:setNamePublic:true", "a:top:50::", "a:me"), events)
    }

    @Test
    fun aReadOfTheSwitchFromBeforeAChangeDoesNotUndoIt() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.refresh()
        // the refresh's read of the switch is on its way when the user flips it
        model.setNamePublic(true)
        client.answerSetNamePublic()
        runPosted()
        client.answerNamePublic(false)
        runPosted()
        assertEquals(true, (model.ui.card as ProtocolCard.Standing).standing.namePublic)
    }

    @Test
    fun aFailedChangeOfTheSwitchIsAOneShotNotice() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.setNamePublic(true)
        client.failSetNamePublic()
        runPosted()
        assertTrue(model.actionFailed)
        assertFalse(model.ui.settingNamePublic)
        assertEquals(false, (model.ui.card as ProtocolCard.Standing).standing.namePublic)
        model.clearActionFailed()
        assertFalse(model.actionFailed)
    }

    @Test
    fun theStandingFromAnOlderMeIsReplacedOnlyByTheLatest() {
        val client = FakeClient("a")
        val model = shownModel(client)
        model.refresh()
        model.refresh()
        // the first refresh's standing answers last and is dropped
        val first = client.mes.removeFirst()
        client.answerMe(me(row(150, isMe = true)))
        runPosted()
        first(Result.success(ProtocolTopMe(snapshot = snapshot(), me = me(row(999, isMe = true)))))
        runPosted()
        assertEquals(150L, (model.ui.card as ProtocolCard.Standing).standing.position)
    }

    @Test
    fun theStateBehindTheTabIsTheModelsOwn() {
        val model = model()
        assertNull(model.currentState.snapshot)
        assertFalse(model.currentState.hasClient)
    }
}
