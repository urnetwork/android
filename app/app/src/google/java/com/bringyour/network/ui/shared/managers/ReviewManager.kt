package com.bringyour.network.ui.shared.managers

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ReviewInfo
import androidx.compose.runtime.remember
import com.bringyour.network.TAG
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.model.ReviewErrorCode

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

    fun requestReviewFlow() {

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

    fun launchReviewFlow(activity: android.app.Activity) {

        reviewInfo?.let {
            val flow = reviewManager.launchReviewFlow(activity, it)
            flow.addOnCompleteListener { _ ->
                // The flow has finished. The API does not indicate whether the user
                // reviewed or not, or even whether the review dialog was shown. Thus, no
                // matter the result, we continue our app flow.
                requestReviewFlow()
            }
        } ?: run {
            requestReviewFlow() // Ensure reviewInfo is not null
        }
    }

}