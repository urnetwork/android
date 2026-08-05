package com.bringyour.network.ui.connect.providerlocations

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.location.MockLocationStatus
import com.bringyour.network.location.openAboutPhone
import com.bringyour.network.location.openDeveloperOptions
import com.bringyour.network.location.openLocationSettings
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.lighten

/**
 * Walks the user through making URnetwork the Android mock location app. The
 * state machine decides which step is current — there is no OS callback for
 * the selection, so state is re-read every time this screen resumes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockLocationGuideScreen(
    navController: NavController,
    viewModel: MockLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // navigating here does not pause the activity, so no ON_RESUME arrives on
    // entry; the observer below covers the important case of returning from
    // the system settings screens the steps launch
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.mock_location_guide_title),
                        style = TopBarTitleTextStyle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
        containerColor = Black,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(id = R.string.mock_location_guide_intro),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Steps read the raw setup signals, not `status`: with the toggle
            // off `status` is DISABLED regardless of how the device is
            // configured, which would mark every step done and hide the
            // actions — exactly when the guide is most needed.
            GuideStep(
                text = stringResource(id = R.string.mock_location_step_developer_options),
                done = state.devOptionsEnabled,
                actionLabel = stringResource(id = R.string.mock_location_open_about_phone),
                current = !state.devOptionsEnabled,
                onAction = { openAboutPhone(context) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideStep(
                text = stringResource(id = R.string.mock_location_step_select_app),
                done = state.mockAppSelected,
                actionLabel = stringResource(id = R.string.mock_location_open_developer_options),
                current = state.devOptionsEnabled && !state.mockAppSelected,
                onAction = { openDeveloperOptions(context) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideStep(
                text = stringResource(id = R.string.mock_location_step_location_services),
                done = state.locationServicesEnabled,
                actionLabel = stringResource(id = R.string.mock_location_open_location_settings),
                current = state.devOptionsEnabled && state.mockAppSelected &&
                        !state.locationServicesEnabled,
                onAction = { openLocationSettings(context) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (state.setupComplete) {
                Text(
                    stringResource(id = R.string.mock_location_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (state.status == MockLocationStatus.ORPHANED) {
                Text(
                    stringResource(id = R.string.mock_location_error_cleanup_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                stringResource(id = R.string.mock_location_disclosure_device_wide),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.mock_location_disclosure_detectable),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.mock_location_disclosure_turn_off_first),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideStep(
    text: String,
    done: Boolean,
    current: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase.lighten(0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                // the bullet sits on the first text line, not centered on a
                // wrapped paragraph
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (done) Green else Color.White),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) TextMuted else Color.White,
                )
            }

            if (current) {
                Spacer(modifier = Modifier.height(12.dp))
                URButton(onClick = onAction) { buttonTextStyle ->
                    Text(actionLabel, style = buttonTextStyle)
                }
            }
        }
    }
}
