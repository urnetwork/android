package com.bringyour.network.ui.account

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URNavListItem
import com.bringyour.network.ui.components.AccountSwitcher
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    planViewModel: PlanViewModel,
    totalPayoutAmount: Double,
    totalPayoutAmountInitialized: Boolean,
    walletCount: Int
) {

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    val networkUser by accountViewModel.networkUser.collectAsState()

    UpgradePlanBottomSheetScaffold(
        scaffoldState = scaffoldState,
        scope = scope,
        planViewModel = planViewModel
    ) {
        AccountScreenContent(
            loginMode = accountViewModel.loginMode,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope,
            networkName = networkUser?.networkName,
            totalPayoutAmount = totalPayoutAmount,
            totalPayoutAmountInitialized = totalPayoutAmountInitialized,
            walletCount = walletCount,
            currentPlan = planViewModel.currentPlan
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreenContent(
    loginMode: LoginMode,
    navController: NavHostController,
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    networkName: String?,
    totalPayoutAmount: Double,
    totalPayoutAmountInitialized: Boolean,
    walletCount: Int,
    currentPlan: Plan
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.account), style = MaterialTheme.typography.headlineSmall)
            AccountSwitcher(
                loginMode = loginMode,
                networkName = networkName

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

                // member area
                Box() {
                    Column {
                        Text(
                            stringResource(id = R.string.member),
                            style = TextStyle(
                                color = TextMuted
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {

                            if (loginMode == LoginMode.Guest) {
                                Text(
                                    stringResource(id = R.string.guest),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            } else {

                                Text(if (currentPlan == Plan.Supporter) stringResource(id = R.string.supporter) else stringResource(id = R.string.free),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }

                            if (loginMode == LoginMode.Guest) {
                                Text(
                                    stringResource(id = R.string.create_account),
                                    style = TextStyle(
                                        color = BlueMedium
                                    ),
                                    modifier = Modifier
                                        .offset(y = (-8).dp)
                                        .clickable {
                                            application?.logout()
                                        }
                                )
                            } else {

                                if (currentPlan == Plan.Basic) {
                                    Text(
                                        stringResource(id = R.string.change),
                                        modifier = Modifier
                                            .offset(y = (-8).dp)
                                            .clickable {
                                                scope.launch {
                                                    scaffoldState.bottomSheetState.expand()
                                                }
                                            },
                                        style = TextStyle(
                                            color = BlueMedium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

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

                                        Text(if (totalPayoutAmount <= 0) "0" else totalPayoutAmount.toString(),
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
                                                    navController.navigate("wallets")
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
                    navController.navigate("profile")
                } else {
                    overlayVc?.openOverlay(OverlayMode.GuestMode.toString())
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_settings,
            text = stringResource(id = R.string.settings),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    navController.navigate("settings")
                } else {
                    overlayVc?.openOverlay(OverlayMode.GuestMode.toString())
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_wallet,
            text = stringResource(id = R.string.wallet),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    navController.navigate("wallets")
                } else {
                    overlayVc?.openOverlay(OverlayMode.GuestMode.toString())
                }
            }
        )
        HorizontalDivider()
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_refer,
            text = stringResource(id = R.string.refer_and_earn),
            onClick = {
                if (loginMode == LoginMode.Authenticated) {
                    overlayVc?.openOverlay(OverlayMode.Refer.toString())
                } else {
                    overlayVc?.openOverlay(OverlayMode.GuestMode.toString())
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

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    URNetworkTheme {

        AccountScreenContent(
            loginMode = LoginMode.Authenticated,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope,
            networkName = "ur_network",
            totalPayoutAmount = 120.12387,
            totalPayoutAmountInitialized = true,
            walletCount = 2,
            currentPlan = Plan.Supporter
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountBasicAuthenticatedPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    URNetworkTheme {

        AccountScreenContent(
            loginMode = LoginMode.Authenticated,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope,
            networkName = "ur_network",
            totalPayoutAmount = 120.12387,
            totalPayoutAmountInitialized = true,
            walletCount = 2,
            currentPlan = Plan.Basic
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountGuestPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    URNetworkTheme {

        AccountScreenContent(
            loginMode = LoginMode.Guest,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope,
            networkName = "ur_network",
            totalPayoutAmount = 0.0,
            totalPayoutAmountInitialized = true,
            walletCount = 0,
            currentPlan = Plan.Basic
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AccountGuestNoWalletPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    URNetworkTheme {
        AccountScreenContent(
            loginMode = LoginMode.Guest,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope,
            networkName = "ur_network",
            totalPayoutAmount = 0.0,
            totalPayoutAmountInitialized = false,
            walletCount = 0,
            currentPlan = Plan.Basic
        )
    }
}