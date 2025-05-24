package com.bringyour.network.ui.leaderboard

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.TAG
import com.bringyour.sdk.GetLeaderboardArgs
import com.bringyour.sdk.GetNetworkRankingResult
import com.bringyour.sdk.LeaderboardEarner
import com.bringyour.sdk.SetNetworkRankingPublicArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    /**
     * Ordered list of top leaderboard earners
     */
    private val _leaderboardEntries = MutableStateFlow<List<LeaderboardEarner>>(emptyList())
    val leaderboardEntries: StateFlow<List<LeaderboardEarner>> = _leaderboardEntries.asStateFlow()

    /**
     * The rank of the network in the leaderboard
     */
    var networkRank by mutableIntStateOf(0)
        private set

    /**
     * The string formatted net provided by the network
     */
    var netProvidedFormatted by mutableStateOf("")

    /**
     * Whether the network is publicly displayed on the leaderboard
     */
    var isNetworkRankingPublic by mutableStateOf(false)
        private set

    private var isSettingNetworkRankingPublic = false

    /**
     * For refreshing content
     */
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Use for displaying toast error messages
     */
    var errorOccurred by mutableStateOf(false)
        private set

    /**
     * Network ID
     */
    var networkId by mutableStateOf<String?>(null)

    val resetErrorOccurred: () -> Unit = {
        errorOccurred = false
    }

    /**
     * For initialization
     */
    var isInitializing by mutableStateOf(true)
        private set

    /**
     * Display error snackbar
     */
    var displayErrorMsg by mutableStateOf(false)
        private set

    val setDisplayErrorMsg: (Boolean) -> Unit = {
        displayErrorMsg = it
    }

    /**
     * Used for initialization and pull to refresh
     */
    fun fetchLeaderboardData() {
        if (isLoading) {
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {

                val leaderboardDeferred = async { getLeaderboard() }
                val rankingDeferred = async { getRanking() }

                val leaderboardResult = leaderboardDeferred.await()
                val rankingResult = rankingDeferred.await()

                leaderboardResult.onSuccess { earners ->
                    _leaderboardEntries.value = earners
                }

                rankingResult.onSuccess { result ->
                    isNetworkRankingPublic = result.networkRanking.leaderboardPublic
                    networkRank = result.networkRanking.leaderboardRank.toInt()
                    netProvidedFormatted = formatDataProvided(result.networkRanking.netMiBCount)
                }

                val anyFailure = leaderboardResult.isFailure || rankingResult.isFailure

                if (anyFailure) {
                    errorOccurred = true
                    Log.i(TAG, "One or more operations failed")
                }

                isLoading = false
                isInitializing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchLeaderboardData", e)
                setDisplayErrorMsg(true)
                isLoading = false
                isInitializing = false
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getRanking(): Result<GetNetworkRankingResult> {
        return try {
            suspendCancellableCoroutine { continuation ->
                deviceManager.device?.api?.getNetworkLeaderboardRanking { result, error ->
                    if (error != null) {
                        Log.i(TAG, "errorgetRanking: ${error.message}")
                        errorOccurred = true
                        continuation.resumeWith(Result.failure(error))
                        return@getNetworkLeaderboardRanking
                    }

                    if (result == null) {
                        val nullError = IllegalStateException("Result is null")
                        Log.i(TAG, "error getRanking: result is null")
                        continuation.resumeWith(Result.failure(nullError))
                        return@getNetworkLeaderboardRanking
                    }

                    continuation.resumeWith(Result.success(Result.success(result)))
                } ?: continuation.resumeWith(Result.failure(IllegalStateException("Device or API is null")))
            }
        } catch (e: Exception) {
            errorOccurred = true
            Result.failure(e)
        }

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getLeaderboard(): Result<List<LeaderboardEarner>> {
        val args = GetLeaderboardArgs()

        return try {
            suspendCancellableCoroutine { continuation ->
                deviceManager.device?.api?.getLeaderboard(args) { result, error ->
                    if (error != null) {
                        Log.i(TAG, "error fetching leaderboard: ${error.message}")
                        errorOccurred = true
                        continuation.resumeWith(Result.failure(error))
                        return@getLeaderboard
                    }

                    if (result == null) {
                        val nullError = IllegalStateException("Result is null")
                        Log.i(TAG, "error fetching leaderboard: result is null")
                        continuation.resumeWith(Result.failure(nullError))
                        return@getLeaderboard
                    }

                    if (result.error != null) {
                        val resultError = IllegalStateException("get leaderboard: result error: ${result.error.message}")
                        continuation.resumeWith(Result.failure(resultError))
                        return@getLeaderboard
                    }

                    val earners = mutableListOf<LeaderboardEarner>()
                    val n = result.earners.len()

                    for (i in 0 until n) {
                        earners.add(result.earners.get(i))
                    }

                    // You need Result.success(Result.success(Unit)) because the coroutine expects a Result of your function's return type,
                    // which is itself a Result<Unit>.
                    continuation.resumeWith(Result.success(Result.success(earners)))
                } ?: continuation.resumeWith(Result.failure(IllegalStateException("Device or API is null")))
            }
        } catch (e: Exception) {
            errorOccurred = true
            Result.failure(e)
        }
    }

    suspend fun toggleNetworkRankingVisibility() {

        val isPublic = !isNetworkRankingPublic

        if (isSettingNetworkRankingPublic) {
            return
        }

        isSettingNetworkRankingPublic = true

        try {
            suspendCancellableCoroutine { continuation ->
                val args = SetNetworkRankingPublicArgs()
                args.isPublic = isPublic
                deviceManager.device?.api?.setNetworkLeaderboardPublic(args) { result, error ->
                    if (error != null) {
                        continuation.resumeWith(Result.failure(error))
                        return@setNetworkLeaderboardPublic
                    }

                    if (result.error != null) {
                        val resultError = IllegalStateException("get leaderboard: result error: ${result.error.message}")
                        continuation.resumeWith(Result.failure(resultError))
                        return@setNetworkLeaderboardPublic
                    }

                    continuation.resumeWith(Result.success(Result.success(Unit)))
                } ?: continuation.resumeWith(Result.failure(IllegalStateException("Device or API is null")))
            }
        } catch (e: Exception) {
            setDisplayErrorMsg(true)
            Result.failure(e)
        }

        isNetworkRankingPublic = isPublic

        getLeaderboard()

        isSettingNetworkRankingPublic = false

    }

    val formatDataProvided: (Float) -> String = { mib ->
        val formatter = java.text.NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
        }

        val PiB = 1024f * 1024f * 1024f
        val TiB = 1024f * 1024f
        val GiB = 1024f

        when {
            mib >= PiB -> {
                val pib = mib / PiB
                val formatted = formatter.format(pib)
                "$formatted PiB"
            }
            mib >= TiB -> {
                val tib = mib / TiB
                val formatted = formatter.format(tib)
                "$formatted TiB"
            }
            mib >= GiB -> {
                val gib = mib / GiB
                val formatted = formatter.format(gib)
                "$formatted GiB"
            }
            else -> {
                val formatted = formatter.format(mib)
                "$formatted MiB"
            }
        }
    }

    init {
        fetchLeaderboardData()

        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, _ ->
            networkId = jwt.networkId.toString()
        }
    }

}