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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.bringyour.network.ui.components.UsageBar
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.components.redeemTransferBalanceCode.RedeemTransferBalanceCodeSheet
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.formatDecimalString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    walletCount: Int,
    totalReferrals: Long,
    meanReliabilityWeight: Double,
    isPro: Boolean,
) {

    val scope = rememberCoroutineScope()

    val networkUser by accountViewModel.networkUser.collectAsState()
    val currentStore by subscriptionBalanceViewModel.currentStore.collectAsState()
    val availableBalanceByteCount by subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState()
    val dailyBalanceBytes by subscriptionBalanceViewModel.startBalanceByteCount.collectAsState()

    val refreshState = rememberPullToRefreshState()

    var isPresentingRedeemTransferBalanceSheet by remember { mutableStateOf(false) }
    val redeemTransferBalanceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        subscriptionBalanceViewModel.fetchSubscriptionBalance()
    }

    Scaffold() { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = subscriptionBalanceViewModel.isRefreshingSubscriptionBalance,
                state = refreshState,
                onRefresh = subscriptionBalanceViewModel.refreshSubscriptionBalance,
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(Black)
                        // .padding(16.dp),
                ) {

                    AccountScreenContent(
                        loginMode = accountViewModel.loginMode,
                        navController = navController,
                        scope = scope,
                        networkName = networkUser?.networkName,
                        totalPayoutAmount = totalPayoutAmount,
                        totalPayoutAmountInitialized = totalPayoutAmountInitialized,
                        walletCount = walletCount,
                        currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
                        currentStore = currentStore,
                        launchOverlay = overlayViewModel.launch,
                        isProcessingUpgrade = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
                        isCheckingSolanaTransaction = subscriptionBalanceViewModel.isCheckingSolanaTransaction.collectAsState().value,
                        isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPolling,
                        usedBytes = subscriptionBalanceViewModel.usedBalanceByteCount,
                        pendingBytes = subscriptionBalanceViewModel.pendingBalanceByteCount,
                        availableBytes = availableBalanceByteCount,
                        totalReferrals = totalReferrals,
                        meanReliabilityWeight = meanReliabilityWeight,
                        dailyBalanceBytes = dailyBalanceBytes,
                        launchRedeemTransferBalanceCodeSheet = {
                            isPresentingRedeemTransferBalanceSheet = true
                        }
                    )
                }
            }
        }

        if (isPresentingRedeemTransferBalanceSheet) {
            RedeemTransferBalanceCodeSheet(
                sheetState = redeemTransferBalanceSheetState,
                setIsPresenting = {
                    isPresentingRedeemTransferBalanceSheet = it
                },
                onSuccess = {
                    subscriptionBalanceViewModel.pollSubscriptionBalance()
                    overlayViewModel.launch(OverlayMode.Upgrade)
                }
            )
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreenContent(
    loginMode: LoginMode,
    navController: NavHostController,
    scope: CoroutineScope,
    networkName: String?,
    totalPayoutAmount: Double,
    totalPayoutAmountInitialized: Boolean,
    walletCount: Int,
    currentPlan: Plan,
    currentStore: String?,
    launchOverlay: (OverlayMode) -> Unit,
    isProcessingUpgrade: Boolean, // checking for Stripe, Apple, Play
    isCheckingSolanaTransaction: Boolean, // checking for potential Solana transaction
    isPollingSubscriptionBalance: Boolean,
    usedBytes: Long,
    availableBytes: Long,
    pendingBytes: Long,
    totalReferrals: Long,
    meanReliabilityWeight: Double,
    dailyBalanceBytes: Long,
    launchRedeemTransferBalanceCodeSheet: () -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                .padding(horizontal = 16.dp)
                .background(
                    OffBlack,
                    RoundedCornerShape(12.dp)
                )
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column() {

                AccountRootSubscription(
                    loginMode = loginMode,
                    currentPlan = currentPlan,
                    currentStore = currentStore,
//                    scope = scope,
                    logout = {
                        application?.logout()

                        val intent = Intent(context, LoginActivity::class.java)
                        context.startActivity(intent)

                        (context as? Activity)?.finish()
                    },
                    isProcessingUpgrade = isProcessingUpgrade,
                    isPollingSubscriptionBalance = isPollingSubscriptionBalance,
                    isCheckingSolanaTransaction = isCheckingSolanaTransaction,
                    navController = navController
                )

                Spacer(modifier = Modifier.height(12.dp))

                UsageBar(
                    usedBytes = usedBytes,
                    pendingBytes = pendingBytes,
                    availableBytes = availableBytes,
                    totalReferrals = totalReferrals,
                    meanReliabilityWeight = meanReliabilityWeight,
                    dailyByteCount = dailyBalanceBytes
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        stringResource(id = R.string.redeem_balance_code),
                        style = TextStyle(color = BlueMedium),
                        modifier = Modifier.clickable {
                            launchRedeemTransferBalanceCodeSheet()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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

        /**
         * view IP
         */
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://ur.io/ip")
                }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = stringResource(id = R.string.check_ip),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(id = R.string.check_ip))
            }
            Row {
                Icon(
                    Icons.Filled.ArrowOutward,
                    contentDescription = "Visit external link",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        URNodeCarousel()

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountSupporterAuthenticatedPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

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
                    currentPlan = Plan.Supporter,
                    launchOverlay = {},
                    isProcessingUpgrade = false,
                    usedBytes = 30_000,
                    pendingBytes = 10_000,
                    availableBytes = 60_000,
                    isCheckingSolanaTransaction = false,
                    isPollingSubscriptionBalance = false,
                    currentStore = null,
                    totalReferrals = 1,
                    meanReliabilityWeight = 0.1,
                    launchRedeemTransferBalanceCodeSheet = {},
                    dailyBalanceBytes = 10_000
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
                    isProcessingUpgrade = false,
                    usedBytes = 30_000,
                    pendingBytes = 10_000,
                    availableBytes = 60_000,
                    isCheckingSolanaTransaction = false,
                    isPollingSubscriptionBalance = false,
                    currentStore = null,
                    totalReferrals = 1,
                    meanReliabilityWeight = 0.1,
                    launchRedeemTransferBalanceCodeSheet = {},
                    dailyBalanceBytes = 10_000
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
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 0.0,
                    totalPayoutAmountInitialized = true,
                    walletCount = 0,
                    currentPlan = Plan.Basic,
                    launchOverlay = {},
                    isProcessingUpgrade = false,
                    usedBytes = 30_000,
                    pendingBytes = 10_000,
                    availableBytes = 60_000,
                    isCheckingSolanaTransaction = false,
                    isPollingSubscriptionBalance = false,
                    currentStore = null,
                    totalReferrals = 1,
                    meanReliabilityWeight = 0.1,
                    launchRedeemTransferBalanceCodeSheet = {},
                    dailyBalanceBytes = 10_000
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
                    scope = scope,
                    networkName = "ur_network",
                    totalPayoutAmount = 0.0,
                    totalPayoutAmountInitialized = false,
                    walletCount = 0,
                    currentPlan = Plan.Basic,
                    launchOverlay = {},
                    isProcessingUpgrade = false,
                    usedBytes = 30_000,
                    pendingBytes = 10_000,
                    availableBytes = 60_000,
                    isCheckingSolanaTransaction = false,
                    isPollingSubscriptionBalance = false,
                    currentStore = null,
                    totalReferrals = 1,
                    meanReliabilityWeight = 0.1,
                    launchRedeemTransferBalanceCodeSheet = {},
                    dailyBalanceBytes = 10_000
                )
            }
        }
    }
}