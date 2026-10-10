package com.bringyour.network.acceptance

import java.util.concurrent.atomic.AtomicBoolean

/** Counters follow the selected build policy; samples cannot select their own ceiling. */
internal fun physicalMemoryRuntimeThresholdBytes(profile: String): Long = when (profile) {
    "android" -> 64L * 1024 * 1024
    "ios-memory-audit-v2", "ios-memory-audit-v1" -> 32L * 1024 * 1024
    else -> error("physical-memory-profile-unsupported")
}

/** Runtime policy evidence must not turn absent/string/floating values into zero. */
internal fun physicalMemoryLong(value: Any?): Long {
    check(value is Long || value is Int) { "physical-memory-integer-required" }
    return (value as Number).toLong()
}

/** An elapsed join timeout is failure, not proof the Java exporter stopped. */
internal fun requirePhysicalMemoryDrainerJoined(
    drainer: Thread?,
    exporterFailed: AtomicBoolean = AtomicBoolean(false),
    join: (Thread) -> Unit = { it.join(5_000) },
) {
    if (drainer != null) {
        join(drainer)
        check(!drainer.isAlive) { "physical-memory-drainer-unjoined" }
    }
    check(!exporterFailed.get()) { "physical-memory-exporter-failed" }
}

/** A daemon exit after any open/append/flush/close failure remains failed. */
internal fun retainPhysicalMemoryExporterFailure(failed: AtomicBoolean, export: () -> Unit) {
    try {
        export()
    } catch (_: Throwable) {
        failed.set(true)
    }
}

/** Independent evidence attempts: summary failure must not discard native rows. */
internal fun writePhysicalMemoryEvidence(
    summary: () -> Unit,
    native: (Boolean) -> Unit,
    fallback: () -> Unit,
    failed: (String) -> Unit,
) {
    val summaryFlushed = runCatching(summary).onFailure { failed("summary-write") }.isSuccess
    if (runCatching { native(summaryFlushed) }.onFailure { failed("native-receipt-write") }.isFailure) {
        runCatching(fallback).onFailure { failed("native-fallback-write") }
    }
}

/** Only this external instrumentation owner joins; logout remains just a request. */
internal fun closePhysicalMemoryOwner(
    stop: () -> Unit,
    joinDrainer: () -> Unit,
    logout: () -> Unit,
    joinDevice: () -> Unit,
    drainDeviceRing: () -> Unit,
    failed: (String) -> Unit,
) {
    fun attempt(stage: String, action: () -> Unit): Boolean =
        runCatching(action).onFailure { failed(stage) }.isSuccess
    attempt("stop-drainer", stop)
    val drainerJoined = attempt("drainer-join", joinDrainer)
    attempt("logout", logout)
    val deviceJoined = attempt("device-join", joinDevice)
    if (drainerJoined && deviceJoined) attempt("device-ring-drain", drainDeviceRing)
}
