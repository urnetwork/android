package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.UsageBar
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.stats.BlockActionsViewModel
import com.bringyour.network.ui.stats.BlockerViewModel
import com.bringyour.network.ui.stats.ConnectStatsSections
import com.bringyour.network.ui.stats.DnsSettingsViewModel
import com.bringyour.network.ui.stats.ThroughputViewModel
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.ConnectLocation

@Composable
fun ConnectActions(
    navController: NavController,
    selectedLocation: ConnectLocation?,
    peerCount: Int,
    providerDiscoverable: Boolean,
    deviceName: String,
    selectedPeerName: String?,
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
    dailyByteCount: Long,
    launchIntro: () -> Unit,
    multipleIps: Boolean,
    toggleMultipleIps: () -> Unit,
    selectedWindowType: WindowType,
    setSelectedWindowType: (WindowType) -> Unit,
    allowDirect: Boolean,
    toggleAllowDirect: () -> Unit,
    postQuantumEncryption: Boolean,
    togglePostQuantumEncryption: () -> Unit,
    throughputViewModel: ThroughputViewModel,
    blockActionsViewModel: BlockActionsViewModel,
    dnsSettingsViewModel: DnsSettingsViewModel,
    blockerViewModel: BlockerViewModel,
    // opens the referral flow from the usage bar referral row
    onReferralClick: () -> Unit,
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
                selectedPeerName = selectedPeerName,
                getLocationColor = getLocationColor,
                onClick = { presentSelectProvider(true) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * Connect / Disconnect buttons
             */
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

            // the extra top spacing pushes the peers line just below the collapsed
            // drawer's peek fold, so it appears only when the drawer opens (iOS parity)
            Spacer(modifier = Modifier.height(24.dp))

            /**
             * Network peers status line: a small dot (brand green when peers are online,
             * amber at zero) + "{n} peers", always shown. Tapping opens the location
             * chooser, which lists these peers at its top. Mirrors the iOS drawer.
             */
            NetworkPeersStatusLine(
                peerCount = peerCount,
                onClick = { presentSelectProvider(true) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            /**
             * Second line under the peers count: whether this device is itself
             * discoverable/connectable as a peer (providing to same-network peers).
             */
            Text(
                when {
                    providerDiscoverable && deviceName.isNotEmpty() ->
                        stringResource(id = R.string.device_discoverable_as, deviceName)
                    providerDiscoverable ->
                        stringResource(id = R.string.device_discoverable)
                    else ->
                        stringResource(id = R.string.device_not_discoverable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Window Type Segmented Button
             */
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                WindowType.entries.forEachIndexed { index, windowType ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = WindowType.entries.size
                        ),
                        onClick = {
                            setSelectedWindowType(windowType)
                        },
                        selected = selectedWindowType == windowType,
                        label = {
                            WindowTypeButtonText(windowType)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.multiple_ips),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                /**
                  * Multiple IPs switch. Available identically in Web and
                  * Streaming -- the two differ in how providers are selected,
                  * not in whether the connection can spread across exits.
                  * Auto balances across both windows and ignores an explicit
                  * window size, so the switch has nothing to act on there.
                  */

                URSwitch(
                    checked = multipleIps,
                    toggle = {
                        toggleMultipleIps()
                    },
                    enabled = selectedWindowType != WindowType.AUTO
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.strong_anonymization),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                /**
                 * Allow direct
                 * When "Strong Anonymization" is true, "allowDirect" is false and vice versa
                 */

                URSwitch(
                    checked = !allowDirect,
                    toggle = {
                        toggleAllowDirect()
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.post_quantum_encryption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                /**
                 * Post quantum encryption
                 * Opportunistic e2e: providers without support fall back to
                 * plaintext at this layer
                 */

                URSwitch(
                    checked = postQuantumEncryption,
                    toggle = {
                        togglePostQuantumEncryption()
                    },
                )
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        /**
         * Statistics and dns sections
         */
        ConnectStatsSections(
            navController = navController,
            throughputViewModel = throughputViewModel,
            blockActionsViewModel = blockActionsViewModel,
            dnsSettingsViewModel = dnsSettingsViewModel,
            blockerViewModel = blockerViewModel,
        )

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

                        if (currentPlan == Plan.Supporter) {
                            Text(stringResource(id = R.string.supporter),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        } else {
                            Text(stringResource(id = R.string.free),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    if (currentPlan != Plan.Supporter) {

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
            }

            Spacer(modifier = Modifier.height(12.dp))

            UsageBar(
                usedBytes = usedBytes,
                pendingBytes = pendingBytes,
                availableBytes = availableBytes,
                meanReliabilityWeight = meanReliabilityWeight,
                totalReferrals = totalReferrals,
                dailyByteCount = dailyByteCount,
                onReferralClick = onReferralClick
            )

        }
    }
}

@Composable
fun OpenProviderListButton(
    selectedLocation: ConnectLocation?,
    selectedPeerName: String?,
    getLocationColor: (String) -> Color,
    onClick: () -> Unit
) {

    val text = when {
        // a selected network peer: show its live device name (the same label as the peer list)
        selectedPeerName != null -> selectedPeerName
        selectedLocation == null || selectedLocation.connectLocationId.bestAvailable ->
            stringResource(id = R.string.best_available_provider)
        else -> selectedLocation.name
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
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                painter = painterResource(id = R.drawable.main_nav_globe),
                contentDescription = stringResource(id = R.string.select_location_provider_content_description),
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
                            pluralStringResource(
                                id = R.plurals.provider_count,
                                count = selectedLocation.providerCount,
                                selectedLocation.providerCount,
                            ),
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

@Composable
fun NetworkPeersStatusLine(
    peerCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        // status dot: brand green when peers are online, amber when none
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (peerCount > 0) Green else Amber,
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            pluralStringResource(
                id = R.plurals.network_peer_count,
                count = peerCount,
                peerCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
fun WindowTypeButtonText(windowType: WindowType) {
    val displayName = stringResource(
        when (windowType) {
            WindowType.AUTO -> R.string.window_type_auto
            WindowType.QUALITY -> R.string.window_type_quality
            WindowType.SPEED -> R.string.window_type_speed
        }
    )

    Text(displayName)
}