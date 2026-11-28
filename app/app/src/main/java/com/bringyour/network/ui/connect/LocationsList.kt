package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import com.bringyour.sdk.ConnectLocation
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ContextMenuItem
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.stats.NetworkPeerUi
import com.bringyour.network.ui.stats.networkPeersSection
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/** What a location row's context menu offers (adaptive app quality, Context_Menus). */
enum class LocationMenuAction { Connect, Disconnect }

/**
 * A location row's context menu, only the connect screen's own actions:
 * Disconnect on the location the connection is on while one is requested,
 * and Connect on every other row, which is what a click on the row does.
 * Pure, for tests.
 */
fun locationMenuActions(selected: Boolean, connectRequested: Boolean): List<LocationMenuAction> =
    if (selected && connectRequested) {
        listOf(LocationMenuAction.Disconnect)
    } else {
        listOf(LocationMenuAction.Connect)
    }

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
    isRefreshing: Boolean,
    networkPeers: List<NetworkPeerUi> = listOf(),
    onConnectPeer: (NetworkPeerUi) -> Unit = {},
    // whether a connection is requested (connecting or connected), and the
    // connect screen's Disconnect: the menu of the row it is on offers it
    connectRequested: Boolean = false,
    onDisconnect: () -> Unit = {},
    // the user's favorites, the first section while not searching, and adding
    // or removing a row's location (its context menu)
    favorites: List<ConnectLocation> = listOf(),
    isFavorite: (ConnectLocation) -> Boolean = { false },
    toggleFavorite: (ConnectLocation) -> Unit = {},
) {

    // a row's context menu (Context_Menus): Connect, as its click, or
    // Disconnect on the location the connection is on
    val connectLabel = stringResource(id = R.string.connect)
    val disconnectLabel = stringResource(id = R.string.disconnect)
    val locationMenu: (Boolean, () -> Unit) -> List<ContextMenuItem> = { selected, connect ->
        locationMenuActions(selected = selected, connectRequested = connectRequested).map { action ->
            when (action) {
                LocationMenuAction.Connect -> ContextMenuItem(label = connectLabel, onClick = connect)
                LocationMenuAction.Disconnect -> ContextMenuItem(label = disconnectLabel, onClick = onDisconnect)
            }
        }
    }
    // then Add to or Remove from favorites, where the location can be one
    val addToFavoritesLabel = stringResource(id = R.string.add_to_favorites)
    val removeFromFavoritesLabel = stringResource(id = R.string.remove_from_favorites)
    val favoriteMenu: (ConnectLocation) -> List<ContextMenuItem> = { location ->
        if (Sdk.canFavoriteLocation(location)) {
            listOf(
                ContextMenuItem(
                    label = if (isFavorite(location)) removeFromFavoritesLabel else addToFavoritesLabel,
                    onClick = { toggleFavorite(location) },
                )
            )
        } else {
            listOf()
        }
    }
    val favoriteExitNote = stringResource(id = R.string.favorite_exit_note)

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

                // the user's favorites first: a tap connects, as on every row,
                // and an exit, one member's device, says it can go away
                if (favorites.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(stringResource(id = R.string.favorites))
                        }
                    }

                    itemsIndexed(
                        favorites,
                        key = { index, location ->
                            indexedLazyListKey("favorite", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        Column {
                            ProviderRow(
                                location = locationDisplayName(location),
                                onClick = {
                                    onLocationSelect(location)
                                },
                                isSelected = selected,
                                contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
                                color = getLocationColor(
                                    if (location.locationType == Sdk.LocationTypeCountry) location.countryCode
                                    else location.connectLocationId.toString()
                                ),
                                onFocusChanged = onFocusChanged,
                                isStable = location.stable,
                                isStrongPrivacy = location.strongPrivacy
                            )
                            if (Sdk.isFavoriteExit(location)) {
                                // under the name, past the row's circle
                                Text(
                                    favoriteExitNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }

                // then the network peers
                networkPeersSection(
                    peers = networkPeers,
                    selectedLocation = selectedLocation,
                    getLocationColor = getLocationColor,
                    onConnectPeer = onConnectPeer,
                    peerMenu = locationMenu,
                )

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

                    itemsIndexed(
                        bestSearchMatches,
                        key = { index, location ->
                            indexedLazyListKey("best", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        ProviderRow(
                            location = locationDisplayName(location),
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
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
                        val selected = selectedLocation?.connectLocationId?.bestAvailable == true
                        ProviderRow(
                            location = stringResource(id = R.string.best_available_provider),
                            onClick = {
                                // passing null for connect location will connect to best available
                                onLocationSelect(null)
                            },
                            color = Red400,
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(null) },
                            onFocusChanged = onFocusChanged,
                            isStable = true,
                            isStrongPrivacy = false,
                            displayIcons = false
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

                    itemsIndexed(
                        connectCountries,
                        key = { index, location ->
                            indexedLazyListKey("country", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        ProviderRow(
                            location = locationDisplayName(location),
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
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

                    itemsIndexed(
                        regions,
                        key = { index, location ->
                            indexedLazyListKey("region", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        ProviderRow(
                            location = locationDisplayName(location),
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
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

                    itemsIndexed(
                        cities,
                        key = { index, location ->
                            indexedLazyListKey("city", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        ProviderRow(
                            location = locationDisplayName(location),
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
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

                    itemsIndexed(
                        devices,
                        key = { index, location ->
                            indexedLazyListKey("device", index, location.connectLocationId)
                        }
                    ) { _, location ->
                        val selected = selectedLocation?.connectLocationId == location.connectLocationId
                        ProviderRow(
                            location = locationDisplayName(location),
                            providerCount = location.providerCount,
                            onClick = {
                                onLocationSelect(location)
                            },
                            isSelected = selected,
                            contextMenuItems = locationMenu(selected) { onLocationSelect(location) } + favoriteMenu(location),
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
