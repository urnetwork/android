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
import com.bringyour.network.ui.account.GuestAccount
import com.bringyour.network.TAG
import com.bringyour.sdk.ExperimentAssignmentList
import com.bringyour.sdk.OnboardingOffer
import com.bringyour.sdk.OnboardingOfferIssueArgs
import com.bringyour.sdk.PriceTier
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Api
import com.bringyour.sdk.PurchaseConfirmationListener
import com.bringyour.sdk.SubscriptionBalanceCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.isActive

@HiltViewModel
class SubscriptionBalanceViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    jwtManager: JwtManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
): ViewModel(), DefaultLifecycleObserver {

    private val _currentStore = MutableStateFlow<String?>(null)
    val currentStore: StateFlow<String?> get() = _currentStore

    /**
     * The onboarding plan data the balance response carries (mmm/onboarding/
     * PLAN.md): the caller's price tier, the welcome offer when one was issued,
     * and the experiment variants per surface. Every plan surface renders from
     * these; the store's localized prices only refine the printed figures.
     */
    private val _priceTier = MutableStateFlow<PriceTier?>(null)
    val priceTier: StateFlow<PriceTier?> = _priceTier.asStateFlow()

    private val _onboardingOffer = MutableStateFlow<OnboardingOffer?>(null)
    val onboardingOffer: StateFlow<OnboardingOffer?> = _onboardingOffer.asStateFlow()

    private val _experiments = MutableStateFlow<ExperimentAssignmentList?>(null)
    val experiments: StateFlow<ExperimentAssignmentList?> = _experiments.asStateFlow()

    /**
     * The store's storefront country (Play's billing country on the play
     * flavor), sent with the balance request so the server resolves the tier
     * from it instead of the IP estimate. Null where the app has no store.
     */
    @Volatile
    var storefrontCountry: String? = null
        private set

    fun setStorefrontCountry(country: String?) {
        val normalized = country?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (normalized != storefrontCountry) {
            storefrontCountry = normalized
            // re-resolve the tier for the store's country
            fetchSubscriptionBalance()
        }
    }

    /** The in-app offer holdout: the regular picker, no offer, no issue call. */
    val isOfferHoldout: Boolean
        get() = _experiments.value?.isHoldout(Sdk.ExperimentSurfaceOfferInApp) == true

    /** The in-app experiment assignment, for the offer events. */
    val offerExperiment: Pair<String, String>
        get() {
            val a = _experiments.value?.forSurface(Sdk.ExperimentSurfaceOfferInApp)
            return Pair(a?.experimentId ?: "", a?.variant ?: "")
        }

    private var issuingOffer = false

    /** A Solana Pay purchase the wallet was opened for; completed when the poll sees Pro. */
    @Volatile
    private var pendingSolanaPurchase: Pair<String, Double>? = null

    fun expectSolanaPurchase(plan: String, amountUsd: Double) {
        pendingSolanaPurchase = Pair(plan, amountUsd)
    }

    /**
     * Issues the welcome offer for a surface (idempotent on the server; the
     * existing record comes back when one exists) and publishes it. Not called
     * for the holdout.
     */
    fun issueOnboardingOffer(surface: String) {
        if (issuingOffer || isOfferHoldout) {
            return
        }
        val api = deviceManager.device?.api ?: return
        issuingOffer = true
        val args = OnboardingOfferIssueArgs()
        args.surface = surface
        args.storefrontCountry = storefrontCountry ?: ""
        api.onboardingOfferIssue(args) { result, err ->
            viewModelScope.launch {
                issuingOffer = false
                if (err != null) {
                    Log.i(TAG, "offer issue error: ${err.message}")
                    return@launch
                }
                result?.offer?.let { _onboardingOffer.value = it }
                result?.error?.let { Log.i(TAG, "offer issue refused: ${it.message}") }
            }
        }
    }

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

    /**
     * The SDK confirmation for Stripe sheet / pay page / checkout-return purchases
     * (PurchaseConfirmation). Its state callbacks arrive on a Go thread and are
     * handed to the main thread here.
     */
    private val purchaseConfirmation = PurchaseConfirmation(
        openSource = { onState ->
            deviceManager.device?.api?.let { api ->
                SdkPurchaseConfirmationSource(api) { state ->
                    viewModelScope.launch { onState(state) }
                }
            }
        },
        onConfirmed = { isPro ->
            isConfirmingPurchase = false
            if (isPro) {
                // the overlay's premium copy reads this; the fetch below refreshes the rest
                _hasActiveSubscription.value = true
            }
            _purchaseConfirmedSequence.update { it + 1L }
            fetchSubscriptionBalance()
            createBackgroundPollingJob()
        },
        onGaveUp = {
            isConfirmingPurchase = false
            _confirmationTimedOutSequence.update { it + 1L }
            fetchSubscriptionBalance()
            createBackgroundPollingJob()
        },
    )

    /** The server confirmed a purchase handed to confirmPurchase: the overlay may celebrate. */
    private val _purchaseConfirmedSequence = MutableStateFlow(0L)
    val purchaseConfirmedSequence: StateFlow<Long> = _purchaseConfirmedSequence.asStateFlow()
    private var consumedPurchaseConfirmedSequence = 0L

    fun consumePurchaseConfirmedSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchaseConfirmedSequence) {
            return false
        }
        consumedPurchaseConfirmedSequence = sequence
        return true
    }

    var isConfirmingPurchase by mutableStateOf(false)
        private set

    /** A purchase UI is opening: load the confirmation baseline before the payment. */
    fun preparePurchaseConfirmation() {
        purchaseConfirmation.prepare()
    }

    /** The purchase UI closed without a purchase. */
    fun cancelPurchaseConfirmation() {
        purchaseConfirmation.cancel()
    }

    /**
     * The purchase UI reported success. Polls until the server confirms; the overlay
     * launches from purchaseConfirmedSequence, the delayed notice from
     * confirmationTimedOutSequence. Without an api there is nothing to confirm
     * against, so the plain bounded poll (and its timeout notice) runs instead.
     */
    fun confirmPurchase() {
        if (purchaseConfirmation.confirm()) {
            isConfirmingPurchase = true
            stopBackgroundPolling()
        } else {
            pollSubscriptionBalance()
        }
    }

    var isPollingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _isCheckingSolanaTransaction = MutableStateFlow<Boolean>(false)
    val isCheckingSolanaTransaction: StateFlow<Boolean> = _isCheckingSolanaTransaction.asStateFlow()

    val isPolling: Boolean
        get() = _isCheckingSolanaTransaction.value || isPollingSubscriptionBalance || isConfirmingPurchase


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

    // the server's `guest`: the network has no login method (read from the live
    // auth methods, so it survives the token refresh that clears guest_mode)
    private val _serverGuest = MutableStateFlow(false)

    // re-signs the jwt for the same network (a converted guest's guest_mode clears)
    val refreshJwt: () -> Unit = {
        deviceManager.device?.refreshToken(0)
    }

    /**
     * A legacy guest network (GuestAccount): the jwt's guest_mode claim or the
     * server's `guest`. A guest is never sold a plan; it adds a sign-in method
     * to this network first.
     */
    val isGuestNetwork: StateFlow<Boolean> = combine(jwtManager.jwtFlow, _serverGuest) { jwt, serverGuest ->
        GuestAccount.isGuest(guestModeClaim = jwt?.guestMode == true, serverGuest = serverGuest)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    /**
     * The Solana return-path check (SolanaPaymentCheck): passed the old 20 s cap
     * without the payment landing, so the user is told it is still checking; and
     * ran out its two minutes, so the user gets the confirmation-delayed notice
     * instead of silence. Consumed-sequence pattern, like the timeout above.
     */
    private val _solanaStillCheckingSequence = MutableStateFlow(0L)
    val solanaStillCheckingSequence: StateFlow<Long> = _solanaStillCheckingSequence.asStateFlow()
    private var consumedSolanaStillCheckingSequence = 0L

    fun consumeSolanaStillCheckingSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedSolanaStillCheckingSequence) {
            return false
        }
        consumedSolanaStillCheckingSequence = sequence
        return true
    }

    private val _solanaCheckTimedOutSequence = MutableStateFlow(0L)
    val solanaCheckTimedOutSequence: StateFlow<Long> = _solanaCheckTimedOutSequence.asStateFlow()
    private var consumedSolanaCheckTimedOutSequence = 0L

    fun consumeSolanaCheckTimedOutSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedSolanaCheckTimedOutSequence) {
            return false
        }
        consumedSolanaCheckTimedOutSequence = sequence
        return true
    }

    private var solanaStillCheckingShown = false

    // ends the persisted pending payment once the check reaches an end
    private var solanaCheckFinished: (() -> Unit)? = null

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

                api.subscriptionBalanceForStorefront(storefrontCountry ?: "", SubscriptionBalanceCallback { result, err ->

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
                            _serverGuest.value = result.guest
                            if (serverIsPro) {
                                pendingSolanaPurchase?.let { (plan, amountUsd) ->
                                    pendingSolanaPurchase = null
                                    com.bringyour.network.analytics.ClientEvents.purchaseCompleted(
                                        Sdk.EventStoreSolana,
                                        com.bringyour.network.analytics.ClientEvents.PRODUCT_SOLANA_PRO_YEARLY,
                                        plan, false, amountUsd, "USD",
                                    )
                                }
                            }
                            val jwtIsPro = jwtManager.jwtFlow.value?.pro == true

                            val provideControlMode = deviceManager.provideControlMode
                            val sync = ProStatusSync.plan(serverIsPro, jwtIsPro, provideControlMode)
                            if (sync.provideControlMode != provideControlMode) {
                                deviceManager.provideControlMode = sync.provideControlMode
                            }
                            if (sync.refreshToken) {
                                deviceManager.device?.refreshToken(0)
                            }

                            result.currentSubscription?.store.let { store ->
                                _currentStore.value = store
                            }

                            // the onboarding plan data: absent on an older server
                            result.priceTier?.let { _priceTier.value = it }
                            _onboardingOffer.value = result.onboardingOffer
                            result.experiments?.let { _experiments.value = it }

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
     * So we want to display a different pending message. The check runs for up to
     * two minutes (finality plus webhook latency); `onFinished` runs once it is
     * confirmed or timed out, not when it is merely paused.
     */
    fun pollSolanaTransaction(
        maxDurationMs: Long = SolanaPaymentCheck.MAX_DURATION_MILLIS,
        onFinished: () -> Unit = {},
    ) {
        if (isPolling) return

        _isCheckingSolanaTransaction.value = true
        solanaStillCheckingShown = false
        solanaCheckFinished = onFinished
        startPolling(maxDurationMs)
    }

    private fun emitSolanaNotice(expired: Boolean) {
        if (!_isCheckingSolanaTransaction.value) {
            return
        }
        val notice = SolanaPaymentCheck.noticeFor(
            elapsedMillis = pollingSession.elapsedMillis(),
            expired = expired,
            confirmed = _hasActiveSubscription.value || isSupporterWithBalance(),
            stillCheckingShown = solanaStillCheckingShown,
        )
        when (notice) {
            SolanaPaymentCheck.Notice.StillChecking -> {
                solanaStillCheckingShown = true
                _solanaStillCheckingSequence.update { it + 1L }
            }
            SolanaPaymentCheck.Notice.TimedOut -> _solanaCheckTimedOutSequence.update { it + 1L }
            SolanaPaymentCheck.Notice.None -> Unit
        }
    }

    private fun finishSolanaCheck() {
        if (!_isCheckingSolanaTransaction.value) {
            return
        }
        val onFinished = solanaCheckFinished
        solanaCheckFinished = null
        onFinished?.invoke()
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
                emitSolanaNotice(expired = true)
                finishSolanaCheck()
                stopPolling()
                return
            }
            ForegroundPollingResume.ACTIVE -> Unit
        }

        pollingJob = viewModelScope.launch {
            fetchSubscriptionBalance()
            if (isSupporterWithBalance()) {
                finishSolanaCheck()
                stopPolling()
                return@launch
            }

            while (isPolling && isActive && !pollingSession.hasExpired()) {

                delay(pollingInterval)
                fetchSubscriptionBalance()
                if (isSupporterWithBalance()) {
                    finishSolanaCheck()
                    stopPolling()
                    break
                }
                emitSolanaNotice(expired = false)
            }

            if (isPolling) {
                Log.i(TAG, "polling timed out")
                emitConfirmationTimedOutIfUnconfirmed()
                emitSolanaNotice(expired = true)
                finishSolanaCheck()
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
        val foreground = processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        foregroundWork.setForeground(foreground)
        purchaseConfirmation.setForeground(foreground)
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundWork.setForeground(true)
        purchaseConfirmation.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundWork.setForeground(false)
        purchaseConfirmation.setForeground(false)
    }

    override fun onCleared() {
        processLifecycle.removeObserver(this)
        purchaseConfirmation.close()
        foregroundWork.close()
        stopPolling()
        stopBackgroundPolling()
        super.onCleared()
    }

}

/** PurchaseConfirmation.Source over the SDK's SubscriptionBalanceViewController. */
private class SdkPurchaseConfirmationSource(
    api: Api,
    onState: (String) -> Unit,
) : PurchaseConfirmation.Source {
    private val controller = Sdk.newSubscriptionBalanceViewController(api)
    private val listenerSub = controller.addPurchaseConfirmationListener(
        PurchaseConfirmationListener { state -> onState(state) }
    )

    override fun start() = controller.start()

    override fun setForeground(foreground: Boolean) = controller.setForeground(foreground)

    override fun startPurchaseConfirmation() = controller.startPurchaseConfirmation()

    override fun clearPurchaseConfirmation() = controller.clearPurchaseConfirmation()

    override fun isPro(): Boolean = controller.isPro

    override fun close() {
        listenerSub.close()
        controller.stop()
        controller.close()
    }
}
