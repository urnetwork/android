package com.bringyour.network.ui.wallet

import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.SectionLoadError
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLearnMoreText
import com.bringyour.network.ui.login.launchBittensorBridge
import com.bringyour.network.ui.stats.ThroughputViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.stats.ProviderStatsSection
import com.bringyour.network.ui.stats.ProviderStatusViewModel
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
import com.bringyour.sdk.Sdk
import java.time.ZoneId

const val UR_XYZ_URL = "https://ur.xyz"

// the SN payout line at the foot of the points card and its two actions
const val EARNINGS_SN_PAYOUT_LINE_TAG = "earnings-sn-payout-line"
const val EARNINGS_SN_PAYOUT_CLAIM_TAG = "earnings-sn-payout-claim"
const val EARNINGS_SET_COLDKEY_TAG = "earnings-set-coldkey"
// the wallet card while the payout key on this device stands in for a wallet
const val EARNINGS_WALLET_LOCAL_KEY_TAG = "earnings-wallet-local-key"

/**
 * The Earnings screen: points first, always. The UR protocol layer (wallet,
 * unclaimed SN25α, settlement v2, claim, Top 200) appears only once a Bittensor
 * wallet is connected; the payout consent with or without one.
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
    accountPointsFailed: Boolean = false,
    // the Top 200 tile's "See the Protocol leaderboard"
    onSeeProtocolBoard: () -> Unit = {},
    // a guest gets no payout key, only the wallet card
    isGuest: Boolean = false,
) {

    val wallet by earningsViewModel.wallet.collectAsState()
    val walletLoaded by earningsViewModel.walletLoaded.collectAsState()
    val walletFailed by earningsViewModel.walletFailed.collectAsState()
    val connectState by earningsViewModel.connectState.collectAsState()
    val claims by earningsViewModel.claims.collectAsState()
    val totalClaimableRao by earningsViewModel.totalClaimableRao.collectAsState()
    val claimsError by earningsViewModel.claimsError.collectAsState()
    val schedule by earningsViewModel.schedule.collectAsState()
    val epochs by earningsViewModel.epochs.collectAsState()
    val epochsLoaded by earningsViewModel.epochsLoaded.collectAsState()
    val top by earningsViewModel.top.collectAsState()
    val top200FocusRequested by earningsViewModel.top200FocusRequested.collectAsState()
    // a request to show the tile ends with the screen, shown or not, and
    // leaving closes the Key screen, which discards a phrase kept in memory
    DisposableEffect(earningsViewModel) {
        onDispose {
            earningsViewModel.consumeTop200Focus()
            earningsViewModel.keyModel.closeScreen()
        }
    }
    val isSeekerHolder by earningsViewModel.isSeekerHolder.collectAsState()
    val claimDialog by earningsViewModel.claimDialog.collectAsState()
    val epochClaimInFlight by earningsViewModel.epochClaimInFlight.collectAsState()
    val manualValidation by earningsViewModel.manualValidation.collectAsState()
    val ledgerLoad by earningsViewModel.ledgerFlow.load.collectAsState()
    val ledgerClaimConfigured by earningsViewModel.ledgerFlow.claimConfigured.collectAsState()
    val ledgerClaimDialog by earningsViewModel.ledgerFlow.dialog.collectAsState()
    val ledgerClaimInFlight by earningsViewModel.ledgerFlow.claimInFlight.collectAsState()
    val consentState by earningsViewModel.consentFlow.state.collectAsState()
    val consentStage by earningsViewModel.consentFlow.proofFlow.stage.collectAsState()

    // the payout key on this device: the sdk's flow behind the payout card
    val keyModel = earningsViewModel.keyModel
    val keyCard by keyModel.card.collectAsState()
    val keyReady by keyModel.ready.collectAsState()
    val pendingReplacement by keyModel.pendingReplacement.collectAsState()
    // what URnetwork's claim relay says of the v2 balance
    val claimStatus by earningsViewModel.claimStatus.collectAsState()
    val claimNowOutcome by earningsViewModel.claimNowOutcome.collectAsState()
    val isClaimingNow by earningsViewModel.isClaimingNow.collectAsState()
    // the capture the flag slots won and not claimed yet
    val ctfWins by earningsViewModel.ctfWins.collectAsState()
    // Change wallet offers a key on this device above the wallets; Use a wallet
    // I already have opens the same chooser without it
    var consentOffersKey by remember { mutableStateOf(false) }

    LaunchedEffect(isGuest) {
        keyModel.setGuest(isGuest)
    }
    // Back up first and the reminder's notification: the Key screen, once the
    // key's flow is ready
    val keyScreenRequested by BittensorKeyScreenRequests.screen.collectAsState()
    LaunchedEffect(keyScreenRequested, keyReady) {
        if (keyScreenRequested && keyReady && BittensorKeyScreenRequests.take()) {
            keyModel.openKeyScreen()
        }
    }

    // the same instance as the provider statistics' below: one nav entry, one store
    val providerStatusViewModel: ProviderStatusViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        fetchAccountPoints()
    }

    LifecycleResumeEffect(Unit) {
        earningsViewModel.onScreenResumed()
        onPauseOrDispose {}
    }

    // how and when providers are paid, with the times from the epoch schedule
    val locale = LocalConfiguration.current.locales[0]
    // the network's consent pays the payout key on this device: a coldkey
    // before the wallet read lists one, so there is no coldkey left to set
    val hasColdkey = wallet != null || keyCard.paysKeyOnThisDevice
    val payoutLine = snPayoutLine(
        walletKnown = earningsViewModel.protocolAvailable && walletLoaded && (hasColdkey || !walletFailed),
        hasColdkey = hasColdkey,
        totalClaimableRao = totalClaimableRao,
        schedule = schedule,
        nowMillis = System.currentTimeMillis(),
        formatTime = { formatSnPayoutTime(it, ZoneId.systemDefault(), locale) },
    )

    EarningsScreenContent(
        navController = navController,
        isRefreshing = earningsViewModel.isRefreshing,
        refresh = {
            fetchAccountPoints()
            earningsViewModel.refresh()
            providerStatusViewModel.refresh()
        },
        totalAccountPoints = totalAccountPoints,
        payoutPoints = payoutPoints,
        referralPoints = referralPoints,
        multiplierPoints = multiplierPoints,
        reliabilityPoints = reliabilityPoints,
        isSeekerHolder = isSeekerHolder,
        accountPointsFailed = accountPointsFailed,
        onRetryAccountPoints = fetchAccountPoints,
        protocolAvailable = earningsViewModel.protocolAvailable,
        wallet = wallet,
        walletLoaded = walletLoaded,
        walletFailed = walletFailed,
        onRetryWallet = earningsViewModel.retryWallet,
        connectState = connectState,
        onConnectWallet = { earningsViewModel.connectWithBridge() },
        onEnterManually = { earningsViewModel.openManualSheet() },
        onContinueLooksNew = { earningsViewModel.continueAfterLooksNew() },
        onDismissConnectState = { earningsViewModel.dismissConnectState() },
        claims = claims,
        totalClaimableRao = totalClaimableRao,
        claimsError = claimsError,
        onOpenClaim = { earningsViewModel.openClaimDialog() },
        // while the session's v1 claim is in flight, Claim opens it whatever the read says
        claimInFlight = epochClaimInFlight,
        epochs = epochs,
        epochsLoaded = epochsLoaded,
        top200 = top200TileState(top),
        focusTop200 = top200FocusRequested,
        onTop200Focused = earningsViewModel::consumeTop200Focus,
        onSeeProtocolBoard = onSeeProtocolBoard,
        reliabilityWindow = reliabilityWindow,
        formatAlpha = earningsViewModel::formatAlpha,
        formatShareBps = earningsViewModel::formatShareBps,
        shortSs58 = earningsViewModel::shortSs58,
        payoutLine = payoutLine,
        onSetColdkey = { earningsViewModel.connectWithBridge() },
        // the device wallet's v2 ledger, or what the claim relay says is claimable
        // for a network with no wallet on this device
        // while the session's v2 claim is in flight, Claim opens it whatever the read says
        ledgerTile = snLedgerTileWithRelay(wallet != null, snLedgerTile(ledgerLoad, ledgerClaimConfigured), claimStatus)
            ?.withClaimInFlight(ledgerClaimInFlight),
        onOpenLedgerClaim = { earningsViewModel.ledgerFlow.open() },
        claimStatus = claimStatus,
        onOpenRelayClaim = { earningsViewModel.openRelayClaimDialog() },
        ctfWins = ctfWins,
        consentTile = if (earningsViewModel.protocolAvailable) snConsentTile(consentState, consentStage) else null,
        onSignConsent = {
            consentOffersKey = true
            earningsViewModel.consentFlow.start()
        },
        onRetryConsent = { earningsViewModel.consentFlow.retry() },
        keyCard = keyCard,
        keyActions = BittensorKeyCardActions(
            setUp = keyModel::setUpPayouts,
            saveFirst = keyModel::saveFirst,
            useWallet = {
                consentOffersKey = false
                earningsViewModel.consentFlow.start()
            },
            finishSetup = keyModel::finishSetup,
            openKeyScreen = keyModel::openKeyScreen,
            reload = keyModel::visit,
        ),
        consentChangesWallet = consentState.networkCredential,
    )

    ClaimDialog(
        state = claimDialog,
        onDismiss = { earningsViewModel.closeClaimDialog() },
        onClaim = { earningsViewModel.claimAll() },
        onRetry = { earningsViewModel.retryClaimDialog() },
        formatAlpha = earningsViewModel::formatAlpha,
        shortSs58 = earningsViewModel::shortSs58,
        explorerTxUrl = earningsViewModel::explorerTxUrl,
        // the gas key sends one claim at a time: Claim waits for the session's v2 claim
        claimHeld = ledgerClaimInFlight,
    )

    SnLedgerClaimDialog(
        state = ledgerClaimDialog,
        onDismiss = { earningsViewModel.ledgerFlow.close() },
        onClaim = { earningsViewModel.ledgerFlow.claim() },
        onRetry = { earningsViewModel.ledgerFlow.retry() },
        formatAlpha = earningsViewModel::formatAlpha,
        shortSs58 = earningsViewModel::shortSs58,
        explorerTxUrl = earningsViewModel::explorerTxUrl,
        // the rows are named as the tile names them
        pools = snLedgerPools(ledgerLoad),
        // the gas key sends one claim at a time: Claim waits for the session's v1 claim
        claimHeld = epochClaimInFlight,
    )

    // Claim now, through URnetwork's claim relay: a screen of its own, with the
    // claim sheet's disclaimer
    ClaimRelayDialog(
        visible = earningsViewModel.isPresentedRelayClaimDialog,
        status = claimStatus,
        outcome = claimNowOutcome,
        claimingNow = isClaimingNow,
        fundsDisclaimer = remember(keyCard) { keyModel.fundsDisclaimerAt(Sdk.BittensorFundsDisclaimerPlaceClaimSheet) },
        onClaimNow = { earningsViewModel.claimNow() },
        onSelfClaim = {
            earningsViewModel.closeRelayClaimDialog()
            earningsViewModel.ledgerFlow.open()
        },
        onDismiss = { earningsViewModel.closeRelayClaimDialog() },
        formatAlpha = earningsViewModel::formatAlpha,
        shortSs58 = earningsViewModel::shortSs58,
    )

    val bridgeContext = LocalContext.current
    BittensorProofSheets(
        flow = earningsViewModel.proofFlow,
        onChoose = { walletId ->
            earningsViewModel.chooseProofWallet(walletId) { url -> launchBittensorBridge(bridgeContext, url) }
        },
        onSubmit = earningsViewModel::submitProof,
    )

    // the payout consent's steps: the wallet chooser, the coldkey the consent will name, then
    // the manual sheet or the bridge page of a session of the consent purpose
    BittensorProofSheets(
        flow = earningsViewModel.consentFlow.proofFlow,
        onChoose = { walletId -> earningsViewModel.consentFlow.chooseWallet(walletId) },
        onSubmit = earningsViewModel.consentFlow::submit,
        chooserHeader = if (consentOffersKey && keyCard.offersSetUp) {
            {
                BittensorKeyChangeWalletOffer(
                    fundsDisclaimer = remember(keyCard) { keyModel.fundsDisclaimerAt(Sdk.BittensorFundsDisclaimerPlaceChangeWallet) },
                    onCreateKey = {
                        // the chooser closes first, then the one tap, with its warning
                        earningsViewModel.consentFlow.proofFlow.dismiss()
                        keyModel.setUpPayouts()
                    },
                )
            }
        } else {
            null
        },
    )
    consentState.coldkeyEntry?.let { entry ->
        SnConsentColdkeySheet(
            entry = entry,
            onAddressChange = { earningsViewModel.consentFlow.updateColdkey(it) },
            onContinue = {
                earningsViewModel.consentFlow.continueWithColdkey { url -> launchBittensorBridge(bridgeContext, url) }
            },
            onDismiss = { earningsViewModel.consentFlow.dismissColdkey() },
        )
    }

    // a WalletConnect bridge return for the consent, handed over by the LoginActivity
    val consentReturn by SnConsentReturns.pending.collectAsState()
    LaunchedEffect(consentReturn) {
        SnConsentReturns.take()?.let { earningsViewModel.consentFlow.handleReturn(it) }
    }

    if (earningsViewModel.isPresentedManualSheet) {
        ConnectWalletSheet(
            address = earningsViewModel.manualAddress,
            onAddressChange = { earningsViewModel.updateManualAddress(it) },
            validation = manualValidation,
            onContinue = { earningsViewModel.continueManual() },
            onDismiss = { earningsViewModel.closeManualSheet() },
        )
    }

    // the payout key's screen, in a secure window of its own
    BittensorKeyScreenDialog(
        model = keyModel,
        shortSs58 = earningsViewModel::shortSs58,
    )

    // a key replaces the payout wallet from the next epoch: warned first, and
    // it is never the default
    if (pendingReplacement != null) {
        BittensorKeyReplaceDialog(
            oldAddress = earningsViewModel.shortSs58(keyCard.address),
            onConfirm = { keyModel.replacementConfirmed() },
            onDismiss = { keyModel.dismissReplacement() },
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
    // the session's v1 claim is in flight: Claim opens it
    claimInFlight: Boolean = false,
    epochs: List<AccountEpoch>,
    epochsLoaded: Boolean,
    top200: Top200TileState,
    reliabilityWindow: ReliabilityWindow?,
    formatAlpha: (Long) -> String,
    formatShareBps: (Long) -> String,
    shortSs58: (String) -> String,
    accountPointsFailed: Boolean = false,
    onRetryAccountPoints: () -> Unit = {},
    walletFailed: Boolean = false,
    onRetryWallet: () -> Unit = {},
    payoutLine: SnPayoutLine? = null,
    onSetColdkey: () -> Unit = {},
    ledgerTile: SnLedgerTile? = null,
    onOpenLedgerClaim: () -> Unit = {},
    // what URnetwork's claim relay says, and its Claim now
    claimStatus: SnClaimStatusState? = null,
    onOpenRelayClaim: () -> Unit = {},
    // the capture the flag slots won and not claimed yet, a line each on the Settlement v2 tile
    ctfWins: List<SnCtfWinState> = emptyList(),
    consentTile: SnConsentTile? = null,
    onSignConsent: () -> Unit = {},
    onRetryConsent: () -> Unit = {},
    // the payout key on this device (BittensorKeyCard.kt)
    keyCard: BittensorKeyCardInfo = BittensorKeyCardInfo(),
    keyActions: BittensorKeyCardActions = BittensorKeyCardActions(),
    // the session holds the network credential, so it can choose the payout wallet
    consentChangesWallet: Boolean = false,
    // the leaderboard's "Claim on Earnings" asked for the Top 200 tile
    focusTop200: Boolean = false,
    onTop200Focused: () -> Unit = {},
    onSeeProtocolBoard: () -> Unit = {},
) {
    val refreshState = rememberPullToRefreshState()
    val top200BringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focusTop200, top200 is Top200TileState.Hidden) {
        if (focusTop200 && top200 !is Top200TileState.Hidden) {
            // once the tile is laid out
            withFrameNanos { }
            top200BringIntoView.bringIntoView()
            onTop200Focused()
        }
    }
    val claimsByEpoch = claims.associateBy { it.epoch }

    // the payout card: the consent, and the payout key where the sdk's flow fills it
    val payoutCard: @Composable () -> Unit = {
        consentTile?.let {
            SnConsentBlock(
                tile = it,
                onSign = onSignConsent,
                onRetry = onRetryConsent,
                shortSs58 = shortSs58,
                key = keyCard,
                keyActions = keyActions,
                changesWallet = consentChangesWallet,
            )
        }
    }

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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
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
                    .tabletReadableColumn()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                // until the payout key's phrase is saved
                if (keyCard.backupReminder) {
                    BittensorKeyBackupBanner(onOpen = keyActions.openKeyScreen)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // the payout card leads while it needs action: no consent, or
                // setup cut short
                if (keyCard.showsKeyPath && consentTile != null) {
                    payoutCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                PointsHeadline(
                    totalAccountPoints = totalAccountPoints,
                    payoutPoints = payoutPoints,
                    referralPoints = referralPoints,
                    reliabilityPoints = reliabilityPoints,
                    multiplierPoints = multiplierPoints,
                    isSeekerHolder = isSeekerHolder,
                    failed = accountPointsFailed,
                    onRetry = onRetryAccountPoints,
                    payoutLine = payoutLine,
                    onClaim = onOpenClaim,
                    onSetColdkey = onSetColdkey,
                )

                Spacer(modifier = Modifier.height(16.dp))


                Spacer(modifier = Modifier.height(16.dp))

                WalletSection(
                    protocolAvailable = protocolAvailable,
                    wallet = wallet,
                    walletLoaded = walletLoaded,
                    walletFailed = walletFailed,
                    onRetryWallet = onRetryWallet,
                    connectState = connectState,
                    onConnectWallet = onConnectWallet,
                    onEnterManually = onEnterManually,
                    onContinueLooksNew = onContinueLooksNew,
                    onDismissConnectState = onDismissConnectState,
                    shortSs58 = shortSs58,
                    // the payout key on this device takes the connect call's place
                    localKey = BittensorWalletCardLocalKey.of(keyCard),
                )

                // the first claim tile carries the claim card's disclaimer
                if (wallet != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    UnclaimedTile(
                        totalClaimableRao = totalClaimableRao,
                        claimsError = claimsError,
                        onOpenClaim = onOpenClaim,
                        claimInFlight = claimInFlight,
                        formatAlpha = formatAlpha,
                        fundsDisclaimer = keyCard.claimFundsDisclaimer,
                    )
                }

                // settlement v2, separate from the v1 claims above: URnetwork claims it
                // for every consented network (the claim relay), and the device's own
                // claim stays as the fallback
                ledgerTile?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    SnLedgerTileBlock(
                        tile = it,
                        onClaim = onOpenLedgerClaim,
                        formatAlpha = formatAlpha,
                        relay = claimStatus,
                        shortSs58 = shortSs58,
                        onClaimNow = onOpenRelayClaim,
                        onSelfClaim = onOpenLedgerClaim,
                        fundsDisclaimer = if (wallet != null) Sdk.BittensorFundsDisclaimerNone else keyCard.claimFundsDisclaimer,
                        ctfWins = ctfWins,
                    )
                }

                // with or without a wallet entry: a network that never connected one is
                // the one the consent pays. It keeps this place while it needs no action
                if (!keyCard.showsKeyPath && consentTile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    payoutCard()
                }

                // the network's standing on the Protocol leaderboard (/sn/top/me)
                val onSignFleetBinding = { navController.navigate(Route.Top200Sign) }
                when (top200) {
                    is Top200TileState.Claimed -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Top200BoundRow(
                            state = top200,
                            onSignFleetBinding = onSignFleetBinding,
                            onSeeBoard = onSeeProtocolBoard,
                            modifier = Modifier.bringIntoViewRequester(top200BringIntoView),
                        )
                    }
                    is Top200TileState.Projected -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Top200Tile(
                            state = top200,
                            onSignFleetBinding = onSignFleetBinding,
                            onSeeBoard = onSeeProtocolBoard,
                            modifier = Modifier.bringIntoViewRequester(top200BringIntoView),
                        )
                    }
                    Top200TileState.Hidden -> {}
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
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    payoutLine: SnPayoutLine? = null,
    onClaim: () -> Unit = {},
    onSetColdkey: () -> Unit = {},
) {
    if (failed) {
        // a failed points fetch is an error, not "0 points"; how payouts work still shows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            SectionLoadError(onRetry = onRetry)
            payoutLine?.let {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = TextFaint)
                Spacer(modifier = Modifier.height(12.dp))
                SnPayoutLineBlock(line = it, onClaim = onClaim, onSetColdkey = onSetColdkey)
            }
        }
        return
    }
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
                        stringResource(id = R.string.seeker_multiplier_benefit),
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

        payoutLine?.let {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TextFaint)
            Spacer(modifier = Modifier.height(12.dp))
            SnPayoutLineBlock(line = it, onClaim = onClaim, onSetColdkey = onSetColdkey)
        }
    }
}

/**
 * How and when a provider is paid on the UR subnet. With a coldkey: the
 * explanation, the current epoch's times when known, and Claim while something
 * is claimable (the claim dialog; the app never claims by itself). Without one:
 * the prompt to set it, with the action that opens the coldkey flow.
 */
@Composable
private fun SnPayoutLineBlock(
    line: SnPayoutLine,
    onClaim: () -> Unit,
    onSetColdkey: () -> Unit,
) {
    Column(modifier = Modifier.testTag(EARNINGS_SN_PAYOUT_LINE_TAG)) {
        when (line) {
            SnPayoutLine.SetColdkey -> {
                Text(
                    stringResource(id = R.string.set_coldkey_to_get_paid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(id = R.string.set_coldkey),
                    modifier = Modifier
                        .testTag(EARNINGS_SET_COLDKEY_TAG)
                        .clickable { onSetColdkey() }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BlueMedium
                )
            }
            is SnPayoutLine.Schedule -> {
                Text(
                    stringResource(id = R.string.sn_payout_schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                line.times?.let { times ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            id = R.string.sn_payout_schedule_times,
                            times.epochEnd,
                            times.claimOpen,
                            times.expiry
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                if (line.claimable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    URButton(
                        onClick = onClaim,
                        modifier = Modifier.testTag(EARNINGS_SN_PAYOUT_CLAIM_TAG)
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.claim), style = buttonTextStyle)
                    }
                }
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
internal fun WalletSection(
    protocolAvailable: Boolean,
    wallet: SnWalletState?,
    walletLoaded: Boolean,
    connectState: WalletConnectState,
    onConnectWallet: () -> Unit,
    onEnterManually: () -> Unit,
    onContinueLooksNew: () -> Unit,
    onDismissConnectState: () -> Unit,
    shortSs58: (String) -> String,
    walletFailed: Boolean = false,
    onRetryWallet: () -> Unit = {},
    // the payout key on this device, while no wallet is read: shown in place of
    // the connect call, which would invite a second wallet
    localKey: BittensorWalletCardLocalKey? = null,
) {
    if (walletLoaded && wallet == null && walletFailed) {
        // a failed read is an error, not the "connect wallet" offer
        SectionLoadError(onRetry = onRetryWallet)
        return
    }

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

    if (localKey != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .testTag(EARNINGS_WALLET_LOCAL_KEY_TAG)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (localKey.linked) Icons.Filled.Verified else Icons.Filled.Key,
                            contentDescription = null,
                            tint = if (localKey.linked) BlueMedium else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            shortSs58(localKey.address),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(
                    id = if (localKey.linked) R.string.wallet_connected_to_protocol else R.string.bittensor_key_card_not_linked
                ),
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
                    is WalletConnectState.SignatureMismatch -> stringResource(
                        id = R.string.bittensor_error_signature_mismatch,
                        bittensorWalletDisplayName(connectState.walletId),
                    ) to Red
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
    // the session's v1 claim is in flight: Claim opens it whatever the read says
    claimInFlight: Boolean = false,
    formatAlpha: (Long) -> String,
    // the claim card's disclaimer (a BittensorFundsDisclaimer*): Earnings shows
    // it once, here only while the payout card has none
    fundsDisclaimer: String = Sdk.BittensorFundsDisclaimerNone,
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
                claimsReadFailureText(claimsError),
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
            enabled = 0 < totalClaimableRao || claimInFlight
        ) { buttonTextStyle ->
            Text(stringResource(id = R.string.claim), style = buttonTextStyle)
        }

        if (bittensorFundsDisclaimerRes(fundsDisclaimer) != null) {
            Spacer(modifier = Modifier.height(16.dp))
            BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer)
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
            top200 = Top200TileState.Hidden,
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
            top200 = Top200TileState.Projected(
                position = 143,
                margin = 0.27,
                label = ProtocolLabel.ESTIMATE,
                bindingOpen = false,
                borderline = false,
            ),
            reliabilityWindow = null,
            formatAlpha = { EarningsFormat.alpha(it) },
            formatShareBps = { EarningsFormat.shareBps(it) },
            shortSs58 = { Ss58.short(it) },
        )
    }
}
