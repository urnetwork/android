package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.bringyour.client.ConnectLocation
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Red400

@Composable
fun LocationsList(
    searchQuery: String,
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
    devices: List<ConnectLocation>,
    onLocationSelect: (ConnectLocation?) -> Unit,
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    onRefresh: () -> Unit,
) {

    if (promotedLocations.isEmpty() && connectCountries.isEmpty() && devices.isEmpty() && searchQuery.isEmpty()) {
        FetchLocationsError(
            onRefresh = onRefresh
        )
    } else if (promotedLocations.isEmpty() && connectCountries.isEmpty() && devices.isEmpty() && searchQuery.isNotEmpty()) {
        // searching but no results found!
        NoLocationsFound()
    } else {
        // success
        LazyColumn {
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
