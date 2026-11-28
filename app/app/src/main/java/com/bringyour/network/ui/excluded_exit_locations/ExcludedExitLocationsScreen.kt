package com.bringyour.network.ui.excluded_exit_locations

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.RowRemoveControl
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.components.excludedExitLocationRemoveControls
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.connect.NoLocationsFound
import com.bringyour.network.ui.connect.locationDisplayName
import com.bringyour.network.ui.connect.locationKey
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.ConnectLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Settings > Excluded exit locations, modeled on the blocked locations
 * screen: the places, such as a US state, that best available, collections
 * and a pick containing them avoid. What they mean is said at the top; then a
 * search of countries, regions and cities, as the location list searches,
 * with Exclude on each; without a search, the excluded places, each with its
 * remove. Every change goes to the connect view model
 * (excludedExitLocationsChanged), so a connection the change affects connects
 * again. The sdk keeps the list and stamps it on every connect.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludedExitLocationsScreen(
    navController: NavController,
    getLocationColor: (String) -> Color,
    // the connect view model's: a connection the change affects connects again
    excludedExitLocationsChanged: () -> Unit,
    viewModel: ExcludedExitLocationsViewModel = hiltViewModel(),
) {

    val excludedLocations by viewModel.excludedLocations.collectAsState()
    val excludedKeys by viewModel.excludedKeys.collectAsState()
    val search by viewModel.search.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val searching = viewModel.currentSearchQuery.isNotEmpty()
    val exclude: (ConnectLocation) -> Unit = { location ->
        viewModel.exclude(location, excludedExitLocationsChanged)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.excluded_exit_locations),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .tabletReadableColumn()
                .fillMaxSize(),
        ) {

            item(key = "excluded-exit-locations-search") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.excluded_exit_locations_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    URSearchInput(
                        value = viewModel.searchQueryTextFieldValue,
                        onValueChange = { query ->
                            if (query.text != viewModel.searchQueryTextFieldValue.text) {
                                viewModel.setSearchQueryTextFieldValue(query)

                                debounceJob?.cancel()
                                debounceJob = scope.launch {
                                    delay(250L)
                                    viewModel.filterLocations(query.text)
                                }
                            }
                        },
                        onSearch = {
                            viewModel.filterLocations(viewModel.searchQueryTextFieldValue.text)
                            keyboardController?.hide()
                        },
                        placeholder = stringResource(id = R.string.search_exit_locations_placeholder),
                        keyboardController = keyboardController,
                        onClear = {
                            viewModel.setSearchQueryTextFieldValue(TextFieldValue(""))
                            viewModel.filterLocations("")
                            keyboardController?.hide()
                        },
                    )

                    // the search field keeps 6 dp of its own below
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            if (searching) {
                exclusionSearchSection(
                    headingId = R.string.countries,
                    keyPrefix = "exclude-country",
                    locations = search.countries,
                    excludedKeys = excludedKeys,
                    onExclude = exclude,
                    getLocationColor = getLocationColor,
                )
                exclusionSearchSection(
                    headingId = R.string.regions,
                    keyPrefix = "exclude-region",
                    locations = search.regions,
                    excludedKeys = excludedKeys,
                    onExclude = exclude,
                    getLocationColor = getLocationColor,
                )
                exclusionSearchSection(
                    headingId = R.string.cities,
                    keyPrefix = "exclude-city",
                    locations = search.cities,
                    excludedKeys = excludedKeys,
                    onExclude = exclude,
                    getLocationColor = getLocationColor,
                )
                if (search.loaded && search.countries.isEmpty() && search.regions.isEmpty() && search.cities.isEmpty()) {
                    item(key = "exclude-none-found") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            NoLocationsFound()
                        }
                    }
                }
            } else if (excludedLocations.isEmpty()) {
                item(key = "excluded-exit-locations-empty") {
                    Text(
                        stringResource(id = R.string.no_excluded_exit_locations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp)
                    )
                }
            } else {
                itemsIndexed(
                    excludedLocations,
                    key = { index, location ->
                        indexedLazyListKey("excluded-exit-location", index, location.connectLocationId)
                    }
                ) { _, location ->
                    ExcludedExitLocationListItem(
                        location = location,
                        onRemove = { viewModel.remove(location, excludedExitLocationsChanged) },
                        getLocationColor = getLocationColor,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

// one kind of place the search found, under its heading as the location list
// shows it: Exclude on each, or Excluded once it is
private fun LazyListScope.exclusionSearchSection(
    @StringRes headingId: Int,
    keyPrefix: String,
    locations: List<ConnectLocation>,
    excludedKeys: Set<String>,
    onExclude: (ConnectLocation) -> Unit,
    getLocationColor: (String) -> Color,
) {
    if (locations.isEmpty()) {
        return
    }

    item(key = "$keyPrefix-heading") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(stringResource(id = headingId))
        }
    }

    itemsIndexed(
        locations,
        key = { index, location ->
            indexedLazyListKey(keyPrefix, index, location.connectLocationId)
        }
    ) { _, location ->
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExclusionPlace(
                    location = location,
                    getLocationColor = getLocationColor,
                    modifier = Modifier.weight(1f),
                )
                if (locationKey(location) in excludedKeys) {
                    Text(
                        stringResource(id = R.string.excluded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    TextButton(onClick = { onExclude(location) }) {
                        Text(
                            stringResource(id = R.string.exclude_exit_location),
                            color = Pink
                        )
                    }
                }
            }

            HorizontalDivider()
        }
    }
}

/** An excluded place with its remove, as a blocked location's row (BlockedRegionListItem). */
@Composable
private fun ExcludedExitLocationListItem(
    location: ConnectLocation,
    onRemove: () -> Unit,
    getLocationColor: (String) -> Color,
    modifier: Modifier = Modifier,
) {

    val rowModifier = modifier
        .fillMaxWidth()
        .height(64.dp)
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExclusionPlace(
                    location = location,
                    getLocationColor = getLocationColor,
                    modifier = Modifier.weight(1f),
                )
                if (RowRemoveControl.Button in excludedExitLocationRemoveControls) {
                    // swipe-to-reveal alone is unreachable for TalkBack, Switch
                    // Access and keyboard users
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(id = R.string.remove),
                            tint = TextMuted,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }

    if (RowRemoveControl.Swipe in excludedExitLocationRemoveControls) {
        SwipeToRevealRow(
            onAction = onRemove,
            modifier = rowModifier,
            content = content,
        )
    } else {
        Box(modifier = rowModifier) {
            content()
        }
    }
}

/**
 * A place's circle, its name, and for a region or a city where it lies, so a
 * place is told from others of its name (Georgia the country and the state).
 */
@Composable
private fun ExclusionPlace(
    location: ConnectLocation,
    getLocationColor: (String) -> Color,
    modifier: Modifier = Modifier,
) {
    val within = listOf(location.region, location.country)
        .filter { !it.isNullOrEmpty() && it != location.name }
        .joinToString(", ")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleImage(
            size = 40.dp,
            imageResourceId = null,
            backgroundColor = getLocationColor(
                if (location.countryCode.isNullOrEmpty()) location.connectLocationId.toString()
                else location.countryCode
            ),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                locationDisplayName(location),
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            if (within.isNotEmpty()) {
                Text(
                    within,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}
