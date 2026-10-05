package com.bringyour.network.ui.connect

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan

/**
 * Feeds the process-wide InsufficientBalanceMonitor, and the start connect
 * gate's plan and poll state, from the state the connect screen shows. Hosted
 * above the tabs so it runs whichever screen is showing; plan and balance poll
 * state only exist in the ui.
 *
 * It also feeds the balance recovery (BalanceRecovery) every account balance
 * reading, so a connect insufficient balance blocked is retried once the
 * balance is back, while the app is in front, and says so.
 */
@Composable
fun InsufficientBalanceNoticeEffect(
    connectViewModel: ConnectViewModel,
    isPro: Boolean,
    isPollingSubscriptionBalance: Boolean,
) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication ?: return
    val contractStatus by connectViewModel.contractStatus.collectAsState()
    val connectStatus by connectViewModel.connectStatus.collectAsState()
    // bumps with every account balance reading (the ui poll, a start connect fetch)
    val balanceChanges by com.bringyour.network.widgets.WidgetSnapshotStore.changes.collectAsState()

    val insufficientBalance = contractStatus?.insufficientBalance == true
    val connectRequested = connectStatus != ConnectStatus.DISCONNECTED

    // the start connect gate outside the ui reads the same plan and poll state
    SideEffect {
        application.uiIsPro = isPro
        application.uiPollingSubscriptionBalance = isPollingSubscriptionBalance
    }

    LaunchedEffect(insufficientBalance, isPro, isPollingSubscriptionBalance, connectRequested) {
        application.insufficientBalanceMonitor.update(
            insufficientBalance = insufficientBalance,
            currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
            isPollingSubscriptionBalance = isPollingSubscriptionBalance,
            connectRequested = connectRequested,
        )
    }

    LaunchedEffect(insufficientBalance, isPro, isPollingSubscriptionBalance, connectRequested, balanceChanges) {
        val retried = application.observeBalanceRecovery(
            gate = insufficientBalanceGate(
                insufficientBalance = insufficientBalance,
                currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
                isPollingSubscriptionBalance = isPollingSubscriptionBalance,
            ),
            connectRequested = connectRequested,
        )
        if (retried) {
            Toast.makeText(context, context.getString(R.string.insufficient_balance_reconnecting), Toast.LENGTH_LONG).show()
        }
    }
}
