package com.bringyour.network.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import kotlinx.coroutines.delay

/**
 * Clears the connect request once insufficient balance has held for the grace
 * (see insufficientBalanceAutoDisconnect). Hosted above the tabs so it runs
 * whichever screen is showing. The effect restarts whenever the gate or the
 * requested connection changes, so the grace only counts a continuous hold;
 * the decision is checked again with fresh state after the wait.
 */
@Composable
fun InsufficientBalanceDisconnectEffect(
    connectViewModel: ConnectViewModel,
    isPro: Boolean,
    isPollingSubscriptionBalance: Boolean,
) {
    val context = LocalContext.current
    val contractStatus by connectViewModel.contractStatus.collectAsState()
    val connectStatus by connectViewModel.connectStatus.collectAsState()

    val currentPlan = if (isPro) Plan.Supporter else Plan.Basic
    val gate = insufficientBalanceGate(
        insufficientBalance = contractStatus?.insufficientBalance == true,
        currentPlan = currentPlan,
        isPollingSubscriptionBalance = isPollingSubscriptionBalance,
    )
    val connectionRequested = connectStatus != ConnectStatus.DISCONNECTED

    val latestPlan by rememberUpdatedState(currentPlan)
    val latestPolling by rememberUpdatedState(isPollingSubscriptionBalance)

    LaunchedEffect(gate, connectionRequested) {
        if (!gate || !connectionRequested) return@LaunchedEffect
        val gateSinceMillis = System.currentTimeMillis()
        delay(INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS)

        val device = connectViewModel.device
        val disconnect = insufficientBalanceAutoDisconnect(
            insufficientBalance = connectViewModel.contractStatus.value?.insufficientBalance == true,
            currentPlan = latestPlan,
            isPollingSubscriptionBalance = latestPolling,
            connectRequested = device?.connectEnabled == true,
            // routeLocal off is the kill switch: keep capture, fail closed
            killSwitch = device?.routeLocal == false,
            gateSinceMillis = gateSinceMillis,
            nowMillis = System.currentTimeMillis(),
        )
        if (disconnect) {
            (context.applicationContext as? MainApplication)?.disconnectForInsufficientBalance()
        }
    }
}
