package com.bringyour.network

/**
 * The build-time identity of the bundled (operator) network space.
 *
 * [legacyHostName] is the host an earlier bundle keyed the same space under.
 * Rolling that key forward at startup is what lets an upgraded install keep
 * its credentials and local state instead of starting a second, empty space.
 */
data class BundleNetworkSpaceIdentity(
    val hostName: String,
    val envName: String,
    val legacyHostName: String = "",
) {
    /** Whether there is a legacy key to roll forward at all. */
    val migratesFromLegacyHost: Boolean
        get() = legacyHostName.isNotBlank() && legacyHostName != hostName

    companion object {
        fun fromBuildConfig(): BundleNetworkSpaceIdentity = BundleNetworkSpaceIdentity(
            hostName = BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME,
            envName = BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME,
            legacyHostName = BuildConfig.BRINGYOUR_BUNDLE_LEGACY_HOST_NAME,
        )
    }
}

/**
 * The network space manager operations [installBundleNetworkSpace] drives,
 * keyed by (hostName, envName). [S] is the space type: the sdk's
 * `NetworkSpace` in the app, anything in a test.
 */
interface BundleNetworkSpaceStore<S : Any> {
    /** `NetworkSpaceManager.migrateNetworkSpace`; true when a space moved. */
    fun migrate(fromHostName: String, toHostName: String, envName: String): Boolean

    /** Whether a space is stored under the key. */
    fun exists(hostName: String, envName: String): Boolean

    /** Creates or refreshes the space under the key with the bundled values. */
    fun update(hostName: String, envName: String): S?

    /** The active space selection. */
    var active: S?
}

/** What [installBundleNetworkSpace] did, for the caller's log line. */
data class BundleNetworkSpaceInstall<S : Any>(
    val networkSpace: S?,
    val migratedFromLegacyHost: Boolean,
    val activated: Boolean,
)

/**
 * Creates or refreshes the bundled space and selects it when it is new or
 * nothing is selected.
 *
 * The legacy-key migration runs FIRST, before the bundled key is read,
 * created, or bound. That ordering is the sdk's contract for
 * `NetworkSpaceManager.migrateNetworkSpace`: it closes the space object stored
 * under the old key and replaces it, so a space obtained before the call would
 * be stale. The migration is idempotent (a second call finds nothing under the
 * old key), so it is safe to run on every launch.
 */
fun <S : Any> installBundleNetworkSpace(
    identity: BundleNetworkSpaceIdentity,
    store: BundleNetworkSpaceStore<S>,
): BundleNetworkSpaceInstall<S> {
    val migrated = identity.migratesFromLegacyHost &&
        store.migrate(identity.legacyHostName, identity.hostName, identity.envName)

    val exists = store.exists(identity.hostName, identity.envName)
    val networkSpace = store.update(identity.hostName, identity.envName)

    // switch to the bundled network space when first created
    // this is important when migrating from an older bundle to a newer bundle
    val activate = !exists || store.active == null
    if (activate) {
        store.active = networkSpace
    }

    return BundleNetworkSpaceInstall(
        networkSpace = networkSpace,
        migratedFromLegacyHost = migrated,
        activated = activate,
    )
}
