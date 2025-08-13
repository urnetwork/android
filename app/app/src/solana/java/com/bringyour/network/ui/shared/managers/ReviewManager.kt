package com.bringyour.network.ui.shared.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ReviewInfo
import androidx.compose.runtime.remember
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.BundleStore
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.model.ReviewErrorCode
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
    private val reviewManager = ReviewManagerFactory.create(context)

    private var reviewInfo: ReviewInfo? = null

    init {
        requestReviewFlow()
    }

    private fun requestReviewFlow() {

        val request: Task<ReviewInfo> = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            reviewInfo = if (task.isSuccessful) {
                task.result
            } else {
                Log.i(TAG, "error prompting review")
                null
            }

        }

    }

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