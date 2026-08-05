package com.bringyour.network.ui.stats

import android.icu.text.RelativeDateTimeFormatter
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.formatByteCountCompact
import kotlinx.coroutines.launch

private data class RuleEditorTarget(
    val candidates: List<String>,
    val selected: Set<String>,
    val ruleId: String?,
)

/**
 * Live routing decisions with the split rules pinned on top.
 *
 * Tapping a block action opens the rule editor to add the action's
 * host values as a local exception. Tapping an action whose decision
 * came from a rule edits that rule. While the list is scrolled away
 * from the top it holds its position; new items collect behind a
 * "new" chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitRulesScreen(
    navController: NavController,
    blockActionsViewModel: BlockActionsViewModel = hiltViewModel(),
) {

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var editorTarget by remember { mutableStateOf<RuleEditorTarget?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val actions = blockActionsViewModel.blockActions
    val rules = blockActionsViewModel.splitRules

    // "at the top" tracks the user's intent, not the raw first-visible index: a
    // prepend bumps firstVisibleItemIndex to 1 (Compose anchors to the old first
    // item's key), so a raw index==0 check would read false exactly when we need to
    // stay pinned. Update it only from a user-driven scroll (isScrollInProgress); a
    // prepend never flips it off the top. General principle for the app's lists.
    var atTop by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (index, offset, scrolling) ->
            if (scrolling) {
                atTop = index == 0 && offset == 0
            }
        }
    }

    // the newest action id seen while at the top. items ahead of it are "new"
    var topSeenActionId by remember { mutableStateOf<String?>(null) }

    // while at the top, keep the list pinned to the very top as new rows prepend and
    // advance the "seen" marker so nothing counts as pending. scrollToItem is an
    // instant jump (no isScrollInProgress), so it can't feed back into `atTop`.
    LaunchedEffect(atTop, actions.firstOrNull()?.id) {
        if (atTop) {
            topSeenActionId = actions.firstOrNull()?.id
            listState.scrollToItem(0)
        }
    }

    val pendingCount = remember(actions, topSeenActionId) {
        val seenId = topSeenActionId
        if (seenId == null) {
            0
        } else {
            val index = actions.indexOfFirst { it.id == seenId }
            if (index < 0) actions.size else index
        }
    }

    val openEditor = { action: BlockActionUi ->
        val rule = blockActionsViewModel.splitRule(action.overrideId)
        editorTarget = if (rule != null) {
            // edit the rule that decided this action
            val candidates = (rule.hosts + action.hostValues).distinct()
            RuleEditorTarget(
                candidates = candidates,
                selected = rule.hosts.toSet(),
                ruleId = rule.id,
            )
        } else {
            // create a rule from the action's host values, all initially UNSELECTED:
            // the common case is picking one or a few server names, so pre-selecting
            // everything just makes the user uncheck the rest
            RuleEditorTarget(
                candidates = action.hostValues,
                selected = emptySet(),
                ruleId = null,
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.split_rules),
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

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {

                /**
                 * Info: how exclusions work
                 */
                item(key = "info") {
                    SplitRulesInfoCard()
                }

                /**
                 * Pinned split rules
                 */
                item(key = "rules-header") {
                    SectionHeader(stringResource(id = R.string.rules))
                }

                if (rules.isEmpty()) {
                    item(key = "rules-empty") {
                        Text(
                            stringResource(id = R.string.split_rules_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextFaint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        rules,
                        key = { index, rule ->
                            indexedLazyListKey("split-rule", index, rule.id)
                        }
                    ) { _, rule ->
                        SwipeToRevealRow(
                            onDelete = { blockActionsViewModel.removeRule(rule.id) }
                        ) {
                            SplitRuleRow(
                                rule = rule,
                                onClick = {
                                    editorTarget = RuleEditorTarget(
                                        candidates = rule.hosts,
                                        selected = rule.hosts.toSet(),
                                        ruleId = rule.id,
                                    )
                                }
                            )
                        }
                    }
                }

                /**
                 * Live block actions
                 */
                item(key = "activity-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(stringResource(id = R.string.activity))
                        if (0 < blockActionsViewModel.allowedCount || 0 < blockActionsViewModel.blockedCount) {
                            Text(
                                stringResource(
                                    id = R.string.allowed_blocked_counts,
                                    blockActionsViewModel.allowedCount,
                                    blockActionsViewModel.blockedCount
                                ),
                                style = TextStyle(fontSize = 11.sp),
                                color = TextFaint
                            )
                        }
                    }
                }

                if (actions.isEmpty()) {
                    item(key = "activity-empty") {
                        Text(
                            stringResource(id = R.string.split_rules_activity_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextFaint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        actions,
                        key = { index, action ->
                            indexedLazyListKey("block-action", index, action.id)
                        }
                    ) { _, action ->
                        BlockActionRow(
                            action = action,
                            onClick = { openEditor(action) }
                        )
                    }
                }

            }

            /**
             * new items chip
             */
            if (!atTop && 0 < pendingCount) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(BlueMedium, shape = RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pluralStringResource(
                            id = R.plurals.new_items_count,
                            count = pendingCount,
                            pendingCount,
                        ),
                        style = TextStyle(fontSize = 12.sp),
                        color = Color.White
                    )
                }
            }

        }
    }

    /**
     * create / edit rule sheet
     */
    editorTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { editorTarget = null },
            sheetState = editorSheetState,
            containerColor = SheetBlack
        ) {
            SplitRuleEditor(
                target = target,
                onCreate = { hosts ->
                    blockActionsViewModel.createLocalRule(hosts)
                    editorTarget = null
                },
                onUpdate = { ruleId, hosts ->
                    blockActionsViewModel.updateRule(ruleId, hosts)
                    editorTarget = null
                },
                onRemove = { ruleId ->
                    blockActionsViewModel.removeRule(ruleId)
                    editorTarget = null
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * Informational card, styled like the app-split summary card, explaining that
 * exclusions apply to the whole co-associated network cluster.
 */
@Composable
private fun SplitRulesInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            stringResource(id = R.string.split_rules_info_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

// A split rule (override) row: all of the rule's host base names and exact ips as
// green chips, with the Local state chip trailing.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitRuleRow(
    rule: SplitRuleUi,
    onClick: () -> Unit,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {

        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (name in rule.hostBaseNames) {
                RuleChip(text = name, matched = true)
            }
            for (ip in rule.ipValues) {
                RuleChip(text = ip, matched = true)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        StateChip(
            text = stringResource(id = R.string.local),
            color = Green,
            highlighted = true
        )

    }
}

// A block-action row. Chips, in order: the exact hosts/ips an override matched
// (green), then the remaining hosts collapsed to base names (white outline), then a
// single "X IPs" pill for the remaining ips. The block/route state chips trail.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockActionRow(
    action: BlockActionUi,
    onClick: () -> Unit,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // green: the exact hosts and ips that matched an override
                for (name in action.matchedHosts) {
                    RuleChip(text = name, matched = true)
                }
                for (ip in action.matchedIps) {
                    RuleChip(text = ip, matched = true)
                }
                // white: the rest of the hosts as collapsed base names
                for (name in action.hostBaseNames) {
                    RuleChip(text = name, matched = false)
                }
                // white: a single count pill for the rest of the ips
                if (0 < action.ipCount) {
                    IpsPill(count = action.ipCount)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                Text(
                    relativeTime(action.timeMillis),
                    style = TextStyle(fontSize = 11.sp),
                    color = TextFaint
                )
                if (0 < action.byteCount) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        formatByteCountCompact(action.byteCount),
                        style = TextStyle(fontSize = 11.sp),
                        color = TextFaint
                    )
                }
                // which exit(s) carry this cluster right now. One id is the
                // healthy shape; two on one row is a site split across
                // egress IPs, live
                for (exitId in action.exitShortIds) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(id = R.string.via_exit, exitId),
                        style = TextStyle(fontSize = 11.sp),
                        color = BlueMedium
                    )
                }
            }

        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            StateChip(
                text = stringResource(id = if (action.block) R.string.blocked else R.string.allowed),
                color = if (action.block) Red else TextMuted,
                highlighted = action.hasBlockOverride
            )

            Spacer(modifier = Modifier.height(6.dp))

            StateChip(
                text = stringResource(id = if (action.local) R.string.local else R.string.remote),
                color = if (action.local) Green else TextMuted,
                highlighted = action.hasRouteOverride
            )
        }

    }
}

// A host/ip chip: a rounded outline box. matched reads green (an override hit it);
// otherwise a subtle white outline box.
@Composable
private fun RuleChip(text: String, matched: Boolean) {
    Text(
        text,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        color = if (matched) Green else Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (matched) Green else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// The single "X IPs" pill for the unmatched ips: a count, never the ips.
@Composable
private fun IpsPill(count: Int) {
    Text(
        pluralStringResource(id = R.plurals.ip_count, count = count, count),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        color = TextMuted,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun StateChip(
    text: String,
    color: Color,
    highlighted: Boolean,
) {
    Text(
        text,
        style = TextStyle(fontSize = 10.sp),
        color = if (highlighted) Black else color,
        modifier = Modifier
            .background(
                color = if (highlighted) color else color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

private fun relativeTime(timeMillis: Long): String {
    // the platform formatters localize for every locale; under 5s reads as "now"
    val now = System.currentTimeMillis()
    if (now - timeMillis < 5_000) {
        return RelativeDateTimeFormatter.getInstance()
            .format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
    }
    return DateUtils.getRelativeTimeSpanString(
        timeMillis,
        now,
        DateUtils.SECOND_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/**
 * Create or edit a split rule: select the host values to route locally
 */
@Composable
private fun SplitRuleEditor(
    target: RuleEditorTarget,
    onCreate: (List<String>) -> Unit,
    onUpdate: (String, List<String>) -> Unit,
    onRemove: (String) -> Unit,
) {

    var selected by remember { mutableStateOf(target.selected) }
    val isEditing = target.ruleId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        Text(
            stringResource(id = if (isEditing) R.string.edit_split_rule else R.string.new_split_rule),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            stringResource(id = R.string.split_rule_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(
                target.candidates,
                key = { index, host ->
                    indexedLazyListKey("rule-candidate", index, host)
                }
            ) { _, host ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (selected.contains(host)) {
                                selected - host
                            } else {
                                selected + host
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected.contains(host),
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + host else selected - host
                        }
                    )
                    Text(
                        host,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        URButton(
            onClick = {
                val hosts = target.candidates.filter { selected.contains(it) }
                if (isEditing) {
                    onUpdate(target.ruleId!!, hosts)
                } else {
                    onCreate(hosts)
                }
            },
            enabled = isEditing || selected.isNotEmpty()
        ) { buttonTextStyle ->
            Text(
                stringResource(id = if (isEditing) R.string.update else R.string.create),
                style = buttonTextStyle
            )
        }

        if (isEditing) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { onRemove(target.ruleId!!) }) {
                    Text(
                        stringResource(id = R.string.remove_rule),
                        color = Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

    }
}
