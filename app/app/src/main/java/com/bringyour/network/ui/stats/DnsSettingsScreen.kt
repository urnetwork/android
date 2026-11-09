package com.bringyour.network.ui.stats

import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLearnMoreText
import com.bringyour.network.ui.connection_region.ConnectionRegionSettingsRow
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextDanger
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.isIpAddressValue
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// how often the screen reads this connection's blocked count while it shows it
private const val DNS_BLOCKED_COUNT_POLL_MILLIS = 2000L

/**
 * The device's dns resolver settings (connect ADBLOCK3RD F1, F2): the
 * connection region, then the traffic dns. The traffic dns opens on the
 * selection in force with its status, then the sdk's picks in their order:
 * URnetwork (default), the preset groups, Custom and, when the exit's country
 * has one, its regional recommendation. A pick applies at once, a personal
 * preset once its id is saved, and Custom opens today's editor, whose changes
 * apply together with the update button. Fast DNS on connect and the
 * URnetwork ad blocker apply at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    navController: NavController,
    dnsSettingsViewModel: DnsSettingsViewModel = hiltViewModel(),
    blockerViewModel: BlockerViewModel = hiltViewModel(),
) {

    var draft by remember { mutableStateOf<DnsSettingsUi?>(null) }
    var original by remember { mutableStateOf<DnsSettingsUi?>(null) }

    // what a pick opens before anything is saved, one at a time: Custom's
    // editor, or a personal preset's id field (by its preset id)
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var personalPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var personalInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var personalErrorId by rememberSaveable { mutableStateOf("") }

    // the editor opens only on the settings in force, never on a blank form
    // while they are unknown, and follows them until the user edits
    LaunchedEffect(dnsSettingsViewModel.settings) {
        val current = dnsSettingsViewModel.settings
        if (current != null && (draft == null || draft == original)) {
            draft = current
            original = current
        }
    }

    // this connection's blocked count changes without a status callback, so
    // it is read again, with the status, while the screen shows it
    val countsBlocked = dnsSettingsViewModel.status.countsBlocked
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(countsBlocked, lifecycleOwner) {
        if (countsBlocked) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    dnsSettingsViewModel.refreshBlockedCount()
                    delay(DNS_BLOCKED_COUNT_POLL_MILLIS)
                }
            }
        }
    }

    val isDirty = draft != null && draft != original
    val selection = dnsSettingsViewModel.selection
    // Custom's editor shows while custom settings are in force, or once Custom
    // is tapped
    val customVisible = customOpen || selection?.kind == DNS_PICK_KIND_CUSTOM

    val scope = rememberCoroutineScope()
    val pickerBringIntoView = remember { BringIntoViewRequester() }
    val customBringIntoView = remember { BringIntoViewRequester() }

    // a pick that applies at once closes what another pick opened
    val selectPick: (String) -> Unit = { presetId ->
        customOpen = false
        personalPresetId = null
        dnsSettingsViewModel.selectPick(presetId)
    }
    val openCustom: () -> Unit = {
        customOpen = true
        personalPresetId = null
        scope.launch {
            // once the editor is laid out
            withFrameNanos { }
            customBringIntoView.bringIntoView()
        }
    }
    val openPersonal: (String) -> Unit = { presetId ->
        if (personalPresetId != presetId) {
            customOpen = false
            personalPresetId = presetId
            personalInput = TextFieldValue(dnsSettingsViewModel.personalIdOf(presetId))
            personalErrorId = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.custom_dns),
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
            )
        },
        containerColor = Black
    ) { innerPadding ->

        val currentDraft = draft

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            // the connection region comes from the network space and shows
            // at once; the traffic dns waits for the device's report
            val sections = dnsSettingsSections(currentDraft)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .tabletReadableColumn()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                /**
                 * Connection region (ingress dns): how the app reaches
                 * URnetwork. Separate from the traffic dns below; changing
                 * one never changes the other.
                 */
                if (sections.connectionRegion) {
                    ConnectionRegionSettingsRow()

                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (!sections.trafficDns || currentDraft == null) {
                    return@Column
                }

                Text(
                    stringResource(id = R.string.traffic_dns),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                /**
                 * The selection in force, with the notice of a preset this
                 * release no longer offers
                 */
                if (selection != null) {
                    DnsSelectionPanel(
                        dnsSettingsViewModel = dnsSettingsViewModel,
                        selection = selection,
                        useDefault = { selectPick(DNS_PRESET_ID_DEFAULT) },
                        changeLocation = {
                            // the location picker, which opens from the
                            // connect screen
                            navController.popBackStack(Route.Connect, inclusive = false)
                            navController.navigate(Route.BrowseLocations)
                        },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selection.dropped) {
                        DnsDroppedNotice(
                            name = selection.droppedName,
                            choose = {
                                scope.launch { pickerBringIntoView.bringIntoView() }
                            },
                            enterCustom = openCustom,
                            // dismissing saves URnetwork's default, which is
                            // what applies meanwhile (ADBLOCK3RD A7)
                            dismiss = { selectPick(DNS_PRESET_ID_DEFAULT) },
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                /**
                 * The picks, in the sdk's order and groups. No pick is
                 * preselected or recommended; Custom's editor opens under
                 * Custom.
                 */
                Column(
                    modifier = Modifier.bringIntoViewRequester(pickerBringIntoView)
                ) {
                    for (section in dnsPickerSections(dnsSettingsViewModel.picks)) {
                        DnsPickerCard(title = dnsKeyText(section.groupKey)) {
                            for (pick in section.picks) {
                                val preset = pick.preset
                                val selected = dnsPickSelected(pick, selection)
                                if (pick.kind == DNS_PICK_KIND_PRESET && preset != null) {
                                    DnsPickRow(
                                        // the preset's name; its line names
                                        // the adblock provider, as a personal
                                        // preset's does
                                        label = dnsPresetName(preset.name),
                                        copy = dnsKeyText(pick.copyKey, preset.providerName),
                                        preset = preset,
                                        selected = selected,
                                        select = {
                                            if (preset.personalIdRequired) {
                                                openPersonal(preset.presetId)
                                            } else {
                                                selectPick(preset.presetId)
                                            }
                                        },
                                    )
                                    if (preset.personalIdRequired && personalPresetId == preset.presetId) {
                                        DnsPersonalIdField(
                                            preset = preset,
                                            input = personalInput,
                                            onInputChange = {
                                                personalInput = it
                                                personalErrorId = ""
                                            },
                                            errorId = personalErrorId,
                                            save = {
                                                val errorId = dnsSettingsViewModel.personalIdErrorId(
                                                    preset.presetId,
                                                    personalInput.text,
                                                )
                                                if (errorId.isNotEmpty()) {
                                                    personalErrorId = errorId
                                                } else if (dnsSettingsViewModel.selectPersonalPreset(preset.presetId, personalInput.text)) {
                                                    personalPresetId = null
                                                }
                                            },
                                        )
                                    }
                                } else if (pick.kind == DNS_PICK_KIND_CUSTOM) {
                                    DnsPickRow(
                                        label = dnsKeyText(pick.labelKey) ?: "",
                                        copy = dnsKeyText(pick.copyKey),
                                        preset = null,
                                        selected = selected,
                                        select = openCustom,
                                        modifier = Modifier.bringIntoViewRequester(customBringIntoView),
                                    )
                                } else {
                                    // URnetwork's default, or the exit country's
                                    // regional recommendation, never selected
                                    // for the user
                                    val countryName = dnsSettingsViewModel.countryName(pick.countryCode)
                                    DnsPickRow(
                                        label = dnsKeyText(pick.labelKey, countryName) ?: "",
                                        copy = dnsKeyText(pick.copyKey, countryName),
                                        preset = null,
                                        selected = selected,
                                        select = { selectPick(pick.presetId) },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (customVisible && section.picks.any { it.kind == DNS_PICK_KIND_CUSTOM }) {
                            DnsCustomEditor(
                                draft = currentDraft,
                                onDraftChange = { draft = it },
                                regionalServers = dnsSettingsViewModel.regionalServers,
                                connectedCountryCode = dnsSettingsViewModel.connectedCountryCode,
                            )
                        }
                    }
                }

                /**
                 * Fast DNS on connect, applied at once to the settings in force
                 * with their selection kept. With a preset the connect window
                 * uses URnetwork's dns (ADBLOCK3RD C7).
                 */
                SettingsGroup(title = null) {
                    // no spacers: the switch's 48 dp touch target spaces the
                    // row and the description
                    DnsToggleRow(
                        label = stringResource(id = FastDnsOnConnectToggle.labelRes),
                        detail = null,
                        checked = FastDnsOnConnectToggle.isOn(dnsSettingsViewModel.settings),
                        toggle = {
                            val inForce = dnsSettingsViewModel.settings
                            if (inForce != null) {
                                val enabled = FastDnsOnConnectToggle.toggled(inForce).enableFallback
                                if (dnsSettingsViewModel.setFastDnsOnConnect(enabled)) {
                                    // Custom's editor keeps its edits: the
                                    // settings it was opened on changed only here
                                    draft = draft?.copy(enableFallback = enabled)
                                    original = original?.copy(enableFallback = enabled)
                                }
                            }
                        }
                    )
                    Text(
                        stringResource(id = FastDnsOnConnectToggle.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint
                    )
                    val presetName = dnsSettingsViewModel.selectedPresetName?.let { dnsPresetName(it) }
                    if (presetName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(id = R.string.dns_preset_fast_dns_note, presetName),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                /**
                 * URnetwork's own blocker, beside any dns choice; it answers
                 * first
                 */
                BlockerTogglePanel(
                    enabled = blockerViewModel.blockerEnabled,
                    toggle = {
                        blockerViewModel.setBlockerEnabled(!blockerViewModel.blockerEnabled)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                URLearnMoreText(
                    text = stringResource(id = R.string.dns_preset_apply_note),
                    linkText = stringResource(id = R.string.learn_more),
                    url = DNS_PROVIDERS_HELP_URL,
                )

                Spacer(modifier = Modifier.height(16.dp))

            }

            // pinned below the scrolling content while Custom's editor shows;
            // on a tablet it is exactly as wide as the content column above
            // it, never the whole screen
            if (sections.trafficDns && customVisible) Column(
                modifier = Modifier
                    .tabletReadableColumn()
                    .padding(16.dp)
            ) {
                URButton(
                    onClick = {
                        val edited = draft
                        val editedFrom = original
                        if (edited != null && editedFrom != null) {
                            if (dnsSettingsViewModel.apply(edited, editedFrom)) {
                                original = edited
                            } else {
                                // the settings changed under the editor: start
                                // again from the ones in force
                                draft = dnsSettingsViewModel.settings
                                original = dnsSettingsViewModel.settings
                            }
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

private fun isValidDohUrl(value: String): Boolean {
    return value.startsWith("https://") && 8 < value.length
}

/**
 * The selection in force, at the top of the traffic dns: the pill while the
 * settings differ from the recommended ones, the status line of a preset or
 * else the summary, with the actions of a failing preset, the verification,
 * this connection's blocked count, and for a preset where its lookups go,
 * with its privacy policy and help (ADBLOCK3RD F1, C8, H2).
 */
@Composable
private fun DnsSelectionPanel(
    dnsSettingsViewModel: DnsSettingsViewModel,
    selection: DnsSelectionUi,
    useDefault: () -> Unit,
    changeLocation: () -> Unit,
) {
    val status = dnsSettingsViewModel.status
    val preset = dnsSettingsViewModel.selectedPreset
    val uriHandler = LocalUriHandler.current
    // a preset's status line stands for its summary on this screen; both, and
    // the blocked count, name the preset, while the verification line and
    // its link name the adblock provider
    val headline = dnsKeyText(status.statusKey, dnsPresetName(status.name))
        ?: dnsKeyText(selection.summaryKey, dnsPresetName(selection.name))
    val detail = dnsKeyText(status.statusDetailKey)
    val verification = dnsKeyText(status.verificationKey, status.providerName)
    val dashboardLink = dnsKeyText(status.verificationLinkKey, status.providerName)
        ?.takeIf { status.verificationUrl.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {

        DnsRecommendationPill(dnsSettingsViewModel)

        if (headline != null) {
            Text(
                headline,
                style = MaterialTheme.typography.bodyLarge,
                // the preset is failing: closed, or failed over to URnetwork's dns
                color = if (status.showUseDefault) Amber else Color.White
            )
        }

        if (detail != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        if (verification != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                verification,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        if (status.showUseDefault || status.showChangeLocation || dashboardLink != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (status.showUseDefault) {
                    TextButton(onClick = useDefault) {
                        Text(
                            stringResource(id = R.string.dns_status_use_default),
                            color = Pink
                        )
                    }
                }
                if (status.showChangeLocation) {
                    TextButton(onClick = changeLocation) {
                        Text(
                            stringResource(id = R.string.dns_status_change_location),
                            color = Pink
                        )
                    }
                }
                if (dashboardLink != null) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(status.verificationUrl) } }) {
                        Text(
                            dashboardLink,
                            color = Pink
                        )
                    }
                }
            }
        }

        // none while the status describes another preset, or without a device
        val blockedCount = status.blockedCount
        if (blockedCount != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                pluralStringResource(
                    id = R.plurals.dns_blocked_count,
                    count = blockedCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    blockedCount,
                    dnsPresetName(status.name),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                stringResource(id = R.string.dns_blocked_count_floor_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextFaint
            )
        }

        if (selection.kind == DNS_PICK_KIND_PRESET) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(id = R.string.dns_preset_disclosure, selection.providerName),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            if (preset != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (preset.privacyUrl.isNotEmpty()) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(preset.privacyUrl) } }) {
                            Text(
                                stringResource(id = R.string.dns_preset_privacy_link),
                                color = Pink
                            )
                        }
                    }
                    if (preset.helpUrl.isNotEmpty()) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(preset.helpUrl) } }) {
                            Text(
                                stringResource(id = R.string.dns_preset_help_link),
                                color = Pink
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The notice of a stored preset this release no longer offers (ADBLOCK3RD
 * A7): URnetwork's dns applies in its place, unfiltered. Choose another
 * preset goes to the picks, Enter it yourself opens Custom's editor, and Got
 * it saves URnetwork's default, which ends the notice.
 */
@Composable
private fun DnsDroppedNotice(
    // the dropped preset's name as it showed; empty for a preset this release
    // never knew
    name: DnsPresetNameUi,
    choose: () -> Unit,
    enterCustom: () -> Unit,
    dismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            if (name.key.isNotEmpty()) {
                stringResource(id = R.string.dns_preset_dropped, dnsPresetName(name))
            } else {
                stringResource(id = R.string.dns_preset_error_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Amber
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = choose) {
                Text(
                    stringResource(id = R.string.dns_preset_dropped_choose),
                    color = Pink
                )
            }
            TextButton(onClick = enterCustom) {
                Text(
                    stringResource(id = R.string.dns_preset_dropped_custom),
                    color = Pink
                )
            }
            TextButton(onClick = dismiss) {
                Text(
                    stringResource(id = R.string.got_it),
                    color = Pink
                )
            }
        }
    }
}

/** A card of picks, under its group's heading when it has one. */
@Composable
private fun DnsPickerCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (title != null) {
            Text(
                title,
                style = TextStyle(color = TextMuted)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

/**
 * One pick: its radio, its label and the line under it, and for a preset its
 * jurisdiction and its provider's privacy policy (ADBLOCK3RD O10). Tapping the
 * row picks it.
 */
@Composable
private fun DnsPickRow(
    label: String,
    copy: String?,
    preset: DnsPresetUi?,
    selected: Boolean,
    select: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { select() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = select
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            if (copy != null) {
                Text(
                    copy,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            if (preset != null) {
                DnsPresetFacts(preset)
            }
        }
    }
}

/**
 * A preset's jurisdiction and a link to its provider's privacy policy, which
 * opens without picking the preset.
 */
@Composable
private fun DnsPresetFacts(preset: DnsPresetUi) {
    val jurisdiction = stringResource(id = R.string.dns_preset_jurisdiction, preset.jurisdiction)
    val privacy = stringResource(id = R.string.dns_preset_privacy_link)
    val facts = remember(jurisdiction, privacy, preset.privacyUrl) {
        val linkStyle = SpanStyle(color = Pink)
        buildAnnotatedString {
            append(jurisdiction)
            if (preset.privacyUrl.isNotEmpty()) {
                append(" · ")
                withLink(
                    LinkAnnotation.Url(
                        preset.privacyUrl,
                        styles = TextLinkStyles(style = linkStyle, pressedStyle = linkStyle)
                    )
                ) {
                    append(privacy)
                }
            }
        }
    }
    Text(
        facts,
        style = MaterialTheme.typography.bodySmall,
        color = TextFaint
    )
}

/**
 * The id field of a personal preset (ADBLOCK3RD D3): the provider's own
 * example as its placeholder, where the provider shows the id, its free
 * plan's limit, the block response to keep, why no IP is linked, and Save.
 * The sdk reads the id out of a bare id, the provider's DoH url or its
 * Private DNS hostname, and checks it.
 */
@Composable
private fun DnsPersonalIdField(
    preset: DnsPresetUi,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    // the sdk's error id of the last Save, empty when none
    errorId: String,
    save: () -> Unit,
) {
    val providerName = preset.providerName
    val uriHandler = LocalUriHandler.current
    val errorMessage = dnsKeyText(errorId)
    val formatMessage = if (errorId == DNS_PRESET_ERROR_PERSONAL_ID_INVALID) {
        dnsKeyText(preset.personalIdFormatKey, providerName, preset.personalIdExample)
    } else {
        null
    }
    val where = dnsKeyText(preset.personalIdWhereKey, providerName)
    val quotaNote = dnsKeyText(preset.quotaNoteKey, providerName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        URTextInput(
            value = input,
            onValueChange = onInputChange,
            placeholder = preset.personalIdExample,
            label = stringResource(id = R.string.dns_personal_id_label),
            isValid = errorId.isEmpty(),
        )

        if (errorMessage != null) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextDanger
            )
        }
        if (formatMessage != null) {
            Text(
                formatMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        if (errorMessage != null || formatMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (where != null) {
            URLearnMoreText(
                text = where,
                linkText = stringResource(id = R.string.learn_more),
                url = DNS_PERSONAL_IDS_HELP_URL,
            )
        }
        if (preset.personalIdHelpUrl.isNotEmpty()) {
            TextButton(onClick = { runCatching { uriHandler.openUri(preset.personalIdHelpUrl) } }) {
                Text(
                    stringResource(id = R.string.dns_personal_id_dashboard_link, providerName),
                    color = Pink
                )
            }
        }

        if (quotaNote != null) {
            Text(
                quotaNote,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            stringResource(id = R.string.dns_personal_id_block_response, providerName),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.dns_personal_id_linked_ip_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextFaint
        )

        Spacer(modifier = Modifier.height(12.dp))

        URButton(
            onClick = save,
            enabled = input.text.isNotBlank()
        ) { buttonTextStyle ->
            Text(
                stringResource(id = R.string.save),
                style = buttonTextStyle
            )
        }
    }
}

/**
 * Custom's editor (ADBLOCK3RD F2): today's resolver toggles, the suggested
 * regional servers and the four lists, seeded from the settings in force
 * (DnsResolverSettingsAsCustom). Its changes apply as custom settings
 * together with the update button.
 */
@Composable
private fun DnsCustomEditor(
    draft: DnsSettingsUi,
    onDraftChange: (DnsSettingsUi) -> Unit,
    regionalServers: List<RegionalDnsSuggestionUi>,
    connectedCountryCode: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        // a filtering server beside URnetwork's servers does not filter: the
        // fastest answer wins (ADBLOCK3RD F1)
        Text(
            stringResource(id = R.string.dns_custom_mix_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))

        /**
         * Resolver toggles
         */
        SettingsGroup(title = stringResource(id = R.string.resolvers)) {
            // no spacers: the switches' 48 dp touch targets space the rows
            DnsToggleRow(
                label = stringResource(id = R.string.dns_over_https),
                detail = stringResource(id = R.string.remote_lowercase),
                checked = draft.enableRemoteDoh,
                toggle = { onDraftChange(draft.copy(enableRemoteDoh = !draft.enableRemoteDoh)) }
            )
            DnsToggleRow(
                label = stringResource(id = R.string.dns_over_https),
                detail = stringResource(id = R.string.local_lowercase),
                checked = draft.enableLocalDoh,
                toggle = { onDraftChange(draft.copy(enableLocalDoh = !draft.enableLocalDoh)) }
            )
            DnsToggleRow(
                label = stringResource(id = R.string.unencrypted_dns),
                detail = stringResource(id = R.string.remote_lowercase),
                checked = draft.enableRemoteDns,
                toggle = { onDraftChange(draft.copy(enableRemoteDns = !draft.enableRemoteDns)) }
            )
            DnsToggleRow(
                label = stringResource(id = R.string.unencrypted_dns),
                detail = stringResource(id = R.string.local_lowercase),
                checked = draft.enableLocalDns,
                toggle = { onDraftChange(draft.copy(enableLocalDns = !draft.enableLocalDns)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        /**
         * Suggested regional servers
         */
        if (regionalServers.isNotEmpty()) {

            val sortedServers = remember(regionalServers, connectedCountryCode) {
                regionalServers.sortedWith(
                    compareByDescending<RegionalDnsSuggestionUi> { it.countryCode == connectedCountryCode }
                        .thenBy { it.countryCode }
                        .thenBy { it.name }
                )
            }

            SettingsGroup(title = stringResource(id = R.string.suggested_remote_dns_servers)) {

                // no spacers: the switches' 48 dp touch targets space the rows
                sortedServers.forEach { server ->
                    RegionalServerRow(
                        server = server,
                        isConnectedCountry = server.countryCode == connectedCountryCode,
                        checked = draft.remoteDnsIpv4.contains(server.ipv4),
                        toggle = {
                            onDraftChange(
                                if (draft.remoteDnsIpv4.contains(server.ipv4)) {
                                    draft.copy(
                                        remoteDnsIpv4 = draft.remoteDnsIpv4 - server.ipv4
                                    )
                                } else {
                                    // adding a suggestion also enables remote dns
                                    draft.copy(
                                        remoteDnsIpv4 = draft.remoteDnsIpv4 + server.ipv4,
                                        enableRemoteDns = true
                                    )
                                }
                            )
                        }
                    )
                }

                // the switch's 48 dp touch target fills the rest
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    stringResource(id = R.string.suggested_remote_dns_servers_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        /**
         * Server lists
         */
        ServerListGroup(
            title = stringResource(id = R.string.remote_doh_urls),
            ipv4 = draft.remoteDohUrlsIpv4,
            ipv6 = draft.remoteDohUrlsIpv6,
            validate = ::isValidDohUrl,
            placeholder = "https://",
            onChange = { v4, v6 ->
                onDraftChange(draft.copy(remoteDohUrlsIpv4 = v4, remoteDohUrlsIpv6 = v6))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ServerListGroup(
            title = stringResource(id = R.string.local_doh_urls),
            ipv4 = draft.localDohUrlsIpv4,
            ipv6 = draft.localDohUrlsIpv6,
            validate = ::isValidDohUrl,
            placeholder = "https://",
            onChange = { v4, v6 ->
                onDraftChange(draft.copy(localDohUrlsIpv4 = v4, localDohUrlsIpv6 = v6))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ServerListGroup(
            title = stringResource(id = R.string.remote_dns_servers),
            ipv4 = draft.remoteDnsIpv4,
            ipv6 = draft.remoteDnsIpv6,
            validate = ::isIpAddressValue,
            placeholder = stringResource(id = R.string.ip_address),
            onChange = { v4, v6 ->
                onDraftChange(draft.copy(remoteDnsIpv4 = v4, remoteDnsIpv6 = v6))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ServerListGroup(
            title = stringResource(id = R.string.local_dns_servers),
            ipv4 = draft.localDnsIpv4,
            ipv6 = draft.localDnsIpv6,
            validate = ::isIpAddressValue,
            placeholder = stringResource(id = R.string.ip_address),
            onChange = { v4, v6 ->
                onDraftChange(draft.copy(localDnsIpv4 = v4, localDnsIpv6 = v6))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsGroup(
    // the heading over the card, or none
    title: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (title != null) {
            Text(
                title,
                style = TextStyle(color = TextMuted)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
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

@Composable
private fun DnsToggleRow(
    label: String,
    detail: String?,
    checked: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (detail != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        URSwitch(
            checked = checked,
            toggle = toggle
        )
    }
}

@Composable
private fun RegionalServerRow(
    server: RegionalDnsSuggestionUi,
    isConnectedCountry: Boolean,
    checked: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (isConnectedCountry) {
                // suggested for the connected country
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = countryColor(server.countryCode),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        server.countryCode.uppercase(),
                        style = TextStyle(fontSize = 10.sp),
                        color = TextFaint
                    )
                }
                Text(
                    server.ipv4,
                    style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }

        }

        URSwitch(
            checked = checked,
            toggle = toggle
        )

    }
}

private fun countryColor(countryCode: String): Color {
    return try {
        Color(android.graphics.Color.parseColor("#${Sdk.getColorHex(countryCode)}"))
    } catch (e: Throwable) {
        TextMuted
    }
}

@Composable
private fun ServerListGroup(
    title: String,
    ipv4: List<String>,
    ipv6: List<String>,
    validate: (String) -> Boolean,
    placeholder: String,
    onChange: (List<String>, List<String>) -> Unit,
) {
    SettingsGroup(title = title) {

        EditableValueList(
            label = stringResource(id = R.string.ipv4),
            values = ipv4,
            validate = validate,
            placeholder = placeholder,
            onChange = { onChange(it, ipv6) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        EditableValueList(
            label = stringResource(id = R.string.ipv6),
            values = ipv6,
            validate = validate,
            placeholder = placeholder,
            onChange = { onChange(ipv4, it) }
        )

    }
}

/**
 * An editable list of string values with inline add and remove
 */
@Composable
private fun EditableValueList(
    label: String,
    values: List<String>,
    validate: (String) -> Boolean,
    placeholder: String,
    onChange: (List<String>) -> Unit,
) {

    var newValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    val trimmed = newValue.text.trim()
    val canAdd = validate(trimmed) && !values.contains(trimmed)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextFaint
        )

        for (value in values) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(id = R.string.remove),
                    tint = TextFaint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            onChange(values - value)
                        }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(modifier = Modifier.weight(1f)) {
                URTextInput(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = placeholder,
                    label = null,
                )
            }

            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(id = R.string.add),
                tint = if (canAdd) Green else TextFaint,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canAdd) {
                        onChange(values + trimmed)
                        newValue = TextFieldValue("")
                    }
            )

        }

    }
}
