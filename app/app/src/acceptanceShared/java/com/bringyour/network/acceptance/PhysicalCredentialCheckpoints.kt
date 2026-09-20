package com.bringyour.network.acceptance

internal enum class PhysicalCredentialStage(val wireValue: String) {
    RUNNER_ON_CREATE_ENTRY("runner-on-create-entry"),
    RUNNER_ON_CREATE_RETURN("runner-on-create-return"),
    TEST_METHOD_ENTRY("test-method-entry"),
    BEFORE_LOGGED_OUT_LAUNCH("before-logged-out-launch"),
    AFTER_LOGGED_OUT_LAUNCH("after-logged-out-launch"),
    BEFORE_CREDENTIAL_READ("before-credential-read"),
}

internal const val PHYSICAL_CREDENTIAL_DIAGNOSTICS_ARGUMENT = "acceptanceCredentialDiagnostics"

internal fun physicalCredentialDiagnosticsEnabled(argument: String?): Boolean = argument == "true"

/** Observe the first argument-aware runner hook without altering base runner behavior. */
internal fun <T> withPhysicalCredentialRunnerCheckpoints(
    enabled: Boolean,
    checkpoint: (PhysicalCredentialStage) -> Unit,
    createRunner: () -> T,
): T {
    if (!enabled) return createRunner()
    runCatching { checkpoint(PhysicalCredentialStage.RUNNER_ON_CREATE_ENTRY) }
    val result = createRunner()
    // A thrown base-runner exception is preserved; do not label it a return.
    runCatching { checkpoint(PhysicalCredentialStage.RUNNER_ON_CREATE_RETURN) }
    return result
}

internal fun physicalCredentialCheckpointAppendAllowed(priorBytes: Long, recordBytes: Int): Boolean =
    priorBytes >= 0 && recordBytes >= 0 && recordBytes <= 16_384 && priorBytes <= 16_384 - recordBytes

internal enum class PhysicalCredentialFileType(val wireValue: String) {
    MISSING("missing"),
    REGULAR("regular"),
    DIRECTORY("directory"),
    SYMLINK("symlink"),
    OTHER("other"),
    UNAVAILABLE("unavailable"),
}

/** Metadata only: this type cannot carry a path, credential, digest or raw error. */
internal data class PhysicalCredentialMetadata(
    val type: PhysicalCredentialFileType,
    val ownerMatchesApp: Boolean? = null,
    val mode: Int? = null,
    val byteCount: Long? = null,
) {
    init {
        if (type == PhysicalCredentialFileType.MISSING || type == PhysicalCredentialFileType.UNAVAILABLE) {
            require(ownerMatchesApp == null && mode == null && byteCount == null)
        } else {
            require(ownerMatchesApp != null && mode != null && mode in 0..0xfff)
            require(byteCount != null && byteCount >= 0)
        }
    }
}

internal fun physicalCredentialCheckpointJson(
    stage: PhysicalCredentialStage,
    elapsedRealtimeMs: Long,
    timeUnixMs: Long,
    readMetadata: () -> PhysicalCredentialMetadata,
): String {
    require(elapsedRealtimeMs >= 0 && timeUnixMs >= 0)
    val metadata = runCatching(readMetadata).getOrElse {
        PhysicalCredentialMetadata(PhysicalCredentialFileType.UNAVAILABLE)
    }
    val exists = when (metadata.type) {
        PhysicalCredentialFileType.MISSING -> "false"
        PhysicalCredentialFileType.UNAVAILABLE -> "null"
        else -> "true"
    }
    val mode = metadata.mode?.let { "\"${it.toString(8)}\"" } ?: "null"
    // Every string is a fixed enum value; no app path or file bytes are read
    // here, and no diagnostic includes an exception's message or toString.
    return "{\"type\":\"physical-credential-checkpoint\",\"schemaVersion\":1," +
        "\"stage\":\"${stage.wireValue}\",\"elapsedRealtimeMs\":$elapsedRealtimeMs," +
        "\"timeUnixMs\":$timeUnixMs,\"exists\":$exists," +
        "\"fileType\":\"${metadata.type.wireValue}\",\"ownerMatchesApp\":${metadata.ownerMatchesApp}," +
        "\"mode\":$mode,\"byteCount\":${metadata.byteCount}}"
}

/** Observe, but do not reorder, restore, retry or otherwise change login setup. */
internal fun <T> withPhysicalCredentialCheckpoints(
    checkpoint: (PhysicalCredentialStage) -> Unit,
    launchLoggedOut: () -> Unit,
    login: () -> T,
): T {
    checkpoint(PhysicalCredentialStage.BEFORE_LOGGED_OUT_LAUNCH)
    launchLoggedOut()
    checkpoint(PhysicalCredentialStage.AFTER_LOGGED_OUT_LAUNCH)
    checkpoint(PhysicalCredentialStage.BEFORE_CREDENTIAL_READ)
    return login()
}
