package com.bringyour.network.ui.shared.managers

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
fun rememberReviewManager(): ReviewManagerRequest {
    val context = LocalContext.current
    return remember {
        ReviewManagerRequest(context)
    }
}

class ReviewManagerRequest(
    val context: Context
) {
    fun launchReviewFlow(
        activity: android.app.Activity,
    ) {

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "solanadappstore://details?id=com.bringyour.network".toUri()
        }
        activity.packageManager?.let { pm ->
            intent.resolveActivity(pm)?.let {
                activity.startActivity(intent)
            }
        }


    }

}