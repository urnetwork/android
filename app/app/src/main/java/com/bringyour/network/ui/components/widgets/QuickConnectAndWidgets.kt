package com.bringyour.network.ui.components.widgets

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon as SystemIcon
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.QuickConnectTileService
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.formatByteRate
import com.bringyour.network.utils.formatPacketRate
import com.bringyour.network.widgets.ContractsWidgetReceiver
import com.bringyour.network.widgets.DashboardWidgetReceiver
import com.bringyour.network.widgets.ProviderGlobeWidgetReceiver
import com.bringyour.network.widgets.WidgetEntry
import com.bringyour.network.widgets.WidgetTheme
import com.bringyour.network.widgets.render.BalanceBarRenderer
import com.bringyour.network.widgets.render.ContractStackRenderer
import com.bringyour.network.widgets.render.GlobeBitmapRenderer
import com.bringyour.network.widgets.render.ThroughputChartRenderer
import com.bringyour.network.widgets.requestPinWidget

/**
 * The quick connect tile and the home screen widgets, with the system flow
 * that adds each one: the Quick Settings add-tile request (Android 13+) and
 * the launcher's pin dialog per widget, with the manual steps spelled out
 * where a device has no such flow. The previews are drawn from sample data
 * by the widgets' own renderers, so they look like the real thing.
 *
 * One body for two hosts: the last onboarding page and Account > Widgets
 * render exactly this, so the two can never drift apart.
 */
@Composable
fun QuickConnectAndWidgets(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tileAdded by remember { mutableStateOf(QuickConnectTileService.isAdded(context)) }
    val tileAddedMessage = stringResource(id = R.string.widget_quick_tile_added)
    val canRequestTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val canPinWidgets = remember {
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }
    val sample = remember { WidgetEntry.sample() }

    val requestTile = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBar = context.getSystemService(StatusBarManager::class.java)
            statusBar?.requestAddTileService(
                ComponentName(context, QuickConnectTileService::class.java),
                context.getString(R.string.app_name),
                SystemIcon.createWithResource(context, R.drawable.ic_tile_quick_on),
                context.mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    QuickConnectTileService.setAdded(context, true)
                    QuickConnectTileService.requestUpdate(context)
                    tileAdded = true
                    Toast.makeText(context, tileAddedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier) {

        Text(
            stringResource(id = R.string.intro_quick_connect_details),
            style = NeueBitLargeTextStyle,
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(32.dp))

        /**
         * The quick connect tile: both states, then the way to add it
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            QuickTilePreview(connected = true)
            QuickTilePreview(connected = false)
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            tileAdded -> {
                AddedChip(text = stringResource(id = R.string.intro_quick_tile_added))
            }
            canRequestTile -> {
                URButton(
                    onClick = requestTile,
                    style = ButtonStyle.SECONDARY
                ) { btnStyle ->
                    Text(
                        stringResource(id = R.string.settings_add_quick_tile),
                        style = btnStyle
                    )
                }
            }
            else -> {
                Text(
                    stringResource(id = R.string.intro_quick_tile_manual),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        /**
         * The widgets: tap a preview to pin it
         */
        Text(
            stringResource(id = R.string.settings_add_widgets),
            style = TopBarTitleTextStyle
        )

        Spacer(modifier = Modifier.height(12.dp))

        WidgetPreviewCard(
            onAdd = { requestPinWidget(context, DashboardWidgetReceiver::class.java) },
            modifier = Modifier.fillMaxWidth()
        ) {
            DashboardPreview(sample)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WidgetPreviewCard(
                onAdd = { requestPinWidget(context, ProviderGlobeWidgetReceiver::class.java) },
                modifier = Modifier.weight(1f)
            ) {
                GlobePreview(sample)
            }
            WidgetPreviewCard(
                onAdd = { requestPinWidget(context, ContractsWidgetReceiver::class.java) },
                modifier = Modifier.weight(1f)
            ) {
                ContractsPreview(sample)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(
                id = if (canPinWidgets) R.string.intro_widgets_tap_to_add else R.string.intro_widgets_manual
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

/**
 * The Quick Settings tile as the system draws it: a round icon-only tile
 * with the connector mark, which lights up pink when the tunnel is on.
 */
@Composable
private fun QuickTilePreview(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = if (connected) Pink else Color(0xFF3A3A3C)
    val ink = if (connected) Black else Color(0xFFB8B8B8)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(fill, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tile_quick_on),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = if (connected) R.string.tile_status_connected else R.string.tile_status_disconnected),
            style = MaterialTheme.typography.bodySmall,
            color = if (connected) Pink else TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun AddedChip(text: String) {
    Row(
        modifier = Modifier
            .background(Green.copy(alpha = 0.14f), RoundedCornerShape(100))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Green)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Green
        )
    }
}

/** A widget-shaped card: the launcher's rounded tile on the widget's black. */
@Composable
private fun WidgetPreviewCard(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(OffBlack)
            .clickable { onAdd() }
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun DashboardPreview(sample: WidgetEntry) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val location = sample.tunnel.location

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.ic_tile_quick_on),
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                location?.name?.ifEmpty { null } ?: stringResource(id = R.string.best_available_provider),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        // the quick connect button, pink like in the widget
        Icon(
            painter = painterResource(id = R.drawable.ic_tile_quick_on),
            contentDescription = null,
            tint = Pink,
            modifier = Modifier.size(24.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        stringResource(id = R.string.balance),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
    )
    Spacer(modifier = Modifier.height(4.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = (maxWidth.value * density).toInt()
        val heightPx = (10 * density).toInt()
        val bar = remember(widthPx) {
            BalanceBarRenderer.render(
                widthPx, heightPx, sample.balance,
                WidgetTheme.balanceUsedArgb, WidgetTheme.balancePendingArgb, WidgetTheme.balanceAvailableArgb,
                density
            ).asImageBitmap()
        }
        Image(
            bitmap = bar,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            contentScale = ContentScale.FillBounds
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    val points = remember(sample) {
        sample.tunnel.throughput.buckets.map {
            ThroughputChartRenderer.Point(it.start, it.clientEgress, it.clientIngress, it.clientEgressPackets, it.clientIngressPackets)
        }
    }
    val bucketSeconds = maxOf(1L, sample.tunnel.throughput.bucketSeconds)
    val peakLabel = remember(points) {
        formatByteRate(ThroughputChartRenderer.peak(points) / bucketSeconds) + " · " +
            formatPacketRate(ThroughputChartRenderer.peakPackets(points) / bucketSeconds)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(id = R.string.widget_client), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(
            stringResource(id = R.string.widget_peak_rate, peakLabel),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = (maxWidth.value * density).toInt()
        val heightPx = (52 * density).toInt()
        val chart = remember(widthPx) {
            ThroughputChartRenderer.render(
                widthPx, heightPx, points, sample.tunnel.throughput.bucketSeconds, sample.nowMillis,
                WidgetTheme.byteSeriesArgb, WidgetTheme.packetSeriesArgb, density
            ).asImageBitmap()
        }
        Image(
            bitmap = chart,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentScale = ContentScale.FillBounds
        )
    }
}

@Composable
private fun GlobePreview(sample: WidgetEntry) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val providers = sample.tunnel.providers

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(id = R.string.site_app_providers),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        Text(
            "${providers.size}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = (maxWidth.value * density).toInt()
        val heightPx = (110 * density).toInt()
        val globe = remember(widthPx) {
            GlobeBitmapRenderer.render(context, widthPx, heightPx, providers, density).asImageBitmap()
        }
        Image(
            bitmap = globe,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            contentScale = ContentScale.FillBounds
        )
    }
}

@Composable
private fun ContractsPreview(sample: WidgetEntry) {
    val density = LocalDensity.current.density
    val peers = sample.tunnel.contracts.take(3)
    val style = remember(density) { ContractStackRenderer.Style(density, compact = true) }

    Text(
        stringResource(id = R.string.site_app_contracts),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        peers.forEach { peer ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Black, RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Text(
                    peer.id.take(8),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val send = remember(peer.id) {
                        ContractStackRenderer.render(peer.send, WidgetTheme.sendStackArgb, pointsRight = true, style = style).asImageBitmap()
                    }
                    val receive = remember(peer.id) {
                        ContractStackRenderer.render(peer.receive, WidgetTheme.receiveStackArgb, pointsRight = false, style = style).asImageBitmap()
                    }
                    Image(bitmap = send, contentDescription = null, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(bitmap = receive, contentDescription = null)
                }
            }
        }
    }
}
