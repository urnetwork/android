package com.bringyour.network

/**
 * Sanitizes SDK split-tunnel package ids before selecting Android VPN
 * allowlist/denylist mode. The VPN owner's sockets must never loop through its
 * own TUN. Removing it before the mode decision is essential: a stale
 * self-only allowlist must become the normal denylist mode, not an empty
 * Android UID set that silently routes no applications.
 */
internal fun sanitizeTunnelAppSplit(
    ownerPackageName: String,
    includedPackageNames: Set<String>,
    excludedPackageNames: Set<String>,
    isPackageInstalled: (String) -> Boolean = { true },
): Pair<Set<String>, Set<String>> {
    fun sanitize(packageNames: Set<String>): Set<String> =
        packageNames.filterTo(linkedSetOf()) {
            it.isNotBlank() && it != ownerPackageName && isPackageInstalled(it)
        }

    return Pair(
        sanitize(includedPackageNames),
        sanitize(excludedPackageNames),
    )
}
