package com.bringyour.network.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.R
import com.bringyour.network.ui.account.UpgradePlanBottomSheetScaffold
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    planViewModel: PlanViewModel,
    settingsViewModel: SettingsViewModel
) {

    val notificationsAllowed = settingsViewModel.permissionGranted.collectAsState().value

    if (planViewModel.currentPlan == Plan.Basic) {
        val scope = rememberCoroutineScope()

        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.Hidden,
                skipHiddenState = false
            )
        )

        UpgradePlanBottomSheetScaffold(
            scaffoldState = scaffoldState,
            scope = scope,
            planViewModel = planViewModel
        ) {
            SettingsScreen(
                navController,
                clientId = accountViewModel.clientId,
                currentPlan = planViewModel.currentPlan,
                notificationsAllowed = notificationsAllowed,
                requestAllowNotifications = settingsViewModel.triggerPermissionRequest,
                notificationsPermanentlyDenied = settingsViewModel.notificationsPermanentlyDenied,
                allowProductUpdates = settingsViewModel.allowProductUpdates,
                updateAllowProductUpdates = settingsViewModel.updateAllowProductUpdates
            )
        }
    } else {
        SettingsScreen(
            navController,
            clientId = accountViewModel.clientId,
            currentPlan = planViewModel.currentPlan,
            notificationsAllowed = notificationsAllowed,
            requestAllowNotifications = settingsViewModel.triggerPermissionRequest,
            notificationsPermanentlyDenied = settingsViewModel.notificationsPermanentlyDenied,
            allowProductUpdates = settingsViewModel.allowProductUpdates,
            updateAllowProductUpdates = settingsViewModel.updateAllowProductUpdates
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    clientId: String,
    currentPlan: Plan,
    notificationsAllowed: Boolean,
    notificationsPermanentlyDenied: Boolean,
    requestAllowNotifications: () -> Unit,
    allowProductUpdates: Boolean,
    updateAllowProductUpdates: (Boolean) -> Unit
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // todo - load this maybe as an config var?
    val discordInviteLink = "https://discord.com/invite/RUNZXMwPRK"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.settings),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {},
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.settings), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.height(64.dp))

            URTextInputLabel(text = stringResource(id = R.string.plan))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        if (currentPlan == Plan.Basic) "Basic" else "Supporter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )


                    if (currentPlan == Plan.Basic) {
                        Spacer(modifier = Modifier.width(2.dp))

                        InfoIconWithOverlay() {
                            Column() {

                                Text(
                                    stringResource(id = R.string.unlock_supporter_tooltip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BlueLight
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // todo - navigate to premium user flow
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(id = R.string.become_supporter),
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            lineHeight = 20.sp,
                                            fontWeight = FontWeight(700),
                                            color = BlueLight,
                                        )
                                    )
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Right Arrow",
                                        tint = BlueLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                }

                if (currentPlan == Plan.Basic) {
                    Text(
                        stringResource(id = R.string.change),
                        style = TextStyle(
                            color = BlueMedium
                        ),
                        modifier = Modifier.clickable {  }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(
                text = stringResource(id = R.string.client_id_label)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        clipboardManager.setText(AnnotatedString(clientId))
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

            ) {
                Text(
                    clientId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = "Copy",
                    tint = TextMuted,
                    modifier = Modifier.width(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = stringResource(id = R.string.share_label))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        clipboardManager.setText(
                            AnnotatedString(clientId)
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

                ) {
                Text(
                    "https://ur.io/c?$clientId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = "Copy",
                    tint = TextMuted,
                    modifier = Modifier.width(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // allow notifications
            URTextInputLabel(text = stringResource(id = R.string.notifications_label))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.receive_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = notificationsAllowed,
                    enabled = !notificationsAllowed && !notificationsPermanentlyDenied,
                    toggle = {
                        requestAllowNotifications()
                    },
                )
            }

            Text(
                if (notificationsPermanentlyDenied || notificationsAllowed) stringResource(id = R.string.update_notification_settings) else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            URTextInputLabel(text = stringResource(id = R.string.stay_in_touch))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.send_product_updates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = allowProductUpdates,
                    toggle = {
                        updateAllowProductUpdates(!allowProductUpdates)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.join_community_discord),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    URLinkText(
                        text = "Discord",
                        url = discordInviteLink,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(discordInviteLink))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Right Arrow",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Basic,
            notificationsAllowed = true,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            updateAllowProductUpdates = {}
        )
    }
}

@Preview
@Composable
fun SettingsScreenSupporterPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = true,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            updateAllowProductUpdates = {}
        )
    }
}

@Preview
@Composable
fun SettingsScreenNotificationsDisabledPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = false,
            notificationsPermanentlyDenied = true,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            updateAllowProductUpdates = {}
        )
    }
}

@Preview
@Composable
fun SettingsScreenNotificationsAllowedPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = false,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = false,
            updateAllowProductUpdates = {}
        )
    }
}