package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseLocationsScreen(
    locationsListViewModel: LocationsListViewModel,
    connectViewModel: ConnectViewModel,
    navController: NavController
) {
    val fetchLocationsState by remember { locationsListViewModel.filterLocationsState }.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Browse Locations",
                        style = TopBarTitleTextStyle
                    )
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
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        ) {
            BrowseLocations(
                connectCountries = locationsListViewModel.connectCountries,
                promotedLocations = locationsListViewModel.promotedLocations,
                cities = locationsListViewModel.cities,
                regions = locationsListViewModel.regions,
                devices = locationsListViewModel.devices,
                bestSearchMatches = locationsListViewModel.bestSearchMatches,
                selectedLocation = connectViewModel.selectedLocation,
                keyboardController = null,
                currentSearchQuery = locationsListViewModel.currentSearchQuery,
                setSearchQueryTextFieldValue = locationsListViewModel.setSearchQueryTextFieldValue,
                searchQueryTextFieldValue = locationsListViewModel.searchQueryTextFieldValue,
                fetchLocationsState = fetchLocationsState,
                connect = {
                    connectViewModel.connect(it)
                    navController.popBackStack()
                          },
                getLocationColor = locationsListViewModel.getLocationColor,
                filterLocations = locationsListViewModel.filterLocations
            )
        }
    }
}