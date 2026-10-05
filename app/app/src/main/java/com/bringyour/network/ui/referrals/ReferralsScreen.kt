package com.bringyour.network.ui.referrals

import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.referral.ReferralGoldPanel
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.models.SectionLoad
import com.bringyour.network.ui.shared.models.referralStatLoads
import com.bringyour.network.ui.settings.updateReferralNetworkBottomSheet.UpdateReferralNetworkBottomSheet
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.EarningsFormat
import kotlinx.coroutines.launch

internal const val ACCEPTANCE_REFERRALS_ADD_CODE_TAG = "acceptance.referrals.addCode"

/** What the referral network row offers. */
sealed interface ReferralNetworkAction {
    /** No referral network: the primary "Add referral code" action. */
    data object AddCode : ReferralNetworkAction

    /** The network this one signed up with, and Update. */
    data class Update(val networkName: String) : ReferralNetworkAction
}

/** The action for the network this one signed up with: Add referral code when there is none. */
internal fun referralNetworkAction(referralNetworkName: String?): ReferralNetworkAction =
    if (referralNetworkName.isNullOrBlank()) {
        ReferralNetworkAction.AddCode
    } else {
        ReferralNetworkAction.Update(referralNetworkName)
    }

/**
 * Account › Referrals ("Refer and earn"). Everything about the referral program
 * in one place: the onboarding referral card (code, share, progress toward the
 * cap, the crown), how many friends joined and the points they earned, and the
 * referral network this network signed up with. The Earnings screen keeps only
 * the points breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralsScreen(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    referralCode: String,
    totalReferrals: Long,
    referralPoints: Double,
    pointsLoad: SectionLoad,
    fetchAccountPoints: () -> Unit,
    referralCodeLoad: SectionLoad = SectionLoad.Loaded,
    retryReferralCode: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val referralNetwork by settingsViewModel.referralNetwork.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val updateReferralNetworkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPresentingUpdateReferralNetworkSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.fetchReferralNetwork()
        fetchAccountPoints()
    }

    ReferralsScreenContent(
        navController = navController,
        referralCode = referralCode,
        totalReferrals = totalReferrals,
        referralPoints = referralPoints,
        pointsLoad = pointsLoad,
        retryPoints = fetchAccountPoints,
        referralCodeLoad = referralCodeLoad,
        retryReferralCode = retryReferralCode,
        referralNetworkName = referralNetwork?.name,
        onUpdateReferralNetwork = {
            scope.launch {
                updateReferralNetworkSheetState.expand()
                isPresentingUpdateReferralNetworkSheet = true
            }
        },
        snackbarHostState = snackbarHostState,
    )

    if (isPresentingUpdateReferralNetworkSheet) {
        val referralNetworkUpdatedMessage = stringResource(id = R.string.referral_network_updated)
        UpdateReferralNetworkBottomSheet(
            sheetState = updateReferralNetworkSheetState,
            setIsPresenting = { isPresentingUpdateReferralNetworkSheet = it },
            onSuccess = {
                isPresentingUpdateReferralNetworkSheet = false
                settingsViewModel.fetchReferralNetwork()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = referralNetworkUpdatedMessage,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onError = { errMsg ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = errMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
            },
            referralNetworkName = referralNetwork?.name
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralsScreenContent(
    navController: NavHostController,
    referralCode: String,
    totalReferrals: Long,
    referralPoints: Double,
    pointsLoad: SectionLoad,
    referralNetworkName: String?,
    onUpdateReferralNetwork: () -> Unit,
    snackbarHostState: SnackbarHostState,
    retryPoints: () -> Unit = {},
    referralCodeLoad: SectionLoad = SectionLoad.Loaded,
    retryReferralCode: () -> Unit = {},
) {
    val statLoads = referralStatLoads(codeLoad = referralCodeLoad, pointsLoad = pointsLoad)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.refer_and_earn),
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            /**
             * 1. The onboarding referral card, reused as is: heading, the
             * "+N GiB/day" detail from the SDK terms, code with copy + share,
             * progress toward the cap, and the crown once a friend has joined.
             */
            ReferralGoldPanel(
                referralCode = referralCode.ifEmpty { null },
                totalReferrals = totalReferrals,
                codeFailed = referralCodeLoad == SectionLoad.Failed,
                onRetryCode = retryReferralCode,
            )

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * 2. Stats: friends joined, and the points they earned this network.
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                ReferralStatColumn(
                    label = stringResource(id = R.string.total_referrals),
                    value = "$totalReferrals",
                    load = statLoads.totalReferrals,
                    onRetry = retryReferralCode,
                    modifier = Modifier.weight(1f)
                )
                ReferralStatColumn(
                    label = stringResource(id = R.string.referral_points),
                    value = EarningsFormat.points(referralPoints),
                    load = statLoads.referralPoints,
                    onRetry = retryPoints,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = TextFaint)
            Spacer(modifier = Modifier.height(16.dp))

            /**
             * 4. The referral network this network signed up with. With none
             * yet, the row is the code entry itself: a friend who missed the
             * field at sign-up adds the code here (support inbox 1698).
             */
            URTextInputLabel(stringResource(id = R.string.referral_network))
            when (val action = referralNetworkAction(referralNetworkName)) {
                ReferralNetworkAction.AddCode -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(
                        onClick = onUpdateReferralNetwork,
                        modifier = Modifier.testTag(ACCEPTANCE_REFERRALS_ADD_CODE_TAG)
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.add_referral_code), style = buttonTextStyle)
                    }
                }
                is ReferralNetworkAction.Update -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            action.networkName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )

                        TextButton(onClick = onUpdateReferralNetwork) {
                            Text(
                                stringResource(id = R.string.update),
                                color = BlueMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ReferralStatColumn(
    label: String,
    value: String,
    load: SectionLoad,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        if (load == SectionLoad.Loaded) {
            Text(value, style = HeadingLargeCondensed)
        } else if (load == SectionLoad.Failed) {
            // a failed fetch is not "0": no value, and Try again
            val loadFailed = stringResource(id = R.string.load_failed)
            Text(
                "\u2014",
                style = HeadingLargeCondensed,
                color = TextMuted,
                modifier = Modifier.semantics { contentDescription = loadFailed }
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    stringResource(id = R.string.try_again),
                    color = BlueMedium
                )
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = TextMuted,
                trackColor = TextFaint,
                strokeWidth = 2.dp
            )
        }
    }
}

@Preview
@Composable
private fun ReferralsScreenPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ReferralsScreenContent(
            navController = navController,
            referralCode = "ABC123",
            totalReferrals = 3,
            referralPoints = 668.0,
            pointsLoad = SectionLoad.Loaded,
            referralNetworkName = "parent_network",
            onUpdateReferralNetwork = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun ReferralsScreenNoReferralsPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        ReferralsScreenContent(
            navController = navController,
            referralCode = "ABC123",
            totalReferrals = 0,
            referralPoints = 0.0,
            pointsLoad = SectionLoad.Loading,
            referralNetworkName = null,
            onUpdateReferralNetwork = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
