package com.bringyour.network.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.utils.isTablet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheetScaffold(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    content: @Composable (PaddingValues) -> Unit,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc

    val containerModifier = Modifier

    if (isTablet()) {
        containerModifier
            .fillMaxWidth()
    } else {
        containerModifier
            .requiredWidth(LocalConfiguration.current.screenWidthDp.dp + 4.dp)
            .fillMaxHeight()
            // .offset(y = 1.dp)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetContent = {
            Box(
                modifier = containerModifier
                    .offset(y = 1.dp)
                    .border(
                        1.dp,
                        MainBorderBase,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp
                        )
                    )
            ) {

                CenterAlignedTopAppBar(
                    title = {
                        Text("Upgrade Subscription", style = TopBarTitleTextStyle)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                )

                val colModifier = Modifier

                if (!isTablet()) {
                    colModifier.fillMaxSize()
                }

                Column(
                    modifier = colModifier
                        .background(color = Black)
                        // .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                    // horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Column {
                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Become a",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                "$5/month",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontFamily = gravityCondensedFamily,
                                    color = TextMuted
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "URnetwork",
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Supporter",
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Support us in building a new kind of network that gives instead of takes.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "You’ll unlock even faster speeds, and first dibs on new features like robust anti-censorship measures and data control.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(64.dp))

                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            URButton(onClick = {

                                // todo - process plan upgrade

                                overlayVc?.openOverlay(OverlayMode.Upgrade.toString())

                                scope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }


                            }) { buttonTextStyle ->
                                Text("Join the movement", style = buttonTextStyle)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

            }
        }) { innerPadding ->
            content(innerPadding)
        }
    }



@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun UpgradePlanModalBottomSheetPreview() {

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )
    val scope = rememberCoroutineScope()

    URNetworkTheme {
        UpgradePlanBottomSheetScaffold(
            scaffoldState = scaffoldState,
            scope = scope
        ) {
            Text("Hello world")
        }
    }
}