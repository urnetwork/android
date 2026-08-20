package com.bringyour.network.ui.stats

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.Sdk

/**
 * Editor for the device transport settings: one carrier, or auto with a
 * per-carrier enable. The auto preference order is the sdk default and is not
 * user-managed. Changes apply together with the update button.
 *
 * The draft is an sdk policy object edited through the sdk's helpers (a newly
 * enabled carrier takes its default priority; the last enabled carrier can't
 * be disabled), with a render snapshot re-taken after every edit, so the
 * editing rules live in one place for every platform. Parameterized by kind:
 * the client policy (reaching providers) or the provider policy (providing
 * for others).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportSettingsScreen(
    navController: NavController,
    kind: TransportSettingsKind,
    transportSettingsViewModel: TransportSettingsViewModel = hiltViewModel(),
) {

    // the sdk draft policy (inside the snapshot) and the policy it started from
    var draft by remember { mutableStateOf<TransportSettingsUi?>(null) }
    var original by remember { mutableStateOf<TransportSettingsUi?>(null) }
    // whether the user has touched the draft; until then it follows the policy
    // in force (which normally is published before the first composition, and
    // otherwise replaces the default fallback when it arrives)
    var edited by remember { mutableStateOf(false) }

    // the sdk default policy for this kind
    val defaultSettings = remember(kind) { kind.defaultSettings() }

    val currentSettings = transportSettingsViewModel.settings(kind)
    val runtimeStatus = transportSettingsViewModel.status(kind)
    LaunchedEffect(currentSettings) {
        if (!edited) {
            val start = currentSettings ?: defaultSettings
            original = start
            draft = TransportSettingsUi(start.sdk.clone() ?: start.sdk)
        }
    }

    val currentDraft = draft
    val currentOriginal = original
    val isDirty = currentDraft != null && currentOriginal != null &&
        !transportSettingsEqual(currentDraft.sdk, currentOriginal.sdk)
    val isDefault = currentDraft != null &&
        transportSettingsEqual(currentDraft.sdk, defaultSettings.sdk)

    // the runtime-status decorations for the current draft. Every display
    // rule (Auto-only, the draft-equals-applied gate, enabled-and-ineligible
    // rows, memory vs generic copy) is the pure predicate's.
    val statusPresentation = if (currentDraft != null) {
        TransportStatusPresentation.compute(
            draft = currentDraft,
            statusPolicy = transportSettingsViewModel.statusPolicy(kind),
            status = runtimeStatus,
        )
    } else {
        TransportStatusPresentation.hidden()
    }

    // takes a fresh render snapshot after an edit of the sdk draft
    val refreshDraft = {
        edited = true
        currentDraft?.let { draft = TransportSettingsUi(it.sdk) }
    }

    // the selectable modes in the sdk's preference order: the order every
    // transport list shows
    val selectable = remember { TransportTypeUi.selectable }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            id = when (kind) {
                                TransportSettingsKind.CLIENT -> R.string.transports
                                TransportSettingsKind.PROVIDER -> R.string.provider_transports
                            }
                        ),
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
            )
        },
        containerColor = Black
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            if (currentDraft == null) {
                return@Column
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                /**
                 * Transport mode: auto, or one carrier
                 */
                SettingsGroup(title = stringResource(id = R.string.transport)) {
                    ModeRow(
                        transport = null,
                        selected = currentDraft.isAuto,
                        onClick = {
                            currentDraft.sdk.mode = Sdk.TransportModeAuto
                            refreshDraft()
                        }
                    )
                    for (transport in selectable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ModeRow(
                            transport = transport,
                            selected = currentDraft.singleTransport == transport,
                            onClick = {
                                currentDraft.sdk.mode = transport.rawValue
                                refreshDraft()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(
                            id = when (kind) {
                                TransportSettingsKind.CLIENT -> R.string.transport_client_footer
                                TransportSettingsKind.PROVIDER -> R.string.transport_provider_footer
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                /**
                 * Carriers enabled under auto, in the sdk preference order
                 */
                if (currentDraft.isAuto) {
                    if (statusPresentation.showBanner) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Amber,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(
                                    id = if (statusPresentation.memoryConstraint) {
                                        R.string.transport_auto_degraded_memory
                                    } else {
                                        R.string.transport_auto_degraded
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    SettingsGroup(title = stringResource(id = R.string.enabled_under_auto)) {
                        selectable.forEachIndexed { index, transport ->
                            if (0 < index) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            val enabled = currentDraft.isAutoEnabled(transport)
                            AutoToggleRow(
                                transport = transport,
                                checked = enabled,
                                constrained = statusPresentation.constrainedTransports.contains(transport),
                                // the last enabled carrier can't be turned off (the
                                // sdk refuses the edit: an empty auto policy would
                                // resolve to the full default), so show it disabled
                                enabled = !(enabled && currentDraft.autoTransports.size == 1),
                                toggle = {
                                    // on/off through the sdk so the default-priority
                                    // and last-carrier rules are the sdk's
                                    if (currentDraft.sdk.setAutoModeEnabled(transport.rawValue, !enabled)) {
                                        refreshDraft()
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(id = R.string.enabled_under_auto_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextFaint
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                /**
                 * Back to the sdk default policy
                 */
                if (!isDefault) {
                    URButton(
                        onClick = {
                            val sdkDefault = defaultSettings.sdk
                            edited = true
                            draft = TransportSettingsUi(sdkDefault.clone() ?: sdkDefault)
                        },
                        style = ButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth()
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.restore_default_transports),
                            style = buttonTextStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                URButton(
                    onClick = {
                        draft?.let {
                            transportSettingsViewModel.apply(it.sdk, kind)
                            navController.popBackStack()
                        }
                    },
                    enabled = isDirty
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.update),
                        style = buttonTextStyle
                    )
                }
            }

        }
    }
}

/**
 * A selectable row for one transport mode: null is auto. A checkmark marks the
 * selected row.
 */
@Composable
private fun ModeRow(
    transport: TransportTypeUi?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (transport != null) {
                TransportLabel(transport, showsDetail = true)
            } else {
                Column {
                    Text(
                        stringResource(id = R.string.auto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        stringResource(id = R.string.transport_auto_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = if (selected) Green else Color.Transparent,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * The enable switch for one carrier under auto
 */
@Composable
private fun AutoToggleRow(
    transport: TransportTypeUi,
    checked: Boolean,
    constrained: Boolean,
    enabled: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TransportLabel(transport, showsDetail = false)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (constrained) {
            // a runtime warning, not an editing restriction: the toggle stays
            // editable
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(id = R.string.transport_unavailable_system_constraints),
                tint = Amber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        URSwitch(
            checked = checked,
            enabled = enabled,
            toggle = toggle
        )
    }
}

/**
 * The carrier's color dot and name, with its one line description when asked
 */
@Composable
private fun TransportLabel(
    transport: TransportTypeUi,
    showsDetail: Boolean,
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(transport.color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                transport.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            val detailRes = transport.detailRes
            if (showsDetail && detailRes != null) {
                Text(
                    stringResource(id = detailRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            title,
            style = TextStyle(color = TextMuted)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}
