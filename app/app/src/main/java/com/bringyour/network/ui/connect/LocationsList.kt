package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.bringyour.client.ConnectLocation
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsList(
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
    devices: List<ConnectLocation>,
    onLocationSelect: (ConnectLocation?) -> Unit,
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {

    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {

        LazyColumn {

            if (promotedLocations.isEmpty() && connectCountries.isEmpty() && devices.isEmpty()) {

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = stringResource(id = R.string.check_internet),
                            tint = Yellow
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Row {
                                Text(
                                    stringResource(id = R.string.check_internet),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }

                            Row {
                                Text(
                                    stringResource(id = R.string.no_providers_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

            } else {
                if (promotedLocations.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.promoted_locations))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        ProviderRow(
                            location = stringResource(id = R.string.best_available_provider),
                            onClick = {
                                // passing null for connect location will connect to best available
                                onLocationSelect(null)
                            },
                            color = Red400,
                            isSelected = selectedLocation?.connectLocationId?.bestAvailable == true
                        )
                    }

                    items(promotedLocations) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.connectLocationId.toString())
                        )
                    }
                }

                if (connectCountries.isNotEmpty()) {
                    item {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.countries))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    items(connectCountries) { country ->
                        ProviderRow(
                            location = country.name,
                            providerCount = country.providerCount,
                            onClick = {
                                onLocationSelect(country)
                            },
                            isSelected = selectedLocation?.connectLocationId == country.connectLocationId,
                            color = getLocationColor(country.countryCode)
                        )
                    }
                }

                if (devices.isNotEmpty()) {
                    item {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.devices))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    items(devices) { device ->
                        ProviderRow(
                            location = device.name,
                            providerCount = device.providerCount,
                            onClick = {
                                onLocationSelect(device)
                            },
                            isSelected = selectedLocation?.connectLocationId == device.connectLocationId,
                            color = getLocationColor(device.connectLocationId.toString())
                        )
                    }
                }
            }
        }
    }
}
