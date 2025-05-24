package com.bringyour.network.ui.leaderboard

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.Green500
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.LeaderboardEarner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    leaderboardViewModel: LeaderboardViewModel = hiltViewModel(),
) {

    val refreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val leaderboardEntries = leaderboardViewModel.leaderboardEntries.collectAsState()


    LaunchedEffect(leaderboardViewModel.displayErrorMsg) {
        if (leaderboardViewModel.displayErrorMsg) {
            snackbarHostState.showSnackbar(
                message = "Something went wrong",
                withDismissAction = true,
            )
            leaderboardViewModel.setDisplayErrorMsg(false)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->

        if (leaderboardViewModel.isInitializing) {

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }

        } else {
            /**
             * Data initialized
             */

            PullToRefreshBox(
                isRefreshing = leaderboardViewModel.isLoading,
                state = refreshState,
                onRefresh = {
                    leaderboardViewModel.fetchLeaderboardData()
                },
                modifier = Modifier.padding(innerPadding)
            ) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                    item {
                        LeaderboardHeader(
                            networkRank = leaderboardViewModel.networkRank,
                            netProvidedFormatted = leaderboardViewModel.netProvidedFormatted,
                            networkRankingPublic = leaderboardViewModel.isNetworkRankingPublic,
                            toggleNetworkRankingPublic = leaderboardViewModel::toggleNetworkRankingVisibility
                        )
                    }

                    itemsIndexed(leaderboardEntries.value) { index, entry ->
                        Column {
                            HorizontalDivider()
                            LeaderboardEntry(
                                entry,
                                rank = index + 1,
                                netProvidedFormatted = leaderboardViewModel.formatDataProvided(entry.netMiBCount),
                                isNetworkRow = leaderboardViewModel.networkId == entry.networkId.toString()
                            )
                        }
                    }
                    item {
                        HorizontalDivider()
                    }

                }
            }

        }

    }

}

@Composable
private fun LeaderboardHeader(
    networkRank: Int,
    netProvidedFormatted: String,
    networkRankingPublic: Boolean,
    toggleNetworkRankingPublic: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            stringResource(id = R.string.leaderboard),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .background(
                    color = MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    bottom = 10.dp, // hacky due to line-height issue
                    end = 16.dp
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(id = R.string.current_ranking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        if (networkRank > 0) "#${networkRank}" else "-",
                        style = HeadingLargeCondensed
                    )

                    Spacer(modifier = Modifier.width(2.dp))
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.net_provided),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        netProvidedFormatted,
                        style = HeadingLargeCondensed,
                    )

                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.display_network_on_leaderboard),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )

                    URSwitch(
                        checked = networkRankingPublic,
                        toggle = {

                            scope.launch {
                                toggleNetworkRankingPublic()
                            }

                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))


            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(id = R.string.leaderboard_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

    }
}

@Composable
private fun LeaderboardEntry(
    row: LeaderboardEarner,
    rank: Int,
    netProvidedFormatted: String,
    isNetworkRow: Boolean
) {

    val isPrivate = !row.isPublic

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "#${rank}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNetworkRow) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (isNetworkRow) Green500 else TextMuted,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isPrivate) "Private Network" else row.networkName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNetworkRow) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isNetworkRow -> Green500
                    isPrivate -> TextMuted
                    else -> Color.White
                }
            )
        }

        Text(
            netProvidedFormatted,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isNetworkRow) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (isNetworkRow) Green500 else Color.White
        )

    }

}