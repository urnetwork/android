package com.bringyour.network.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bringyour.network.BuildConfig
import com.bringyour.network.ui.settings.AddAuthMethodSheet
import com.bringyour.network.ui.settings.AddAuthRefusal
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.sdk.AddAuthArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * A legacy guest's "create an account" and every upgrade entry while the network
 * is a guest (GuestAccount): the add-sign-in-method sheet, adding the method to
 * THIS network through AddAuth, then re-signing the jwt. An email or phone is
 * then verified with a code before `onAdded` runs. The plan and balance
 * stay on the network; nothing logs out. Once a method exists the server stops
 * reporting a guest, and the upgrade entries lead to checkout again.
 */
@Composable
fun GuestConversionSheet(
    settingsViewModel: SettingsViewModel,
    activityResultSender: ActivityResultSender?,
    refreshJwt: () -> Unit,
    onAdded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAddingAuth by settingsViewModel.isAddingAuth.collectAsState()
    val conversion = remember(settingsViewModel) {
        GuestConversion(object : GuestConversionSession<AddAuthArgs> {
            override fun addAuth(args: AddAuthArgs, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit) {
                settingsViewModel.addAuth(args, onSuccess, onError)
            }
            override fun refreshJwt() = refreshJwt()
            // the conversion never leaves the network
            override fun logout() {}
        })
    }

    AddAuthMethodSheet(
        visible = true,
        onDismiss = onDismiss,
        showSsoOptions = BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE,
        activityResultSender = activityResultSender,
        isAddingAuth = isAddingAuth,
        addAuth = { args, onSuccess, onError ->
            conversion.addSignInMethod(args, onSuccess, onError)
        },
        onAdded = onAdded
    )
}
