package com.bringyour.network.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRootSubscription(
    loginMode: LoginMode,
    currentPlan: Plan,
    isProcessingUpgrade: Boolean,
    scope: CoroutineScope,
    logout: () -> Unit,
    setIsPresentingUpgradePlanSheet: (Boolean) -> Unit,
    upgradePlanSheetState: SheetState
) {
    // member area
    Box {
        Column {
            Text(
                stringResource(id = R.string.member),
                style = TextStyle(
                    color = TextMuted
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {

                if (loginMode == LoginMode.Guest) {
                    Text(
                        stringResource(id = R.string.guest),
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {

                    if (isProcessingUpgrade) {

                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )

                    } else {
                        Text(if (currentPlan == Plan.Supporter) stringResource(id = R.string.supporter) else stringResource(id = R.string.free),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                if (loginMode == LoginMode.Guest) {
                    Text(
                        stringResource(id = R.string.create_account),
                        style = TextStyle(
                            color = BlueMedium
                        ),
                        modifier = Modifier
                            .offset(y = (-8).dp)
                            .clickable {
                                logout()
                            }
                    )
                } else {

                    if (currentPlan == Plan.Basic && !isProcessingUpgrade) {
                        Text(
                            stringResource(id = R.string.change),
                            modifier = Modifier
                                .offset(y = (-8).dp)
                                .clickable {
                                    scope.launch {
                                        setIsPresentingUpgradePlanSheet(true)
                                        upgradePlanSheetState.expand()
                                    }
                                },
                            style = TextStyle(
                                color = BlueMedium
                            )
                        )
                    }
                }
            }
        }
    }
}