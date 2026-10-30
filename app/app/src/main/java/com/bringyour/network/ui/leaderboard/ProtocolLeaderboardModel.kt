package com.bringyour.network.ui.leaderboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The calls the Protocol tab makes on one device's api: the board (sdk SnTop,
 * GET /sn/top), the caller's standing (SnTopMe, GET /sn/top/me) and the
 * network's points_leaderboard_public flag, the one "show my name on
 * leaderboards" switch shared with the Points tab (Q12). Each call answers
 * once, on an sdk thread.
 */
internal interface ProtocolTopClient {
    // the calls carry the network's auth: the card and the switch need it
    val signedIn: Boolean

    // one page of the board: the first with an empty cursor, then the page
    // after `cursor`, pinned to `snapshotId`
    fun top(limit: Int, cursor: String, snapshotId: String, done: (Result<ProtocolTopPage>) -> Unit)

    fun me(done: (Result<ProtocolTopMe>) -> Unit)

    fun namePublic(done: (Result<Boolean>) -> Unit)

    fun setNamePublic(public: Boolean, done: (Result<Unit>) -> Unit)
}

/** SnTopResult: one page of the board. */
data class ProtocolTopPage(
    val snapshot: ProtocolSnapshot,
    val rows: List<ProtocolRow>,
    // the caller's row wherever it ranks; null when not signed in or not ranked
    val me: ProtocolRow?,
    // null or empty at the end of the list
    val next: String?,
)

/** SnTopMeResult: the caller's standing, null when the network has no row on the board. */
data class ProtocolTopMe(
    val snapshot: ProtocolSnapshot?,
    val me: ProtocolMe?,
)

/**
 * The Protocol tab of the leaderboard (apple/LEADERTOP.md): the board's top
 * 400, paged, and the caller's own standing. Owns the tab's state and the
 * calls behind it. Main-thread confined; ProtocolLeaderboardViewModel binds
 * it to the device and the screen.
 *
 * The first appearance on a client loads the first page, the standing and the
 * name switch; after that the tab reloads only when asked (a pull, a retry, a
 * change of the switch), so a return to the tab keeps its place in the list.
 * Every next page is pinned to the first page's snapshot. A page from another
 * snapshot (the server moved on) reloads the list from the top instead of
 * mixing two tempos' rankings.
 *
 * Answers arrive on sdk threads. [post] hands each to the main thread, where
 * it applies only while the client it was asked of is still the current one
 * and no later request of its kind replaced it: another client (an account
 * switch, a sign out) clears the tab, and the model's close stops it.
 *
 * A failed next page is never asked for again by scrolling: the footer's
 * retry is the retry (the hot-loop rule of PointsLeaderboardPaging). A failed
 * refresh keeps the rows it would have replaced.
 */
internal class ProtocolLeaderboardModel(
    private val settings: ProtocolLeaderboardSettings,
    private val post: (() -> Unit) -> Unit,
) {
    var ui by mutableStateOf(protocolLeaderboardUi(ProtocolLeaderboardState.Initial, settings))
        private set

    // a change of the name switch failed: a one-shot notice
    var actionFailed by mutableStateOf(false)
        private set

    private var state = ProtocolLeaderboardState.Initial

    private var client: ProtocolTopClient? = null

    // changes with the client and at the close: an answer asked for before is dropped
    private var generation = 0L

    // the latest first page; a next page asked for under an earlier one is dropped
    private var listSequence = 0L

    // the latest /sn/top/me
    private var meSequence = 0L

    // the latest read or change of the name switch
    private var nameSequence = 0L

    // a seek's target position not loaded yet; 0 when none
    private var seekPosition = 0L

    private var visible = false
    private var closed = false

    /** The tab's state, for the tests. */
    val currentState: ProtocolLeaderboardState
        get() = state

    /** The client the calls go to; null without a device. */
    fun setClient(nextClient: ProtocolTopClient?) {
        if (closed || client === nextClient) {
            return
        }
        client = nextClient
        generation++
        seekPosition = 0L
        // the former account's rows, standing and switch go with it
        apply(
            ProtocolLeaderboardState.Initial.copy(
                hasClient = nextClient != null,
                signedIn = nextClient?.signedIn == true,
            )
        )
        if (visible) {
            loadAll()
        }
    }

    /** Whether the tab shows; its first appearance on a client loads it. */
    fun setVisible(nextVisible: Boolean) {
        if (closed) {
            return
        }
        val appeared = nextVisible && !visible
        visible = nextVisible
        if (appeared && !state.listLoaded && !state.listLoading) {
            loadAll()
        }
    }

    /** Pull to refresh: the newest snapshot from the top, the standing and the switch. */
    fun refresh() {
        if (closed) {
            return
        }
        loadAll()
    }

    /**
     * The retry rows: the first page (or the last refresh) again when it
     * failed, else the next page that failed; and the standing when it failed.
     */
    fun retry() {
        if (closed) {
            return
        }
        if (!state.listLoaded || state.listFailed) {
            loadFirstPage()
        } else if (state.pageFailed) {
            apply(state.copy(pageFailed = false))
            loadPage(settings.pageSize)
        }
        if (state.meFailed) {
            loadMe()
        }
    }

    /** The next page, asked for directly. */
    fun loadMore() {
        if (closed) {
            return
        }
        loadPage(settings.pageSize)
    }

    /**
     * The list's last visible row (an index into the rows; -1 when none is
     * visible): the next page is asked for once it nears the end of the
     * loaded rows. A failed page is not asked for again here.
     */
    fun onLastVisibleRow(index: Int) {
        if (closed) {
            return
        }
        val current = state
        if (
            PointsLeaderboardPaging.shouldLoadMore(
                lastVisibleRowIndex = index,
                rowCount = current.rows.size,
                isLoading = current.listLoading || current.pageLoading,
                isEndReached = current.next == null,
                hasError = current.pageFailed,
                threshold = settings.loadMoreThreshold,
            )
        ) {
            loadPage(settings.pageSize)
        }
    }

    /**
     * The position indicator was released over `position`: pages toward it
     * until it is loaded. A loaded position needs nothing; the tab scrolls.
     * A seek is the user asking, so a failed page is tried again.
     */
    fun seekTo(position: Long) {
        if (closed || !state.listLoaded) {
            return
        }
        val total = ui.indicatorTotal
        if (total <= 0L) {
            return
        }
        val target = position.coerceIn(1L, total)
        val last = state.rows.lastOrNull()?.position ?: 0L
        if (target <= last) {
            seekPosition = 0L
            return
        }
        seekPosition = target
        if (state.pageFailed) {
            apply(state.copy(pageFailed = false))
        }
        // a page on its way continues the seek when it lands
        if (!state.pageLoading) {
            loadPage(settings.seekPageSize)
        }
    }

    /** The "show my network name" switch (points_leaderboard_public, shared with Points). */
    fun setNamePublic(public: Boolean) {
        val opened = client ?: return
        if (closed || !opened.signedIn || state.settingNamePublic) {
            return
        }
        val requestGeneration = generation
        // a read of the switch on its way answers from before this change
        nameSequence++
        apply(state.copy(settingNamePublic = true))
        opened.setNamePublic(public) { result ->
            post {
                if (generation != requestGeneration) {
                    return@post
                }
                if (result.isSuccess) {
                    nameSequence++
                    apply(state.copy(namePublic = public, settingNamePublic = false))
                    // the board shows or hides the name
                    loadFirstPage()
                    loadMe()
                } else {
                    apply(state.copy(settingNamePublic = false))
                    actionFailed = true
                }
            }
        }
    }

    fun clearActionFailed() {
        actionFailed = false
    }

    /** The screen is gone: nothing updates after. */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        generation++
        visible = false
        client = null
        seekPosition = 0L
        apply(ProtocolLeaderboardState.Initial)
    }

    private fun loadAll() {
        loadFirstPage()
        loadMe()
        loadNamePublic()
    }

    private fun loadFirstPage() {
        val opened = client ?: return
        val requestGeneration = generation
        val sequence = ++listSequence
        // a refresh replaces the rows a seek was paging through
        seekPosition = 0L
        apply(state.copy(listLoading = true, listFailed = false, pageLoading = false, pageFailed = false))
        opened.top(limit = settings.pageSize, cursor = "", snapshotId = "") { result ->
            post {
                if (generation != requestGeneration || listSequence != sequence) {
                    return@post
                }
                val page = result.getOrNull()
                if (page != null) {
                    val rows = rowsInRange(listOf(), page.rows)
                    apply(
                        state.copy(
                            snapshot = page.snapshot,
                            rows = rows,
                            next = nextCursor(page, rows),
                            listLoaded = true,
                            listLoading = false,
                            listFailed = false,
                        )
                    )
                } else {
                    apply(state.copy(listLoading = false, listFailed = true))
                }
            }
        }
    }

    private fun loadPage(limit: Int) {
        val opened = client ?: return
        val current = state
        val cursor = current.next ?: return
        if (!current.listLoaded || current.listLoading || current.pageLoading || current.pageFailed) {
            return
        }
        val pinnedSnapshotId = current.snapshot?.id.orEmpty()
        val requestGeneration = generation
        val sequence = listSequence
        apply(current.copy(pageLoading = true))
        opened.top(limit = limit, cursor = cursor, snapshotId = pinnedSnapshotId) { result ->
            post {
                if (generation != requestGeneration || listSequence != sequence) {
                    return@post
                }
                val page = result.getOrNull()
                if (page == null) {
                    seekPosition = 0L
                    apply(state.copy(pageLoading = false, pageFailed = true))
                    return@post
                }
                val pageSnapshotId = page.snapshot.id
                if (pinnedSnapshotId.isNotEmpty() && pageSnapshotId.isNotEmpty() && pageSnapshotId != pinnedSnapshotId) {
                    // the server moved on: the whole list again from the new snapshot
                    loadFirstPage()
                    return@post
                }
                val rows = rowsInRange(state.rows, page.rows)
                apply(
                    state.copy(
                        rows = rows,
                        next = nextCursor(page, rows),
                        pageLoading = false,
                        pageFailed = false,
                    )
                )
                continueSeek()
            }
        }
    }

    private fun continueSeek() {
        val target = seekPosition
        if (target <= 0L) {
            return
        }
        val last = state.rows.lastOrNull()?.position ?: 0L
        if (target <= last || state.next == null) {
            seekPosition = 0L
            return
        }
        loadPage(settings.seekPageSize)
    }

    // the loaded rows and the page's rows after them, in order, without
    // repeats and within the list's range
    private fun rowsInRange(loaded: List<ProtocolRow>, page: List<ProtocolRow>): List<ProtocolRow> {
        var last = loaded.lastOrNull()?.position ?: 0L
        val added = mutableListOf<ProtocolRow>()
        for (row in page.sortedBy { it.position }) {
            if (row.position <= last || settings.listRange < row.position) {
                continue
            }
            added.add(row)
            last = row.position
        }
        return loaded + added
    }

    // the page after these rows, if any: none past the list's range, and none
    // after an empty page, which would ask for the same nothing again
    private fun nextCursor(page: ProtocolTopPage, rows: List<ProtocolRow>): String? {
        val next = page.next?.takeIf { it.isNotEmpty() } ?: return null
        if (page.rows.isEmpty()) {
            return null
        }
        val last = rows.lastOrNull()?.position ?: 0L
        if (settings.listRange <= last) {
            return null
        }
        return next
    }

    private fun loadMe() {
        val opened = client ?: return
        if (!opened.signedIn) {
            return
        }
        val requestGeneration = generation
        val sequence = ++meSequence
        apply(state.copy(meLoading = true, meFailed = false))
        opened.me { result ->
            post {
                if (generation != requestGeneration || meSequence != sequence) {
                    return@post
                }
                val answer = result.getOrNull()
                if (answer != null) {
                    apply(
                        state.copy(
                            me = answer.me,
                            meSnapshot = answer.snapshot,
                            meLoaded = true,
                            meLoading = false,
                            meFailed = false,
                        )
                    )
                } else {
                    // the standing shown stays; the card says it failed only when there is none
                    apply(state.copy(meLoading = false, meFailed = true))
                }
            }
        }
    }

    private fun loadNamePublic() {
        val opened = client ?: return
        if (!opened.signedIn) {
            return
        }
        val requestGeneration = generation
        val sequence = ++nameSequence
        opened.namePublic { result ->
            post {
                if (generation != requestGeneration || nameSequence != sequence) {
                    return@post
                }
                // a failed read leaves the switch as it was
                result.getOrNull()?.let { apply(state.copy(namePublic = it)) }
            }
        }
    }

    private fun apply(next: ProtocolLeaderboardState) {
        state = next
        ui = protocolLeaderboardUi(next, settings)
    }
}
