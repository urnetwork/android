package com.bringyour.network.ui.shared.managers

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.bringyour.network.ui.shared.models.BundleStore

@Composable
fun rememberReviewManager(): ReviewManagerRequest {
    val context = LocalContext.current
    return remember {
        ReviewManagerRequest(context)
    }
}

class ReviewManagerRequest(
    val context: Context,
) {
    fun requestReviewFlow() {

    }

    fun launchReviewFlow(
        activity: android.app.Activity,
//        bundleStore: BundleStore?
    ) {

    }

}