package com.bringyour.network.ui.login
import android.util.Log
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Used on LoginInitial on individual build flavors
 */
fun handleLoginFlow(
    networkJwt: String,
    scope: CoroutineScope,
    appLogin: (String) -> Unit,
    authClientAndFinish: (
        callback: (String?) -> Unit,
    ) -> Unit,
    onErr: () -> Unit,
    onContentVisibilityChange: (Boolean) -> Unit,
    onWelcomeOverlayVisibilityChange: (Boolean) -> Unit,
) {
    scope.launch {
        appLogin(networkJwt)

        onContentVisibilityChange(false)

        delay(500)

        onWelcomeOverlayVisibilityChange(true)

        delay(2250)

        authClientAndFinish { error ->
            if (error != null) {
                Log.i(TAG, "auth client and finish err: $error")
                onErr()
            }
        }
    }
}
