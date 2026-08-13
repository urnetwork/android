package com.bringyour.network.ui.connect.providerlocations

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.Identicon
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.shared.viewmodels.PostQuantumIdentityViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import kotlinx.coroutines.delay

/**
 * The connected providers and where they are. A screen rather than a bottom
 * sheet: the list is the point of the view, and a sheet dismisses itself on
 * the same downward drag used to scroll it. Dismissal is the explicit control
 * in the top bar, matching the contract details screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderLocationsScreen(
    navController: NavController,
    getLocationColor: (String) -> Color,
    mockLocationSection: @Composable (() -> Unit)? = null,
    viewModel: ProviderLocationsViewModel = hiltViewModel(),
    postQuantumIdentityViewModel: PostQuantumIdentityViewModel = hiltViewModel(),
) {
    val rows by viewModel.providerLocations.collectAsState()
    val selectedClientId by viewModel.selectedClientId.collectAsState()
    val listState = rememberLazyListState()

    // providers with an identity-verified e2e session, keyed by the same
    // egress client id the rows carry — membership is the "end-to-end
    // encrypted" signal, and the value is the peer's identity identicon
    // rendered at badge size (see ProviderIdentityRowUi.identiconBadge)
    val pqIdenticonByClientId = remember(postQuantumIdentityViewModel.providerIdentities) {
        postQuantumIdentityViewModel.providerIdentities.associate {
            it.clientId to it.identiconBadge
        }
    }

    // Keep the selection on screen. It moves without the list being touched —
    // a wheel step on the globe, the default landing on the longest connected
    // provider, a removal handing it to the nearest — and a selection the user
    // cannot see is not a selection. Rows scrolled out of view are the only
    // ones worth moving for.
    LaunchedEffect(selectedClientId, rows) {
        val index = rows.indexOfFirst { it.clientId == selectedClientId }
        if (index < 0) {
            return@LaunchedEffect
        }
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo.any {
            it.index == index &&
                layout.viewportStartOffset <= it.offset &&
                it.offset + it.size <= layout.viewportEndOffset
        }
        if (!visible) {
            listState.animateScrollToItem(index)
        }
    }

    // the connected durations tick locally against the absolute connected-since
    // stamps, so a running clock costs one recomposition a second instead of an
    // sdk event per provider
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.provider_locations_title),
                        style = TopBarTitleTextStyle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
        containerColor = Black,
    ) { innerPadding ->
        // The toggle and globe are fixed; only the list scrolls, in the space
        // left below them.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            mockLocationSection?.let {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    it()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // full bleed: the globe spans the screen width, outside the
            // horizontal padding the rows use. The sphere is scaled to fit
            // and centered inside that square, so it never overflows.
            ProviderGlobe(
                rows = rows,
                selectedClientId = selectedClientId,
                onSelect = { viewModel.select(it) },
                onStep = { viewModel.step(it) },
                getLocationColor = getLocationColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.provider_locations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
            ) {
                itemsIndexed(
                    rows,
                    key = { index, row ->
                        indexedLazyListKey("provider-location", index, row.clientId)
                    },
                ) { _, row ->
                    SwipeToRevealRow(onDelete = { viewModel.removeProvider(row.clientId) }) {
                        ProviderLocationRowItem(
                            row = row,
                            selected = row.clientId == selectedClientId,
                            nowMillis = nowMillis,
                            getLocationColor = getLocationColor,
                            onSelect = { viewModel.select(row.clientId) },
                            pqIdenticon = pqIdenticonByClientId[row.clientId],
                        )
                    }
                    HorizontalDivider()
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderLocationRowItem(
    row: ProviderLocationRow,
    selected: Boolean,
    nowMillis: Long,
    getLocationColor: (String) -> Color,
    onSelect: () -> Unit,
    // the provider's post-quantum identity identicon at badge size, non-null
    // only when the provider has an identity-verified end-to-end encrypted
    // session; rendered as a small badge to the right of the client id
    pqIdenticon: ImageBitmap? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val color = providerColor(row, getLocationColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = PROVIDER_ROW_PADDING, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // fixed-size column so rows stay aligned whether or not the provider
        // is selected — the selection ring is drawn inside this same box
        ProviderDot(color = color, selected = selected)

        // The same padding on both sides of the dot, so it reads as centered
        // in its column: the sheet edge and the detail text are each
        // PROVIDER_ROW_PADDING away from the dot box (which itself carries an
        // equal ring allowance around the dot).
        Spacer(modifier = Modifier.width(PROVIDER_ROW_PADDING))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            // the client id, tap to copy, with the provider's post-quantum
            // identity identicon as a trailing badge when an end-to-end
            // encrypted session is verified. The badge is skipped entirely
            // (not an empty placeholder) when there is no verified session —
            // absence is the "not e2e" state.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.clientId,
                    style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = if (selected) Color.White else TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false: a short id keeps the badge snug against the
                    // text; a long id ellipsizes instead of pushing it away
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(row.clientId))
                            Toast.makeText(context, R.string.client_id_copied, Toast.LENGTH_SHORT)
                                .show()
                        },
                )
                pqIdenticon?.let { image ->
                    Spacer(modifier = Modifier.width(6.dp))
                    val pqDescription = stringResource(R.string.post_quantum_encryption)
                    Identicon(
                        image = image,
                        size = PostQuantumIdentityViewModel.BADGE_IDENTICON_SIZE.dp,
                        modifier = Modifier.semantics { contentDescription = pqDescription },
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                placeLabel(row).ifEmpty { stringResource(R.string.provider_location_unknown) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                coordinatesLabel(row),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                formatConnectedDuration(context, row.connectedSinceMillis, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

// dot geometry, in dp: the ring is an outline PROVIDER_DOT_RING_GAP outside
// the solid dot's edge. The box is sized for the ring so the column width
// never changes with selection.
// The diameter matches the location circles in the choose-locations list
// (ProviderRow's CircleImage), so a country reads the same size everywhere.
// the row's horizontal padding, mirrored as the gap between the dot and the
// detail text so the dot sits centered in its column
private val PROVIDER_ROW_PADDING = 16.dp
private val PROVIDER_DOT_DIAMETER = 40.dp
private val PROVIDER_DOT_RING_GAP = 4.dp
private val PROVIDER_DOT_RING_STROKE = 1.5.dp
private val PROVIDER_DOT_BOX =
    PROVIDER_DOT_DIAMETER + (PROVIDER_DOT_RING_GAP + PROVIDER_DOT_RING_STROKE) * 2f

@Composable
private fun ProviderDot(color: Color, selected: Boolean) {
    Canvas(modifier = Modifier.size(PROVIDER_DOT_BOX)) {
        val dotRadius = PROVIDER_DOT_DIAMETER.toPx() / 2f
        drawCircle(color = color, radius = dotRadius, center = center)
        if (selected) {
            val stroke = PROVIDER_DOT_RING_STROKE.toPx()
            drawCircle(
                color = color,
                radius = dotRadius + PROVIDER_DOT_RING_GAP.toPx() + stroke / 2f,
                center = center,
                style = Stroke(width = stroke),
            )
        }
    }
}

/** "City, Region, Country" — omitting whichever parts the server does not know. */
fun placeLabel(row: ProviderLocationRow): String =
    listOf(row.city, row.region, row.country)
        .filter { it.isNotEmpty() }
        .joinToString(", ")

/** "37.7749, -122.4194", or an em dash when the provider has no coordinates. */
fun coordinatesLabel(row: ProviderLocationRow): String {
    val lat = row.lat
    val lon = row.lon
    if (lat == null || lon == null) {
        return "—"
    }
    return String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)
}

@Composable
private fun formatConnectedDuration(
    context: android.content.Context,
    connectedSinceMillis: Long,
    nowMillis: Long,
): String {
    if (connectedSinceMillis <= 0) {
        return ""
    }
    val elapsedSeconds = ((nowMillis - connectedSinceMillis) / 1000).coerceAtLeast(0)
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    return when {
        0 < hours -> context.getString(
            R.string.provider_connected_duration_hours,
            hours.toInt(),
            minutes.toInt(),
        )
        0 < minutes -> context.getString(
            R.string.provider_connected_duration_minutes,
            minutes.toInt(),
        )
        else -> context.getString(
            R.string.provider_connected_duration_seconds,
            seconds.toInt(),
        )
    }
}
