package com.bringyour.network.ui.stats

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

private data class AppRuleEditorTarget(
    val packageName: String,
    val label: String,
    // null when creating
    val ruleId: String?,
    val mode: AppRuleMode,
)

/**
 * App split rules: all installed apps, with the overridden apps pinned
 * on top showing their included or local state. Tapping an app opens
 * a dialog to exclude it from the vpn or force it through the vpn.
 * Inclusions take precedence over exclusions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSplitRulesScreen(
    navController: NavController,
    blockActionsViewModel: BlockActionsViewModel = hiltViewModel(),
    installedAppsViewModel: InstalledAppsViewModel = hiltViewModel(),
) {

    var editorTarget by remember { mutableStateOf<AppRuleEditorTarget?>(null) }
    val context = LocalContext.current

    val appRules = blockActionsViewModel.appRules
    val appsByPackage = remember(installedAppsViewModel.installedApps) {
        installedAppsViewModel.installedApps.associateBy { it.packageName }
    }
    val ruledPackages = remember(appRules) {
        appRules.map { it.appId }.toSet()
    }
    val unruledApps = remember(installedAppsViewModel.installedApps, ruledPackages) {
        installedAppsViewModel.installedApps.filter { it.packageName !in ruledPackages }
    }

    // inclusions take precedence: when any app is included the tunnel runs in
    // allowlist mode and the exclude rules have no distinct effect
    val includeMode = blockActionsViewModel.tunnelIncludedAppIds.isNotEmpty()
    val excludeMode = !includeMode && blockActionsViewModel.tunnelExcludedAppIds.isNotEmpty()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.app_split_rules),
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

            if (installedAppsViewModel.isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = TextMuted
                    )
                }

            } else {

                PullToRefreshBox(
                    isRefreshing = installedAppsViewModel.isRefreshing,
                    onRefresh = { installedAppsViewModel.refresh() }
                ) {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        /**
                         * Summary of the active behavior
                         */
                        item(key = "summary") {
                            AppSplitSummary(
                                includeMode = includeMode,
                                excludeMode = excludeMode,
                            )
                        }

                        /**
                         * Pinned app rules
                         */
                        if (appRules.isNotEmpty()) {
                            item(key = "rules-header") {
                                Text(
                                    stringResource(id = R.string.rules),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            itemsIndexed(
                                appRules,
                                key = { index, rule ->
                                    indexedLazyListKey(
                                        "app-rule",
                                        index,
                                        "${rule.id}:${rule.appId}"
                                    )
                                }
                            ) { _, rule ->
                                val app = appsByPackage[rule.appId]
                                // an exclude rule has no effect while include
                                // mode is active, so render it muted
                                val ruleActive = rule.mode != AppRuleMode.EXCLUDED || !includeMode
                                SwipeToRevealRow(
                                    onDelete = { blockActionsViewModel.removeAppRule(rule.id) }
                                ) {
                                    AppRow(
                                        label = app?.label ?: rule.appId,
                                        packageName = rule.appId,
                                        icon = app?.icon,
                                        trailing = {
                                            StateChip(
                                                // an excluded app routes locally: same Local language
                                                // as the split rules screen. a pinned app is in the
                                                // tunnel like any other, just held to one exit
                                                text = stringResource(
                                                    id = when (rule.mode) {
                                                        AppRuleMode.INCLUDED -> R.string.included
                                                        AppRuleMode.EXCLUDED -> R.string.local
                                                        AppRuleMode.PINNED -> R.string.pinned
                                                    }
                                                ),
                                                color = if (ruleActive) {
                                                    when (rule.mode) {
                                                        AppRuleMode.INCLUDED -> BlueMedium
                                                        AppRuleMode.EXCLUDED -> Green
                                                        AppRuleMode.PINNED -> BlueMedium
                                                    }
                                                } else {
                                                    TextMuted
                                                },
                                                highlighted = ruleActive
                                            )
                                        },
                                        onClick = {
                                            editorTarget = AppRuleEditorTarget(
                                                packageName = rule.appId,
                                                label = app?.label ?: rule.appId,
                                                ruleId = rule.id,
                                                mode = rule.mode,
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        /**
                         * All installed apps
                         */
                        item(key = "apps-header") {
                            Text(
                                stringResource(id = R.string.apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        itemsIndexed(
                            unruledApps,
                            key = { index, app ->
                                indexedLazyListKey("app", index, app.packageName)
                            }
                        ) { _, app ->
                            AppRow(
                                label = app.label,
                                packageName = app.packageName,
                                icon = app.icon,
                                trailing = {},
                                onClick = {
                                    editorTarget = AppRuleEditorTarget(
                                        packageName = app.packageName,
                                        label = app.label,
                                        ruleId = null,
                                        mode = AppRuleMode.EXCLUDED,
                                    )
                                }
                            )
                        }

                    }

                }

            }

        }
    }

    /**
     * exclude / include dialog
     */
    editorTarget?.let { target ->
        AppRuleDialog(
            target = target,
            onCreate = { mode ->
                blockActionsViewModel.createAppRule(target.packageName, mode)
                Toast.makeText(
                    context,
                    context.getString(
                        when (mode) {
                            AppRuleMode.INCLUDED -> R.string.app_included_toast
                            AppRuleMode.EXCLUDED -> R.string.app_excluded_toast
                            AppRuleMode.PINNED -> R.string.app_pinned_toast
                        },
                        target.label
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                editorTarget = null
            },
            onUpdate = { mode ->
                target.ruleId?.let {
                    blockActionsViewModel.updateAppRule(it, mode)
                }
                editorTarget = null
            },
            onRemove = {
                target.ruleId?.let {
                    blockActionsViewModel.removeAppRule(it)
                }
                editorTarget = null
            },
            onDismiss = {
                editorTarget = null
            }
        )
    }
}

/**
 * The active app-split behavior, and the include-precedence note.
 */
@Composable
private fun AppSplitSummary(
    includeMode: Boolean,
    excludeMode: Boolean,
) {
    val statusColor = when {
        includeMode -> BlueMedium
        excludeMode -> Green
        else -> TextMuted
    }
    val statusText = stringResource(
        id = when {
            includeMode -> R.string.app_split_active_include
            excludeMode -> R.string.app_split_active_exclude
            else -> R.string.app_split_active_none
        }
    )

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

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = statusColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            stringResource(id = R.string.app_split_precedence_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Box(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                packageName,
                style = TextStyle(fontSize = 11.sp),
                color = TextFaint
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        trailing()

    }
}

/**
 * Choose whether the app is excluded from the vpn or forced through it
 */
@Composable
private fun AppRuleDialog(
    target: AppRuleEditorTarget,
    onCreate: (AppRuleMode) -> Unit,
    onUpdate: (AppRuleMode) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {

    var mode by remember { mutableStateOf(target.mode) }
    val isEditing = target.ruleId != null

    URDialog(
        visible = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                target.label,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.padding(4.dp))

            Text(
                target.packageName,
                style = TextStyle(fontSize = 11.sp),
                color = TextFaint
            )

            Spacer(modifier = Modifier.padding(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mode = AppRuleMode.EXCLUDED },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == AppRuleMode.EXCLUDED,
                    onClick = { mode = AppRuleMode.EXCLUDED }
                )
                Column {
                    Text(
                        stringResource(id = R.string.exclude_app),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        stringResource(id = R.string.exclude_app_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.padding(4.dp))

            // pin: the app stays in the tunnel, but all of its traffic is
            // held to one exit so its api and cdn share an egress ip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mode = AppRuleMode.PINNED },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == AppRuleMode.PINNED,
                    onClick = { mode = AppRuleMode.PINNED }
                )
                Column {
                    Text(
                        stringResource(id = R.string.pin_app),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        stringResource(id = R.string.pin_app_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.padding(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mode = AppRuleMode.INCLUDED },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == AppRuleMode.INCLUDED,
                    onClick = { mode = AppRuleMode.INCLUDED }
                )
                Column {
                    Text(
                        stringResource(id = R.string.include_app),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        stringResource(id = R.string.include_app_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.padding(12.dp))

            URButton(
                onClick = {
                    if (isEditing) {
                        onUpdate(mode)
                    } else {
                        onCreate(mode)
                    }
                }
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = if (isEditing) R.string.update else R.string.create),
                    style = buttonTextStyle
                )
            }

            if (isEditing) {
                Spacer(modifier = Modifier.padding(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onRemove) {
                        Text(
                            stringResource(id = R.string.remove_rule),
                            color = Red
                        )
                    }
                }
            }

        }
    }
}
