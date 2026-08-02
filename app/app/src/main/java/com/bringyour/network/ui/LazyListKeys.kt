package com.bringyour.network.ui

/**
 * Returns a Bundle-saveable key that remains unique even when an API omits an
 * identifier or returns the same identifier for multiple rows.
 *
 * Backend identifiers are still included to keep diagnostics readable, while
 * the section and ordered index provide the uniqueness Compose requires.
 */
internal fun indexedLazyListKey(
    section: String,
    index: Int,
    backendId: Any?,
): String = "$section:$index:${backendId?.toString().orEmpty()}"
