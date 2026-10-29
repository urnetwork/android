package com.bringyour.network.ui.connect.providerlocations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.bringyour.network.R
import com.bringyour.network.location.MockLocationStatus
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted

/**
 * The Android-only "sync device location with provider" control at the top of
 * the provider locations sheet.
 *
 * The OS gives no callback when the user picks the mock location app, so eligibility
 * is re-read whenever this returns to the foreground (the same discipline as the
 * battery optimization toggle).
 *
 * @param navController Navigation controller used to route to setup and troubleshooting guides.
 * @param viewModel ViewModel providing mock location state and actions.
 */
@Composable
fun MockLocationSection(
    navController: NavController,
    viewModel: MockLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // the sheet usually opens while the activity is already resumed, so no
    // ON_RESUME arrives — read the setup signals once on show as well, or the
    // toggle would decide against stale state
    LaunchedEffect(Unit) {
        viewModel.refreshEligibility()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEligibility()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(id = R.string.sync_device_location_with_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )

                InfoIconWithOverlay {
                    Column {
                        Text(stringResource(id = R.string.mock_location_guide_intro))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(id = R.string.mock_location_disclosure_device_wide))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(id = R.string.mock_location_disclosure_detectable))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(id = R.string.mock_location_guide_title),
                            color = BlueMedium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                navController.navigate(Route.MockLocationGuide)
                            },
                        )
                    }
                }
            }

            // ORPHANED is not a togglable state: the test providers are stuck until
            // the op comes back, so the preference cannot express what the user wants.
            // The recovery row below is the way out of it from this screen.
            URSwitch(
                checked = state.enabled && state.status != MockLocationStatus.ORPHANED,
                enabled = state.status != MockLocationStatus.ORPHANED,
                toggle = {
                    val enabled = !state.enabled
                    viewModel.setEnabled(enabled)
                    if (enabled && !state.setupComplete) {
                        navController.navigate(Route.MockLocationGuide)
                    }
                },
            )
        }

        // the note is always present: "use most stable provider" at rest,
        // replaced by the more specific status while the toggle is working
        // through setup / waiting / syncing / cleanup
        val label = when (state.status) {
            MockLocationStatus.NEEDS_DEV_OPTIONS,
            MockLocationStatus.NEEDS_SELECTION,
            MockLocationStatus.NEEDS_LOCATION_ON,
            MockLocationStatus.NEEDS_LOCATION_PERMISSION,
            -> stringResource(id = R.string.mock_location_needs_setup)

            MockLocationStatus.ELIGIBLE ->
                stringResource(id = R.string.mock_location_waiting_for_provider)

            MockLocationStatus.ACTIVE -> state.target?.let {
                stringResource(id = R.string.mock_location_active, it.label)
            } ?: stringResource(id = R.string.use_most_stable_provider)

            MockLocationStatus.ORPHANED ->
                stringResource(id = R.string.mock_location_status_stuck)

            else -> stringResource(id = R.string.use_most_stable_provider)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.status == MockLocationStatus.ORPHANED)
                MaterialTheme.colorScheme.error
            else
                TextMuted,
        )

        if (state.status == MockLocationStatus.ORPHANED) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Route.MockLocationGuide) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(id = R.string.mock_location_error_stuck_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    // without the weight the text takes the whole row and the
                    // chevron below measures to zero width
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
