package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.network.ui.components.referral.ReferralTerms
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundWorkOwner
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.SectionLoad
import com.bringyour.network.ui.shared.models.referralCodeLoadAfterFetch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A batch of newly observed referrals for the local network. `isFirst` marks
 * the crowning: the count went from zero to earned, which gets the full-screen
 * celebration; later batches get the gold toast.
 */
data class ReferralCelebration(
    val joined: Long,
    val isFirst: Boolean,
)

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    @ApplicationContext context: Context,
): ViewModel(), DefaultLifecycleObserver {

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 30_000 // every 30 seconds
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foregroundWork = ForegroundWorkOwner(
        start = { pollReferralCode() },
        stop = { stopPolling() },
    )

    private val _referralCode = MutableStateFlow<String>("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    // a failed fetch used to be only logged, leaving the code's spinner up for good
    private val _codeLoad = MutableStateFlow(SectionLoad.Loading)
    val codeLoad: StateFlow<SectionLoad> = _codeLoad.asStateFlow()

    private val _totalReferralCount = MutableStateFlow<Long>(0)
    val totalReferralCount: StateFlow<Long> = _totalReferralCount.asStateFlow()

    /**
     * The referral program's cap and bonus, from the server with the code
     * (defaults until the first fetch).
     */
    private val _terms = MutableStateFlow(ReferralTerms.Default)
    val terms: StateFlow<ReferralTerms> = _terms.asStateFlow()

    /**
     * Referral celebrations, keyed off the count the last celebration (or the
     * baseline) left behind, persisted per network so an increment observed on
     * this device celebrates exactly once. The first fetch for a network only
     * records the baseline: pre-existing referrals (reinstall, app update, a
     * second device) are old news, not a surprise.
     */
    private val celebrationPrefs =
        context.getSharedPreferences("referral_celebrations", Context.MODE_PRIVATE)

    private val _pendingCelebration = MutableStateFlow<ReferralCelebration?>(null)
    val pendingCelebration: StateFlow<ReferralCelebration?> = _pendingCelebration.asStateFlow()

    val clearCelebration: () -> Unit = {
        _pendingCelebration.value = null
    }

    // resolved async from the local jwt; celebration baselines are keyed by it
    @Volatile
    private var networkId: String? = null

    private fun ensureNetworkId() {
        if (networkId != null) {
            return
        }
        deviceManager.asyncLocalState?.parseByJwt { jwt, success ->
            if (success) {
                jwt?.networkId?.toString()?.let { id ->
                    if (id.isNotEmpty()) {
                        networkId = id
                    }
                }
            }
        }
    }

    private fun maybeCelebrate(count: Long) {
        val id = networkId ?: return
        val key = "celebrated_count_$id"

        if (!celebrationPrefs.contains(key)) {
            // first observation for this network on this device: baseline only
            celebrationPrefs.edit().putLong(key, count).apply()
            return
        }

        val previous = celebrationPrefs.getLong(key, 0L)
        if (count > previous) {
            val pending = _pendingCelebration.value
            _pendingCelebration.value = if (pending == null) {
                ReferralCelebration(
                    joined = count - previous,
                    isFirst = previous == 0L,
                )
            } else {
                // an unseen celebration is still up: fold the new arrivals in
                ReferralCelebration(
                    joined = pending.joined + (count - previous),
                    isFirst = pending.isFirst,
                )
            }
            celebrationPrefs.edit().putLong(key, count).apply()
        } else if (count < previous) {
            // referrals can be unlinked; re-baseline quietly
            celebrationPrefs.edit().putLong(key, count).apply()
        }
    }

    val pollReferralCode: () -> Unit = {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            fetchReferralCode()

            while (isActive) {
                delay(pollingInterval)
                fetchReferralCode()
            }
        }
    }

    private fun settleCode(failed: Boolean, code: String?) {
        _codeLoad.value = referralCodeLoadAfterFetch(
            failed = failed,
            code = code,
            shownCode = _referralCode.value,
        )
    }

    /** Try again after a failed fetch: the spinner comes back while it runs. */
    val retryReferralCode: () -> Unit = {
        _codeLoad.value = SectionLoad.retrying(_codeLoad.value)
        fetchReferralCode()
    }

    val fetchReferralCode: () -> Unit = fetchReferralCode@{
        ensureNetworkId()

        val api = deviceManager.device?.api
        if (api == null) {
            settleCode(failed = true, code = null)
            return@fetchReferralCode
        }

        api.getNetworkReferralCode { result, error ->

            if (error != null || result == null) {
                Log.i(TAG, "Error getNetworkReferralCode: $error")
                viewModelScope.launch { settleCode(failed = true, code = null) }
                return@getNetworkReferralCode
            }

            if (result.error != null) {
                Log.i(TAG, "Result error getNetworkReferralCode: ${result.error.message}")
                viewModelScope.launch { settleCode(failed = true, code = null) }
                return@getNetworkReferralCode
            }

            viewModelScope.launch {
                settleCode(failed = false, code = result.referralCode)
                if (!result.referralCode.isNullOrBlank()) {
                    _referralCode.value = result.referralCode
                }
                _totalReferralCount.value = result.totalReferrals
                _terms.value = ReferralTerms.from(result)

                maybeCelebrate(result.totalReferrals)
            }
        }
    }

    init {
        processLifecycle.addObserver(this)
        foregroundWork.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundWork.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundWork.setForeground(false)
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        processLifecycle.removeObserver(this)
        foregroundWork.close()
        super.onCleared()
    }

}
