package com.bringyour.network.ui.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.Identicon
import com.bringyour.network.ui.shared.viewmodels.PostQuantumIdentityViewModel
import com.bringyour.network.ui.shared.viewmodels.formatIdentityKeyHashForDisplay
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

// at most this many provider identicons in the deck; the peer count
// label carries the total
private const val MAX_DECK_IDENTICONS = 5

// how far each deck identicon tucks under the previous one
private val DECK_OVERLAP = 10.dp

/**
 * The Post Quantum Identity (PQI) panel in the account tab: a left-aligned
 * card — the title, this device's own identicon at 2x row size, its key
 * hash and client id (each tap-copies), the live deck of providers with an
 * identity-verified e2e session (tap opens the provider identities list),
 * and the explanation footer. The panel and the deck row are always
 * visible -- at zero qualifying peers the deck shows a "0 peers" status and
 * keeps its height.
 *
 * `viewModel` is null in previews, rendering the empty (no key) state.
 */
@Composable
fun PostQuantumIdentityPanel(
    viewModel: PostQuantumIdentityViewModel?,
    navigateToProviderIdentities: () -> Unit,
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isPresentingShareDialog by remember { mutableStateOf(false) }

    val ownIdentity = viewModel?.ownIdentity
    val peers = viewModel?.providerIdentities ?: listOf()

    val panelIdenticonSize = PostQuantumIdentityViewModel.PANEL_IDENTICON_SIZE.dp
    val deckIdenticonSize = PostQuantumIdentityViewModel.DECK_IDENTICON_SIZE.dp

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(
                OffBlack,
                RoundedCornerShape(12.dp)
            )
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {

            Text(
                stringResource(id = R.string.post_quantum_identity),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // this device's own identity: the large identicon, then the key
            // hash and client id, each tap to copy
            if (ownIdentity != null) {

                // tap opens the share dialog (screenshot-friendly identicon
                // + hash + client id, with a share affordance)
                Identicon(
                    image = ownIdentity.identicon,
                    size = panelIdenticonSize,
                    modifier = Modifier
                        .clip(RoundedCornerShape(panelIdenticonSize / 6))
                        .clickable {
                            isPresentingShareDialog = true
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // the identity key hash, tap to copy the full hash
                Text(
                    formatIdentityKeyHashForDisplay(ownIdentity.publicKeyHash),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color.White,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(ownIdentity.publicKeyHash))
                        Toast.makeText(context, R.string.identity_key_hash_copied, Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // the client id, tap to copy
                Text(
                    ownIdentity.clientId,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TextFaint,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(ownIdentity.clientId))
                        Toast.makeText(context, R.string.client_id_copied, Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // the connected peer identity deck with the peer count: always
            // visible (a "0 peers" status when none), tap for details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = deckIdenticonSize)
                    .clickable {
                        navigateToProviderIdentities()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                val visible = peers.take(MAX_DECK_IDENTICONS)
                if (visible.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(-DECK_OVERLAP)
                    ) {
                        visible.forEach { row ->
                            // a ring in the card background color separates the
                            // overlapped identicons
                            Identicon(
                                image = row.identiconSmall,
                                size = deckIdenticonSize,
                                modifier = Modifier.border(
                                    2.dp,
                                    OffBlack,
                                    RoundedCornerShape(deckIdenticonSize / 6)
                                )
                            )
                        }
                    }
                }

                // the peer count indicator, always shown; the total covers
                // any identicons beyond the visible deck. printf-style key
                // shared with every platform (see peer_count_other)
                Text(
                    if (peers.size == 1) {
                        stringResource(id = R.string.peer_count_one)
                    } else {
                        stringResource(id = R.string.peer_count_other, peers.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(id = R.string.post_quantum_identity_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }

    if (isPresentingShareDialog && ownIdentity != null && viewModel != null) {
        PostQuantumIdentityShareDialog(
            row = ownIdentity,
            shareIdenticon = viewModel.identicon(
                ownIdentity.publicKey,
                ownIdentity.publicKeyHash,
                PostQuantumIdentityViewModel.SHARE_IDENTICON_SIZE
            ),
            onDismiss = {
                isPresentingShareDialog = false
            }
        )
    }
}
