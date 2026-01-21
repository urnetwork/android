package com.bringyour.network.ui.balance_codes

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.redeemTransferBalanceCode.RedeemTransferBalanceCodeSheet
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.RedeemedBalanceCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceCodesScreen(
    navController: NavController,
    viewModel: BalanceCodesViewModel = hiltViewModel<BalanceCodesViewModel>()
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val balanceCodes by viewModel.balanceCodes.collectAsState()

    val context = LocalContext.current

    val errorEvents = viewModel.errorEvents.collectAsState(initial = null)
    errorEvents.value?.let { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                         stringResource(id = R.string.balance_codes_title),
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
                            contentDescription = stringResource(id = R.string.redeem_balance_code)
                        )
                    }
                },
            )
        },
        containerColor = Black
    ) { padding ->

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            BalanceCodesTable(
                balanceCodes = balanceCodes,
                onRefresh = {
                    viewModel.fetchNetworkBalanceCodes()
                },
                isRefreshing = viewModel.isFetchingBalanceCodes.collectAsState().value
            )

        }

        if (viewModel.displayBottonSheet.collectAsState().value) {
            RedeemTransferBalanceCodeSheet(
                sheetState = sheetState,
                setIsPresenting = {
                    viewModel.setDisplayBottomSheet(it)
                },
                onSuccess = {
                    viewModel.fetchNetworkBalanceCodes()
                }
            )
        }

    }

}

@Composable
private fun BalanceCodesTable(
    balanceCodes: List<RedeemedBalanceCode>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {

    val listState = rememberLazyListState()

    PullToRefreshBox(
        onRefresh = onRefresh,
        isRefreshing = isRefreshing
    ) {

        if (balanceCodes.isEmpty()) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Text(
                        stringResource(id = R.string.no_redeemed_balance_codes_found),
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
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            stringResource(id = R.string.balance_code),
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Text(
                            stringResource(id = R.string.data),
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Text(
                            stringResource(id = R.string.redeemed),
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Text(
                            stringResource(id = R.string.expires),
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                items(balanceCodes, key = {it.balanceCodeId.idStr}) { balanceCode ->
                    BalanceCodeListItem(
                        balanceCode = balanceCode,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun BalanceCodeListItem(
    balanceCode: RedeemedBalanceCode,
) {

    val maskSecret: (String) -> String = { secret ->
        if (secret.length <= 6) secret
        else "${secret.take(3)}...${secret.takeLast(3)}"
    }

    val formatBytes: (Long) -> String = { bytes ->
        val oneTiB = 1024L * 1024 * 1024 * 1024
        val oneGiB = 1024L * 1024 * 1024
        when {
            bytes >= oneTiB -> String.format("%.2f TiB", bytes.toDouble() / oneTiB)
            else -> String.format("%.2f GiB", bytes.toDouble() / oneGiB)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {

        Text(
            maskSecret(balanceCode.secret),
            modifier = Modifier.weight(1f)
        )

        Text(
            "+${formatBytes(balanceCode.balanceByteCount)}",
            modifier = Modifier.weight(1f)
        )

        Text(
            balanceCode.redeemTime.format("Jan 2, 2006"),
            modifier = Modifier.weight(1f)
        )

        Text(
            balanceCode.endTime.format("Jan 2, 2006"),
            modifier = Modifier.weight(1f)
        )

    }

}