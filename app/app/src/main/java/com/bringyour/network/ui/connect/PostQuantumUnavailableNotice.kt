package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.models.ConnectStatus

/**
 * Whether the sdk's no post-quantum exits notice shows for the status: while
 * a connection is requested and not connected. Connected, the location has
 * exits; disconnected, nothing is asked of it.
 */
internal fun postQuantumUnavailableShows(connectStatus: ConnectStatus): Boolean =
    connectStatus == ConnectStatus.CONNECTING ||
        connectStatus == ConnectStatus.DESTINATION_SET ||
        connectStatus == ConnectStatus.CONNECT_FAILED

/**
 * The sdk's notice that post-quantum encryption is on and the selected
 * location has no post-quantum exits right now
 * (ConnectViewController.getPostQuantumUnavailable), as the connect screen
 * shows it in place of connecting: the line, Pick another location, which
 * opens the location picker, and Turn off post-quantum, which turns the
 * connect option off and saves it as its switch does. The sdk decides when it
 * holds; nothing changes until the user picks one, and Disconnect beside the
 * notice stays the way out.
 */
@Composable
fun PostQuantumUnavailableNotice(
    // the selected location's name while the notice holds, null otherwise
    locationName: String?,
    connectStatus: ConnectStatus,
    pickAnotherLocation: () -> Unit,
    turnOffPostQuantum: () -> Unit,
) {
    val name = locationName?.takeIf { postQuantumUnavailableShows(connectStatus) } ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.post_quantum_no_exits, name),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("acceptance.post_quantum_unavailable_notice")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            URButton(
                onClick = pickAnotherLocation,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("acceptance.post_quantum_unavailable_pick_location")
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.post_quantum_no_exits_pick_location),
                    style = buttonTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            URButton(
                onClick = turnOffPostQuantum,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("acceptance.post_quantum_unavailable_turn_off")
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.post_quantum_no_exits_turn_off),
                    style = buttonTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
