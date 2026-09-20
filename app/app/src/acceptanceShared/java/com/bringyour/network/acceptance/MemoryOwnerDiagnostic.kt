package com.bringyour.network.acceptance

import java.io.File
import java.lang.reflect.InvocationTargetException

/** Acceptance-only bridge: old native bindings fail closed, never skip evidence. */
fun writeMemoryOwnerDiagnostic(device: Any, directory: File, label: String): File {
    require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
        "a bounded diagnostic label is required"
    }
    val destination = File(directory, "physical-owners-$label.json")
    check(!destination.exists()) { "diagnostic evidence already exists" }
    // Reflection permits compiling the test APK with an ordinary older AAR.
    // It is NOT a fallback: the pre-traffic census must succeed with the
    // freshly attested SDK, and absence of the new binding is a hard failure.
    val writer = try {
        device.javaClass.getMethod("writeMemoryOwnerCensus", String::class.java)
    } catch (_: NoSuchMethodException) {
        throw IllegalStateException("owner census requires a freshly rebuilt diagnostic SDK")
    }
    check(writer.returnType == Void.TYPE) { "invalid owner census binding" }
    try {
        writer.invoke(device, destination.absolutePath)
    } catch (error: InvocationTargetException) {
        throw IllegalStateException("owner census write failed", error.targetException)
    }
    check(destination.isFile && destination.length() > 0) { "owner census evidence is empty" }
    return destination
}
