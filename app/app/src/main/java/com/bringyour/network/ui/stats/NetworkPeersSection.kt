package com.bringyour.network.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.ContextMenuBox
import com.bringyour.network.ui.components.ContextMenuItem
import com.bringyour.network.ui.components.focusRing
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.ConnectLocation

/**
 * Network peers pinned at the top of the available providers list.
 * Shows the connected peers with provide enabled, updating in real time.
 * Hidden while there are no peers. Adds its rows into a LazyListScope so
 * it composes inline with the locations list.
 */
fun LazyListScope.networkPeersSection(
    peers: List<NetworkPeerUi>,
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
    onConnectPeer: (NetworkPeerUi) -> Unit,
    // a peer row's context menu from whether it is the selected location and
    // its connect, as the location rows below have (LocationsList)
    peerMenu: (Boolean, () -> Unit) -> List<ContextMenuItem> = { _, _ -> listOf() },
) {
    if (peers.isEmpty()) {
        return
    }

    item(key = "peers-header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(stringResource(id = R.string.network_peers))
        }
    }

    itemsIndexed(
        peers,
        key = { index, peer ->
            indexedLazyListKey("peer", index, peer.clientId)
        }
    ) { _, peer ->
        val selected = selectedLocation?.connectLocationId?.clientId?.idStr == peer.clientId
        NetworkPeerRow(
            peer = peer,
            isSelected = selected,
            color = getLocationColor(peer.clientId),
            onClick = { onConnectPeer(peer) },
            contextMenuItems = peerMenu(selected) { onConnectPeer(peer) },
        )
    }
}

@Composable
private fun NetworkPeerRow(
    peer: NetworkPeerUi,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    contextMenuItems: List<ContextMenuItem>,
) {
    ContextMenuBox(items = contextMenuItems) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing()
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            CircleImage(
                size = 40.dp,
                backgroundColor = color,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    peer.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                if (peer.deviceName.isNotEmpty() && peer.deviceSpec.isNotEmpty()) {
                    Text(
                        peer.deviceSpec,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // providing indicator. The radio-signal antenna matches the ios peer
            // rows (`antenna.radiowaves.left.and.right`) — "calling the other
            // device" reads better than the globe used for public locations
            Icon(
                imageVector = Icons.Filled.SettingsInputAntenna,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(16.dp)
            )

        }
    }
}
