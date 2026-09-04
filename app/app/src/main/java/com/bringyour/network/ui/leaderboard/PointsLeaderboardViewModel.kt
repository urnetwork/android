package com.bringyour.network.ui.leaderboard

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.PointsLeaderboardRow
import com.bringyour.sdk.PointsLeaderboardViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SetEmojiTagArgs
import com.bringyour.sdk.SetPointsLeaderboardPublicArgs
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * One ranked network as the list renders it: a value copy of the sdk row
 * (the sdk re-emits fresh proxies on every event), with the preformatted
 * texts the sdk fills in. `displayName` is empty when the row is anonymous;
 * the screen then shows its localized "Anonymous". `emojiTag` shows either way.
 */
data class PointsLeaderboardRowUi(
    val networkId: String,
    val displayName: String,
    val anonymous: Boolean,
    val emojiTag: String,
    val totalPointsText: String,
    val blocksWithPointsText: String,
    val streakText: String,
    val longestStreakText: String,
    val rankPointsText: String,
    val rankBlocksText: String,
    val rankStreakText: String,
)

/** The caller's own row (always its own name) and its opt-in flag. */
data class PointsLeaderboardMeUi(
    val row: PointsLeaderboardRowUi?,
    val isPublic: Boolean,
)

/**
 * The Points tab of the leaderboard. Every row, rank and page comes from the
 * sdk's PointsLeaderboardViewController (android/POINTSLEADERBOARD.md); this
 * only mirrors its state into Compose and forwards the sort, load-more and
 * refresh intents. Opting in and the emoji tag go through the sdk api.
 */
@HiltViewModel
class PointsLeaderboardViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<PointsLeaderboardRowUi>>(emptyList())

    /**
     * The network's own name from the jwt: what the header shows until `me`
     * lands, and what the own row shows when it is anonymous to everyone else
     * (the caller always sees their own name).
     */
    val ownNetworkName: StateFlow<String> = deviceManager.jwtFlow
        .map { it?.networkName ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, deviceManager.jwtFlow.value?.networkName ?: "")
    val rows: StateFlow<List<PointsLeaderboardRowUi>> = _rows.asStateFlow()

    var sort by mutableStateOf(Sdk.PointsLeaderboardSortPoints)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isEndReached by mutableStateOf(false)
        private set

    /** the first page has landed (rows, an empty end, or an error) */
    var hasLoaded by mutableStateOf(false)
        private set

    /** the controller's last request error; empty when the last page landed */
    var errorMessage by mutableStateOf("")
        private set

    var totalRanked by mutableLongStateOf(0L)
        private set

    var latestEpoch by mutableLongStateOf(0L)
        private set

    var me by mutableStateOf<PointsLeaderboardMeUi?>(null)
        private set

    /** the network's opt-in, from `me` and updated locally on toggle */
    var isPointsPublic by mutableStateOf(false)
        private set

    /** the network's emoji tag, from `me` and updated locally on save */
    var emojiTag by mutableStateOf("")
        private set

    var isSettingPublic by mutableStateOf(false)
        private set

    var isSavingEmojiTag by mutableStateOf(false)
        private set

    /** a one-shot error from the opt-in toggle or the emoji save, for a snackbar */
    var actionError by mutableStateOf<String?>(null)
        private set

    val clearActionError: () -> Unit = {
        actionError = null
    }

    // the controller must be closed on the device that opened it, so the
    // owner is tracked across device changes (same discipline as the
    // provider locations view model)
    private var vc: PointsLeaderboardViewController? = null
    private var vcDevice: DeviceLocal? = null
    private var sub: Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null

    // after a local toggle or save, `me` from an older in-flight page could
    // briefly disagree with what the user just did; the local values win
    // until a response newer than the edit lands
    private var ownFlagsEditedAt = 0L
    private var ownFlagsAppliedAt = 0L

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            attach(device)
        }
        attach(deviceManager.device)
    }

    private fun attach(device: DeviceLocal?) {
        sub?.close()
        sub = null
        detachViewController()
        vcDevice = device
        val openVc = device?.openPointsLeaderboardViewController()
        vc = openVc
        sub = openVc?.addPointsLeaderboardListener {
            // the sdk calls from its own thread; state is read on main
            viewModelScope.launch { readState() }
        }
        openVc?.start()
        viewModelScope.launch { readState() }
    }

    private fun detachViewController() {
        val openVc = vc ?: return
        vc = null
        vcDevice?.closePointsLeaderboardViewController(openVc)
        vcDevice = null
    }

    private fun toUi(row: PointsLeaderboardRow): PointsLeaderboardRowUi {
        return PointsLeaderboardRowUi(
            networkId = row.networkId?.toString() ?: "",
            displayName = row.displayName ?: "",
            anonymous = row.anonymous,
            emojiTag = row.emojiTag ?: "",
            totalPointsText = row.totalPointsText ?: "",
            blocksWithPointsText = row.blocksWithPointsText ?: "",
            streakText = row.streakText ?: "",
            longestStreakText = row.longestStreakText ?: "",
            rankPointsText = row.rankPointsText ?: "",
            rankBlocksText = row.rankBlocksText ?: "",
            rankStreakText = row.rankStreakText ?: "",
        )
    }

    /**
     * Mirrors the controller into Compose state. Rows are compared by value
     * and only assigned when something changed, so a no-op event does not
     * re-render the whole list.
     */
    private fun readState() {
        val openVc = vc ?: return
        val list = openVc.rows
        val next = ArrayList<PointsLeaderboardRowUi>()
        if (list != null) {
            for (i in 0 until list.len()) {
                val row = list.get(i) ?: continue
                next.add(toUi(row))
            }
        }
        if (_rows.value != next) {
            _rows.value = next
        }
        sort = openVc.sort ?: Sdk.PointsLeaderboardSortPoints
        isLoading = openVc.isLoading
        isEndReached = openVc.isEndReached
        errorMessage = openVc.errorMessage ?: ""
        totalRanked = openVc.totalRanked
        latestEpoch = openVc.latestEpoch

        val meSdk = openVc.me
        val meUi = meSdk?.let { PointsLeaderboardMeUi(it.row?.let(::toUi), it.pointsLeaderboardPublic) }
        me = meUi
        if (meUi != null && ownFlagsAppliedAt >= ownFlagsEditedAt) {
            isPointsPublic = meUi.isPublic
            emojiTag = meUi.row?.emojiTag ?: ""
        }
        if (!isLoading && (next.isNotEmpty() || isEndReached || errorMessage.isNotEmpty())) {
            hasLoaded = true
        }
    }

    /** Switches the sort; the controller clears its rows and reloads. */
    fun selectSort(sort: String) {
        if (sort == this.sort || !Sdk.isPointsLeaderboardSort(sort)) {
            return
        }
        // reflect the chip immediately; the controller confirms on its event
        this.sort = sort
        vc?.setSort(sort)
    }

    fun loadMore() {
        vc?.loadMore()
    }

    fun refresh() {
        // the next `me` that lands is newer than any local edit
        ownFlagsAppliedAt = System.nanoTime()
        vc?.refresh()
    }

    /** Retries after an error: the controller re-requests the same page. */
    fun retry() {
        if (_rows.value.isEmpty()) {
            refresh()
        } else {
            loadMore()
        }
    }

    private fun api(): Api? {
        return vcDevice?.api ?: deviceManager.device?.api
    }

    fun togglePointsPublic() {
        if (isSettingPublic) {
            return
        }
        val api = api()
        if (api == null) {
            actionError = "Device or API is null"
            return
        }
        val target = !isPointsPublic
        isSettingPublic = true
        viewModelScope.launch {
            val error = setPointsPublic(api, target)
            if (error == null) {
                ownFlagsEditedAt = System.nanoTime()
                isPointsPublic = target
                // the list shows or hides the own row; `me` is re-read too
                refresh()
            } else {
                actionError = error
            }
            isSettingPublic = false
        }
    }

    /**
     * Stores the tag (already normalized by `Sdk.validateEmojiTag`), or an
     * empty string to clear it. `onDone` gets the server's message on failure.
     */
    fun saveEmojiTag(tag: String, onDone: (String?) -> Unit) {
        if (isSavingEmojiTag) {
            return
        }
        val api = api()
        if (api == null) {
            onDone("Device or API is null")
            return
        }
        isSavingEmojiTag = true
        viewModelScope.launch {
            val result = setEmojiTag(api, tag)
            result.onSuccess { stored ->
                ownFlagsEditedAt = System.nanoTime()
                emojiTag = stored
                refresh()
                onDone(null)
            }
            result.onFailure { e ->
                onDone(e.message ?: "")
            }
            isSavingEmojiTag = false
        }
    }

    private suspend fun setPointsPublic(api: Api, public: Boolean): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val args = SetPointsLeaderboardPublicArgs()
                args.public = public
                api.setPointsLeaderboardPublic(args) { result, error ->
                    // never throw inside a gomobile callback: the exception
                    // cannot cross JNI and ART aborts the process
                    val message = when {
                        error != null -> error.message ?: "error"
                        result == null -> "set points leaderboard public: result is null"
                        result.error != null -> result.error.message ?: "error"
                        else -> null
                    }
                    continuation.resume(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setPointsPublic", e)
            e.message ?: "error"
        }
    }

    private suspend fun setEmojiTag(api: Api, tag: String): Result<String> {
        return try {
            suspendCancellableCoroutine { continuation ->
                val args = SetEmojiTagArgs()
                args.emojiTag = tag
                api.setEmojiTag(args) { result, error ->
                    val outcome: Result<String> = when {
                        error != null -> Result.failure(error)
                        result == null -> Result.failure(IllegalStateException("set emoji tag: result is null"))
                        result.error != null -> Result.failure(IllegalStateException(result.error.message ?: "error"))
                        else -> Result.success(result.emojiTag ?: "")
                    }
                    continuation.resume(outcome)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setEmojiTag", e)
            Result.failure(e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sub?.close()
        sub = null
        detachViewController()
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
    }
}
