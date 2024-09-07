package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
) {

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    UpgradePlanBottomSheetScaffold(
        scaffoldState = scaffoldState,
        scope = scope
    ) {
        AccountScreenContent(
            loginMode = accountViewModel.loginMode,
            navController = navController,
            scaffoldState = scaffoldState,
            scope = scope
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
            Text("Account", style = MaterialTheme.typography.headlineSmall)
            AccountSwitcher(loginMode = loginMode)
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
                            "Member",
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
                                Text("Guest",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            } else {
                                Text("Free",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }

                            if (loginMode == LoginMode.Guest) {
                                ClickableText(
                                    modifier = Modifier.offset(y = (-8).dp),
                                    text = AnnotatedString("Create account"),
                                    onClick = {
                                        application?.logout()
                                    },
                                    style = TextStyle(
                                        color = BlueMedium
                                    )
                                )
                            } else {
                                ClickableText(
                                    modifier = Modifier.offset(y = (-8).dp),
                                    text = AnnotatedString("Change"),
                                    onClick = {
                                        Log.i("AccountScreen", "expanding bottom sheet")
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

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // earnings area
                Box() {
                    Column {
                        Text(
                            "Earnings",
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
                            Row(
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text("0",
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
                            ClickableText(
                                modifier = Modifier.offset(y = -8.dp),
                                text = AnnotatedString("Set up wallet"),
                                onClick = {},
                                style = TextStyle(
                                    color = BlueMedium
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_user,
            text = "Profile",
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
            text = "Settings",
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
            text = "Wallet",
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
            text = "Refer and earn",
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
private fun AccountAuthenticatedPreview() {

    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    URNetworkTheme {

        UpgradePlanBottomSheetScaffold(
            scaffoldState = scaffoldState,
            scope = scope
        ) {
            AccountScreenContent(
                loginMode = LoginMode.Authenticated,
                navController = navController,
                scaffoldState = scaffoldState,
                scope = scope,
            )
        }
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

        UpgradePlanBottomSheetScaffold(
            scaffoldState = scaffoldState,
            scope = scope
        ) {
            AccountScreenContent(
                loginMode = LoginMode.Guest,
                navController = navController,
                scaffoldState = scaffoldState,
                scope = scope,
            )
        }
    }
}