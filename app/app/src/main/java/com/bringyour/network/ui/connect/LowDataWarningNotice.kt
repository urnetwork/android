package com.bringyour.network.ui.connect

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.FoldSafeAlertDialog
import com.bringyour.network.utils.formatBalanceBytes
import com.bringyour.sdk.LowDataWarning
import com.bringyour.sdk.Sdk

/*
 * The low-data warning, as the app shows it: little data is left before the
 * balance runs out. When to warn is the sdk's decision, the same on every
 * platform (Sdk.lowDataWarningForBalance: once per threshold per balance
 * period, remembered in the local state). MainApplication hands it every
 * balance the app loads and shows what it returns, as a local notification
 * like the out-of-balance notice and, while the app is in front, as this
 * alert over the main screens. This file holds only the words and the alert.
 * The warning only informs: nothing disconnects, and once the data runs out
 * the out-of-balance notice takes over.
 */

/**
 * The warning's words: the part of the data left for the percent threshold,
 * the amount alone for the byte count one, the amount as the usage bar and
 * "About your data" write it.
 */
fun lowDataWarningMessage(context: Context, warning: LowDataWarning): String {
    val amount = formatBalanceBytes(warning.availableByteCount)
    return if (warning.kind == Sdk.LowDataWarningKindPercent) {
        context.getString(R.string.low_data_warning_percent, warning.availablePercent, amount)
    } else {
        // Sdk.LowDataWarningKindByteCount, and any kind a newer sdk adds: the amount is always true
        context.getString(R.string.low_data_warning_byte_count, amount)
    }
}

/**
 * The alert while MainApplication holds a warning (lowDataWarning): Get Pro,
 * which runs [onUpgrade], beside Got it on a plan that is not Pro, else Got it
 * alone. Either closes the alert and the warning's notification.
 */
@Composable
fun LowDataWarningAlert(isPro: Boolean, onUpgrade: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication ?: return
    val warning by application.lowDataWarning.collectAsState()
    val current = warning ?: return
    val configuration = LocalConfiguration.current
    // the catalog's words for the configuration's locale
    val message = remember(current, context, configuration) { lowDataWarningMessage(context, current) }

    FoldSafeAlertDialog(
        onDismissRequest = { application.dismissLowDataWarning() },
        title = { Text(stringResource(id = R.string.low_data_warning_title)) },
        text = { Text(message) },
        confirmButton = {
            if (isPro) {
                TextButton(onClick = { application.dismissLowDataWarning() }) {
                    Text(stringResource(id = R.string.got_it))
                }
            } else {
                TextButton(
                    onClick = {
                        application.dismissLowDataWarning()
                        onUpgrade()
                    }
                ) {
                    Text(stringResource(id = R.string.get_pro))
                }
            }
        },
        dismissButton = {
            // on Pro, Got it is the only button
            if (!isPro) {
                TextButton(onClick = { application.dismissLowDataWarning() }) {
                    Text(stringResource(id = R.string.got_it))
                }
            }
        },
    )
}
