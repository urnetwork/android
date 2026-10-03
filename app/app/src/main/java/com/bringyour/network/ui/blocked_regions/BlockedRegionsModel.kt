package com.bringyour.network.ui.blocked_regions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A blocked-locations request that failed, shown once as a snackbar on the
 * blocked locations screen.
 */
sealed class BlockedRegionsNotice {
    object LoadFailed : BlockedRegionsNotice()
    data class BlockFailed(val locationName: String) : BlockedRegionsNotice()
    data class UnblockFailed(val locationName: String) : BlockedRegionsNotice()
}

/**
 * The network's blocked-location calls. Each `done` runs once, on any thread.
 * A call returns false, without calling `done`, when there is no api.
 */
interface BlockedRegionsSource<T> {
    fun fetch(done: (Result<List<T>>) -> Unit): Boolean
    fun block(location: T, done: (failed: Boolean) -> Unit): Boolean
    fun unblock(location: T, done: (failed: Boolean) -> Unit): Boolean
}

/**
 * The blocked locations list and its edits. Every failure used to be only
 * logged: a failed unblock put the optimistically removed row back with no
 * word, a failed block or load left the list as it was, and with no api the
 * screen stayed busy for good. Now each failure leaves a `notice` for the
 * screen and clears the busy flags.
 *
 * Mutate on the main thread only; `post` moves a call's completion there.
 */
class BlockedRegionsModel<T>(
    private val source: BlockedRegionsSource<T>,
    private val post: (() -> Unit) -> Unit,
    private val nameOf: (T) -> String,
    private val isSame: (T, T) -> Boolean,
) {
    private val _regions = MutableStateFlow<List<T>>(emptyList())
    val regions: StateFlow<List<T>> = _regions.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // the last failed request, until the screen has shown it
    private val _notice = MutableStateFlow<BlockedRegionsNotice?>(null)
    val notice: StateFlow<BlockedRegionsNotice?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun contains(location: T): Boolean =
        _regions.value.any { isSame(it, location) }

    private fun sorted(regions: List<T>): List<T> =
        regions.sortedBy { nameOf(it).lowercase() }

    fun fetch() {
        if (_isFetching.value) {
            return
        }
        _isFetching.value = true
        val started = source.fetch { result ->
            post {
                result
                    .onSuccess { _regions.value = sorted(it) }
                    .onFailure { _notice.value = BlockedRegionsNotice.LoadFailed }
                _isFetching.value = false
            }
        }
        if (!started) {
            _isFetching.value = false
            _notice.value = BlockedRegionsNotice.LoadFailed
        }
    }

    fun block(location: T) {
        if (_isProcessing.value || contains(location)) {
            return
        }
        _isProcessing.value = true
        val failBlock = {
            _notice.value = BlockedRegionsNotice.BlockFailed(nameOf(location))
            _isProcessing.value = false
        }
        val started = source.block(location) { failed ->
            post {
                if (failed) {
                    failBlock()
                } else {
                    _regions.value = sorted(_regions.value + location)
                    _isProcessing.value = false
                }
            }
        }
        if (!started) {
            failBlock()
        }
    }

    fun unblock(location: T) {
        if (_isProcessing.value || !contains(location)) {
            return
        }
        _isProcessing.value = true
        val removed = _regions.value.first { isSame(it, location) }
        // optimistic: the row goes now, and comes back if the server refuses
        _regions.value = _regions.value.filter { !isSame(it, location) }

        val failUnblock = {
            if (!contains(removed)) {
                _regions.value = sorted(_regions.value + removed)
            }
            _notice.value = BlockedRegionsNotice.UnblockFailed(nameOf(removed))
            _isProcessing.value = false
        }
        val started = source.unblock(removed) { failed ->
            post {
                if (failed) {
                    failUnblock()
                } else {
                    _isProcessing.value = false
                }
            }
        }
        if (!started) {
            failUnblock()
        }
    }
}
