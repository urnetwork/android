package com.bringyour.network

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture

class BackgroundUpdateWorker(context: Context, workerParams: WorkerParameters) : ListenableWorker(context, workerParams) {
    override fun startWork(): ListenableFuture<Result> {
        val app = applicationContext as MainApplication

        app.backgroundUpdate()

        return CallbackToFutureAdapter.getFuture {
            it.set(Result.success())
        }
    }
}
