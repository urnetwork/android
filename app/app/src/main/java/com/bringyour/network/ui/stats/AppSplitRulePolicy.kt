package com.bringyour.network.ui.stats

/**
 * What an app split rule does with the app's traffic.
 *
 * EXCLUDED and INCLUDED are tunnel MEMBERSHIP (enforced by the VpnService
 * builder's disallow/allow lists). PINNED is not membership at all: the app
 * uses the tunnel like any other, but all of its flows are held to one exit,
 * so its API session and its CDNs present a single egress IP -- the fix for
 * apps whose images fail to load behind a multi-exit VPN. A pinned app must
 * never reach the builder's allow list, or the VPN would flip to
 * allowlist mode and route ONLY pinned apps.
 */
enum class AppSplitMode {
    EXCLUDED,
    INCLUDED,
    PINNED;

    companion object {
        fun of(local: Boolean, pin: Boolean): AppSplitMode = when {
            local -> EXCLUDED
            pin -> PINNED
            else -> INCLUDED
        }
    }
}

/**
 * Result of partitioning app rules into included, excluded, and pinned groups,
 * along with the resulting tunnel mode and per-rule active visibility status.
 */
data class AppRulePartition(
    val includedAppIds: List<String>,
    val excludedAppIds: List<String>,
    val pinnedAppIds: List<String>,
) {
    /**
     * When any app is explicitly included, the tunnel runs in allowlist mode.
     */
    val isIncludeMode: Boolean
        get() = includedAppIds.isNotEmpty()

    /**
     * When no apps are included and at least one is excluded, the tunnel runs in denylist mode.
     */
    val isExcludeMode: Boolean
        get() = !isIncludeMode && excludedAppIds.isNotEmpty()

    /**
     * An exclude rule has no distinct effect while include mode is active (allowlist overrides denylist).
     */
    fun isRuleActive(mode: AppSplitMode): Boolean {
        return mode != AppSplitMode.EXCLUDED || !isIncludeMode
    }
}

object AppSplitRulePolicy {

    fun resolveMode(local: Boolean, pin: Boolean): AppSplitMode =
        AppSplitMode.of(local, pin)

    fun <T> partition(
        rules: List<T>,
        appIdSelector: (T) -> String,
        modeSelector: (T) -> AppSplitMode,
    ): AppRulePartition {
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        val pinned = mutableListOf<String>()

        for (rule in rules) {
            val appId = appIdSelector(rule)
            when (modeSelector(rule)) {
                AppSplitMode.INCLUDED -> included.add(appId)
                AppSplitMode.EXCLUDED -> excluded.add(appId)
                AppSplitMode.PINNED -> pinned.add(appId)
            }
        }

        return AppRulePartition(
            includedAppIds = included,
            excludedAppIds = excluded,
            pinnedAppIds = pinned,
        )
    }
}
