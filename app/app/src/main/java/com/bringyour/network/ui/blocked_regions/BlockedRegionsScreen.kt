package com.bringyour.network.ui.blocked_regions

import com.bringyour.network.ui.components.tabletReadableColumn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.RowRemoveControl
import com.bringyour.network.ui.components.blockedLocationRemoveControls
import com.bringyour.network.ui.components.SwipeToRevealRow
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val notice by viewModel.notice.collectAsState()

    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        val message = when (shown) {
            BlockedRegionsNotice.LoadFailed ->
                context.getString(R.string.blocked_locations_load_failed)
            is BlockedRegionsNotice.BlockFailed ->
                context.getString(R.string.blocked_location_block_failed, shown.locationName)
            is BlockedRegionsNotice.UnblockFailed ->
                context.getString(R.string.blocked_location_unblock_failed, shown.locationName)
        }
        viewModel.clearNotice()
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
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
                modifier = Modifier.tabletReadableColumn().fillMaxSize(),
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

                itemsIndexed(
                    blockedLocations,
                    key = { index, location ->
                        indexedLazyListKey("blocked-location", index, location.locationId)
                    }
                ) { _, location ->
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
                    modifier = Modifier.weight(1f),
                )
                if (RowRemoveControl.Button in blockedLocationRemoveControls) {
                    // swipe-to-reveal alone is unreachable for TalkBack, Switch
                    // Access and keyboard users, and invisible to anyone who
                    // does not know to swipe
                    IconButton(onClick = { onRemove(blockedLocation.locationId) }) {
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

    if (RowRemoveControl.Swipe in blockedLocationRemoveControls) {
        SwipeToRevealRow(
            onAction = { onRemove(blockedLocation.locationId) },
            modifier = rowModifier,
            content = content,
        )
    } else {
        Box(modifier = rowModifier) {
            content()
        }
    }
}
