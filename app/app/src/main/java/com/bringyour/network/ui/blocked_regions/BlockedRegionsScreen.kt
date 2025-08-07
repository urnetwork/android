package com.bringyour.network.ui.blocked_regions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.BlockedLocation
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.Id
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedRegionsScreen(
    countries: List<ConnectLocation>,
    navController: NavController,
    getLocationColor: (String) -> Color,
    viewModel: BlockedRegionsViewModel = hiltViewModel()
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.blocked_locations),
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
                actions = {
                    IconButton(onClick = {
                        viewModel.setDisplayBottomSheet(true)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.plus_icon),
                            contentDescription = stringResource(id = R.string.add_blocked_location),
                        )
                    }
                },
            )
        },
        containerColor = Black
    ) { padding ->

        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        ) {

            BlockedRegionsScreen(
                blockedLocations = viewModel.blockedRegions.collectAsState().value,
                onRefresh = {
                    viewModel.fetchBlockedRegions()
                },
                isRefreshing = viewModel.isFetchingLocations.collectAsState().value,
                remove = viewModel.unblockLocation,
                getLocationColor = getLocationColor
            )
        }

        if (viewModel.displayBottonSheet.collectAsState().value) {
            AddBlockedLocationSheet(
                dismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            viewModel.setDisplayBottomSheet(false)
                        }
                    }
                },
                sheetState = sheetState,
                countries = countries,
                onSelect = {
                    viewModel.blockRegion(
                        it.connectLocationId.locationId,
                        it.name,
                        it.countryCode
                    )
                },
                getLocationColor = getLocationColor
            )
        }

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedRegionsScreen(
    blockedLocations: List<BlockedLocation>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    remove: (Id) -> Unit,
    getLocationColor: (String) -> Color,
) {

    val listState = rememberLazyListState()

    PullToRefreshBox(
        onRefresh = onRefresh,
        isRefreshing = isRefreshing
    ) {

        if (blockedLocations.isEmpty()) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Text(
                        stringResource(id = R.string.no_blocked_locations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                }
            }

        } else {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = listState
            ) {

                items(blockedLocations, key = {it.locationId.idStr}) { location ->
                    BlockedRegionListItem(
                        blockedLocation = location,
                        onRemove = { id ->
                            remove(id)
                        },
                        getLocationColor = getLocationColor,
                        modifier = Modifier.animateItem()
                    )
                }
            }

        }

    }
}

@Composable
fun BlockedRegionListItem(
    blockedLocation: BlockedLocation,
    onRemove: (Id) -> Unit,
    getLocationColor: (String) -> Color,
    modifier: Modifier = Modifier,
) {

    val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onRemove(blockedLocation.locationId)
            // Reset item when toggling done status
            it != SwipeToDismissBoxValue.StartToEnd
        }
    )

    SwipeToDismissBox (
        state = swipeToDismissBoxState,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        backgroundContent = {
            when (swipeToDismissBoxState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {}
                SwipeToDismissBoxValue.EndToStart -> {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red)
                            .wrapContentSize(Alignment.CenterEnd)
                            .padding(12.dp),
                        tint = Color.White
                    )
                }
                SwipeToDismissBoxValue.Settled -> {}
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleImage(
                    size = 40.dp,
                    imageResourceId = null,
                    backgroundColor = getLocationColor(blockedLocation.countryCode),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    blockedLocation.locationName,
                    style = MaterialTheme.typography.bodyLarge,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}