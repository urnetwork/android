package com.bringyour.network.acceptance

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.ResultReceiver
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal data class EgressProbeResponse(
    val message: String,
    val sourceUid: Int,
)

/** Runs the public-IP request in the test APK UID and receives its result over Binder. */
internal object EgressProbeRequest {
    private data class Callback(
        val resultCode: Int,
        val nonce: String?,
        val message: String?,
        val sourceUid: Int,
    )

    fun execute(
        instrumentation: Instrumentation,
        timeoutMillis: Long,
        fixedResult: String? = null,
    ): EgressProbeResponse {
        require(timeoutMillis > 0) { "egress probe timeout must be positive" }

        val nonce = UUID.randomUUID().toString()
        val callback = AtomicReference<Callback?>()
        val completed = CountDownLatch(1)
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                callback.compareAndSet(
                    null,
                    Callback(
                        resultCode = resultCode,
                        nonce = resultData?.getString(EgressProbeActivity.RESULT_NONCE),
                        message = resultData?.getString(EgressProbeActivity.RESULT_MESSAGE),
                        sourceUid = Binder.getCallingUid(),
                    ),
                )
                completed.countDown()
            }
        }

        val testContext = instrumentation.context
        val intent = Intent().apply {
            component = ComponentName(testContext.packageName, EgressProbeActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EgressProbeActivity.EXTRA_RESULT_RECEIVER, receiver)
            putExtra(EgressProbeActivity.EXTRA_REQUEST_NONCE, nonce)
            putExtra(EgressProbeActivity.EXTRA_FINISH_AFTER_RESULT, true)
            fixedResult?.let { putExtra(EgressProbeActivity.EXTRA_FIXED_RESULT, it) }
        }
        testContext.startActivity(intent)

        val received = try {
            completed.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AssertionError("interrupted while waiting for second-UID egress probe", error)
        }
        if (!received) {
            throw AssertionError("egress probe did not return over Binder within ${timeoutMillis / 1_000}s")
        }

        val result = callback.get()
            ?: throw AssertionError("egress probe callback completed without a result")
        if (result.resultCode != Activity.RESULT_OK) {
            throw AssertionError("egress probe returned result code ${result.resultCode}")
        }
        if (result.nonce != nonce) {
            throw AssertionError("egress probe returned a stale or mismatched request nonce")
        }

        val expectedSourceUid = testContext.applicationInfo.uid
        val productUid = instrumentation.targetContext.applicationInfo.uid
        if (result.sourceUid != expectedSourceUid || result.sourceUid == productUid) {
            throw AssertionError(
                "egress probe result came from uid ${result.sourceUid}; " +
                    "expected test uid $expectedSourceUid distinct from product uid $productUid",
            )
        }

        return EgressProbeResponse(
            message = result.message
                ?: throw AssertionError("egress probe returned no result message"),
            sourceUid = result.sourceUid,
        )
    }

    fun queryPublicIp(instrumentation: Instrumentation, timeoutMillis: Long): String {
        val message = execute(instrumentation, timeoutMillis).message
        if (message.startsWith("ACCEPTANCE_ERROR=")) {
            throw AssertionError("egress probe failed: ${message.removePrefix("ACCEPTANCE_ERROR=")}")
        }
        if (!message.startsWith("ACCEPTANCE_IP=")) {
            throw AssertionError("invalid egress probe response: $message")
        }
        return message.removePrefix("ACCEPTANCE_IP=").trim()
    }
}
