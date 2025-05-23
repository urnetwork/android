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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.Green500
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.LeaderboardEarner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    leaderboardViewModel: LeaderboardViewModel = hiltViewModel(),
) {

    val refreshState = rememberPullToRefreshState()

    Scaffold() { innerPadding ->

        PullToRefreshBox(
            isRefreshing = leaderboardViewModel.isLoading,
            state = refreshState,
            onRefresh = {
                leaderboardViewModel.fetchLeaderboardData()
                        },
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                // .padding(16.dp),
            ) {

                item {
                    LeaderboardHeader(
                        networkRank = leaderboardViewModel.networkRank,
                        netProvidedFormatted = leaderboardViewModel.netProvidedFormatted
                    )
                }

                itemsIndexed(leaderboardViewModel.leaderboardEntries.value) { index, entry ->
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

@Composable
private fun LeaderboardHeader(
    networkRank: Int,
    netProvidedFormatted: String
) {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            "Leaderboard",
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
                    "Current Ranking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "#${networkRank}",
                        style = HeadingLargeCondensed
                    )

                    Spacer(modifier = Modifier.width(2.dp))
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Net Provided",
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

                    // Spacer(modifier = Modifier.width(2.dp))
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
                        "Display network on leaderboard",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )

                    URSwitch(
                        checked = true,
                        toggle = {
//                            toggleAllowForeground()
//                            application?.updateVpnService()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))


            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "The leaderboard is the sum of the last 4 payments. It is updated each payment cycle.",
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
                modifier = Modifier.width(36.dp),
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