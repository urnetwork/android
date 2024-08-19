package com.bringyour.network.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
) {

    val context = LocalContext.current
    var enableConnectionNotifications by remember { mutableStateOf(true) }
    var enableProductUpdates by remember { mutableStateOf(true) }

    val discordInviteLink = "https://discord.com/invite/RUNZXMwPRK"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Settings", style = TopBarTitleTextStyle)
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
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.height(64.dp))

            URTextInputLabel(text = "Plan")
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "URnetwork member",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    InfoIconWithOverlay() {
                        Column() {

                            Text(
                                "Unlock even faster speeds and first dibs on new features.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BlueLight
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        // todo - navigate to premium user flow
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Become a supporter",
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
                ClickableText(
                    text = AnnotatedString("Change"),
                    onClick = {},
                    style = TextStyle(
                        color = BlueMedium
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = "Notifications")
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Receive connection notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = enableConnectionNotifications,
                    toggle = {
                        enableConnectionNotifications = !enableConnectionNotifications
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = "Stay in touch")

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Send me product updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = enableProductUpdates,
                    toggle = {
                        enableProductUpdates = !enableProductUpdates
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
                        "Join the community on",
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
        SettingsScreen(navController)
    }
}