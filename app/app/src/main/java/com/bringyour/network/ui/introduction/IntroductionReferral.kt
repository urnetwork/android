package com.bringyour.network.ui.introduction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.IntroRoute
import com.bringyour.network.ui.components.ChartKey
import com.bringyour.network.ui.components.CopyReferralCode
import com.bringyour.network.ui.components.ShareButton
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionReferral(
    navController: NavHostController,
    dismiss: () -> Unit,
    totalReferrals: Long,
    referralCode: String
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("")},
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(
                    "Step 2",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .background(
                            OffBlack,
                            RoundedCornerShape(12.dp)
                        )
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Refer friends",
                            style = TopBarTitleTextStyle
                        )

                        Text(
                            "${totalReferrals}/5",
                            style = TopBarTitleTextStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ReferralBar(
                        totalReferrals = totalReferrals
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "You get +30 GiB/month\nYour friend gets +30 GiB/month\nFor Life!",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Refer some friends and watch your free data go up.",
                        style = TopBarTitleTextStyle
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    URTextInputLabel("Bonus referral code")

//                    Spacer(modifier = Modifier.height(4.dp))

                    CopyReferralCode(
                        bonusReferralCode = referralCode
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ShareButton(referralLink = "https://ur.io/c?bonus=${referralCode}")

                }

            }

            URButton(onClick = {
                dismiss()
            }) { btnStyle ->
                Text("Enter")
            }

        }
    }
}

@Composable
fun ReferralBar(
    totalReferrals: Long
) {

    val maxReferrals = 5f
    val cornerRadius = 6.dp
    val usedColor = BlueMedium
    val availableColor = TextFaint

    var displayReferralCount = totalReferrals.toFloat()

    if (displayReferralCount >= 5) {
        displayReferralCount = 5f
    }

    val usedFraction = displayReferralCount / maxReferrals
    val availableFraction = 1f - usedFraction

    val minWeight = 0.0001f
    val safeUsedFraction = if (usedFraction <= 0f) minWeight else usedFraction
    val safeAvailableFraction = if (availableFraction <= 0f) minWeight else availableFraction


    Column {

        Row(modifier = Modifier
            .height(12.dp)
            .fillMaxWidth()
        ) {

            Box(
                modifier = Modifier
                    .weight(safeUsedFraction)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerRadius,
                            bottomStart = cornerRadius,
                            topEnd = if (displayReferralCount == maxReferrals) cornerRadius else 0.dp,
                            bottomEnd = if (displayReferralCount == maxReferrals) cornerRadius else 0.dp,
                        )
                    )
                    .background(usedColor)
            )

            Box(
                modifier = Modifier
                    .weight(safeAvailableFraction)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = if (displayReferralCount == 0f) cornerRadius else 0.dp,
                            bottomStart = if (displayReferralCount == 0f) cornerRadius else 0.dp,
                            topEnd = cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    )
                    .background(availableColor)
            )

        }

        Spacer(modifier = Modifier.height(4.dp))

        Row {

            // used
            ChartKey(
                label = "Referrals",
                color = usedColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // available
            ChartKey(
                label = stringResource(id = R.string.available_data_key),
                color = availableColor
            )
        }
    }
}