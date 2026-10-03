package com.bringyour.network.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan

/**
 * Feeds the process-wide InsufficientBalanceMonitor from the state the connect
 * screen shows. Hosted above the tabs so it runs whichever screen is showing;
 * plan and balance poll state only exist in the ui.
 */
@Composable
fun InsufficientBalanceNoticeEffect(
    connectViewModel: ConnectViewModel,
    isPro: Boolean,
    isPollingSubscriptionBalance: Boolean,
) {
    val application = LocalContext.current.applicationContext as? MainApplication ?: return
    val contractStatus by connectViewModel.contractStatus.collectAsState()
    val connectStatus by connectViewModel.connectStatus.collectAsState()

    val insufficientBalance = contractStatus?.insufficientBalance == true
    val connectRequested = connectStatus != ConnectStatus.DISCONNECTED

    LaunchedEffect(insufficientBalance, isPro, isPollingSubscriptionBalance, connectRequested) {
        application.insufficientBalanceMonitor.update(
            insufficientBalance = insufficientBalance,
            currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
            isPollingSubscriptionBalance = isPollingSubscriptionBalance,
            connectRequested = connectRequested,
        )
    }
}
