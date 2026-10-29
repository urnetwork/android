package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.sdk.ConnectLocation
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.components.tabletReadableColumn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrowseLocations(
    selectedLocation: ConnectLocation?,
    connectCountries: List<ConnectLocation>,
    cities: List<ConnectLocation>,
    regions: List<ConnectLocation>,
    devices: List<ConnectLocation>,
    bestSearchMatches: List<ConnectLocation>,
    getLocationColor: (String) -> Color,
    filterLocations: (String) -> Unit,
    connect: (ConnectLocation?) -> Unit,
    fetchLocationsState: FilterLocationsState,
    searchQueryTextFieldValue: TextFieldValue,
    setSearchQueryTextFieldValue: (TextFieldValue) -> Unit,
    currentSearchQuery: String,
    keyboardController: SoftwareKeyboardController?,
    lazyListState: LazyListState,
    refreshLocations: () -> Unit,
    networkPeers: List<com.bringyour.network.ui.stats.NetworkPeerUi> = listOf(),
    onConnectPeer: (com.bringyour.network.ui.stats.NetworkPeerUi) -> Unit = {},
    // a connection requested, and Disconnect, for the context menu of the row
    // it is on (LocationsList)
    connectRequested: Boolean = false,
    disconnect: () -> Unit = {},
) {

    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // opened from the keyboard, typing goes straight to the search, and Down
    // from there reaches the locations (Keyboard_Navigation); a tap opens it
    // without raising the on-screen keyboard, as before
    val searchFocus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) {
            runCatching { searchFocus.requestFocus() }
        }
    }

    Column(
        Modifier
            // a wide window centers the search and the list in the readable column
            .tabletReadableColumn()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            URSearchInput(
                value = searchQueryTextFieldValue,
                onValueChange = { query ->
                    if (query.text != searchQueryTextFieldValue.text) {
                        setSearchQueryTextFieldValue(query)

                        debounceJob?.cancel()
                        debounceJob = scope.launch {
                            delay(250L)
                            filterLocations(query.text)
                        }
                    }
                },
                onSearch = {
                    filterLocations(searchQueryTextFieldValue.text)
                    keyboardController?.hide()
                },
                placeholder = stringResource(id = R.string.search_placeholder),
                keyboardController = keyboardController,
                onClear = {
                    setSearchQueryTextFieldValue(TextFieldValue(""))
                    filterLocations("")
                    keyboardController?.hide()
                },
                focusRequester = searchFocus,
            )
        }

        // the search field keeps 6 dp of its own below
        Spacer(modifier = Modifier.height(18.dp))


        if (fetchLocationsState == FilterLocationsState.Error) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FetchLocationsError(
                    onRefresh = {
                        filterLocations(currentSearchQuery)
                    }
                )
            }
        } else {
            LocationsList(
                onLocationSelect = { location ->
                    connect(location)
                },
                connectCountries = connectCountries,
                cities = cities,
                regions = regions,
                bestSearchMatches = bestSearchMatches,
                getLocationColor = getLocationColor,
                selectedLocation = selectedLocation,
                devices = devices,
                onRefresh = {
                    refreshLocations()
                },
                searchQuery = currentSearchQuery,
                listState = lazyListState,
                isRefreshing = fetchLocationsState == FilterLocationsState.Loading,
                networkPeers = networkPeers,
                onConnectPeer = onConnectPeer,
                connectRequested = connectRequested,
                onDisconnect = disconnect,
            )
        }
    }
}

/**
 * The location list's scroll position, kept above the screen and the dialog
 * that show the list, so a fold or a resize that turns one into the other
 * carries the position across (apple/DUO.md). Saved with the screen.
 */
@Stable
class LocationListPosition(index: Int = 0, offset: Int = 0) {
    var index by mutableIntStateOf(index)
    var offset by mutableIntStateOf(offset)

    companion object {
        val Saver: Saver<LocationListPosition, List<Int>> = Saver(
            save = { listOf(it.index, it.offset) },
            restore = { LocationListPosition(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }) },
        )
    }
}

@Composable
fun rememberLocationListPosition(): LocationListPosition =
    rememberSaveable(saver = LocationListPosition.Saver) { LocationListPosition() }

/** A list state that starts at [position] and keeps it up to date as the list scrolls. */
@Composable
fun rememberLocationListState(position: LocationListPosition): LazyListState {
    val state = rememberLazyListState(position.index, position.offset)
    LaunchedEffect(state, position) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                position.index = index
                position.offset = offset
            }
    }
    return state
}
