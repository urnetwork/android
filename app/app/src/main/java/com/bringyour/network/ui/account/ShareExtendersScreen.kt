package com.bringyour.network.ui.account

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ShareButton
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// the drawn side of the share code
private val QR_SIZE = 240.dp
// K7's outline of the connector shape around the centered glyph
private val QR_GLYPH_OUTLINE = 4.dp

/**
 * Share extenders (EXTENDER.md K7): this space's addresses as one payload,
 * rendered as a code to scan, as copyable text and through the system share
 * sheet. Keys and records are never shared — an imported address is an
 * unverified bootstrap entry that upgrades when a record arrives over the
 * feed — and the operator settings go only when asked for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareExtendersScreen(
    navController: NavController,
    viewModel: ExtendersViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    var includeSettings by remember { mutableStateOf(false) }
    // the payload is rebuilt whenever the switch moves, since the settings
    // block is part of what is encoded
    val share = remember(includeSettings, viewModel.editable) {
        extenderShareFor(includeSettings) { viewModel.buildShare(it) }
    }
    val qrSizePx = with(density) { QR_SIZE.roundToPx() }
    val outlinePx = with(density) { QR_GLYPH_OUTLINE.toPx() }
    // encoding and drawing a code of this size is hundreds of thousands of
    // pixels, so it never runs on the composition's thread
    val qr by produceState<Bitmap?>(initialValue = null, share.text, qrSizePx) {
        value = withContext(Dispatchers.Default) {
            extenderShareQrBitmap(context, share.text, qrSizePx, outlinePx)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.share_extenders),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            qr?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(id = R.string.share_extenders),
                    modifier = Modifier
                        .size(QR_SIZE)
                        .background(Color.White),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                pluralStringResource(
                    id = R.plurals.share_extenders_count,
                    count = share.count,
                    share.count,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(id = R.string.share_extenders_hint),
                style = TextStyle(fontSize = 12.sp),
                color = TextMuted,
            )

            Spacer(modifier = Modifier.height(24.dp))

            /**
             * The operator settings ride along only when asked for. An
             * importer still confirms the operator host before they replace
             * its own.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(id = R.string.include_extender_settings))
                Spacer(modifier = Modifier.width(16.dp))
                URSwitch(
                    checked = includeSettings,
                    toggle = { includeSettings = !includeSettings },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // the payload as text, for a channel that cannot carry a code
            Text(
                share.text,
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                color = TextFaint,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(share.text))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                },
                enabled = share.present,
            ) { style ->
                Text(stringResource(id = R.string.copy_share_text), style = style)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (share.present) {
                ShareButton(text = share.text)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
