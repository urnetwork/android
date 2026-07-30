package com.bringyour.network.ui.settings

import androidx.compose.runtime.Composable
import com.bringyour.sdk.AddAuthArgs

// github is deliberately de-Googled (no play-services-auth dependency), so
// this flavor never shows the Google option (showGoogleOption is false via
// BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE) and this body is unreachable in
// practice. It must still exist so AddAuthMethodSheet.kt's call to
// GoogleAddAuthButton(...) resolves for this flavor's compilation.
@Composable
fun GoogleAddAuthButton(
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    isAddingAuth: Boolean,
    onAdded: () -> Unit,
    onError: (String) -> Unit,
) {
}
