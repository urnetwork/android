package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.UsageBar
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.ConnectLocation

@Composable
fun ConnectActions(
    navController: NavController,
    selectedLocation: ConnectLocation?,
    presentSelectProvider: (Boolean) -> Unit,
    getLocationColor: (String) -> Color,
    minHeight: Dp,
    currentPlan: Plan,
    connect: () -> Unit,
    disconnect: () -> Unit,
    reconnectTunnel: () -> Unit,
    connectStatus: ConnectStatus,
    isPollingSubscriptionBalance: Boolean,
    displayReconnectTunnel: Boolean,
    insufficientBalance: Boolean,
    usedBytes: Long,
    availableBytes: Long,
    pendingBytes: Long,
    meanReliabilityWeight: Double,
    totalReferrals: Long,
    launchIntro: () -> Unit,
) {

    Column(
        modifier = Modifier
            .defaultMinSize(minHeight = minHeight)
            .fillMaxWidth()
    ) {


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {

            OpenProviderListButton(
                selectedLocation = selectedLocation,
                getLocationColor = getLocationColor,
                onClick = { presentSelectProvider(true) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .height(48.dp)
            ) {

                if (insufficientBalance && currentPlan != Plan.Supporter && !isPollingSubscriptionBalance) {
                    URButton(
                        onClick = {
                            navController.navigate(Route.Upgrade)
                        },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(id = R.string.insufficient_balance),
                                style = buttonTextStyle,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {

                    if (connectStatus == ConnectStatus.DISCONNECTED) {
                        URButton(onClick = connect) { buttonTextStyle ->
                            Text(
                                stringResource(id = R.string.connect),
                                style = buttonTextStyle,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (connectStatus != ConnectStatus.DISCONNECTED && !displayReconnectTunnel) {
                        URButton(
                            onClick = disconnect,
                            style = ButtonStyle.OUTLINE
                        ) { buttonTextStyle ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {

                                Text(
                                    stringResource(id = R.string.disconnect),
                                    style = buttonTextStyle,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                            }
                        }
                    }

                    if (displayReconnectTunnel) {
                        URButton(
                            onClick = {
//                                application?.startVpnService()
                                reconnectTunnel()
                            },
                            style = ButtonStyle.OUTLINE
                        ) { buttonTextStyle ->
                            Text(
                                stringResource(id = R.string.reconnect),
                                style = buttonTextStyle,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (currentPlan != Plan.Supporter) {

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MainTintedBackgroundBase,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {


                // member area
                Column {
                    Text(
                        stringResource(id = R.string.plan),
                        style = TextStyle(
                            color = TextMuted
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {

                        if (isPollingSubscriptionBalance) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(24.dp),
                                    color = TextMuted,
                                    trackColor = TextFaint,
                                    strokeWidth = 2.dp
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(stringResource(id = R.string.checking_payment),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextMuted
                                )

                            }

                        } else {
                            Text(stringResource(id = R.string.free),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }


                        TextButton(onClick = {
                            launchIntro()
                        }) {
                            Text(
                                stringResource(id = R.string.get_pro),
                                style = TextStyle(
                                    color = Pink
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                UsageBar(
                    usedBytes = usedBytes,
                    pendingBytes = pendingBytes,
                    availableBytes = availableBytes,
                    meanReliabilityWeight = meanReliabilityWeight,
                    totalReferrals = totalReferrals
                )

            }
        }
    }
}

@Composable
fun OpenProviderListButton(
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    onClick: () -> Unit
) {

    val text = if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
        stringResource(id = R.string.best_available_provider)
    } else {
        selectedLocation.name
    }

    val iconTint = if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
        Red400
    } else {

        val key =
            if (selectedLocation.countryCode.isNullOrEmpty()) selectedLocation.connectLocationId.toString()
            else selectedLocation.countryCode

        getLocationColor(key)
    }

    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                painter = painterResource(id = R.drawable.main_nav_globe),
                contentDescription = "Select location provider globe",
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column() {

                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (selectedLocation != null) {

                    if (selectedLocation.providerCount > 0) {
                        Text(
                            stringResource(R.string.provider_count, selectedLocation.providerCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }

                    // todo - show warning if unstable

                }

            }
            Spacer(modifier = Modifier.width(4.dp))

        }

        TextButton(onClick = onClick) {
            Text(
                stringResource(id = R.string.change),
                color = Pink
            )
        }
    }

}