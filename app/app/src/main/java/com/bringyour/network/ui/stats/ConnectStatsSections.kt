package com.bringyour.network.ui.stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.MutedCoral
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/**
 * The statistics sections in the connect sheet: client statistics,
 * local statistics, and custom dns
 */
@Composable
fun ConnectStatsSections(
    navController: NavController,
    throughputViewModel: ThroughputViewModel,
    blockActionsViewModel: BlockActionsViewModel,
    dnsSettingsViewModel: DnsSettingsViewModel,
    blockerViewModel: BlockerViewModel,
) {

    /**
     * Client statistics: remote and blocked traffic.
     * Tap to open the client contract details.
     */
    StatsCard(
        title = stringResource(id = R.string.client_statistics),
        onClick = {
            navController.navigate(Route.ContractStats(provider = false))
        }
    ) {

        TransferChart(
            points = throughputViewModel.clientPoints,
            route = ThroughputRoute.REMOTE,
            title = stringResource(id = R.string.remote),
            windowSeconds = throughputViewModel.windowSeconds,
        )

        Spacer(modifier = Modifier.height(12.dp))

        /**
         * The remote traffic of the window by transport, full width under
         * the remote plot. Tap to open the transport settings. The child tap
         * wins over the card's tap.
         */
        TransportDistributionBar(
            distribution = throughputViewModel.clientTransportDistribution,
            onClick = {
                navController.navigate(Route.TransportSettings(provider = false))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        TransferChart(
            points = throughputViewModel.clientPoints,
            route = ThroughputRoute.BLOCK,
            title = stringResource(id = R.string.blocked),
            windowSeconds = throughputViewModel.windowSeconds,
            byteColor = Red,
            packetColor = MutedCoral,
        )

    }

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * App split rules: its own touch target above the local statistics.
     * Opens the app split rules. The active apps (included when any exist,
     * else excluded) show as an overlapping icon deck next to the label.
     */
    val includedAppIds = blockActionsViewModel.tunnelIncludedAppIds
    val excludedAppIds = blockActionsViewModel.tunnelExcludedAppIds
    AppSplitRulesPanel(
        includedCount = includedAppIds.size,
        excludedCount = excludedAppIds.size,
        activeAppIds = if (includedAppIds.isNotEmpty()) includedAppIds else excludedAppIds,
        onClick = {
            navController.navigate(Route.AppSplitRules)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * Local statistics: traffic routed to the local network.
     * Tap to open the split rules.
     */
    StatsCard(
        title = stringResource(id = R.string.local_statistics),
        onClick = {
            navController.navigate(Route.SplitRules)
        }
    ) {

        TransferChart(
            points = throughputViewModel.clientPoints,
            route = ThroughputRoute.LOCAL,
            title = stringResource(id = R.string.local),
            windowSeconds = throughputViewModel.windowSeconds,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            pluralStringResource(
                id = R.plurals.split_rule_count,
                count = blockActionsViewModel.splitRules.size,
                blockActionsViewModel.splitRules.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

    }

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * Custom dns summary. Tap to open the dns settings.
     */
    StatsCard(
        title = stringResource(id = R.string.custom_dns),
        onClick = {
            navController.navigate(Route.DnsSettings)
        }
    ) {

        // sits at the top of the card, above the status rows, when the applied
        // settings differ from the recommended ones
        DnsRecommendationPill(dnsSettingsViewModel)

        val settings = dnsSettingsViewModel.settings

        if (settings != null) {
            DnsStatusRow(stringResource(id = R.string.dns_over_https), settings.dohEnabled)
            Spacer(modifier = Modifier.height(8.dp))
            DnsStatusRow(stringResource(id = R.string.unencrypted_dns), settings.unencryptedDnsEnabled)
            Spacer(modifier = Modifier.height(8.dp))
            DnsStatusRow(stringResource(id = R.string.local_dns), settings.localDnsEnabled)
            Spacer(modifier = Modifier.height(8.dp))
            DnsStatusRow(stringResource(id = R.string.local_dns_fallback), settings.localDnsFallbackEnabled)
        } else {
            Text(
                stringResource(id = R.string.dns_settings_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint
            )
        }

    }

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * Ad and tracker blocker toggle. The device applies it immediately and
     * persists it to local settings.
     */
    BlockerTogglePanel(
        enabled = blockerViewModel.blockerEnabled,
        toggle = {
            blockerViewModel.setBlockerEnabled(!blockerViewModel.blockerEnabled)
        }
    )
}

/**
 * The ad and tracker blocker toggle, as its own panel under the custom dns
 * summary.
 */
@Composable
fun BlockerTogglePanel(
    enabled: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.block_ads_and_trackers),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        URSwitch(
            checked = enabled,
            toggle = toggle
        )
    }
}

/**
 * "X apps excluded" or "X apps included", as its own tappable panel.
 * Inclusions take precedence. Tap to open the app split rules. The active
 * apps show as an overlapping icon deck next to the label.
 */
@Composable
fun AppSplitRulesPanel(
    includedCount: Int,
    excludedCount: Int,
    activeAppIds: List<String>,
    onClick: () -> Unit,
) {

    val text = when {
        0 < includedCount -> pluralStringResource(
            id = R.plurals.apps_included_count,
            count = includedCount,
            includedCount,
        )
        0 < excludedCount -> pluralStringResource(
            id = R.plurals.apps_excluded_count,
            count = excludedCount,
            excludedCount,
        )
        // no rules configured yet: a generic starter label rather than "0 apps excluded"
        else -> stringResource(R.string.app_split_starter)
    }

    val icons = rememberAppIcons(activeAppIds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.width(12.dp))

        AppIconDeck(
            icons = icons,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * A horizontal row of app icons that collapses into an overlapping deck
 * (leftmost on top) when the icons are wider than the available width.
 */
@Composable
private fun AppIconDeck(
    icons: List<ImageBitmap>,
    modifier: Modifier = Modifier,
) {
    if (icons.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    val iconSize = 22.dp
    val ring = 1.dp

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val cellPx = with(density) { (iconSize + ring * 2).toPx() }
        val fullGapPx = with(density) { 4.dp.toPx() }
        val fullStepPx = cellPx + fullGapPx
        val n = icons.size

        // step between icon left edges: full spacing when they fit, else
        // overlap to fit within the available width (floored so they never
        // fully collapse)
        val stepPx = if (n <= 1 || n * fullStepPx <= maxWidthPx) {
            fullStepPx
        } else {
            ((maxWidthPx - cellPx) / (n - 1)).coerceAtLeast(cellPx * 0.4f)
        }

        // how many actually fit; the rest are dropped
        val maxVisible = if (stepPx <= 0f) {
            1
        } else {
            (((maxWidthPx - cellPx) / stepPx).toInt() + 1).coerceIn(1, n)
        }
        val visible = icons.take(maxVisible)

        Box {
            visible.forEachIndexed { index, icon ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset((index * stepPx).toInt(), 0) }
                        // leftmost on top
                        .zIndex((n - index).toFloat())
                        .size(iconSize + ring * 2)
                        .background(MainTintedBackgroundBase, RoundedCornerShape(7.dp))
                        .padding(ring)
                ) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

/**
 * Loads the app icons for the given package names off the main thread.
 */
@Composable
private fun rememberAppIcons(packageNames: List<String>): List<ImageBitmap> {
    val context = LocalContext.current
    val key = packageNames.joinToString(",")
    val icons by produceState(initialValue = emptyList<ImageBitmap>(), key) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packageNames.mapNotNull { packageName ->
                runCatching {
                    pm.getApplicationIcon(packageName).toBitmap(64, 64).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return icons
}

/**
 * A small pill at the top of the custom dns card, shown when the applied dns
 * settings differ from the recommended ones.
 *
 * A connected country with a known-better regional recommendation takes
 * precedence: the pill reads "unapplied recommended settings for {country}" and
 * carries that country's color dot. Otherwise, when the safe defaults are not
 * applied, it reads "the default safe settings are not applied" with no dot.
 * Renders nothing once the applied settings already match the best
 * recommendation. This condenses the recommendation logic on the dns settings
 * screen into a single glanceable pill.
 *
 * Kept in its own composable so it only recomposes when the dns settings change,
 * rather than with the rest of the card.
 */
@Composable
private fun DnsRecommendationPill(
    dnsSettingsViewModel: DnsSettingsViewModel,
) {
    // the currently applied settings; no pill until they load
    val current = dnsSettingsViewModel.settings ?: return

    val message: String
    val countryCode: String?

    val recommended = dnsSettingsViewModel.recommendedSettings
    if (recommended != null) {
        // a connected country with a known-better regional recommendation
        if (current == recommended) return // already on the regional recommendation
        val code = dnsSettingsViewModel.connectedCountryCode
        val name = dnsSettingsViewModel.connectedCountryName
            ?: code?.uppercase()
            ?: stringResource(id = R.string.this_region)
        message = stringResource(id = R.string.dns_pill_recommended, name)
        countryCode = code
    } else {
        // otherwise nudge toward the safe defaults when they are not applied
        val defaults = dnsSettingsViewModel.defaultSettings ?: return
        if (current == defaults) return
        message = stringResource(id = R.string.dns_pill_defaults)
        countryCode = null
    }

    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .background(
                MutedCoral.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // a regional recommendation dots the pill with the connected country color
        if (countryCode != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = countryColor(countryCode),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            message,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

/**
 * The color associated with a country code, falling back to a muted gray when
 * the sdk has no color or the hex is unparseable.
 */
private fun countryColor(countryCode: String): Color {
    return try {
        Color(android.graphics.Color.parseColor("#${Sdk.getColorHex(countryCode)}"))
    } catch (e: Throwable) {
        TextMuted
    }
}

@Composable
private fun DnsStatusRow(
    label: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (enabled) Green else TextFaint.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        Text(
            stringResource(id = if (enabled) R.string.on else R.string.off),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Green else TextMuted
        )
    }
}

@Composable
private fun StatsCard(
    title: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = TextStyle(color = TextMuted)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        content()
    }
}
