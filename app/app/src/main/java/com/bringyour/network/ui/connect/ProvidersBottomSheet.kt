package com.bringyour.network.ui.connect

// TODO: deprecate this

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.R
import com.bringyour.sdk.ConnectLocation
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProvidersBottomSheet(
    selectedLocation: ConnectLocation?,
    connect: (ConnectLocation?) -> Unit,
    locationsViewModel: LocationsListViewModel,
    setIsPresented: (Boolean) -> Unit,
) {

    val fetchLocationsState by remember { locationsViewModel.filterLocationsState }.collectAsState()

    ProvidersBottomSheet(
        selectedLocation = selectedLocation,
        connectCountries = locationsViewModel.connectCountries,
        promotedLocations = locationsViewModel.promotedLocations,
        cities = locationsViewModel.cities,
        regions = locationsViewModel.regions,
        devices = locationsViewModel.devices,
        bestSearchMatches = locationsViewModel.bestSearchMatches,
        getLocationColor = locationsViewModel.getLocationColor,
        filterLocations = locationsViewModel.filterLocations,
        fetchLocationsState = fetchLocationsState,
        searchQueryTextFieldValue = locationsViewModel.searchQueryTextFieldValue,
        setSearchQueryTextFieldValue = locationsViewModel.setSearchQueryTextFieldValue,
        currentSearchQuery = locationsViewModel.currentSearchQuery,
        connect = connect,
        refreshLocations = locationsViewModel.refreshLocations,
        setIsPresented = setIsPresented
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    selectedLocation: ConnectLocation?,
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
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
    refreshLocations: () -> Unit,
    setIsPresented: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val lazyListState = rememberLazyListState()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    Dialog(
        onDismissRequest = { setIsPresented(false) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {

            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Select Provider", style = TopBarTitleTextStyle) },
                        navigationIcon = {
                            IconButton(onClick = { setIsPresented(false) }) {
                                Icon(Icons.Default.Close, "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Black,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                },
                containerColor = Black,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // .systemBarsPadding()
                        .padding(innerPadding)
                ) {


                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {

                        Column(
                            Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Row(
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
                                    },
                                    placeholder = stringResource(id = R.string.search_placeholder),
                                    keyboardController = keyboardController,
                                    onClear = {
                                        setSearchQueryTextFieldValue(TextFieldValue(""))
                                        filterLocations("")
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                        }



                        when (fetchLocationsState) {
                            FilterLocationsState.Loaded,
                            FilterLocationsState.Loading -> {

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {

                                    LocationsList(
                                        onLocationSelect = { location ->
                                            connect(location)
                                            setIsPresented(false)
                                        },
                                        promotedLocations = promotedLocations,
                                        connectCountries = connectCountries,
                                        cities = cities,
                                        regions = regions,
                                        bestSearchMatches = bestSearchMatches,
                                        getLocationColor = getLocationColor,
                                        selectedLocation = selectedLocation,
                                        devices = devices,
                                        onRefresh = {
                                            filterLocations(currentSearchQuery)
                                        },
                                        searchQuery = currentSearchQuery,
                                        listState = lazyListState,
                                        isRefreshing = true
                                    )

                                    if (fetchLocationsState == FilterLocationsState.Loading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Black.copy(alpha = 0.5f))
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.width(24.dp)
                                                    .align(Alignment.Center),
                                            )
                                        }
                                    }

                                }

                            }

                            FilterLocationsState.Error -> {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    FetchLocationsError(
                                        onRefresh = {
                                            filterLocations(currentSearchQuery)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    }

}

@Preview
@Composable
private fun PreviewBottomSheet() {

    URNetworkTheme {
        ProvidersBottomSheet(
            connect = {},
            selectedLocation = null,
            connectCountries = listOf<ConnectLocation>(),
            promotedLocations = listOf<ConnectLocation>(),
            cities = listOf<ConnectLocation>(),
            regions = listOf<ConnectLocation>(),
            devices = listOf<ConnectLocation>(),
            bestSearchMatches = listOf<ConnectLocation>(),
            getLocationColor = { _ ->
                BlueMedium
            },
            filterLocations = { _ -> },
            fetchLocationsState = FilterLocationsState.Loaded,
            searchQueryTextFieldValue = TextFieldValue(""),
            setSearchQueryTextFieldValue = {},
            currentSearchQuery = "",
            refreshLocations = {},
            setIsPresented = {}
        )
    }
}

@Preview
@Composable
private fun PreviewBottomSheetExpanded() {

    URNetworkTheme {
        ProvidersBottomSheet(
            connect = {},
            selectedLocation = null,
            connectCountries = listOf<ConnectLocation>(),
            promotedLocations = listOf<ConnectLocation>(),
            cities = listOf<ConnectLocation>(),
            regions = listOf<ConnectLocation>(),
            devices = listOf<ConnectLocation>(),
            bestSearchMatches = listOf<ConnectLocation>(),
            getLocationColor = { _ ->
                BlueMedium
            },
            filterLocations = { _ -> },
            fetchLocationsState = FilterLocationsState.Loaded,
            searchQueryTextFieldValue = TextFieldValue(""),
            setSearchQueryTextFieldValue = {},
            currentSearchQuery = "",
            refreshLocations = {},
            setIsPresented = {}
        )
    }
}
