package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundWorkOwner
import com.bringyour.network.ForegroundPollingResume
import com.bringyour.network.ForegroundPollingSession
import com.bringyour.network.JwtManager
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.sdk.SubscriptionBalanceCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.isActive

@HiltViewModel
class SubscriptionBalanceViewModel @Inject constructor(
    deviceManager: DeviceManager,
    jwtManager: JwtManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
): ViewModel(), DefaultLifecycleObserver {

    private val _currentStore = MutableStateFlow<String?>(null)
    val currentStore: StateFlow<String?> get() = _currentStore

    private val _isInitialized = MutableStateFlow<Boolean>(false)
    val isInitialized: StateFlow<Boolean> get() = _isInitialized

    /**
     * When actively polling for plan subscription change
     */
    private var pollingJob: Job? = null
    private var pollingInterval: Long = 5000 // 5 seconds
    private val pollingSession = ForegroundPollingSession()

    /**
     * Background polling for available bytes
     */
    private var backgroundPollingJob: Job? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foregroundWork = ForegroundWorkOwner(
        start = {
            if (isPolling) {
                resumePollingJob()
            } else {
                createBackgroundPollingJob()
            }
        },
        stop = {
            stopBackgroundPolling()
            pausePolling()
        },
    )

    var isPollingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _isCheckingSolanaTransaction = MutableStateFlow<Boolean>(false)
    val isCheckingSolanaTransaction: StateFlow<Boolean> = _isCheckingSolanaTransaction.asStateFlow()

    val isPolling: Boolean
        get() = _isCheckingSolanaTransaction.value || isPollingSubscriptionBalance


    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableBalanceByteCount = MutableStateFlow<Long>(0)
    val availableBalanceByteCount: StateFlow<Long> get() = _availableBalanceByteCount

    var pendingBalanceByteCount by mutableLongStateOf(0)
        private set

    var usedBalanceByteCount by mutableLongStateOf(0)
        private set

    private val _startBalanceByteCount = MutableStateFlow<Long>(0)
    val startBalanceByteCount: StateFlow<Long> get() = _startBalanceByteCount

    var isRefreshingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _errorFetchingSubscriptionBalance = MutableStateFlow(false)
    val errorFetchingSubscriptionBalance: StateFlow<Boolean> = _errorFetchingSubscriptionBalance

    /**
     * True exactly when the SERVER reports an active subscription
     * (`currentSubscription != null`). This -- not payment evidence, not the jwt's
     * baked-in `pro` claim -- is what the post-purchase overlay is allowed to
     * celebrate. Until it flips true the overlay stays processing-shaped.
     */
    private val _hasActiveSubscription = MutableStateFlow(false)
    val hasActiveSubscription: StateFlow<Boolean> = _hasActiveSubscription.asStateFlow()

    /**
     * The confirmation poll ran out its budget (2 minutes) without the server
     * confirming. This used to die as a single log line ("polling timed out") while
     * the user sat on a "You're premium." overlay backed by nothing. Consumed-sequence
     * pattern, same as PlanViewModel's error/pending sequences: the collector in
     * MainNavHost shows a dialog saying the payment was received and the plan will
     * update by itself.
     *
     * Only bumped for polls started with payment evidence (pollSubscriptionBalance),
     * never for the speculative solana check, where "payment received" would be a lie.
     */
    private val _confirmationTimedOutSequence = MutableStateFlow(0L)
    val confirmationTimedOutSequence: StateFlow<Long> = _confirmationTimedOutSequence.asStateFlow()
    private var consumedConfirmationTimedOutSequence = 0L

    fun consumeConfirmationTimedOutSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedConfirmationTimedOutSequence) {
            return false
        }
        consumedConfirmationTimedOutSequence = sequence
        return true
    }

    val setErrorReachingSubscriptionBalance: (Boolean) -> Unit = {
        _errorFetchingSubscriptionBalance.value = it
    }

    val refreshSubscriptionBalance: () -> Unit = {
        if (!isRefreshingSubscriptionBalance) {
            isRefreshingSubscriptionBalance = true
            fetchSubscriptionBalance()
        }
    }

    val fetchSubscriptionBalance: () -> Unit = {

        if (!_isLoading.value) {

            _isLoading.value = true

            /**
             * If the device or its api is not up yet, the call below dispatches
             * NOTHING and no callback ever arrives to clear `_isLoading`. It would stay
             * true forever, and because every fetch is guarded on `!_isLoading.value`,
             * both poll loops -- and every later refresh -- would become silent no-ops
             * for the life of this view model. The balance and the Pro label would then
             * be stale until the app was relaunched.
             *
             * So bail out cleanly instead of wedging: the caller polls again shortly.
             */
            val api = deviceManager.device?.api
            if (api == null) {
                _isLoading.value = false
                isRefreshingSubscriptionBalance = false
            } else {

                api.subscriptionBalance( SubscriptionBalanceCallback { result, err ->

                    viewModelScope.launch {
                        if (err != null) {

                            _isLoading.value = false
                            isRefreshingSubscriptionBalance = false
                            _errorFetchingSubscriptionBalance.value = true

                        } else {

                            if (result == null) {
                                _isLoading.value = false
                                isRefreshingSubscriptionBalance = false
                                return@launch
                            }

                            /**
                             * The server is the source of truth for Pro, and
                             * `currentSubscription` is non-null exactly when the network is
                             * Pro. The jwt's `pro` claim is baked in when the token is
                             * issued, so it goes stale on BOTH an upgrade and a lapse.
                             * Refresh the token whenever the two disagree, in either
                             * direction.
                             *
                             * The downgrade case used to be unreachable: it lived inside
                             * `currentSubscription?.plan?.let`, which does not run precisely
                             * when the user is no longer pro. A lapsed subscriber kept
                             * showing "Supporter", kept Pro behavior, and kept the upgrade
                             * CTA hidden until the app was relaunched.
                             */
                            val serverIsPro = result.currentSubscription != null
                            _hasActiveSubscription.value = serverIsPro
                            val jwtIsPro = jwtManager.jwtFlow.value?.pro == true

                            if (serverIsPro && !jwtIsPro) {
                                // free -> paid: reset provide mode to never once at the
                                // upgrade; the user can opt back in and that choice persists
                                deviceManager.provideControlMode = ProvideControlMode.NEVER
                            }

                            if (serverIsPro != jwtIsPro) {
                                deviceManager.device?.refreshToken(0)
                            }

                            result.currentSubscription?.store.let { store ->
                                _currentStore.value = store
                            }

                            _availableBalanceByteCount.value = result.balanceByteCount
                            pendingBalanceByteCount = result.openTransferByteCount
                            _startBalanceByteCount.value = result.startBalanceByteCount
                            usedBalanceByteCount = (result.startBalanceByteCount - result.balanceByteCount - pendingBalanceByteCount).coerceAtLeast(0)

                            // the Home Screen dashboard's balance bar reads this snapshot; the
                            // widget writer refreshes it on its own slow cadence, the app on every fetch
                            (appContext.applicationContext as? com.bringyour.network.MainApplication)
                                ?.widgetSnapshotWriter
                                ?.publishBalance(
                                    com.bringyour.network.widgets.WidgetBalanceSnapshot(
                                        updatedAtMillis = System.currentTimeMillis(),
                                        startBalanceByteCount = result.startBalanceByteCount,
                                        balanceByteCount = result.balanceByteCount,
                                        openTransferByteCount = result.openTransferByteCount,
                                        isPro = serverIsPro,
                                    )
                                )
                            _errorFetchingSubscriptionBalance.value = false
                            _isLoading.value = false
                        }

                        isRefreshingSubscriptionBalance = false
                        if (!_isInitialized.value) {
                            _isInitialized.value = true
                        }

                    }

                })

            }

        }

    }

    private val isSupporterWithBalance: () -> Boolean = {
        val currentIsPro = jwtManager.jwtFlow.value?.pro == true

        currentIsPro && _availableBalanceByteCount.value > 0
    }

    /**
     * This is used when we have evidence of a payment (ie Stripe, Apple, Play)
     */
    fun pollSubscriptionBalance(maxDurationMs: Long = 120_000L) {

//        if (isPolling) return
        if (isPollingSubscriptionBalance) return

        isPollingSubscriptionBalance = true
        startPolling(maxDurationMs)
    }

    /**
     * When we regain focus from a wallet, and there is a solana payment reference id (in SolanaPaymentViewModel), start polling
     * This is different than pollSubscriptionBalance, as do not know if the user submitted a transaction or not
     * So we want to display a different pending message, and poll for a little less time
     */
    fun pollSolanaTransaction(maxDurationMs: Long = 20_000L) {
        if (isPolling) return

        _isCheckingSolanaTransaction.value = true
        startPolling(maxDurationMs)
    }

    private fun startPolling(maxDurationMs: Long) {
        pollingSession.start(maxDurationMs)
        if (processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            resumePollingJob()
        }
    }

    private fun resumePollingJob() {
        pollingJob?.cancel()
        when (pollingSession.resume()) {
            ForegroundPollingResume.INACTIVE -> return
            ForegroundPollingResume.EXPIRED -> {
                // Observe a webhook that landed while backgrounded before
                // ending the bounded confirmation session.
                fetchSubscriptionBalance()
                emitConfirmationTimedOutIfUnconfirmed()
                stopPolling()
                return
            }
            ForegroundPollingResume.ACTIVE -> Unit
        }

        pollingJob = viewModelScope.launch {
            fetchSubscriptionBalance()
            if (isSupporterWithBalance()) {
                stopPolling()
                return@launch
            }

            while (isPolling && isActive && !pollingSession.hasExpired()) {

                delay(pollingInterval)
                fetchSubscriptionBalance()
                if (isSupporterWithBalance()) {
                    stopPolling()
                    break
                }
            }

            if (isPolling) {
                Log.i(TAG, "polling timed out")
                emitConfirmationTimedOutIfUnconfirmed()
                stopPolling()
            }
        }
    }

    /**
     * The poll gave up. If it was backed by payment evidence and the server still has
     * not confirmed, that MUST reach the user as more than a log line. (The last
     * fetch may still be in flight -- the dialog copy tolerates the race: "your plan
     * will update automatically".)
     */
    private fun emitConfirmationTimedOutIfUnconfirmed() {
        if (isPollingSubscriptionBalance &&
            !_hasActiveSubscription.value &&
            !isSupporterWithBalance()
        ) {
            _confirmationTimedOutSequence.update { it + 1L }
        }
    }

    fun createBackgroundPollingJob() {
        if (isPolling) {
            return
        }
        backgroundPollingJob?.cancel()
        backgroundPollingJob = viewModelScope.launch {

            fetchSubscriptionBalance()

            while (isActive) {
                delay(60_000) // poll every minute
                fetchSubscriptionBalance()
                if (isSupporterWithBalance()) {

                    stopBackgroundPolling()
                    break
                }
            }
        }
    }

    fun stopBackgroundPolling() {
        backgroundPollingJob?.cancel()
        backgroundPollingJob = null
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingSession.stop()
        isPollingSubscriptionBalance = false
        _isCheckingSolanaTransaction.value = false
    }

    private fun pausePolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingSession.pause()
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

    override fun onCleared() {
        processLifecycle.removeObserver(this)
        foregroundWork.close()
        stopPolling()
        stopBackgroundPolling()
        super.onCleared()
    }

}
