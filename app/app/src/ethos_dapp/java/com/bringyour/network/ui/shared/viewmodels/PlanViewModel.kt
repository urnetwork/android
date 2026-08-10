package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.NetworkSpaceManagerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private val _onUpgradeSuccess = MutableSharedFlow<Unit>()
    val onUpgradeSuccess: SharedFlow<Unit> = _onUpgradeSuccess.asSharedFlow()

    private val _upgradeSuccessSequence = MutableStateFlow(0L)
    val upgradeSuccessSequence: StateFlow<Long> = _upgradeSuccessSequence.asStateFlow()
    private var consumedUpgradeSuccessSequence = 0L

    private val _restoredSubscriptionSequence = MutableStateFlow(0L)
    val restoredSubscriptionSequence: StateFlow<Long> = _restoredSubscriptionSequence.asStateFlow()
    private var consumedRestoredSubscriptionSequence = 0L

    /**
     * A purchase the store accepted but has not completed (awaiting approval, or an
     * out-of-band payment). This flavor has no Google Play billing, so it is never
     * emitted here -- the sequence stays 0 and the collector in MainNavHost (which is
     * shared across flavors) simply never fires. It exists so that shared UI can handle
     * the pending case uniformly wherever a store CAN report it.
     */
    private val _purchasePendingSequence = MutableStateFlow(0L)
    val purchasePendingSequence: StateFlow<Long> = _purchasePendingSequence.asStateFlow()
    private var consumedPurchasePendingSequence = 0L

    /**
     * Play purchase-report outcomes (wrong network / report deferred). Only the Play
     * flavor reports store purchases to the server, so these are never emitted here
     * -- the sequences stay 0 and the shared MainNavHost collectors never fire.
     */
    private val _purchaseWrongNetworkSequence = MutableStateFlow(0L)
    val purchaseWrongNetworkSequence: StateFlow<Long> = _purchaseWrongNetworkSequence.asStateFlow()
    private var consumedPurchaseWrongNetworkSequence = 0L

    private val _purchaseReportDeferredSequence = MutableStateFlow(0L)
    val purchaseReportDeferredSequence: StateFlow<Long> = _purchaseReportDeferredSequence.asStateFlow()
    private var consumedPurchaseReportDeferredSequence = 0L

    val triggerUpgradeSuccess: () -> Unit = {
        _upgradeSuccessSequence.update { it + 1L }
        viewModelScope.launch {
            _onUpgradeSuccess.emit(Unit)
        }
    }

    fun consumeUpgradeSuccessSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedUpgradeSuccessSequence) {
            return false
        }
        consumedUpgradeSuccessSequence = sequence
        return true
    }

    fun consumePurchasePendingSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchasePendingSequence) {
            return false
        }
        consumedPurchasePendingSequence = sequence
        return true
    }

    fun consumePurchaseWrongNetworkSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchaseWrongNetworkSequence) {
            return false
        }
        consumedPurchaseWrongNetworkSequence = sequence
        return true
    }

    fun consumePurchaseReportDeferredSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchaseReportDeferredSequence) {
            return false
        }
        consumedPurchaseReportDeferredSequence = sequence
        return true
    }

    fun consumeRestoredSubscriptionSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedRestoredSubscriptionSequence) {
            return false
        }
        consumedRestoredSubscriptionSequence = sequence
        return true
    }

    /**
     * Billing errors, surfaced by the shared UI for EVERY flavor. Nothing sets this
     * here today (this flavor has no Google Play billing), but any payment path that
     * fails must be able to put a message in front of the user rather than failing
     * silently — which is what used to happen everywhere.
     */
    var changePlanError by mutableStateOf<String?>(null)
        private set

    val setChangePlanError: (String?) -> Unit = { msg ->
        changePlanError = msg
    }

    /**
     * No Play Billing purchase to re-launch on this flavor, so there is nothing to
     * retry from the error dialog — it offers only Close. A "Try again" button that
     * did nothing would be worse than not offering one.
     */
    val retryUpgrade: (() -> Unit)? = null

    var networkId by mutableStateOf<String?>(null)
        private set

    // fixme: can we pull this from stripe? should we?
    val formattedSubscriptionPrice = "5.00"

    var inProgress by mutableStateOf(false)
        private set

    init {
        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, success ->
            networkId = if (success) jwt?.networkId?.toString() else null
        }
    }
}
