package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLearnMoreText
import com.bringyour.network.ui.stats.ThroughputViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.stats.ProviderStatsSection
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.utils.Ss58
import com.bringyour.sdk.ReliabilityWindow

const val UR_XYZ_URL = "https://ur.xyz"

/**
 * The Earnings screen: points first, always. The UR protocol layer (wallet,
 * unclaimed SN25α, claim, Top 200) appears only once a Bittensor wallet is connected.
 */
@Composable
fun EarningsScreen(
    navController: NavHostController,
    earningsViewModel: EarningsViewModel,
    overlayViewModel: OverlayViewModel,
    totalAccountPoints: Double,
    payoutPoints: Double,
    referralPoints: Double,
    multiplierPoints: Double,
    reliabilityPoints: Double,
    fetchAccountPoints: () -> Unit,
    reliabilityWindow: ReliabilityWindow?,
) {
    val context = LocalContext.current

    val wallet by earningsViewModel.wallet.collectAsState()
    val walletLoaded by earningsViewModel.walletLoaded.collectAsState()
    val connectState by earningsViewModel.connectState.collectAsState()
    val claims by earningsViewModel.claims.collectAsState()
    val totalClaimableRao by earningsViewModel.totalClaimableRao.collectAsState()
    val claimsError by earningsViewModel.claimsError.collectAsState()
    val epochs by earningsViewModel.epochs.collectAsState()
    val epochsLoaded by earningsViewModel.epochsLoaded.collectAsState()
    val head by earningsViewModel.head.collectAsState()
    val isSeekerHolder by earningsViewModel.isSeekerHolder.collectAsState()
    val claimDialog by earningsViewModel.claimDialog.collectAsState()
    val manualValidation by earningsViewModel.manualValidation.collectAsState()

    LaunchedEffect(Unit) {
        fetchAccountPoints()
    }

    LifecycleResumeEffect(Unit) {
        earningsViewModel.onScreenResumed()
        onPauseOrDispose {}
    }

    EarningsScreenContent(
        navController = navController,
        isRefreshing = earningsViewModel.isRefreshing,
        refresh = {
            fetchAccountPoints()
            earningsViewModel.refresh()
        },
        totalAccountPoints = totalAccountPoints,
        payoutPoints = payoutPoints,
        referralPoints = referralPoints,
        multiplierPoints = multiplierPoints,
        reliabilityPoints = reliabilityPoints,
        isSeekerHolder = isSeekerHolder,
        protocolAvailable = earningsViewModel.protocolAvailable,
        wallet = wallet,
        walletLoaded = walletLoaded,
        connectState = connectState,
        onConnectWallet = { earningsViewModel.connectWithBridge(context) },
        onEnterManually = { earningsViewModel.openManualSheet() },
        onContinueLooksNew = { earningsViewModel.continueAfterLooksNew() },
        onDismissConnectState = { earningsViewModel.dismissConnectState() },
        claims = claims,
        totalClaimableRao = totalClaimableRao,
        claimsError = claimsError,
        onOpenClaim = { earningsViewModel.openClaimDialog() },
        epochs = epochs,
        epochsLoaded = epochsLoaded,
        head = head,
        reliabilityWindow = reliabilityWindow,
        formatAlpha = earningsViewModel::formatAlpha,
        formatShareBps = earningsViewModel::formatShareBps,
        shortSs58 = earningsViewModel::shortSs58,
    )

    ClaimDialog(
        state = claimDialog,
        onDismiss = { earningsViewModel.closeClaimDialog() },
        onClaim = { earningsViewModel.claimAll() },
        onRetry = { earningsViewModel.retryClaimDialog() },
        formatAlpha = earningsViewModel::formatAlpha,
        shortSs58 = earningsViewModel::shortSs58,
        explorerTxUrl = earningsViewModel::explorerTxUrl,
    )

    if (earningsViewModel.isPresentedManualSheet) {
        ConnectWalletSheet(
            address = earningsViewModel.manualAddress,
            onAddressChange = { earningsViewModel.updateManualAddress(it) },
            validation = manualValidation,
            onContinue = { earningsViewModel.continueManual(context) },
            onDismiss = { earningsViewModel.closeManualSheet() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreenContent(
    navController: NavHostController,
    isRefreshing: Boolean,
    refresh: () -> Unit,
    totalAccountPoints: Double,
    payoutPoints: Double,
    referralPoints: Double,
    multiplierPoints: Double,
    reliabilityPoints: Double,
    isSeekerHolder: Boolean,
    protocolAvailable: Boolean,
    wallet: SnWalletState?,
    walletLoaded: Boolean,
    connectState: WalletConnectState,
    onConnectWallet: () -> Unit,
    onEnterManually: () -> Unit,
    onContinueLooksNew: () -> Unit,
    onDismissConnectState: () -> Unit,
    claims: List<EpochClaim>,
    totalClaimableRao: Long,
    claimsError: String?,
    onOpenClaim: () -> Unit,
    epochs: List<AccountEpoch>,
    epochsLoaded: Boolean,
    head: SnHeadState?,
    reliabilityWindow: ReliabilityWindow?,
    formatAlpha: (Long) -> String,
    formatShareBps: (Long) -> String,
    shortSs58: (String) -> String,
) {
    val refreshState = rememberPullToRefreshState()
    val claimsByEpoch = claims.associateBy { it.epoch }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.earnings),
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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = refreshState,
            onRefresh = refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                PointsHeadline(
                    totalAccountPoints = totalAccountPoints,
                    payoutPoints = payoutPoints,
                    referralPoints = referralPoints,
                    reliabilityPoints = reliabilityPoints,
                    multiplierPoints = multiplierPoints,
                    isSeekerHolder = isSeekerHolder
                )

                Spacer(modifier = Modifier.height(16.dp))


                Spacer(modifier = Modifier.height(16.dp))

                WalletSection(
                    protocolAvailable = protocolAvailable,
                    wallet = wallet,
                    walletLoaded = walletLoaded,
                    connectState = connectState,
                    onConnectWallet = onConnectWallet,
                    onEnterManually = onEnterManually,
                    onContinueLooksNew = onContinueLooksNew,
                    onDismissConnectState = onDismissConnectState,
                    shortSs58 = shortSs58
                )

                if (wallet != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    UnclaimedTile(
                        totalClaimableRao = totalClaimableRao,
                        claimsError = claimsError,
                        onOpenClaim = onOpenClaim,
                        formatAlpha = formatAlpha
                    )
                }

                head?.let { h ->
                    if (h.bound) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Top200BoundRow(head = h)
                    } else if (h.eligible) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Top200Tile(head = h)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = TextFaint)
                Spacer(modifier = Modifier.height(16.dp))

                // provider statistics follow the provide mode: with providing off
                // the reliability chart hides and the section says so, the same
                // gate and message as the stats screen
                val throughputViewModel: ThroughputViewModel = hiltViewModel()
                if (throughputViewModel.providerStatsEnabled) {
                    NetworkReliability(reliabilityWindow = reliabilityWindow)

                    Spacer(modifier = Modifier.height(16.dp))
                }

                ProviderStatsSection(
                    navController = navController,
                    throughputViewModel = throughputViewModel
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = TextFaint)
                Spacer(modifier = Modifier.height(16.dp))

                EpochHistory(
                    epochs = epochs,
                    claimsByEpoch = claimsByEpoch,
                    showAlpha = wallet != null,
                    loaded = epochsLoaded,
                    formatAlpha = formatAlpha,
                    formatShareBps = formatShareBps
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun PointsHeadline(
    totalAccountPoints: Double,
    payoutPoints: Double,
    referralPoints: Double,
    reliabilityPoints: Double,
    multiplierPoints: Double,
    isSeekerHolder: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Text(
            EarningsFormat.points(totalAccountPoints),
            style = TextStyle(
                fontFamily = gravityCondensedFamily,
                fontWeight = FontWeight(900),
                fontSize = 56.sp,
                color = Color.White
            )
        )
        Text(
            stringResource(id = R.string.points_earned),
            modifier = Modifier.padding(top = 0.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = TextFaint)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PointsBreakdownColumn(label = stringResource(id = R.string.providing), points = payoutPoints)
            PointsBreakdownColumn(label = stringResource(id = R.string.referral), points = referralPoints)
            PointsBreakdownColumn(label = stringResource(id = R.string.reliability), points = reliabilityPoints)
        }

        if (isSeekerHolder) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TextFaint)
            Spacer(modifier = Modifier.height(12.dp))
            /**
             * The badge and its two lines take whatever width the value leaves;
             * the value itself never wraps. Without the weight the text column
             * filled the row and the "+76,072" was squeezed into a one-digit
             * column, one digit per line.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.point_multiplier),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.width(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(id = R.string.seeker_token_verified),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(id = R.string.seeker_points_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "+${EarningsFormat.points(multiplierPoints)}",
                    style = HeadingLargeCondensed,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun PointsBreakdownColumn(label: String, points: Double) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Text(EarningsFormat.points(points), style = HeadingLargeCondensed)
    }
}


@Composable
private fun WalletSection(
    protocolAvailable: Boolean,
    wallet: SnWalletState?,
    walletLoaded: Boolean,
    connectState: WalletConnectState,
    onConnectWallet: () -> Unit,
    onEnterManually: () -> Unit,
    onContinueLooksNew: () -> Unit,
    onDismissConnectState: () -> Unit,
    shortSs58: (String) -> String,
) {
    if (!walletLoaded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = TextMuted,
                trackColor = TextFaint,
                strokeWidth = 2.dp
            )
        }
        return
    }

    if (wallet != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(100)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.bittensor_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(id = R.string.bittensor_wallet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Text(
                        shortSs58(wallet.coldkeySs58),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(id = R.string.wallet_connected_to_protocol),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // plain text, then a "Learn more" link to the protocol site
        URLearnMoreText(
            text = stringResource(id = R.string.wallet_not_retroactive),
            linkText = stringResource(id = R.string.learn_more),
            url = UR_XYZ_URL,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (connectState) {
            is WalletConnectState.LooksNew -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        shortSs58(connectState.address),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.wallet_looks_new_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Amber
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(onClick = onContinueLooksNew) { buttonTextStyle ->
                        Text(stringResource(id = R.string.connect), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(onClick = onDismissConnectState, style = ButtonStyle.OUTLINE) { buttonTextStyle ->
                        Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
                    }
                }
            }
            else -> {
                URButton(
                    onClick = onConnectWallet,
                    enabled = protocolAvailable && !connectState.busy,
                    isProcessing = connectState.busy
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.connect_bittensor_wallet), style = buttonTextStyle)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(id = R.string.enter_address_manually),
                    modifier = Modifier
                        .clickable(enabled = protocolAvailable && !connectState.busy) { onEnterManually() }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (protocolAvailable) BlueMedium else TextMuted
                )

                val (status, color) = when (connectState) {
                    is WalletConnectState.Validating -> stringResource(id = R.string.checking_wallet_address) to TextMuted
                    WalletConnectState.InvalidAddress -> stringResource(id = R.string.invalid_ss58_address) to Red
                    is WalletConnectState.Blocked -> stringResource(id = R.string.wallet_blocked) to Red
                    is WalletConnectState.Failed ->
                        (connectState.detail ?: stringResource(id = R.string.chain_rpc_unreachable)) to Red
                    is WalletConnectState.Connected -> stringResource(id = R.string.wallet_connected_to_protocol) to Green
                    else -> null to TextMuted
                }
                if (status != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        status,
                        modifier = Modifier.clickable { onDismissConnectState() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun UnclaimedTile(
    totalClaimableRao: Long,
    claimsError: String?,
    onOpenClaim: () -> Unit,
    formatAlpha: (Long) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(id = R.string.unclaimed),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Text(
            formatAlpha(totalClaimableRao),
            style = TextStyle(
                fontFamily = gravityCondensedFamily,
                fontWeight = FontWeight(900),
                fontSize = 40.sp,
                color = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (claimsError != null) {
            Text(
                stringResource(id = R.string.chain_rpc_unreachable),
                style = MaterialTheme.typography.bodyMedium,
                color = Red
            )
        } else {
            Text(
                stringResource(id = R.string.claims_open_after_finalization),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        URButton(
            onClick = onOpenClaim,
            enabled = 0 < totalClaimableRao
        ) { buttonTextStyle ->
            Text(stringResource(id = R.string.claim), style = buttonTextStyle)
        }
    }
}

@Preview
@Composable
private fun EarningsScreenNoWalletPreview() {
    URNetworkTheme {
        EarningsScreenContent(
            navController = rememberNavController(),
            isRefreshing = false,
            refresh = {},
            totalAccountPoints = 12_480.0,
            payoutPoints = 9_120.0,
            referralPoints = 2_400.0,
            multiplierPoints = 0.0,
            reliabilityPoints = 960.0,
            isSeekerHolder = false,
            protocolAvailable = true,
            wallet = null,
            walletLoaded = true,
            connectState = WalletConnectState.Idle,
            onConnectWallet = {},
            onEnterManually = {},
            onContinueLooksNew = {},
            onDismissConnectState = {},
            claims = emptyList(),
            totalClaimableRao = 0,
            claimsError = null,
            onOpenClaim = {},
            epochs = emptyList(),
            epochsLoaded = true,
            head = null,
            reliabilityWindow = null,
            formatAlpha = { EarningsFormat.alpha(it) },
            formatShareBps = { EarningsFormat.shareBps(it) },
            shortSs58 = { Ss58.short(it) },
        )
    }
}

@Preview
@Composable
private fun EarningsScreenWalletPreview() {
    URNetworkTheme {
        EarningsScreenContent(
            navController = rememberNavController(),
            isRefreshing = false,
            refresh = {},
            totalAccountPoints = 12_480.0,
            payoutPoints = 9_120.0,
            referralPoints = 2_400.0,
            multiplierPoints = 0.0,
            reliabilityPoints = 960.0,
            isSeekerHolder = false,
            protocolAvailable = true,
            wallet = SnWalletState(SampleProtocolSource.SAMPLE_COLDKEY, null, 0),
            walletLoaded = true,
            connectState = WalletConnectState.Idle,
            onConnectWallet = {},
            onEnterManually = {},
            onContinueLooksNew = {},
            onDismissConnectState = {},
            claims = listOf(
                EpochClaim(1218, 71, 3_241_000_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
            ),
            totalClaimableRao = 3_241_000_000,
            claimsError = null,
            onOpenClaim = {},
            epochs = listOf(
                AccountEpoch(1218, 0, 0, 2_210.0, 71),
            ),
            epochsLoaded = true,
            head = SnHeadState(true, 812.0, 640.0, 143, 200, false, null, 0, 0, 1219, "server"),
            reliabilityWindow = null,
            formatAlpha = { EarningsFormat.alpha(it) },
            formatShareBps = { EarningsFormat.shareBps(it) },
            shortSs58 = { Ss58.short(it) },
        )
    }
}
