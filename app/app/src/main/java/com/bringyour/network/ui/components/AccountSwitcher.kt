package com.bringyour.network.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv

enum class LoginMode {
    Guest, Authenticated
}

@Composable
fun AccountSwitcher(
    loginMode: LoginMode,
    networkName: String?,
    launchOverlay: (OverlayMode) -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    var isOverlayVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester()}

    Box(
        modifier = Modifier.size(32.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.account_switcher_avatar),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    isOverlayVisible = true
                           },
            colorFilter = if (loginMode == LoginMode.Authenticated) null
                else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        )

        if (isOverlayVisible) {
            when(loginMode) {
                LoginMode.Guest -> GuestPopup(
                    onDismiss = { isOverlayVisible = false },
                    application = application,
                    context = context,
                    launchOverlay = launchOverlay,
                    focusRequester = focusRequester
                )
                LoginMode.Authenticated -> AuthenticatedPopup(
                    onDismiss = { isOverlayVisible = false },
                    application = application,
                    context = context,
                    launchOverlay = launchOverlay,
                    networkName = networkName,
                    focusRequester = focusRequester
                )
            }
        }
    }
}

@Composable
fun AccountSwitcherPopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10F),
        contentAlignment = Alignment.Center
    ) {
        Popup(
            onDismissRequest = onDismiss,
            alignment = Alignment.TopEnd,
            properties = PopupProperties(focusable = true),
            offset = IntOffset(x = 0, y = 96),
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .clip(RoundedCornerShape(6.dp))
                    .shadow(6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .width(256.dp)
                        .clickable { /* Do nothing to prevent dismiss */ }
                ) {
                    Column {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun GuestPopup(
    onDismiss: () -> Unit,
    context: Context?,
    application: MainApplication?,
    launchOverlay: (OverlayMode) -> Unit,
    focusRequester: FocusRequester
) {

    AccountSwitcherPopup(onDismiss = { onDismiss() }) {
        PopupActionRow(
            iconResourceId = R.drawable.main_nav_user_filled,
            text = "Guest Mode",
            onClick = {},
            isSelected = true,
        )

        HorizontalDivider()

        PopupActionRow(
            iconResourceId = R.drawable.plus,
            text = "Create Account",
            onClick = {
                application?.logout()

                val intent = Intent(context, LoginActivity::class.java)
                context?.startActivity(intent)
                
                (context as? Activity)?.finish()
            },
            focusRequester = focusRequester
        )

        HorizontalDivider()

        PopupActionRow(
            iconResourceId = R.drawable.export,
            text = "Share URnetwork",
            onClick = {
                launchOverlay(OverlayMode.Refer)
                onDismiss()
            },
        )
    }
}

@Composable
fun AuthenticatedPopup(
    onDismiss: () -> Unit,
    context: Context?,
    application: MainApplication?,
    launchOverlay: (OverlayMode) -> Unit,
    networkName: String?,
    focusRequester: FocusRequester
) {

    AccountSwitcherPopup(onDismiss = { onDismiss() }) {
        PopupActionRow(
            iconResourceId = R.drawable.main_nav_user_filled,
            text = networkName ?: "",
            onClick = {},
            isSelected = true,
        )
        HorizontalDivider()
        PopupActionRow(
            iconResourceId = R.drawable.sign_out,
            text = "Log out",
            onClick = {
                application?.logout()

                val intent = Intent(context, LoginActivity::class.java)
                context?.startActivity(intent)

                (context as? Activity)?.finish()
            },
            focusRequester = focusRequester
        )

        HorizontalDivider()
        PopupActionRow(
            iconResourceId = R.drawable.export,
            text = "Share URnetwork",
            onClick = {
                launchOverlay(OverlayMode.Refer)
                onDismiss()
            },
        )
    }
}

@Composable
fun PopupActionRow(
    onClick: () -> Unit,
    iconResourceId: Int,
    text: String,
    isSelected: Boolean = false,
    focusRequester: FocusRequester? = null
) {

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedBgColor = MainBorderBase.copy(alpha = 0.6f)
    val bgColor = animateColorAsState(
        targetValue = if (isFocused) {
            isFocusedBgColor
        } else {
            MainBorderBase
        }, label = ""
    )

    val modifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor.value)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Icon(
                painterResource(id = iconResourceId),
                contentDescription = "Connect",
                modifier = Modifier.width(16.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Connect",
                modifier = Modifier.width(16.dp),
                tint = BlueMedium
            )
        }
    }

    if (focusRequester != null && isTv()) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
fun AccountSwitcherGuestPreview() {
    URNetworkTheme {
        AccountSwitcher(
            loginMode = LoginMode.Guest,
            networkName = "ur_network",
            launchOverlay = {}
        )
    }
}

@Preview
@Composable
fun AccountSwitcherAuthenticatedPreview() {
    URNetworkTheme {
        AccountSwitcher(
            loginMode = LoginMode.Authenticated,
            networkName = "ur_network",
            launchOverlay = {}
        )
    }
}

@Preview
@Composable
fun GuestPopupPreview() {

    val focusRequester = remember { FocusRequester() }

    URNetworkTheme {
        GuestPopup(
            onDismiss = {},
            application =  null,
            context = null,
            launchOverlay = {},
            focusRequester = focusRequester
        )
    }
}