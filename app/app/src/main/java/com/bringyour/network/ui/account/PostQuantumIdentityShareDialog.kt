package com.bringyour.network.ui.account

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bringyour.network.R
import com.bringyour.network.ui.components.Identicon
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.shared.viewmodels.PostQuantumIdentityViewModel
import com.bringyour.network.ui.shared.viewmodels.ProviderIdentityRowUi
import com.bringyour.network.ui.shared.viewmodels.formatIdentityKeyHashForShare
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import java.io.File

/**
 * The identity share dialog: a share-size identicon (4x the panel) with the
 * full grouped key hash and client id beneath, laid out for an easy
 * screenshot — the identicon + hash + id are the complete side-channel
 * verification payload, like a QR code dialog. The share affordance shares
 * the canonical identicon png together with the hash and client id as text.
 */
@Composable
fun PostQuantumIdentityShareDialog(
    row: ProviderIdentityRowUi,
    shareIdenticon: ImageBitmap?,
    onDismiss: () -> Unit,
) {

    val context = LocalContext.current

    URDialog(
        visible = true,
        onDismiss = onDismiss
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            // the share identicon at its canonical display size, clamped to
            // the dialog width on narrow screens (the raster stays canonical)
            val identiconSize = min(
                PostQuantumIdentityViewModel.SHARE_IDENTICON_SIZE.dp,
                maxWidth
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.post_quantum_identity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                Identicon(
                    image = shareIdenticon,
                    size = identiconSize
                )

                Spacer(modifier = Modifier.height(24.dp))

                // the full grouped hash: the share dialog is for reading and
                // screenshots, so nothing is truncated
                Text(
                    formatIdentityKeyHashForShare(row.publicKeyHash),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    row.clientId,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = {
                        sharePostQuantumIdentity(context, row)
                    }
                ) { buttonTextStyle ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(id = R.string.share),
                            style = buttonTextStyle
                        )
                    }
                }
            }
        }
    }
}

/**
 * ACTION_SEND chooser payload: the canonical identicon png rendered from the
 * raw public identity key at share size — the same bytes every platform
 * produces for this key, so shared icons compare exactly — with the full
 * hash and client id riding along as text.
 */
private fun sharePostQuantumIdentity(context: Context, row: ProviderIdentityRowUi) {
    try {
        val png = Sdk.renderIdenticonPng(
            row.publicKey,
            (PostQuantumIdentityViewModel.SHARE_IDENTICON_SIZE * 2).toLong()
        ) ?: return

        val shareDir = File(context.cacheDir, "share")
        shareDir.mkdirs()
        val file = File(shareDir, "post_quantum_identity.png")
        file.writeBytes(png)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${row.publicKeyHash}\n${row.clientId}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.post_quantum_identity)
            )
        )
    } catch (e: Exception) {
        // identicon render or file write failed; nothing to share
    }
}
