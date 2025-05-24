package com.bringyour.network.ui.account

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URNavListItem
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.UpgradePlanBottomSheet
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.formatDecimalString
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    overlayViewModel: OverlayViewModel,
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    totalPayoutAmount: Double,
    totalPayoutAmountInitialized: Boolean,
    walletCount: Int
) {

    val scope = rememberCoroutineScope()

    val networkUser by accountViewModel.networkUser.collectAsState()
    val currentPlan by subscriptionBalanceViewModel.currentPlan.collectAsState()

    val upgradePlanSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPresentingUpgradePlanSheet by remember { mutableStateOf(false) }
    val setIsPresentingUpgradePlanSheet: (Boolean) -> Unit = { isPresenting ->
        isPresentingUpgradePlanSheet = isPresenting
    }

    LaunchedEffect(Unit) {
        // This code runs when the screen appears
        subscriptionBalanceViewModel.fetchSubscriptionBalance()

        planViewModel.onUpgradeSuccess.collect {

            // poll subscription balance until it's updated
            subscriptionBalanceViewModel.pollSubscriptionBalance()

        }
    }

    Scaffold() { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Black)
                .padding(innerPadding)
                .padding(16.dp),
        ) {

            AccountScreenContent(
                loginMode = accountViewModel.loginMode,
                navController = navController,
                upgradePlanSheetState = upgradePlanSheetState,
                scope = scope,
                networkName = networkUser?.networkName,
                totalPayoutAmount = totalPayoutAmount,
                totalPayoutAmountInitialized = totalPayoutAmountInitialized,
                walletCount = walletCount,
                currentPlan = currentPlan,
                launchOverlay = overlayViewModel.launch,
                setIsPresentingUpgradePlanSheet = setIsPresentingUpgradePlanSheet,
                isProcessingUpgrade = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
            )

            if (isPresentingUpgradePlanSheet) {
                UpgradePlanBottomSheet(
                    sheetState = upgradePlanSheetState,
                    scope = scope,
                    planViewModel = planViewModel,
                    overlayViewModel = overlayViewModel,
                    setIsPresentingUpgradePlanSheet = setIsPresentingUpgradePlanSheet
                )
            }

        }

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreenContent(
    loginMode: LoginMode,
    navController: NavHostController,
    upgradePlanSheetState: SheetState,
    scope: CoroutineScope,
    networkName: String?,
    totalPayoutAmount: Double,
    totalPayoutAmountInitialized: Boolean,
    walletCount: Int,
    currentPlan: Plan,
    launchOverlay: (OverlayMode) -> Unit,
    setIsPresentingUpgradePlanSheet: (Boolean) -> Unit,
    isProcessingUpgrade: Boolean
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            // .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.account), style = MaterialTheme.typography.headlineSmall)
            AccountSwitcher(
                loginMode = loginMode,
                networkName = networkName,
                launchOverlay = launchOverlay
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .background(
                    Color(0xFF1C1C1C),
                    RoundedCornerShape(12.dp)
                )
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column() {

                AccountRootSubscription(
                    loginMode = loginMode,
                    currentPlan = currentPlan,
                    isProcessingUpgrade = isProcessingUpgrade,
                    scope = scope,
                    logout = {
                        application?.logout()

                        val intent = Intent(context, LoginActivity::class.java)
                        context.startActivity(intent)

                        (context as? Activity)?.finish()
                    },
                    setIsPresentingUpgradePlanSheet = setIsPresentingUpgradePlanSheet,
                    upgradePlanSheetState = upgradePlanSheetState
                )

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // earnings area
                Box() {
                    Column {
                        Text(
                            stringResource(id = R.string.earnings),
                            style = TextStyle(
                                color = TextMuted
                            )
                        )

                        Row() {
                            if (totalPayoutAmountInitialized) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                    ) {

                                        Text(if (totalPayoutAmount <= 0) "0" else formatDecimalString(totalPayoutAmount, 4),
                                            style = MaterialTheme.typography.headlineMedium
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text("USDC",
                                            modifier = Modifier.offset(y = -8.dp),
                                            style = TextStyle(
                                                color = TextMuted
                                            )
                                        )
                                    }

                                    if (walletCount <= 0) {
                                        Text(
                                            text = stringResource(id = R.string.set_up_wallet),
                                            modifier = Modifier
                                                .offset(y = (-8).dp)
                                                .clickable {
                                                    if (loginMode == LoginMode.Guest) {
                                                        launchOverlay(OverlayMode.GuestMode)
                                                    } else {
                                                        navController.navigate(Route.Wallets)
                                                    }
                                                },
                                            style = TextStyle(
                                                color = BlueMedium
                                            )
                                        )

                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.height(42.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(16.dp),
                                        color = TextMuted,
                                        trackColor = TextFaint,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_user,
            text = stringResource(id = R.string.profile),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    navController.navigate(Route.Profile)
                } else {
                    launchOverlay(OverlayMode.GuestMode)
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_settings,
            text = stringResource(id = R.string.settings),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    navController.navigate(Route.Settings)
                } else {
                    launchOverlay(OverlayMode.GuestMode)
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_wallet,
            text = stringResource(id = R.string.wallet),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    navController.navigate(Route.Wallets)
                } else {
                    launchOverlay(OverlayMode.GuestMode)
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_refer,
            text = stringResource(id = R.string.refer_and_earn),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    launchOverlay(OverlayMode.Refer)
                } else {
                    launchOverlay(OverlayMode.GuestMode)
                }
            }
        )
        HorizontalDivider()

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountSupporterAuthenticatedPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    URNetworkTheme {

        Scaffold() { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {

                AccountScreenContent(
                    loginMode = LoginMode.Authenticated,
                    navController = navController,
                    upgradePlanSheetState = sheetState,
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 120.12387,
                    totalPayoutAmountInitialized = true,
                    walletCount = 2,
                    currentPlan = Plan.Supporter,
                    launchOverlay = {},
                    setIsPresentingUpgradePlanSheet = {},
                    isProcessingUpgrade = false
                )
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountBasicAuthenticatedPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    URNetworkTheme {

        Scaffold() { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {

                AccountScreenContent(
                    loginMode = LoginMode.Authenticated,
                    navController = navController,
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 120.12387,
                    totalPayoutAmountInitialized = true,
                    walletCount = 2,
                    currentPlan = Plan.Basic,
                    launchOverlay = {},
                    upgradePlanSheetState = sheetState,
                    setIsPresentingUpgradePlanSheet = {},
                    isProcessingUpgrade = false
                )
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountGuestPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    URNetworkTheme {
        Scaffold() { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {

                AccountScreenContent(
                    loginMode = LoginMode.Guest,
                    navController = navController,
                    upgradePlanSheetState = sheetState,
                    setIsPresentingUpgradePlanSheet = {},
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 0.0,
                    totalPayoutAmountInitialized = true,
                    walletCount = 0,
                    currentPlan = Plan.Basic,
                    launchOverlay = {},
                    isProcessingUpgrade = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountGuestNoWalletPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    URNetworkTheme {

        Scaffold() { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                AccountScreenContent(
                    loginMode = LoginMode.Guest,
                    navController = navController,
                    upgradePlanSheetState = sheetState,
                    setIsPresentingUpgradePlanSheet = {},
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 0.0,
                    totalPayoutAmountInitialized = false,
                    walletCount = 0,
                    currentPlan = Plan.Basic,
                    launchOverlay = {},
                    isProcessingUpgrade = false
                )
            }
        }
    }
}