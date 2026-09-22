package com.bringyour.network.ui.stats

/**
 * Pure deterministic filtering logic for the App Split Rules screen.
 */
object AppSplitFilter {

    /**
     * Filters app split rules. Matches [query] case-insensitively against the
     * app's display label (if present in [labelsByPackage]) or its package ID ([appIdSelector]).
     *
     * Leading and trailing whitespace in [query] is trimmed. An empty or blank query returns
     * all rules unchanged, preserving original list ordering.
     */
    fun <T> filterRules(
        rules: List<T>,
        labelsByPackage: Map<String, String?>,
        query: String,
        appIdSelector: (T) -> String,
    ): List<T> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return rules
        }
        return rules.filter { rule ->
            val appId = appIdSelector(rule)
            val label = labelsByPackage[appId]
            (label?.contains(trimmed, ignoreCase = true) == true) ||
                appId.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * Filters unruled apps. Matches [query] case-insensitively against the
     * app's display label or its package name.
     *
     * Leading and trailing whitespace in [query] is trimmed. An empty or blank query returns
     * all apps unchanged, preserving original list ordering.
     */
    fun <T> filterApps(
        apps: List<T>,
        query: String,
        labelSelector: (T) -> String,
        packageSelector: (T) -> String,
    ): List<T> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return apps
        }
        return apps.filter { app ->
            labelSelector(app).contains(trimmed, ignoreCase = true) ||
                packageSelector(app).contains(trimmed, ignoreCase = true)
        }
    }
}
