package com.bringyour.network.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton

// The login stack rule, shared by every flavor (and every app): up to THREE
// full-width buttons, then the remaining sign-in methods as square icon tiles,
// four per row with each row's tiles stretched to fill it (a last row of two is
// two half-width tiles), the rows as wide as the buttons above. Each flavor only
// supplies its ordered lists (see LoginInitial in the flavor source sets); the
// email / phone form follows the stack in the caller.

/** One way to sign in: its label, glyph and action. */
class LoginMethod(
    val label: String,
    val icon: @Composable (size: Dp) -> Unit,
    val onClick: () -> Unit,
    val processing: Boolean = false,
    val enabled: Boolean = true,
    val testTag: String? = null,
)

private const val FULL_WIDTH_MAX = 3
private const val TILES_PER_ROW = 4

@Composable
fun LoginStack(
    full: List<LoginMethod>,
    tiles: List<LoginMethod>,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        full.take(FULL_WIDTH_MAX).forEachIndexed { index, method ->
            if (index > 0) Spacer(modifier = Modifier.height(16.dp))
            LoginPill(method = method, enabled = enabled)
        }

        if (tiles.isNotEmpty()) {
            if (full.isNotEmpty()) Spacer(modifier = Modifier.height(16.dp))
            tiles.chunked(TILES_PER_ROW).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { method ->
                        LoginTile(
                            method = method,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginPill(method: LoginMethod, enabled: Boolean) {
    URButton(
        style = ButtonStyle.SECONDARY,
        onClick = method.onClick,
        enabled = enabled && method.enabled,
        isProcessing = method.processing,
        modifier = method.testTag?.let { Modifier.testTag(it) } ?: Modifier
    ) { buttonTextStyle ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            method.icon(18.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(method.label, style = buttonTextStyle)
        }
    }
}

@Composable
private fun LoginTile(method: LoginMethod, enabled: Boolean, modifier: Modifier) {
    val active = enabled && method.enabled && !method.processing
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .alpha(if (active || method.processing) 1f else 0.5f)
            .clickable(enabled = active) { method.onClick() }
            .then(method.testTag?.let { Modifier.testTag(it) } ?: Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (method.processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    method.icon(22.dp)
                }
            }
            Text(
                method.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── the methods, with their store labels and glyphs ──────────────────────────
// `tile` picks the short caption over the full-button label.

@Composable
fun googleLoginMethod(onClick: () -> Unit, processing: Boolean = false) = LoginMethod(
    // Google is always a full-width button on Android, so only the full label exists here
    label = stringResource(id = R.string.google_auth_btn_text),
    icon = { size ->
        Image(
            painter = painterResource(id = R.drawable.google_login_icon),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    processing = processing,
)

@Composable
fun appleLoginMethod(onClick: () -> Unit, processing: Boolean = false, tile: Boolean = false) = LoginMethod(
    label = stringResource(id = if (tile) R.string.apple else R.string.sign_in_with_apple),
    icon = { size ->
        Image(
            painter = painterResource(id = R.drawable.apple_logo),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    processing = processing,
)

@Composable
fun instantAccountLoginMethod(onClick: () -> Unit) = LoginMethod(
    // the store's create_instant_account key is not tagged for android yet; the
    // literal the screen has always shown, until that tag lands
    label = stringResource(id = R.string.create_instant_account),
    icon = { size ->
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    testTag = "acceptance.login.instant",
)

@Composable
fun secretKeyLoginMethod(onClick: () -> Unit) = LoginMethod(
    label = stringResource(id = R.string.login_tile_secret_key),
    icon = { size ->
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    testTag = "acceptance.login.secret",
)

@Composable
fun authCodeLoginMethod(onClick: () -> Unit, tile: Boolean = false) = LoginMethod(
    label = stringResource(id = if (tile) R.string.auth_code else R.string.auth_code_login_button_text),
    icon = { size ->
        Image(
            painter = painterResource(id = R.drawable.auth_code),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
)

@Composable
fun bittensorLoginMethod(onClick: () -> Unit, processing: Boolean = false, tile: Boolean = false) = LoginMethod(
    label = stringResource(id = if (tile) R.string.bittensor else R.string.bittensor_sign_in),
    icon = { size ->
        // the glyph is drawn white in the drawable; black on the white button
        Image(
            painter = painterResource(id = R.drawable.bittensor_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    processing = processing,
)

@Composable
fun solanaLoginMethod(onClick: () -> Unit, processing: Boolean = false, tile: Boolean = false) = LoginMethod(
    label = stringResource(id = if (tile) R.string.solana else R.string.solana_sign_in),
    icon = { size ->
        Image(
            painter = painterResource(id = R.drawable.solana_logo),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    },
    onClick = onClick,
    processing = processing,
)
