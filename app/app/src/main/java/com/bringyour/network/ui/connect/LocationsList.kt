package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.bringyour.sdk.ConnectLocation
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Red400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsList(
    searchQuery: String,
    connectCountries: List<ConnectLocation>,
    cities: List<ConnectLocation>,
    regions: List<ConnectLocation>,
    devices: List<ConnectLocation>,
    bestSearchMatches: List<ConnectLocation>,
    onLocationSelect: (ConnectLocation?) -> Unit,
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    onRefresh: () -> Unit,
    onFocusChanged: () -> Unit = {},
    listState: LazyListState,
    isRefreshing: Boolean
) {

    if (
        connectCountries.isEmpty() &&
        devices.isEmpty() &&
        regions.isEmpty() &&
        cities.isEmpty() &&
        bestSearchMatches.isEmpty() &&
        searchQuery.isEmpty()) {
        // there has probably been an uncaught error
        // everything is empty, including search
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            FetchLocationsError(
                onRefresh = onRefresh
            )
        }
    } else if (
        connectCountries.isEmpty() &&
        devices.isEmpty() &&
        regions.isEmpty() &&
        cities.isEmpty() &&
        bestSearchMatches.isEmpty() &&
        searchQuery.isNotEmpty()
        ) {
            // searching but no results found!
            NoLocationsFound()
    } else {
        // success

        PullToRefreshBox(
            onRefresh = onRefresh,
            isRefreshing = isRefreshing
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = listState
                // verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                if (bestSearchMatches.isNotEmpty()) {
                    item {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.top_matches))
                        }
                    }

                    items(bestSearchMatches) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.connectLocationId.toString()),
                            onFocusChanged = onFocusChanged,
                            isStable = location.stable,
                            isStrongPrivacy = location.strongPrivacy
                        )
                    }
                }


                if (bestSearchMatches.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.promoted_locations))
                        }
                    }

                    item {
                        ProviderRow(
                            location = stringResource(id = R.string.best_available_provider),
                            onClick = {
                                // passing null for connect location will connect to best available
                                onLocationSelect(null)
                            },
                            color = Red400,
                            isSelected = selectedLocation?.connectLocationId?.bestAvailable == true,
                            onFocusChanged = onFocusChanged,
                            isStable = true,
                            isStrongPrivacy = false
                        )
                    }

                }

                if (connectCountries.isNotEmpty()) {
                    item {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.countries))
                        }
                    }

                    items(connectCountries) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.countryCode),
                            onFocusChanged = onFocusChanged,
                            isStable = location.stable,
                            isStrongPrivacy = location.strongPrivacy
                        )
                    }
                }

                if (regions.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.regions))
                        }
                    }

                    items(regions) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.connectLocationId.toString()),
                            onFocusChanged = onFocusChanged,
                            isStable = location.stable,
                            isStrongPrivacy = location.strongPrivacy
                        )
                    }
                }

                if (cities.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.cities))
                        }
                    }

                    items(cities) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.connectLocationId.toString()),
                            onFocusChanged = onFocusChanged,
                            isStable = location.stable,
                            isStrongPrivacy = location.strongPrivacy
                        )
                    }
                }

                if (devices.isNotEmpty()) {
                    item {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.devices))
                        }
                    }

                    items(devices) { location ->
                        ProviderRow(
                            location = location.name,
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                            color = getLocationColor(location.connectLocationId.toString()),
                            onFocusChanged = onFocusChanged,
                            isStable = location.stable,
                            isStrongPrivacy = location.strongPrivacy
                        )
                    }
                }
            }

        }
    }
}
